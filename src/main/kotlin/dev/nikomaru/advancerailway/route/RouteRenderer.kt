/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.route

import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import kotlin.math.ceil

/** 表示用に駅名・路線名を解決した経路の 1 区間。 */
data class RenderedLeg(
    /** 1 始まりの区間番号。 */
    val index: Int,
    /** 出発地の表示名（駅名、または起点が現在地なら originLabel）。 */
    val fromLabel: String,
    /** 到着駅の表示名。 */
    val toLabel: String,
    val mode: TravelMode,
    /** レール区間の路線（グループ）名。徒歩区間は `null`。 */
    val lineLabel: String?,
    /** レール区間の路線 ID（`/ar railway info` へのリンク用）。徒歩区間は `null`。 */
    val railwayId: RailwayId?,
    /** レール区間の路線（グループ）ID。まとめ表示のグルーピングキー。徒歩区間・無所属レールは `null`。 */
    val group: GroupId?,
    /** この区間の所要時間（秒）。まとめ表示で合算するため保持する。 */
    val timeSeconds: Long,
    /** この区間の所要時間（分、小数第 1 位）。 */
    val minutes: Double,
)

/**
 * 表示用に、連続する同一路線のレール区間を 1 つにまとめたセグメント。
 * 徒歩区間・無所属レール（路線 ID があってもグループ未設定）の区間は、それぞれ 1 セグメントとして残す。
 */
data class RenderedSegment(
    /** 1 始まりのセグメント番号。 */
    val index: Int,
    /** セグメント先頭の出発地表示名。 */
    val fromLabel: String,
    /** セグメント末尾の到着駅表示名。 */
    val toLabel: String,
    val mode: TravelMode,
    /** レールセグメントの路線名。徒歩・無所属レールは `null`。 */
    val lineLabel: String?,
    /** 単一区間のセグメントのときのみ路線 ID（`[詳細]` リンク用）。複数区間をまとめた場合は `null`。 */
    val railwayId: RailwayId?,
    /** このセグメントにまとめた区間（レッグ）数。 */
    val legCount: Int,
    /** セグメント全体の所要時間（分、小数第 1 位）。区間ごとの秒を合算してから丸める。 */
    val minutes: Double,
)

/** 表示用に解決した経路全体。 */
data class RenderedRoute(
    val fromLabel: String,
    val toLabel: String,
    val totalMinutes: Double,
    val legCount: Int,
    val legs: List<RenderedLeg>,
)

/**
 * [Route]（ID のみを持つ）を、駅名・路線名を解決した表示用データ [RenderedRoute] へ変換する純粋ロジック。
 *
 * ID ではなく人間が読める名前を出すことが目的。名前解決関数が `null`（未登録）を返した場合は ID へフォールバックする。
 * Bukkit / Koin に依存しないため、実データを使った単体テストができる。
 */
object RouteRenderer {

    /** 秒を「小数第 1 位までの分」へ丸める（[dev.nikomaru.advancerailway.commands.railway.RailwayInfoCommand] と同じ表記）。 */
    fun minutes(seconds: Long): Double = ceil(seconds / 6.0) / 10

    /**
     * @param route 経路（[RouteFinder.findRoute] の結果）。
     * @param originLabel 起点の表示名（現在地なら「現在地」、駅起点なら駅名）。
     * @param stationName 駅 ID → 表示名。未登録・空文字なら ID へフォールバックさせるため `null` を返してよい。
     * @param groupName 路線（グループ）ID → 表示名。同上。
     */
    fun render(
        route: Route,
        originLabel: String,
        stationName: (StationId) -> String?,
        groupName: (GroupId) -> String?,
    ): RenderedRoute {
        fun station(id: StationId): String = stationName(id)?.takeIf { it.isNotBlank() } ?: id.value

        val legs = route.legs.mapIndexed { index, leg ->
            RenderedLeg(
                index = index + 1,
                fromLabel = leg.from?.let { station(it) } ?: originLabel,
                toLabel = station(leg.to),
                mode = leg.mode,
                lineLabel = leg.group?.let { groupName(it)?.takeIf(String::isNotBlank) ?: it.value },
                railwayId = leg.railwayId,
                group = leg.group,
                timeSeconds = leg.timeSeconds,
                minutes = minutes(leg.timeSeconds),
            )
        }
        return RenderedRoute(
            fromLabel = originLabel,
            toLabel = legs.lastOrNull()?.toLabel ?: originLabel,
            totalMinutes = minutes(route.totalSeconds),
            legCount = legs.size,
            legs = legs,
        )
    }

    /**
     * 連続する同一路線（同一グループ）のレール区間を 1 セグメントにまとめる。
     *
     * まとめ条件は「両区間ともレール」かつ「両区間とも同一の非 null グループ」。
     * 徒歩区間や、グループ未設定のレール区間（路線名を持たない区間）は、それぞれ独立したセグメントにする。
     * まとめた区間の所要時間は、各区間の秒を合算してから丸めることで丸め誤差の累積を防ぐ。
     *
     * @param legs [render] が返した区間リスト（[RenderedRoute.legs]）。
     * @return 路線ごとにまとめた表示用セグメント列（番号は 1 から振り直す）。
     */
    fun groupLegs(legs: List<RenderedLeg>): List<RenderedSegment> {
        val groups = mutableListOf<MutableList<RenderedLeg>>()
        for (leg in legs) {
            val current = groups.lastOrNull()
            val mergeable = current != null &&
                leg.mode == TravelMode.RAIL &&
                current.last().mode == TravelMode.RAIL &&
                leg.group != null &&
                current.last().group == leg.group
            if (mergeable) current!!.add(leg) else groups.add(mutableListOf(leg))
        }
        return groups.mapIndexed { index, group ->
            val first = group.first()
            val last = group.last()
            RenderedSegment(
                index = index + 1,
                fromLabel = first.fromLabel,
                toLabel = last.toLabel,
                mode = first.mode,
                lineLabel = first.lineLabel,
                // 単一区間のときだけ [詳細] リンク用の路線 ID を残す。
                railwayId = group.singleOrNull()?.railwayId,
                legCount = group.size,
                minutes = minutes(group.sumOf { it.timeSeconds }),
            )
        }
    }
}
