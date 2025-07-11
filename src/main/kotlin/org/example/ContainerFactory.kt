package org.example

fun interface ContainerFactory {
    fun create(route: RouteConfig): Container
}
