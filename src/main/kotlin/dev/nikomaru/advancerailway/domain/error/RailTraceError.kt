/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.domain.error

enum class RailTraceError {
    ATTACHED_TO_LIMIT, MULTIPLE_RAIL, NOT_FOUND_END_POINT, DIRECTION_NOT_FOUND
}

fun RailTraceError.toUserMessage(): String = when (this) {
    RailTraceError.ATTACHED_TO_LIMIT -> "<red>探索が上限に達しました。config の limit / inspectMaxEndpoints を確認してください。"
    RailTraceError.MULTIPLE_RAIL -> "<red>経路の途中に分岐があるため、単一経路を特定できませんでした。"
    RailTraceError.NOT_FOUND_END_POINT -> "<red>終点にたどり着けませんでした。"
    RailTraceError.DIRECTION_NOT_FOUND -> "<red>flags の出発方角に対応する隣接レールが見つかりません。"
}