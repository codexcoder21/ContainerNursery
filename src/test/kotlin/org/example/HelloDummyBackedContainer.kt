package org.example

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CountDownLatch

class HelloDummyBackedContainer(
    private val clock: Clock,
    private val delayMs: Long = 3000L
) : Container {
    var started = 0
    private val startedLatch = CountDownLatch(1)

    private suspend fun delay() = suspendCancellableCoroutine<Unit> { cont ->
        val scheduled = clock.schedule(clock.now() + delayMs) { cont.resume(Unit) }
        cont.invokeOnCancellation { clock.unschedule(scheduled) }
    }

    override suspend fun start() {
        started++
        delay()
        startedLatch.countDown()
    }

    override suspend fun handle(call: ApplicationCall) {
        startedLatch.await()
        call.respondText("Hello World")
    }

    override fun shutdown() {}

    override fun kill() {}
}
