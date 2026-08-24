/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * [IdIndex] の名前解決ロジックのテスト。
 *
 * 主キーが UUID になり slug が別に付いたため、コマンド引数は
 * 「表示名 → slug → UUID」の順で解決する。その優先順位と、
 * どれにも一致しない入力が null になること（コマンド側でエラーを出せること）を固定する。
 */
class IdIndexTest {

    private val fti = IdEntry(UUID.fromString("01890000-0000-7000-8000-000000000001"), "fti", "ふれんちとーす島")
    private val akmt = IdEntry(UUID.fromString("01890000-0000-7000-8000-000000000002"), "akmt", "赤松")
    private val noname = IdEntry(UUID.fromString("01890000-0000-7000-8000-000000000003"), "noname", null)

    @Test
    @DisplayName("suggestions offer display names, not slugs, when a name is present")
    fun suggestsNames() {
        assertEquals(setOf("ふれんちとーす島", "赤松"), IdIndex.suggestions(listOf(fti, akmt)))
    }

    @Test
    @DisplayName("resolve maps a display name back to its id")
    fun resolvesNameToId() {
        assertEquals(fti.id, IdIndex.resolve(listOf(fti, akmt), "ふれんちとーす島"))
        assertEquals(akmt.id, IdIndex.resolve(listOf(fti, akmt), "赤松"))
    }

    @Test
    @DisplayName("resolve still accepts a raw slug (power users / clicked links)")
    fun resolvesSlug() {
        assertEquals(fti.id, IdIndex.resolve(listOf(fti), "fti"))
    }

    @Test
    @DisplayName("resolve accepts the UUID itself")
    fun resolvesUuid() {
        assertEquals(fti.id, IdIndex.resolve(listOf(fti), fti.id.toString()))
    }

    @Test
    @DisplayName("resolve returns null for an unknown token so the command can report it")
    fun unknownTokenIsNull() {
        assertNull(IdIndex.resolve(listOf(fti), "does-not-exist"))
    }

    @Test
    @DisplayName("an entry without a name falls back to its slug for suggestions and still resolves")
    fun blankNameFallsBackToSlug() {
        val entries = listOf(fti, noname)
        assertEquals(setOf("ふれんちとーす島", "noname"), IdIndex.suggestions(entries))
        assertEquals(noname.id, IdIndex.resolve(entries, "noname"))
    }

    @Test
    @DisplayName("railways have no display name, so suggestions are slugs only")
    fun noNameUsesSlugs() {
        val entries = listOf(
            IdEntry(UUID.fromString("01890000-0000-7000-8000-00000000000a"), "atmk_htat", null),
            IdEntry(UUID.fromString("01890000-0000-7000-8000-00000000000b"), "cfp_MRK", null),
        )
        assertEquals(setOf("atmk_htat", "cfp_MRK"), IdIndex.suggestions(entries))
        assertEquals(entries[0].id, IdIndex.resolve(entries, "atmk_htat"))
    }
}
