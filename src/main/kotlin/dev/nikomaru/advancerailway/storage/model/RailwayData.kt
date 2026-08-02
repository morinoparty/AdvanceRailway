/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.model

import dev.nikomaru.advancerailway.domain.geometry.Line3D
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.rail.BranchDirection
import dev.nikomaru.advancerailway.storage.DataPaths
import dev.nikomaru.advancerailway.storage.FileLoader
import dev.nikomaru.advancerailway.storage.type.LineType
import dev.nikomaru.advancerailway.storage.serialization.WorldSerializer
import dev.nikomaru.advancerailway.storage.serialization.writeAtomically
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.utils.Utils.json
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.bukkit.World

@Serializable(with = RailwayDataSerializer::class)
sealed class RailwayData {
    abstract val id: RailwayId
    abstract val group: GroupId?
    abstract val world: World
    abstract val lineType: LineType
    abstract val line: Line3D
    abstract val fromStation: StationId
    abstract val toStation: StationId
    abstract val timeRequired: Long //秒
    abstract val startPoint: Point3D
    abstract val endPoint: Point3D
    abstract val directionPoint: Point3D

    /** 旧形式。分岐情報を持たないため、経路の再トレースは分岐なしの単一経路に限られる。 */
    @Serializable
    data class V1(
        override val id: RailwayId,
        override val group: GroupId?,
        override val world: @Serializable(with = WorldSerializer::class) World,
        override val lineType: LineType,
        override val line: Line3D,
        override val fromStation: StationId,
        override val toStation: StationId,
        override val timeRequired: Long,
        override val startPoint: Point3D,
        override val endPoint: Point3D,
        override val directionPoint: Point3D
    ): RailwayData()

    /**
     * 現行形式。分岐点で選んだ方角列 [flags]（inspect の分岐フラグと同じ形式）を持ち、
     * 開始点と flags から経路を再現・検証できる。
     */
    @Serializable
    data class V2(
        override val id: RailwayId,
        override val group: GroupId?,
        override val world: @Serializable(with = WorldSerializer::class) World,
        override val lineType: LineType,
        override val line: Line3D,
        override val fromStation: StationId,
        override val toStation: StationId,
        override val timeRequired: Long,
        override val startPoint: Point3D,
        override val endPoint: Point3D,
        override val directionPoint: Point3D,
        val flags: List<BranchDirection> = emptyList(),
    ): RailwayData()

    /** 共通フィールドの copy。V1/V2 どちらでも形式を保ったまま更新する。 */
    fun copyCommon(
        group: GroupId? = this.group,
        lineType: LineType = this.lineType,
        fromStation: StationId = this.fromStation,
        toStation: StationId = this.toStation,
    ): RailwayData = when (this) {
        is V1 -> copy(group = group, lineType = lineType, fromStation = fromStation, toStation = toStation)
        is V2 -> copy(group = group, lineType = lineType, fromStation = fromStation, toStation = toStation)
    }

    suspend fun save() {
        val file = DataPaths.railways.resolve("${id.value}.json")
        writeAtomically(file, json.encodeToString(this))
        FileLoader.mapDataLoad()
    }

    companion object {
        fun load(id: RailwayId): RailwayData {
            val file = DataPaths.railways.resolve("${id.value}.json")
            return json.decodeFromString(file.readText())
        }
    }
}

/**
 * 旧ファイルには型情報がないため、内容で V1/V2 を判別する。
 * flags フィールドを持てば V2（V2 は encodeDefaults=true で必ず flags を書き出す）。
 */
object RailwayDataSerializer: JsonContentPolymorphicSerializer<RailwayData>(RailwayData::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RailwayData> =
        if (element.jsonObject.containsKey("flags")) RailwayData.V2.serializer() else RailwayData.V1.serializer()
}
