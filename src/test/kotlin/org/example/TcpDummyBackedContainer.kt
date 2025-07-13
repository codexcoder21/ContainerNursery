package org.example

import io.ktor.server.application.ApplicationCall
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class TcpDummyBackedContainer : Container {
    override var hostPort: Int = 0
        private set
    private lateinit var server: ServerSocket
    private var running = true
    private lateinit var serverThread: Thread

    override suspend fun start() {
        server = ServerSocket(0)
        hostPort = server.localPort
        serverThread = thread {
            while (running) {
                try {
                    val client: Socket = server.accept()
                    client.getOutputStream().use { it.write("tcp".toByteArray()) }
                    client.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    override suspend fun handle(call: ApplicationCall) {}

    override fun shutdown() {
        running = false
        server.close()
        serverThread.join()
    }

    override fun kill() { shutdown() }
}
