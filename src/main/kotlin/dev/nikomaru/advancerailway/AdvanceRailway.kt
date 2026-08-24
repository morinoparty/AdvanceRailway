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
import dev.nikomaru.advancerailway.commands.GeneralCommand
import dev.nikomaru.advancerailway.commands.group.GroupEditCommand
import dev.nikomaru.advancerailway.commands.group.GroupInfoCommand
import dev.nikomaru.advancerailway.commands.group.GroupMainCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayEditCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayExploreCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayInfoCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayMainCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayRouteCommand
import dev.nikomaru.advancerailway.commands.station.StationEditCommand
import dev.nikomaru.advancerailway.commands.station.StationInfoCommand
import dev.nikomaru.advancerailway.commands.station.StationMainCommand
import dev.nikomaru.advancerailway.platform.map.MapRenderer
import dev.nikomaru.advancerailway.storage.database.DatabaseInitializer
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.loader.ConfigDataLoader
import dev.nikomaru.advancerailway.storage.migration.JsonImport
import dev.nikomaru.advancerailway.listener.RailClickEvent
import dev.nikomaru.advancerailway.integration.mineauth.MineAuthIntegration
import dev.nikomaru.advancerailway.commands.parser.GroupIdParser
import dev.nikomaru.advancerailway.commands.parser.Point3DParser
import dev.nikomaru.advancerailway.commands.parser.RailwayIdParser
import dev.nikomaru.advancerailway.commands.parser.StationIdParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.bukkit.CloudBukkitCapabilities
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.incendo.cloud.paper.LegacyPaperCommandManager
import org.incendo.cloud.setting.ManagerSetting
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
        settingMap()
        ConfigDataLoader().load()
        setupDatabase()
        MapRenderer.refresh()
        // MineAuth が導入されていれば HTTP エンドポイントを登録する（未導入でも動作する）。
        MineAuthIntegration.register(this)
    }

    /**
     * データベースへ接続し、スキーマを用意してリポジトリを Koin へ登録する。
     * 旧 JSON データが残っていれば、この時点で 1 度だけ取り込む。
     *
     * JDBC はブロッキングなので、接続とスキーマ作成は [Dispatchers.IO] で行う。
     */
    private suspend fun setupDatabase() {
        withContext(Dispatchers.IO) {
            DatabaseInitializer.connect(dataFolder)
            DatabaseInitializer.createTables()
        }
        val stationRepository = StationRepository()
        val railwayRepository = RailwayRepository()
        val groupRepository = GroupRepository()
        loadKoinModules(module {
            single { stationRepository }
            single { railwayRepository }
            single { groupRepository }
        })
        JsonImport(this, stationRepository, railwayRepository, groupRepository).runIfNeeded()
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
        // Brigadier ネイティブ（modern PaperCommandManager）は unquoted 引数が ASCII 限定で、
        // 駅名などの日本語入力を受け付けないため、Bukkit レガシー登録の
        // LegacyPaperCommandManager を使う（AdvancedShopFinder と同じ構成）。
        // asyncCoordinator でハンドラを実行、installCoroutineSupport で suspend ハンドラを許可する。
        val commandManager: LegacyPaperCommandManager<CommandSender> =
            LegacyPaperCommandManager.createNative(
                this,
                ExecutionCoordinator.asyncCoordinator(),
            )

        if (commandManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            commandManager.registerAsynchronousCompletions()
        }

        commandManager.settings().set(ManagerSetting.ALLOW_UNSAFE_REGISTRATION, true)
        // フラグを最後のリテラル直後から解析させる。これが無いと、省略可能引数の後ろに置いたフラグ
        // （例: /ar railway route <to> --rail-only）が省略可能引数のパーサーに食われてしまう。
        commandManager.settings().set(ManagerSetting.LIBERAL_FLAG_PARSING, true)

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
            RailwayExploreCommand(),
            RailwayInfoCommand(),
            RailwayEditCommand(),
            RailwayRouteCommand(),
            StationMainCommand(),
            StationInfoCommand(),
            StationEditCommand(),
            GroupMainCommand(),
            GroupInfoCommand(),
            GroupEditCommand(),
        )
    }

    private fun setEventHandlers() { // Register event handlers
        server.pluginManager.registerSuspendingEvents(RailClickEvent(), this)
    }
}