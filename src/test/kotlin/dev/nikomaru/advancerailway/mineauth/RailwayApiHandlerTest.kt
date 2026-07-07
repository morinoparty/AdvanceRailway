/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.mineauth

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.Line3D
import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.file.data.GroupData
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.data.StationData
import dev.nikomaru.advancerailway.file.type.LineType
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.utils.Utils.json
import io.ktor.http.ContentType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bukkit.World
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus
import java.awt.Color
import java.io.File
import java.nio.file.Files

/**
 * [RailwayApiHandler] を MockBukkit の実サーバ相当環境で駆動する結合テスト。
 * データフォルダへ実際に JSON を書き出し、ファイル読み取り〜DTO 変換〜JSON 応答までを検証する。
 *
 * StationUtils / RailwayUtils / GroupUtils は `object` シングルトンで、`plugin` を
 * `by inject()` で JVM 生存期間中キャッシュする（かつ 3 種とも StationUtils.plugin 経由で読む）。
 * そのためテストごとに mock/データフォルダを差し替えると最初のフォルダが固定されてしまう。
 * これを避けるため、[TestInstance.Lifecycle.PER_CLASS] でサーバ・プラグイン・フィクスチャを
 * クラス全体で 1 度だけ用意し、各テストは read-only とする。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RailwayApiHandlerTest {

    private lateinit var server: ServerMock
    private lateinit var world: World
    private lateinit var dataFolder: File
    private val handler = RailwayApiHandler()

    @BeforeAll
    fun setup() {
        // 他クラスが Koin を起動したまま終わっていても安全に起動できるようにする。
        if (GlobalContext.getOrNull() != null) stopKoin()

        server = MockBukkit.mock()
        world = server.addSimpleWorld("world")

        dataFolder = Files.createTempDirectory("advancerailway-test").toFile()

        // AdvanceRailway 本体は onEnableAsync が重いため、dataFolder のみを差し替えたモックを使う。
        val plugin = mockk<AdvanceRailway>(relaxed = true)
        every { plugin.dataFolder } returns dataFolder

        startKoin {
            modules(module { single<AdvanceRailway> { plugin } })
        }

        writeFixtures()
    }

    @AfterAll
    fun tearDown() {
        stopKoin()
        MockBukkit.unmock()
        dataFolder.deleteRecursively()
    }

    @Test
    @DisplayName("getStation returns the station as application/json")
    fun getStationReturnsJson() = runBlocking {
        val response = handler.getStation("st01")

        assertEquals(ContentType.Application.Json, response.contentType)
        val obj = json.parseToJsonElement(response.text).jsonObject
        assertEquals("st01", obj["id"]!!.jsonPrimitive.content)
        assertEquals("Central", obj["name"]!!.jsonPrimitive.content)
        assertEquals("world", obj["world"]!!.jsonPrimitive.content)
        assertEquals("#FF7F00", obj["color"]!!.jsonPrimitive.content)
        assertEquals("64.0", obj["point"]!!.jsonObject["y"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("getStation throws NOT_FOUND for a missing id")
    fun getStationNotFound() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getStation("does-not-exist") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
    }

    @Test
    @DisplayName("listStations returns every station on disk")
    fun listStationsReturnsAll() = runBlocking {
        val response = handler.listStations()

        val stations = json.parseToJsonElement(response.text).jsonObject["stations"]!!.jsonArray
        assertEquals(2, stations.size)
        val ids = stations.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
        assertEquals(setOf("st01", "st02"), ids)
    }

    @Test
    @DisplayName("getRailway maps line/stations/points to the DTO")
    fun getRailwayReturnsJson() = runBlocking {
        val response = handler.getRailway("rw01")

        val obj = json.parseToJsonElement(response.text).jsonObject
        assertEquals("rw01", obj["id"]!!.jsonPrimitive.content)
        assertEquals("UP_LINE", obj["lineType"]!!.jsonPrimitive.content)
        assertEquals("st01", obj["fromStation"]!!.jsonPrimitive.content)
        assertEquals("st02", obj["toStation"]!!.jsonPrimitive.content)
        assertEquals("120", obj["timeRequired"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("listRailways returns every railway on disk")
    fun listRailwaysReturnsAll() = runBlocking {
        val response = handler.listRailways()

        val railways = json.parseToJsonElement(response.text).jsonObject["railways"]!!.jsonArray
        assertEquals(1, railways.size)
        assertEquals("rw01", railways.first().jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("getGroup returns the group with a hex color")
    fun getGroupReturnsJson() = runBlocking {
        val response = handler.getGroup("g1")

        val obj = json.parseToJsonElement(response.text).jsonObject
        assertEquals("g1", obj["id"]!!.jsonPrimitive.content)
        assertEquals("Yamanote", obj["name"]!!.jsonPrimitive.content)
        assertEquals("#00FF00", obj["color"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("getGroup throws NOT_FOUND for a missing id")
    fun getGroupNotFound() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getGroup("missing") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
    }

    /**
     * テスト用の駅・路線・グループを実際のシリアライズ形式で dataFolder へ書き出す。
     */
    private fun writeFixtures() {
        writeStation("st01", "Central", Point3D(1.0, 64.0, -3.0), Color(255, 127, 0))
        writeStation("st02", "North", Point3D(5.0, 64.0, 10.0), Color(0, 0, 255))

        val railway = RailwayData(
            id = RailwayId("rw01"),
            group = GroupId("g1"),
            world = world,
            lineType = LineType.UP_LINE,
            line = Line3D(Point3D(1.0, 64.0, -3.0), Point3D(5.0, 64.0, 10.0)),
            fromStation = StationId("st01"),
            toStation = StationId("st02"),
            timeRequired = 120L,
            startPoint = Point3D(1.0, 64.0, -3.0),
            endPoint = Point3D(5.0, 64.0, 10.0),
            directionPoint = Point3D(2.0, 64.0, -3.0),
        )
        write("railways", "rw01", json.encodeToString(railway))

        val group = GroupData(GroupId("g1"), "Yamanote", Color(0, 255, 0))
        write("groups", "g1", json.encodeToString(group))
    }

    private fun writeStation(id: String, name: String, point: Point3D, color: Color) {
        val station = StationData(
            stationId = StationId(id),
            name = name,
            numbering = null,
            world = world,
            point = point,
            overrideSize = null,
            color = color,
        )
        write("stations", id, json.encodeToString(station))
    }

    private fun write(type: String, id: String, content: String) {
        val file = dataFolder.resolve("data").resolve(type).resolve("$id.json")
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}
