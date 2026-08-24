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
import org.jetbrains.exposed.v1.core.Table

/**
 * グループ内の駅の並び順。駅ナンバリングはこの [position] から算出する。
 *
 * 実体ではなく所属情報なので、駅・グループが消えたら CASCADE で一緒に消す。
 * 1 つの駅が複数のグループに属してよい（乗換駅）。
 */
object GroupStationTable : Table("group_stations") {
    val group = reference("group_id", GroupTable, onDelete = ReferenceOption.CASCADE)
    val station = reference("station_id", StationTable, onDelete = ReferenceOption.CASCADE)

    /** グループ内での 0 始まりの並び順。 */
    val position = integer("position")

    override val primaryKey = PrimaryKey(group, station)

    init {
        uniqueIndex(group, position)
    }
}
