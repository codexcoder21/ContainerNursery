package org.example

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.encodedPath
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class RouteConfig(
    val domain: String,
    val image: String,
    val keepWarmSeconds: Long,
    val port: Int
)

data class Config(
    val routes: List<RouteConfig>
)

class DockerContainer(
    val id: String,
    val port: Int,
    val lastAccessed: Long = System.currentTimeMillis()
)

val dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost("unix:///var/run/docker.sock").build()
val dockerHttpClient = ApacheDockerHttpClient.Builder()
    .dockerHost(dockerClientConfig.dockerHost)
    .connectionTimeout(Duration.ofSeconds(30))
    .responseTimeout(Duration.ofSeconds(30))
    .build()
val dockerClient: DockerClient = DockerClientImpl.getInstance(dockerClientConfig, dockerHttpClient)
val httpClient = HttpClient(CIO)

val activeContainers = ConcurrentHashMap<String, DockerContainer>()
val containerAccessTimes = ConcurrentHashMap<String, Long>()
val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

fun Application.module() {
    val configStream = Application::class.java.classLoader.getResourceAsStream("config.json")
        ?: throw RuntimeException("config.json not found")
    val config: Config = Gson().fromJson(configStream.reader(), object : TypeToken<Config>() {}.type)

    routing {
        route("/{...}") {
            handle {
                val requestHost = call.request.host()
                val routeConfig = config.routes.find { it.domain == requestHost }

                if (routeConfig != null) {
                    val container = activeContainers.computeIfAbsent(routeConfig.domain) {
                        startContainer(routeConfig)
                    }
                    containerAccessTimes[routeConfig.domain] = System.currentTimeMillis()

                    val targetUrl = URLBuilder().apply {
                        protocol = URLProtocol.HTTP
                        this.host = "localhost"
                        port = container.port
                        encodedPath = call.request.path()
                        parameters.appendAll(call.request.queryParameters)
                    }

                    try {
                        val response = httpClient.request(targetUrl.buildString()) {
                            method = call.request.httpMethod
                            headers.appendAll(call.request.headers)
                            call.request.contentLength()?.let {
                                setBody(call.request.receiveChannel())
                            }
                        }
                        response.headers.forEach { name, values ->
                            values.forEach { call.response.headers.append(name, it) }
                        }
                        call.respondBytesWriter(status = response.status) {
                            response.bodyAsChannel().copyTo(this)
                        }
                    } catch (e: Exception) {
                        call.respondText("Error proxying request: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                        e.printStackTrace()
                    }
                } else {
                    call.respondText("No route found for ${requestHost}", status = io.ktor.http.HttpStatusCode.NotFound)
                }
            }
        }
    }

    // Schedule the keep-warm check
    scheduledExecutor.scheduleAtFixedRate({
        val now = System.currentTimeMillis()
        activeContainers.forEach { (domain, container) ->
            val routeConfig = config.routes.find { it.domain == domain }
            if (routeConfig != null) {
                val lastAccessed = containerAccessTimes.getOrDefault(domain, now)
                if (now - lastAccessed > routeConfig.keepWarmSeconds * 1000) {
                    println("Shutting down container for ${domain} (id: ${container.id}) due to inactivity.")
                    stopContainer(container.id)
                    activeContainers.remove(domain)
                    containerAccessTimes.remove(domain)
                }
            }
        }
    }, 10, 10, TimeUnit.SECONDS) // Check every 10 seconds

    environment.monitor.subscribe(ApplicationStopping) {
        println("Application stopping, shutting down all active containers.")
        activeContainers.forEach { (_, container) ->
            stopContainer(container.id)
        }
        scheduledExecutor.shutdown()
    }
}

fun startContainer(routeConfig: RouteConfig): DockerContainer {
    println("Starting container for ${routeConfig.domain} using image ${routeConfig.image}")

    val container = dockerClient.createContainerCmd(routeConfig.image)
        .withHostConfig(HostConfig.newHostConfig().withRuntime("gvisor").withPublishAllPorts(true))
        .withEnv("PORT=${routeConfig.port}")
        .exec()

    dockerClient.startContainerCmd(container.id).exec()

    val inspectContainerResponse = dockerClient.inspectContainerCmd(container.id).exec()
    val portBinding = inspectContainerResponse.networkSettings.ports.bindings.entries.firstOrNull {
        it.key.port == routeConfig.port
    }?.value?.firstOrNull()

    if (portBinding == null) {
        throw RuntimeException("Failed to get port binding for container ${container.id}")
    }

    val hostPort = portBinding.hostPortSpec.toInt()
    println("Container started with ID: ${container.id}, Host Port: ${hostPort}")

    return DockerContainer(container.id, hostPort)
}

fun stopContainer(containerId: String) {
    dockerClient.stopContainerCmd(containerId).exec()
    dockerClient.removeContainerCmd(containerId).exec()
    println("Container ${containerId} stopped and removed.")
}

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}
