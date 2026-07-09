/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.loader

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.file.data.ConfigData
import dev.nikomaru.advancerailway.file.utils.writeAtomically
import dev.nikomaru.advancerailway.utils.Utils.json
import kotlinx.serialization.encodeToString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

class ConfigDataLoader: KoinComponent {
    private val plugin: AdvanceRailway by inject()

    fun load() {
        val file = plugin.dataFolder.resolve("config.json")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            val data = ConfigData(1000)
            val str = json.encodeToString(data)
            writeAtomically(file, str)
        }
        val configData = json.decodeFromString<ConfigData>(file.readText())
        loadKoinModules(module {
            single { configData }
        })
    }

}