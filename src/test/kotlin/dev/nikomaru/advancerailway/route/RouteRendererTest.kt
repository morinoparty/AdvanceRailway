/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.route

import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.RailwayId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [RouteRenderer.groupLegs] のまとめ表示ロジックの単体テスト。
 * Bukkit / Koin に依存しない純粋ロジックなので、[RenderedLeg] を直接組んで検証する。
 */
class RouteRendererTest {

    private var counter = 0

    /** テスト用の区間を組む。既定はレール区間。 */
    private fun leg(
        from: String,
        to: String,
        seconds: Long,
        group: String?,
        railway: String? = "rw${counter++}",
        mode: TravelMode = TravelMode.RAIL,
    ): RenderedLeg = RenderedLeg(
        index = counter,
        fromLabel = from,
        toLabel = to,
        mode = mode,
        lineLabel = group,
        railwayId = railway?.let { RailwayId(it) },
        group = group?.let { GroupId(it) },
        timeSeconds = seconds,
        minutes = RouteRenderer.minutes(seconds),
    )

    @Test
    @DisplayName("連続する同一路線のレール区間を 1 セグメントにまとめる")
    fun mergesConsecutiveSameLine() {
        val legs = listOf(
            leg("岩間", "青原", 48, "seika"),
            leg("青原", "城下町", 60, "seika"),
            leg("城下町", "葉吹", 60, "seika"),
        )

        val segments = RouteRenderer.groupLegs(legs)

        assertEquals(1, segments.size)
        val seg = segments.first()
        assertEquals(1, seg.index)
        assertEquals("岩間", seg.fromLabel)
        assertEquals("葉吹", seg.toLabel)
        assertEquals(3, seg.legCount)
        assertEquals("seika", seg.lineLabel)
        // 秒を合算してから丸める: ceil(168 / 6) / 10 = 2.8
        assertEquals(2.8, seg.minutes)
        // まとめ行は路線をまたぐため railwayId を持たない（[詳細] リンクなし）。
        assertNull(seg.railwayId)
    }

    @Test
    @DisplayName("異なる路線・徒歩は別セグメントに分かれ、番号は振り直される")
    fun splitsDifferentLinesAndWalks() {
        val legs = listOf(
            leg("A", "B", 60, "line1"),
            leg("B", "C", 60, "line2"),
            leg("C", "D", 30, group = null, railway = null, mode = TravelMode.WALK),
            leg("D", "E", 60, "line2"),
        )

        val segments = RouteRenderer.groupLegs(legs)

        assertEquals(4, segments.size)
        assertEquals(listOf(1, 2, 3, 4), segments.map { it.index })
        assertEquals(TravelMode.WALK, segments[2].mode)
        assertNull(segments[2].lineLabel)
        // line2 は非連続なのでまとめない。
        assertEquals("line2", segments[1].lineLabel)
        assertEquals("line2", segments[3].lineLabel)
    }

    @Test
    @DisplayName("単一区間のセグメントは railwayId（[詳細] リンク用）を残す")
    fun singleLegKeepsRailwayId() {
        val segments = RouteRenderer.groupLegs(listOf(leg("A", "B", 114, "line", railway = "rw01")))

        assertEquals(1, segments.size)
        assertEquals("rw01", segments.first().railwayId?.value)
        assertEquals(1, segments.first().legCount)
    }

    @Test
    @DisplayName("グループ未設定（無所属）のレール区間は連続していてもまとめない")
    fun doesNotMergeUnaffiliatedRail() {
        val legs = listOf(
            leg("A", "B", 60, group = null),
            leg("B", "C", 60, group = null),
        )

        val segments = RouteRenderer.groupLegs(legs)

        assertEquals(2, segments.size)
    }
}
