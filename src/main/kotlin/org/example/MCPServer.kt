package org.example

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException

fun getSecretPassword(): String = "P@ssw0rd"

const val DEFAULT_PORT = 12006

class MCPServer(private val port: Int = DEFAULT_PORT) {
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

fun main(args: Array<String>) {
    val options = Options().apply {
        addOption("p", "port", true, "Port to listen on (default $DEFAULT_PORT)")
    }

    val parser = DefaultParser()
    val port = try {
        val cmd = parser.parse(options, args)
        cmd.getOptionValue("p")?.toIntOrNull() ?: DEFAULT_PORT
    } catch (e: ParseException) {
        HelpFormatter().printHelp("MCPServer", options)
        return
    }

    val server = MCPServer(port)
    server.start()
    println("MCPServer started on port ${server.port()}")
    server.jettyServer.join()
}
