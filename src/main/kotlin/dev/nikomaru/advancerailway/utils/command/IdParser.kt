/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.utils.command

import dev.nikomaru.advancerailway.AdvanceRailway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import revxrsal.commands.bukkit.BukkitCommandHandler
import revxrsal.commands.bukkit.sender
import revxrsal.commands.command.CommandActor
import revxrsal.commands.command.ExecutableCommand
import revxrsal.commands.process.ValueResolver

/**
 * Shared implementation for id parsers (Group/Railway/Station) that are all backed by a
 * per-type subfolder under the plugin's data directory (e.g. files under `data/groups/`), whose
 * suggestions are simply the file names in that folder.
 */
abstract class IdParser<T: Any>(
    private val subfolder: String,
    private val idType: Class<T>,
    private val idFactory: (String) -> T,
): ValueParser<T>(), KoinComponent {
    val plugin: AdvanceRailway by inject()

    private val folder get() = plugin.dataFolder.resolve("data").resolve(subfolder)

    /** Ensures the backing data folder exists. Call once at startup, never from [suggestions]. */
    fun ensureDataFolder() {
        if (!folder.exists()) {
            folder.mkdirs()
        }
    }

    override fun suggestions(args: List<String>, sender: CommandSender, command: ExecutableCommand): Set<String> =
        runBlocking(Dispatchers.IO) {
            folder.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
        }

    override fun resolve(context: ValueResolver.ValueResolverContext): T = idFactory(context.pop())

    protected fun BukkitCommandHandler.registerIdParser() {
        // Note: do NOT call ensureDataFolder() here. This runs from setCommand(), which
        // executes before Koin is started (setupKoin()), and folder access resolves the
        // lazily-injected `plugin` via Koin. Data folders are created explicitly, after
        // Koin startup, by AdvanceRailway.onEnableAsync().
        autoCompleter.registerParameterSuggestions(
            idType,
        ) { args: List<String>, sender: CommandActor, command: ExecutableCommand ->
            suggestions(args, sender.sender, command)
        }
        registerValueResolver(idType, this@IdParser)
    }
}
