package org.example

import java.util.ServiceLoader

/**
 * [ContainerFactory] implementation that delegates creation of containers to
 * protocol specific factories discovered via SPI.
 */
class ServiceLoaderContainerFactory : ContainerFactory {
    private val factories: Map<String, ProtocolContainerFactory>

    init {
        val loader = ServiceLoader.load(ProtocolContainerFactory::class.java)
        val map = mutableMapOf<String, ProtocolContainerFactory>()
        loader.forEach { factory ->
            factory.protocols.forEach { proto ->
                map[proto] = factory
            }
        }
        factories = map
    }

    override fun create(route: RouteConfig): Container {
        val idx = route.image.indexOf("://")
        require(idx > 0) { "Image must be prefixed with protocol://" }
        val protocol = route.image.substring(0, idx)
        val img = route.image.substring(idx + 3)
        val factory = factories[protocol]
            ?: throw IllegalArgumentException("No factory for protocol $protocol")
        val stripped = route.copy(image = img)
        return factory.create(protocol, stripped)
    }
}
