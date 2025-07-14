package org.example

/**
 * Implementations are loaded via [java.util.ServiceLoader] and are
 * responsible for handling specific image protocols.
 * The [protocols] list contains all protocols (without `://` suffix)
 * that this factory can create containers for.
 */
interface ProtocolContainerFactory {
    val protocols: List<String>

    fun create(protocol: String, route: RouteConfig): Container
}
