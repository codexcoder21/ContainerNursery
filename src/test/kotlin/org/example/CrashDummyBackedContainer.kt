package org.example

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText

class CrashDummyBackedContainer(
    private val startForever: Boolean = false,
    private val handleForever: Boolean = false,
    private val shutdownForever: Boolean = false,
    private val id: String = "dummy"
) : Container {
    var killed = 0
    var started = 0
    var handled = 0
    var shutdowns = 0
    override val hostPort: Int = 0

    override suspend fun start() {
        started++
        if (startForever) while (true) { Thread.sleep(1000) }
    }

    override suspend fun handle(call: ApplicationCall) {
        handled++
        if (handleForever) while (true) { Thread.sleep(1000) } else {
            call.respondText(id)
        }
    }

    override fun shutdown() {
        shutdowns++
        if (shutdownForever) while (true) { Thread.sleep(1000) }
    }

    override fun kill() {
        killed++
    }
}

class CrashDummyContainerFactory(private val supplier: () -> CrashDummyBackedContainer) : ContainerFactory {
    override fun create(route: RouteConfig): Container = supplier()
}
