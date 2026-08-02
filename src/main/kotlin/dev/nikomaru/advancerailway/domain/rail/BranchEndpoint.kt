/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.domain.rail

/**
 * 分岐点で選んだ進行方角。Minecraft 座標系では dx=+1 が東、dz=+1 が南。
 */
enum class BranchDirection(val label: String) {
    NORTH("N"), SOUTH("S"), EAST("E"), WEST("W");

    companion object {
        fun from(dx: Int, dz: Int): BranchDirection? = when {
            dx > 0 -> EAST
            dx < 0 -> WEST
            dz > 0 -> SOUTH
            dz < 0 -> NORTH
            else -> null
        }

        /** "EE" のようなフラグ文字列を方角列に変換する。不正な文字が含まれる場合は null。 */
        fun parse(flags: String): List<BranchDirection>? {
            return flags.uppercase().map { c ->
                entries.find { it.label == c.toString() } ?: return null
            }
        }
    }
}

/** 探索が終端に達した理由。 */
enum class EndpointKind {
    /** 先にレールがない（線路の末端）。 */
    RAIL_END,

    /** レール直下が config の inspectStopBlocks に含まれるブロックだった。 */
    STOP_BLOCK,

    /** 既に通過したレールに戻ってきた（環状線路・合流）。 */
    LOOP,
}

/**
 * inspect の分岐探索で見つかった1つの終端。
 *
 * [flags] は通過した各分岐点で選んだ方角の並び。
 * 例: 分岐を2回ともに東へ進んだ経路は [EAST, EAST]（表示 "EE"）。分岐なしなら空。
 */
data class BranchEndpoint(
    val flags: List<BranchDirection>,
    val kind: EndpointKind,
    /** start=クリック点, direction=最初の隣接レール, end=終端 */
    val forward: InspectData,
    /** start=終端, direction=終端の1つ手前, end=クリック点 */
    val backward: InspectData,
) {
    fun flagString(): String = flags.joinToString("") { it.label }
}
