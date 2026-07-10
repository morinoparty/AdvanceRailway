/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.mineauth.dto

import dev.nikomaru.advancerailway.utils.Utils.json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * MineAuth エンドポイントが実際に出力する JSON 契約を、ハンドラーと同じ [json] 設定で検証する。
 */
class RailwayDtosTest {

    @Test
    @DisplayName("StationDto serializes with a nested point and keeps null fields")
    fun stationDtoJsonContract() {
        val dto = StationDto(
            id = "st01",
            name = "Central",
            numbering = null,
            world = "world",
            point = PointDto(1.0, 64.0, -3.0),
            overrideSize = null,
            color = "#FF7F00",
        )

        val obj = json.parseToJsonElement(json.encodeToString(StationDto.serializer(), dto)).jsonObject

        assertEquals("st01", obj["id"]!!.jsonPrimitive.content)
        assertEquals("Central", obj["name"]!!.jsonPrimitive.content)
        assertEquals("#FF7F00", obj["color"]!!.jsonPrimitive.content)
        // nullable フィールドは省略されず null として出力される。
        assertEquals(JsonNull, obj["numbering"])
        assertEquals(JsonNull, obj["overrideSize"])
        // point はネストされたオブジェクトになる。
        val point = obj["point"]!!.jsonObject
        assertEquals("1.0", point["x"]!!.jsonPrimitive.content)
        assertEquals("64.0", point["y"]!!.jsonPrimitive.content)
        assertEquals("-3.0", point["z"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("RailwayDto round-trips through the handler json config")
    fun railwayDtoRoundTrip() {
        val dto = RailwayDto(
            id = "rw01",
            group = null,
            world = "world",
            lineType = "UP_LINE",
            fromStation = "st01",
            toStation = "st02",
            timeRequired = 120L,
            startPoint = PointDto(0.0, 0.0, 0.0),
            endPoint = PointDto(10.0, 0.0, 5.0),
            directionPoint = PointDto(1.0, 0.0, 0.0),
        )

        val encoded = json.encodeToString(RailwayDto.serializer(), dto)
        val decoded = json.decodeFromString(RailwayDto.serializer(), encoded)

        assertEquals(dto, decoded)
        val obj = json.parseToJsonElement(encoded).jsonObject
        assertEquals(JsonNull, obj["group"])
        assertEquals("120", obj["timeRequired"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("List responses wrap items under a named array")
    fun stationsResponseWrapsItems() {
        val response = StationsResponse(
            listOf(
                StationDto("st01", "A", "N1", "world", PointDto(0.0, 0.0, 0.0), 1.5, "#000000"),
                StationDto("st02", "B", null, "world", PointDto(1.0, 1.0, 1.0), null, "#FFFFFF"),
            )
        )

        val obj = json.parseToJsonElement(
            json.encodeToString(StationsResponse.serializer(), response)
        ).jsonObject
        val stations = obj["stations"]!!
        // "stations" キー配下に配列としてまとまっていること。
        assertTrue(stations.toString().contains("st01"))
        assertTrue(stations.toString().contains("st02"))
        assertEquals(2, response.stations.size)
    }
}
