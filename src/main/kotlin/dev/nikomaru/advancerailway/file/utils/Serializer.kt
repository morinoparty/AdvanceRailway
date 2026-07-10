/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.utils

import dev.nikomaru.advancerailway.Line3D
import dev.nikomaru.advancerailway.Point3D
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.awt.Color
import java.util.*

object UUIDSerializer: KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
}

// OfflinePlayer <==> UUID
object OfflinePlayerSerializer: KSerializer<OfflinePlayer> {
    override val descriptor = PrimitiveSerialDescriptor("OfflinePlayer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OfflinePlayer {
        return Bukkit.getOfflinePlayer(UUID.fromString(decoder.decodeString()))
    }

    override fun serialize(encoder: Encoder, value: OfflinePlayer) {
        encoder.encodeString(value.uniqueId.toString())
    }
}

object Point3DSerializer: KSerializer<Point3D> {
    override val descriptor = PrimitiveSerialDescriptor("Point3D", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Point3D {
        val split = decoder.decodeString().split(",")
        return Point3D(split[0].toDouble(), split[1].toDouble(), split[2].toDouble())
    }

    override fun serialize(encoder: Encoder, value: Point3D) {
        encoder.encodeString("${value.x},${value.y},${value.z}")
    }
}

object Line3DSerializer: KSerializer<Line3D> {
    override val descriptor = PrimitiveSerialDescriptor("Line3D", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Line3D {
        val points = decoder.decodeString().split(":").map { it.drop(1) }.map { it.dropLast(1) }.map { it.split(",") }
            .map { Point3D(it[0].toDouble(), it[1].toDouble(), it[2].toDouble()) }
        val line = Line3D(points[0], points[1])
        points.drop(2).forEach { line.addPoint(it) }

        return line
    }

    override fun serialize(encoder: Encoder, value: Line3D) {
        val points = value.points
        val str = points.joinToString(":") { "(${it.x},${it.y},${it.z})" }
        encoder.encodeString(str)
    }
}

object ColorSerializer: KSerializer<Color> {
    override val descriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Color {
        val split = decoder.decodeString().split(",")
        return Color(split[0].toInt(), split[1].toInt(), split[2].toInt())
    }

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeString("${value.red},${value.green},${value.blue}")
    }
}

object WorldSerializer: KSerializer<org.bukkit.World> {
    override val descriptor = PrimitiveSerialDescriptor("World", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): org.bukkit.World {
        val name = decoder.decodeString()
        return Bukkit.getWorld(name)
            ?: throw SerializationException("World not found: \"$name\"")
    }

    override fun serialize(encoder: Encoder, value: org.bukkit.World) {
        encoder.encodeString(value.name)
    }
}