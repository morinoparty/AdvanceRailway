/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.utils

import dev.nikomaru.advancerailway.domain.geometry.Point3D
import kotlinx.serialization.json.Json
import org.bukkit.Location

object Utils {
    /** 設定ファイル（config.json）の読み書きに使う。データ本体はデータベースに入る。 */
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun Location.toPoint3D() = Point3D(x, y, z)

    fun Point3D.toLocation(world: org.bukkit.World) = Location(world, x, y, z)
}
