/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.database

import dev.nikomaru.advancerailway.storage.database.table.GroupStationTable
import dev.nikomaru.advancerailway.storage.database.table.GroupTable
import dev.nikomaru.advancerailway.storage.database.table.RailwayTable
import dev.nikomaru.advancerailway.storage.database.table.StationTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import java.io.File

/**
 * SQLite への接続とスキーマ作成。
 *
 * データは全てこの 1 ファイル（`<dataFolder>/advancerailway.db`）に入る。
 * 外部 DB は使わないため接続プールは持たず、Exposed の [Database.connect] に直接 URL を渡す。
 * テストからはインメモリの JDBC URL を渡して同じ経路を使う。
 */
object DatabaseInitializer {

    /** プラグインデータフォルダ内の SQLite ファイル名。 */
    const val DATABASE_FILE_NAME = "advancerailway.db"

    /** AdvanceRailway が管理する全テーブル。 */
    val ALL_TABLES = arrayOf(GroupTable, StationTable, RailwayTable, GroupStationTable)

    /** [dataFolder] 配下の SQLite ファイルへ接続する。 */
    fun connect(dataFolder: File): Database {
        dataFolder.mkdirs()
        return connect("jdbc:sqlite:${dataFolder.resolve(DATABASE_FILE_NAME).absolutePath}")
    }

    /** 任意の JDBC URL へ接続する（テスト用）。 */
    fun connect(jdbcUrl: String): Database = Database.connect(
        url = jdbcUrl,
        driver = "org.sqlite.JDBC",
        // SQLite は接続ごとに外部キー制約が既定で無効。有効にしないと、参照されている駅を
        // 消せてしまい、group_stations の ON DELETE CASCADE も効かない。
        setupConnection = { it.createStatement().use { statement -> statement.execute("PRAGMA foreign_keys = ON") } },
    )

    /**
     * テーブルを作成し、既存スキーマに不足している列があれば追加する。
     * 列の追加だけを扱う運用で、破壊的な変更（列の削除・型変更）は行わない。
     */
    fun createTables() {
        transaction {
            SchemaUtils.create(*ALL_TABLES)
            MigrationUtils.statementsRequiredForDatabaseMigration(*ALL_TABLES, withLogs = false)
                .forEach { exec(it) }
        }
    }
}
