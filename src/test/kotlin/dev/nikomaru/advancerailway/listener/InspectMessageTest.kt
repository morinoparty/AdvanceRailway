/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.listener

import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.rail.BranchDirection
import dev.nikomaru.advancerailway.domain.rail.BranchEndpoint
import dev.nikomaru.advancerailway.domain.rail.EndpointKind
import dev.nikomaru.advancerailway.domain.rail.InspectData
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.storage.model.StationData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.awt.Color

/**
 * `/ar inspect` の終端 1 行（[InspectMessage]）のテスト。
 *
 * ここで守りたいのは **`[作成]` のリンクが確実に出ること**。inspect の価値はクリックだけで
 * `ar railway add` を組み立てられる点にあり、実際にレールを敷いた状態でしか動かせない場所に
 * 置いておくと、壊れても気づけないまま出荷されてしまう。
 */
class InspectMessageTest {

    private val start = Point3D(1.0, 64.0, -3.0)
    private val end = Point3D(5.0, 64.0, 10.0)

    private fun station(slug: String, name: String) = StationData(
        id = StationId.new(),
        slug = Slug(slug),
        name = name,
        worldName = "world",
        point = Point3D(0.0, 64.0, 0.0),
        overrideSize = null,
        color = Color.WHITE,
    )

    private fun line(
        from: StationData? = station("fti", "ふれんちとーす島"),
        to: StationData? = station("akmt", "赤松"),
        flags: String? = "EE",
    ) = InspectMessage.endpointLine("<yellow>[flags: EE]</yellow> ", from, to, start, end, flags)

    @Test
    @DisplayName("the line offers a clickable [作成] with a ready-to-run railway add command")
    fun offersCreateLink() {
        val message = line()!!

        assertTrue(message.contains("[作成]"), "[作成] が出ていません: $message")
        assertTrue(
            message.contains("/ar railway add fti_akmt ${start.toPlainString()} ${end.toPlainString()} EE"),
            "組み立てられたコマンドが期待と違います: $message",
        )
    }

    @Test
    @DisplayName("both the display name and the slug are shown for each endpoint")
    fun showsNameAndSlug() {
        val message = line()!!

        assertTrue(message.contains("ふれんちとーす島"), message)
        assertTrue(message.contains("(fti)"), message)
        assertTrue(message.contains("赤松"), message)
        assertTrue(message.contains("(akmt)"), message)
    }

    @Test
    @DisplayName("a station name containing '<' is escaped and does not swallow the [作成] link")
    fun escapesStationNameSoTheLinkSurvives() {
        // MiniMessage は `<` からをタグとして読むため、エスケープを忘れると駅名の後ろ
        // （＝[作成] リンク）が丸ごと表示から消える。
        val message = line(from = station("heart", "<3の駅"))!!

        assertTrue(message.contains("\\<3の駅"), "駅名がエスケープされていません: $message")
        // 生の `<` が残っていないこと（エスケープ済みの `\<` は直前のバックスラッシュで判定する）。
        val raw = message.indexOf("<3の駅")
        assertTrue(raw > 0 && message[raw - 1] == '\\', "駅名の `<` が生のまま残っています: $message")
        assertTrue(message.contains("[作成]"), "エスケープ漏れで [作成] が消えています: $message")
        assertTrue(message.indexOf("[作成]") > raw, "[作成] が駅名より前にあります: $message")
    }

    @Test
    @DisplayName("no [作成] when the flags for that direction could not be derived")
    fun noCreateLinkWithoutFlags() {
        val message = line(flags = null)!!

        assertFalse(message.contains("[作成]"), "フラグ不明なのに [作成] が出ています: $message")
        // 駅の情報自体は出す（どこへ繋がっているかは分かるようにする）。
        assertTrue(message.contains("ふれんちとーす島"), message)
    }

    @Test
    @DisplayName("no [作成] when the flags are empty, which would build an invalid command")
    fun noCreateLinkWithEmptyFlags() {
        assertFalse(line(flags = "")!!.contains("[作成]"))
    }

    @Test
    @DisplayName("a route that returns to its own station is skipped entirely")
    fun skipsSelfReturningRoute() {
        val same = station("fti", "ふれんちとーす島")
        assertNull(InspectMessage.endpointLine("", same, same, start, end, "EE"))
    }

    @Test
    @DisplayName("without a nearby station the coordinates are still reported, with no [作成]")
    fun reportsMissingStation() {
        val message = line(to = null)!!

        assertTrue(message.contains("付近に駅が登録されていません"), message)
        assertFalse(message.contains("[作成]"), message)
        assertTrue(message.contains(start.toPlainString()), message)
    }

    private fun endpoint(kind: EndpointKind) = BranchEndpoint(
        flags = listOf(BranchDirection.EAST),
        kind = kind,
        forward = InspectData(start, start, end),
        backward = InspectData(end, end, start),
    )

    @Test
    @DisplayName("endpoints that loop back onto the same track are kept out of the list")
    fun loopEndpointsAreExcluded() {
        val all = listOf(
            endpoint(EndpointKind.RAIL_END),
            endpoint(EndpointKind.LOOP),
            endpoint(EndpointKind.STOP_BLOCK),
            endpoint(EndpointKind.LOOP),
        )

        val (shown, excluded) = InspectMessage.partitionForDisplay(all)

        assertEquals(2, shown.size)
        assertEquals(2, excluded)
        // 登録できる終端（線路の端・停止ブロック）だけが残る。
        assertEquals(setOf(EndpointKind.RAIL_END, EndpointKind.STOP_BLOCK), shown.map { it.kind }.toSet())
    }

    @Test
    @DisplayName("the excluded count is reported so an empty result is not left unexplained")
    fun excludedCountIsReported() {
        assertNull(InspectMessage.excludedNote(0))
        assertTrue(InspectMessage.excludedNote(3)!!.contains("3 件"))
    }

    @Test
    @DisplayName("the label prefix carries the flags and the endpoint kind")
    fun labelCarriesFlagsAndKind() {
        val message = line()!!
        assertEquals(true, message.startsWith("<yellow>[flags: EE]</yellow> "), message)
    }
}
