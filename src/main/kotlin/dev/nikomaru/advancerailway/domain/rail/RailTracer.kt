/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.domain.rail

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.nikomaru.advancerailway.domain.error.RailTraceError
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import org.bukkit.Material
import org.bukkit.block.data.Rail

/** ブロック照会の抽象。本番は Bukkit World、テストではフェイクを実装する。 */
interface RailWorld {
    /** その座標のレール形状。レールでなければ null。 */
    fun shapeAt(p: Point3D): Rail.Shape?

    /** レール直下 (x, y-1, z) のブロック種別。 */
    fun materialBelow(p: Point3D): Material
}

/**
 * レール網の探索。Bukkit のワールドやスレッドに依存しない純粋ロジック。
 * メインスレッドでの呼び出しは [RailwayUtils.railEndpointInspect] が担う。
 */
object RailTracer {

    private data class Frame(val prev: Point3D, val cur: Point3D, val flags: List<BranchDirection>)

    /**
     * [first] から [directionPoint] 方向へレールをたどり、全分岐を探索して終端を列挙する。
     *
     * - [limit] は探索全体の総ステップ予算（メインスレッド占有時間の上限）。
     * - 同じ有向エッジは一度しか歩かないため、環状線路や合流は [EndpointKind.LOOP] で終端化される。
     * - レール直下が [stopBlocks] のブロックなら [EndpointKind.STOP_BLOCK] で終端化する。
     */
    fun trace(
        first: Point3D,
        directionPoint: Point3D,
        world: RailWorld,
        stopBlocks: Set<Material>,
        limit: Long,
        maxEndpoints: Int,
    ): Either<RailTraceError, List<BranchEndpoint>> {
        val results = mutableListOf<BranchEndpoint>()
        val visitedEdges = hashSetOf<Pair<Point3D, Point3D>>()
        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame(first, directionPoint, emptyList()))
        var steps = 0L

        fun endpoint(kind: EndpointKind, prev: Point3D, cur: Point3D, flags: List<BranchDirection>) = BranchEndpoint(
            flags = flags,
            kind = kind,
            forward = InspectData(first, directionPoint, cur),
            backward = InspectData(cur, prev, first),
        )

        while (stack.isNotEmpty()) {
            var (prev, cur, flags) = stack.removeLast()
            while (true) {
                if (++steps > limit) {
                    return RailTraceError.ATTACHED_TO_LIMIT.left()
                }
                if (!visitedEdges.add(prev to cur)) {
                    results.add(endpoint(EndpointKind.LOOP, prev, cur, flags))
                    break
                }
                if (world.materialBelow(cur) in stopBlocks) {
                    results.add(endpoint(EndpointKind.STOP_BLOCK, prev, cur, flags))
                    break
                }
                val rails = nextRails(prev, cur, world)
                if (rails.isEmpty()) {
                    results.add(endpoint(EndpointKind.RAIL_END, prev, cur, flags))
                    break
                }
                if (rails.size == 1) {
                    prev = cur
                    cur = rails.first()
                    continue
                }
                if (results.size + stack.size + rails.size > maxEndpoints) {
                    return RailTraceError.ATTACHED_TO_LIMIT.left()
                }
                for (next in rails) {
                    val flag = BranchDirection.from((next.x - cur.x).toInt(), (next.z - cur.z).toInt()) ?: continue
                    stack.addLast(Frame(cur, next, flags + flag))
                }
                break
            }
        }
        return results.right()
    }

    /** [cur] から進めるレール座標を返す（[prev] へ戻る方向は除外）。 */
    fun nextRails(prev: Point3D, cur: Point3D, world: RailWorld): List<Point3D> {
        val shape = world.shapeAt(cur) ?: return emptyList()
        val diff = prev.getDiff(cur)
        return availableOffsets(shape).filterNot {
            it.first == -diff.first.toInt() && it.second == -diff.second.toInt() && it.third == -diff.third.toInt()
        }.map { (x, y, z) ->
            Point3D(cur.x + x, cur.y + y, cur.z + z)
        }.filter { world.shapeAt(it) != null }
    }

    /** レール形状ごとの進行可能オフセット (dx, dy, dz)。 */
    fun availableOffsets(shape: Rail.Shape): List<Triple<Int, Int, Int>> = when (shape) {
        Rail.Shape.EAST_WEST -> {
            listOf(
                Triple(1, 0, 0), Triple(-1, 0, 0), Triple(1, -1, 0), Triple(-1, -1, 0)
            )
        }

        Rail.Shape.NORTH_SOUTH -> {
            listOf(
                Triple(0, -1, -1), Triple(0, -1, 1), Triple(0, 0, -1), Triple(0, 0, 1)
            )
        }

        Rail.Shape.ASCENDING_EAST -> {
            listOf(
                Triple(-1, 0, 0), Triple(-1, -1, 0), Triple(1, 1, 0)
            )
        }

        Rail.Shape.ASCENDING_WEST -> {
            listOf(
                Triple(1, 0, 0), Triple(1, -1, 0), Triple(-1, 1, 0)
            )
        }

        Rail.Shape.ASCENDING_NORTH -> {
            listOf(
                Triple(0, 0, 1), Triple(0, -1, 1), Triple(0, 1, -1)
            )
        }

        Rail.Shape.ASCENDING_SOUTH -> {
            listOf(
                Triple(0, 0, -1), Triple(0, -1, -1), Triple(0, 1, 1)
            )
        }

        Rail.Shape.SOUTH_EAST -> {
            listOf(
                Triple(0, -1, 1), Triple(0, 0, 1), Triple(1, 0, 0), Triple(1, -1, 0)
            )
        }

        Rail.Shape.SOUTH_WEST -> {
            listOf(
                Triple(0, -1, 1), Triple(0, 0, 1), Triple(-1, 0, 0), Triple(-1, -1, 0)
            )
        }

        Rail.Shape.NORTH_EAST -> {
            listOf(
                Triple(0, -1, -1), Triple(0, 0, -1), Triple(1, 0, 0), Triple(1, -1, 0)
            )
        }

        Rail.Shape.NORTH_WEST -> {
            listOf(
                Triple(0, -1, -1), Triple(0, 0, -1), Triple(-1, 0, 0), Triple(-1, -1, 0)
            )
        }
    }
}
