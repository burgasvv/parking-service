package org.burgas.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

@Serializable
data class ExceptionResponse(
    val status: String,
    val code: Int,
    val message: String
)

fun Application.configureRouting() {

    install(StatusPages) {
        exception<Exception> { call, cause ->
            val exceptionResponse = ExceptionResponse(
                status = HttpStatusCode.BadRequest.description,
                code = HttpStatusCode.BadRequest.value,
                message = cause.localizedMessage
            )
            call.respond(exceptionResponse)
        }
    }
}
