/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.utils

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * [content] を [target] へアトミックに書き込む。
 *
 * 一旦同じディレクトリ内の一時ファイルへ書き出してから
 * [Files.move] でアトミックに置き換えることで、書き込み途中で
 * クラッシュしても [target] が中途半端な内容で壊れないようにする。
 * ファイルシステムがアトミック移動に対応していない場合は
 * [StandardCopyOption.REPLACE_EXISTING] のみでフォールバックする。
 */
fun writeAtomically(target: File, content: String) {
    val parent = target.parentFile
    parent?.mkdirs()
    val tmp = File.createTempFile(target.name, ".tmp", parent)
    try {
        tmp.writeText(content)
        try {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        tmp.delete()
    }
}
