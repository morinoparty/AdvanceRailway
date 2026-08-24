/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [IdValidation] と [Slug] のバリデーションを検証する（#116/#130）。
 *
 * かつてこの検証は「ID がファイル名になる」ことへの防御だったが、データベース化した現在も
 * slug はコマンド引数・HTTP のパス・MiniMessage に載るため、同じ allowlist を維持する。
 */
class IdValidationTest {

    @Test
    @DisplayName("isValid accepts ordinary alphanumeric ids")
    fun isValidAcceptsOrdinaryIds() {
        assertTrue(IdValidation.isValid("st01"))
        assertTrue(IdValidation.isValid("Central_Line-1"))
        assertTrue(IdValidation.isValid("a"))
    }

    @Test
    @DisplayName("isValid rejects path traversal segments")
    fun isValidRejectsPathTraversal() {
        assertFalse(IdValidation.isValid(".."))
        assertFalse(IdValidation.isValid("."))
        assertFalse(IdValidation.isValid("../../x"))
        assertFalse(IdValidation.isValid("..%2F..%2Fx"))
    }

    @Test
    @DisplayName("isValid rejects path separators")
    fun isValidRejectsPathSeparators() {
        assertFalse(IdValidation.isValid("a/b"))
        assertFalse(IdValidation.isValid("a\\b"))
        assertFalse(IdValidation.isValid("/etc/passwd"))
    }

    @Test
    @DisplayName("isValid rejects an empty string")
    fun isValidRejectsEmptyString() {
        assertFalse(IdValidation.isValid(""))
    }

    @Test
    @DisplayName("isValid rejects embedded newlines and spaces")
    fun isValidRejectsEmbeddedWhitespace() {
        assertFalse(IdValidation.isValid("st01\n"))
        assertFalse(IdValidation.isValid("st01 "))
    }

    @Test
    @DisplayName("Slug accepts a normal id")
    fun slugAcceptsNormalId() {
        assertEquals("st01", Slug("st01").value)
    }

    @Test
    @DisplayName("Slug rejects a URL-encoded traversal id")
    fun slugRejectsTraversalId() {
        assertThrows<IllegalArgumentException> { Slug("..%2F..%2Fx") }
    }

    @Test
    @DisplayName("Slug.parse returns null instead of throwing, for use at input boundaries")
    fun slugParseReturnsNull() {
        assertNull(Slug.parse("../../x"))
        assertNull(Slug.parse(".."))
        assertEquals("st01", Slug.parse("st01")?.value)
    }
}
