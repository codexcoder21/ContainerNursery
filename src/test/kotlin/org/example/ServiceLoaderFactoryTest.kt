package org.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceLoaderFactoryTest {
    @Test
    fun serviceLoaderCreatesContainer() = testApplication {
        val route = RouteConfig("loader.test", "dummytest://anything", 30, 8081, RouteType.HTTP)
        val router = object : RequestRouter {
            override suspend fun route(call: io.ktor.server.application.ApplicationCall) = route
        }
        val factory = ServiceLoaderContainerFactory()
        val nursery = ContainerNursery(router, SystemClock(), factory)
        application { module(Config(listOf(route)), router, nursery, SystemClock(), factory) }
        val response = client.get("/") { header(HttpHeaders.Host, route.domain) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Hello World"))
        nursery.shutdown()
    }
}
