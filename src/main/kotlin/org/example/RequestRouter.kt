package org.example

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.server.request.host
import io.ktor.server.application.ApplicationCall
import java.io.File

interface RequestRouter {
    suspend fun route(call: ApplicationCall): RouteConfig?
}

class ConfigFileRequestRouter(private val config: Config) : RequestRouter {
    override suspend fun route(call: ApplicationCall): RouteConfig? {
        val host = call.request.host()
        return config.routes.find { it.domain == host }
    }
}

fun requestRouterFromFile(path: String): RequestRouter {
    val config: Config = Gson().fromJson(File(path).readText(), object : TypeToken<Config>() {}.type)
    return ConfigFileRequestRouter(config)
}
