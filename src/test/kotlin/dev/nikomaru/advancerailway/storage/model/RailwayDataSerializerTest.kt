/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.model

import dev.nikomaru.advancerailway.utils.Utils.json
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [RailwayDataSerializer] のバージョン判別（Bukkit 非依存部分）のテスト。
 * 実ファイルのデコード経路は RailwayApiHandlerTest のフィクスチャがカバーする。
 */
class RailwayDataSerializerTest {

    private fun select(jsonText: String) =
        RailwayDataSerializer.selectDeserializer(json.parseToJsonElement(jsonText))

    @Test
    @DisplayName("version:3 selects V3")
    fun versionFieldSelectsV3() {
        assertEquals(RailwayData.V3.serializer(), select("""{"version": 3, "flags": "EE"}"""))
    }

    @Test
    @DisplayName("flags without version selects V2")
    fun flagsWithoutVersionSelectsV2() {
        assertEquals(RailwayData.V2.serializer(), select("""{"flags": ["EAST"]}"""))
    }

    @Test
    @DisplayName("neither version nor flags selects V1")
    fun bareObjectSelectsV1() {
        assertEquals(RailwayData.V1.serializer(), select("""{"id": "a_b"}"""))
    }

    @Test
    @DisplayName("unknown version throws instead of guessing")
    fun unknownVersionThrows() {
        assertThrows<SerializationException> { select("""{"version": 4}""") }
    }
}
