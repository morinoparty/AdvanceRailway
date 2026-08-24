/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.model

import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.Slug
import java.awt.Color

/**
 * グループ（路線）。永続化は
 * [dev.nikomaru.advancerailway.storage.database.repository.GroupRepository] が担う。
 *
 * 駅ナンバリングの接頭辞と開始番号を持つ。個々の駅の番号は、グループ内の駅の並び順
 * （`group_stations.position`）と合わせて
 * [dev.nikomaru.advancerailway.domain.numbering.StationNumbering] が組み立てる。
 */
data class GroupData(
    val id: GroupId,
    val slug: Slug,
    val name: String,
    val railwayColor: Color,
    /** ナンバリングの接頭辞（`JY` など）。null ならこのグループはナンバリングを持たない。 */
    val numberingPrefix: String? = null,
    /** ナンバリングの開始番号。 */
    val numberingStart: Int = 1,
)
