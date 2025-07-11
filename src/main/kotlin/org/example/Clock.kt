package org.example

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

interface Scheduled {
    fun cancel()
}

interface Clock {
    fun now(): Long
    fun schedule(atMillis: Long, callback: () -> Unit): Scheduled
    fun unschedule(scheduled: Scheduled) { scheduled.cancel() }
}

class SystemClock : Clock {
    private val executor = ScheduledThreadPoolExecutor(1)
    override fun now(): Long = System.currentTimeMillis()
    override fun schedule(atMillis: Long, callback: () -> Unit): Scheduled {
        val delay = kotlin.math.max(0L, atMillis - now())
        val future = executor.schedule(callback, delay, TimeUnit.MILLISECONDS)
        return object : Scheduled { override fun cancel() { future.cancel(false) } }
    }
}
