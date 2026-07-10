/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.value

import dev.nikomaru.advancerailway.utils.StationUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable(with = StationNameSerializer::class)
class StationId(val value: String) {
    init {
        require(IdValidation.isValid(value)) { "Invalid station ID: \"$value\"" }
    }

    suspend fun toData() = StationUtils.getStationData(this).getOrNull()

    override fun toString(): String {
        return value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StationId) return false

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

object StationNameSerializer: KSerializer<StationId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StationName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StationId) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): StationId {
        return StationId(decoder.decodeString())
    }
}