/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */


package dev.nikomaru.advancerailway.integration.mineauth.dto

import kotlinx.serialization.Serializable

/**
 * 書き込み系エンドポイントのリクエストボディ。
 *
 * 更新（PATCH）系は全フィールドが省略可能で、**`null` は「変更しない」**を意味する。
 * kotlinx.serialization では「キーが無い」と「明示的な null」を区別できないため、
 * 値を消したい場合は [unset] にフィールド名を並べる（例: `"unset": ["overrideSize"]`）。
 */

/** 駅の新規作成。 */
@Serializable
data class CreateStationRequest(
    val slug: String,
    val name: String,
    val world: String,
    val point: PointDto,
    val overrideSize: Double? = null,
    /** #RRGGBB 形式。省略時は slug から決定論的に導出する。 */
    val color: String? = null,
)

/** 駅の部分更新。 */
@Serializable
data class UpdateStationRequest(
    val slug: String? = null,
    val name: String? = null,
    val world: String? = null,
    val point: PointDto? = null,
    val overrideSize: Double? = null,
    val color: String? = null,
    /** 値を消したいフィールド名（`overrideSize` のみ対応）。 */
    val unset: List<String> = emptyList(),
)

/** グループの新規作成。 */
@Serializable
data class CreateGroupRequest(
    val slug: String,
    val name: String,
    /** #RRGGBB 形式。省略時はランダムな色を割り当てる。 */
    val color: String? = null,
    val numberingPrefix: String? = null,
    val numberingStart: Int? = null,
)

/** グループの部分更新。 */
@Serializable
data class UpdateGroupRequest(
    val slug: String? = null,
    val name: String? = null,
    val color: String? = null,
    val numberingPrefix: String? = null,
    val numberingStart: Int? = null,
    /** 値を消したいフィールド名（`numberingPrefix` のみ対応）。 */
    val unset: List<String> = emptyList(),
)

/**
 * 路線の新規作成。始点・終点・flags からサーバー側がレールをトレースして経路を求める
 * （`/ar railway add` と同じ入力）。
 */
@Serializable
data class CreateRailwayRequest(
    val slug: String,
    val world: String,
    val startPoint: PointDto,
    val endPoint: PointDto,
    /** 出発方角＋各分岐点で選ぶ方角の並び（例 `EE`）。 */
    val flags: String,
    /** グループの ID または slug。 */
    val group: String? = null,
    /** [dev.nikomaru.advancerailway.storage.type.LineType] の名前。省略時は `UP_DOWN_LINE`。 */
    val lineType: String? = null,
    /** 始点駅の ID または slug。省略時は始点座標の最寄り駅。 */
    val fromStation: String? = null,
    /** 終点駅の ID または slug。省略時は終点座標の最寄り駅。 */
    val toStation: String? = null,
)

/**
 * 路線の部分更新。
 * [startPoint] [endPoint] [flags] が揃って指定された場合のみ経路を引き直す。
 */
@Serializable
data class UpdateRailwayRequest(
    val slug: String? = null,
    val group: String? = null,
    val lineType: String? = null,
    val timeRequired: Long? = null,
    val fromStation: String? = null,
    val toStation: String? = null,
    val startPoint: PointDto? = null,
    val endPoint: PointDto? = null,
    val flags: String? = null,
    /** 値を消したいフィールド名（`group` のみ対応）。 */
    val unset: List<String> = emptyList(),
)

/** グループ内の駅の並びの一括置換。並びがそのままナンバリング順になる。 */
@Serializable
data class ReplaceGroupStationsRequest(
    /** 駅の ID または slug を、ナンバリング順に並べたもの。 */
    val stations: List<String>,
)
