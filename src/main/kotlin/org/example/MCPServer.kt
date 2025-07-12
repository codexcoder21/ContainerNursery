package org.example

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

fun getSecretPassword(): String = "P@ssw0rd"

class MCPServer(private val port: Int = 0) {
    val jettyServer = Server(port)
    init {
        val context = ServletContextHandler(ServletContextHandler.NO_SESSIONS)
        context.contextPath = "/"
        context.addServlet(ServletHolder(PasswordServlet()), "/password")
        jettyServer.handler = context
    }

    fun start() { jettyServer.start() }
    fun stop() { jettyServer.stop() }
    fun port(): Int = (jettyServer.connectors.first() as org.eclipse.jetty.server.ServerConnector).localPort

    private class PasswordServlet : HttpServlet() {
        override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "text/plain"
            resp.writer.write(getSecretPassword())
        }
    }
}

fun main() {
    val server = MCPServer(8080)
    server.start()
    println("MCPServer started on port 8080")
    server.port() // to trigger port evaluation
    server.jettyServer.join()
}
