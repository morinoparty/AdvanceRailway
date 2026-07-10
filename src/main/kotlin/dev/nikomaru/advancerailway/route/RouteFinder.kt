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
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import java.util.PriorityQueue

/**
 * ネットワークグラフ上の 1 本の有向辺。
 * 駅 [from] から駅 [to] へ、路線 [railwayId] を [timeRequired] 秒で結ぶ。
 */
data class RouteEdge(
    val railwayId: RailwayId,
    val from: StationId,
    val to: StationId,
    val timeRequired: Long,
    val group: GroupId?,
)

/** 求めた経路の 1 区間（1 本の路線に乗る単位）。 */
data class RouteLeg(
    val railwayId: RailwayId,
    val from: StationId,
    val to: StationId,
    val timeRequired: Long,
    val group: GroupId?,
)

/** 出発駅から到着駅までの経路探索結果。 */
data class Route(
    val legs: List<RouteLeg>,
    /**
     * 合計所要時間（秒）。
     * 区間ごとに分へ丸めてから合算すると誤差が積み上がるため、生の秒で合算した値を保持する。
     */
    val totalSeconds: Long,
) {
    /** 出発駅から到着駅まで、通過順に並んだ駅列。 */
    val stations: List<StationId>
        get() = if (legs.isEmpty()) emptyList() else listOf(legs.first().from) + legs.map { it.to }
}

/** 経路を返せなかった理由。 */
sealed interface RouteError {
    /** 出発駅と到着駅が同一。 */
    data object SameStation : RouteError

    /** 出発駅と到着駅の間に経路が存在しない（未接続 / 孤立駅）。 */
    data object NoPath : RouteError
}

/**
 * 路線データを重み付きグラフとみなし、駅間の最短（所要時間最小）経路を求める純粋ロジック。
 *
 * Bukkit / Koin に依存せず、[RouteEdge] のリストと [StationId] のみで完結するため、
 * サーバーを起動しなくても単体テストできる（[dev.nikomaru.advancerailway.mineauth.dto.RailwayDtoMapper]
 * と同じ「サーバー非依存の純粋ロジックを切り出す」方針）。
 */
object RouteFinder {

    /**
     * すべての [RailwayData] からグラフの辺集合を構築する。
     *
     * v1 では路線を**無向辺**として扱い、各路線につき両方向の [RouteEdge] を張る。
     * [dev.nikomaru.advancerailway.file.type.LineType] による方向別ルーティング
     * （UP_LINE / DOWN_LINE の区別）は、既存データがすべて UP_DOWN_LINE であり
     * from/to の意味が確定していないため、将来対応とする。
     */
    fun buildEdges(railways: List<RailwayData>): List<RouteEdge> = railways.flatMap { railway ->
        listOf(
            RouteEdge(railway.id, railway.fromStation, railway.toStation, railway.timeRequired, railway.group),
            RouteEdge(railway.id, railway.toStation, railway.fromStation, railway.timeRequired, railway.group),
        )
    }

    /**
     * [from] から [to] までの、所要時間が最小となる経路を Dijkstra 法で求める。
     *
     * [timeRequired]（秒、非負）を辺の重みとする。駅の存在確認は行わない
     * （呼び出し側が事前に検証する想定）。到達不能な場合は [RouteError.NoPath] を返す。
     *
     * @return 最短経路、または探索に失敗した理由。
     */
    fun findRoute(edges: List<RouteEdge>, from: StationId, to: StationId): Either<RouteError, Route> {
        if (from == to) return RouteError.SameStation.left()

        val adjacency: Map<StationId, List<RouteEdge>> = edges.groupBy { it.from }
        val distance = HashMap<StationId, Long>().apply { put(from, 0L) }
        val incomingEdge = HashMap<StationId, RouteEdge>()
        val settled = HashSet<StationId>()
        val queue = PriorityQueue<Pair<StationId, Long>>(compareBy { it.second })
        queue.add(from to 0L)

        while (queue.isNotEmpty()) {
            val (node, nodeDistance) = queue.poll()
            if (!settled.add(node)) continue // 既に確定済み（PriorityQueue の遅延削除）。
            if (node == to) break
            for (edge in adjacency[node].orEmpty()) {
                if (edge.to in settled) continue
                val candidate = nodeDistance + edge.timeRequired
                if (candidate < (distance[edge.to] ?: Long.MAX_VALUE)) {
                    distance[edge.to] = candidate
                    incomingEdge[edge.to] = edge
                    queue.add(edge.to to candidate)
                }
            }
        }

        val total = distance[to] ?: return RouteError.NoPath.left()

        // to から from へ辿って経路を復元する。
        val legs = ArrayDeque<RouteLeg>()
        var cursor = to
        while (cursor != from) {
            val edge = incomingEdge[cursor] ?: return RouteError.NoPath.left()
            legs.addFirst(RouteLeg(edge.railwayId, edge.from, edge.to, edge.timeRequired, edge.group))
            cursor = edge.from
        }
        return Route(legs.toList(), total).right()
    }
}
