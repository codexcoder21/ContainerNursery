package org.example

import kotlin.test.Test
import kotlin.test.assertEquals
import java.net.Socket
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.InetSocketAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import org.example.RouteType

class NetworkProxyTest {
    @Test
    fun tcpRouteForwards() {
        val route = RouteConfig("tcp.test", "dummy://dummy", 30, 9011, RouteType.TCP)
        val container = TcpDummyBackedContainer()
        val config = Config(listOf(route))
        val nursery = ContainerNursery(object : RequestRouter {
            override suspend fun route(call: io.ktor.server.application.ApplicationCall) = null
        }, containerFactory = TcpUdpContainerFactory { container })
        val job = startTcpProxy(route, nursery)
        runBlocking { delay(500) }
        var result: String? = null
        repeat(5) {
            try {
                Socket("localhost", route.port).use { sock ->
                    val buffer = ByteArray(3)
                    sock.getInputStream().read(buffer)
                    result = String(buffer)
                }
                return@repeat
            } catch (_: Exception) {
                runBlocking { delay(200) }
            }
        }
        assertEquals("tcp", result)
        nursery.shutdown()
        runBlocking { job.cancelAndJoin() }
    }

    @Test
    fun udpRouteForwards() {
        val route = RouteConfig("udp.test", "dummy://dummy", 30, 9012, RouteType.UDP)
        val container = UdpDummyBackedContainer()
        val config = Config(listOf(route))
        val nursery = ContainerNursery(object : RequestRouter {
            override suspend fun route(call: io.ktor.server.application.ApplicationCall) = null
        }, containerFactory = TcpUdpContainerFactory { container })
        val job = startUdpProxy(route, nursery)
        runBlocking { delay(500) }
        val socket = DatagramSocket()
        val send = DatagramPacket("hi".toByteArray(), 2, java.net.InetAddress.getByName("localhost"), route.port)
        var result: String? = null
        repeat(5) {
            try {
                socket.send(send)
                val resp = DatagramPacket(ByteArray(3), 3)
                socket.receive(resp)
                result = String(resp.data, 0, resp.length)
                return@repeat
            } catch (_: Exception) {
                runBlocking { delay(200) }
            }
        }
        socket.close()
        assertEquals("udp", result)
        nursery.shutdown()
        runBlocking { job.cancelAndJoin() }
    }
}

private fun startTcpProxy(route: RouteConfig, nursery: ContainerNursery): Job = GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
    ServerSocket(route.port).use { server ->
        val client = server.accept()
        val container = nursery.getOrCreate(route)
        Socket("localhost", container.hostPort).use { backend ->
            val toBackend = launch { client.getInputStream().copyTo(backend.getOutputStream()) }
            val fromBackend = launch { backend.getInputStream().copyTo(client.getOutputStream()) }
            toBackend.join(); fromBackend.join()
        }
        client.close()
    }
}

private fun startUdpProxy(route: RouteConfig, nursery: ContainerNursery): Job = GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
    DatagramSocket(route.port).use { socket ->
        val buf = ByteArray(65535)
        val packet = DatagramPacket(buf, buf.size)
        socket.receive(packet)
        val container = nursery.getOrCreate(route)
        DatagramSocket().use { backendSocket ->
            val backendAddr = InetSocketAddress("localhost", container.hostPort)
            val outPkt = DatagramPacket(packet.data, packet.length, backendAddr)
            backendSocket.send(outPkt)
            val respBuf = ByteArray(65535)
            val respPkt = DatagramPacket(respBuf, respBuf.size)
            backendSocket.receive(respPkt)
            val clientResp = DatagramPacket(respPkt.data, respPkt.length, packet.socketAddress)
            socket.send(clientResp)
        }
    }
}

private class TcpUdpContainerFactory(private val supplier: () -> Container) : ContainerFactory {
    override fun create(route: RouteConfig): Container = supplier()
}
