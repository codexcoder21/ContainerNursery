package org.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.net.Socket
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

enum class RouteType {
    HTTP,
    TCP,
    UDP;
}

data class RouteConfig(
    val domain: String,
    val image: String,
    val keepWarmSeconds: Long,
    val port: Int,
    val type: RouteType = RouteType.HTTP
)

data class Config(
    val routes: List<RouteConfig>
)

fun Application.module(
    config: Config,
    router: RequestRouter = ConfigFileRequestRouter(Config(config.routes.filter { it.type == RouteType.HTTP })),
    nursery: ContainerNursery? = null,
    clock: Clock = SystemClock(),
    containerFactory: ContainerFactory = org.example.docker.DockerContainerFactory(),
    httpClient: HttpClient? = null
) {
    val currentNursery = nursery ?: ContainerNursery(router, clock, containerFactory)

    config.routes.filter { it.type == RouteType.TCP }.forEach { route ->
        startTcpProxy(route, currentNursery)
    }
    config.routes.filter { it.type == RouteType.UDP }.forEach { route ->
        startUdpProxy(route, currentNursery)
    }

    routing {
        route("/{...}") {
            handle {
                val container = runBlocking { currentNursery.getOrCreate(call) }
                if (container != null) {
                    runBlocking { currentNursery.forwardRequest(container, call) }
                } else {
                    call.respondText(
                        "No route found for ${call.request.host()}",
                        status = io.ktor.http.HttpStatusCode.NotFound
                    )
                }
            }
        }
    }

    environment.monitor.subscribe(ApplicationStopping) {
        println("Application stopping, shutting down all active containers.")
        currentNursery.shutdown()
    }
}

private fun startTcpProxy(route: RouteConfig, nursery: ContainerNursery) {
    GlobalScope.launch {
        val server = ServerSocket(route.port)
        while (true) {
            val client = server.accept()
            launch {
                val container = nursery.getOrCreate(route)
                val backend = Socket("localhost", container.hostPort)
                val toBackend = launch { client.getInputStream().copyTo(backend.getOutputStream()) }
                val fromBackend = launch { backend.getInputStream().copyTo(client.getOutputStream()) }
                toBackend.join(); fromBackend.join()
                client.close(); backend.close()
            }
        }
    }
}

private fun startUdpProxy(route: RouteConfig, nursery: ContainerNursery) {
    GlobalScope.launch {
        val socket = DatagramSocket(route.port)
        val buf = ByteArray(65535)
        while (true) {
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            launch {
                val container = nursery.getOrCreate(route)
                val backendSocket = DatagramSocket()
                val backendAddr = InetSocketAddress("localhost", container.hostPort)
                val outPkt = DatagramPacket(packet.data, packet.length, backendAddr)
                backendSocket.send(outPkt)
                val respBuf = ByteArray(65535)
                val respPkt = DatagramPacket(respBuf, respBuf.size)
                backendSocket.receive(respPkt)
                val clientResp = DatagramPacket(respPkt.data, respPkt.length, packet.socketAddress)
                socket.send(clientResp)
                backendSocket.close()
            }
        }
    }
}

object ContainerNurseryServer {
    @JvmStatic
    fun main(args: Array<String>) {
        val configFilePath = args.firstOrNull() ?: "config.json"
        val config = configFromFile(configFilePath)
        embeddedServer(Netty, port = 8080) {
            module(config)
        }.start(wait = true)
    }
}
