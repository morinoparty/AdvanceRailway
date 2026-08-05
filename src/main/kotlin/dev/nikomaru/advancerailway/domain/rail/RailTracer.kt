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

    /**
     * 探索中の1経路。[visited] は経路ごとに独立させる。
     * 全経路で共有すると、複線・待避線の合流地点で後着の経路が LOOP として
     * 途中終端になり、その先の本来の終端（と flags の組）が列挙されない。
     */
    private data class Frame(
        val prev: Point3D,
        val cur: Point3D,
        val firstLeg: Point3D,
        val flags: List<BranchDirection>,
        val visited: HashSet<Pair<Point3D, Point3D>>,
        val steps: Int,
    )

    /**
     * [first] から [directionPoint] 方向へレールをたどり、全分岐を探索して終端を列挙する。
     * flags に出発方角は含まれない（呼び出し側が方向を固定しているため）。
     *
     * - [limit] は探索全体の総ステップ予算（メインスレッド占有時間の上限）。
     * - 経路が自分自身の通過エッジに戻ると [EndpointKind.LOOP] で終端化される。
     * - レール直下が [stopBlocks] のブロックなら [EndpointKind.STOP_BLOCK] で終端化する。
     */
    fun trace(
        first: Point3D,
        directionPoint: Point3D,
        world: RailWorld,
        stopBlocks: Set<Material>,
        limit: Long,
        maxEndpoints: Int,
    ): Either<RailTraceError, List<BranchEndpoint>> = explore(
        first = first,
        seeds = listOf(Frame(first, directionPoint, directionPoint, emptyList(), hashSetOf(), 0)),
        world = world,
        stopBlocks = stopBlocks,
        limit = limit,
        maxEndpoints = maxEndpoints,
    )

    /**
     * [first] に接続する全方向へ探索し、終端を列挙する（inspect 用）。
     * 各終端の flags の先頭は [first] からの出発方角で、以降が分岐点で選んだ方角。
     * `/ar railway add` の flags 引数にそのまま使える形式。
     */
    fun traceAll(
        first: Point3D,
        world: RailWorld,
        stopBlocks: Set<Material>,
        limit: Long,
        maxEndpoints: Int,
    ): Either<RailTraceError, List<BranchEndpoint>> {
        val seeds = adjacentRails(first, world).mapNotNull { leg ->
            val flag = BranchDirection.fromPoints(first, leg) ?: return@mapNotNull null
            Frame(first, leg, leg, listOf(flag), hashSetOf(), 0)
        }
        return explore(first, seeds, world, stopBlocks, limit, maxEndpoints)
    }

    private fun explore(
        first: Point3D,
        seeds: List<Frame>,
        world: RailWorld,
        stopBlocks: Set<Material>,
        limit: Long,
        maxEndpoints: Int,
    ): Either<RailTraceError, List<BranchEndpoint>> {
        val results = mutableListOf<BranchEndpoint>()
        val stack = ArrayDeque(seeds)
        var steps = 0L

        fun endpoint(kind: EndpointKind, frame: Frame) = BranchEndpoint(
            flags = frame.flags,
            kind = kind,
            forward = InspectData(first, frame.firstLeg, frame.cur),
            backward = InspectData(frame.cur, frame.prev, first),
        )

        while (stack.isNotEmpty()) {
            var frame = stack.removeLast()
            while (true) {
                if (++steps > limit) {
                    return RailTraceError.ATTACHED_TO_LIMIT.left()
                }
                if (!frame.visited.add(frame.prev to frame.cur)) {
                    results.add(endpoint(EndpointKind.LOOP, frame))
                    break
                }
                if (world.materialBelow(frame.cur) in stopBlocks) {
                    results.add(endpoint(EndpointKind.STOP_BLOCK, frame))
                    break
                }
                val rails = nextRails(frame.prev, frame.cur, world)
                if (rails.isEmpty()) {
                    results.add(endpoint(EndpointKind.RAIL_END, frame))
                    break
                }
                if (rails.size == 1) {
                    frame = frame.copy(prev = frame.cur, cur = rails.first())
                    continue
                }
                if (results.size + stack.size + rails.size > maxEndpoints) {
                    return RailTraceError.ATTACHED_TO_LIMIT.left()
                }
                for (next in rails) {
                    val flag = BranchDirection.fromPoints(frame.cur, next) ?: continue
                    stack.addLast(
                        frame.copy(
                            prev = frame.cur,
                            cur = next,
                            flags = frame.flags + flag,
                            visited = HashSet(frame.visited),
                        )
                    )
                }
                break
            }
        }
        return results.right()
    }

    /**
     * [first] から [end] へ到達できる経路を全方向・全分岐について列挙する。
     * 各候補の flags は先頭が出発方角、以降が分岐点で選ぶ方角（add の flags と同形式）。
     *
     * 行き止まり・経路内ループ・終点以外の stopBlock に当たった経路は棄却する。
     * 総ステップ数が [limit] を超えるか、候補が [maxRoutes] を超えると打ち切りエラー。
     */
    fun findRoutes(
        first: Point3D,
        end: Point3D,
        world: RailWorld,
        stopBlocks: Set<Material>,
        limit: Long,
        maxRoutes: Int,
    ): Either<RailTraceError, List<RouteCandidate>> {
        val results = mutableListOf<RouteCandidate>()
        val stack = ArrayDeque(
            adjacentRails(first, world).mapNotNull { leg ->
                val flag = BranchDirection.fromPoints(first, leg) ?: return@mapNotNull null
                Frame(first, leg, leg, listOf(flag), hashSetOf(), 1)
            }
        )
        var steps = 0L
        while (stack.isNotEmpty()) {
            var frame = stack.removeLast()
            while (true) {
                if (++steps > limit) {
                    return RailTraceError.ATTACHED_TO_LIMIT.left()
                }
                if (!frame.visited.add(frame.prev to frame.cur)) {
                    break
                }
                if (frame.cur == end) {
                    if (results.size >= maxRoutes) {
                        return RailTraceError.ATTACHED_TO_LIMIT.left()
                    }
                    results.add(RouteCandidate(frame.flags, frame.steps))
                    break
                }
                if (world.materialBelow(frame.cur) in stopBlocks) {
                    break
                }
                val rails = nextRails(frame.prev, frame.cur, world)
                if (rails.isEmpty()) {
                    break
                }
                if (rails.size == 1) {
                    frame = frame.copy(prev = frame.cur, cur = rails.first(), steps = frame.steps + 1)
                    continue
                }
                for (next in rails) {
                    val flag = BranchDirection.fromPoints(frame.cur, next) ?: continue
                    stack.addLast(
                        frame.copy(
                            prev = frame.cur,
                            cur = next,
                            flags = frame.flags + flag,
                            visited = HashSet(frame.visited),
                            steps = frame.steps + 1,
                        )
                    )
                }
                break
            }
        }
        return results.right()
    }

    /** [cur] から進めるレール座標を返す（[prev] は除外）。 */
    fun nextRails(prev: Point3D, cur: Point3D, world: RailWorld): List<Point3D> = adjacentRails(cur, world).filter { it != prev }

    /**
     * [cur] に接続しているレール座標。
     *
     * 現在の形状（shape）が向いている先に加えて、**隣のレールがこちらへ接続している**場合も
     * 接続とみなす。ポイント（分岐レール）はレバー等で形状が切り替わるため、現在の切替状態
     * だけを見ると直進状態の T 字路などで分岐を見落とす。隣接レール側の形状から逆向きに
     * 判定することで、切替状態に依存せず全ての脚を検出する。
     * 隣どうしでも互いに向き合っていない並行線は接続扱いにならない。
     */
    fun adjacentRails(cur: Point3D, world: RailWorld): List<Point3D> {
        val shape = world.shapeAt(cur) ?: return emptyList()
        val fromShape = availableOffsets(shape).map { (x, y, z) ->
            Point3D(cur.x + x, cur.y + y, cur.z + z)
        }.filter { world.shapeAt(it) != null }
        val connectsBack = NEIGHBOR_OFFSETS.mapNotNull { (x, y, z) ->
            val neighbor = Point3D(cur.x + x, cur.y + y, cur.z + z)
            val neighborShape = world.shapeAt(neighbor) ?: return@mapNotNull null
            val connected = availableOffsets(neighborShape).any { (nx, ny, nz) ->
                neighbor.x + nx == cur.x && neighbor.y + ny == cur.y && neighbor.z + nz == cur.z
            }
            if (connected) neighbor else null
        }
        return (fromShape + connectsBack).distinct()
    }

    /** 水平 4 方向 × 上下 1 段の近傍オフセット。 */
    private val NEIGHBOR_OFFSETS: List<Triple<Int, Int, Int>> =
        listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).flatMap { (dx, dz) ->
            listOf(Triple(dx, 0, dz), Triple(dx, -1, dz), Triple(dx, 1, dz))
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
