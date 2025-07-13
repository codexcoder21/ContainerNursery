package org.example.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.HostConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.encodedPath
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.delay
import org.example.Container
import org.example.RouteType

class DockerBackedContainer(
    private val image: String,
    private val internalPort: Int,
    private val type: RouteType,
    private val dockerClient: DockerClient
) : Container {
    private var containerId: String? = null
    private var _hostPort: Int = -1
    override val hostPort: Int
        get() = _hostPort

    override suspend fun start() {
        if (containerId != null) return
        val container = dockerClient.createContainerCmd(image)
            .withHostConfig(HostConfig.newHostConfig().withPublishAllPorts(true))
            .withEnv("PORT=$internalPort")
            .exec()
        dockerClient.startContainerCmd(container.id).exec()
        val inspect = dockerClient.inspectContainerCmd(container.id).exec()
        val portBinding = inspect.networkSettings.ports.bindings.entries.firstOrNull { it.key.port == internalPort }?.value?.firstOrNull()
            ?: throw RuntimeException("Failed to get port binding for container ${container.id}")
        _hostPort = portBinding.hostPortSpec.toInt()
        if (type == RouteType.HTTP) {
            waitUntilReady("localhost", _hostPort)
        }
        containerId = container.id
    }

    override suspend fun handle(call: ApplicationCall) {
        start()
        val httpClient = HttpClient(CIO)
        try {
            val targetUrl = URLBuilder().apply {
                protocol = URLProtocol.HTTP
                host = "localhost"
                port = _hostPort
                encodedPath = call.request.uri.substringBefore("?")
                parameters.appendAll(call.request.queryParameters)
            }
            val response = httpClient.request(targetUrl.buildString()) {
                method = call.request.httpMethod
                headers.appendAll(call.request.headers)
                call.request.contentLength()?.let { setBody(call.request.receiveChannel()) }
            }
            response.headers.forEach { name, values ->
                values.forEach { call.response.headers.append(name, it) }
            }
            call.respondBytesWriter(status = response.status) {
                response.bodyAsChannel().copyTo(this)
            }
        } finally {
            httpClient.close()
        }
    }

    override fun shutdown() {
        containerId?.let { id ->
            try {
                dockerClient.stopContainerCmd(id).exec()
                dockerClient.removeContainerCmd(id).exec()
                println("Container $id stopped and removed.")
            } catch (e: Exception) {
                System.err.println("Error stopping/removing container $id: ${e.message}")
            }
        }
    }

    override fun kill() {
        containerId?.let { id ->
            try {
                dockerClient.killContainerCmd(id).exec()
                dockerClient.removeContainerCmd(id).exec()
                println("Container $id force killed and removed.")
            } catch (e: Exception) {
                System.err.println("Error killing/removing container $id: ${e.message}")
            }
        }
    }

    private suspend fun waitUntilReady(host: String, port: Int, timeoutSeconds: Long = 60) {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = timeoutSeconds * 1000
        val client = HttpClient(CIO)
        try {
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                try {
                    val response = client.get("http://$host:$port/")
                    if (response.status.value in 200..299) return
                } catch (_: Exception) {
                }
                delay(2000)
            }
            throw RuntimeException("Container at $host:$port did not become ready within $timeoutSeconds seconds.")
        } finally {
            client.close()
        }
    }
}
