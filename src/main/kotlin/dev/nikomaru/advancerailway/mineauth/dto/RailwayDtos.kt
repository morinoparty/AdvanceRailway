/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.mineauth.dto

import kotlinx.serialization.Serializable

/**
 * MineAuth の HTTP API 経由で返却する DTO 群。
 *
 * これらは Bukkit 型（World / Color など）や独自の value class を含まず、
 * プリミティブと文字列のみで構成する。
 * [RailwayApiHandler] はこれらの `@Serializable` DTO をそのまま返し、
 * JSON へのシリアライズは MineAuth 側が各 DTO のシリアライザを解決して行う。
 */

/** 3 次元座標。 */
@Serializable
data class PointDto(
    val x: Double,
    val y: Double,
    val z: Double,
)

/** 駅情報。 */
@Serializable
data class StationDto(
    val id: String,
    val name: String,
    val numbering: String?,
    val world: String,
    val point: PointDto,
    val overrideSize: Double?,
    /** #RRGGBB 形式のカラーコード。 */
    val color: String,
)

/** 路線情報。 */
@Serializable
data class RailwayDto(
    val id: String,
    val group: String?,
    val world: String,
    val lineType: String,
    val fromStation: String,
    val toStation: String,
    /** 所要時間（秒）。 */
    val timeRequired: Long,
    val startPoint: PointDto,
    val endPoint: PointDto,
    val directionPoint: PointDto,
)

/** グループ情報。 */
@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    /** #RRGGBB 形式のカラーコード。 */
    val color: String,
)

/** 駅一覧レスポンス。 */
@Serializable
data class StationsResponse(val stations: List<StationDto>)

/** 路線一覧レスポンス。 */
@Serializable
data class RailwaysResponse(val railways: List<RailwayDto>)

/** グループ一覧レスポンス。 */
@Serializable
data class GroupsResponse(val groups: List<GroupDto>)

/** 経路の 1 区間。レール乗車または徒歩移動の単位。 */
@Serializable
data class RouteLegDto(
    /** 移動手段。`"RAIL"`（路線）または `"WALK"`（徒歩）。 */
    val mode: String,
    /** レール区間で乗る路線 ID。徒歩区間では `null`。 */
    val railway: String?,
    /** 区間の出発駅 ID。起点が現在地の場合は `null`（現状 API では常に駅）。 */
    val from: String?,
    /** 区間の出発駅の表示名（ID フォールバックあり）。起点が現在地の場合は `null`。 */
    val fromName: String?,
    /** 区間の到着駅 ID。 */
    val to: String,
    /** 区間の到着駅の表示名（ID フォールバックあり）。 */
    val toName: String,
    /** この区間の所要時間（秒）。 */
    val timeRequired: Long,
    /** 路線が属するグループ ID。徒歩区間や無所属の場合は `null`。 */
    val group: String?,
    /** 路線（グループ）の表示名。徒歩区間や無所属の場合は `null`。 */
    val line: String?,
)

/** 駅間経路レスポンス。 */
@Serializable
data class RouteResponse(
    val from: String,
    /** 出発駅の表示名（ID フォールバックあり）。 */
    val fromName: String,
    val to: String,
    /** 到着駅の表示名（ID フォールバックあり）。 */
    val toName: String,
    /** 合計所要時間（秒）。 */
    val totalTime: Long,
    /** 出発駅から到着駅まで、通過順に並んだ駅 ID 列。 */
    val stations: List<String>,
    val legs: List<RouteLegDto>,
)

/** ネットワークの件数サマリ。 */
@Serializable
data class StatsResponse(
    val stations: Int,
    val railways: Int,
    val groups: Int,
)
