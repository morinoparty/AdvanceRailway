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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val directionPoint: Point3D
    ): RailwayData()

    /**
     * 旧形式。分岐点で選んだ方角列 [flags] を持つが、探索開始方向は隣接レール座標
     * [directionPoint] で表す（V3 で flags の先頭方角に統合された）。
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
        val directionPoint: Point3D,
        val flags: List<BranchDirection> = emptyList(),
    ): RailwayData()

    /**
     * 現行形式。[flags] は始点からの出発方角（1文字目）と各分岐点で選ぶ方角の並び
     * （例 "EE" = 東へ出発し、最初の分岐で東）。必ず1文字以上で、始点と flags だけで
     * 経路を再現・検証できる。[version] はファイル形式の判別用。
     */
    @Serializable
    data class V3(
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
        val flags: String,
        val version: Int = 3,
    ): RailwayData() {
        /** flags を方角列に解析する。不正な文字を含む・空の場合は null。 */
        fun branchFlags(): List<BranchDirection>? =
            BranchDirection.parse(flags)?.takeIf { it.isNotEmpty() }
    }

    /** 共通フィールドの copy。どの形式でも形式を保ったまま更新する。 */
    fun copyCommon(
        group: GroupId? = this.group,
        lineType: LineType = this.lineType,
        fromStation: StationId = this.fromStation,
        toStation: StationId = this.toStation,
    ): RailwayData = when (this) {
        is V1 -> copy(group = group, lineType = lineType, fromStation = fromStation, toStation = toStation)
        is V2 -> copy(group = group, lineType = lineType, fromStation = fromStation, toStation = toStation)
        is V3 -> copy(group = group, lineType = lineType, fromStation = fromStation, toStation = toStation)
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
 * バージョンをファイル内容で判別する。
 * V3 以降は明示の version フィールドを持つ。旧ファイルには型情報がないため、
 * flags フィールドを持てば V2（V2 は encodeDefaults=true で必ず flags を書き出す）、
 * どちらもなければ V1。
 */
object RailwayDataSerializer: JsonContentPolymorphicSerializer<RailwayData>(RailwayData::class) {
    // 可視性をテスト（バージョン判別の検証）のため public に広げている
    public override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RailwayData> {
        val obj = element.jsonObject
        val version = obj["version"]?.jsonPrimitive?.intOrNull
        return when {
            version == 3 -> RailwayData.V3.serializer()
            version != null -> throw SerializationException("Unsupported railway data version: $version")
            obj.containsKey("flags") -> RailwayData.V2.serializer()
            else -> RailwayData.V1.serializer()
        }
    }
}
