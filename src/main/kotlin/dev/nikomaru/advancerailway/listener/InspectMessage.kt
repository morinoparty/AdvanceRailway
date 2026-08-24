/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.listener

import dev.nikomaru.advancerailway.commands.nameWithSlug
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.rail.BranchEndpoint
import dev.nikomaru.advancerailway.domain.rail.EndpointKind
import dev.nikomaru.advancerailway.storage.model.StationData

/**
 * `/ar inspect` が返す終端 1 行の組み立て。
 *
 * Bukkit にもデータベースにも依存しない純粋な文字列生成にしてある。`[作成]` のリンクが
 * 出るかどうかは「クリックしてすぐ路線を登録できるか」を決める要なのに、実際にレールを
 * 敷いた状態でしか確認できないと壊れても気づけないため、ここだけ切り出してテストできるようにした。
 */
internal object InspectMessage {

    /**
     * 一覧に出す終端と、環状・合流で打ち切られて除外した件数に分ける。
     *
     * 同じ線路へ戻ってくる経路は路線として登録できない（`ar railway add` の始点と終点が
     * 同じ線路上に乗ってしまう）ので一覧からは外す。ただし黙って消すと「0 件」になった
     * 理由が分からないため、件数だけは呼び出し側が伝えられるように返す。
     */
    fun partitionForDisplay(endpoints: List<BranchEndpoint>): Pair<List<BranchEndpoint>, Int> {
        val (loops, shown) = endpoints.partition { it.kind == EndpointKind.LOOP }
        return shown to loops.size
    }

    /** 除外した件数の注記。除外が無ければ null。 */
    fun excludedNote(excluded: Int): String? =
        if (excluded <= 0) null else "<gray>（同じ線路に戻ってくる経路 $excluded 件は除外しました）"

    /** 行頭の `[flags: EE]` と終端の種別。 */
    fun label(endpoint: BranchEndpoint): String = buildString {
        append("<yellow>[flags: ${endpoint.flagString()}]</yellow> ")
        when (endpoint.kind) {
            EndpointKind.STOP_BLOCK -> append("<gold>[停止ブロック]</gold> ")
            EndpointKind.LOOP -> append("<gray>[環状/合流]</gray> ")
            EndpointKind.RAIL_END -> {}
        }
    }

    /**
     * 終端 1 件の表示行を組み立てる。
     *
     * @param flags `/ar railway add` にそのまま渡せるフラグ列。算出できない向きでは null で、
     *   その場合 `[作成]` は出さない（誤ったフラグで登録させないため）。
     * @return 送信する行。始点と終点の最寄り駅が同じ（自駅へ戻ってくる経路）の場合は null。
     */
    fun endpointLine(
        label: String,
        startStation: StationData?,
        endStation: StationData?,
        start: Point3D,
        end: Point3D,
        flags: String?,
    ): String? {
        if (startStation != null && startStation.id == endStation?.id) {
            // 自分の駅に戻ってくる経路は表示しない
            return null
        }
        if (startStation == null || endStation == null) {
            return "$label<white>${start.toPlainString()} -> ${end.toPlainString()}</white> " +
                "<gray>(付近に駅が登録されていません)"
        }
        val suggest = if (flags.isNullOrEmpty()) {
            ""
        } else {
            val slug = "${startStation.slug.value}_${endStation.slug.value}"
            " <click:suggest_command:'/ar railway add $slug ${start.toPlainString()} " +
                "${end.toPlainString()} $flags'><green>[作成]</green></click>"
        }
        // 駅名は必ず nameWithSlug 経由で出す。エスケープを挟まないと、名前に含まれる `<` が
        // MiniMessage のタグとして解釈され、後ろの [作成] リンクごと表示から消える。
        return "$label${nameWithSlug(startStation.name, startStation.slug)} " +
            "<gray>${start.toPlainString()}</gray> <yellow>-></yellow> " +
            "${nameWithSlug(endStation.name, endStation.slug)} " +
            "<gray>${end.toPlainString()}</gray>$suggest"
    }
}
