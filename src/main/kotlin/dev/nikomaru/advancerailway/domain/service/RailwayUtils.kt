/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.domain.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.domain.geometry.Line3D
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.rail.BranchDirection
import dev.nikomaru.advancerailway.domain.rail.BranchEndpoint
import dev.nikomaru.advancerailway.domain.rail.RailTracer
import dev.nikomaru.advancerailway.domain.rail.RailWorld
import dev.nikomaru.advancerailway.domain.rail.RouteCandidate
import dev.nikomaru.advancerailway.domain.error.RailTraceError
import dev.nikomaru.advancerailway.storage.model.ConfigData
import dev.nikomaru.advancerailway.platform.coroutines.minecraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.Rail
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object RailwayUtils: KoinComponent {
    val plugin: AdvanceRailway by inject()
    val config: ConfigData by inject()

    fun getRailAvailableDirection(shape: Rail.Shape): List<Triple<Int, Int, Int>> = RailTracer.availableOffsets(shape)

    fun getRailAvailableDirection(point: Point3D): List<Triple<Int, Int, Int>> {
        val location = point.toLocation(Bukkit.getWorld("world")!!)
        return getRailAvailableDirection((location.block.blockData as Rail).shape)
    }

    private fun bukkitRailWorld(world: World): RailWorld = object : RailWorld {
        override fun shapeAt(p: Point3D): Rail.Shape? = (p.toLocation(world).block.blockData as? Rail)?.shape

        override fun materialBelow(p: Point3D): Material =
            world.getBlockAt(p.x.toInt(), p.y.toInt() - 1, p.z.toInt()).type
    }

    /** config の inspectStopBlocks を Material に解決する。不明な名前は警告してスキップ。 */
    fun resolveStopBlocks(): Set<Material> = config.inspectStopBlocks.mapNotNull { name ->
        Material.matchMaterial(name).also {
            if (it == null) {
                plugin.logger.warning("Unknown material in inspectStopBlocks: $name")
            }
        }
    }.toSet()

    /**
     * 指定レールに接続しているレール座標を返す。
     * ポイント（分岐レール）は切替状態に関係なく全ての脚を返す（[RailTracer.adjacentRails] 参照）。
     */
    suspend fun detectAdjacentRails(
        point: Point3D,
        world: World = Bukkit.getWorld("world")!!,
    ): List<Point3D> = withContext(Dispatchers.minecraft) {
        RailTracer.adjacentRails(point, bukkitRailWorld(world))
    }

    /**
     * クリック点からレール網を全方向へ探索し、分岐ごとの終端を列挙する。
     * 各終端の flags の先頭はクリック点からの出発方角で、以降が分岐点で選んだ方角
     * （`/ar railway add` の flags 引数と同じ形式）。
     *
     * [flagPrefix] を指定すると、その並びに沿う経路だけを探索する。
     */
    suspend fun railEndpointInspect(
        first: Point3D,
        world: World = Bukkit.getWorld("world")!!,
        flagPrefix: List<BranchDirection> = emptyList(),
    ): Either<RailTraceError, List<BranchEndpoint>> = withContext(Dispatchers.minecraft) {
        RailTracer.traceAll(
            first, bukkitRailWorld(world), resolveStopBlocks(), config.limit, config.inspectMaxEndpoints, flagPrefix
        )
    }

    /**
     * [first] から [end] へ到達できる経路の候補を全方向・全分岐について列挙する。
     * 各候補の flags は `/ar railway add` の flags 引数にそのまま使える。
     */
    suspend fun findRoutes(
        first: Point3D,
        end: Point3D,
        world: World = Bukkit.getWorld("world")!!,
    ): Either<RailTraceError, List<RouteCandidate>> = withContext(Dispatchers.minecraft) {
        RailTracer.findRoutes(
            first, end, bukkitRailWorld(world), resolveStopBlocks(), config.limit, config.inspectMaxEndpoints
        )
    }

    /**
     * 始点から終点までの単一経路をトレースする。
     * [flags] の先頭は始点からの出発方角、以降は分岐点で順に消費して進路を選ぶ方角
     * （inspect / findRoutes の flags と同じ形式）。
     * 出発方角に対応する隣接レールがなければ [RailTraceError.DIRECTION_NOT_FOUND]、
     * フラグが足りない・一致する進路がない分岐に当たると [RailTraceError.MULTIPLE_RAIL]。
     */
    suspend fun getLine(
        startPoint: Point3D,
        endPoint: Point3D,
        flags: List<BranchDirection>,
    ): Either<RailTraceError, Line3D> = withContext(Dispatchers.minecraft) {
        val railWorld = bukkitRailWorld(Bukkit.getWorld("world")!!)
        val departure = flags.firstOrNull() ?: return@withContext RailTraceError.DIRECTION_NOT_FOUND.left()
        val directionPoint = RailTracer.adjacentRails(startPoint, railWorld)
            .firstOrNull { BranchDirection.fromPoints(startPoint, it) == departure }
            ?: return@withContext RailTraceError.DIRECTION_NOT_FOUND.left()
        var previousPoint = startPoint
        var currentPoint = directionPoint
        var count = 0
        var flagIndex = 1
        val line = Line3D(startPoint, directionPoint)
        while (count < config.limit) {
            val rails = RailTracer.nextRails(previousPoint, currentPoint, railWorld)
            if (rails.isEmpty()) {
                return@withContext RailTraceError.NOT_FOUND_END_POINT.left()
            }
            val next = if (rails.size == 1) {
                rails.first()
            } else {
                val flag = flags.getOrNull(flagIndex++) ?: return@withContext RailTraceError.MULTIPLE_RAIL.left()
                rails.find { candidate ->
                    BranchDirection.from(
                        (candidate.x - currentPoint.x).toInt(), (candidate.z - currentPoint.z).toInt()
                    ) == flag
                } ?: return@withContext RailTraceError.MULTIPLE_RAIL.left()
            }
            previousPoint = currentPoint
            currentPoint = next
            if (currentPoint == endPoint) {
                line.addPoint(currentPoint)
                return@withContext line.right()
            }
            if (count % 2 == 0) {
                line.addPoint(currentPoint)
            }
            count++
        }
        return@withContext RailTraceError.ATTACHED_TO_LIMIT.left()
    }

}
