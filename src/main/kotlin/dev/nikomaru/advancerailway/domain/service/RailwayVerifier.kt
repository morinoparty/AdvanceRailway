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
import dev.nikomaru.advancerailway.domain.error.RailTraceError
import dev.nikomaru.advancerailway.storage.DataPaths
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.utils.Utils.json

/**
 * V2 路線の経路を検証する（`/ar railway check` から実行）。
 * V2 は開始点＋分岐フラグから経路を再現できるため、再トレース結果が保存済みの
 * 経路と一致するかを確認し、レールが変更されていれば問題として報告する。
 *
 * 再トレースはメインスレッドで走り、未ロードのチャンクを同期ロードしうるため、
 * 起動時の自動実行はやめてコマンドでの手動実行に限定している。
 */
object RailwayVerifier {

    /** 検証で見つかった問題。いずれも `/ar railway redraw` で引き直すと解消できる。 */
    sealed interface Problem {
        val data: RailwayData.V2
    }

    /** 再トレース自体が失敗した（レールが撤去・変更された可能性がある）。 */
    data class TraceFailed(override val data: RailwayData.V2, val error: RailTraceError): Problem

    /** 再トレースはできたが、経路が保存時と一致しない。 */
    data class RouteChanged(override val data: RailwayData.V2): Problem

    data class Result(val checked: Int, val problems: List<Problem>)

    suspend fun verifyAll(): Result {
        val files = DataPaths.railways.listFiles()?.filter { it.extension == "json" } ?: return Result(0, emptyList())
        var checked = 0
        val problems = mutableListOf<Problem>()
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
                is Either.Left -> problems += TraceFailed(data, traced.value)
                is Either.Right -> if (traced.value.points != data.line.points) {
                    problems += RouteChanged(data)
                }
            }
        }
        return Result(checked, problems)
    }
}
