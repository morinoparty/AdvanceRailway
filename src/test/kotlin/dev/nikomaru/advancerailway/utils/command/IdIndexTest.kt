/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.utils.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * [IdIndex] の純粋な名前解決ロジックのテスト。実サーバーの駅データ形式（JSON）を一時フォルダへ書いて検証する。
 */
class IdIndexTest {

    private fun writeStation(dir: File, id: String, name: String?) {
        val nameJson = if (name == null) "null" else "\"$name\""
        File(dir, "$id.json").writeText(
            """{"stationId":"$id","name":$nameJson,"world":"world","point":"0.0,64.0,0.0","color":"1,2,3"}"""
        )
    }

    @Test
    @DisplayName("suggestions offer display names, not ids, when a name field is present")
    fun suggestsNames(@TempDir dir: File) {
        writeStation(dir, "fti", "ふれんちとーす島")
        writeStation(dir, "akmt", "赤松")
        val entries = IdIndex.read(dir, "name")

        assertEquals(setOf("ふれんちとーす島", "赤松"), IdIndex.suggestions(entries))
    }

    @Test
    @DisplayName("resolveId maps a display name back to its id")
    fun resolvesNameToId(@TempDir dir: File) {
        writeStation(dir, "fti", "ふれんちとーす島")
        writeStation(dir, "akmt", "赤松")
        val entries = IdIndex.read(dir, "name")

        assertEquals("fti", IdIndex.resolveId(entries, "ふれんちとーす島"))
        assertEquals("akmt", IdIndex.resolveId(entries, "赤松"))
    }

    @Test
    @DisplayName("resolveId still accepts a raw id (power users / clicks)")
    fun resolvesRawId(@TempDir dir: File) {
        writeStation(dir, "fti", "ふれんちとーす島")
        val entries = IdIndex.read(dir, "name")

        assertEquals("fti", IdIndex.resolveId(entries, "fti"))
    }

    @Test
    @DisplayName("resolveId returns the token unchanged when it matches no name (invalid input surfaces downstream)")
    fun unknownTokenPassesThrough(@TempDir dir: File) {
        writeStation(dir, "fti", "ふれんちとーす島")
        val entries = IdIndex.read(dir, "name")

        assertEquals("does-not-exist", IdIndex.resolveId(entries, "does-not-exist"))
    }

    @Test
    @DisplayName("a null / blank name falls back to the id for suggestions")
    fun blankNameFallsBackToId(@TempDir dir: File) {
        writeStation(dir, "fti", "ふれんちとーす島")
        writeStation(dir, "noname", null)
        val entries = IdIndex.read(dir, "name")

        assertEquals(setOf("ふれんちとーす島", "noname"), IdIndex.suggestions(entries))
        assertEquals("noname", IdIndex.resolveId(entries, "noname")) // id still works
    }

    @Test
    @DisplayName("with no name field (e.g. railways) suggestions are ids only")
    fun noNameFieldUsesIds(@TempDir dir: File) {
        File(dir, "atmk_htat.json").writeText("""{"id":"atmk_htat","group":"morimotoWest","timeRequired":85}""")
        File(dir, "cfp_MRK.json").writeText("""{"id":"cfp_MRK","group":"pakapaka","timeRequired":625}""")
        val entries = IdIndex.read(dir, null)

        assertEquals(setOf("atmk_htat", "cfp_MRK"), IdIndex.suggestions(entries))
        assertEquals("atmk_htat", IdIndex.resolveId(entries, "atmk_htat"))
    }

    @Test
    @DisplayName("the folder signature changes when a file's content changes (cache invalidation)")
    fun signatureChangesOnEdit(@TempDir dir: File) {
        writeStation(dir, "fti", "ふれんちとーす島")
        val before = IdIndex.signature(dir)
        // rewrite with a later mtime + different name
        File(dir, "fti.json").setLastModified(System.currentTimeMillis() + 2000)
        val after = IdIndex.signature(dir)

        assertNotEquals(before, after)
    }
}
