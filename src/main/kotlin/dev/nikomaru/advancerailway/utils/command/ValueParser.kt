/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.utils.command

import org.bukkit.command.CommandSender
import revxrsal.commands.command.ExecutableCommand
import revxrsal.commands.process.ValueResolver

abstract class ValueParser<T: Any>: ValueResolver<T> {
    abstract fun suggestions(args: List<String>, sender: CommandSender, command: ExecutableCommand): Set<String>

    abstract override fun resolve(context: ValueResolver.ValueResolverContext): T

}