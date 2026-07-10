/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.railway

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.commands.DataPaths
import dev.nikomaru.advancerailway.commands.getOrSend
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.utils.RailwayUtils
import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import kotlin.math.ceil

@Command("ar railway", "advancerailway railway")
@CommandPermission("advancerailway.command.railway.read")
class RailwayInfoCommand: KoinComponent {
    val plugin: AdvanceRailway by inject()

    @Subcommand("info")
    suspend fun info(sender: CommandSender, railwayId: RailwayId) {
        val data = RailwayUtils.getRailwayData(railwayId).getOrSend(sender) { "Railway not found" } ?: return
        sender.sendRichMessage("Railway ID: ${data.id.value}")
        sender.sendRichMessage("Railway Stations: ${data.toStation} -> ${data.fromStation}")
        sender.sendRichMessage("Railway Length: ${ceil(data.timeRequired / 6.0) / 10} minutes")
    }

    @Subcommand("list")
    fun list(sender: CommandSender) {
        val list = DataPaths.railways.listFiles()?.map { it.nameWithoutExtension }
            ?: run {
                sender.sendRichMessage("No railway found")
                return
            }
        if (list.isEmpty()) {
            sender.sendRichMessage("No railway found")
            return
        }
        sender.sendRichMessage("Railway List: <yellow>Click for details")
        list.forEach {
            sender.sendRichMessage("<click:run_command:/ar railway info $it>$it</click>")
        }
    }
}