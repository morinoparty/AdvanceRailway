/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.railway


import dev.nikomaru.advancerailway.commands.stationTpLink
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.rail.BranchDirection
import dev.nikomaru.advancerailway.storage.DataPaths
import dev.nikomaru.advancerailway.storage.FileLoader
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.storage.type.LineType
import dev.nikomaru.advancerailway.domain.id.IdValidation
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.service.RailwayUtils
import dev.nikomaru.advancerailway.domain.service.RailwayVerifier
import dev.nikomaru.advancerailway.domain.service.StationUtils
import dev.nikomaru.advancerailway.utils.Utils.json
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission

@Command("ar|advancerailway railway")
class RailwayMainCommand {

    // flags は inspect の分岐フラグ（例: "EE"）。分岐点で進む方角を先頭から順に指定する。
    // 省略可能引数は nullable で受ける。@Default("") は空文字がトークンを消費せず
    // CommandTree の解析が無限再帰して StackOverflowError になるため使わないこと。
    @Command("add <railwayId> <startPoint> <directionPoint> <endPoint> [flags]")
    @CommandDescription("駅間の経路を計算して新しい路線を登録します（flags: 分岐点で選ぶ方角の並び。例: EE）")
    @Permission("advancerailway.railway.manage")
    suspend fun register(
        sender: CommandSender,
        @Argument("railwayId") railwayId: String,
        @Argument("startPoint") startPoint: Point3D,
        @Argument("directionPoint") directionPoint: Point3D,
        @Argument("endPoint") endPoint: Point3D,
        @Argument("flags") flags: String?
    ) {
        if (!IdValidation.isValid(railwayId)) {
            sender.sendRichMessage("<red>路線 ID が不正です: <white>$railwayId</white>")
            return
        }
        sender.sendRichMessage("<gray>路線を登録しています…")
        handleRailway(sender, railwayId, startPoint, directionPoint, endPoint, flags, "登録")
    }

    @Command("redraw <railwayId> <startPoint> <directionPoint> <endPoint> [flags]")
    @CommandDescription("路線の経路を引き直します（flags: 分岐点で選ぶ方角の並び。例: EE）")
    @Permission("advancerailway.railway.manage")
    suspend fun redraw(
        sender: CommandSender,
        @Argument("railwayId") railwayId: String,
        @Argument("startPoint") startPoint: Point3D,
        @Argument("directionPoint") directionPoint: Point3D,
        @Argument("endPoint") endPoint: Point3D,
        @Argument("flags") flags: String?
    ) {
        if (!IdValidation.isValid(railwayId)) {
            sender.sendRichMessage("<red>路線 ID が不正です: <white>$railwayId</white>")
            return
        }
        sender.sendRichMessage("<gray>路線の経路を引き直しています…")
        handleRailway(sender, railwayId, startPoint, directionPoint, endPoint, flags, "引き直し")
    }

    private suspend fun handleRailway(
        sender: CommandSender,
        railwayId: String,
        startPoint: Point3D,
        directionPoint: Point3D,
        endPoint: Point3D,
        flags: String?,
        action: String
    ) {
        val branchFlags = BranchDirection.parse(flags ?: "") ?: run {
            sender.sendRichMessage("<red>分岐フラグが不正です（N/S/E/W のみ使用できます）: <white>$flags</white>")
            return
        }
        val line = RailwayUtils.getLine(startPoint, directionPoint, endPoint, branchFlags).getOrNull() ?: run {
            sender.sendRichMessage("<red>レール経路の取得に失敗しました。")
            return
        }
        val world = if (sender is Player) sender.world else Bukkit.getWorlds().first()
        val fromStation = StationUtils.nearStation(startPoint.toLocation(world)).getOrNull() ?: run {
            sender.sendRichMessage("<red>始点付近の駅が見つかりません。")
            return
        }
        val toStation = StationUtils.nearStation(endPoint.toLocation(world)).getOrNull() ?: run {
            sender.sendRichMessage("<red>終点付近の駅が見つかりません。")
            return
        }
        val railwayData = RailwayData.V2(
            id = RailwayId(railwayId),
            group = null,
            world = world,
            lineType = LineType.UP_DOWN_LINE,
            line = line,
            fromStation = fromStation,
            toStation = toStation, timeRequired = line.getLength().toLong() / 8,
            startPoint = startPoint,
            endPoint = endPoint,
            directionPoint = directionPoint,
            flags = branchFlags
        )
        railwayData.save()
        sender.sendRichMessage("<green>路線を${action}しました: <white>$railwayId</white>")
    }

    /**
     * 旧形式 (V1) の路線データを再トレースして V2 に移行する。
     * V1 は分岐情報を持たないため flags なしで再トレースし、分岐に当たった路線は
     * 失敗として報告する（redraw で flags を指定して引き直すと V2 になる）。
     */
    @Command("migrate")
    @CommandDescription("旧形式 (V1) の路線データを V2 に移行します")
    @Permission("advancerailway.railway.manage")
    suspend fun migrate(sender: CommandSender) {
        val files = DataPaths.railways.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        var migrated = 0
        var skipped = 0
        var failed = 0
        for (file in files) {
            val data = try {
                json.decodeFromString<RailwayData>(file.readText())
            } catch (e: Exception) {
                sender.sendRichMessage("<red>読み込み失敗: <white>${file.name}</white> — ${e.message}")
                failed++
                continue
            }
            if (data is RailwayData.V2) {
                skipped++
                continue
            }
            val line = RailwayUtils.getLine(data.startPoint, data.directionPoint, data.endPoint).getOrNull()
            if (line == null) {
                sender.sendRichMessage(
                    "<red>移行失敗: <white>${data.id.value}</white> — 経路を再トレースできません" +
                        "（分岐がある場合は redraw で flags を指定してください） ${stationTpLink(data.fromStation)}"
                )
                failed++
                continue
            }
            RailwayData.V2(
                id = data.id,
                group = data.group,
                world = data.world,
                lineType = data.lineType,
                line = line,
                fromStation = data.fromStation,
                toStation = data.toStation,
                timeRequired = data.timeRequired,
                startPoint = data.startPoint,
                endPoint = data.endPoint,
                directionPoint = data.directionPoint,
                flags = emptyList()
            ).save()
            migrated++
        }
        sender.sendRichMessage("<green>移行完了: 移行 $migrated 件 / スキップ（既に V2）$skipped 件 / 失敗 $failed 件")
    }

    /**
     * V2 路線の経路が保存時から変わっていないか、開始点＋分岐フラグから再トレースして検証する。
     * 以前は起動時に自動実行していたが、未ロードチャンクの同期ロードで起動が重くなるため
     * コマンドでの手動実行に変更した。失敗行の [TP] で始点駅へ飛んで現地を確認できる。
     */
    @Command("check")
    @CommandDescription("全 V2 路線の経路が保存時から変わっていないか検証します")
    @Permission("advancerailway.railway.manage")
    suspend fun check(sender: CommandSender) {
        sender.sendRichMessage("<gray>路線の経路を検証しています…")
        val result = RailwayVerifier.verifyAll()
        if (result.checked == 0) {
            sender.sendRichMessage("<yellow>検証対象の V2 路線がありません。")
            return
        }
        for (problem in result.problems) {
            val id = problem.data.id.value
            val tp = stationTpLink(problem.data.fromStation)
            when (problem) {
                is RailwayVerifier.TraceFailed -> sender.sendRichMessage(
                    "<red>再トレース失敗: <white>$id</white> (${problem.error}) — レールが変更された可能性があります $tp"
                )

                is RailwayVerifier.RouteChanged -> sender.sendRichMessage(
                    "<red>経路変化: <white>$id</white> — 経路が保存時から変化しています $tp"
                )
            }
        }
        if (result.problems.isEmpty()) {
            sender.sendRichMessage("<green>検証完了: ${result.checked} 件すべて変更ありませんでした。")
        } else {
            sender.sendRichMessage(
                "<yellow>検証完了: ${result.checked} 件中 ${result.problems.size} 件に変更を検出しました。" +
                    "/ar railway redraw で引き直してください。"
            )
        }
    }

    @Command("remove <railwayId>")
    @CommandDescription("指定した路線を削除します")
    @Permission("advancerailway.railway.manage")
    suspend fun remove(sender: CommandSender, @Argument("railwayId") railwayId: RailwayId) {
        val file = DataPaths.railways.resolve("$railwayId.json")
        if (!file.exists()) {
            sender.sendRichMessage("<red>路線が見つかりません。")
            return
        }
        file.delete()
        FileLoader.mapDataLoad()
        sender.sendRichMessage("<green>路線を削除しました: <white>$railwayId</white>")
    }
}
