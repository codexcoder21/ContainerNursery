package org.example.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import org.example.Container
import org.example.ContainerFactory
import org.example.RouteConfig
import java.time.Duration

class DockerContainerFactory : ContainerFactory {
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

    override fun create(route: RouteConfig): Container {
        return DockerBackedContainer(route.image, route.port, dockerClient)
    }
}
