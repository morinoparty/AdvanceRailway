/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.utils.command

import dev.nikomaru.advancerailway.Point3D
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [Point3DParser.parsePoint3D] が壊れた入力（引数の数が違う・数値でない）に対して
 * クラッシュせず null を返すことを確認する（#134 の回帰防止）。
 * Cloud 移行後はパーサ本体が null を [org.incendo.cloud.parser.ArgumentParseResult.failure] に変換する。
 */
class Point3DParserTest {

    @Test
    @DisplayName("parses a well formed comma separated point")
    fun parsesWellFormedPoint() {
        assertEquals(Point3D(1.5, 2.0, -3.25), Point3DParser.parsePoint3D("1.5,2,-3.25"))
    }

    @Test
    @DisplayName("returns null when too few components are given")
    fun returnsNullOnTooFewComponents() {
        assertNull(Point3DParser.parsePoint3D("1,2"))
    }

    @Test
    @DisplayName("returns null when too many components are given")
    fun returnsNullOnTooManyComponents() {
        assertNull(Point3DParser.parsePoint3D("1,2,3,4"))
    }

    @Test
    @DisplayName("returns null on non numeric components")
    fun returnsNullOnNonNumericComponents() {
        assertNull(Point3DParser.parsePoint3D("a,b,c"))
    }

    @Test
    @DisplayName("returns null on blank input")
    fun returnsNullOnBlankInput() {
        assertNull(Point3DParser.parsePoint3D(""))
    }
}
