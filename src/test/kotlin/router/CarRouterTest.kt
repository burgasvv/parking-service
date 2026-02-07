package router

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.basicAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.burgas.database.Authority
import org.burgas.database.CarRequest
import org.burgas.database.CarShortResponse
import org.burgas.database.DatabaseFactory
import org.burgas.database.IdentityEntity
import org.burgas.module
import org.burgas.routing.CsrfToken
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

class CarRouterTest {

    @Test
    fun `test car router endpoints`() = testApplication {
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

        httpClient.post("/api/v1/cars/create") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val carRequest = CarRequest(
                brand = "BMW", model = "M7", description = "Описание M7", identityId = identityEntity.id.value
            )
            setBody(carRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.Created, this.status)
            }

        val carShortResponses = httpClient.get("/api/v1/cars") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }
            .body<List<CarShortResponse>>()
        val carShortResponse = carShortResponses.first { first -> first.model == "M7" }

        httpClient.get("/api/v1/cars/by-identity") {
            parameter("identityId", identityEntity.id.value.toString())
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.get("/api/v1/cars/by-id") {
            parameter("carId", carShortResponse.id.toString())
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.put("/api/v1/cars/update") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val carRequest = CarRequest(
                id = carShortResponse.id, model = "M9"
            )
            setBody(carRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.delete("/api/v1/cars/delete") {
            parameter("carId", carShortResponse.id.toString())
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
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