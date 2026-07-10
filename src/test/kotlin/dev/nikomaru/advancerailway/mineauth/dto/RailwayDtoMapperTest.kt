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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.awt.Color

class RailwayDtoMapperTest {

    @Test
    @DisplayName("colorToHex formats colors as uppercase #RRGGBB")
    fun colorToHexFormatsUppercase() {
        assertEquals("#000000", RailwayDtoMapper.colorToHex(Color(0, 0, 0)))
        assertEquals("#FFFFFF", RailwayDtoMapper.colorToHex(Color(255, 255, 255)))
        assertEquals("#FF7F00", RailwayDtoMapper.colorToHex(Color(255, 127, 0)))
    }

    @Test
    @DisplayName("colorToHex zero-pads single-digit components")
    fun colorToHexZeroPads() {
        // 各成分が 1 桁になる値でも常に 2 桁へゼロ埋めされること。
        assertEquals("#010203", RailwayDtoMapper.colorToHex(Color(1, 2, 3)))
    }

    @Test
    @DisplayName("toPointDto copies x/y/z verbatim")
    fun toPointDtoCopiesCoordinates() {
        val dto = RailwayDtoMapper.toPointDto(Point3D(1.5, -2.0, 3.25))
        assertEquals(1.5, dto.x)
        assertEquals(-2.0, dto.y)
        assertEquals(3.25, dto.z)
    }
}
