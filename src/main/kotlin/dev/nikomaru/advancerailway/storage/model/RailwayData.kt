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
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.domain.rail.BranchDirection
import dev.nikomaru.advancerailway.storage.type.LineType
import java.time.Instant

/**
 * 路線。永続化は [dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository] が担う。
 *
 * かつて JSON 形式のバージョン（V1/V2/V3）を sealed class で表していたが、データベースには
 * 現行形式（[flags] に出発方角＋分岐で選ぶ方角を持つ形）だけを格納するため単一のクラスにした。
 * 旧形式の取り込みは [dev.nikomaru.advancerailway.storage.migration.JsonImport] が変換する。
 */
data class RailwayData(
    val id: RailwayId,
    val slug: Slug,
    val group: GroupId?,
    val worldName: String,
    val lineType: LineType,
    val line: Line3D,
    val fromStation: StationId,
    val toStation: StationId,
    /** 所要時間（秒）。 */
    val timeRequired: Long,
    val startPoint: Point3D,
    val endPoint: Point3D,
    /** 出発方角（1 文字目）と各分岐点で選ぶ方角の並び（例 `EE`）。必ず 1 文字以上。 */
    val flags: String,
    /**
     * 経路が実際のレールと一致することを最後に確認できた時刻。未確認なら null。
     * 確認に失敗しても更新しない（前回成功した時刻を残し、いつから未確認かを分かるようにする）。
     */
    val lastCheckedAt: Instant? = null,
) {
    /** flags を方角列に解析する。不正な文字を含む・空の場合は null。 */
    fun branchFlags(): List<BranchDirection>? = BranchDirection.parse(flags)?.takeIf { it.isNotEmpty() }
}
