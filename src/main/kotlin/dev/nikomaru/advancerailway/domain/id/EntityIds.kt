/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.domain.id

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * 駅・路線・グループの主キー。値は **UUIDv7**（time-ordered epoch）で、採番順に単調増加する。
 *
 * 以前は人間可読な文字列（`tokyo` など）がそのまま主キー兼ファイル名だったため、
 * 表示上の都合で ID を変えると全参照が壊れていた。現在その文字列は [Slug] として別に持ち、
 * エンティティ同士の参照はここで定義する UUID だけを使う。
 */
@JvmInline
value class StationId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        /** 新しい駅 ID を採番する。 */
        fun new(): StationId = StationId(UuidCreator.getTimeOrderedEpoch())

        /** 文字列を UUID として解釈する。UUID として不正なら null。 */
        fun parse(raw: String): StationId? = parseUuid(raw)?.let(::StationId)
    }
}

@JvmInline
value class RailwayId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun new(): RailwayId = RailwayId(UuidCreator.getTimeOrderedEpoch())

        fun parse(raw: String): RailwayId? = parseUuid(raw)?.let(::RailwayId)
    }
}

@JvmInline
value class GroupId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun new(): GroupId = GroupId(UuidCreator.getTimeOrderedEpoch())

        fun parse(raw: String): GroupId? = parseUuid(raw)?.let(::GroupId)
    }
}

/**
 * [UUID.fromString] は不正な文字列で例外を投げるため、境界（コマンド引数・HTTP のパス）で
 * 「UUID かどうか」を判定するための例外を投げない版。
 */
internal fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()
