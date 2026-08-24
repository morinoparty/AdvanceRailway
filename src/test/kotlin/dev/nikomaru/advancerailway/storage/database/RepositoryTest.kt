/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.database

import dev.nikomaru.advancerailway.domain.geometry.Line3D
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.GroupData
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.storage.model.StationData
import dev.nikomaru.advancerailway.storage.type.LineType
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * リポジトリを実際の SQLite に対して駆動するテスト。
 *
 * 検証したいのは、以前ファイル走査で担保していたことを DB が引き受けられているか:
 * slug の一意性、参照している行を消せないこと（FK）、グループ内の駅の並び順。
 *
 * Exposed の `transaction {}` は最後に接続したデータベースを既定にするため、
 * DB を使うテストクラスは JVM 内で逐次実行される前提（Gradle の既定）で書いている。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoryTest {

    private val stations = StationRepository()
    private val railways = RailwayRepository()
    private val groups = GroupRepository()
    private lateinit var dataFolder: java.io.File

    @BeforeAll
    fun setup() {
        dataFolder = Files.createTempDirectory("advancerailway-repo-test").toFile()
        DatabaseInitializer.connect("jdbc:sqlite:${dataFolder.resolve("test.db").absolutePath}")
        DatabaseInitializer.createTables()
    }

    @AfterAll
    fun tearDown() {
        dataFolder.deleteRecursively()
    }

    @BeforeEach
    fun clear() {
        transaction { DatabaseInitializer.ALL_TABLES.reversed().forEach { it.deleteAll() } }
    }

    // --- 駅 -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a station round-trips through the database unchanged")
    fun stationRoundTrip() = runBlocking {
        val data = station("st01", "Central", Point3D(1.5, 64.0, -3.25), Color(255, 127, 0), overrideSize = 7.5)
        stations.insert(data)

        val loaded = stations.findById(data.id)
        assertNotNull(loaded)
        assertEquals(data.slug, loaded!!.slug)
        assertEquals("Central", loaded.name)
        assertEquals(1.5, loaded.point.x)
        assertEquals(-3.25, loaded.point.z)
        assertEquals(7.5, loaded.overrideSize)
        assertEquals(Color(255, 127, 0), loaded.color)
    }

    @Test
    @DisplayName("resolve accepts both the UUID and the slug")
    fun stationResolvesEitherKey() = runBlocking {
        val data = station("st01", "Central")
        stations.insert(data)

        assertEquals(data.id, stations.resolve("st01")?.id)
        assertEquals(data.id, stations.resolve(data.id.toString())?.id)
        assertNull(stations.resolve("nope"))
        // slug として不正な文字列でも例外にせず null を返す（境界で 404 にできる）。
        assertNull(stations.resolve("../etc/passwd"))
    }

    @Test
    @DisplayName("two stations cannot share a slug")
    fun stationSlugIsUnique() = runBlocking {
        stations.insert(station("st01", "Central"))

        assertTrue(stations.slugExists(Slug("st01")))
        assertThrows<Exception> { runBlocking { stations.insert(station("st01", "Duplicate")) } }
        Unit
    }

    @Test
    @DisplayName("slugExists ignores the station being updated, so a slug can be kept on rename")
    fun slugExistsExcludesSelf() = runBlocking {
        val data = station("st01", "Central")
        stations.insert(data)

        assertTrue(stations.slugExists(Slug("st01")))
        assertFalse(stations.slugExists(Slug("st01"), excluding = data.id))
    }

    @Test
    @DisplayName("findByWorld only returns stations of that world")
    fun findByWorldFilters() = runBlocking {
        stations.insert(station("st01", "Overworld"))
        stations.insert(station("nt01", "Nether", worldName = "world_nether"))

        assertEquals(listOf("st01"), stations.findByWorld("world").map { it.slug.value })
        assertEquals(listOf("nt01"), stations.findByWorld("world_nether").map { it.slug.value })
        assertTrue(stations.findByWorld("world_the_end").isEmpty())
    }

    // --- 路線 ---------------------------------------------------------------------------------

    @Test
    @DisplayName("a railway round-trips, keeping the exact point list of its line")
    fun railwayRoundTripKeepsLinePoints() = runBlocking {
        val from = station("st01", "A").also { stations.insert(it) }
        val to = station("st02", "B").also { stations.insert(it) }
        // 3 点目は 1-2 と共線。addPoint を通すと圧縮されてしまうため、保存・復元で点数が変わらないことを見る。
        val line = Line3D(Point3D(0.0, 64.0, 0.0), Point3D(5.0, 64.0, 0.0))
        line.points = arrayListOf(Point3D(0.0, 64.0, 0.0), Point3D(5.0, 64.0, 0.0), Point3D(10.0, 64.0, 0.0))
        val data = railway("rw01", from.id, to.id, line = line)
        railways.insert(data)

        val loaded = railways.findBySlug(Slug("rw01"))
        assertNotNull(loaded)
        assertEquals(3, loaded!!.line.points.size)
        assertEquals(10.0, loaded.line.points.last().x)
        assertEquals("EE", loaded.flags)
        assertNull(loaded.lastCheckedAt)
    }

    @Test
    @DisplayName("markChecked records the verification time only for the given railways")
    fun markCheckedIsSelective() = runBlocking {
        val from = station("st01", "A").also { stations.insert(it) }
        val to = station("st02", "B").also { stations.insert(it) }
        val checked = railway("rw01", from.id, to.id).also { railways.insert(it) }
        val untouched = railway("rw02", from.id, to.id).also { railways.insert(it) }

        val at = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        assertEquals(1, railways.markChecked(listOf(checked.id), at))

        assertEquals(at, railways.findById(checked.id)?.lastCheckedAt)
        // 検証に失敗した路線は触らないので、前回の値（ここでは未確認）がそのまま残る。
        assertNull(railways.findById(untouched.id)?.lastCheckedAt)
    }

    @Test
    @DisplayName("a station referenced by a railway cannot be deleted")
    fun stationInUseIsProtectedByForeignKey() = runBlocking {
        val from = station("st01", "A").also { stations.insert(it) }
        val to = station("st02", "B").also { stations.insert(it) }
        railways.insert(railway("rw01", from.id, to.id))

        // 呼び出し側は findByStation で参照を調べてから消す。直接消そうとすれば FK が止める。
        assertEquals(listOf("rw01"), railways.findByStation(from.id).map { it.slug.value })
        assertThrows<Exception> { runBlocking { stations.delete(from.id) } }
        Unit
    }

    @Test
    @DisplayName("a group referenced by a railway cannot be deleted")
    fun groupInUseIsProtectedByForeignKey() = runBlocking {
        val group = group("g1", "Yamanote").also { groups.insert(it) }
        val from = station("st01", "A").also { stations.insert(it) }
        val to = station("st02", "B").also { stations.insert(it) }
        railways.insert(railway("rw01", from.id, to.id, group = group.id))

        assertEquals(listOf("rw01"), railways.findByGroup(group.id).map { it.slug.value })
        assertThrows<Exception> { runBlocking { groups.delete(group.id) } }
        Unit
    }

    // --- グループと駅の並び ---------------------------------------------------------------------

    @Test
    @DisplayName("replaceStations renumbers positions from zero, and applying it twice changes nothing")
    fun replaceStationsIsIdempotent() = runBlocking {
        val group = group("g1", "Yamanote", prefix = "JY").also { groups.insert(it) }
        val a = station("a", "A").also { stations.insert(it) }
        val b = station("b", "B").also { stations.insert(it) }
        val c = station("c", "C").also { stations.insert(it) }

        groups.replaceStations(group.id, listOf(a.id, b.id, c.id))
        assertEquals(listOf(0, 1, 2), groups.stationsOf(group.id).map { it.position })
        assertEquals(listOf("JY01", "JY02", "JY03"), groups.stationsOf(group.id).map { it.numbering })

        groups.replaceStations(group.id, listOf(a.id, b.id, c.id))
        assertEquals(listOf("a", "b", "c"), groups.stationsOf(group.id).map { it.station.slug.value })

        // 並べ替えれば番号も付け直される。
        groups.replaceStations(group.id, listOf(c.id, a.id))
        assertEquals(listOf("c", "a"), groups.stationsOf(group.id).map { it.station.slug.value })
        assertEquals(listOf("JY01", "JY02"), groups.stationsOf(group.id).map { it.numbering })
    }

    @Test
    @DisplayName("a station can belong to several groups and gets a number in each")
    fun stationBelongsToSeveralGroups() = runBlocking {
        val yamanote = group("yamanote", "山手線", prefix = "JY").also { groups.insert(it) }
        val chuo = group("chuo", "中央線", prefix = "JC", start = 10).also { groups.insert(it) }
        val transfer = station("tky", "東京").also { stations.insert(it) }

        groups.replaceStations(yamanote.id, listOf(transfer.id))
        groups.replaceStations(chuo.id, listOf(transfer.id))

        val memberships = groups.groupsOf(transfer.id)
        assertEquals(2, memberships.size)
        assertEquals(setOf("JC10", "JY01"), memberships.map { it.numbering }.toSet())
        assertEquals(setOf("JC10", "JY01"), groups.allGroupsOfStations()[transfer.id]!!.map { it.numbering }.toSet())
    }

    @Test
    @DisplayName("deleting a station drops its group memberships (cascade), leaving the rest numbered")
    fun deletingStationCascadesMembership() = runBlocking {
        val group = group("g1", "Yamanote", prefix = "JY").also { groups.insert(it) }
        val a = station("a", "A").also { stations.insert(it) }
        val b = station("b", "B").also { stations.insert(it) }
        groups.replaceStations(group.id, listOf(a.id, b.id))

        stations.delete(a.id)

        val remaining = groups.stationsOf(group.id)
        assertEquals(listOf("b"), remaining.map { it.station.slug.value })
        // b は position 1 のままなので番号は JY02。詰めたい場合は replaceStations で並べ直す。
        assertEquals(listOf("JY02"), remaining.map { it.numbering })
    }

    @Test
    @DisplayName("a group without a numbering prefix yields no numbers")
    fun groupWithoutPrefixHasNoNumbers() = runBlocking {
        val group = group("g1", "Nameless").also { groups.insert(it) }
        val a = station("a", "A").also { stations.insert(it) }
        groups.replaceStations(group.id, listOf(a.id))

        assertTrue(groups.stationsOf(group.id).all { it.numbering == null })
        assertTrue(groups.allGroupsOfStations()[a.id]!!.all { it.numbering == null })
    }

    // --- フィクスチャ --------------------------------------------------------------------------

    private fun station(
        slug: String,
        name: String,
        point: Point3D = Point3D(0.0, 64.0, 0.0),
        color: Color = Color.WHITE,
        overrideSize: Double? = null,
        worldName: String = "world",
    ) = StationData(StationId.new(), Slug(slug), name, worldName, point, overrideSize, color)

    private fun group(slug: String, name: String, prefix: String? = null, start: Int = 1) =
        GroupData(GroupId.new(), Slug(slug), name, Color.GREEN, prefix, start)

    private fun railway(
        slug: String,
        from: StationId,
        to: StationId,
        group: GroupId? = null,
        line: Line3D = Line3D(Point3D(0.0, 64.0, 0.0), Point3D(1.0, 64.0, 0.0)),
    ) = RailwayData(
        id = RailwayId.new(),
        slug = Slug(slug),
        group = group,
        worldName = "world",
        lineType = LineType.UP_DOWN_LINE,
        line = line,
        fromStation = from,
        toStation = to,
        timeRequired = 60L,
        startPoint = Point3D(0.0, 64.0, 0.0),
        endPoint = Point3D(1.0, 64.0, 0.0),
        flags = "EE",
    )
}
