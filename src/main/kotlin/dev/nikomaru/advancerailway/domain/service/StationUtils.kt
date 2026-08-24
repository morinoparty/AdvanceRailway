/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.domain.service

import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.StationData
import dev.nikomaru.advancerailway.utils.Utils.toPoint3D
import org.bukkit.Location
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 駅にまつわるドメイン処理。データの読み書き自体は [StationRepository] が持つ。
 */
object StationUtils : KoinComponent {
    private val stationRepository: StationRepository by inject()

    /**
     * [location] に最も近い駅を返す。見つからなければ null。
     *
     * 最寄り駅は同一ワールド内でのみ判定する（別ディメンションの駅を誤って選ばないため）。
     * 同一ワールド内では座標が 1:1 のため、ネザー等のスケーリング補正は不要。
     */
    suspend fun nearStation(location: Location): StationData? {
        val worldName = location.world?.name ?: return null
        val target = location.toPoint3D()
        return stationRepository.findByWorld(worldName).minByOrNull { it.point.distanceTo2D(target) }
    }
}
