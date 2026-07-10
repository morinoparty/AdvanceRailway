/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.route

import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [RouteFinder] の純粋なグラフ探索ロジックの単体テスト。
 * Bukkit / Koin に依存しないため、サーバーを起動せずに [RouteEdge] を直接組み立てて検証する。
 */
class RouteFinderTest {

    private fun st(id: String) = StationId(id)
    private fun rw(id: String) = RailwayId(id)

    /** 双方向の辺（無向路線）を張るヘルパ。既定の buildEdges と同じ扱い。 */
    private fun undirected(railway: String, a: String, b: String, time: Long, group: String? = null): List<RouteEdge> {
        val g = group?.let { GroupId(it) }
        return listOf(
            RouteEdge(rw(railway), st(a), st(b), time, g),
            RouteEdge(rw(railway), st(b), st(a), time, g),
        )
    }

    private fun rightOrFail(result: arrow.core.Either<RouteError, Route>): Route {
        assertInstanceOf(arrow.core.Either.Right::class.java, result, "expected a route but got $result")
        return (result as arrow.core.Either.Right).value
    }

    @Test
    @DisplayName("finds a single-leg route across one railway")
    fun singleLeg() {
        val edges = undirected("rw-ab", "a", "b", 120)
        val route = rightOrFail(RouteFinder.findRoute(edges, st("a"), st("b")))

        assertEquals(1, route.legs.size)
        assertEquals(120L, route.totalSeconds)
        assertEquals(listOf("a", "b"), route.stations.map { it.value })
        assertEquals("rw-ab", route.legs.first().railwayId.value)
    }

    @Test
    @DisplayName("chains multiple railways into a multi-leg route and sums seconds")
    fun multiLeg() {
        val edges = undirected("rw-ab", "a", "b", 60) + undirected("rw-bc", "b", "c", 90)
        val route = rightOrFail(RouteFinder.findRoute(edges, st("a"), st("c")))

        assertEquals(2, route.legs.size)
        assertEquals(150L, route.totalSeconds)
        assertEquals(listOf("a", "b", "c"), route.stations.map { it.value })
    }

    @Test
    @DisplayName("prefers the shortest total time over fewer hops")
    fun prefersShortestTime() {
        // Direct a->c costs 1000; the two-hop a->b->c costs 20. Dijkstra must pick the cheaper.
        val edges = undirected("rw-ac", "a", "c", 1000) +
            undirected("rw-ab", "a", "b", 10) +
            undirected("rw-bc", "b", "c", 10)
        val route = rightOrFail(RouteFinder.findRoute(edges, st("a"), st("c")))

        assertEquals(20L, route.totalSeconds)
        assertEquals(listOf("a", "b", "c"), route.stations.map { it.value })
    }

    @Test
    @DisplayName("treats railways as undirected: the reverse direction is routable")
    fun reverseIsRoutable() {
        val edges = undirected("rw-ab", "a", "b", 42)
        val route = rightOrFail(RouteFinder.findRoute(edges, st("b"), st("a")))

        assertEquals(1, route.legs.size)
        assertEquals(42L, route.totalSeconds)
        assertEquals(listOf("b", "a"), route.stations.map { it.value })
    }

    @Test
    @DisplayName("carries the group through to each leg")
    fun carriesGroup() {
        val edges = undirected("rw-ab", "a", "b", 30, group = "main-line")
        val route = rightOrFail(RouteFinder.findRoute(edges, st("a"), st("b")))

        assertEquals("main-line", route.legs.first().group?.value)
    }

    @Test
    @DisplayName("returns SameStation when from equals to")
    fun sameStation() {
        val edges = undirected("rw-ab", "a", "b", 10)
        val result = RouteFinder.findRoute(edges, st("a"), st("a"))

        assertTrue(result.isLeft())
        assertEquals(RouteError.SameStation, (result as arrow.core.Either.Left).value)
    }

    @Test
    @DisplayName("returns NoPath when the destination is in a disconnected component")
    fun noPathDisconnected() {
        val edges = undirected("rw-ab", "a", "b", 10) + undirected("rw-cd", "c", "d", 10)
        val result = RouteFinder.findRoute(edges, st("a"), st("d"))

        assertTrue(result.isLeft())
        assertEquals(RouteError.NoPath, (result as arrow.core.Either.Left).value)
    }

    @Test
    @DisplayName("returns NoPath when the origin has no edges at all")
    fun noPathIsolatedOrigin() {
        val edges = undirected("rw-bc", "b", "c", 10)
        val result = RouteFinder.findRoute(edges, st("a"), st("c"))

        assertTrue(result.isLeft())
        assertEquals(RouteError.NoPath, (result as arrow.core.Either.Left).value)
    }
}
