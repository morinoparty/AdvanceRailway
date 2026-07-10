/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.file.FileLoader
import dev.nikomaru.advancerailway.listener.RailClickEvent
import dev.nikomaru.advancerailway.utils.Utils.toPoint3D
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import revxrsal.commands.help.CommandHelp

@Command("ar", "advancerailway")
@CommandPermission("advancerailway.command.common")
class GeneralCommand: KoinComponent {
    val plugin: AdvanceRailway by inject()

    @Subcommand("info")
    fun info(sender: CommandSender) {
        sender.sendRichMessage("AdvanceRailway Information")
        sender.sendRichMessage("Version: ${plugin.pluginMeta.version}")
        sender.sendRichMessage("Author: ${plugin.pluginMeta.authors.joinToString(",")}")
        sender.sendRichMessage("Website: ${plugin.pluginMeta.website}")
    }

    @Subcommand("targetBlock")
    fun targetBlock(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendRichMessage("This command can only be executed by players.")
            return
        }
        val block = sender.getTargetBlockExact(10)
        val location = block?.location ?: run {
            sender.sendRichMessage("No block found.")
            return
        }
        sender.sendRichMessage("Block: ${location.toPoint3D().toPlainString()}")
    }

    @Subcommand("help")
    @Description("Shows the help menu")
    fun help(sender: CommandSender, helpEntries: CommandHelp<String>, @Default("1") page: Int) {
        for (entry in helpEntries.paginate(page, 7))  // 7 entries per page
            sender.sendRichMessage(entry)
    }

    @Subcommand("inspect")
    fun detect(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendRichMessage("This command can only be executed by players.")
            return
        }
        sender.sendRichMessage("Click on a block to detect the railway.")
        RailClickEvent.detect.add(sender)
    }

    @Subcommand("reload")
    suspend fun reload(sender: CommandSender) {
        FileLoader.load()
        sender.sendRichMessage("Reloaded map and config data.")
    }
}