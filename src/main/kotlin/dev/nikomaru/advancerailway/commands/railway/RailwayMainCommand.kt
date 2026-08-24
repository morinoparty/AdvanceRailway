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
import dev.nikomaru.advancerailway.commands.formatCheckedAt
import dev.nikomaru.advancerailway.commands.stationTpLink
import dev.nikomaru.advancerailway.domain.error.toUserMessage
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.domain.rail.BranchDirection
import dev.nikomaru.advancerailway.domain.service.RailwayUtils
import dev.nikomaru.advancerailway.domain.service.RailwayVerifier
import dev.nikomaru.advancerailway.domain.service.StationUtils
import dev.nikomaru.advancerailway.platform.map.MapRenderer
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.storage.type.LineType
import dev.nikomaru.advancerailway.utils.Utils.toLocation
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Flag
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

@Command("ar|advancerailway railway")
class RailwayMainCommand : KoinComponent {

    private val railwayRepository: RailwayRepository by inject()
    private val stationRepository: StationRepository by inject()

    /**
     * flags は inspect / explore が提示するフラグ列。先頭が始点からの出発方角で、
     * 以降が各分岐点で選ぶ方角（例: "EE" = 東へ出発し、最初の分岐で東）。
     * 既存路線と同じ slug を指定すると経路を引き直して上書きする（グループ・種別は維持）。
     */
    @Command("add <slug> <startPoint> <endPoint> <flags>")
    @CommandDescription("駅間の経路を計算して路線を登録・引き直します（flags: 出発方角＋分岐で選ぶ方角。例: EE）")
    @Permission("advancerailway.railway.manage")
    suspend fun register(
        sender: CommandSender,
        @Argument("slug") rawSlug: String,
        @Argument("startPoint") startPoint: Point3D,
        @Argument("endPoint") endPoint: Point3D,
        @Argument("flags") flags: String,
    ) {
        val slug = Slug.parse(rawSlug) ?: run {
            sender.sendRichMessage("<red>路線 slug が不正です: <white>$rawSlug</white>")
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
        val fromStation = StationUtils.nearStation(startPoint.toLocation(world)) ?: run {
            sender.sendRichMessage("<red>始点付近の駅が見つかりません。")
            return
        }
        val toStation = StationUtils.nearStation(endPoint.toLocation(world)) ?: run {
            sender.sendRichMessage("<red>終点付近の駅が見つかりません。")
            return
        }
        // 引き直し（上書き）の場合はグループ・種別の設定を引き継ぐ。
        val existing = railwayRepository.findBySlug(slug)
        val data = RailwayData(
            id = existing?.id ?: RailwayId.new(),
            slug = slug,
            group = existing?.group,
            worldName = world.name,
            lineType = existing?.lineType ?: LineType.UP_DOWN_LINE,
            line = line,
            fromStation = fromStation.id,
            toStation = toStation.id,
            timeRequired = line.getLength().toLong() / 8,
            startPoint = startPoint,
            endPoint = endPoint,
            flags = branchFlags.joinToString("") { it.label },
            // 今トレースした経路そのものを保存しているので、この瞬間は確認済みとして扱う。
            lastCheckedAt = Instant.now(),
        )
        if (existing == null) railwayRepository.insert(data) else railwayRepository.update(data)
        MapRenderer.refresh()
        val action = if (existing == null) "登録" else "上書き（引き直し）"
        sender.sendRichMessage("<green>路線を${action}しました: <white>$rawSlug</white>")
    }

    /**
     * 路線の経路が保存時から変わっていないか、開始点＋flags から再トレースして検証する。
     * 未ロードチャンクの同期ロードで重くなるためコマンドでの手動実行に限定している。
     * 失敗行の [TP] で始点駅へ飛んで現地を確認できる。
     *
     * 検証に成功した路線には確認時刻を記録する（`/ar railway info` で確認できる）。
     * `--group` / `--station` で対象を絞れる（両方指定した場合は AND）。
     */
    @Command("check")
    @CommandDescription("路線の経路が保存時から変わっていないか検証します（--group/--station で絞り込み）")
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
                if (group != null || station != null) "<yellow>条件に一致する路線がありません。"
                else "<yellow>検証対象の路線がありません。"
            )
            return
        }
        for (problem in result.problems) {
            val slug = problem.data.slug.value
            val fromStation = stationRepository.findById(problem.data.fromStation)
            val tp = fromStation?.let { stationTpLink(it.slug) } ?: ""
            val since = formatCheckedAt(problem.data.lastCheckedAt)
            when (problem) {
                is RailwayVerifier.TraceFailed -> sender.sendRichMessage(
                    "<red>再トレース失敗: <white>$slug</white> (${problem.error}) — " +
                        "レールが変更された可能性があります <gray>(最終確認: $since)</gray> $tp"
                )

                is RailwayVerifier.RouteChanged -> sender.sendRichMessage(
                    "<red>経路変化: <white>$slug</white> — 経路が保存時から変化しています " +
                        "<gray>(最終確認: $since)</gray> $tp"
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
        val data = railwayRepository.findById(railwayId) ?: run {
            sender.sendRichMessage("<red>路線が見つかりません。")
            return
        }
        railwayRepository.delete(railwayId)
        MapRenderer.refresh()
        sender.sendRichMessage("<green>路線を削除しました: <white>${data.slug.value}</white>")
    }
}
