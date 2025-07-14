package org.example.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import org.example.Container
import org.example.ProtocolContainerFactory
import org.example.RouteConfig
import java.time.Duration
import java.io.File
import java.io.FileInputStream

class DockerContainerFactory : ProtocolContainerFactory {
    override val protocols = listOf("dockerlocal", "dockerremote", "dockerfile")

    private val dockerClient: DockerClient

    init {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost("unix:///var/run/docker.sock").build()
        val http = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(30))
            .build()
        dockerClient = DockerClientImpl.getInstance(config, http)
    }

    override fun create(protocol: String, route: RouteConfig): Container {
        when (protocol) {
            "dockerremote" -> {
                try {
                    dockerClient.pullImageCmd(route.image).start().awaitCompletion()
                } catch (_: Exception) {}
            }
            "dockerfile" -> {
                val file = File(route.image)
                if (file.exists()) {
                    FileInputStream(file).use { stream ->
                        dockerClient.loadImageCmd(stream).exec()
                    }
                }
            }
        }
        return DockerBackedContainer(route.image, 8080, route.type, dockerClient)
    }
}
