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
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.model.RailwayData
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

/**
 * 路線の経路を検証する（`/ar railway check` から実行）。
 * 開始点＋flags（出発方角＋分岐方角列）から経路を再現できるため、再トレース結果が
 * 保存済みの経路と一致するかを確認し、レールが変更されていれば問題として報告する。
 *
 * 再トレースはメインスレッドで走り、未ロードのチャンクを同期ロードしうるため、
 * 起動時の自動実行はやめてコマンドでの手動実行に限定している。
 */
object RailwayVerifier : KoinComponent {
    private val railwayRepository: RailwayRepository by inject()

    /** 検証で見つかった問題。いずれも `/ar railway add` で引き直すと解消できる。 */
    sealed interface Problem {
        val data: RailwayData
    }

    /** 再トレース自体が失敗した（レールが撤去・変更された可能性がある）。 */
    data class TraceFailed(override val data: RailwayData, val error: RailTraceError) : Problem

    /** 再トレースはできたが、経路が保存時と一致しない。 */
    data class RouteChanged(override val data: RailwayData) : Problem

    data class Result(val checked: Int, val problems: List<Problem>)

    /**
     * [filter] に一致した路線だけを検証する（既定は全件）。
     *
     * 経路が一致した路線には確認時刻（[RailwayData.lastCheckedAt]）を記録する。
     * 問題が見つかった路線は更新しないので、前回成功した時刻が残る。
     */
    suspend fun verifyAll(filter: (RailwayData) -> Boolean = { true }): Result {
        var checked = 0
        val problems = mutableListOf<Problem>()
        val verified = mutableListOf<RailwayData>()
        for (data in railwayRepository.findAll()) {
            if (!filter(data)) continue
            checked++
            val flags = data.branchFlags()
            if (flags == null) {
                problems += TraceFailed(data, RailTraceError.DIRECTION_NOT_FOUND)
                continue
            }
            when (val traced = RailwayUtils.getLine(data.startPoint, data.endPoint, flags)) {
                is Either.Left -> problems += TraceFailed(data, traced.value)
                is Either.Right -> if (traced.value.points != data.line.points) {
                    problems += RouteChanged(data)
                } else {
                    verified += data
                }
            }
        }
        railwayRepository.markChecked(verified.map { it.id }, Instant.now())
        return Result(checked, problems)
    }
}
