/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.utils.coroutines

import dev.nikomaru.advancerailway.AdvanceRailway
import org.bukkit.plugin.java.JavaPlugin
import kotlin.coroutines.CoroutineContext

object DispatcherContainer {
    /**
     * Gets the async coroutine context.
     */
    val async: CoroutineContext by lazy {
        AsyncCoroutineDispatcher(JavaPlugin.getPlugin(AdvanceRailway::class.java))
    }

    /**
     * Gets the sync coroutine context.
     */
    val sync: CoroutineContext by lazy {
        MinecraftCoroutineDispatcher(JavaPlugin.getPlugin(AdvanceRailway::class.java))
    }
}