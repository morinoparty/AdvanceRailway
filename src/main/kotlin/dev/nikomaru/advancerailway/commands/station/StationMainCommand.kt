/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.station

import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.platform.map.MapRenderer
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.StationData
import dev.nikomaru.advancerailway.utils.Utils.toLocation
import dev.nikomaru.advancerailway.utils.Utils.toPoint3D
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import dev.nikomaru.advancerailway.AdvanceRailway
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Command("ar|advancerailway station")
class StationMainCommand : KoinComponent {

    private val plugin: AdvanceRailway by inject()
    private val stationRepository: StationRepository by inject()
    private val railwayRepository: RailwayRepository by inject()

    /**
     * 駅を新規登録する。引数の `slug` は人間が使う短い識別子で、主キーは UUIDv7 が自動採番される。
     */
    @Command("add <slug> <name> [point]")
    @CommandDescription("駅を新規登録します（座標省略時は実行者の現在地）")
    @Permission("advancerailway.station.manage")
    suspend fun add(
        sender: CommandSender,
        @Argument("slug") rawSlug: String,
        @Argument("name") name: String,
        @Argument("point") point: Point3D?,
    ) {
        if (sender !is Player && point == null) {
            sender.sendRichMessage("<red>座標を指定してください（プレイヤー以外は必須です）。")
            return
        }
        val slug = Slug.parse(rawSlug) ?: run {
            sender.sendRichMessage("<red>駅 slug が不正です: <white>$rawSlug</white>")
            return
        }
        if (stationRepository.slugExists(slug)) {
            sender.sendRichMessage("<red>その slug は既に使われています: <white>$rawSlug</white>")
            return
        }
        val resolvedPoint = point ?: (sender as Player).location.toPoint3D()
        val worldName = if (sender is Player) {
            sender.world.name
        } else {
            Bukkit.getWorld("world")?.name ?: run {
                sender.sendRichMessage("<red>ワールド \"world\" が見つかりません。")
                return
            }
        }
        stationRepository.insert(
            StationData(
                id = StationId.new(),
                slug = slug,
                name = name,
                worldName = worldName,
                point = resolvedPoint,
                overrideSize = null,
                color = StationData.defaultColor(slug),
            )
        )
        MapRenderer.refresh()
        sender.sendRichMessage("<green>駅を追加しました。")
    }

    @Command("remove <stationId>")
    @CommandDescription("駅を削除します（依存する路線がある場合は削除できません）")
    @Permission("advancerailway.station.manage")
    suspend fun remove(sender: CommandSender, @Argument("stationId") stationId: StationId) {
        val data = stationRepository.findById(stationId) ?: run {
            sender.sendRichMessage("<red>駅が見つかりません。")
            return
        }
        val dependents = railwayRepository.findByStation(stationId)
        if (dependents.isNotEmpty()) {
            sender.sendRichMessage(
                "<red>駅 <yellow>${data.slug.value}</yellow> は削除できません。" +
                    "次の路線が参照しています: <white>${dependents.joinToString(", ") { it.slug.value }}</white>"
            )
            return
        }
        stationRepository.delete(stationId)
        MapRenderer.refresh()
        sender.sendRichMessage("<green>駅を削除しました。")
    }

    @Command("tp <stationId>")
    @CommandDescription("駅の座標へテレポートします（プレイヤー専用）")
    @Permission("advancerailway.station.tp")
    suspend fun tp(sender: CommandSender, @Argument("stationId") stationId: StationId) {
        if (sender !is Player) {
            sender.sendRichMessage("<red>このコマンドはプレイヤー専用です。")
            return
        }
        val data = stationRepository.findById(stationId) ?: run {
            sender.sendRichMessage("<red>駅が見つかりません。")
            return
        }
        val world = Bukkit.getWorld(data.worldName) ?: run {
            sender.sendRichMessage("<red>ワールドが見つかりません: <white>${data.worldName}</white>")
            return
        }
        // teleport はメインスレッド専用 API。
        withContext(plugin.minecraftDispatcher) {
            sender.teleport(data.point.toLocation(world))
        }
        sender.sendRichMessage("<green>${data.name} へテレポートしました。")
    }
}
