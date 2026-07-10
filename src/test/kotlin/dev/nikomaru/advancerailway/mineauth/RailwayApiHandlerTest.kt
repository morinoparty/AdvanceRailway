/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.bukkit.World
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        // 別ワールド（経路探索のクロスワールド NoPath ケース用）。
        server.addSimpleWorld("world_nether")

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
    @DisplayName("getStation returns the station DTO")
    fun getStationReturnsDto() = runBlocking {
        val station = handler.getStation("st01")

        assertEquals("st01", station.id)
        assertEquals("Central", station.name)
        assertEquals("world", station.world)
        assertEquals("#FF7F00", station.color)
        assertEquals(64.0, station.point.y)
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
    @DisplayName("getStation rejects a path-traversal id with NOT_FOUND before touching disk")
    fun getStationRejectsInvalidId() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getStation("../config") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
    }

    @Test
    @DisplayName("listStations returns every station on disk")
    fun listStationsReturnsAll() = runBlocking {
        val response = handler.listStations()

        assertEquals(4, response.stations.size)
        val ids = response.stations.map { it.id }.toSet()
        assertEquals(setOf("st01", "st02", "st03", "nt01"), ids)
    }

    @Test
    @DisplayName("getRailway maps line/stations/points to the DTO")
    fun getRailwayReturnsDto() = runBlocking {
        val railway = handler.getRailway("rw01")

        assertEquals("rw01", railway.id)
        assertEquals("UP_LINE", railway.lineType)
        assertEquals("st01", railway.fromStation)
        assertEquals("st02", railway.toStation)
        assertEquals(2L, railway.timeRequired)
    }

    @Test
    @DisplayName("listRailways returns every railway on disk")
    fun listRailwaysReturnsAll() = runBlocking {
        val response = handler.listRailways()

        assertEquals(1, response.railways.size)
        assertEquals("rw01", response.railways.first().id)
    }

    @Test
    @DisplayName("getRailway throws NOT_FOUND for a missing id")
    fun getRailwayNotFound() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getRailway("does-not-exist") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
    }

    @Test
    @DisplayName("getRailway rejects a URL-encoded path-traversal id with NOT_FOUND before touching disk")
    fun getRailwayRejectsInvalidId() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getRailway("..%2F..%2Fx") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
    }

    @Test
    @DisplayName("listGroups returns every group on disk")
    fun listGroupsReturnsAll() = runBlocking {
        val response = handler.listGroups()

        assertEquals(1, response.groups.size)
        assertEquals("g1", response.groups.first().id)
    }

    @Test
    @DisplayName("getGroup returns the group with a hex color")
    fun getGroupReturnsDto() = runBlocking {
        val group = handler.getGroup("g1")

        assertEquals("g1", group.id)
        assertEquals("Yamanote", group.name)
        assertEquals("#00FF00", group.color)
    }

    @Test
    @DisplayName("getGroup throws NOT_FOUND for a missing id")
    fun getGroupNotFound() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getGroup("missing") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
    }

    @Test
    @DisplayName("getGroup rejects a relative path-traversal id with NOT_FOUND before touching disk")
    fun getGroupRejectsInvalidId() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getGroup("../../x") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
    }

    @Test
    @DisplayName("getRoute picks the fast rail leg between connected stations")
    fun getRouteReturnsRoute() = runBlocking {
        val route = handler.getRoute("st01", "st02")

        assertEquals("st01", route.from)
        assertEquals("st02", route.to)
        assertEquals(2L, route.totalTime)
        assertEquals(listOf("st01", "st02"), route.stations)
        assertEquals(1, route.legs.size)
        assertEquals("RAIL", route.legs.first().mode)
        assertEquals("rw01", route.legs.first().railway)
        assertEquals("g1", route.legs.first().group)
    }

    @Test
    @DisplayName("getRoute is undirected: the reverse direction also routes by rail")
    fun getRouteReverse() = runBlocking {
        val route = handler.getRoute("st02", "st01")

        assertEquals(listOf("st02", "st01"), route.stations)
        assertEquals("RAIL", route.legs.first().mode)
        assertEquals(2L, route.totalTime)
    }

    @Test
    @DisplayName("getRoute reaches a rail-disconnected station via rail then a final walk")
    fun getRouteWalkingFallback() = runBlocking {
        // st03 has no railway; the cheapest path is rail st01->st02 then a short walk to st03.
        val route = handler.getRoute("st01", "st03")

        assertEquals(listOf("st01", "st02", "st03"), route.stations)
        assertEquals("RAIL", route.legs.first().mode)
        val last = route.legs.last()
        assertEquals("WALK", last.mode)
        assertNull(last.railway)
        assertNull(last.group) // the rail group must not leak onto the walk leg
        assertEquals("st03", last.to)
        assertEquals(4L, route.totalTime)
    }

    @Test
    @DisplayName("getRoute throws BAD_REQUEST when from equals to")
    fun getRouteSameStation() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getRoute("st01", "st01") }
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.status)
        assertEquals("same_station", error.code)
    }

    @Test
    @DisplayName("getRoute throws NOT_FOUND (no_route) for a station in another world with no bridging rail")
    fun getRouteNoPath() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getRoute("st01", "nt01") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
        assertEquals("no_route", error.code)
    }

    @Test
    @DisplayName("getRoute throws NOT_FOUND (station_not_found) for an unknown station")
    fun getRouteUnknownStation() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getRoute("st01", "does-not-exist") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
        assertEquals("station_not_found", error.code)
    }

    @Test
    @DisplayName("getRoute rejects a path-traversal station id before touching disk")
    fun getRouteRejectsInvalidId() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getRoute("../config", "st02") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
        assertEquals("station_not_found", error.code)
    }

    /**
     * テスト用の駅・路線・グループを実際のシリアライズ形式で dataFolder へ書き出す。
     */
    private fun writeFixtures() {
        writeStation("st01", "Central", Point3D(1.0, 64.0, -3.0), Color(255, 127, 0))
        writeStation("st02", "North", Point3D(5.0, 64.0, 10.0), Color(0, 0, 255))
        // st03 は路線に接続されない孤立駅（同一ワールドなので徒歩フォールバックで到達できる）。
        writeStation("st03", "Isolated", Point3D(9.0, 64.0, 20.0), Color(128, 128, 128))
        // nt01 は別ワールドの駅（レール未接続 → クロスワールドで NoPath）。
        writeStation("nt01", "Nether", Point3D(0.0, 64.0, 0.0), Color(200, 0, 0), server.getWorld("world_nether")!!)

        // rw01 の所要時間は徒歩（約 3 秒）より速い 2 秒とし、st01<->st02 ではレールが選ばれるようにする。
        val railway = RailwayData(
            id = RailwayId("rw01"),
            group = GroupId("g1"),
            world = world,
            lineType = LineType.UP_LINE,
            line = Line3D(Point3D(1.0, 64.0, -3.0), Point3D(5.0, 64.0, 10.0)),
            fromStation = StationId("st01"),
            toStation = StationId("st02"),
            timeRequired = 2L,
            startPoint = Point3D(1.0, 64.0, -3.0),
            endPoint = Point3D(5.0, 64.0, 10.0),
            directionPoint = Point3D(2.0, 64.0, -3.0),
        )
        write("railways", "rw01", json.encodeToString(railway))

        val group = GroupData(GroupId("g1"), "Yamanote", Color(0, 255, 0))
        write("groups", "g1", json.encodeToString(group))
    }

    private fun writeStation(id: String, name: String, point: Point3D, color: Color, inWorld: World = world) {
        val station = StationData(
            stationId = StationId(id),
            name = name,
            numbering = null,
            world = inWorld,
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
