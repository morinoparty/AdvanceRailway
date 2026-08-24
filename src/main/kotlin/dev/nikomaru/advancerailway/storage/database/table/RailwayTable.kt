/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.database.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * 路線テーブル。
 *
 * 形式は旧 JSON の V3 相当のみを持つ（`flags` に出発方角＋分岐で選ぶ方角の並び）。
 * `line` は点列を `(x,y,z):(x,y,z):…` の文字列で保持する。これは旧 JSON と同じ表現で、
 * 点列を再構築せずそのまま復元するためのもの（`Line3D.addPoint` の共線圧縮で
 * 点列が変わると `/ar railway check` が経路変化を誤検出する）。
 *
 * 参照する駅・グループは FK で縛る。駅やグループを消す前に路線を消す必要があるため、
 * 削除 API はあらかじめ参照の有無を調べて 409 を返す。
 */
object RailwayTable : UuidV7Table("railways") {
    val slug = varchar("slug", SLUG_LENGTH).uniqueIndex()

    val group = reference("group_id", GroupTable, onDelete = ReferenceOption.RESTRICT).nullable().index()

    val world = varchar("world", WORLD_LENGTH)

    /** [dev.nikomaru.advancerailway.storage.type.LineType] の名前。 */
    val lineType = varchar("line_type", 16)

    val fromStation = reference("from_station_id", StationTable, onDelete = ReferenceOption.RESTRICT).index()
    val toStation = reference("to_station_id", StationTable, onDelete = ReferenceOption.RESTRICT).index()

    /** 所要時間（秒）。 */
    val timeRequired = long("time_required")

    val startX = double("start_x")
    val startY = double("start_y")
    val startZ = double("start_z")
    val endX = double("end_x")
    val endY = double("end_y")
    val endZ = double("end_z")

    /** 出発方角＋各分岐で選ぶ方角の並び（例 `EE`）。 */
    val flags = varchar("flags", 255)

    /** 経路の点列（`(x,y,z):(x,y,z):…`）。 */
    val line = text("line")

    /**
     * 経路が実際のレールと一致することを最後に確認できた時刻。
     * 確認が成功したときだけ更新する（失敗しても前回の成功時刻は残す）ため、
     * 「いつから未確認か」がそのまま読み取れる。未確認なら null。
     */
    val lastCheckedAt = timestamp("last_checked_at").nullable()
}
