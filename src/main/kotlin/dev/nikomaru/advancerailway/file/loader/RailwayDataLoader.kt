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
import dev.nikomaru.advancerailway.file.DataPaths
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.type.LineType
import dev.nikomaru.advancerailway.utils.Utils.json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import xyz.jpenilla.squaremap.api.Key
import xyz.jpenilla.squaremap.api.Point
import xyz.jpenilla.squaremap.api.SimpleLayerProvider
import xyz.jpenilla.squaremap.api.marker.Marker
import xyz.jpenilla.squaremap.api.marker.MarkerOptions
import java.awt.Color
import java.util.*
import kotlin.math.ceil

class RailwayDataLoader: KoinComponent {
    private val plugin: AdvanceRailway by inject()
    private val provider: SimpleLayerProvider by inject()
    private val railwayDataFolder = DataPaths.railways
    private val groupDataFolder = DataPaths.groups


    suspend fun load() {
        if (!railwayDataFolder.exists()) {
            railwayDataFolder.mkdirs()
        }
        if (!groupDataFolder.exists()) {
            groupDataFolder.mkdirs()
        }
        railwayDataFolder.listFiles()?.forEach { file ->
            val data = try {
                json.decodeFromString<RailwayData>(file.readText())
            } catch (e: Exception) {
                plugin.logger.warning("Skipping malformed railway data file '${file.name}': ${e.message}")
                return@forEach
            }
            val key = Key.of(data.id.value)
            val marker = Marker.multiPolyline(data.line.points.map { Point.of(it.x, it.z) })
            val arrow = when (data.lineType) {
                LineType.UP_DOWN_LINE -> "<->"
                else -> "->"
            }
            // 参照先を 1 度だけ解決する。削除済み等で解決できない場合は警告を出し、
            // ツールチップにリテラル "null" を出さないよう ID をプレースホルダとして表示する。
            val fromData = data.fromStation.toData()
            val toData = data.toStation.toData()
            val groupData = data.group?.toData()
            if (fromData == null || toData == null) {
                plugin.logger.warning(
                    "Railway '${data.id.value}' references missing station(s): " +
                        "from=${data.fromStation.value}${if (fromData == null) " (missing)" else ""}, " +
                        "to=${data.toStation.value}${if (toData == null) " (missing)" else ""}"
                )
            }
            if (data.group != null && groupData == null) {
                plugin.logger.warning(
                    "Railway '${data.id.value}' references missing group: ${data.group.value}"
                )
            }
            val fromName = fromData?.name ?: data.fromStation.value
            val toName = toData?.name ?: data.toStation.value
            val option = MarkerOptions.builder().clickTooltip("""
                行き先 : $fromName $arrow $toName </span><br/>
                所要時間 : 約 ${ceil(data.timeRequired / 6.0) / 10} 分 </span><br/>
                ${groupData?.name ?: ""}
            """.trimIndent())
            val random = Random()
            random.setSeed(data.group.hashCode().toLong())
            val randomColor = Color(
                random.nextInt(256), random.nextInt(256), random.nextInt(256)
            )
            val color = groupData?.railwayColor ?: randomColor
            option.fillColor(color)
            option.strokeColor(color)
            marker.markerOptions(option)
            provider.addMarker(key, marker)
        }
    }
}