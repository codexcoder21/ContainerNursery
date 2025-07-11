package org.example

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

class ContainerNursery(
    private val router: RequestRouter,
    private val clock: Clock = SystemClock(),
    private val containerFactory: ContainerFactory
) {
    companion object {
        private const val START_TIMEOUT_MS = 60_000L
        private const val REQUEST_TIMEOUT_MS = 5 * 60_000L
        private const val SHUTDOWN_TIMEOUT_MS = 60_000L
    }

    private val activeContainers = ConcurrentHashMap<String, Container>()
    private val containerAccessTimes = ConcurrentHashMap<String, Long>()
    private val routeConfigs = ConcurrentHashMap<String, RouteConfig>()
    private var checkTask: Scheduled? = null

    init {
        scheduleCheck()
    }

    private fun scheduleCheck() {
        checkTask = clock.schedule(clock.now() + 10_000) {
            checkInactive()
            scheduleCheck()
        }
    }

    private fun runWithTimeout(timeout: Long, container: Container, block: suspend () -> Unit) {
        val thread = Thread {
            runBlocking { block() }
        }
        val scheduled = clock.schedule(clock.now() + timeout) {
            println("Operation timed out, killing container.")
            container.kill()
            thread.interrupt()
        }
        thread.start()
        thread.join()
        clock.unschedule(scheduled)
    }

    private fun shutdownContainer(domain: String, container: Container) {
        runWithTimeout(SHUTDOWN_TIMEOUT_MS, container) { container.shutdown() }
        activeContainers.remove(domain)
        containerAccessTimes.remove(domain)
        routeConfigs.remove(domain)
    }

    private fun checkInactive() {
        val now = clock.now()
        activeContainers.forEach { (domain, container) ->
            val route = routeConfigs[domain] ?: return@forEach
            val last = containerAccessTimes.getOrDefault(domain, now)
            if (now - last > route.keepWarmSeconds * 1000) {
                println("Shutting down container for $domain due to inactivity.")
                shutdownContainer(domain, container)
            }
        }
    }

    internal suspend fun getOrCreate(call: io.ktor.server.application.ApplicationCall): Container? {
        val route = router.route(call) ?: return null
        routeConfigs.putIfAbsent(route.domain, route)
        val container = activeContainers.computeIfAbsent(route.domain) {
            containerFactory.create(route)
        }
        containerAccessTimes[route.domain] = clock.now()
        runWithTimeout(START_TIMEOUT_MS, container) { container.start() }
        return container
    }

    suspend fun forwardRequest(container: Container, call: io.ktor.server.application.ApplicationCall) {
        runWithTimeout(REQUEST_TIMEOUT_MS, container) { container.handle(call) }
    }

    fun shutdown() {
        println("Shutting down ContainerNursery, stopping all active containers.")
        activeContainers.forEach { (domain, container) ->
            shutdownContainer(domain, container)
        }
        checkTask?.cancel()
    }
}
