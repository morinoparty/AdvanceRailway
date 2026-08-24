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
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.utils.Utils.toPoint3D
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.Color

@Command("ar|advancerailway station")
class StationEditCommand : KoinComponent {

    private val stationRepository: StationRepository by inject()

    @Command("set name <stationId> <newName>")
    @CommandDescription("駅の名前を変更します")
    @Permission("advancerailway.station.manage")
    suspend fun setName(
        sender: CommandSender,
        @Argument("stationId") stationId: StationId,
        @Argument("newName") newName: String,
    ) {
        val data = stationRepository.findById(stationId) ?: run {
            sender.sendRichMessage("<red>駅が見つかりません。")
            return
        }
        stationRepository.update(data.copy(name = newName))
        MapRenderer.refresh()
        sender.sendRichMessage("<green>駅名を変更しました。")
    }

    /** slug は主キーではないので後から変えられる。一意性だけ確認する。 */
    @Command("set slug <stationId> <newSlug>")
    @CommandDescription("駅の slug（短い識別子）を変更します")
    @Permission("advancerailway.station.manage")
    suspend fun setSlug(
        sender: CommandSender,
        @Argument("stationId") stationId: StationId,
        @Argument("newSlug") rawSlug: String,
    ) {
        val data = stationRepository.findById(stationId) ?: run {
            sender.sendRichMessage("<red>駅が見つかりません。")
            return
        }
        val slug = Slug.parse(rawSlug) ?: run {
            sender.sendRichMessage("<red>slug が不正です: <white>$rawSlug</white>")
            return
        }
        if (stationRepository.slugExists(slug, excluding = stationId)) {
            sender.sendRichMessage("<red>その slug は既に使われています: <white>$rawSlug</white>")
            return
        }
        stationRepository.update(data.copy(slug = slug))
        MapRenderer.refresh()
        sender.sendRichMessage("<green>slug を <white>$rawSlug</white> に変更しました。")
    }

    @Command("set location <stationId> [point]")
    @CommandDescription("駅の座標とワールドを設定します")
    @Permission("advancerailway.station.manage")
    suspend fun setLocation(
        sender: CommandSender,
        @Argument("stationId") stationId: StationId,
        @Argument("point") inputPoint: Point3D?,
    ) {
        if (sender !is Player && inputPoint == null) {
            sender.sendRichMessage("<red>座標を指定してください（プレイヤー以外は必須です）。")
            return
        }
        val point = inputPoint ?: (sender as Player).location.toPoint3D()
        val worldName = if (sender is Player) {
            sender.world.name
        } else {
            Bukkit.getWorld("world")?.name ?: run {
                sender.sendRichMessage("<red>ワールド \"world\" が見つかりません。")
                return
            }
        }
        val data = stationRepository.findById(stationId) ?: run {
            sender.sendRichMessage("<red>駅が見つかりません。")
            return
        }
        stationRepository.update(data.copy(point = point, worldName = worldName))
        MapRenderer.refresh()
        sender.sendRichMessage("<green>駅の座標を変更しました。")
    }

    @Command("set color <stationId> <r> <g> <b>")
    @CommandDescription("駅のカラー（RGB）を設定します")
    @Permission("advancerailway.station.manage")
    suspend fun setColor(
        sender: CommandSender,
        @Argument("stationId") stationId: StationId,
        @Argument("r") r: Int,
        @Argument("g") g: Int,
        @Argument("b") b: Int,
    ) {
        if (r !in 0..255 || g !in 0..255 || b !in 0..255) {
            sender.sendRichMessage("<red>RGB は各 0〜255 で指定してください。")
            return
        }
        val data = stationRepository.findById(stationId) ?: run {
            sender.sendRichMessage("<red>駅が見つかりません。")
            return
        }
        stationRepository.update(data.copy(color = Color(r, g, b)))
        MapRenderer.refresh()
        sender.sendRichMessage("<green>駅の色を変更しました。")
    }
}
