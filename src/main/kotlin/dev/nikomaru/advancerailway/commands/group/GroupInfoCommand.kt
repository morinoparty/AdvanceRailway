/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.group

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.commands.DataPaths
import dev.nikomaru.advancerailway.commands.getOrSend
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.utils.GroupUtils
import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("ar group", "advancerailway group")
@CommandPermission("advancerailway.command.group.read")
class GroupInfoCommand: KoinComponent {
    val plugin: AdvanceRailway by inject()

    @Subcommand("info")
    suspend fun info(sender: CommandSender, groupId: GroupId) {
        val data = GroupUtils.getGroupData(groupId).getOrSend(sender) { "Group not found" } ?: return
        sender.sendRichMessage("Group Info: <yellow>${data.groupId.value}")
        sender.sendRichMessage("<yellow>Display Name: <reset>${data.name}")
        sender.sendRichMessage("<yellow>Color: <reset>${data.railwayColor}")
    }

    @Subcommand("list")
    fun list(sender: CommandSender) {
        val list =
            DataPaths.groups.listFiles()?.map { it.nameWithoutExtension } ?: run {
                sender.sendRichMessage("No group found")
                return
            }
        if (list.isEmpty()) {
            sender.sendRichMessage("No group found")
            return
        }
        sender.sendRichMessage("Group List: <yellow>Click for details")
        list.forEach {
            sender.sendRichMessage("<click:run_command:/ar group info $it>$it</click>")
        }
    }
}