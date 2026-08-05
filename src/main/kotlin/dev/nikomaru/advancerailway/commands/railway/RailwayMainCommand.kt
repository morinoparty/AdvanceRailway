/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.railway


import arrow.core.Either
import dev.nikomaru.advancerailway.commands.stationTpLink
import dev.nikomaru.advancerailway.domain.error.toUserMessage
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.rail.BranchDirection
import dev.nikomaru.advancerailway.storage.DataPaths
import dev.nikomaru.advancerailway.storage.FileLoader
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.storage.type.LineType
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.IdValidation
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.StationId
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
import org.incendo.cloud.annotations.Flag
import org.incendo.cloud.annotations.Permission

@Command("ar|advancerailway railway")
class RailwayMainCommand {

    /**
     * flags は inspect / explore が提示するフラグ列。先頭が始点からの出発方角で、
     * 以降が各分岐点で選ぶ方角（例: "EE" = 東へ出発し、最初の分岐で東）。
     * 既存路線と同じ ID を指定すると経路を引き直して上書きする（グループ・種別は維持）。
     */
    @Command("add <railwayId> <startPoint> <endPoint> <flags>")
    @CommandDescription("駅間の経路を計算して路線を登録・引き直します（flags: 出発方角＋分岐で選ぶ方角。例: EE）")
    @Permission("advancerailway.railway.manage")
    suspend fun register(
        sender: CommandSender,
        @Argument("railwayId") railwayId: String,
        @Argument("startPoint") startPoint: Point3D,
        @Argument("endPoint") endPoint: Point3D,
        @Argument("flags") flags: String
    ) {
        if (!IdValidation.isValid(railwayId)) {
            sender.sendRichMessage("<red>路線 ID が不正です: <white>$railwayId</white>")
            return
        }
        val branchFlags = BranchDirection.parse(flags)?.takeIf { it.isNotEmpty() } ?: run {
            sender.sendRichMessage("<red>flags が不正です（N/S/E/W を1文字以上。先頭が出発方角）: <white>$flags</white>")
            return
        }
        sender.sendRichMessage("<gray>路線を登録しています…")
        val line = when (val traced = RailwayUtils.getLine(startPoint, endPoint, branchFlags)) {
            is Either.Left -> {
                sender.sendRichMessage(traced.value.toUserMessage())
                return
            }

            is Either.Right -> traced.value
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
        // 引き直し（上書き）の場合はグループ・種別の設定を引き継ぐ
        val existing = RailwayUtils.getRailwayData(RailwayId(railwayId)).getOrNull()
        RailwayData.V3(
            id = RailwayId(railwayId),
            group = existing?.group,
            world = world,
            lineType = existing?.lineType ?: LineType.UP_DOWN_LINE,
            line = line,
            fromStation = fromStation,
            toStation = toStation,
            timeRequired = line.getLength().toLong() / 8,
            startPoint = startPoint,
            endPoint = endPoint,
            flags = branchFlags.joinToString("") { it.label },
        ).save()
        val action = if (existing == null) "登録" else "上書き（引き直し）"
        sender.sendRichMessage("<green>路線を${action}しました: <white>$railwayId</white>")
    }

    /**
     * 旧形式 (V1/V2) の路線データを V3 に移行する。
     * V2 は directionPoint を出発方角に変換して flags の先頭に組み込むだけで、再トレースは不要。
     * V1 は分岐情報を持たないため出発方角のみで再トレースし、分岐に当たった路線は
     * 失敗として報告する（add で flags を指定して引き直すと V3 になる）。
     */
    @Command("migrate")
    @CommandDescription("旧形式 (V1/V2) の路線データを V3 に移行します")
    @Permission("advancerailway.railway.manage")
    suspend fun migrate(sender: CommandSender) {
        val files = DataPaths.railways.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        var migrated = 0
        var skipped = 0
        var failed = 0

        fun departureOf(startPoint: Point3D, directionPoint: Point3D, id: RailwayId): BranchDirection? {
            val departure = BranchDirection.fromPoints(startPoint, directionPoint)
            if (departure == null) {
                sender.sendRichMessage(
                    "<red>移行失敗: <white>${id.value}</white> — directionPoint から出発方角を求められません"
                )
            }
            return departure
        }

        for (file in files) {
            val data = try {
                json.decodeFromString<RailwayData>(file.readText())
            } catch (e: Exception) {
                sender.sendRichMessage("<red>読み込み失敗: <white>${file.name}</white> — ${e.message}")
                failed++
                continue
            }
            when (data) {
                is RailwayData.V3 -> skipped++

                is RailwayData.V2 -> {
                    val departure = departureOf(data.startPoint, data.directionPoint, data.id)
                    if (departure == null) {
                        failed++
                        continue
                    }
                    RailwayData.V3(
                        id = data.id,
                        group = data.group,
                        world = data.world,
                        lineType = data.lineType,
                        line = data.line,
                        fromStation = data.fromStation,
                        toStation = data.toStation,
                        timeRequired = data.timeRequired,
                        startPoint = data.startPoint,
                        endPoint = data.endPoint,
                        flags = departure.label + data.flags.joinToString("") { it.label },
                    ).save()
                    migrated++
                }

                is RailwayData.V1 -> {
                    val departure = departureOf(data.startPoint, data.directionPoint, data.id)
                    if (departure == null) {
                        failed++
                        continue
                    }
                    val line = RailwayUtils.getLine(data.startPoint, data.endPoint, listOf(departure)).getOrNull()
                    if (line == null) {
                        sender.sendRichMessage(
                            "<red>移行失敗: <white>${data.id.value}</white> — 経路を再トレースできません" +
                                "（分岐がある場合は add で flags を指定してください） ${stationTpLink(data.fromStation)}"
                        )
                        failed++
                        continue
                    }
                    RailwayData.V3(
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
                        flags = departure.label,
                    ).save()
                    migrated++
                }
            }
        }
        sender.sendRichMessage("<green>移行完了: 移行 $migrated 件 / スキップ（既に V3）$skipped 件 / 失敗 $failed 件")
    }

    /**
     * V3 路線の経路が保存時から変わっていないか、開始点＋flags から再トレースして検証する。
     * 以前は起動時に自動実行していたが、未ロードチャンクの同期ロードで起動が重くなるため
     * コマンドでの手動実行に変更した。失敗行の [TP] で始点駅へ飛んで現地を確認できる。
     *
     * `--group` / `--station` で対象を絞れる（両方指定した場合は AND）。
     */
    @Command("check")
    @CommandDescription("V3 路線の経路が保存時から変わっていないか検証します（--group/--station で絞り込み）")
    @Permission("advancerailway.railway.manage")
    suspend fun check(
        sender: CommandSender,
        @Flag(
            value = "group",
            aliases = ["g"],
            description = "指定グループに属する路線だけを検証します",
        ) group: GroupId?,
        @Flag(
            value = "station",
            aliases = ["s"],
            description = "指定駅に接続している路線だけを検証します",
        ) station: StationId?,
    ) {
        sender.sendRichMessage("<gray>路線の経路を検証しています…")
        val result = RailwayVerifier.verifyAll { data ->
            (group == null || data.group == group) &&
                (station == null || data.fromStation == station || data.toStation == station)
        }
        if (result.checked == 0) {
            sender.sendRichMessage(
                if (group != null || station != null) "<yellow>条件に一致する V3 路線がありません。"
                else "<yellow>検証対象の V3 路線がありません（旧形式は /ar railway migrate で移行してください）。"
            )
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
                    "/ar railway add で引き直してください。"
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
