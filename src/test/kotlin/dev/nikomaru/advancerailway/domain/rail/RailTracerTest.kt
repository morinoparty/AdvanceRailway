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
    fun switchedJunctionDetectsBranch() {
        // T 字路のポイントが「直進」（EAST_WEST）に切り替わっている状態。
        // 形状だけを見ると南の脚が見えないが、南のレール（NORTH_SOUTH）が
        // こちらへ接続しているため分岐として検出されること。
        val rails = buildMap {
            putAll(straightEastWest(0, 8))
            put(p(4, 0, 1), Rail.Shape.NORTH_SOUTH)
            put(p(4, 0, 2), Rail.Shape.NORTH_SOUTH)
        }
        val result = trace(FakeRailWorld(rails), p(0, 0, 0), p(1, 0, 0))
        val endpoints = (result as Either.Right).value
        assertEquals(2, endpoints.size)
        val byFlags = endpoints.associateBy { it.flags }
        assertEquals(p(8, 0, 0), byFlags[listOf(BranchDirection.EAST)]?.forward?.end)
        assertEquals(p(4, 0, 2), byFlags[listOf(BranchDirection.SOUTH)]?.forward?.end)
    }

    @Test
    fun parallelTrackIsNotABranch() {
        // 隣り合う並行線（互いに向き合っていない）は接続扱いにしない。
        val rails = buildMap {
            putAll(straightEastWest(0, 5, z = 0))
            putAll(straightEastWest(0, 5, z = 1))
        }
        val result = trace(FakeRailWorld(rails), p(0, 0, 0), p(1, 0, 0))
        val endpoints = (result as Either.Right).value
        assertEquals(1, endpoints.size)
        assertEquals(emptyList<BranchDirection>(), endpoints.single().flags)
        assertEquals(p(5, 0, 0), endpoints.single().forward.end)
    }

    @Test
    fun adjacentRailsIncludesSwitchedLeg() {
        // クリック地点がポイント本体の場合も、切替状態に関係なく 3 本の脚が返ること。
        val rails = buildMap {
            putAll(straightEastWest(3, 5))
            put(p(4, 0, 1), Rail.Shape.NORTH_SOUTH)
        }
        val adjacent = RailTracer.adjacentRails(p(4, 0, 0), FakeRailWorld(rails))
        assertEquals(setOf(p(3, 0, 0), p(5, 0, 0), p(4, 0, 1)), adjacent.toSet())
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

    /**
     * 待避線パターン: (3,0,0) で本線（東）と待避線（南）に分かれ、(7,0,0) で合流して
     * (10,0,0) まで続く。合流の先の終端は本線経由 (EE) と待避線経由 (SE) の両方で
     * 到達できるため、どちらの flags でも列挙されること（visited を全経路で共有すると
     * 後着側が合流地点で LOOP 終端になり、片方しか出てこない）。
     */
    private fun passingLoopWorld(): FakeRailWorld {
        val rails = buildMap {
            putAll(straightEastWest(0, 2))
            put(p(3, 0, 0), Rail.Shape.SOUTH_EAST)
            put(p(3, 0, 1), Rail.Shape.NORTH_EAST)
            putAll(straightEastWest(4, 6, z = 1))
            put(p(7, 0, 1), Rail.Shape.NORTH_WEST)
            putAll(straightEastWest(4, 6))
            put(p(7, 0, 0), Rail.Shape.SOUTH_WEST)
            putAll(straightEastWest(8, 10))
        }
        return FakeRailWorld(rails)
    }

    @Test
    fun passingLoopYieldsBothRoutesToFarEndpoint() {
        val result = trace(passingLoopWorld(), p(0, 0, 0), p(1, 0, 0))
        val endpoints = (result as Either.Right).value
        val byFlagString = endpoints.associateBy { it.flagString() }
        // 本線経由・待避線経由の両方で合流先の終端に到達できること。
        // visited は経路ごとに独立しているので、合流地点で後着側が潰れることはない。
        assertEquals(p(10, 0, 0), byFlagString["EE"]?.forward?.end)
        assertEquals(EndpointKind.RAIL_END, byFlagString["EE"]?.kind)
        assertEquals(p(10, 0, 0), byFlagString["SE"]?.forward?.end)
        assertEquals(EndpointKind.RAIL_END, byFlagString["SE"]?.kind)
        // 待避線を回ってから分岐点へ戻る経路は、同じレールを 2 度通るため
        // そこで LOOP として打ち切られる（クリック地点まで戻る終端は列挙されない）。
        assertEquals(EndpointKind.LOOP, byFlagString["ES"]?.kind)
        assertEquals(EndpointKind.LOOP, byFlagString["SW"]?.kind)
        assertEquals(4, endpoints.size)
    }

    @Test
    fun flagPrefixKeepsOnlyTheRequestedDirection() {
        // 分岐点の上をクリックすると西・南・東の 3 方向へ探索が走る。
        // 東だけを見たいときに、出発方角を接頭辞で指定して絞り込めること。
        val world = doubleBranchWorld()
        val all = (RailTracer.traceAll(p(5, 0, 0), world, emptySet(), 1000, 16) as Either.Right).value
        val east = (
            RailTracer.traceAll(p(5, 0, 0), world, emptySet(), 1000, 16, listOf(BranchDirection.EAST))
                as Either.Right
            ).value

        assertTrue(all.size > east.size, "絞り込みで件数が減っていません: ${all.map { it.flagString() }}")
        assertTrue(
            east.all { it.flagString().startsWith("E") },
            "東以外へ出発する経路が残っています: ${east.map { it.flagString() }}",
        )
    }

    @Test
    fun flagPrefixNarrowsDownBranchChoices() {
        val world = doubleBranchWorld()

        // 出発（東）と 1 つ目の分岐（東）を固定する。2 つ目の分岐は指定していないので両方出る。
        val twoFixed = RailTracer.traceAll(
            p(0, 0, 0), world, emptySet(), 1000, 16,
            listOf(BranchDirection.EAST, BranchDirection.EAST),
        )
        assertEquals(listOf("EEE", "EES"), (twoFixed as Either.Right).value.map { it.flagString() }.sorted())

        // 2 つ目の分岐まで指定すれば 1 本に絞れる。
        val allFixed = RailTracer.traceAll(
            p(0, 0, 0), world, emptySet(), 1000, 16,
            listOf(BranchDirection.EAST, BranchDirection.EAST, BranchDirection.EAST),
        )
        assertEquals(listOf("EEE"), (allFixed as Either.Right).value.map { it.flagString() })
    }

    @Test
    fun flagPrefixLongerThanTheTrackStillYieldsThePath() {
        // 分岐が無い一本道に長い接頭辞を渡しても、指定どおり進めている限り終端は出す。
        val world = FakeRailWorld(straightEastWest(0, 10))
        val result = RailTracer.traceAll(
            p(5, 0, 0), world, emptySet(), 1000, 16,
            listOf(BranchDirection.EAST, BranchDirection.EAST, BranchDirection.EAST),
        )
        val endpoints = (result as Either.Right).value

        assertEquals(listOf("E"), endpoints.map { it.flagString() })
        assertEquals(p(10, 0, 0), endpoints.single().forward.end)
    }

    @Test
    fun noEndpointRevisitsTheClickedRail() {
        // 一度通ったレールは 2 度通らない規則により、折り返してクリック地点へ戻る経路は
        // 終端として現れない（戻ってくる手前で LOOP 打ち切りになる）。
        val result = trace(passingLoopWorld(), p(0, 0, 0), p(1, 0, 0))
        val endpoints = (result as Either.Right).value

        assertTrue(
            endpoints.none { it.forward.end == p(0, 0, 0) },
            "クリック地点へ戻る終端が残っています: ${endpoints.map { it.flagString() to it.forward.end }}",
        )
    }

    @Test
    fun traceAllIncludesDepartureDirectionInFlags() {
        val world = FakeRailWorld(straightEastWest(0, 10))
        val result = RailTracer.traceAll(p(5, 0, 0), world, emptySet(), 1000, 16)
        val endpoints = (result as Either.Right).value
        assertEquals(2, endpoints.size)
        val byFlagString = endpoints.associateBy { it.flagString() }
        assertEquals(p(10, 0, 0), byFlagString["E"]?.forward?.end)
        assertEquals(p(0, 0, 0), byFlagString["W"]?.forward?.end)
    }

    @Test
    fun traceAllPrependsDepartureToBranchFlags() {
        val rails = buildMap {
            putAll(straightEastWest(0, 8))
            put(p(4, 0, 1), Rail.Shape.NORTH_SOUTH)
            put(p(4, 0, 2), Rail.Shape.NORTH_SOUTH)
        }
        val result = RailTracer.traceAll(p(0, 0, 0), FakeRailWorld(rails), emptySet(), 1000, 16)
        val endpoints = (result as Either.Right).value
        val byFlagString = endpoints.associateBy { it.flagString() }
        assertEquals(p(8, 0, 0), byFlagString["EE"]?.forward?.end)
        assertEquals(p(4, 0, 2), byFlagString["ES"]?.forward?.end)
    }

    @Test
    fun findRoutesOnStraightLine() {
        val world = FakeRailWorld(straightEastWest(0, 10))
        val result = RailTracer.findRoutes(p(0, 0, 0), p(10, 0, 0), world, emptySet(), 1000, 16)
        val routes = (result as Either.Right).value
        assertEquals(1, routes.size)
        assertEquals("E", routes.single().flagString())
        assertEquals(10, routes.single().steps)
    }

    @Test
    fun findRoutesEnumeratesAllRoutesThroughPassingLoop() {
        val result = RailTracer.findRoutes(p(0, 0, 0), p(10, 0, 0), passingLoopWorld(), emptySet(), 1000, 16)
        val routes = (result as Either.Right).value
        val byFlagString = routes.associateBy { it.flagString() }
        // 先頭は出発方角 (E)、以降が (3,0,0)・(7,0,0) の分岐で選ぶ方角
        assertEquals(setOf("EEE", "ESE"), byFlagString.keys)
        assertEquals(10, byFlagString["EEE"]?.steps)
        assertEquals(12, byFlagString["ESE"]?.steps)
    }

    @Test
    fun findRoutesReturnsEmptyWhenUnreachable() {
        val world = FakeRailWorld(straightEastWest(0, 10))
        val result = RailTracer.findRoutes(p(0, 0, 0), p(20, 0, 5), world, emptySet(), 1000, 16)
        assertEquals(emptyList<RouteCandidate>(), (result as Either.Right).value)
    }

    @Test
    fun findRoutesDiscardsRoutesThroughStopBlock() {
        val world = FakeRailWorld(
            rails = straightEastWest(0, 10),
            below = mapOf(p(5, 0, 0) to Material.GOLD_BLOCK),
        )
        val blocked = RailTracer.findRoutes(p(0, 0, 0), p(10, 0, 0), world, setOf(Material.GOLD_BLOCK), 1000, 16)
        assertEquals(emptyList<RouteCandidate>(), (blocked as Either.Right).value)
        // 終点自体が stopBlock 上なら到達扱い
        val toStop = RailTracer.findRoutes(p(0, 0, 0), p(5, 0, 0), world, setOf(Material.GOLD_BLOCK), 1000, 16)
        assertEquals("E", (toStop as Either.Right).value.single().flagString())
    }

    @Test
    fun branchDirectionFromPoints() {
        assertEquals(BranchDirection.EAST, BranchDirection.fromPoints(p(0, 0, 0), p(1, 0, 0)))
        assertEquals(BranchDirection.WEST, BranchDirection.fromPoints(p(0, 0, 0), p(-1, 0, 0)))
        assertEquals(BranchDirection.SOUTH, BranchDirection.fromPoints(p(0, 0, 0), p(0, 0, 1)))
        assertEquals(BranchDirection.NORTH, BranchDirection.fromPoints(p(0, 0, 0), p(0, -1, -1)))
        assertNull(BranchDirection.fromPoints(p(0, 0, 0), p(0, 0, 0)))
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
