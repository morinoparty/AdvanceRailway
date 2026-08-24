/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.database.table

import com.github.f4b6a3.uuid.UuidCreator
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.util.UUID

/**
 * UUIDv7 を主キーに採番するテーブルの共通基底。
 *
 * Exposed の `UUIDTable` は `UUID.randomUUID()`（v4）を採番するため使えない。
 * v7 は時刻順に単調増加するので、主キーのインデックスが挿入順に伸びて断片化しにくく、
 * 採番順＝作成順として扱える。
 */
abstract class UuidV7Table(name: String) : IdTable<UUID>(name) {
    // Table.uuid() は kotlin.uuid.Uuid を返すため、Bukkit と同じ java.util.UUID を扱う javaUUID を使う。
    final override val id: Column<EntityID<UUID>> =
        javaUUID("id").clientDefault { UuidCreator.getTimeOrderedEpoch() }.entityId()

    final override val primaryKey = PrimaryKey(id)
}

/** slug の最大長。実運用の ID は十数文字だが、余裕を持たせる。 */
const val SLUG_LENGTH = 64

/** 表示名の最大長。 */
const val NAME_LENGTH = 128

/** ワールド名の最大長。 */
const val WORLD_LENGTH = 64
