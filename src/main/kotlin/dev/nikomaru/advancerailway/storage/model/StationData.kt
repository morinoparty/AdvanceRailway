/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.model

import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import java.awt.Color
import kotlin.random.Random

/**
 * 駅。永続化は [dev.nikomaru.advancerailway.storage.database.repository.StationRepository] が担う。
 *
 * ワールドは実体ではなく名前で持つ。ワールドが読み込まれていない環境でも
 * 駅データ自体は読み出せる必要があるため（一覧・API など）。
 *
 * ナンバリングは駅ではなくグループ（路線）が持つ。[GroupData.numberingPrefix] と
 * グループ内の並び順から算出する。
 */
data class StationData(
    val id: StationId,
    val slug: Slug,
    val name: String,
    val worldName: String,
    val point: Point3D,
    val overrideSize: Double?,
    val color: Color,
) {
    companion object {
        /**
         * slug から決定論的に既定色を導出する。
         *
         * 主キーが UUID になった今も、色は **slug** をシードにする。UUID をシードにすると
         * 移行のたびに既存駅の色が変わってしまうため。導出結果は作成時に実体化して保存する。
         */
        fun defaultColor(slug: Slug): Color {
            val random = Random(slug.value.hashCode().toLong())
            return Color(random.nextInt(256), random.nextInt(256), random.nextInt(256))
        }
    }
}
