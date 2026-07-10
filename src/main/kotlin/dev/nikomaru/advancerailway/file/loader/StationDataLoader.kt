/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.loader

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.file.data.ConfigData
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.data.StationData
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.utils.Utils.json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import xyz.jpenilla.squaremap.api.Key
import xyz.jpenilla.squaremap.api.Point
import xyz.jpenilla.squaremap.api.SimpleLayerProvider
import xyz.jpenilla.squaremap.api.marker.Marker
import xyz.jpenilla.squaremap.api.marker.MarkerOptions


class StationDataLoader: KoinComponent {
    private val plugin: AdvanceRailway by inject()
    private val provider: SimpleLayerProvider by inject()
    private val config: ConfigData by inject()
    private val stationDataFolder = plugin.dataFolder.resolve("data").resolve("stations")
    private val railwayDataFolder = plugin.dataFolder.resolve("data").resolve("railways")
    private val joinedCount = hashMapOf<StationId, Int>()

    fun load() {
        if (!stationDataFolder.exists()) {
            stationDataFolder.mkdirs()
        }
        if (!railwayDataFolder.exists()) {
            railwayDataFolder.mkdirs()
        }
        railwayDataFolder.listFiles()?.forEach { file ->
            val data = try {
                json.decodeFromString<RailwayData>(file.readText())
            } catch (e: Exception) {
                plugin.logger.warning("Skipping malformed railway data file '${file.name}': ${e.message}")
                return@forEach
            }
            joinedCount[data.toStation] = joinedCount.getOrDefault(data.toStation, 0) + 1
            joinedCount[data.fromStation] = joinedCount.getOrDefault(data.fromStation, 0) + 1
        }

        stationDataFolder.listFiles()?.forEach { file ->
            val stationData = try {
                json.decodeFromString<StationData>(file.readText())
            } catch (e: Exception) {
                plugin.logger.warning("Skipping malformed station data file '${file.name}': ${e.message}")
                return@forEach
            }
            val key = Key.of(stationData.stationId.value)
            val color = stationData.color
            val colorOption = MarkerOptions.builder().fillColor(color.brighter()).strokeColor(color).clickTooltip(
                """
                ${if (stationData.numbering != null) "${stationData.numbering} </span><br/>" else ""}
                名前 : ${stationData.name} </span><br/>
                """.trimIndent()
            )
            val size = stationData.overrideSize ?: joinedCount[stationData.stationId]?.let {
                config.circleMultiple.times(it)
            } ?: config.circleSizeBase
            val marker =
                Marker.circle(Point.of(stationData.point.x, stationData.point.z), size).markerOptions(colorOption)
            provider.addMarker(key, marker)
        }
    }
}