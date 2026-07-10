/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [IdValidation] と、それを利用する各 ID 値クラスのバリデーションを検証する（#116/#130）。
 * パストラバーサル（`..`, `/`, `\`）やその URL エンコード表現を確実に弾くことを確認する。
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
    @DisplayName("StationId constructor accepts a normal id")
    fun stationIdAcceptsNormalId() {
        assertEquals("st01", StationId("st01").value)
    }

    @Test
    @DisplayName("StationId constructor rejects a URL-encoded traversal id")
    fun stationIdRejectsTraversalId() {
        assertThrows<IllegalArgumentException> { StationId("..%2F..%2Fx") }
    }

    @Test
    @DisplayName("RailwayId constructor rejects a relative traversal id")
    fun railwayIdRejectsTraversalId() {
        assertThrows<IllegalArgumentException> { RailwayId("../../x") }
    }

    @Test
    @DisplayName("GroupId constructor rejects a bare traversal id")
    fun groupIdRejectsTraversalId() {
        assertThrows<IllegalArgumentException> { GroupId("..") }
    }
}
