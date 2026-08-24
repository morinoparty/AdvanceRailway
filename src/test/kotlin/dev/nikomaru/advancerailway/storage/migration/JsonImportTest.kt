/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.migration

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.storage.database.DatabaseInitializer
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.StationData
import io.mockk.every
import io.mockk.mockk
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
import java.io.File
import java.nio.file.Files

/**
 * 旧 JSON データの一度きりの取り込み（[JsonImport]）のテスト。
 *
 * 稼働中のサーバーはこの経路を 1 回だけ通る。壊れると本番データが失われるので、
 * 「取り込む条件」「色が変わらないこと」「取り込めない路線を黙って捨てないこと」
 * 「二度目は走らないこと」を固定する。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsonImportTest {

    private val stations = StationRepository()
    private val railways = RailwayRepository()
    private val groups = GroupRepository()
    private lateinit var dataFolder: File
    private lateinit var plugin: AdvanceRailway

    @BeforeAll
    fun setup() {
        dataFolder = Files.createTempDirectory("advancerailway-import-test").toFile()
        plugin = mockk(relaxed = true)
        every { plugin.dataFolder } returns dataFolder
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
        dataFolder.listFiles()?.filter { it.isDirectory }?.forEach { it.deleteRecursively() }
    }

    private fun import() = JsonImport(plugin, stations, railways, groups)

    @Test
    @DisplayName("imports groups, stations and railways, then archives the legacy folder")
    fun importsAndArchives() = runBlocking {
        writeGroup("g1", "Yamanote")
        writeStation("st01", "Central", color = "255,127,0")
        writeStation("st02", "North")
        writeRailwayV3("rw01", from = "st01", to = "st02", group = "g1")

        val result = import().runIfNeeded()

        assertNotNull(result)
        assertEquals(1, result!!.groups)
        assertEquals(2, result.stations)
        assertEquals(1, result.railways)

        // 旧 ID は slug になり、主キーは新しく採番される。
        val st01 = stations.findBySlug(Slug("st01"))
        assertNotNull(st01)
        assertEquals("Central", st01!!.name)
        val railway = railways.findBySlug(Slug("rw01"))
        assertNotNull(railway)
        assertEquals(st01.id, railway!!.fromStation)
        assertEquals(groups.findBySlug(Slug("g1"))!!.id, railway.group)
        assertEquals("EE", railway.flags)

        // 取り込んだ data/ は退避され、再取り込みされない。
        assertFalse(dataFolder.resolve("data").exists())
        assertTrue(dataFolder.listFiles()!!.any { it.name.startsWith("data-backup-json-") })
    }

    @Test
    @DisplayName("keeps the colour of a station that had none by seeding from its slug")
    fun materialisesDefaultColour() = runBlocking {
        writeStation("st01", "Central", color = null)

        import().runIfNeeded()

        val imported = stations.findBySlug(Slug("st01"))!!
        // 旧仕様と同じ見た目を保つため、UUID ではなく slug をシードに色を作る。
        assertEquals(StationData.defaultColor(Slug("st01")), imported.color)
    }

    @Test
    @DisplayName("converts a V2 railway by folding its directionPoint into the flags")
    fun convertsV2Railway() = runBlocking {
        writeStation("st01", "A")
        writeStation("st02", "B")
        // directionPoint が始点の東隣 → 出発方角は E。既存の分岐フラグ [EAST] が後ろに続く。
        writeRailwayV2("rw01", from = "st01", to = "st02", directionPoint = "1.0,64.0,0.0", flags = """["EAST"]""")

        val result = import().runIfNeeded()

        assertEquals(1, result!!.railways)
        assertEquals("EE", railways.findBySlug(Slug("rw01"))!!.flags)
    }

    @Test
    @DisplayName("reports V1 railways as skipped instead of importing them silently")
    fun skipsV1Railway() = runBlocking {
        writeStation("st01", "A")
        writeStation("st02", "B")
        writeRailwayV1("rw01", from = "st01", to = "st02")

        val result = import().runIfNeeded()

        assertEquals(0, result!!.railways)
        assertEquals(listOf("rw01"), result.skippedRailways)
        assertNull(railways.findBySlug(Slug("rw01")))
    }

    @Test
    @DisplayName("does not import the legacy numbering (numbering now belongs to groups)")
    fun dropsLegacyNumbering() = runBlocking {
        writeGroup("g1", "Yamanote")
        writeStation("st01", "Central", numbering = "M01")

        import().runIfNeeded()

        // グループ側の接頭辞は未設定で始まり、駅の並びも空。移行後に設定し直す。
        val group = groups.findBySlug(Slug("g1"))!!
        assertNull(group.numberingPrefix)
        assertTrue(groups.stationsOf(group.id).isEmpty())
    }

    @Test
    @DisplayName("does nothing when the database already holds data")
    fun skipsWhenDatabaseIsNotEmpty() = runBlocking {
        writeStation("st01", "Central")
        assertNotNull(import().runIfNeeded())

        // 2 回目: data/ を用意し直しても、DB に行があるので取り込まない。
        writeStation("st02", "Second")
        assertNull(import().runIfNeeded())
        assertNull(stations.findBySlug(Slug("st02")))
    }

    @Test
    @DisplayName("does nothing when there is no legacy folder at all (fresh install)")
    fun skipsWhenNoLegacyFolder() = runBlocking {
        assertNull(import().runIfNeeded())
        assertEquals(0L, stations.count())
    }

    @Test
    @DisplayName("skips a malformed file but still imports the rest")
    fun skipsMalformedFile() = runBlocking {
        writeStation("st01", "Central")
        write("stations", "broken", "{ this is not valid json")

        val result = import().runIfNeeded()

        assertEquals(1, result!!.stations)
        assertNotNull(stations.findBySlug(Slug("st01")))
    }

    // --- 旧形式のフィクスチャ ---------------------------------------------------------------

    private fun writeStation(id: String, name: String, color: String? = "1,2,3", numbering: String? = null) {
        val colorJson = color?.let { ""","color":"$it"""" } ?: ""
        val numberingJson = numbering?.let { ""","numbering":"$it"""" } ?: ""
        write(
            "stations", id,
            """{"stationId":"$id","name":"$name","world":"world","point":"0.0,64.0,0.0"$numberingJson$colorJson}"""
        )
    }

    private fun writeGroup(id: String, name: String) {
        write("groups", id, """{"groupId":"$id","name":"$name","railwayColor":"0,255,0"}""")
    }

    private fun writeRailwayV3(id: String, from: String, to: String, group: String?) {
        val groupJson = group?.let { """"group":"$it",""" } ?: ""
        write(
            "railways", id,
            """{"id":"$id",${groupJson}"world":"world","lineType":"UP_LINE",""" +
                """"line":"(0.0,64.0,0.0):(5.0,64.0,0.0)","fromStation":"$from","toStation":"$to",""" +
                """"timeRequired":60,"startPoint":"0.0,64.0,0.0","endPoint":"5.0,64.0,0.0","flags":"EE","version":3}"""
        )
    }

    private fun writeRailwayV2(id: String, from: String, to: String, directionPoint: String, flags: String) {
        write(
            "railways", id,
            """{"id":"$id","world":"world","lineType":"UP_LINE",""" +
                """"line":"(0.0,64.0,0.0):(5.0,64.0,0.0)","fromStation":"$from","toStation":"$to",""" +
                """"timeRequired":60,"startPoint":"0.0,64.0,0.0","endPoint":"5.0,64.0,0.0",""" +
                """"directionPoint":"$directionPoint","flags":$flags}"""
        )
    }

    private fun writeRailwayV1(id: String, from: String, to: String) {
        write(
            "railways", id,
            """{"id":"$id","world":"world","lineType":"UP_LINE",""" +
                """"line":"(0.0,64.0,0.0):(5.0,64.0,0.0)","fromStation":"$from","toStation":"$to",""" +
                """"timeRequired":60,"startPoint":"0.0,64.0,0.0","endPoint":"5.0,64.0,0.0",""" +
                """"directionPoint":"1.0,64.0,0.0"}"""
        )
    }

    private fun write(type: String, id: String, content: String) {
        val file = dataFolder.resolve("data").resolve(type).resolve("$id.json")
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}
