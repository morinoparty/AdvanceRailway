/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.integration.mineauth

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.domain.geometry.Line3D
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.domain.service.StationUtils
import dev.nikomaru.advancerailway.integration.mineauth.dto.CreateGroupRequest
import dev.nikomaru.advancerailway.integration.mineauth.dto.CreateStationRequest
import dev.nikomaru.advancerailway.integration.mineauth.dto.PointDto
import dev.nikomaru.advancerailway.integration.mineauth.dto.ReplaceGroupStationsRequest
import dev.nikomaru.advancerailway.integration.mineauth.dto.UpdateGroupRequest
import dev.nikomaru.advancerailway.integration.mineauth.dto.UpdateRailwayRequest
import dev.nikomaru.advancerailway.integration.mineauth.dto.UpdateStationRequest
import dev.nikomaru.advancerailway.storage.database.DatabaseInitializer
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.ConfigData
import dev.nikomaru.advancerailway.storage.model.GroupData
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.storage.model.StationData
import dev.nikomaru.advancerailway.storage.type.LineType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.bukkit.Location
import org.bukkit.World
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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
import party.morino.mineauth.api.http.Response
import xyz.jpenilla.squaremap.api.SimpleLayerProvider
import java.awt.Color
import java.nio.file.Files

/**
 * [RailwayApiHandler] を MockBukkit の実サーバ相当環境と実データベースで駆動する結合テスト。
 *
 * データはテンポラリの SQLite ファイルに入れ、リポジトリ経由の読み書き〜DTO 変換までを検証する。
 * Koin の `object` シングルトン（StationUtils / MapRenderer）は注入結果を JVM 生存期間中キャッシュするため、
 * サーバ・プラグイン・データベースは [TestInstance.Lifecycle.PER_CLASS] でクラス全体に 1 度だけ用意する。
 * 書き込み系テストがデータを変えるので、フィクスチャは各テスト前に入れ直す。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RailwayApiHandlerTest {

    private lateinit var server: ServerMock
    private lateinit var world: World
    private lateinit var dataFolder: java.io.File
    private val handler = RailwayApiHandler()
    private val stationRepository = StationRepository()
    private val railwayRepository = RailwayRepository()
    private val groupRepository = GroupRepository()

    @BeforeAll
    fun setup() {
        // 他クラスが Koin を起動したまま終わっていても安全に起動できるようにする。
        if (GlobalContext.getOrNull() != null) stopKoin()

        server = MockBukkit.mock()
        world = server.addSimpleWorld("world")
        // 駅の無いワールド（nearestStation の no_station ケース用）。
        server.addSimpleWorld("world_the_end")
        // 別ワールド（経路探索のクロスワールド NoPath ケース用）。
        server.addSimpleWorld("world_nether")

        dataFolder = Files.createTempDirectory("advancerailway-test").toFile()

        // AdvanceRailway 本体は onEnableAsync が重いため、dataFolder のみを差し替えたモックを使う。
        val plugin = mockk<AdvanceRailway>(relaxed = true)
        every { plugin.dataFolder } returns dataFolder

        DatabaseInitializer.connect("jdbc:sqlite:${dataFolder.resolve("test.db").absolutePath}")
        DatabaseInitializer.createTables()

        startKoin {
            modules(
                module {
                    single<AdvanceRailway> { plugin }
                    // MapRenderer が書き込み後に呼ぶマーカー描画は、ここでは副作用を捨てる。
                    single<SimpleLayerProvider> { mockk(relaxed = true) }
                    single { ConfigData(limit = 1000) }
                    single { stationRepository }
                    single { railwayRepository }
                    single { groupRepository }
                }
            )
        }
    }

    @AfterAll
    fun tearDown() {
        stopKoin()
        MockBukkit.unmock()
        dataFolder.deleteRecursively()
    }

    @BeforeEach
    fun reseed() = runBlocking {
        transaction {
            DatabaseInitializer.ALL_TABLES.reversed().forEach { it.deleteAll() }
        }
        seedFixtures()
    }

    // --- 読み取り ------------------------------------------------------------------------------

    @Test
    @DisplayName("getStation resolves by slug and returns the station DTO")
    fun getStationReturnsDto() = runBlocking {
        val station = handler.getStation("st01")

        assertEquals("st01", station.slug)
        assertEquals("Central", station.name)
        assertEquals("world", station.world)
        assertEquals("#FF7F00", station.color)
        assertEquals(64.0, station.point.y)
    }

    @Test
    @DisplayName("getStation also resolves by UUID")
    fun getStationResolvesByUuid() = runBlocking {
        val id = stationRepository.findBySlug(Slug("st01"))!!.id
        assertEquals("st01", handler.getStation(id.toString()).slug)
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
    @DisplayName("getStation rejects an invalid slug with NOT_FOUND")
    fun getStationRejectsInvalidId() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getStation("../config") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
    }

    @Test
    @DisplayName("listStations returns every stored station")
    fun listStationsReturnsAll() = runBlocking {
        val response = handler.listStations()

        assertEquals(4, response.stations.size)
        assertEquals(setOf("st01", "st02", "st03", "nt01"), response.stations.map { it.slug }.toSet())
    }

    @Test
    @DisplayName("getRailway maps stations/points and the last verification time to the DTO")
    fun getRailwayReturnsDto() = runBlocking {
        val railway = handler.getRailway("rw01")

        assertEquals("rw01", railway.slug)
        assertEquals("UP_LINE", railway.lineType)
        assertEquals("st01", railway.fromStationSlug)
        assertEquals("st02", railway.toStationSlug)
        assertEquals(2L, railway.timeRequired)
        assertEquals("E", railway.flags)
        // 未検証の路線は最終確認時刻を持たない。
        assertNull(railway.lastCheckedAt)
    }

    @Test
    @DisplayName("listRailways returns every stored railway")
    fun listRailwaysReturnsAll() = runBlocking {
        val response = handler.listRailways()

        assertEquals(1, response.railways.size)
        assertEquals("rw01", response.railways.first().slug)
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
    @DisplayName("listGroups returns every stored group")
    fun listGroupsReturnsAll() = runBlocking {
        val response = handler.listGroups()

        assertEquals(1, response.groups.size)
        assertEquals("g1", response.groups.first().slug)
    }

    @Test
    @DisplayName("getGroup returns the group with a hex color and its numbering settings")
    fun getGroupReturnsDto() = runBlocking {
        val group = handler.getGroup("g1")

        assertEquals("g1", group.slug)
        assertEquals("Yamanote", group.name)
        assertEquals("#00FF00", group.color)
        assertEquals("JY", group.numberingPrefix)
        assertEquals(1, group.numberingStart)
    }

    @Test
    @DisplayName("getGroup throws NOT_FOUND for a missing id")
    fun getGroupNotFound() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.getGroup("missing") }
        }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
    }

    // --- ナンバリング --------------------------------------------------------------------------

    @Test
    @DisplayName("groupStations returns the ordered stations with numbering derived from their position")
    fun groupStationsNumbering() = runBlocking {
        val response = handler.groupStations("g1")

        assertEquals(listOf("st01", "st02"), response.stations.map { it.station.slug })
        assertEquals(listOf(0, 1), response.stations.map { it.position })
        assertEquals(listOf("JY01", "JY02"), response.stations.map { it.numbering })
    }

    @Test
    @DisplayName("a station exposes one numbering entry per group it belongs to")
    fun stationExposesNumberings() = runBlocking {
        val station = handler.getStation("st01")

        assertEquals(1, station.numberings.size)
        assertEquals("g1", station.numberings.first().groupSlug)
        assertEquals("JY01", station.numberings.first().numbering)
    }

    @Test
    @DisplayName("replaceGroupStations reorders the stations and renumbers them")
    fun replaceGroupStationsReorders() = runBlocking {
        val response = handler.replaceGroupStations("g1", ReplaceGroupStationsRequest(listOf("st02", "st01", "st03")))

        assertEquals(listOf("st02", "st01", "st03"), response.stations.map { it.station.slug })
        assertEquals(listOf("JY01", "JY02", "JY03"), response.stations.map { it.numbering })
    }

    @Test
    @DisplayName("replaceGroupStations rejects a station listed twice")
    fun replaceGroupStationsRejectsDuplicates() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.replaceGroupStations("g1", ReplaceGroupStationsRequest(listOf("st01", "st01"))) }
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.status)
        assertEquals("duplicate_station", error.code)
    }

    @Test
    @DisplayName("clearing the numbering prefix removes the numbers but keeps the order")
    fun unsetNumberingPrefix() = runBlocking {
        handler.updateGroup("g1", UpdateGroupRequest(unset = listOf("numberingPrefix")))

        val response = handler.groupStations("g1")
        assertEquals(listOf("st01", "st02"), response.stations.map { it.station.slug })
        assertTrue(response.stations.all { it.numbering == null })
    }

    @Test
    @DisplayName("the numbering start offsets every station's number")
    fun numberingStartOffsets() = runBlocking {
        handler.updateGroup("g1", UpdateGroupRequest(numberingStart = 10))

        assertEquals(listOf("JY10", "JY11"), handler.groupStations("g1").stations.map { it.numbering })
    }

    // --- 書き込み ------------------------------------------------------------------------------

    @Test
    @DisplayName("createStation stores the station and answers 201")
    fun createStationCreates() = runBlocking {
        val response = handler.createStation(
            CreateStationRequest(
                slug = "st99",
                name = "New",
                world = "world",
                point = PointDto(1.0, 2.0, 3.0),
            )
        )

        assertTrue(response is Response.Ok)
        val ok = response as Response.Ok
        assertEquals(HttpStatus.CREATED, ok.status)
        assertEquals("st99", ok.body.slug)
        assertNotNull(stationRepository.findBySlug(Slug("st99")))
    }

    @Test
    @DisplayName("createStation rejects a duplicate slug with 409")
    fun createStationRejectsDuplicateSlug() {
        val error = assertThrows<HttpError> {
            runBlocking {
                handler.createStation(
                    CreateStationRequest("st01", "Dup", "world", PointDto(0.0, 0.0, 0.0))
                )
            }
        }
        assertEquals(HttpStatus.CONFLICT, error.status)
        assertEquals("slug_conflict", error.code)
    }

    @Test
    @DisplayName("createStation rejects an invalid slug with 422")
    fun createStationRejectsInvalidSlug() {
        val error = assertThrows<HttpError> {
            runBlocking {
                handler.createStation(
                    CreateStationRequest("../x", "Bad", "world", PointDto(0.0, 0.0, 0.0))
                )
            }
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.status)
        assertEquals("invalid_slug", error.code)
    }

    @Test
    @DisplayName("updateStation changes only the fields present in the request")
    fun updateStationIsPartial() = runBlocking {
        val updated = handler.updateStation("st01", UpdateStationRequest(name = "Renamed"))

        assertEquals("Renamed", updated.name)
        // 指定しなかったフィールドは元のまま。
        assertEquals("st01", updated.slug)
        assertEquals("#FF7F00", updated.color)
        assertEquals(1.0, updated.point.x)
    }

    @Test
    @DisplayName("updateStation clears overrideSize only when it is listed in unset")
    fun updateStationUnsetClearsField() = runBlocking {
        handler.updateStation("st01", UpdateStationRequest(overrideSize = 12.0))
        assertEquals(12.0, handler.getStation("st01").overrideSize)

        // null は「変更しない」を意味するので、これでは消えない。
        handler.updateStation("st01", UpdateStationRequest(name = "Still"))
        assertEquals(12.0, handler.getStation("st01").overrideSize)

        handler.updateStation("st01", UpdateStationRequest(unset = listOf("overrideSize")))
        assertNull(handler.getStation("st01").overrideSize)
    }

    @Test
    @DisplayName("updateStation rejects a slug that another station already uses")
    fun updateStationRejectsSlugConflict() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.updateStation("st01", UpdateStationRequest(slug = "st02")) }
        }
        assertEquals(HttpStatus.CONFLICT, error.status)
        assertEquals("slug_conflict", error.code)
    }

    @Test
    @DisplayName("deleteStation refuses to delete a station a railway still references")
    fun deleteStationInUse() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.deleteStation("st01") }
        }
        assertEquals(HttpStatus.CONFLICT, error.status)
        assertEquals("station_in_use", error.code)
        assertTrue(error.details["railways"]!!.contains("rw01"))
    }

    @Test
    @DisplayName("deleteStation removes a station nothing references")
    fun deleteStationRemovesUnreferenced() = runBlocking {
        assertTrue(handler.deleteStation("st03").deleted)
        assertNull(stationRepository.findBySlug(Slug("st03")))
    }

    @Test
    @DisplayName("createGroup stores the group and answers 201")
    fun createGroupCreates() = runBlocking {
        val response = handler.createGroup(
            CreateGroupRequest(slug = "chuo", name = "中央線", color = "#FFA500", numberingPrefix = "JC")
        )

        val ok = response as Response.Ok
        assertEquals(HttpStatus.CREATED, ok.status)
        assertEquals("chuo", ok.body.slug)
        assertEquals("#FFA500", ok.body.color)
        assertEquals("JC", ok.body.numberingPrefix)
    }

    @Test
    @DisplayName("deleteGroup refuses to delete a group a railway still belongs to")
    fun deleteGroupInUse() {
        val error = assertThrows<HttpError> {
            runBlocking { handler.deleteGroup("g1") }
        }
        assertEquals(HttpStatus.CONFLICT, error.status)
        assertEquals("group_in_use", error.code)
    }

    @Test
    @DisplayName("updateRailway can detach the group via unset")
    fun updateRailwayUnsetGroup() = runBlocking {
        val updated = handler.updateRailway("rw01", UpdateRailwayRequest(unset = listOf("group")))

        assertNull(updated.group)
        assertNull(updated.groupSlug)
    }

    @Test
    @DisplayName("updateRailway keeps the last verification time when it does not retrace")
    fun updateRailwayKeepsLastChecked() = runBlocking {
        val checked = java.time.Instant.parse("2026-08-01T00:00:00Z")
        val current = railwayRepository.findBySlug(Slug("rw01"))!!
        railwayRepository.update(current.copy(lastCheckedAt = checked))

        val updated = handler.updateRailway("rw01", UpdateRailwayRequest(timeRequired = 99))

        assertEquals(99L, updated.timeRequired)
        assertEquals(checked.toString(), updated.lastCheckedAt)
    }

    @Test
    @DisplayName("deleteRailway removes the railway")
    fun deleteRailwayRemoves() = runBlocking {
        assertTrue(handler.deleteRailway("rw01").deleted)
        assertNull(railwayRepository.findBySlug(Slug("rw01")))
    }

    // --- 経路 ----------------------------------------------------------------------------------

    @Test
    @DisplayName("getRoute picks the fast rail leg between connected stations")
    fun getRouteReturnsRoute() = runBlocking {
        val st01 = stationRepository.findBySlug(Slug("st01"))!!.id
        val st02 = stationRepository.findBySlug(Slug("st02"))!!.id
        val route = handler.getRoute("st01", "st02")

        assertEquals(st01.toString(), route.from)
        assertEquals("Central", route.fromName)
        assertEquals(st02.toString(), route.to)
        assertEquals("North", route.toName)
        assertEquals(2L, route.totalTime)
        assertEquals(listOf(st01.toString(), st02.toString()), route.stations)
        assertEquals(1, route.legs.size)
        val leg = route.legs.first()
        assertEquals("RAIL", leg.mode)
        assertEquals("rw01", leg.railwaySlug)
        assertEquals("Yamanote", leg.line) // the line's display name, not the group id
        assertEquals("Central", leg.fromName)
        assertEquals("North", leg.toName)
    }

    @Test
    @DisplayName("getRoute reaches a rail-disconnected station via rail then a final walk")
    fun getRouteWalkingFallback() = runBlocking {
        // st03 has no railway; the cheapest path is rail st01->st02 then a short walk to st03.
        val route = handler.getRoute("st01", "st03")

        assertEquals(3, route.stations.size)
        assertEquals("RAIL", route.legs.first().mode)
        val last = route.legs.last()
        assertEquals("WALK", last.mode)
        assertNull(last.railway)
        assertNull(last.group) // the rail group must not leak onto the walk leg
        assertNull(last.line) // walk legs have no line name
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

    // --- 関連の取得 ----------------------------------------------------------------------------

    @Test
    @DisplayName("stationRailways returns railways touching the station")
    fun stationRailwaysReturnsTouching() = runBlocking {
        val response = handler.stationRailways("st01")
        assertEquals(1, response.railways.size)
        assertEquals("rw01", response.railways.first().slug)
    }

    @Test
    @DisplayName("stationRailways returns empty for a station with no railways")
    fun stationRailwaysEmptyForIsolated() = runBlocking {
        assertTrue(handler.stationRailways("st03").railways.isEmpty())
    }

    @Test
    @DisplayName("groupRailways returns railways belonging to the group")
    fun groupRailwaysReturnsMembers() = runBlocking {
        val response = handler.groupRailways("g1")
        assertEquals(1, response.railways.size)
        assertEquals("rw01", response.railways.first().slug)
    }

    @Test
    @DisplayName("nearestStation returns the closest station in the world")
    fun nearestStationReturnsClosest() = runBlocking {
        // (2,-3) is nearest to st01 (1,64,-3); y is ignored.
        assertEquals("st01", handler.nearestStation("world", 2.0, -3.0).slug)
    }

    @Test
    @DisplayName("nearestStation only considers stations in the requested world")
    fun nearestStationRespectsWorld() = runBlocking {
        // In the nether only nt01 exists, so it wins regardless of coordinates.
        assertEquals("nt01", handler.nearestStation("world_nether", 999.0, 999.0).slug)
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

    // --- StationUtils.nearStation（RailClickEvent が使う最寄り駅判定）---------------------------

    @Test
    @DisplayName("nearStation returns the closest station in the clicked location's world")
    fun nearStationReturnsClosest() = runBlocking {
        // (2,-3) は st01 (1,64,-3) に最も近い（y は無視）。
        assertEquals("st01", StationUtils.nearStation(Location(world, 2.0, 64.0, -3.0))?.slug?.value)
    }

    @Test
    @DisplayName("nearStation never matches a station in another dimension")
    fun nearStationExcludesOtherWorld() = runBlocking {
        // 別ワールド nt01 と同座標 (0,0) をオーバーワールドでクリックしても nt01 は候補外。
        assertEquals("st01", StationUtils.nearStation(Location(world, 0.0, 64.0, 0.0))?.slug?.value)
    }

    @Test
    @DisplayName("nearStation returns null when the clicked world has no stations")
    fun nearStationNoStationInWorld() = runBlocking {
        val theEnd = server.getWorld("world_the_end")!!
        assertNull(StationUtils.nearStation(Location(theEnd, 0.0, 64.0, 0.0)))
    }

    @Test
    @DisplayName("stats returns the network counts")
    fun statsReturnsCounts() = runBlocking {
        val stats = handler.stats()
        assertEquals(4, stats.stations) // st01, st02, st03, nt01
        assertEquals(1, stats.railways)
        assertEquals(1, stats.groups)
    }

    /** テスト用の駅・路線・グループをデータベースへ入れる。 */
    private suspend fun seedFixtures() {
        val group = GroupData(
            id = GroupId.new(),
            slug = Slug("g1"),
            name = "Yamanote",
            railwayColor = Color(0, 255, 0),
            numberingPrefix = "JY",
            numberingStart = 1,
        )
        groupRepository.insert(group)

        val st01 = station("st01", "Central", Point3D(1.0, 64.0, -3.0), Color(255, 127, 0))
        val st02 = station("st02", "North", Point3D(5.0, 64.0, 10.0), Color(0, 0, 255))
        // st03 は路線に接続されない孤立駅（同一ワールドなので徒歩フォールバックで到達できる）。
        station("st03", "Isolated", Point3D(9.0, 64.0, 20.0), Color(128, 128, 128))
        // nt01 は別ワールドの駅（レール未接続 → クロスワールドで NoPath）。
        station("nt01", "Nether", Point3D(0.0, 64.0, 0.0), Color(200, 0, 0), "world_nether")

        // rw01 の所要時間は徒歩（約 3 秒）より速い 2 秒とし、st01<->st02 ではレールが選ばれるようにする。
        railwayRepository.insert(
            RailwayData(
                id = RailwayId.new(),
                slug = Slug("rw01"),
                group = group.id,
                worldName = "world",
                lineType = LineType.UP_LINE,
                line = Line3D(Point3D(1.0, 64.0, -3.0), Point3D(5.0, 64.0, 10.0)),
                fromStation = st01.id,
                toStation = st02.id,
                timeRequired = 2L,
                startPoint = Point3D(1.0, 64.0, -3.0),
                endPoint = Point3D(5.0, 64.0, 10.0),
                flags = "E",
            )
        )

        groupRepository.replaceStations(group.id, listOf(st01.id, st02.id))
    }

    private suspend fun station(
        slug: String,
        name: String,
        point: Point3D,
        color: Color,
        worldName: String = "world",
    ): StationData {
        val data = StationData(
            id = StationId.new(),
            slug = Slug(slug),
            name = name,
            worldName = worldName,
            point = point,
            overrideSize = null,
            color = color,
        )
        stationRepository.insert(data)
        return data
    }
}
