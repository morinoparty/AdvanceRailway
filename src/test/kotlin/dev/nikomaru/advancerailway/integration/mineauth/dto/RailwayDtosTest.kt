/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.integration.mineauth.dto

import dev.nikomaru.advancerailway.utils.Utils.json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * MineAuth エンドポイントが実際に出力する JSON 契約を、ハンドラーと同じ [json] 設定で検証する。
 *
 * エンティティは UUID の `id` と人間可読な `slug` を両方持ち、駅ナンバリングは
 * 駅の単一フィールドではなく所属グループごとの配列（`numberings`）になっている。
 */
class RailwayDtosTest {

    private val stationId = "01890000-0000-7000-8000-000000000001"
    private val groupId = "01890000-0000-7000-8000-0000000000a1"

    @Test
    @DisplayName("StationDto serializes with a nested point and keeps null fields")
    fun stationDtoJsonContract() {
        val dto = StationDto(
            id = stationId,
            slug = "st01",
            name = "Central",
            world = "world",
            point = PointDto(1.0, 64.0, -3.0),
            overrideSize = null,
            color = "#FF7F00",
        )

        val obj = json.parseToJsonElement(json.encodeToString(StationDto.serializer(), dto)).jsonObject

        assertEquals(stationId, obj["id"]!!.jsonPrimitive.content)
        assertEquals("st01", obj["slug"]!!.jsonPrimitive.content)
        assertEquals("Central", obj["name"]!!.jsonPrimitive.content)
        assertEquals("#FF7F00", obj["color"]!!.jsonPrimitive.content)
        // nullable フィールドは省略されず null として出力される。
        assertEquals(JsonNull, obj["overrideSize"])
        // 所属グループが無い駅のナンバリングは空配列。
        assertTrue(obj["numberings"]!!.jsonArray.isEmpty())
        // point はネストされたオブジェクトになる。
        val point = obj["point"]!!.jsonObject
        assertEquals("1.0", point["x"]!!.jsonPrimitive.content)
        assertEquals("64.0", point["y"]!!.jsonPrimitive.content)
        assertEquals("-3.0", point["z"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("StationDto exposes one numbering entry per group the station belongs to")
    fun stationDtoNumberings() {
        val dto = StationDto(
            id = stationId,
            slug = "st01",
            name = "Central",
            world = "world",
            point = PointDto(0.0, 0.0, 0.0),
            overrideSize = null,
            color = "#000000",
            numberings = listOf(
                StationNumberingDto(groupId, "yamanote", "山手線", 0, "JY01"),
                StationNumberingDto(groupId, "chuo", "中央線", 4, null),
            ),
        )

        val obj = json.parseToJsonElement(json.encodeToString(StationDto.serializer(), dto)).jsonObject
        val numberings = obj["numberings"]!!.jsonArray
        assertEquals(2, numberings.size)
        assertEquals("JY01", numberings[0].jsonObject["numbering"]!!.jsonPrimitive.content)
        // 接頭辞を持たないグループでは番号が付かず null になる。
        assertEquals(JsonNull, numberings[1].jsonObject["numbering"])
        assertEquals("4", numberings[1].jsonObject["position"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("RailwayDto round-trips through the handler json config")
    fun railwayDtoRoundTrip() {
        val dto = RailwayDto(
            id = "01890000-0000-7000-8000-0000000000f1",
            slug = "rw01",
            group = null,
            groupSlug = null,
            world = "world",
            lineType = "UP_LINE",
            fromStation = stationId,
            fromStationSlug = "st01",
            toStation = "01890000-0000-7000-8000-000000000002",
            toStationSlug = "st02",
            timeRequired = 120L,
            startPoint = PointDto(0.0, 0.0, 0.0),
            endPoint = PointDto(10.0, 0.0, 5.0),
            flags = "EE",
            lastCheckedAt = "2026-08-24T03:04:05Z",
        )

        val encoded = json.encodeToString(RailwayDto.serializer(), dto)
        val decoded = json.decodeFromString(RailwayDto.serializer(), encoded)

        assertEquals(dto, decoded)
        val obj = json.parseToJsonElement(encoded).jsonObject
        assertEquals(JsonNull, obj["group"])
        assertEquals("120", obj["timeRequired"]!!.jsonPrimitive.content)
        assertEquals("EE", obj["flags"]!!.jsonPrimitive.content)
        assertEquals("2026-08-24T03:04:05Z", obj["lastCheckedAt"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("a railway that has never been verified reports lastCheckedAt as null")
    fun railwayDtoUncheckedIsNull() {
        val dto = RailwayDto(
            id = "01890000-0000-7000-8000-0000000000f2",
            slug = "rw02",
            group = groupId,
            groupSlug = "yamanote",
            world = "world",
            lineType = "UP_DOWN_LINE",
            fromStation = stationId,
            fromStationSlug = "st01",
            toStation = "01890000-0000-7000-8000-000000000002",
            toStationSlug = "st02",
            timeRequired = 60L,
            startPoint = PointDto(0.0, 0.0, 0.0),
            endPoint = PointDto(1.0, 0.0, 0.0),
            flags = "N",
            lastCheckedAt = null,
        )

        val obj = json.parseToJsonElement(json.encodeToString(RailwayDto.serializer(), dto)).jsonObject
        assertEquals(JsonNull, obj["lastCheckedAt"])
    }

    @Test
    @DisplayName("GroupDto carries the numbering prefix and start number")
    fun groupDtoNumbering() {
        val dto = GroupDto(
            id = groupId,
            slug = "yamanote",
            name = "山手線",
            color = "#9ACD32",
            numberingPrefix = "JY",
            numberingStart = 1,
        )

        val obj = json.parseToJsonElement(json.encodeToString(GroupDto.serializer(), dto)).jsonObject
        assertEquals("JY", obj["numberingPrefix"]!!.jsonPrimitive.content)
        assertEquals("1", obj["numberingStart"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("List responses wrap items under a named array")
    fun stationsResponseWrapsItems() {
        val response = StationsResponse(
            listOf(
                StationDto(stationId, "st01", "A", "world", PointDto(0.0, 0.0, 0.0), 1.5, "#000000"),
                StationDto(
                    "01890000-0000-7000-8000-000000000002", "st02", "B", "world",
                    PointDto(1.0, 1.0, 1.0), null, "#FFFFFF",
                ),
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
