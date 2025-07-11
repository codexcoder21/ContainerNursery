package org.example

import java.util.concurrent.ConcurrentHashMap

class ContainerNursery(
    private val router: RequestRouter,
    private val clock: Clock = SystemClock(),
    private val containerFactory: ContainerFactory
) {
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

    private fun checkInactive() {
        val now = clock.now()
        activeContainers.forEach { (domain, container) ->
            val route = routeConfigs[domain] ?: return@forEach
            val last = containerAccessTimes.getOrDefault(domain, now)
            if (now - last > route.keepWarmSeconds * 1000) {
                println("Shutting down container for $domain due to inactivity.")
                container.shutdown()
                activeContainers.remove(domain)
                containerAccessTimes.remove(domain)
                routeConfigs.remove(domain)
            }
        }
    }

    suspend fun getOrCreate(call: io.ktor.server.application.ApplicationCall): Container? {
        val route = router.route(call) ?: return null
        routeConfigs.putIfAbsent(route.domain, route)
        val container = activeContainers.computeIfAbsent(route.domain) {
            containerFactory.create(route)
        }
        containerAccessTimes[route.domain] = clock.now()
        container.start()
        return container
    }

    fun shutdown() {
        println("Shutting down ContainerNursery, stopping all active containers.")
        activeContainers.values.forEach { it.shutdown() }
        checkTask?.cancel()
    }
}
