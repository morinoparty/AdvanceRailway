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
import dev.nikomaru.advancerailway.domain.error.DataSearchError
import dev.nikomaru.advancerailway.domain.error.RailTraceError
import dev.nikomaru.advancerailway.storage.DataPaths
import dev.nikomaru.advancerailway.storage.model.ConfigData
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.utils.Utils.json
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
     * クリック点から片方向へレール網を全探索し、分岐ごとの終端を列挙する。
     * 分岐で失敗せず、各終端に分岐点で選んだ方角のフラグ（例: "EE"）が付く。
     */
    suspend fun railEndpointInspect(
        first: Point3D,
        directionPoint: Point3D,
        world: World = Bukkit.getWorld("world")!!,
    ): Either<RailTraceError, List<BranchEndpoint>> = withContext(Dispatchers.minecraft) {
        RailTracer.trace(
            first, directionPoint, bukkitRailWorld(world), resolveStopBlocks(), config.limit, config.inspectMaxEndpoints
        )
    }

    /**
     * 始点から終点までの単一経路をトレースする。
     * 分岐点では [flags] の先頭から順に方角を消費して進路を選ぶ（inspect の分岐フラグと同じ形式）。
     * フラグが足りない・一致する進路がない分岐に当たると [RailTraceError.MULTIPLE_RAIL]。
     */
    suspend fun getLine(
        startPoint: Point3D,
        directionPoint: Point3D,
        endPoint: Point3D,
        flags: List<BranchDirection> = emptyList(),
    ): Either<RailTraceError, Line3D> = withContext(Dispatchers.minecraft) {
        val railWorld = bukkitRailWorld(Bukkit.getWorld("world")!!)
        var previousPoint = startPoint
        var currentPoint = directionPoint
        var count = 0
        var flagIndex = 0
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


    suspend fun getRailwayData(railwayId: RailwayId): Either<DataSearchError, RailwayData> =
        withContext(Dispatchers.IO) {
            val folder = DataPaths.railways
            if (!folder.exists()) {
                folder.mkdirs()
                return@withContext Either.Left(DataSearchError.NOT_FOUND)
            }
            val file = folder.resolve("${railwayId.value}.json")
            if (!file.exists()) {
                return@withContext Either.Left(DataSearchError.NOT_FOUND)
            }
            return@withContext try {
                Either.Right(json.decodeFromString<RailwayData>(file.readText()))
            } catch (e: Exception) {
                plugin.logger.warning("Failed to decode railway data '${file.name}': ${e.message}")
                Either.Left(DataSearchError.DESERIALIZATION_FAILED)
            }
        }

}