/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.parser

import dev.nikomaru.advancerailway.domain.id.StationId
import org.bukkit.command.CommandSender
import org.incendo.cloud.parser.ParserDescriptor

object StationIdParser : IdParser<CommandSender, StationId>(IdEntries::stations, ::StationId) {
    fun stationIdParser(): ParserDescriptor<CommandSender, StationId> =
        ParserDescriptor.of(this, StationId::class.java)
}
