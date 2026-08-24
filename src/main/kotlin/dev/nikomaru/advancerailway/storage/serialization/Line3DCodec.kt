/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.serialization

import dev.nikomaru.advancerailway.domain.geometry.Line3D
import dev.nikomaru.advancerailway.domain.geometry.Point3D

/**
 * [Line3D] と `(x,y,z):(x,y,z):…` 形式の文字列との相互変換。
 *
 * 旧 JSON 形式と同じ表現をそのままデータベースの `line` 列に使う。
 * デコードでは [Line3D.addPoint] を通さず点列を直接復元する。addPoint は共線点の圧縮を伴い、
 * 保存時と点列が変わってしまうため（redraw 直後でも `/ar railway check` が経路変化を
 * 誤検出していた原因がこれだった）。
 */
object Line3DCodec {

    /** 点列を文字列へ変換する。 */
    fun encode(line: Line3D): String = line.points.joinToString(":") { "(${it.x},${it.y},${it.z})" }

    /**
     * 文字列から点列を復元する。
     *
     * @throws IllegalArgumentException 点が 2 つ未満、または数値として解釈できない要素を含む場合。
     */
    fun decode(raw: String): Line3D {
        val points = raw.split(":")
            .map { it.removePrefix("(").removeSuffix(")").split(",") }
            .map { parts ->
                require(parts.size == 3) { "Invalid point in line: \"$raw\"" }
                Point3D(parts[0].toDouble(), parts[1].toDouble(), parts[2].toDouble())
            }
        require(points.size >= 2) { "Line must have at least two points: \"$raw\"" }
        val line = Line3D(points[0], points[1])
        line.points = ArrayList(points)
        return line
    }
}
