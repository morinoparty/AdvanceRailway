/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.data

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.AdvanceRailway.Companion.plugin
import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.file.FileLoader
import dev.nikomaru.advancerailway.file.utils.ColorSerializer
import dev.nikomaru.advancerailway.file.utils.WorldSerializer
import dev.nikomaru.advancerailway.file.utils.writeAtomically
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.utils.Utils.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.bukkit.World
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.Color
import kotlin.random.Random

@Serializable
data class StationData(
    val stationId: StationId,
    val name: String,
    val numbering: String?,
    val world: @Serializable(with = WorldSerializer::class) World,
    val point: Point3D,
    val overrideSize: Double?,
    val color: @Serializable(with = ColorSerializer::class) Color = defaultColor(stationId)
): KoinComponent {
    val plugin: AdvanceRailway by inject()

    suspend fun save() {
        val file = plugin.dataFolder.resolve("data").resolve("stations").resolve("${stationId.value}.json")
        writeAtomically(file, json.encodeToString(this))
        FileLoader.mapDataLoad()
    }

    companion object {
        /**
         * 駅 ID から決定論的に既定色を導出する。
         *
         * 乱数シードを駅 ID に固定することで、`color` を持たない旧データを
         * デコードするたびに色が変わってしまう問題を防ぐ。
         */
        fun defaultColor(stationId: StationId): Color {
            val random = Random(stationId.hashCode().toLong())
            return Color(random.nextInt(256), random.nextInt(256), random.nextInt(256))
        }

        fun load(stationId: StationId): StationData {
            val file = plugin.dataFolder.resolve("data").resolve("stations").resolve("${stationId.value}.json")
            return json.decodeFromString(file.readText())
        }
    }
}
