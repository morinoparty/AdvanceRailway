/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.group

import dev.nikomaru.advancerailway.commands.getOrSend
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.utils.GroupUtils
import org.bukkit.command.CommandSender
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.awt.Color

@Command("ar group", "advancerailway group")
@CommandPermission("advancerailway.command.group.write")
class GroupEditCommand {
    @Subcommand("set name")
    suspend fun setName(sender: CommandSender, groupId: GroupId, newName: String) {
        val data = GroupUtils.getGroupData(groupId).getOrSend(sender) { "Group not found" } ?: return
        data.copy(name = newName).save()
        sender.sendRichMessage("Station name set")
    }

    @Subcommand("set color")
    suspend fun setColor(sender: CommandSender, groupId: GroupId, r: Int, g: Int, b: Int) {
        if (r !in 0..255 || g !in 0..255 || b !in 0..255) {
            sender.sendRichMessage("Error: RGB values must each be between 0 and 255")
            return
        }
        val data = GroupUtils.getGroupData(groupId).getOrSend(sender) { "Group not found" } ?: return
        val color = Color(r, g, b)
        data.copy(railwayColor = color).save()
        sender.sendRichMessage("group color set")
    }

}
