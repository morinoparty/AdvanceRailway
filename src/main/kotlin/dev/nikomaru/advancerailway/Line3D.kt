/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway

import dev.nikomaru.advancerailway.file.utils.Line3DSerializer
import kotlinx.serialization.Serializable

@Serializable(with = Line3DSerializer::class)
class Line3D(first: Point3D, second: Point3D) {
    var points = arrayListOf<Point3D>()

    init {
        points.add(first)
        points.add(second)
    }


    fun removePoint(point: Point3D) {
        points.remove(point)
    }

    fun joinLine(line: Line3D): Line3D {
        val lastLinesInclination = points[points.size - 2].getInclination(points.last())
        val firstLinesInclination = line.points[0].getInclination(line.points[1])
        if (points.last() == line.points[0]) {
            if (lastLinesInclination == firstLinesInclination) {
                points = ArrayList(points.dropLast(1))
            }
            points.addAll(line.points.drop(1))
        } else {
            if (lastLinesInclination == firstLinesInclination) {
                points = ArrayList(points.dropLast(1))
                points.addAll(line.points.drop(1))
            } else {
                points.addAll(line.points)
            }
        }
        return this
    }

    fun addPoint(point: Point3D) {
        val lastLinesInclination = points[points.size - 2].getInclination(points.last())
        val firstLinesInclination = points.last().getInclination(point)
        if (points.last() != point) {
            if (lastLinesInclination == firstLinesInclination) {
                points = ArrayList(points.dropLast(1))
                points.add(point)
            } else {
                points.add(point)
            }
        }
    }

    fun getLength(): Double {
        var length = 0.0
        for (i in 0 until points.size - 1) {
            length += points[i].distanceTo(points[i + 1])
        }
        return length
    }

    override fun toString(): String {
        return "Line3D(points=$points)"
    }
}

