package org.example

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
import kotlinx.coroutines.runBlocking
import java.io.File

data class RouteConfig(
    val domain: String,
    val image: String,
    val keepWarmSeconds: Long,
    val port: Int
)

data class Config(
    val routes: List<RouteConfig>
)



fun Application.module(dockerManager: DockerManager? = null, httpClient: HttpClient? = null) {
    val configStream = Application::class.java.classLoader.getResourceAsStream("config.json")
        ?: throw RuntimeException("config.json not found")
    val config: Config = Gson().fromJson(configStream.reader(), object : TypeToken<Config>() {}.type)

    val currentDockerManager = dockerManager ?: DockerManager(config)

    routing {
        route("/{...}") {
            handle {
                val requestHost = call.request.host()
                val routeConfig = config.routes.find { it.domain == requestHost }

                if (routeConfig != null) {
                    val container = runBlocking {
                        currentDockerManager.getOrCreateContainer(routeConfig)
                    }

                    val targetUrl = URLBuilder().apply {
                        protocol = URLProtocol.HTTP
                        this.host = "localhost"
                        port = container.port
                        encodedPath = call.request.uri.substringBefore("?")
                        parameters.appendAll(call.request.queryParameters)
                    }
                    println("Proxying to: ${targetUrl.buildString()}")

                    val currentHttpClient = httpClient ?: HttpClient(CIO)
                    try {
                        val response = currentHttpClient.request(targetUrl.buildString()) {
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

    environment.monitor.subscribe(ApplicationStopping) {
        println("Application stopping, shutting down all active containers.")
        runBlocking {
            currentDockerManager.shutdown()
        }
    }
}

fun main() {
    embeddedServer(Netty, port = 8080) { module(httpClient = HttpClient(CIO)) }.start(wait = true)
}
