/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.group

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.commands.DataPaths
import dev.nikomaru.advancerailway.file.FileLoader
import dev.nikomaru.advancerailway.file.data.GroupData
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.utils.Utils.json
import kotlinx.serialization.decodeFromString
import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.awt.Color

@Command("ar group", "advancerailway group")
@CommandPermission("advancerailway.command.group.write")
class GroupMainCommand: KoinComponent {
    val plugin: AdvanceRailway by inject()

    @Subcommand("add")
    fun add(sender: CommandSender, id: String, name: String) {
        if (!IdValidation.isValid(id)) {
            sender.sendRichMessage("Error: Invalid group ID \"$id\"")
            return
        }
        val groupId = GroupId(id)
        val data = GroupData(groupId, name, Color.getHSBColor(Math.random().toFloat(), 1.0f, 1.0f))
        data.save()
        sender.sendRichMessage("Group added")
    }

    @Subcommand("remove")
    suspend fun remove(sender: CommandSender, id: GroupId) {
        val file = DataPaths.groups.resolve("${id.value}.json")
        if (!file.exists()) {
            sender.sendRichMessage("Group not found")
            return
        }
        val dependents = (DataPaths.railways.listFiles() ?: emptyArray()).mapNotNull { railwayFile ->
            runCatching { json.decodeFromString<RailwayData>(railwayFile.readText()) }.getOrNull()
        }.filter { it.group == id }.map { it.id.value }
        if (dependents.isNotEmpty()) {
            sender.sendRichMessage(
                "Error: Cannot remove group <yellow>${id.value}</yellow>; " +
                    "referenced by railway(s): ${dependents.joinToString(", ")}"
            )
            return
        }
        file.delete()
        FileLoader.mapDataLoad()
        sender.sendRichMessage("Group removed")
    }

}