/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.route

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import java.util.PriorityQueue
import kotlin.math.roundToLong

/** グラフ上のノードとなる駅（幾何情報つき）。 */
data class StationNode(
    val id: StationId,
    /** 駅の存在するワールド名（徒歩接続の同一ワールド判定に使う）。 */
    val world: String,
    val point: Point3D,
)

/** 2 駅を結ぶ路線（無向のレール辺）。 */
data class RailEdge(
    val railwayId: RailwayId,
    val from: StationId,
    val to: StationId,
    val timeRequired: Long,
    val group: GroupId?,
)

/** 経路の起点。駅、またはプレイヤーの現在地。 */
sealed interface Waypoint {
    val world: String
    val point: Point3D

    /** 既存の駅を起点とする。 */
    data class Station(val node: StationNode) : Waypoint {
        override val world: String get() = node.world
        override val point: Point3D get() = node.point
    }

    /** プレイヤーの現在地を起点とする（駅 ID を持たない）。 */
    data class Origin(override val world: String, override val point: Point3D) : Waypoint
}

/** 経路の各区間の移動手段。 */
enum class TravelMode {
    /** 路線（レール）に乗る。 */
    RAIL,

    /** 駅・現在地の間を歩く。 */
    WALK,
}

/** 求めた経路の 1 区間。 */
data class RouteLeg(
    val mode: TravelMode,
    /** [TravelMode.RAIL] のとき乗る路線。[TravelMode.WALK] では `null`。 */
    val railwayId: RailwayId?,
    /** 区間の出発駅。起点が現在地の場合は `null`。 */
    val from: StationId?,
    /** 区間の到着駅（中間・終点は常に駅）。 */
    val to: StationId,
    /** この区間の所要時間（秒）。 */
    val timeSeconds: Long,
    val group: GroupId?,
)

/** 経路探索結果。 */
data class Route(
    val legs: List<RouteLeg>,
    /** 合計所要時間（秒）。表示と一致するよう、各区間の丸め済み秒の総和とする。 */
    val totalSeconds: Long,
) {
    /** 起点（現在地は除く）から終点まで、通過順に並んだ駅列。 */
    val stations: List<StationId>
        get() = if (legs.isEmpty()) emptyList() else buildList {
            legs.first().from?.let { add(it) }
            legs.forEach { add(it.to) }
        }
}

/** 経路を返せなかった理由。 */
sealed interface RouteError {
    /** 出発駅と到着駅が同一。 */
    data object SameStation : RouteError

    /** 経路が存在しない（異なるワールド間で、それらを結ぶレール経路も無い）。 */
    data object NoPath : RouteError
}

/**
 * 路線（レール）と徒歩を組み合わせた幾何グラフ上で、駅間の最短（所要時間最小）経路を A* で求める純粋ロジック。
 *
 * - **レール辺**: 各路線を無向辺とし、重みは [RailEdge.timeRequired]（秒）。
 * - **徒歩辺**: 同一ワールドの任意のノード間を直線水平距離（[Point3D.distanceTo2D]）÷ [walkSpeed] 秒で結ぶ。
 *   これにより、レールで直接つながっていない駅どうしや現在地からでも「歩いて」到達できる。
 *   異なるワールド間は徒歩不可（座標系が異なるため）。
 *
 * ヒューリスティックは「終点までの直線距離 ÷ グラフ内の最大移動速度」。どの辺も
 * 「直線変位 ÷ 最大速度」より速くは進めないため許容的（admissible）であり、A* は最短経路を保証する。
 *
 * Bukkit / Koin に依存せず [Point3D] とワールド名（文字列）のみで完結するため、サーバー非依存で単体テストできる。
 */
object RouteFinder {

    /** Minecraft の既定の歩行速度（ブロック / 秒）。 */
    const val DEFAULT_WALK_SPEED: Double = 4.317

    /** 現在地ノードの内部キー。ID の allowlist（`[A-Za-z0-9_-]`）に含まれない文字を使い駅と衝突させない。 */
    private const val ORIGIN_KEY = "@origin"

    private data class Node(val key: String, val stationId: StationId?, val world: String, val point: Point3D)

    /**
     * ノードへ入る辺の記録（経路復元用）。
     * [cost] は丸め前の秒（実数）で、A* の探索と許容ヒューリスティックの整合のために用いる。
     * 表示用の整数秒は復元時に [cost] を丸めて求める。
     */
    private data class Incoming(
        val fromKey: String,
        val mode: TravelMode,
        val railwayId: RailwayId?,
        val fromStation: StationId?,
        val toStation: StationId,
        val cost: Double,
        val group: GroupId?,
    )

    /**
     * [from] から駅 [to] までの最短経路を求める。
     *
     * @param stations グラフに含める全駅（幾何情報つき）。[to] もこの中に含まれていること。
     * @param railways 全路線（レール辺）。
     * @param from 起点（駅または現在地）。
     * @param to 終点となる駅。
     * @param walkSpeed 歩行速度（ブロック / 秒）。
     */
    fun findRoute(
        stations: List<StationNode>,
        railways: List<RailEdge>,
        from: Waypoint,
        to: StationNode,
        walkSpeed: Double = DEFAULT_WALK_SPEED,
    ): Either<RouteError, Route> {
        val fromStationId = (from as? Waypoint.Station)?.node?.id
        if (fromStationId != null && fromStationId == to.id) return RouteError.SameStation.left()

        val fromNode = when (from) {
            is Waypoint.Station -> Node(from.node.id.value, from.node.id, from.world, from.point)
            is Waypoint.Origin -> Node(ORIGIN_KEY, null, from.world, from.point)
        }
        val goalNode = Node(to.id.value, to.id, to.world, to.point)

        val stationNodes = stations.map { Node(it.id.value, it.id, it.world, it.point) }
        val nodeByKey = HashMap<String, Node>()
        stationNodes.forEach { nodeByKey[it.key] = it }
        nodeByKey[fromNode.key] = fromNode // 起点が現在地でも駅でも登録（駅なら上書きで同一）。

        val railAdjacency: Map<String, List<RailEdge>> = buildRailAdjacency(railways)
        val maxSpeed = maxSpeed(stations, railways, walkSpeed)

        val gScore = HashMap<String, Double>().apply { put(fromNode.key, 0.0) }
        val incoming = HashMap<String, Incoming>()
        val settled = HashSet<String>()
        val open = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        open.add(fromNode.key to heuristic(fromNode, goalNode, maxSpeed))

        while (open.isNotEmpty()) {
            val (key, _) = open.poll()
            if (!settled.add(key)) continue // 遅延削除。
            if (key == goalNode.key) break
            val current = nodeByKey.getValue(key)
            val baseG = gScore.getValue(key)

            for ((neighbor, incomingEdge) in neighbors(current, stationNodes, nodeByKey, railAdjacency, walkSpeed)) {
                if (neighbor.key in settled) continue
                val tentative = baseG + incomingEdge.cost
                if (tentative < (gScore[neighbor.key] ?: Double.MAX_VALUE)) {
                    gScore[neighbor.key] = tentative
                    incoming[neighbor.key] = incomingEdge
                    open.add(neighbor.key to tentative + heuristic(neighbor, goalNode, maxSpeed))
                }
            }
        }

        if (goalNode.key !in gScore) return RouteError.NoPath.left()

        // 終点から起点へ辿って経路を復元する。表示用の整数秒はここで各区間の実数コストを丸めて求める。
        val legs = ArrayDeque<RouteLeg>()
        var cursor = goalNode.key
        while (cursor != fromNode.key) {
            val edge = incoming[cursor] ?: return RouteError.NoPath.left()
            legs.addFirst(
                RouteLeg(edge.mode, edge.railwayId, edge.fromStation, edge.toStation, edge.cost.roundToLong(), edge.group)
            )
            cursor = edge.fromKey
        }
        return Route(legs.toList(), legs.sumOf { it.timeSeconds }).right()
    }

    /** レール辺を「出発駅キー -> 辺」の隣接リストにする（無向なので双方向に張る）。 */
    private fun buildRailAdjacency(railways: List<RailEdge>): Map<String, List<RailEdge>> =
        railways.flatMap { edge ->
            listOf(
                edge,
                RailEdge(edge.railwayId, edge.to, edge.from, edge.timeRequired, edge.group),
            )
        }.groupBy { it.from.value }

    /**
     * [current] から出る辺（レール + 同一ワールドの徒歩）を列挙する。
     * A* の重み [Incoming.cost] は丸め前の秒（実数）とし、ヒューリスティックとの整合を保つ。
     * [nodeByKey] はレール辺の到達駅解決に用いる（呼び出しごとに再構築しないよう外から渡す）。
     */
    private fun neighbors(
        current: Node,
        stationNodes: List<Node>,
        nodeByKey: Map<String, Node>,
        railAdjacency: Map<String, List<RailEdge>>,
        walkSpeed: Double,
    ): List<Pair<Node, Incoming>> {
        val result = ArrayList<Pair<Node, Incoming>>()

        // レール辺（起点が駅のときのみ）。
        current.stationId?.let { fromId ->
            for (edge in railAdjacency[current.key].orEmpty()) {
                val neighbor = nodeByKey[edge.to.value] ?: continue
                result += neighbor to Incoming(
                    fromKey = current.key,
                    mode = TravelMode.RAIL,
                    railwayId = edge.railwayId,
                    fromStation = fromId,
                    toStation = edge.to,
                    cost = edge.timeRequired.toDouble(),
                    group = edge.group,
                )
            }
        }

        // 徒歩辺（同一ワールドの他の駅すべて）。
        for (neighbor in stationNodes) {
            if (neighbor.key == current.key) continue
            if (neighbor.world != current.world) continue
            result += neighbor to Incoming(
                fromKey = current.key,
                mode = TravelMode.WALK,
                railwayId = null,
                fromStation = current.stationId,
                toStation = neighbor.stationId!!, // stationNodes は必ず駅。
                cost = current.point.distanceTo2D(neighbor.point) / walkSpeed,
                group = null,
            )
        }
        return result
    }

    /**
     * ヒューリスティック: 終点までの直線水平距離 ÷ [maxSpeed]。
     * ワールドが異なる場合は距離を見積もれないため 0（常に許容的）。
     */
    private fun heuristic(node: Node, goal: Node, maxSpeed: Double): Double =
        if (node.world != goal.world) 0.0 else node.point.distanceTo2D(goal.point) / maxSpeed

    /** グラフ内の最大「直線変位速度」（徒歩とレールの実効速度の最大）。ヒューリスティックの許容性のため。 */
    private fun maxSpeed(stations: List<StationNode>, railways: List<RailEdge>, walkSpeed: Double): Double {
        val pointById = stations.associate { it.id.value to it.point }
        var max = walkSpeed
        for (edge in railways) {
            if (edge.timeRequired <= 0) continue
            val a = pointById[edge.from.value] ?: continue
            val b = pointById[edge.to.value] ?: continue
            val distance = a.distanceTo2D(b)
            if (distance <= 0.0) continue
            val speed = distance / edge.timeRequired
            if (speed > max) max = speed
        }
        return max
    }
}
