package org.example

class ManualClock(private var nowMillis: Long = 0L) : Clock {
    private data class Task(val time: Long, val callback: () -> Unit)
    private val tasks = mutableListOf<Task>()

    override fun now(): Long = nowMillis

    override fun schedule(atMillis: Long, callback: () -> Unit): Scheduled {
        val task = Task(atMillis, callback)
        tasks.add(task)
        tasks.sortBy { it.time }
        return object : Scheduled { override fun cancel() { tasks.remove(task) } }
    }

    fun advanceBy(millis: Long) {
        val target = nowMillis + millis
        while (true) {
            val next = tasks.firstOrNull() ?: break
            if (next.time > target) break
            tasks.removeAt(0)
            nowMillis = next.time
            next.callback()
        }
        nowMillis = target
    }
}
