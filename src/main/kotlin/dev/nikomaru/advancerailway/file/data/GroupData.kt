/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.data

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.AdvanceRailway.Companion.plugin
import dev.nikomaru.advancerailway.file.utils.ColorSerializer
import dev.nikomaru.advancerailway.file.utils.writeAtomically
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.utils.Utils.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.Color

@Serializable
data class GroupData(
    val groupId: GroupId,
    val name: String,
    val railwayColor: @Serializable(with = ColorSerializer::class) Color,
): KoinComponent {
    val plugin: AdvanceRailway by inject()
    fun save() {
        val file = plugin.dataFolder.resolve("data").resolve("groups").resolve("${groupId.value}.json")
        writeAtomically(file, json.encodeToString(this))
    }

    companion object {
        fun load(groupId: GroupId): GroupData {
            val file = plugin.dataFolder.resolve("data").resolve("groups").resolve("${groupId.value}.json")
            return json.decodeFromString(file.readText())
        }
    }
}