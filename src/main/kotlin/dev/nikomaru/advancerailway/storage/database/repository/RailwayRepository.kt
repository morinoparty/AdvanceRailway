/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.database.repository

import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.domain.id.parseUuid
import dev.nikomaru.advancerailway.storage.database.dbQuery
import dev.nikomaru.advancerailway.storage.database.table.RailwayTable
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.storage.serialization.Line3DCodec
import dev.nikomaru.advancerailway.storage.type.LineType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

/** 路線のデータベースアクセス。 */
class RailwayRepository {

    suspend fun findById(id: RailwayId): RailwayData? = dbQuery {
        RailwayTable.selectAll().where { RailwayTable.id eq id.value }.firstOrNull()?.toRailwayData()
    }

    suspend fun findBySlug(slug: Slug): RailwayData? = dbQuery {
        RailwayTable.selectAll().where { RailwayTable.slug eq slug.value }.firstOrNull()?.toRailwayData()
    }

    /** UUID でも slug でも路線を引く。 */
    suspend fun resolve(raw: String): RailwayData? {
        parseUuid(raw)?.let { uuid -> findById(RailwayId(uuid))?.let { return it } }
        return Slug.parse(raw)?.let { findBySlug(it) }
    }

    suspend fun findAll(): List<RailwayData> = dbQuery {
        RailwayTable.selectAll().orderBy(RailwayTable.slug).map { it.toRailwayData() }
    }

    /** 始点・終点のどちらかが [stationId] である路線。駅の削除可否判定にも使う。 */
    suspend fun findByStation(stationId: StationId): List<RailwayData> = dbQuery {
        RailwayTable.selectAll()
            .where { (RailwayTable.fromStation eq stationId.value) or (RailwayTable.toStation eq stationId.value) }
            .orderBy(RailwayTable.slug)
            .map { it.toRailwayData() }
    }

    /** グループに属する路線。グループの削除可否判定にも使う。 */
    suspend fun findByGroup(groupId: GroupId): List<RailwayData> = dbQuery {
        RailwayTable.selectAll()
            .where { RailwayTable.group eq groupId.value }
            .orderBy(RailwayTable.slug)
            .map { it.toRailwayData() }
    }

    suspend fun count(): Long = dbQuery { RailwayTable.selectAll().count() }

    suspend fun slugExists(slug: Slug, excluding: RailwayId? = null): Boolean = dbQuery {
        RailwayTable.selectAll().where {
            if (excluding == null) {
                RailwayTable.slug eq slug.value
            } else {
                (RailwayTable.slug eq slug.value) and (RailwayTable.id neq excluding.value)
            }
        }.empty().not()
    }

    suspend fun insert(data: RailwayData): RailwayData = dbQuery {
        RailwayTable.insert { it.putRailway(data) }
        data
    }

    suspend fun update(data: RailwayData): Boolean = dbQuery {
        RailwayTable.update({ RailwayTable.id eq data.id.value }) { it.putRailway(data) } > 0
    }

    suspend fun delete(id: RailwayId): Boolean = dbQuery {
        RailwayTable.deleteWhere { RailwayTable.id eq id.value } > 0
    }

    /**
     * 経路の確認が成功した路線に、確認時刻を記録する。
     * 失敗した路線には触れないので、前回成功した時刻がそのまま残る。
     */
    suspend fun markChecked(ids: Collection<RailwayId>, at: Instant): Int = dbQuery {
        if (ids.isEmpty()) return@dbQuery 0
        val values = ids.map { it.value }
        RailwayTable.update({ RailwayTable.id inList values }) { it[lastCheckedAt] = at }
    }
}

private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.putRailway(data: RailwayData) {
    this[RailwayTable.id] = data.id.value
    this[RailwayTable.slug] = data.slug.value
    this[RailwayTable.group] = data.group?.value
    this[RailwayTable.world] = data.worldName
    this[RailwayTable.lineType] = data.lineType.name
    this[RailwayTable.fromStation] = data.fromStation.value
    this[RailwayTable.toStation] = data.toStation.value
    this[RailwayTable.timeRequired] = data.timeRequired
    this[RailwayTable.startX] = data.startPoint.x
    this[RailwayTable.startY] = data.startPoint.y
    this[RailwayTable.startZ] = data.startPoint.z
    this[RailwayTable.endX] = data.endPoint.x
    this[RailwayTable.endY] = data.endPoint.y
    this[RailwayTable.endZ] = data.endPoint.z
    this[RailwayTable.flags] = data.flags
    this[RailwayTable.line] = Line3DCodec.encode(data.line)
    this[RailwayTable.lastCheckedAt] = data.lastCheckedAt
}

internal fun ResultRow.toRailwayData(): RailwayData = RailwayData(
    id = RailwayId(this[RailwayTable.id].value),
    slug = Slug(this[RailwayTable.slug]),
    group = this[RailwayTable.group]?.let { GroupId(it.value) },
    worldName = this[RailwayTable.world],
    lineType = LineType.valueOf(this[RailwayTable.lineType]),
    line = Line3DCodec.decode(this[RailwayTable.line]),
    fromStation = StationId(this[RailwayTable.fromStation].value),
    toStation = StationId(this[RailwayTable.toStation].value),
    timeRequired = this[RailwayTable.timeRequired],
    startPoint = Point3D(this[RailwayTable.startX], this[RailwayTable.startY], this[RailwayTable.startZ]),
    endPoint = Point3D(this[RailwayTable.endX], this[RailwayTable.endY], this[RailwayTable.endZ]),
    flags = this[RailwayTable.flags],
    lastCheckedAt = this[RailwayTable.lastCheckedAt],
)
