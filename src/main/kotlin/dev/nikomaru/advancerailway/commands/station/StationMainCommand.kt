/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.station

import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.file.DataPaths
import dev.nikomaru.advancerailway.file.FileLoader
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.data.StationData
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.utils.Utils.json
import dev.nikomaru.advancerailway.utils.Utils.toPoint3D
import kotlinx.serialization.decodeFromString
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission

@Command("ar|advancerailway station")
class StationMainCommand {

    @Command("add <id> <name> [point]")
    @CommandDescription("駅を新規登録します（座標省略時は実行者の現在地）")
    @Permission("advancerailway.station.manage")
    suspend fun add(
        sender: CommandSender,
        @Argument("id") id: String,
        @Argument("name") name: String,
        @Argument("point") point: Point3D?,
    ) { // Add station
        if (sender !is Player && point == null) {
            sender.sendRichMessage("<red>座標を指定してください（プレイヤー以外は必須です）。")
            return
        }
        if (!IdValidation.isValid(id)) {
            sender.sendRichMessage("<red>駅 ID が不正です: <white>$id</white>")
            return
        }
        val resolvedPoint = point ?: (sender as Player).location.toPoint3D()
        val world = if (sender is Player) {
            sender.world
        } else {
            Bukkit.getWorld("world") ?: run {
                sender.sendRichMessage("<red>ワールド \"world\" が見つかりません。")
                return
            }
        }
        val stationId = StationId(id)
        val data = StationData(stationId, name, null, world, resolvedPoint, null)
        data.save()
        sender.sendRichMessage("<green>駅を追加しました。")
    }

    @Command("remove <id>")
    @CommandDescription("駅を削除します（依存する路線がある場合は削除できません）")
    @Permission("advancerailway.station.manage")
    suspend fun remove(sender: CommandSender, @Argument("id") id: StationId) { // Remove station
        val file = DataPaths.stations.resolve("${id.value}.json")
        if (!file.exists()) {
            sender.sendRichMessage("<red>駅が見つかりません。")
            return
        }
        val dependents = (DataPaths.railways.listFiles() ?: emptyArray()).mapNotNull { railwayFile ->
            runCatching { json.decodeFromString<RailwayData>(railwayFile.readText()) }.getOrNull()
        }.filter { it.fromStation == id || it.toStation == id }.map { it.id.value }
        if (dependents.isNotEmpty()) {
            sender.sendRichMessage(
                "<red>駅 <yellow>${id.value}</yellow> は削除できません。" +
                    "次の路線が参照しています: <white>${dependents.joinToString(", ")}</white>"
            )
            return
        }
        file.delete()
        FileLoader.mapDataLoad()
        sender.sendRichMessage("<green>駅を削除しました。")
    }

}
