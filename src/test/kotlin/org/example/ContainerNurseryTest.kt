package org.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.example.RouteType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContainerNurseryTest {
    private class StaticRouter(private val config: RouteConfig) : RequestRouter {
        override suspend fun route(call: io.ktor.server.application.ApplicationCall): RouteConfig? = config
    }

    private val route = RouteConfig("test.com", "dummy://dummy", 300, 8080, RouteType.HTTP)

    @Test
    fun helloContainerResponds() = testApplication {
        val clock = SystemClock()
        val container = HelloDummyBackedContainer(clock)
        val nursery = ContainerNursery(StaticRouter(route), clock) { container }
        application { module(Config(listOf(route)), StaticRouter(route), nursery, clock, { container }) }
        val response = client.get("/") { header(HttpHeaders.Host, route.domain) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Hello World"))
        nursery.shutdown()
    }

    @Test
    fun startHangIsKilled() = testApplication {
        val clock = ManualClock()
        val container = CrashDummyBackedContainer(startForever = true)
        val nursery = ContainerNursery(StaticRouter(route), clock, CrashDummyContainerFactory { container })
        application { module(Config(listOf(route)), StaticRouter(route), nursery, clock, CrashDummyContainerFactory { container }) }

        val deferred = GlobalScope.async { client.get("/") { header(HttpHeaders.Host, route.domain) } }
        clock.advanceBy(60_000)
        deferred.await()
        assertTrue(container.killed > 0)
        nursery.shutdown()
    }

    @Test
    fun requestHangIsKilled() = testApplication {
        val clock = ManualClock()
        val container = CrashDummyBackedContainer(handleForever = true)
        val nursery = ContainerNursery(StaticRouter(route), clock, CrashDummyContainerFactory { container })
        application { module(Config(listOf(route)), StaticRouter(route), nursery, clock, CrashDummyContainerFactory { container }) }

        val deferred = GlobalScope.async { client.get("/") { header(HttpHeaders.Host, route.domain) } }
        clock.advanceBy(300_000)
        deferred.await()
        assertTrue(container.killed > 0)
        nursery.shutdown()
    }

    @Test
    fun shutdownTimeoutKillsContainer() = testApplication {
        val clock = ManualClock()
        val container = CrashDummyBackedContainer(shutdownForever = true)
        val nursery = ContainerNursery(StaticRouter(route), clock, CrashDummyContainerFactory { container })
        application { module(Config(listOf(route)), StaticRouter(route), nursery, clock, CrashDummyContainerFactory { container }) }

        client.get("/") { header(HttpHeaders.Host, route.domain) }
        nursery.shutdown()
        clock.advanceBy(60_000)
        assertTrue(container.killed > 0)
    }

    @Test
    fun idleContainerIsShutdown() = testApplication {
        val clock = ManualClock()
        val container = CrashDummyBackedContainer()
        val nursery = ContainerNursery(StaticRouter(route.copy(keepWarmSeconds = 300)), clock, CrashDummyContainerFactory { container })
        application { module(Config(listOf(route.copy(keepWarmSeconds = 300))), StaticRouter(route.copy(keepWarmSeconds = 300)), nursery, clock, CrashDummyContainerFactory { container }) }

        client.get("/") { header(HttpHeaders.Host, route.domain) }
        clock.advanceBy(300_000)
        clock.advanceBy(10_000)
        assertTrue(container.shutdowns > 0)
        nursery.shutdown()
    }

    @Test
    fun repeatedRequestsPreventShutdown() = testApplication {
        val clock = ManualClock()
        val container = CrashDummyBackedContainer()
        val nursery = ContainerNursery(StaticRouter(route.copy(keepWarmSeconds = 300)), clock, CrashDummyContainerFactory { container })
        application { module(Config(listOf(route.copy(keepWarmSeconds = 300))), StaticRouter(route.copy(keepWarmSeconds = 300)), nursery, clock, CrashDummyContainerFactory { container }) }

        client.get("/") { header(HttpHeaders.Host, route.domain) }
        clock.advanceBy(100_000)
        client.get("/") { header(HttpHeaders.Host, route.domain) }
        clock.advanceBy(200_000)
        client.get("/") { header(HttpHeaders.Host, route.domain) }
        clock.advanceBy(200_000)
        client.get("/") { header(HttpHeaders.Host, route.domain) }
        clock.advanceBy(290_000)
        assertEquals(0, container.shutdowns)
        clock.advanceBy(20_000)
        clock.advanceBy(10_000)
        assertTrue(container.shutdowns > 0)
        nursery.shutdown()
    }
}
