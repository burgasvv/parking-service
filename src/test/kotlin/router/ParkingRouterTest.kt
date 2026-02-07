package router

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.burgas.database.AddressEntity
import org.burgas.database.AddressRequest
import org.burgas.database.Authority
import org.burgas.database.CarEntity
import org.burgas.database.DatabaseFactory
import org.burgas.database.IdentityEntity
import org.burgas.database.ParkingCarRequest
import org.burgas.database.ParkingRequest
import org.burgas.database.ParkingShortResponse
import org.burgas.module
import org.burgas.routing.CsrfToken
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

class ParkingRouterTest {

    @Test
    fun `test parking router endpoints`() = testApplication {
        this.application {
            module()
        }
        val httpClient = this.createClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
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

        val carEntity = transaction(
            db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        ) {
            CarEntity.new {
                this.brand = "BMW"
                this.model = "A5"
                this.description = "Описание автомобиля BMW A5"
                this.identity = identityEntity
            }
        }

        val addressEntity = transaction(
            db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        ) {
            AddressEntity.new {
                this.city = "Tomsk"
                this.street = "Petrovka"
                this.house = "67/1"
            }
        }

        val csrfToken = httpClient.get("/api/v1/security/csrf-token") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
            .body<CsrfToken>()

        httpClient.post("/api/v1/parking/create") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val parkingRequest = ParkingRequest(
                address = AddressRequest(id = addressEntity.id.value),
                price = 1500.50
            )
            setBody(parkingRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.Created, this.status)
            }

        val parkingShortResponses = httpClient.get("/api/v1/parking") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }
            .body<List<ParkingShortResponse>>()
        val parkingShortResponse = parkingShortResponses.first { first -> first.price == 1500.50 }

        httpClient.get("/api/v1/parking/by-id") {
            parameter("parkingId", parkingShortResponse.id.toString())
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.put("/api/v1/parking/update") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val parkingRequest = ParkingRequest(
                id = parkingShortResponse.id,
                price = 1600.20
            )
            setBody(parkingRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.post("/api/v1/parking/add-cars") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val parkingCarRequest = ParkingCarRequest(
                parkingId = parkingShortResponse.id!!,
                carId = carEntity.id.value
            )
            val parkingCarRequests = listOf(parkingCarRequest)
            setBody(parkingCarRequests)
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.delete("/api/v1/parking/remove-cars") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val parkingCarRequest = ParkingCarRequest(
                parkingId = parkingShortResponse.id!!,
                carId = carEntity.id.value
            )
            val parkingCarRequests = listOf(parkingCarRequest)
            setBody(parkingCarRequests)
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.delete("/api/v1/parking/delete") {
            parameter("parkingId", parkingShortResponse.id.toString())
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
            addressEntity.delete()
        }

        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            carEntity.delete()
        }

        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            identityEntity.delete()
        }
    }
}