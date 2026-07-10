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
import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [RouteFinder] の純粋な幾何グラフ探索（レール + 徒歩、A*）の単体テスト。
 * Bukkit / Koin に依存しないため、[StationNode] / [RailEdge] を直接組み立てて検証する。
 */
class RouteFinderTest {

    private fun station(id: String, x: Double, z: Double, world: String = "world") =
        StationNode(StationId(id), world, Point3D(x, 64.0, z))

    private fun rail(id: String, from: StationNode, to: StationNode, time: Long, group: String? = null) =
        RailEdge(RailwayId(id), from.id, to.id, time, group?.let { GroupId(it) })

    private fun rightOrFail(result: Either<RouteError, Route>): Route {
        assertInstanceOf(Either.Right::class.java, result, "expected a route but got $result")
        return (result as Either.Right).value
    }

    private fun leftOf(result: Either<RouteError, Route>): RouteError {
        assertInstanceOf(Either.Left::class.java, result, "expected an error but got $result")
        return (result as Either.Left).value
    }

    @Test
    @DisplayName("prefers a fast rail over the slow walking edge between far stations")
    fun prefersRail() {
        val a = station("a", 0.0, 0.0)
        val b = station("b", 1000.0, 0.0) // walking ~232s
        val route = rightOrFail(RouteFinder.findRoute(listOf(a, b), listOf(rail("rw", a, b, 10)), Waypoint.Station(a), b))

        assertEquals(1, route.legs.size)
        assertEquals(TravelMode.RAIL, route.legs.first().mode)
        assertEquals("rw", route.legs.first().railwayId?.value)
        assertEquals(10L, route.totalSeconds)
    }

    @Test
    @DisplayName("falls back to a walking leg when no rail connects the stations")
    fun walkingFallback() {
        val a = station("a", 0.0, 0.0)
        val b = station("b", 10.0, 0.0) // 10 / 4.317 = 2.3 -> 2s
        val route = rightOrFail(RouteFinder.findRoute(listOf(a, b), emptyList(), Waypoint.Station(a), b))

        assertEquals(1, route.legs.size)
        assertEquals(TravelMode.WALK, route.legs.first().mode)
        assertNull(route.legs.first().railwayId)
        assertEquals(2L, route.totalSeconds)
    }

    @Test
    @DisplayName("takes a walking shortcut when it beats a slow rail")
    fun walkingBeatsSlowRail() {
        val a = station("a", 0.0, 0.0)
        val b = station("b", 10.0, 0.0)
        val route = rightOrFail(
            RouteFinder.findRoute(listOf(a, b), listOf(rail("slow", a, b, 100)), Waypoint.Station(a), b)
        )

        assertEquals(TravelMode.WALK, route.legs.first().mode)
        assertEquals(2L, route.totalSeconds)
    }

    @Test
    @DisplayName("chains rail hops and prefers them over a long direct walk")
    fun multiHopRail() {
        val a = station("a", 0.0, 0.0)
        val b = station("b", 1000.0, 0.0)
        val c = station("c", 2000.0, 0.0)
        val route = rightOrFail(
            RouteFinder.findRoute(
                listOf(a, b, c), listOf(rail("ab", a, b, 10), rail("bc", b, c, 10)), Waypoint.Station(a), c
            )
        )

        assertEquals(2, route.legs.size)
        assertTrue(route.legs.all { it.mode == TravelMode.RAIL })
        assertEquals(listOf("a", "b", "c"), route.stations.map { it.value })
        assertEquals(20L, route.totalSeconds)
    }

    @Test
    @DisplayName("combines a walk from the current location with a rail hop")
    fun originThenRail() {
        val a = station("a", 1.0, 0.0)
        val b = station("b", 1000.0, 0.0)
        val origin = Waypoint.Origin("world", Point3D(0.0, 64.0, 0.0)) // ~0.2s walk to a
        val route = rightOrFail(RouteFinder.findRoute(listOf(a, b), listOf(rail("ab", a, b, 10)), origin, b))

        assertEquals(2, route.legs.size)
        assertEquals(TravelMode.WALK, route.legs.first().mode)
        assertNull(route.legs.first().from) // starts from the current location
        assertEquals(TravelMode.RAIL, route.legs.last().mode)
        // current location is not a station, so the station list starts at 'a'.
        assertEquals(listOf("a", "b"), route.stations.map { it.value })
    }

    @Test
    @DisplayName("carries the group through rail legs")
    fun carriesGroup() {
        val a = station("a", 0.0, 0.0)
        val b = station("b", 1000.0, 0.0)
        val route = rightOrFail(
            RouteFinder.findRoute(listOf(a, b), listOf(rail("rw", a, b, 10, group = "main-line")), Waypoint.Station(a), b)
        )

        assertEquals("main-line", route.legs.first().group?.value)
    }

    @Test
    @DisplayName("returns SameStation when from equals to")
    fun sameStation() {
        val a = station("a", 0.0, 0.0)
        assertEquals(RouteError.SameStation, leftOf(RouteFinder.findRoute(listOf(a), emptyList(), Waypoint.Station(a), a)))
    }

    @Test
    @DisplayName("returns NoPath across worlds with no bridging rail")
    fun noPathAcrossWorlds() {
        val a = station("a", 0.0, 0.0, world = "world")
        val b = station("b", 0.0, 0.0, world = "world_nether")
        assertEquals(RouteError.NoPath, leftOf(RouteFinder.findRoute(listOf(a, b), emptyList(), Waypoint.Station(a), b)))
    }

    @Test
    @DisplayName("rail bridges two worlds even without a walking edge")
    fun railBridgesWorlds() {
        val a = station("a", 0.0, 0.0, world = "world")
        val b = station("b", 0.0, 0.0, world = "world_nether")
        val route = rightOrFail(
            RouteFinder.findRoute(listOf(a, b), listOf(rail("portal", a, b, 50)), Waypoint.Station(a), b)
        )

        assertEquals(1, route.legs.size)
        assertEquals(TravelMode.RAIL, route.legs.first().mode)
        assertEquals(50L, route.totalSeconds)
    }
}
