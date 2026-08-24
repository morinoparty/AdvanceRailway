/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.platform.map

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.ConfigData
import dev.nikomaru.advancerailway.storage.model.GroupData
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.storage.model.StationData
import dev.nikomaru.advancerailway.storage.type.LineType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import xyz.jpenilla.squaremap.api.Key
import xyz.jpenilla.squaremap.api.Point
import xyz.jpenilla.squaremap.api.SimpleLayerProvider
import xyz.jpenilla.squaremap.api.marker.Marker
import xyz.jpenilla.squaremap.api.marker.MarkerOptions
import java.awt.Color
import kotlin.math.ceil
import kotlin.random.Random

/**
 * squaremap 上の駅・路線マーカーを、データベースの現在の内容から描き直す。
 *
 * 以前はファイル読み込みとマーカー描画が同じクラス（FileLoader と各 DataLoader）に同居していたが、
 * データの読み出しはリポジトリの責務になったため、ここは描画だけを持つ。
 * データを書き換えたコマンド・API は最後に [refresh] を呼ぶ。
 */
object MapRenderer : KoinComponent {
    private val plugin: AdvanceRailway by inject()
    private val provider: SimpleLayerProvider by inject()
    private val config: ConfigData by inject()
    private val stationRepository: StationRepository by inject()
    private val railwayRepository: RailwayRepository by inject()
    private val groupRepository: GroupRepository by inject()

    /**
     * 全消去→再構築を直列化するためのロック。
     * 複数の保存処理が並行して呼んでも clearMarkers() と addMarker() が交錯しないようにする。
     */
    private val mutex = Mutex()

    /** データベースの現在の内容でマーカーを描き直す。 */
    suspend fun refresh() = mutex.withLock {
        val stations = stationRepository.findAll()
        val railways = railwayRepository.findAll()
        val groups = groupRepository.findAll().associateBy { it.id }
        val numberings = groupRepository.allGroupsOfStations()
            .mapValues { (_, groups) -> groups.mapNotNull { it.numbering } }

        provider.clearMarkers()
        renderStations(stations, railways, numberings)
        renderRailways(railways, stations.associateBy { it.id }, groups)
    }

    private fun renderStations(
        stations: List<StationData>,
        railways: List<RailwayData>,
        numberings: Map<StationId, List<String>>,
    ) {
        // 接続路線数。マーカーの既定サイズに使う。
        val joinedCount = hashMapOf<StationId, Int>()
        railways.forEach { railway ->
            joinedCount[railway.fromStation] = joinedCount.getOrDefault(railway.fromStation, 0) + 1
            joinedCount[railway.toStation] = joinedCount.getOrDefault(railway.toStation, 0) + 1
        }

        stations.forEach { station ->
            val color = station.color
            // ナンバリングは駅ではなくグループが持つため、所属グループから算出した番号を並べる
            // （乗換駅は複数の番号を持つ）。所属が無ければ行ごと出さない。
            val numbering = numberings[station.id]?.takeIf { it.isNotEmpty() }
                ?.joinToString(" / ")
                ?.let { "$it </span><br/>" }
                ?: ""
            val options = MarkerOptions.builder().fillColor(color.brighter()).strokeColor(color).clickTooltip(
                """
                $numbering
                名前 : ${station.name} </span><br/>
                """.trimIndent()
            )
            val size = station.overrideSize
                ?: joinedCount[station.id]?.let { config.circleMultiple.times(it) }
                ?: config.circleSizeBase
            val marker = Marker.circle(Point.of(station.point.x, station.point.z), size).markerOptions(options)
            provider.addMarker(Key.of("station-${station.slug.value}"), marker)
        }
    }

    private fun renderRailways(
        railways: List<RailwayData>,
        stations: Map<StationId, StationData>,
        groups: Map<dev.nikomaru.advancerailway.domain.id.GroupId, GroupData>,
    ) {
        railways.forEach { railway ->
            val marker = Marker.multiPolyline(railway.line.points.map { Point.of(it.x, it.z) })
            val arrow = if (railway.lineType == LineType.UP_DOWN_LINE) "<->" else "->"
            val fromData = stations[railway.fromStation]
            val toData = stations[railway.toStation]
            val groupData = railway.group?.let { groups[it] }
            // FK があるので通常は解決できるが、想定外の状態でもツールチップに "null" を出さないよう
            // ID をプレースホルダとして表示し、警告を残す。
            if (fromData == null || toData == null) {
                plugin.logger.warning(
                    "Railway '${railway.slug.value}' references missing station(s): " +
                        "from=${railway.fromStation}${if (fromData == null) " (missing)" else ""}, " +
                        "to=${railway.toStation}${if (toData == null) " (missing)" else ""}"
                )
            }
            val fromName = fromData?.name ?: railway.fromStation.toString()
            val toName = toData?.name ?: railway.toStation.toString()
            val options = MarkerOptions.builder().clickTooltip(
                """
                行き先 : $fromName $arrow $toName </span><br/>
                所要時間 : 約 ${ceil(railway.timeRequired / 6.0) / 10} 分 </span><br/>
                ${groupData?.name ?: ""}
                """.trimIndent()
            )
            val color = groupData?.railwayColor ?: randomColorFor(railway)
            options.fillColor(color)
            options.strokeColor(color)
            marker.markerOptions(options)
            provider.addMarker(Key.of("railway-${railway.slug.value}"), marker)
        }
    }

    /** グループ未設定の路線の色。グループ ID をシードにして描画のたびに変わらないようにする。 */
    private fun randomColorFor(railway: RailwayData): Color {
        val random = Random(railway.group.hashCode().toLong())
        return Color(random.nextInt(256), random.nextInt(256), random.nextInt(256))
    }
}
