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
import dev.nikomaru.advancerailway.error.DataSearchError
import dev.nikomaru.advancerailway.file.data.GroupData
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.data.StationData
import dev.nikomaru.advancerailway.file.type.LineType
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.utils.GroupUtils
import dev.nikomaru.advancerailway.utils.RailwayUtils
import dev.nikomaru.advancerailway.utils.StationUtils
import dev.nikomaru.advancerailway.utils.Utils.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.bukkit.Location
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
        // 駅の無いワールド（StationUtils.nearStation の NOT_FOUND ケース用）。
        server.addSimpleWorld("world_the_end")
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
        assertEquals("Central", route.fromName)
        assertEquals("st02", route.to)
        assertEquals("North", route.toName)
        assertEquals(2L, route.totalTime)
        assertEquals(listOf("st01", "st02"), route.stations)
        assertEquals(1, route.legs.size)
        val leg = route.legs.first()
        assertEquals("RAIL", leg.mode)
        assertEquals("rw01", leg.railway)
        assertEquals("g1", leg.group)
        assertEquals("Yamanote", leg.line) // the line's display name, not the group id
        assertEquals("Central", leg.fromName)
        assertEquals("North", leg.toName)
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
        assertNull(last.line) // walk legs have no line name
        assertEquals("st03", last.to)
        assertEquals("Isolated", last.toName) // station name resolved on the walk leg too
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

    @Test
    @DisplayName("stationRailways returns railways touching the station")
    fun stationRailwaysReturnsTouching() = runBlocking {
        val response = handler.stationRailways("st01")
        assertEquals(1, response.railways.size)
        assertEquals("rw01", response.railways.first().id)
    }

    @Test
    @DisplayName("stationRailways returns empty for a station with no railways")
    fun stationRailwaysEmptyForIsolated() = runBlocking {
        assertTrue(handler.stationRailways("st03").railways.isEmpty())
    }

    @Test
    @DisplayName("stationRailways throws NOT_FOUND for an unknown station")
    fun stationRailwaysNotFound() {
        val error = assertThrows<HttpError> { runBlocking { handler.stationRailways("missing") } }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
    }

    @Test
    @DisplayName("groupRailways returns railways belonging to the group")
    fun groupRailwaysReturnsMembers() = runBlocking {
        val response = handler.groupRailways("g1")
        assertEquals(1, response.railways.size)
        assertEquals("rw01", response.railways.first().id)
    }

    @Test
    @DisplayName("groupRailways throws NOT_FOUND for an unknown group")
    fun groupRailwaysNotFound() {
        val error = assertThrows<HttpError> { runBlocking { handler.groupRailways("missing") } }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
    }

    @Test
    @DisplayName("nearestStation returns the closest station in the world")
    fun nearestStationReturnsClosest() = runBlocking {
        // (2,-3) is nearest to st01 (1,64,-3); y is ignored.
        val station = handler.nearestStation("world", 2.0, -3.0)
        assertEquals("st01", station.id)
    }

    @Test
    @DisplayName("nearestStation only considers stations in the requested world")
    fun nearestStationRespectsWorld() = runBlocking {
        // In the nether only nt01 exists, so it wins regardless of coordinates.
        val station = handler.nearestStation("world_nether", 999.0, 999.0)
        assertEquals("nt01", station.id)
    }

    @Test
    @DisplayName("nearestStation throws NOT_FOUND (no_station) when the world has no stations")
    fun nearestStationNoStation() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.nearestStation("world_the_end", 0.0, 0.0) }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
        assertEquals("no_station", error.code)
    }

    // --- StationUtils.nearStation（RailClickEvent が使う最寄り駅判定）---------------------------------

    @Test
    @DisplayName("nearStation returns the closest station in the clicked location's world")
    fun nearStationReturnsClosest() = runBlocking {
        // (2,-3) は st01 (1,64,-3) に最も近い（y は無視）。
        val result = StationUtils.nearStation(Location(world, 2.0, 64.0, -3.0))
        assertEquals("st01", result.getOrNull()?.value)
    }

    @Test
    @DisplayName("nearStation never matches a station in another dimension")
    fun nearStationExcludesOtherWorld() = runBlocking {
        // 別ワールド nt01 と同座標 (0,0) をオーバーワールドでクリックしても nt01 は候補外。
        // 同一ワールドの最寄り st01 が返る。
        val result = StationUtils.nearStation(Location(world, 0.0, 64.0, 0.0))
        assertEquals("st01", result.getOrNull()?.value)
    }

    @Test
    @DisplayName("nearStation returns NOT_FOUND when the clicked world has no stations")
    fun nearStationNoStationInWorld() = runBlocking {
        val theEnd = server.getWorld("world_the_end")!!
        val result = StationUtils.nearStation(Location(theEnd, 0.0, 64.0, 0.0))
        assertNull(result.getOrNull())
    }

    // --- *Utils.get*Data の直接カバレッジ（NOT_FOUND / 破損ファイル分離）--------------------------------

    @Test
    @DisplayName("getStationData/getGroupData/getRailwayData return the stored entity")
    fun getDataHappyPath() = runBlocking {
        assertEquals("Central", StationUtils.getStationData(StationId("st01")).getOrNull()?.name)
        assertEquals("Yamanote", GroupUtils.getGroupData(GroupId("g1")).getOrNull()?.name)
        assertEquals("st01", RailwayUtils.getRailwayData(RailwayId("rw01")).getOrNull()?.fromStation?.value)
    }

    @Test
    @DisplayName("get*Data returns NOT_FOUND for a missing id")
    fun getDataNotFound() = runBlocking {
        assertEquals(DataSearchError.NOT_FOUND, StationUtils.getStationData(StationId("nope")).leftOrNull())
        assertEquals(DataSearchError.NOT_FOUND, GroupUtils.getGroupData(GroupId("nope")).leftOrNull())
        assertEquals(DataSearchError.NOT_FOUND, RailwayUtils.getRailwayData(RailwayId("nope")).leftOrNull())
    }

    @Test
    @DisplayName("getStationData maps a corrupt on-disk file to DESERIALIZATION_FAILED (crash isolation)")
    fun getDataCorruptFile() = runBlocking {
        // 破損ファイルは他テストへ漏らさないよう、このテスト内だけで作成・削除する。
        val broken = dataFolder.resolve("data").resolve("stations").resolve("brokenst.json")
        broken.writeText("{ this is not valid json")
        try {
            val result = StationUtils.getStationData(StationId("brokenst"))
            assertEquals(DataSearchError.DESERIALIZATION_FAILED, result.leftOrNull())
        } finally {
            broken.delete()
        }
    }

    @Test
    @DisplayName("stats returns the network counts")
    fun statsReturnsCounts() = runBlocking {
        val stats = handler.stats()
        assertEquals(4, stats.stations) // st01, st02, st03, nt01
        assertEquals(1, stats.railways)
        assertEquals(1, stats.groups)
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
