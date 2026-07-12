/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.utils.command

import dev.nikomaru.advancerailway.Point3D
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.context.CommandInput
import org.incendo.cloud.parser.ArgumentParseResult
import org.incendo.cloud.parser.ArgumentParser
import org.incendo.cloud.parser.ParserDescriptor
import org.incendo.cloud.suggestion.BlockingSuggestionProvider

/**
 * `x,y,z` 形式のトークンを [Point3D] に解決する Cloud パーサ。
 * プレイヤーが実行する場合は現在地を 1 件だけ補完候補として出す。
 * 解析の中核 [parsePoint3D] は Bukkit 非依存にして単体テストできるようにしている。
 */
class Point3DParser<C> :
    ArgumentParser<C, Point3D>,
    BlockingSuggestionProvider.Strings<C> {

    override fun parse(
        commandContext: CommandContext<C & Any>,
        commandInput: CommandInput,
    ): ArgumentParseResult<Point3D> {
        val input = commandInput.readString()
        return when (val point = parsePoint3D(input)) {
            null -> ArgumentParseResult.failure(
                IllegalArgumentException("座標は x,y,z 形式（数値 3 つ）で入力してください: $input")
            )

            else -> ArgumentParseResult.success(point)
        }
    }

    override fun stringSuggestions(
        commandContext: CommandContext<C>,
        input: CommandInput,
    ): Iterable<String> {
        val sender = commandContext.sender() as? Player ?: return emptyList()
        val loc = sender.location
        return listOf("${loc.x.toInt()},${loc.y.toInt()},${loc.z.toInt()}")
    }

    companion object {
        /** `x,y,z` を [Point3D] に解析する。形式不正・数値でない場合は null（Bukkit 非依存・テスト可能）。 */
        fun parsePoint3D(input: String): Point3D? {
            val components = input.split(",")
            if (components.size != 3) return null
            val nums = components.map { it.trim().toDoubleOrNull() ?: return null }
            return Point3D(nums[0], nums[1], nums[2])
        }

        fun point3DParser(): ParserDescriptor<CommandSender, Point3D> =
            ParserDescriptor.of(Point3DParser(), Point3D::class.java)
    }
}
