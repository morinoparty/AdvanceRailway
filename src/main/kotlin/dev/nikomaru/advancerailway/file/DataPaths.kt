/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file

import dev.nikomaru.advancerailway.AdvanceRailway
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * プラグインデータフォルダ配下のパスを一元化するヘルパ。
 *
 * コマンド・ファイル永続化・ユーティリティ・MineAuth 連携のあちこちに散らばっていた
 * `dataFolder.resolve("data").resolve("groups"/"railways"/"stations")` という重複したリテラルを
 * 1 箇所に集約する。データフォルダ構成を変更する際にここだけを直せば済むようにするのが目的。
 *
 * [AdvanceRailway] は [StationUtils][dev.nikomaru.advancerailway.utils.StationUtils] などの
 * 各 Utils と同様に Koin から解決する（`by inject()` は遅延評価のため、クラスロード時には触れない）。
 * アクセサを computed（`get()`）にしているのも、初期化順序に依存しないようにするため。
 */
object DataPaths : KoinComponent {
    private val plugin: AdvanceRailway by inject()

    /** `data/` 直下。各種データフォルダの親。 */
    private val dataFolder: File get() = plugin.dataFolder.resolve("data")

    /** `data/groups/`。 */
    val groups: File get() = dataFolder.resolve("groups")

    /** `data/railways/`。 */
    val railways: File get() = dataFolder.resolve("railways")

    /** `data/stations/`。 */
    val stations: File get() = dataFolder.resolve("stations")

    /**
     * `data/<type>/` を返す。サブフォルダ名を動的に受け取る呼び出し
     * （例: `listIds("stations")` のような汎用処理）向け。
     */
    fun of(type: String): File = dataFolder.resolve(type)

    /** `import/`（プラグインデータ直下、`data/` の外）。CSV/JSON 一括取り込み用。 */
    val import: File get() = plugin.dataFolder.resolve("import")
}
