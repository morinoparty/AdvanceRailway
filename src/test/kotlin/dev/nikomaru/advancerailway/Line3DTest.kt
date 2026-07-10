/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Line3DTest {
    @Test
    fun testDistanceTo() {
        val point1 = Point3D(0.0, 0.0, 0.0)
        val point2 = Point3D(1.0, 2.0, 2.0)
        assertEquals(3.0, point1.distanceTo(point2))
    }

    @Test
    fun testGetInclination() {
        val point1 = Point3D(0.0, 0.0, 0.0)
        val point2 = Point3D(1.0, 1.0, 1.0)
        assertEquals(Pair(0.9553166181245092, 0.9553166181245092), point1.getInclination(point2))
    }

    @Test
    fun joinLine1() {
        val point1 = Point3D(0.0, 0.0, 0.0)
        val point2 = Point3D(1.0, 2.0, 3.0)
        val point3 = Point3D(1.0, 2.0, 3.0)
        val point4 = Point3D(2.0, 4.0, 6.0)
        val line1 = Line3D(point1, point2)
        val line2 = Line3D(point3, point4)
        line1.joinLine(line2)
        assertEquals(2, line1.points.size)
    }

    @Test
    fun joinLine2() {
        val point1 = Point3D(0.0, 0.0, 0.0)
        val point2 = Point3D(1.0, 2.0, 3.0)
        val point3 = Point3D(2.0, 4.0, 6.0)
        val point4 = Point3D(3.0, 6.0, 9.0)
        val line1 = Line3D(point1, point2)
        val line2 = Line3D(point3, point4)
        line1.joinLine(line2)
        println(line1)
        assertEquals(2, line1.points.size)
    }
}