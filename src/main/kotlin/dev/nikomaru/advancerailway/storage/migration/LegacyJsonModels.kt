/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.migration

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 旧 JSON 形式（`data/{stations,railways,groups}/<id>.json`）を読むためだけの型。
 *
 * 本体のドメインモデルとは意図的に切り離してある。ドメイン側はデータベース前提の形に
 * なっており、旧形式の都合（駅の numbering、路線のバージョン差）を持ち込みたくないため。
 * 取り込みが済んだサーバーでは不要になるので、[JsonImport] ごと将来削除できる。
 *
 * 座標・色は旧シリアライザと同じ文字列表現（`x,y,z` と `r,g,b`）で入っている。
 */
@Serializable
internal data class LegacyStation(
    val stationId: String,
    val name: String,
    /** 旧形式の駅ナンバリング。現在はグループ側が持つため取り込まない。 */
    val numbering: String? = null,
    val world: String,
    val point: String,
    val overrideSize: Double? = null,
    val color: String? = null,
)

@Serializable
internal data class LegacyGroup(
    val groupId: String,
    val name: String,
    val railwayColor: String,
)

/**
 * 路線の旧形式。V1/V2/V3 を 1 つの形で受ける。
 *
 * - V3: `version = 3`、[flags] は `"EE"` のような文字列
 * - V2: [flags] は方角名の配列、出発方角は [directionPoint] から求める
 * - V1: [flags] を持たない。分岐情報が無く再トレースにはワールドのブロック読みが必要なため取り込まない
 */
@Serializable
internal data class LegacyRailway(
    val id: String,
    val group: String? = null,
    val world: String,
    val lineType: String,
    val line: String,
    val fromStation: String,
    val toStation: String,
    val timeRequired: Long,
    val startPoint: String,
    val endPoint: String,
    val directionPoint: String? = null,
    val flags: JsonElement? = null,
    val version: Int? = null,
)
