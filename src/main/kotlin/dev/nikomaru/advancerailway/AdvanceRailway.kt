/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway

import com.comphenix.protocol.ProtocolLibrary
import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.registerSuspendingEvents
import dev.nikomaru.advancerailway.commands.FileCommand
import dev.nikomaru.advancerailway.commands.GeneralCommand
import dev.nikomaru.advancerailway.commands.group.GroupEditCommand
import dev.nikomaru.advancerailway.commands.group.GroupInfoCommand
import dev.nikomaru.advancerailway.commands.group.GroupMainCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayEditCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayInfoCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayMainCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayRouteCommand
import dev.nikomaru.advancerailway.commands.station.StationEditCommand
import dev.nikomaru.advancerailway.commands.station.StationInfoCommand
import dev.nikomaru.advancerailway.commands.station.StationMainCommand
import dev.nikomaru.advancerailway.file.FileLoader
import dev.nikomaru.advancerailway.listener.RailClickEvent
import dev.nikomaru.advancerailway.mineauth.MineAuthIntegration
import dev.nikomaru.advancerailway.utils.command.GroupIdParser
import dev.nikomaru.advancerailway.utils.command.Point3DParser
import dev.nikomaru.advancerailway.utils.command.RailwayIdParser
import dev.nikomaru.advancerailway.utils.command.StationIdParser
import dev.nikomaru.advancerailway.commands.CommandSenderMapper
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.incendo.cloud.CommandManager
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.incendo.cloud.paper.PaperCommandManager
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import xyz.jpenilla.squaremap.api.*


open class AdvanceRailway: SuspendingJavaPlugin() {
    companion object {
        lateinit var plugin: AdvanceRailway
            private set
    }

    override suspend fun onEnableAsync() { // Plugin startup logic
        plugin = this
        setCommand()
        setEventHandlers()
        setupKoin()
        ensureCommandDataFolders()
        settingMap()
        FileLoader.load()
        // MineAuth が導入されていれば HTTP エンドポイントを登録する（未導入でも動作する）。
        MineAuthIntegration.register(this)
    }

    private fun settingMap() {
        val squaremapApi = SquaremapProvider.get()
        val world = Bukkit.getWorld("world")
        val mapWorld: MapWorld = squaremapApi.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world)).orElse(null)
        val provider = SimpleLayerProvider.builder("Railway").build()
        mapWorld.layerRegistry().register(Key.of("Railway"), provider)

        loadKoinModules(module {
            single { squaremapApi }
            single { provider }
        })
    }

    /**
     * Creates the data/{groups,railways,stations} folders used by the id parsers' suggestions.
     * Must run after [setupKoin], since it resolves the Koin-injected plugin instance; the id
     * parsers' own `suggestions()` deliberately stays read-only and never creates these folders.
     */
    private fun ensureCommandDataFolders() {
        GroupIdParser.ensureDataFolder()
        RailwayIdParser.ensureDataFolder()
        StationIdParser.ensureDataFolder()
    }

    private fun setupKoin() {
        val appModule = module {
            single<AdvanceRailway> { this@AdvanceRailway }
            single { ProtocolLibrary.getProtocolManager() }
        }

        GlobalContext.getOrNull() ?: GlobalContext.startKoin {
            modules(appModule)
        }
    }

    override suspend fun onDisableAsync() { // Plugin shutdown logic

    }

    private fun setCommand() {
        // Incendo Cloud へ移行中。Brigadier ネイティブ送信者を CommandSender へマップし、
        // asyncCoordinator でハンドラを実行、installCoroutineSupport で suspend ハンドラを許可する。
        val commandManager: CommandManager<CommandSender> =
            PaperCommandManager.builder(CommandSenderMapper())
                .executionCoordinator(ExecutionCoordinator.asyncCoordinator())
                .buildOnEnable(this)

        // ID・座標のカスタムパーサを登録する（補完＝表示名、解決＝名前 or ID）。
        with(commandManager.parserRegistry()) {
            registerParser(Point3DParser.point3DParser())
            registerParser(StationIdParser.stationIdParser())
            registerParser(RailwayIdParser.railwayIdParser())
            registerParser(GroupIdParser.groupIdParser())
        }

        val annotationParser = AnnotationParser(commandManager, CommandSender::class.java)
        annotationParser.installCoroutineSupport()

        annotationParser.parse(
            GeneralCommand(),
            RailwayMainCommand(),
            RailwayInfoCommand(),
            RailwayEditCommand(),
            RailwayRouteCommand(),
            StationMainCommand(),
            StationInfoCommand(),
            StationEditCommand(),
            GroupMainCommand(),
            GroupInfoCommand(),
            GroupEditCommand(),
            FileCommand(),
        )
    }

    private fun setEventHandlers() { // Register event handlers
        server.pluginManager.registerSuspendingEvents(RailClickEvent(), this)
    }
}