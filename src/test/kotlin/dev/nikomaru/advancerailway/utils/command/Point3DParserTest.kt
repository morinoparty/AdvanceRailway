/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.utils.command

import dev.nikomaru.advancerailway.Point3D
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import revxrsal.commands.command.CommandParameter
import revxrsal.commands.exception.InvalidNumberException
import revxrsal.commands.process.ValueResolver

/**
 * [Point3DParser.resolve] が壊れた入力（引数の数が違う・数値でない）に対して
 * クラッシュせず [InvalidNumberException] を送出することを確認する（#134 の回帰防止）。
 */
class Point3DParserTest {

    private fun contextFor(input: String): ValueResolver.ValueResolverContext {
        val context = mockk<ValueResolver.ValueResolverContext>()
        val parameter = mockk<CommandParameter>(relaxed = true)
        every { context.pop() } returns input
        every { context.parameter() } returns parameter
        return context
    }

    @Test
    @DisplayName("resolve parses a well formed comma separated point")
    fun resolveParsesWellFormedPoint() {
        val point = Point3DParser.resolve(contextFor("1.5,2,-3.25"))
        assertEquals(Point3D(1.5, 2.0, -3.25), point)
    }

    @Test
    @DisplayName("resolve throws InvalidNumberException when too few components are given")
    fun resolveThrowsOnTooFewComponents() {
        assertThrows<InvalidNumberException> {
            Point3DParser.resolve(contextFor("1,2"))
        }
    }

    @Test
    @DisplayName("resolve throws InvalidNumberException when too many components are given")
    fun resolveThrowsOnTooManyComponents() {
        assertThrows<InvalidNumberException> {
            Point3DParser.resolve(contextFor("1,2,3,4"))
        }
    }

    @Test
    @DisplayName("resolve throws InvalidNumberException on non numeric components")
    fun resolveThrowsOnNonNumericComponents() {
        assertThrows<InvalidNumberException> {
            Point3DParser.resolve(contextFor("a,b,c"))
        }
    }

    @Test
    @DisplayName("resolve throws InvalidNumberException on blank input")
    fun resolveThrowsOnBlankInput() {
        assertThrows<InvalidNumberException> {
            Point3DParser.resolve(contextFor(""))
        }
    }
}
