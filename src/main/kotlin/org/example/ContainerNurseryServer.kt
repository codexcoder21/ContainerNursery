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

data class RouteConfig(
    val domain: String,
    val image: String,
    val keepWarmSeconds: Long,
    val port: Int
)

data class Config(
    val routes: List<RouteConfig>
)

fun Application.module(
    router: RequestRouter,
    nursery: ContainerNursery? = null,
    clock: Clock = SystemClock(),
    containerFactory: ContainerFactory = org.example.docker.DockerContainerFactory(),
    httpClient: HttpClient? = null
) {
    val currentNursery = nursery ?: ContainerNursery(router, clock, containerFactory)

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

object ContainerNurseryServer {
    @JvmStatic
    fun main(args: Array<String>) {
        val configFilePath = args.firstOrNull() ?: "config.json"
        val router = requestRouterFromFile(configFilePath)
        embeddedServer(Netty, port = 8080) {
            module(router)
        }.start(wait = true)
    }
}
