package router

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.burgas.database.Authority
import org.burgas.database.IdentityRequest
import org.burgas.database.IdentityShortResponse
import org.burgas.module
import org.burgas.routing.CsrfToken
import org.mindrot.jbcrypt.BCrypt
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityRouterTest {

    @Test
    fun `test identity router endpoints`() = testApplication {
        this.application {
            module()
        }
        val httpClient = this.createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val csrfToken = httpClient.get("/api/v1/security/csrf-token") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
            .body<CsrfToken>()

        httpClient.post("/api/v1/identities/create") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            val identityRequest = IdentityRequest(
                authority = Authority.ADMIN, username = "admin", password = BCrypt.hashpw("admin", BCrypt.gensalt()),
                email = "admin@gmail.com", enabled = true, firstname = "Admin", lastname = "Admin", patronymic = "Admin"
            )
            setBody(identityRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.Created, this.status)
            }

        val identityShortResponses = httpClient.get("/api/v1/identities") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }
            .body<List<IdentityShortResponse>>()
        val identityShortResponse = identityShortResponses.first { first -> first.email == "admin@gmail.com" }

        httpClient.get("/api/v1/identities/by-id") {
            parameter("identityId", identityShortResponse.id.toString())
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.put("/api/v1/identities/update") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val identityRequest = IdentityRequest(
                id = identityShortResponse.id,
                username = "admin test"
            )
            setBody(identityRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.delete("/api/v1/identities/delete") {
            parameter("identityId", identityShortResponse.id.toString())
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }
    }
}