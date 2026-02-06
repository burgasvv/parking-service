package org.burgas.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class ExceptionResponse(
    val status: String,
    val code: Int,
    val message: String
)

@Serializable
data class CsrfToken(val token: String)

fun Application.configureRouting() {

//    install(StatusPages) {
//        exception<Exception> { call, cause ->
//            val exceptionResponse = ExceptionResponse(
//                status = HttpStatusCode.BadRequest.description,
//                code = HttpStatusCode.BadRequest.value,
//                message = cause.localizedMessage
//            )
//            call.respond(exceptionResponse)
//        }
//    }

    install(Sessions) {
        cookie<CsrfToken>("CSRF_TOKEN")
    }

    install(CSRF) {
        allowOrigin("http://localhost:9000")
        originMatchesHost()
        checkHeader("X-CSRF-Token")
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)

        allowCredentials = true
        allowSameOrigin = true

        allowHost("localhost:4200", listOf("http", "https"))
    }

    routing {

        route("/api/v1/security") {

            get("/csrf-token") {
                val csrfToken = call.sessions.get(CsrfToken::class)
                if (csrfToken != null) {
                    call.respond(HttpStatusCode.OK, csrfToken)
                } else {
                    val token = UUID.randomUUID()
                    val newCsrfToken = CsrfToken(token.toString())
                    call.sessions.set(newCsrfToken, CsrfToken::class)
                    call.respond(HttpStatusCode.OK, newCsrfToken)
                }
            }
        }
    }
}