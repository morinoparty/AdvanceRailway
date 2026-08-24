/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.database.repository

import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.domain.id.parseUuid
import dev.nikomaru.advancerailway.domain.numbering.StationNumbering
import dev.nikomaru.advancerailway.storage.database.dbQuery
import dev.nikomaru.advancerailway.storage.database.table.GroupStationTable
import dev.nikomaru.advancerailway.storage.database.table.GroupTable
import dev.nikomaru.advancerailway.storage.database.table.StationTable
import dev.nikomaru.advancerailway.storage.model.GroupData
import dev.nikomaru.advancerailway.storage.model.StationData
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.awt.Color

/** グループ内での駅の位置と、そこから算出したナンバリング。 */
data class StationInGroup(val station: StationData, val position: Int, val numbering: String?)

/** 駅が属するグループと、その中での位置・ナンバリング。 */
data class GroupOfStation(val group: GroupData, val position: Int, val numbering: String?)

/** グループ（路線）と、グループ内の駅の並び順のデータベースアクセス。 */
class GroupRepository {

    suspend fun findById(id: GroupId): GroupData? = dbQuery {
        GroupTable.selectAll().where { GroupTable.id eq id.value }.firstOrNull()?.toGroupData()
    }

    suspend fun findBySlug(slug: Slug): GroupData? = dbQuery {
        GroupTable.selectAll().where { GroupTable.slug eq slug.value }.firstOrNull()?.toGroupData()
    }

    /** UUID でも slug でもグループを引く。 */
    suspend fun resolve(raw: String): GroupData? {
        parseUuid(raw)?.let { uuid -> findById(GroupId(uuid))?.let { return it } }
        return Slug.parse(raw)?.let { findBySlug(it) }
    }

    suspend fun findAll(): List<GroupData> = dbQuery {
        GroupTable.selectAll().orderBy(GroupTable.slug).map { it.toGroupData() }
    }

    suspend fun count(): Long = dbQuery { GroupTable.selectAll().count() }

    suspend fun slugExists(slug: Slug, excluding: GroupId? = null): Boolean = dbQuery {
        GroupTable.selectAll().where {
            if (excluding == null) {
                GroupTable.slug eq slug.value
            } else {
                (GroupTable.slug eq slug.value) and (GroupTable.id neq excluding.value)
            }
        }.empty().not()
    }

    suspend fun insert(data: GroupData): GroupData = dbQuery {
        GroupTable.insert { it.putGroup(data) }
        data
    }

    suspend fun update(data: GroupData): Boolean = dbQuery {
        GroupTable.update({ GroupTable.id eq data.id.value }) { it.putGroup(data) } > 0
    }

    suspend fun delete(id: GroupId): Boolean = dbQuery {
        GroupTable.deleteWhere { GroupTable.id eq id.value } > 0
    }

    /**
     * グループに属する駅を並び順で返す。ナンバリングもここで算出する。
     */
    suspend fun stationsOf(id: GroupId): List<StationInGroup> = dbQuery {
        val group = GroupTable.selectAll().where { GroupTable.id eq id.value }.firstOrNull()?.toGroupData()
            ?: return@dbQuery emptyList()
        (GroupStationTable innerJoin StationTable)
            .selectAll()
            .where { GroupStationTable.group eq id.value }
            .orderBy(GroupStationTable.position)
            .map { row ->
                val position = row[GroupStationTable.position]
                StationInGroup(
                    station = row.toStationData(),
                    position = position,
                    numbering = StationNumbering.format(group.numberingPrefix, group.numberingStart, position),
                )
            }
    }

    /**
     * 駅が属するグループを、その中での位置とナンバリングつきで返す。
     * 乗換駅は複数のグループに属し得るため、駅単体の表示ではこれを列挙する。
     */
    suspend fun groupsOf(stationId: StationId): List<GroupOfStation> = dbQuery {
        (GroupStationTable innerJoin GroupTable)
            .selectAll()
            .where { GroupStationTable.station eq stationId.value }
            .orderBy(GroupTable.slug)
            .map { row ->
                val group = row.toGroupData()
                val position = row[GroupStationTable.position]
                GroupOfStation(
                    group = group,
                    position = position,
                    numbering = StationNumbering.format(group.numberingPrefix, group.numberingStart, position),
                )
            }
    }

    /**
     * 全駅について、所属グループと並び順・ナンバリングを 1 クエリでまとめて引く。
     * マーカー再描画や一覧 API のように全駅を一度に扱う経路で、駅ごとに問い合わせないためのもの。
     */
    suspend fun allGroupsOfStations(): Map<StationId, List<GroupOfStation>> = dbQuery {
        (GroupStationTable innerJoin GroupTable)
            .selectAll()
            .orderBy(GroupTable.slug)
            .map { row ->
                val group = row.toGroupData()
                val position = row[GroupStationTable.position]
                StationId(row[GroupStationTable.station].value) to GroupOfStation(
                    group = group,
                    position = position,
                    numbering = StationNumbering.format(group.numberingPrefix, group.numberingStart, position),
                )
            }
            .groupBy({ it.first }, { it.second })
    }

    /**
     * グループの駅の並びを一括で置き換える。position は [stations] の並びから 0 起点で振り直す。
     *
     * 1 件ずつの挿入・移動ではなく一括置換にしているのは、position の連番と一意制約を
     * 常に矛盾なく保つため（同じ入力を 2 回適用しても結果が変わらない）。
     */
    suspend fun replaceStations(id: GroupId, stations: List<StationId>) = dbQuery {
        GroupStationTable.deleteWhere { GroupStationTable.group eq id.value }
        stations.forEachIndexed { index, stationId ->
            GroupStationTable.insert {
                it[group] = id.value
                it[station] = stationId.value
                it[position] = index
            }
        }
    }
}

private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.putGroup(data: GroupData) {
    this[GroupTable.id] = data.id.value
    this[GroupTable.slug] = data.slug.value
    this[GroupTable.name] = data.name
    this[GroupTable.color] = data.railwayColor.rgb
    this[GroupTable.numberingPrefix] = data.numberingPrefix
    this[GroupTable.numberingStart] = data.numberingStart
}

internal fun ResultRow.toGroupData(): GroupData = GroupData(
    id = GroupId(this[GroupTable.id].value),
    slug = Slug(this[GroupTable.slug]),
    name = this[GroupTable.name],
    railwayColor = Color(this[GroupTable.color]),
    numberingPrefix = this[GroupTable.numberingPrefix],
    numberingStart = this[GroupTable.numberingStart],
)
