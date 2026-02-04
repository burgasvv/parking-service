package org.burgas

import io.ktor.server.application.*
import org.burgas.database.configureDatabases
import org.burgas.routing.configureRouting
import org.burgas.security.configureAuthentication
import org.burgas.serialization.configureSerialization
import org.burgas.service.configureIdentityRoutes

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureDatabases()
    configureRouting()
    configureAuthentication()

    configureIdentityRoutes()
}
