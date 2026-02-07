package router

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.burgas.database.*
import org.burgas.module
import org.burgas.routing.CsrfToken
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

class AddressRouterTest {

    @Test
    fun `test address router endpoints`() = testApplication {
        this.application {
            module()
        }
        val httpClient = this.createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val identityEntity = transaction(
            db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        ) {
            IdentityEntity.new {
                this.authority = Authority.ADMIN
                this.username = "admin"
                this.password = BCrypt.hashpw("admin", BCrypt.gensalt())
                this.email = "admin@gmail.com"
                this.enabled = true
                this.firstname = "Admin"
                this.lastname = "Admin"
                this.patronymic = "Admin"
            }
        }

        val csrfToken = httpClient.get("/api/v1/security/csrf-token") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
            .body<CsrfToken>()

        httpClient.post("/api/v1/addresses/create") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val addressRequest = AddressRequest(
                city = "Orel", street = "Signal", house = "56/2"
            )
            setBody(addressRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.Created, this.status)
            }

        val addressShortResponses = httpClient.get("/api/v1/addresses") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }
            .body<List<AddressShortResponse>>()
        val addressShortResponse = addressShortResponses.first { first -> first.city == "Orel" }

        httpClient.get("/api/v1/addresses/by-id") {
            parameter("addressId", addressShortResponse.id.toString())
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.put("/api/v1/addresses/update") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val addressRequest = AddressRequest(
                id = addressShortResponse.id, house = "23/4"
            )
            setBody(addressRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.delete("/api/v1/addresses/delete") {
            parameter("addressId", addressShortResponse.id.toString())
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            identityEntity.delete()
        }
    }
}