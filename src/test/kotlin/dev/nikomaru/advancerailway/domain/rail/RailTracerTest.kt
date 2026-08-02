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
import dev.nikomaru.advancerailway.domain.error.RailTraceError
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import org.bukkit.Material
import org.bukkit.block.data.Rail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RailTracerTest {

    private class FakeRailWorld(
        private val rails: Map<Point3D, Rail.Shape>,
        private val below: Map<Point3D, Material> = emptyMap(),
    ): RailWorld {
        override fun shapeAt(p: Point3D): Rail.Shape? = rails[p]

        override fun materialBelow(p: Point3D): Material = below[p] ?: Material.STONE
    }

    private fun p(x: Int, y: Int, z: Int) = Point3D(x.toDouble(), y.toDouble(), z.toDouble())

    private fun straightEastWest(fromX: Int, toX: Int, z: Int = 0): Map<Point3D, Rail.Shape> =
        (fromX..toX).associate { p(it, 0, z) to Rail.Shape.EAST_WEST }

    private fun trace(
        world: RailWorld,
        first: Point3D,
        direction: Point3D,
        stopBlocks: Set<Material> = emptySet(),
        limit: Long = 1000,
        maxEndpoints: Int = 16,
    ) = RailTracer.trace(first, direction, world, stopBlocks, limit, maxEndpoints)

    /**
     * 西から junction (SOUTH_EAST) に入ると南・東の2分岐になるレイアウト。
     * x=0..4 直線 → (5,0,0) junction → 南 (5,0,1..3) / 東 (6..7,0,0) → (8,0,0) junction2 → 南 (8,0,1..2) / 東 (9..10,0,0)
     */
    private fun doubleBranchWorld(): FakeRailWorld {
        val rails = buildMap {
            putAll(straightEastWest(0, 4))
            put(p(5, 0, 0), Rail.Shape.SOUTH_EAST)
            put(p(5, 0, 1), Rail.Shape.NORTH_SOUTH)
            put(p(5, 0, 2), Rail.Shape.NORTH_SOUTH)
            put(p(5, 0, 3), Rail.Shape.NORTH_SOUTH)
            putAll(straightEastWest(6, 7))
            put(p(8, 0, 0), Rail.Shape.SOUTH_EAST)
            put(p(8, 0, 1), Rail.Shape.NORTH_SOUTH)
            put(p(8, 0, 2), Rail.Shape.NORTH_SOUTH)
            putAll(straightEastWest(9, 10))
        }
        return FakeRailWorld(rails)
    }

    @Test
    fun straightLineEndsWithSingleEndpoint() {
        val world = FakeRailWorld(straightEastWest(0, 10))
        val result = trace(world, p(0, 0, 0), p(1, 0, 0))
        val endpoints = (result as Either.Right).value
        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals(emptyList<BranchDirection>(), endpoint.flags)
        assertEquals(EndpointKind.RAIL_END, endpoint.kind)
        assertEquals(InspectData(p(0, 0, 0), p(1, 0, 0), p(10, 0, 0)), endpoint.forward)
        assertEquals(InspectData(p(10, 0, 0), p(9, 0, 0), p(0, 0, 0)), endpoint.backward)
    }

    @Test
    fun singleBranchYieldsFlaggedEndpoints() {
        val rails = buildMap {
            putAll(straightEastWest(0, 4))
            put(p(5, 0, 0), Rail.Shape.SOUTH_EAST)
            put(p(5, 0, 1), Rail.Shape.NORTH_SOUTH)
            put(p(5, 0, 2), Rail.Shape.NORTH_SOUTH)
            putAll(straightEastWest(6, 8))
        }
        val result = trace(FakeRailWorld(rails), p(0, 0, 0), p(1, 0, 0))
        val endpoints = (result as Either.Right).value
        assertEquals(2, endpoints.size)
        val byFlags = endpoints.associateBy { it.flags }
        assertEquals(p(5, 0, 2), byFlags[listOf(BranchDirection.SOUTH)]?.forward?.end)
        assertEquals(p(8, 0, 0), byFlags[listOf(BranchDirection.EAST)]?.forward?.end)
        assertTrue(endpoints.all { it.kind == EndpointKind.RAIL_END })
    }

    @Test
    fun twoEastBranchesProduceEEFlag() {
        val result = trace(doubleBranchWorld(), p(0, 0, 0), p(1, 0, 0))
        val endpoints = (result as Either.Right).value
        assertEquals(3, endpoints.size)
        val byFlagString = endpoints.associateBy { it.flagString() }
        assertEquals(p(5, 0, 3), byFlagString["S"]?.forward?.end)
        assertEquals(p(8, 0, 2), byFlagString["ES"]?.forward?.end)
        assertEquals(p(10, 0, 0), byFlagString["EE"]?.forward?.end)
    }

    @Test
    fun circularTrackTerminatesAsLoop() {
        val rails = buildMap {
            put(p(0, 0, 0), Rail.Shape.SOUTH_EAST)
            put(p(1, 0, 0), Rail.Shape.EAST_WEST)
            put(p(2, 0, 0), Rail.Shape.EAST_WEST)
            put(p(3, 0, 0), Rail.Shape.SOUTH_WEST)
            put(p(3, 0, 1), Rail.Shape.NORTH_SOUTH)
            put(p(3, 0, 2), Rail.Shape.NORTH_SOUTH)
            put(p(3, 0, 3), Rail.Shape.NORTH_WEST)
            put(p(2, 0, 3), Rail.Shape.EAST_WEST)
            put(p(1, 0, 3), Rail.Shape.EAST_WEST)
            put(p(0, 0, 3), Rail.Shape.NORTH_EAST)
            put(p(0, 0, 1), Rail.Shape.NORTH_SOUTH)
            put(p(0, 0, 2), Rail.Shape.NORTH_SOUTH)
        }
        val result = trace(FakeRailWorld(rails), p(1, 0, 0), p(2, 0, 0))
        val endpoints = (result as Either.Right).value
        assertEquals(1, endpoints.size)
        assertEquals(EndpointKind.LOOP, endpoints.single().kind)
    }

    @Test
    fun stopBlockTerminatesExploration() {
        val world = FakeRailWorld(
            rails = straightEastWest(0, 10),
            below = mapOf(p(5, 0, 0) to Material.GOLD_BLOCK),
        )
        val result = trace(world, p(0, 0, 0), p(1, 0, 0), stopBlocks = setOf(Material.GOLD_BLOCK))
        val endpoints = (result as Either.Right).value
        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals(EndpointKind.STOP_BLOCK, endpoint.kind)
        assertEquals(p(5, 0, 0), endpoint.forward.end)
        assertEquals(p(4, 0, 0), endpoint.backward.direction)
    }

    @Test
    fun unlistedBlockDoesNotStopExploration() {
        val world = FakeRailWorld(
            rails = straightEastWest(0, 10),
            below = mapOf(p(5, 0, 0) to Material.GOLD_BLOCK),
        )
        val result = trace(world, p(0, 0, 0), p(1, 0, 0), stopBlocks = setOf(Material.DIAMOND_BLOCK))
        val endpoints = (result as Either.Right).value
        assertEquals(p(10, 0, 0), endpoints.single().forward.end)
        assertEquals(EndpointKind.RAIL_END, endpoints.single().kind)
    }

    @Test
    fun limitExceededReturnsError() {
        val world = FakeRailWorld(straightEastWest(0, 100))
        val result = trace(world, p(0, 0, 0), p(1, 0, 0), limit = 5)
        assertEquals(RailTraceError.ATTACHED_TO_LIMIT, (result as Either.Left).value)
    }

    @Test
    fun maxEndpointsExceededReturnsError() {
        val result = trace(doubleBranchWorld(), p(0, 0, 0), p(1, 0, 0), maxEndpoints = 1)
        assertEquals(RailTraceError.ATTACHED_TO_LIMIT, (result as Either.Left).value)
    }

    @Test
    fun branchDirectionFromOffsets() {
        assertEquals(BranchDirection.EAST, BranchDirection.from(1, 0))
        assertEquals(BranchDirection.WEST, BranchDirection.from(-1, 0))
        assertEquals(BranchDirection.SOUTH, BranchDirection.from(0, 1))
        assertEquals(BranchDirection.NORTH, BranchDirection.from(0, -1))
        assertNull(BranchDirection.from(0, 0))
    }

    @Test
    fun branchDirectionParse() {
        assertEquals(listOf(BranchDirection.EAST, BranchDirection.EAST), BranchDirection.parse("EE"))
        assertEquals(listOf(BranchDirection.NORTH, BranchDirection.SOUTH), BranchDirection.parse("ns"))
        assertEquals(emptyList<BranchDirection>(), BranchDirection.parse(""))
        assertNull(BranchDirection.parse("EX"))
    }
}
