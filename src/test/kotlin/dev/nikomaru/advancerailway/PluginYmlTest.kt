/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml

/**
 * 生成された `plugin.yml`（resource-factory 出力）に対する回帰テスト。CI の `./gradlew test` で走る。
 *
 * 2 つの過去のインシデントを固定する:
 *  1. version が `project.version` に置換されず、固定のプレースホルダ文字列のまま出荷され
 *     `ar info` に生表示されていた（#version-fix）。
 *  2. `advancerailway.admin` が `default: true` かつ全ノードの親だったため、
 *     一般プレイヤーにも書き込み権限がカスケードしていた（#permission-fix）。
 *
 * 期待バージョンは build.gradle.kts が `advancerailway.projectVersion` システムプロパティで渡す。
 */
class PluginYmlTest {

    private fun loadPluginYml(): Map<String, Any> {
        val stream = javaClass.getResourceAsStream("/plugin.yml")
            ?: error("plugin.yml がテストクラスパスに見つかりません（processResources 未実行の可能性）")
        @Suppress("UNCHECKED_CAST")
        return stream.use { Yaml().load(it) as Map<String, Any> }
    }

    @Suppress("UNCHECKED_CAST")
    private fun permissions(): Map<String, Any> =
        loadPluginYml()["permissions"] as? Map<String, Any>
            ?: error("plugin.yml に permissions セクションがありません")

    /** ノードの default 値を小文字文字列で返す（未指定は "op"＝Bukkit の既定）。 */
    private fun defaultOf(node: String): String {
        val perms = permissions()
        assertNotNull(perms[node], "権限ノード $node が plugin.yml に存在しません")
        @Suppress("UNCHECKED_CAST")
        val entry = perms[node] as Map<String, Any>
        return (entry["default"]?.toString() ?: "op").lowercase()
    }

    @Test
    @DisplayName("version は project.version に置換され、プレースホルダのままではない")
    fun versionIsSubstituted() {
        val expected = System.getProperty("advancerailway.projectVersion")
        assertNotNull(expected, "システムプロパティ advancerailway.projectVersion が未設定です")
        val actual = loadPluginYml()["version"]?.toString()
        assertEquals(expected, actual, "plugin.yml の version が project.version と一致しません")
        assertNotEquals("miencraft_plugin_version", actual, "version がプレースホルダのまま出力されています")
    }

    @Test
    @DisplayName("admin は default=TRUE ではない（全員付与のカスケードを防ぐ）")
    fun adminIsNotDefaultTrue() {
        assertNotEquals("true", defaultOf("advancerailway.admin"))
    }

    @Test
    @DisplayName("編集・運用系の権限は全員に付与されない（default != true）")
    fun writeAndOpsNodesAreNotPublic() {
        val opOnly = listOf(
            "advancerailway.manage",
            "advancerailway.station.manage",
            "advancerailway.railway.manage",
            "advancerailway.group.manage",
            "advancerailway.file",
            "advancerailway.reload",
            "advancerailway.inspect",
            "advancerailway.debug",
        )
        opOnly.forEach { node ->
            assertNotEquals("true", defaultOf(node), "$node が全員に付与されています（default=true）")
        }
    }

    @Test
    @DisplayName("閲覧系（info/view/route）は全員に公開されている（default=true）")
    fun readNodesArePublic() {
        val public = listOf(
            "advancerailway.user",
            "advancerailway.info",
            "advancerailway.station.view",
            "advancerailway.railway.view",
            "advancerailway.railway.route",
            "advancerailway.group.view",
        )
        public.forEach { node ->
            assertEquals("true", defaultOf(node), "$node が全員に公開されていません（default!=true）")
        }
    }
}
