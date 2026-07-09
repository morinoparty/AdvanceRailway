/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.utils

import dev.nikomaru.advancerailway.Line3D
import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.utils.Utils.json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color

/**
 * [Point3DSerializer] / [Line3DSerializer] / [ColorSerializer] の往復（encode -> decode -> encode）
 * の安定性と、壊れた文字列を与えた際に静かに壊れたデータへ落ちずに例外を送出することを検証する。
 * Bukkit には依存しない純粋なロジックのテスト。
 */
class SerializerTest {

    // ---- Point3DSerializer ----

    @Test
    @DisplayName("Point3D round trips through encode -> decode -> encode")
    fun point3DRoundTrips() {
        val point = Point3D(12.5, -3.0, 100.25)
        val encoded = json.encodeToString(Point3DSerializer, point)
        val decoded = json.decodeFromString(Point3DSerializer, encoded)
        assertEquals(point, decoded)
        assertEquals(encoded, json.encodeToString(Point3DSerializer, decoded))
    }

    @Test
    @DisplayName("Point3D deserialize throws instead of silently truncating on too few components")
    fun point3DDeserializeThrowsOnTooFewComponents() {
        assertThrows<Exception> {
            json.decodeFromString(Point3DSerializer, "\"1.0,2.0\"")
        }
    }

    @Test
    @DisplayName("Point3D deserialize throws on non numeric components")
    fun point3DDeserializeThrowsOnNonNumericComponents() {
        assertThrows<Exception> {
            json.decodeFromString(Point3DSerializer, "\"a,b,c\"")
        }
    }

    @Test
    @DisplayName("Point3D deserialize throws on empty string")
    fun point3DDeserializeThrowsOnEmptyString() {
        assertThrows<Exception> {
            json.decodeFromString(Point3DSerializer, "\"\"")
        }
    }

    // ---- Line3DSerializer ----

    @Test
    @DisplayName("Line3D round trips through encode -> decode -> encode")
    fun line3DRoundTrips() {
        val line = Line3D(Point3D(0.0, 64.0, 0.0), Point3D(10.0, 70.0, -5.0))
        val encoded = json.encodeToString(Line3DSerializer, line)
        val decoded = json.decodeFromString(Line3DSerializer, encoded)
        assertEquals(line.points, decoded.points)
        assertEquals(encoded, json.encodeToString(Line3DSerializer, decoded))
    }

    @Test
    @DisplayName("Line3D round trips with additional intermediate points")
    fun line3DRoundTripsWithIntermediatePoints() {
        val line = Line3D(Point3D(0.0, 64.0, 0.0), Point3D(10.0, 64.0, 0.0))
        // Non-collinear point so addPoint keeps it as a distinct vertex.
        line.addPoint(Point3D(10.0, 70.0, 5.0))
        val encoded = json.encodeToString(Line3DSerializer, line)
        val decoded = json.decodeFromString(Line3DSerializer, encoded)
        assertEquals(line.points, decoded.points)
        assertEquals(encoded, json.encodeToString(Line3DSerializer, decoded))
    }

    @Test
    @DisplayName("Line3D deserialize throws on malformed input without a separator")
    fun line3DDeserializeThrowsOnMalformedInput() {
        assertThrows<Exception> {
            json.decodeFromString(Line3DSerializer, "\"garbage\"")
        }
    }

    @Test
    @DisplayName("Line3D deserialize throws on empty string")
    fun line3DDeserializeThrowsOnEmptyString() {
        assertThrows<Exception> {
            json.decodeFromString(Line3DSerializer, "\"\"")
        }
    }

    // ---- ColorSerializer ----

    @Test
    @DisplayName("Color round trips through encode -> decode -> encode")
    fun colorRoundTrips() {
        val color = Color(255, 128, 0)
        val encoded = json.encodeToString(ColorSerializer, color)
        val decoded = json.decodeFromString(ColorSerializer, encoded)
        assertEquals(color, decoded)
        assertEquals(encoded, json.encodeToString(ColorSerializer, decoded))
    }

    @Test
    @DisplayName("Color deserialize throws on too few components")
    fun colorDeserializeThrowsOnTooFewComponents() {
        assertThrows<Exception> {
            json.decodeFromString(ColorSerializer, "\"255,0\"")
        }
    }

    @Test
    @DisplayName("Color deserialize throws on non numeric components")
    fun colorDeserializeThrowsOnNonNumericComponents() {
        assertThrows<Exception> {
            json.decodeFromString(ColorSerializer, "\"r,g,b\"")
        }
    }

    @Test
    @DisplayName("Color deserialize throws on out of range component")
    fun colorDeserializeThrowsOnOutOfRangeComponent() {
        assertThrows<Exception> {
            json.decodeFromString(ColorSerializer, "\"300,0,0\"")
        }
    }
}
