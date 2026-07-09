/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
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
