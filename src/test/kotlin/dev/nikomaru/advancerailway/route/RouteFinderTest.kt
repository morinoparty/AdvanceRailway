/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
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
        assertNull(route.legs.first().group)
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
    @DisplayName("rides rail then walks the last stretch to a rail-disconnected station")
    fun railThenWalkCombo() {
        val a = station("a", 0.0, 0.0)
        val b = station("b", 1000.0, 0.0)
        val c = station("c", 1010.0, 0.0) // 10 blocks from b, no rail -> 2s walk
        val route = rightOrFail(
            RouteFinder.findRoute(
                listOf(a, b, c), listOf(rail("ab", a, b, 5, group = "main")), Waypoint.Station(a), c
            )
        )

        assertEquals(2, route.legs.size)
        assertEquals(TravelMode.RAIL, route.legs[0].mode)
        assertEquals("main", route.legs[0].group?.value)
        assertEquals(TravelMode.WALK, route.legs[1].mode)
        assertNull(route.legs[1].group) // the rail group must not leak onto the walk leg
        assertEquals("c", route.legs[1].to.value)
        assertEquals(7L, route.totalSeconds) // 5s rail + 2s walk
    }

    @Test
    @DisplayName("combines a walk from the current location with a rail hop and pins the walk time")
    fun originThenRail() {
        val a = station("a", 10.0, 0.0) // origin -> a is 10 blocks = 2s
        val b = station("b", 1000.0, 0.0)
        val origin = Waypoint.Origin("world", Point3D(0.0, 64.0, 0.0))
        val route = rightOrFail(RouteFinder.findRoute(listOf(a, b), listOf(rail("ab", a, b, 10)), origin, b))

        assertEquals(2, route.legs.size)
        assertEquals(TravelMode.WALK, route.legs.first().mode)
        assertNull(route.legs.first().from) // starts from the current location
        assertEquals(2L, route.legs.first().timeSeconds) // 10 / 4.317 = 2.3 -> 2s
        assertEquals(TravelMode.RAIL, route.legs.last().mode)
        assertEquals(12L, route.totalSeconds) // 2s walk + 10s rail
        // current location is not a station, so the station list starts at 'a'.
        assertEquals(listOf("a", "b"), route.stations.map { it.value })
    }

    @Test
    @DisplayName("uses unrounded costs so a rail beats an accumulated per-hop walk rounding")
    fun usesUnroundedCostsForSearch() {
        // Three 6-block walk hops: each rounds 1.39s -> 1s, so a naive per-hop-rounded search would
        // total 3s and wrongly beat the 4s rail. Unrounded, the walk path is ~4.17s, so rail wins.
        val a = station("a", 0.0, 0.0)
        val b = station("b", 6.0, 0.0)
        val c = station("c", 12.0, 0.0)
        val d = station("d", 18.0, 0.0)
        val route = rightOrFail(
            RouteFinder.findRoute(listOf(a, b, c, d), listOf(rail("ad", a, d, 4)), Waypoint.Station(a), d)
        )

        assertEquals(1, route.legs.size)
        assertEquals(TravelMode.RAIL, route.legs.first().mode)
        assertEquals(4L, route.totalSeconds)
    }

    @Test
    @DisplayName("totalSeconds equals the sum of the per-leg rounded seconds")
    fun totalIsSumOfLegs() {
        val a = station("a", 0.0, 0.0)
        val b = station("b", 10.0, 0.0)
        val c = station("c", 25.0, 0.0)
        val route = rightOrFail(RouteFinder.findRoute(listOf(a, b, c), emptyList(), Waypoint.Station(a), c))

        assertEquals(route.legs.sumOf { it.timeSeconds }, route.totalSeconds)
    }

    @Test
    @DisplayName("uses the horizontal (2D) distance, ignoring the y difference")
    fun ignoresVerticalDistance() {
        val a = StationNode(StationId("a"), "world", Point3D(0.0, 0.0, 0.0))
        val b = StationNode(StationId("b"), "world", Point3D(10.0, 500.0, 0.0)) // huge y gap
        val route = rightOrFail(RouteFinder.findRoute(listOf(a, b), emptyList(), Waypoint.Station(a), b))

        assertEquals(2L, route.totalSeconds) // 10 / 4.317 = 2s, y ignored
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
    @DisplayName("respects a custom walk speed")
    fun customWalkSpeed() {
        val a = station("a", 0.0, 0.0)
        val b = station("b", 20.0, 0.0)
        // At 10 b/s, 20 blocks = 2s (vs ~4.6s at the default speed).
        val route = rightOrFail(RouteFinder.findRoute(listOf(a, b), emptyList(), Waypoint.Station(a), b, walkSpeed = 10.0))

        assertEquals(2L, route.totalSeconds)
    }

    @Test
    @DisplayName("returns SameStation when from equals to")
    fun sameStation() {
        val a = station("a", 0.0, 0.0)
        assertEquals(RouteError.SameStation, leftOf(RouteFinder.findRoute(listOf(a), emptyList(), Waypoint.Station(a), a)))
    }

    @Test
    @DisplayName("does not report SameStation when an Origin sits on the destination")
    fun originOnDestinationIsNotSameStation() {
        // An Origin has no station id, so from==to can't trigger SameStation; it should route (0s walk).
        val a = station("a", 0.0, 0.0)
        val origin = Waypoint.Origin("world", Point3D(0.0, 64.0, 0.0))
        val route = rightOrFail(RouteFinder.findRoute(listOf(a), emptyList(), origin, a))

        assertEquals(1, route.legs.size)
        assertEquals(TravelMode.WALK, route.legs.first().mode)
        assertEquals("a", route.legs.first().to.value)
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

    @Test
    @DisplayName("a railway referencing an unknown station does not crash the search")
    fun railwayToUnknownStationIsIgnored() {
        val a = station("a", 0.0, 0.0)
        val b = station("b", 10.0, 0.0)
        // rail 'ghost' points a -> zzz which is not in the station list; it must be skipped, walking still works.
        val ghost = RailEdge(RailwayId("ghost"), a.id, StationId("zzz"), 1, null)
        val route = rightOrFail(RouteFinder.findRoute(listOf(a, b), listOf(ghost), Waypoint.Station(a), b))

        assertEquals(TravelMode.WALK, route.legs.first().mode)
        assertEquals(2L, route.totalSeconds)
    }

    @Test
    @DisplayName("ignores a zero-length degenerate rail in the heuristic without crashing")
    fun zeroLengthRailDoesNotBreakHeuristic() {
        // Two stations at the same point connected by a 0s rail; maxSpeed must not divide by zero.
        val a = station("a", 0.0, 0.0)
        val b = station("b", 0.0, 0.0)
        val c = station("c", 100.0, 0.0)
        val route = rightOrFail(
            RouteFinder.findRoute(
                listOf(a, b, c), listOf(rail("ab0", a, b, 0), rail("bc", b, c, 5)), Waypoint.Station(a), c
            )
        )

        assertEquals(5L, route.totalSeconds) // 0s a->b + 5s b->c
    }
}
