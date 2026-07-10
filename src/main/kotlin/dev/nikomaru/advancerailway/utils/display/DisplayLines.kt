/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.utils.display

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.wrappers.WrappedParticle
import dev.nikomaru.advancerailway.Line3D
import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.utils.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DisplayLines: KoinComponent {
    private val protocolManager: ProtocolManager by inject()
    private val lines = arrayListOf<Line3D>()
    private val target = arrayListOf<Player>()
    private var color = Color.RED

    fun addDisplayObject(line: Line3D) {
        lines.add(line)
    }

    fun addTarget(player: Player) {
        target.add(player)
    }

    fun setColor(color: Color) {
        this.color = color
    }

    suspend fun display() {
        withContext(Dispatchers.async) {
            lines.forEach { line ->
                val size = line.points.size
                val perSecond = 2
                repeat(perSecond * 20) {
                    for (i in 0 until size - 1) {
                        val point1 = line.points[i]
                        val point2 = line.points[i + 1]
                        val step = 0.3
                        val distance = point1.distanceTo(point2)
                        target.forEach {
                            var current = point1
                            var count = 0
                            val x = (point2.x - point1.x) / distance * step
                            val y = (point2.y - point1.y) / distance * step
                            val z = (point2.z - point1.z) / distance * step
                            if (point1.x >= point2.x) {
                                while (current.x >= point2.x && current.y >= point2.y && current.z >= point2.z && count < 1000) {
                                    summonParticle(it, current.x, current.y, current.z)
                                    current = Point3D(current.x - x, current.y - y, current.z - z)
                                    count++
                                }
                            } else {
                                while (current.x <= point2.x && current.y <= point2.y && current.z <= point2.z && count < 1000) {
                                    summonParticle(it, current.x, current.y, current.z)
                                    current = Point3D(current.x + x, current.y + y, current.z + z)
                                    count++
                                }
                            }
                        }

                    }
                    delay(1000 / perSecond.toLong())
                }
            }
        }
    }


    private suspend fun summonParticle(player: Player, x: Double, y: Double, z: Double) = withContext(Dispatchers.IO) {
        val packet = protocolManager.createPacket(PacketType.Play.Server.WORLD_PARTICLES)
        packet.newParticles.write(
            0, WrappedParticle.create(
                Particle.DUST, Particle.DustOptions(color, 2.0F)
            )
        )
        packet.doubles.write(0, x).write(1, y).write(2, z)
        protocolManager.sendServerPacket(player, packet)
    }
}