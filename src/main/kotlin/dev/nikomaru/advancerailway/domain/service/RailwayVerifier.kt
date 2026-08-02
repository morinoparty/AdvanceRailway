/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.domain.service

import arrow.core.Either
import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.storage.DataPaths
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.utils.Utils.json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 起動時に V2 路線の経路を検証する。
 * V2 は開始点＋分岐フラグから経路を再現できるため、再トレース結果が保存済みの
 * 経路と一致するかを確認し、レールが変更されていれば警告を出す。
 *
 * 再トレースはメインスレッドで走り、未ロードのチャンクを同期ロードしうるため、
 * 起動時に一度だけ実行する（reload や save のたびには走らせない）。
 */
object RailwayVerifier: KoinComponent {
    private val plugin: AdvanceRailway by inject()

    suspend fun verifyAll() {
        val files = DataPaths.railways.listFiles()?.filter { it.extension == "json" } ?: return
        var checked = 0
        var changed = 0
        for (file in files) {
            val data = try {
                json.decodeFromString<RailwayData>(file.readText())
            } catch (e: Exception) {
                continue // 壊れたファイルは RailwayDataLoader が警告済み
            }
            if (data !is RailwayData.V2) {
                continue
            }
            checked++
            when (val traced = RailwayUtils.getLine(data.startPoint, data.directionPoint, data.endPoint, data.flags)) {
                is Either.Left -> {
                    changed++
                    plugin.logger.warning(
                        "路線 '${data.id.value}' の経路を再トレースできませんでした (${traced.value})。レールが変更された可能性があります。/ar railway redraw で引き直してください。"
                    )
                }

                is Either.Right -> if (traced.value.points != data.line.points) {
                    changed++
                    plugin.logger.warning(
                        "路線 '${data.id.value}' の経路が保存時から変化しています。/ar railway redraw で引き直してください。"
                    )
                }
            }
        }
        if (checked > 0) {
            plugin.logger.info("V2 路線の経路検証: $checked 件中 $changed 件に変更を検出しました。")
        }
    }
}
