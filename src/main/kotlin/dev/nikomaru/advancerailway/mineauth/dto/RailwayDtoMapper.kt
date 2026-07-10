/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.mineauth.dto

import dev.nikomaru.advancerailway.Point3D
import java.awt.Color

/**
 * DTO へのマッピングのうち、Bukkit に依存しない純粋な変換ロジックを提供するオブジェクト。
 * サーバーを起動しなくても単体テストできるよう、ここへ切り出している。
 */
object RailwayDtoMapper {

    /** [java.awt.Color] を `#RRGGBB` 形式（大文字・各成分 2 桁ゼロ埋め）へ変換する。 */
    fun colorToHex(color: Color): String = "#%02X%02X%02X".format(color.red, color.green, color.blue)

    /** [Point3D] を [PointDto] へ変換する。 */
    fun toPointDto(point: Point3D): PointDto = PointDto(point.x, point.y, point.z)
}
