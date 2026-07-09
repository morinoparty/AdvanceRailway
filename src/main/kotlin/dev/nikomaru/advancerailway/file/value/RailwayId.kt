/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.value

import dev.nikomaru.advancerailway.utils.RailwayUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RailwayNameSerializer::class)
class RailwayId(val value: String) {
    init {
        require(IdValidation.isValid(value)) { "Invalid railway ID: \"$value\"" }
    }

    suspend fun toData() = RailwayUtils.getRailwayData(this).getOrNull()
    override fun toString(): String = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RailwayId) return false

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

object RailwayNameSerializer: KSerializer<RailwayId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RailwayName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RailwayId) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): RailwayId {
        return RailwayId(decoder.decodeString())
    }
}