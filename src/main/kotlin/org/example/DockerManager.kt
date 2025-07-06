package org.example

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class DockerContainer(
    val id: String,
    val port: Int,
    val lastAccessed: Long = System.currentTimeMillis()
)

class DockerManager(private val config: Config) {

    private val dockerClient: DockerClient
    private val activeContainers = ConcurrentHashMap<String, DockerContainer>()
    private val containerAccessTimes = ConcurrentHashMap<String, Long>()
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        val dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost("unix:///var/run/docker.sock").build()
        val dockerHttpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(dockerClientConfig.dockerHost)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(30))
            .build()
        dockerClient = DockerClientImpl.getInstance(dockerClientConfig, dockerHttpClient)

        // Schedule the keep-warm check
        scheduledExecutor.scheduleAtFixedRate({
            val now = System.currentTimeMillis()
            activeContainers.forEach { (domain, container) ->
                val routeConfig = config.routes.find { it.domain == domain }
                if (routeConfig != null) {
                    val lastAccessed = containerAccessTimes.getOrDefault(domain, now)
                    if (now - lastAccessed > routeConfig.keepWarmSeconds * 1000) {
                        println("Shutting down container for ${domain} (id: ${container.id}) due to inactivity.")
                        runBlocking {
                            stopContainer(container.id)
                        }
                        activeContainers.remove(domain)
                        containerAccessTimes.remove(domain)
                    }
                }
            }
        }, 10, 10, TimeUnit.SECONDS) // Check every 10 seconds
    }

    suspend fun getOrCreateContainer(routeConfig: RouteConfig): DockerContainer {
        return activeContainers.computeIfAbsent(routeConfig.domain) {
            runBlocking {
                startContainer(routeConfig)
            }
        }.also {
            containerAccessTimes[routeConfig.domain] = System.currentTimeMillis()
        }
    }

    private suspend fun startContainer(routeConfig: RouteConfig): DockerContainer {
        println("Starting container for ${routeConfig.domain} using image ${routeConfig.image}")

        
        println("Attempting to start container for ${routeConfig.domain} using image ${routeConfig.image}")
        println("HostConfig runtime: gvisor, publishAllPorts: true")
        println("Environment variable: PORT=${routeConfig.port}")

        val container = dockerClient.createContainerCmd(routeConfig.image)
            .withHostConfig(HostConfig.newHostConfig().withPublishAllPorts(true))
            .withEnv("PORT=${routeConfig.port}")
            .exec()

        dockerClient.startContainerCmd(container.id).exec()

        val inspectContainerResponse = dockerClient.inspectContainerCmd(container.id).exec()
        println("Inspect Container Response: $inspectContainerResponse")
        val portBinding = inspectContainerResponse.networkSettings.ports.bindings.entries.firstOrNull {
            it.key.port == routeConfig.port
        }?.value?.firstOrNull()
        println("Port Binding: $portBinding")

        if (portBinding == null) {
            throw RuntimeException("Failed to get port binding for container ${container.id}")
        }

        val hostPort = portBinding.hostPortSpec.toInt()
        println("Container started with ID: ${container.id}, Host Port: ${hostPort}")

        waitUntilContainerReady("localhost", hostPort)

        return DockerContainer(container.id, hostPort)
    }

    private suspend fun waitUntilContainerReady(host: String, port: Int, timeoutSeconds: Long = 60) {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = timeoutSeconds * 1000
        val httpClient = HttpClient(CIO)
        try {
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                try {
                    val response = httpClient.get("http://$host:$port/")
                    if (response.status.value in 200..299) {
                        println("Container at $host:$port is ready.")
                        return
                    }
                } catch (e: Exception) {
                    // Container not ready yet, wait and retry
                }
                delay(2000) // Wait for 2000 milliseconds before retrying
            }
            throw RuntimeException("Container at $host:$port did not become ready within $timeoutSeconds seconds.")
        } finally {
            httpClient.close()
        }
    }

    fun stopContainer(containerId: String) {
        try {
            dockerClient.stopContainerCmd(containerId).exec()
            dockerClient.removeContainerCmd(containerId).exec()
            println("Container ${containerId} stopped and removed.")
        } catch (e: Exception) {
            System.err.println("Error stopping/removing container ${containerId}: ${e.message}")
        }
    }

    fun shutdown() {
        println("Shutting down DockerManager, stopping all active containers.")
        activeContainers.forEach { (_, container) ->
            stopContainer(container.id)
        }
        scheduledExecutor.shutdownNow()
        coroutineScope.cancel()
    }
}