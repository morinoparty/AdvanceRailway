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
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.domain.id.parseUuid
import dev.nikomaru.advancerailway.storage.database.dbQuery
import dev.nikomaru.advancerailway.storage.database.table.StationTable
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

/** 駅のデータベースアクセス。 */
class StationRepository {

    suspend fun findById(id: StationId): StationData? = dbQuery {
        StationTable.selectAll().where { StationTable.id eq id.value }.firstOrNull()?.toStationData()
    }

    suspend fun findBySlug(slug: Slug): StationData? = dbQuery {
        StationTable.selectAll().where { StationTable.slug eq slug.value }.firstOrNull()?.toStationData()
    }

    /**
     * UUID でも slug でも駅を引く。コマンド引数・HTTP のパスなど、どちらが来るか分からない境界向け。
     */
    suspend fun resolve(raw: String): StationData? {
        parseUuid(raw)?.let { uuid -> findById(StationId(uuid))?.let { return it } }
        return Slug.parse(raw)?.let { findBySlug(it) }
    }

    suspend fun findAll(): List<StationData> = dbQuery {
        StationTable.selectAll().orderBy(StationTable.slug).map { it.toStationData() }
    }

    /** 同一ワールドの駅のみを返す（最寄り駅探索用）。 */
    suspend fun findByWorld(worldName: String): List<StationData> = dbQuery {
        StationTable.selectAll().where { StationTable.world eq worldName }.map { it.toStationData() }
    }

    suspend fun count(): Long = dbQuery { StationTable.selectAll().count() }

    /** [excluding] を除いて slug が使われているかを判定する（更新時の重複チェック用）。 */
    suspend fun slugExists(slug: Slug, excluding: StationId? = null): Boolean = dbQuery {
        StationTable.selectAll().where {
            if (excluding == null) {
                StationTable.slug eq slug.value
            } else {
                (StationTable.slug eq slug.value) and (StationTable.id neq excluding.value)
            }
        }.empty().not()
    }

    suspend fun insert(data: StationData): StationData = dbQuery {
        StationTable.insert { it.putStation(data) }
        data
    }

    /** @return 対象が存在して更新できたか。 */
    suspend fun update(data: StationData): Boolean = dbQuery {
        StationTable.update({ StationTable.id eq data.id.value }) { it.putStation(data) } > 0
    }

    /** @return 削除できたか。参照している路線があると FK 制約で例外になるため、呼ぶ前に確認すること。 */
    suspend fun delete(id: StationId): Boolean = dbQuery {
        StationTable.deleteWhere { StationTable.id eq id.value } > 0
    }
}

private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.putStation(data: StationData) {
    this[StationTable.id] = data.id.value
    this[StationTable.slug] = data.slug.value
    this[StationTable.name] = data.name
    this[StationTable.world] = data.worldName
    this[StationTable.pointX] = data.point.x
    this[StationTable.pointY] = data.point.y
    this[StationTable.pointZ] = data.point.z
    this[StationTable.overrideSize] = data.overrideSize
    this[StationTable.color] = data.color.rgb
}

internal fun ResultRow.toStationData(): StationData = StationData(
    id = StationId(this[StationTable.id].value),
    slug = Slug(this[StationTable.slug]),
    name = this[StationTable.name],
    worldName = this[StationTable.world],
    point = Point3D(this[StationTable.pointX], this[StationTable.pointY], this[StationTable.pointZ]),
    overrideSize = this[StationTable.overrideSize],
    color = Color(this[StationTable.color]),
)
