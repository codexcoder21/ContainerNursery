package org.example

import io.ktor.server.application.ApplicationCall
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class UdpDummyBackedContainer : Container {
    override var hostPort: Int = 0
        private set
    private lateinit var socket: DatagramSocket
    private var running = true
    private lateinit var serverThread: Thread

    override suspend fun start() {
        socket = DatagramSocket(0)
        hostPort = socket.localPort
        serverThread = thread {
            val buf = ByteArray(65535)
            while (running) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val resp = DatagramPacket("udp".toByteArray(), 3, packet.socketAddress)
                    socket.send(resp)
                } catch (_: Exception) {
                }
            }
        }
    }

    override suspend fun handle(call: ApplicationCall) {}

    override fun shutdown() {
        running = false
        socket.close()
        serverThread.join()
    }

    override fun kill() { shutdown() }
}
