/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.parser

import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.storage.database.DatabaseInitializer
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.GroupData
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.storage.model.StationData
import dev.nikomaru.advancerailway.storage.type.LineType
import dev.nikomaru.advancerailway.domain.geometry.Line3D
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.awt.Color
import java.nio.file.Files

/**
 * コマンド補完・引数解決がデータベースの実データから組み立てられているかを見る。
 *
 * `/ar station tp <駅>` のような引数は、プレイヤーが **駅名か slug** を打つ想定で、
 * UUID を覚えて打つことは無い。[IdEntries] が名前と slug の両方を載せ、
 * [IdIndex] がそれを ID に解決できることをここで固定する。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdEntriesTest {

    private val stations = StationRepository()
    private val railways = RailwayRepository()
    private val groups = GroupRepository()
    private lateinit var dataFolder: java.io.File

    @BeforeAll
    fun setup(): Unit = runBlocking {
        dataFolder = Files.createTempDirectory("advancerailway-identries-test").toFile()
        DatabaseInitializer.connect("jdbc:sqlite:${dataFolder.resolve("test.db").absolutePath}")
        DatabaseInitializer.createTables()
        transaction { DatabaseInitializer.ALL_TABLES.reversed().forEach { it.deleteAll() } }

        val group = GroupData(GroupId.new(), Slug("ym"), "山手線", Color.GREEN).also { groups.insert(it) }
        val fti = station("fti", "ふれんちとーす島")
        val akmt = station("akmt", "赤松")
        railways.insert(
            RailwayData(
                id = RailwayId.new(),
                slug = Slug("fti_akmt"),
                group = group.id,
                worldName = "world",
                lineType = LineType.UP_DOWN_LINE,
                line = Line3D(Point3D(0.0, 64.0, 0.0), Point3D(1.0, 64.0, 0.0)),
                fromStation = fti.id,
                toStation = akmt.id,
                timeRequired = 60L,
                startPoint = Point3D(0.0, 64.0, 0.0),
                endPoint = Point3D(1.0, 64.0, 0.0),
                flags = "E",
            )
        )
    }

    @AfterAll
    fun tearDown() {
        dataFolder.deleteRecursively()
    }

    @Test
    @DisplayName("station suggestions offer display names, and both the name and the slug resolve")
    fun stationsResolveByNameAndSlug() = runBlocking {
        val entries = IdEntries.stations()
        val fti = stations.findBySlug(Slug("fti"))!!

        assertEquals(setOf("ふれんちとーす島", "赤松"), IdIndex.suggestions(entries))
        assertEquals(fti.id.value, IdIndex.resolve(entries, "ふれんちとーす島"))
        assertEquals(fti.id.value, IdIndex.resolve(entries, "fti"))
        assertEquals(fti.id.value, IdIndex.resolve(entries, fti.id.value.toString()))
        assertNull(IdIndex.resolve(entries, "存在しない駅"))
    }

    @Test
    @DisplayName("group suggestions offer display names, and both the name and the slug resolve")
    fun groupsResolveByNameAndSlug() = runBlocking {
        val entries = IdEntries.groups()
        val group = groups.findBySlug(Slug("ym"))!!

        assertEquals(setOf("山手線"), IdIndex.suggestions(entries))
        assertEquals(group.id.value, IdIndex.resolve(entries, "山手線"))
        assertEquals(group.id.value, IdIndex.resolve(entries, "ym"))
    }

    @Test
    @DisplayName("railways have no display name, so they are suggested and resolved by slug")
    fun railwaysResolveBySlug() = runBlocking {
        val entries = IdEntries.railways()
        val railway = railways.findBySlug(Slug("fti_akmt"))!!

        assertEquals(setOf("fti_akmt"), IdIndex.suggestions(entries))
        assertEquals(railway.id.value, IdIndex.resolve(entries, "fti_akmt"))
    }

    private suspend fun station(slug: String, name: String): StationData {
        val data = StationData(
            id = StationId.new(),
            slug = Slug(slug),
            name = name,
            worldName = "world",
            point = Point3D(0.0, 64.0, 0.0),
            overrideSize = null,
            color = Color.WHITE,
        )
        stations.insert(data)
        return data
    }
}
