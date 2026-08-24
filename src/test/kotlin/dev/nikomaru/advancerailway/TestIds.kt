/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway

import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.StationId
import java.util.UUID

/**
 * テストで「読める名前」から決定論的に ID を作るためのヘルパー。
 *
 * 主キーは UUIDv7 になったが、テストのフィクスチャは `fti` のような読める鍵で書けたほうが分かりやすい。
 * [UUID.nameUUIDFromBytes] で鍵から常に同じ UUID を導くことで、同じ鍵＝同じ ID として扱える。
 * 本番の採番（時刻順の v7）とは別物なので、プロダクションコードからは使わない。
 */
object TestIds {
    fun uuid(key: String): UUID = UUID.nameUUIDFromBytes(key.toByteArray())

    fun station(key: String): StationId = StationId(uuid(key))

    fun railway(key: String): RailwayId = RailwayId(uuid(key))

    fun group(key: String): GroupId = GroupId(uuid(key))
}
