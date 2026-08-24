/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.domain.numbering

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 駅ナンバリングの組み立て（[StationNumbering]）。
 *
 * 番号は駅ではなくグループが持つ接頭辞・開始番号と、グループ内の並び順から決まる。
 */
class StationNumberingTest {

    @Test
    @DisplayName("numbers start at the group's start number and follow the position")
    fun countsFromStart() {
        assertEquals("JY01", StationNumbering.format("JY", 1, 0))
        assertEquals("JY02", StationNumbering.format("JY", 1, 1))
        assertEquals("JY10", StationNumbering.format("JY", 1, 9))
    }

    @Test
    @DisplayName("a non-1 start number offsets every station")
    fun honoursStartOffset() {
        assertEquals("JC10", StationNumbering.format("JC", 10, 0))
        assertEquals("JC12", StationNumbering.format("JC", 10, 2))
        assertEquals("JC00", StationNumbering.format("JC", 0, 0))
    }

    @Test
    @DisplayName("numbers are zero padded to two digits, and longer numbers are left as they are")
    fun padsToTwoDigits() {
        assertEquals("A05", StationNumbering.format("A", 5, 0))
        assertEquals("A99", StationNumbering.format("A", 99, 0))
        assertEquals("A100", StationNumbering.format("A", 100, 0))
    }

    @Test
    @DisplayName("a group without a prefix has no numbering at all")
    fun noPrefixMeansNoNumber() {
        assertNull(StationNumbering.format(null, 1, 0))
        assertNull(StationNumbering.format("", 1, 0))
        assertNull(StationNumbering.format("   ", 1, 0))
    }
}
