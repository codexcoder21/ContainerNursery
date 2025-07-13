package org.example

import org.json.JSONObject
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
    val config = configFromFile(path)
    return ConfigFileRequestRouter(config)
}

fun configFromFile(path: String): Config {
    val text = File(path).readText()
    val root = JSONObject(text)
    val routesJson = root.getJSONArray("routes")
    val routes = mutableListOf<RouteConfig>()
    for (i in 0 until routesJson.length()) {
        val obj = routesJson.getJSONObject(i)
        val type = RouteType.valueOf(obj.optString("type", "http").uppercase())
        routes += RouteConfig(
            domain = obj.getString("domain"),
            image = obj.getString("image"),
            keepWarmSeconds = obj.getLong("keepWarmSeconds"),
            port = obj.getInt("port"),
            type = type
        )
    }
    return Config(routes)
}
