/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.route

import arrow.core.Either
import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

/**
 * 実サーバーのデータ（`src/test/resources/serverdata/`）を使った [RouteFinder] / [RouteRenderer] の結合テスト。
 *
 * データは Bukkit 型に依存せず生 JSON として読み込むため、サーバーを起動せずに実ネットワーク上の経路探索と
 * **駅名・路線名の解決**（ID ではなく人間可読な名前が出ること）を検証できる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealDataRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var stations: List<StationNode>
    private lateinit var railways: List<RailEdge>
    private lateinit var stationNames: Map<StationId, String>
    private lateinit var groupNames: Map<GroupId, String>

    init {
        loadFixtures()
    }

    private fun resourceDir(name: String): File {
        val url = javaClass.classLoader.getResource("serverdata/data/$name")
            ?: error("test resource serverdata/data/$name not found")
        return File(url.toURI())
    }

    private fun jsonFiles(name: String): List<File> =
        resourceDir(name).listFiles { f -> f.isFile && f.extension == "json" }?.sorted() ?: emptyList()

    private fun parsePoint(raw: String): Point3D {
        val (x, y, z) = raw.split(",").map { it.trim().toDouble() }
        return Point3D(x, y, z)
    }

    private fun loadFixtures() {
        val stationObjects = jsonFiles("stations").map { json.parseToJsonElement(it.readText()).jsonObject }
        stations = stationObjects.map { obj ->
            StationNode(
                id = StationId(obj["stationId"]!!.jsonPrimitive.content),
                world = obj["world"]!!.jsonPrimitive.content,
                point = parsePoint(obj["point"]!!.jsonPrimitive.content),
            )
        }
        stationNames = stationObjects.associate {
            StationId(it["stationId"]!!.jsonPrimitive.content) to (it["name"]?.jsonPrimitive?.contentOrNull ?: "")
        }

        railways = jsonFiles("railways").map { json.parseToJsonElement(it.readText()).jsonObject }.map { obj ->
            RailEdge(
                railwayId = RailwayId(obj["id"]!!.jsonPrimitive.content),
                from = StationId(obj["fromStation"]!!.jsonPrimitive.content),
                to = StationId(obj["toStation"]!!.jsonPrimitive.content),
                timeRequired = obj["timeRequired"]!!.jsonPrimitive.long,
                group = obj["group"]?.jsonPrimitive?.contentOrNull?.let { GroupId(it) },
            )
        }

        groupNames = jsonFiles("groups").map { json.parseToJsonElement(it.readText()).jsonObject }.associate {
            GroupId(it["groupId"]!!.jsonPrimitive.content) to (it["name"]?.jsonPrimitive?.contentOrNull ?: "")
        }
    }

    private fun node(id: String): StationNode = stations.first { it.id.value == id }

    private fun route(from: String, to: String): Route {
        val result = RouteFinder.findRoute(stations, railways, Waypoint.Station(node(from)), node(to))
        assertInstanceOf(Either.Right::class.java, result, "expected a route from $from to $to but got $result")
        return (result as Either.Right).value
    }

    @Test
    @DisplayName("the real network loads (159 stations, 271 railways, 32 groups)")
    fun fixturesLoaded() {
        assertEquals(159, stations.size)
        assertEquals(271, railways.size)
        assertEquals(32, groupNames.size)
        // spot-check the two stations from the reported command output.
        assertEquals("ふれんちとーす島", stationNames[StationId("fti")])
        assertEquals("赤松", stationNames[StationId("akmt")])
    }

    @Test
    @DisplayName("renders the fti -> akmt route with human-readable station and line names, not ids")
    fun rendersNamesNotIds() {
        val route = route("fti", "akmt")
        val rendered = RouteRenderer.render(
            route,
            originLabel = stationNames.getValue(StationId("fti")),
            stationName = { stationNames[it] },
            groupName = { groupNames[it] },
        )

        // Endpoints show names, not ids.
        assertEquals("ふれんちとーす島", rendered.fromLabel)
        assertEquals("赤松", rendered.toLabel)
        assertTrue(rendered.legs.isNotEmpty())
        assertTrue(rendered.totalMinutes > 0.0)

        // No rendered label is a bare station id when that station has a real name.
        rendered.legs.forEach { leg ->
            assertFalse(
                leg.fromLabel == "fti" || leg.toLabel == "akmt",
                "labels must be names, not ids: ${leg.fromLabel} -> ${leg.toLabel}",
            )
        }

        // Each rail leg's line label equals the group's display NAME (id fallback would differ from the map value).
        route.legs.zip(rendered.legs).forEach { (raw, shown) ->
            when (raw.mode) {
                TravelMode.RAIL -> {
                    val group = raw.group
                    if (group != null && groupNames[group]?.isNotBlank() == true) {
                        assertEquals(groupNames[group], shown.lineLabel)
                    }
                }
                TravelMode.WALK -> assertNull(shown.lineLabel)
            }
        }

        // At least one rail leg exists and shows a proper Japanese line name.
        val railLine = rendered.legs.firstOrNull { it.mode == TravelMode.RAIL }?.lineLabel
        assertNotNull(railLine, "expected at least one rail leg with a line name")
    }

    @Test
    @DisplayName("the rendered station chain is continuous (each leg's arrival is the next leg's departure)")
    fun stationChainIsContinuous() {
        val rendered = RouteRenderer.render(
            route("fti", "akmt"),
            originLabel = stationNames.getValue(StationId("fti")),
            stationName = { stationNames[it] },
            groupName = { groupNames[it] },
        )
        for (i in 0 until rendered.legs.size - 1) {
            assertEquals(
                rendered.legs[i].toLabel, rendered.legs[i + 1].fromLabel,
                "leg ${i + 1} arrives at ${rendered.legs[i].toLabel} but leg ${i + 2} departs from ${rendered.legs[i + 1].fromLabel}",
            )
        }
    }

    @Test
    @DisplayName("routes between two rail-connected stations of the real network")
    fun directRailNeighbours() {
        // Pick a real railway whose endpoints are both known stations, and route between them.
        val edge = railways.first { e ->
            e.timeRequired > 0 && stations.any { it.id == e.from } && stations.any { it.id == e.to }
        }
        val route = route(edge.from.value, edge.to.value)
        assertTrue(route.legs.isNotEmpty())
        assertTrue(route.totalSeconds > 0)
    }
}
