/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands

import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.incendo.cloud.SenderMapper

/**
 * Cloud の Brigadier ネイティブ送信者 [CommandSourceStack] と Bukkit の [CommandSender] を相互変換する。
 * これによりコマンドハンドラは従来どおり [CommandSender] を受け取れる。
 * morinoparty 系プラグイン（MoripaFishing 等）と同じ実装。
 */
class CommandSenderMapper : SenderMapper<CommandSourceStack, CommandSender> {
    override fun map(source: CommandSourceStack): CommandSender = source.sender

    override fun reverse(sender: CommandSender): CommandSourceStack {
        return object : CommandSourceStack {
            override fun getLocation(): Location {
                if (sender is Entity) {
                    return sender.location
                }
                val worlds = Bukkit.getWorlds()
                return Location(if (worlds.isEmpty()) null else worlds.first(), 0.0, 0.0, 0.0)
            }

            override fun getSender(): CommandSender = sender

            override fun getExecutor(): Entity? = sender as? Entity

            override fun withLocation(location: Location): CommandSourceStack = sender as CommandSourceStack

            override fun withExecutor(executor: Entity): CommandSourceStack = sender as CommandSourceStack
        }
    }
}
