package org.example

import io.ktor.server.application.ApplicationCall

interface Container {
    val hostPort: Int
    suspend fun start()
    suspend fun handle(call: ApplicationCall)
    fun shutdown()
    fun kill()
}
