/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.platform.map.MapRenderer
import dev.nikomaru.advancerailway.storage.loader.ConfigDataLoader
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.domain.rail.BranchDirection
import dev.nikomaru.advancerailway.listener.InspectRequest
import dev.nikomaru.advancerailway.listener.RailClickEvent
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.utils.Utils.toPoint3D
import kotlinx.coroutines.withContext
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotation.specifier.Range
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Flag
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * `/ar`（別名 `/advancerailway`）直下の一般・運用コマンド。
 *
 * 権限はクラスに付けずメソッド単位で明示する（ロール型）。
 * - 閲覧系（info）は `advancerailway.info`（全員 TRUE）。
 * - 運用系（reload/inspect/debug）は OP 限定の個別ノード。
 *
 * Cloud は asyncCoordinator でハンドラをメインスレッド外で実行するため、
 * Bukkit のメインスレッド専用 API（`getTargetBlockExact`・非スレッドセーフな collection への追加）は
 * [minecraftDispatcher] で明示的にメインスレッドへ切り替える。
 */
@Command("ar|advancerailway")
class GeneralCommand : KoinComponent {
    val plugin: AdvanceRailway by inject()
    private val stationRepository: StationRepository by inject()

    @Command("info")
    @CommandDescription("プラグインの情報（バージョン・作者・サイト）を表示します")
    @Permission("advancerailway.info")
    fun info(sender: CommandSender) {
        sender.sendRichMessage("<gray>=== <aqua><bold>AdvanceRailway</bold></aqua> <gray>===")
        sender.sendRichMessage("<yellow>バージョン: <white>${plugin.pluginMeta.version}")
        sender.sendRichMessage("<yellow>作者: <white>${plugin.pluginMeta.authors.joinToString(", ")}")
        sender.sendRichMessage("<yellow>サイト: <white>${plugin.pluginMeta.website}")
        sender.sendRichMessage("<gray>コマンド一覧: <white>/ar help")
    }

    @Command("help")
    @CommandDescription("コマンドの一覧を表示します")
    @Permission("advancerailway.info")
    fun help(sender: CommandSender) {
        sender.sendRichMessage("<gray>=== <aqua><bold>AdvanceRailway コマンド</bold></aqua> <gray>===")
        sender.sendRichMessage("<yellow>■ 一般")
        sender.sendRichMessage("<white>/ar info <gray>- プラグイン情報")
        sender.sendRichMessage("<white>/ar help <gray>- このヘルプ")
        sender.sendRichMessage("<white>/ar inspect [flags] [--to <駅>] <gray>- 線路の解析 (運営)")
        sender.sendRichMessage("<yellow>■ 駅 <gray>(閲覧は全員 / 編集は運営)")
        sender.sendRichMessage("<white>/ar station list [page] <gray>- 駅一覧")
        sender.sendRichMessage("<white>/ar station info <駅> <gray>- 駅の詳細")
        sender.sendRichMessage("<white>/ar station tp <駅> <gray>- 駅へテレポート (運営)")
        sender.sendRichMessage("<white>/ar station add|remove|set … <gray>- 駅の編集")
        sender.sendRichMessage("<yellow>■ 路線")
        sender.sendRichMessage("<white>/ar railway list [page] <gray>- 路線一覧")
        sender.sendRichMessage("<white>/ar railway info <路線> <gray>- 路線の詳細")
        sender.sendRichMessage("<white>/ar railway route <出発> [到着] <gray>- 最短経路")
        sender.sendRichMessage("<white>/ar railway check <gray>- 路線経路の検証 (運営)")
        sender.sendRichMessage("<white>/ar railway explore point|railway … <gray>- 経路候補の調査 (運営)")
        sender.sendRichMessage("<white>/ar railway add|remove|set … <gray>- 路線の編集")
        sender.sendRichMessage("<yellow>■ グループ")
        sender.sendRichMessage("<white>/ar group list [page] <gray>- グループ一覧")
        sender.sendRichMessage("<white>/ar group info <グループ> <gray>- グループの詳細")
        sender.sendRichMessage("<white>/ar group add|remove|set … <gray>- グループの編集")
        sender.sendRichMessage("<white>/ar group station set|list <グループ> <gray>- 駅の並び（ナンバリング順）")
    }

    @Command("reload")
    @CommandDescription("マップと設定データを再読み込みします")
    @Permission("advancerailway.reload")
    suspend fun reload(sender: CommandSender) {
        ConfigDataLoader().load()
        MapRenderer.refresh()
        sender.sendRichMessage("<green>設定を再読み込みし、マップを描き直しました。")
    }

    /**
     * クリックしたレールから線路網を探索する。分岐の多い場所では終端が一気に増えるため、
     * 見たい経路をあらかじめ絞れるようにしている。
     *
     * - `flags` — たどる方角の並び（例 `E` / `EE`）。先頭が出発方角で、指定した並びから
     *   外れる分岐は探索しない。省略すると全方向を探索する。
     * - `--to` — その駅を終点とする経路だけを表示する。
     */
    @Command("inspect [flags]")
    @CommandDescription("クリックしたレールを解析します（flags で方向を絞り、--to で終点駅を指定）")
    @Permission("advancerailway.inspect")
    suspend fun inspect(
        sender: CommandSender,
        @Argument("flags") flags: String?,
        @Flag(
            value = "to",
            aliases = ["t"],
            description = "指定した駅を終点とする経路だけを表示します",
        ) to: StationId?,
    ) {
        if (sender !is Player) {
            sender.sendRichMessage("<red>このコマンドはプレイヤー専用です。")
            return
        }
        // 空文字は「指定なし」として扱う（Cloud の省略可能引数は空文字で渡ってくることがある）。
        val prefix = if (flags.isNullOrBlank()) {
            emptyList()
        } else {
            BranchDirection.parse(flags) ?: run {
                sender.sendRichMessage(
                    "<red>flags が不正です（N/S/E/W の並びで指定してください）: <white>$flags</white>"
                )
                return
            }
        }
        // detect は非スレッドセーフな HashMap で、イベントハンドラ（メインスレッド）と共有される。
        // 出し入れはメインスレッドで行う。
        withContext(plugin.minecraftDispatcher) {
            RailClickEvent.detect[sender.uniqueId] = InspectRequest(prefix, to)
        }
        val conditions = buildList {
            if (prefix.isNotEmpty()) add("方角 <white>${prefix.joinToString("") { it.label }}</white>")
            if (to != null) add("終点 <white>${stationRepository.findById(to)?.name ?: to}</white>")
        }
        if (conditions.isEmpty()) {
            sender.sendRichMessage("<yellow>解析したいレールをクリックしてください。")
        } else {
            sender.sendRichMessage(
                "<yellow>解析したいレールをクリックしてください。<gray>（${conditions.joinToString(" / ")} で絞り込み）"
            )
        }
    }

    // Cloud はメソッド引数を @Command の syntax 文字列に宣言する必要がある（[..] は任意引数）。
    @Command("debug block [distance]")
    @CommandDescription("見ているブロックの座標を表示します（デバッグ用・プレイヤー専用）")
    @Permission("advancerailway.debug")
    suspend fun debugBlock(
        sender: CommandSender,
        @Argument("distance") @Default("10") @Range(min = "1", max = "100") distance: Int,
    ) {
        if (sender !is Player) {
            sender.sendRichMessage("<red>このコマンドはプレイヤー専用です。")
            return
        }
        // getTargetBlockExact はメインスレッド専用 API のため、メインスレッドで実行する。
        val location = withContext(plugin.minecraftDispatcher) {
            sender.getTargetBlockExact(distance)?.location
        }
        if (location == null) {
            sender.sendRichMessage("<red>ブロックが見つかりませんでした。")
            return
        }
        sender.sendRichMessage("<yellow>ブロック: <white>${location.toPoint3D().toPlainString()}")
    }
}
