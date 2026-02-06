package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.burgas.database.*
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.util.*

fun CarEntity.insert(carRequest: CarRequest) {
    this.brand = carRequest.brand ?: throw IllegalArgumentException("Car brand is null")
    this.model = carRequest.model ?: throw IllegalArgumentException("Car model is null")
    this.description = carRequest.description ?: throw IllegalArgumentException("Car description is null")
    this.identity =
        IdentityEntity.findById(carRequest.identityId ?: throw IllegalArgumentException("Car identity id is null"))
            ?: throw IllegalArgumentException("Car identity is null")
}

fun CarEntity.update(carRequest: CarRequest) {
    this.brand = carRequest.brand ?: this.brand
    this.model = carRequest.model ?: this.model
    this.description = carRequest.description ?: this.description
    this.identity = IdentityEntity.findById(carRequest.identityId ?: UUID.randomUUID()) ?: this.identity
}

fun CarEntity.toCarShortResponse(): CarShortResponse {
    return CarShortResponse(
        id = this.id.value,
        brand = this.brand,
        model = this.model,
        description = this.description
    )
}

fun CarEntity.toCarFullResponse(): CarFullResponse {
    return CarFullResponse(
        id = this.id.value,
        brand = this.brand,
        model = this.model,
        description = this.description,
        identity = this.identity.toIdentityShortResponse(),
        parking = this.parking.map { it.toParkingWithAddressResponse() }
    )
}

class CarService {

    suspend fun create(carRequest: CarRequest) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            CarEntity.new { this.insert(carRequest) }
        }
    }

    suspend fun findAll() = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres) {
            CarEntity.all().map { it.toCarShortResponse() }
        }
    }

    suspend fun findByIdentityId(identityId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres) {
            CarEntity.find { CarTable.identityId eq identityId }.map { it.toCarShortResponse() }
        }
    }

    suspend fun findById(carId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres) {
            (CarEntity.findById(carId) ?: throw IllegalArgumentException("Car not found"))
                .load(CarEntity::identity, CarEntity::parking)
                .toCarFullResponse()
        }
    }

    suspend fun update(carRequest: CarRequest) = withContext(Dispatchers.Default) {
        val carId = carRequest.id ?: throw IllegalArgumentException("Car id is null")
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (CarEntity.findByIdAndUpdate(carId) { it.update(carRequest) }) ?: throw IllegalArgumentException("Car not found")
        }
    }

    suspend fun delete(carId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (CarEntity.findById(carId) ?: throw IllegalArgumentException("Car not found")).delete()
        }
    }
}

fun Application.configureCarRoutes() {

    val carService = CarService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (call.request.path().equals("/api/v1/cars/by-identity", false)) {
                val principal =
                    call.principal<UserPasswordCredential>() ?: throw IllegalArgumentException("Not authenticated")
                val identityId = UUID.fromString(call.parameters["identityId"])

                val identityEntity = transaction(db = DatabaseFactory.postgres) {
                    IdentityEntity.findById(identityId)
                        ?: throw IllegalArgumentException("Identity not found for proceed")
                }

                if (identityEntity.email == principal.name) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Not authorized")
                }

            } else if (
                call.request.path().equals("/api/v1/cars/by-id", false) ||
                call.request.path().equals("/api/v1/cars/delete", false)
            ) {
                val principal =
                    call.principal<UserPasswordCredential>() ?: throw IllegalArgumentException("Not authenticated")

                val carId = UUID.fromString(call.parameters["carId"])
                val identityResult = transaction(db = DatabaseFactory.postgres) {
                    IdentityTable.leftJoin(CarTable, { CarTable.identityId }, { IdentityTable.id })
                        .selectAll()
                        .where { CarTable.id eq carId }
                        .distinct()
                        .singleOrNull() ?: throw IllegalArgumentException("Car identity not found for proceed")
                }

                if (identityResult[IdentityTable.email] == principal.name) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Not authorized")
                }

            } else if (call.request.path().equals("/api/v1/cars/create", false)) {
                val principal =
                    call.principal<UserPasswordCredential>() ?: throw IllegalArgumentException("Not authenticated")

                val carRequest = call.receive(CarRequest::class)
                val identityId =
                    carRequest.identityId ?: throw IllegalArgumentException("Car identity id is null for proceed")

                val identityEntity = transaction(db = DatabaseFactory.postgres) {
                    IdentityEntity.findById(identityId)
                        ?: throw IllegalArgumentException("Identity not found for proceed")
                }

                if (identityEntity.email == principal.name) {
                    call.attributes[AttributeKey<CarRequest>("carRequest")] = carRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Not authorized")
                }

            } else if (call.request.path().equals("/api/v1/cars/update", false)) {
                val principal =
                    call.principal<UserPasswordCredential>() ?: throw IllegalArgumentException("Not authenticated")

                val carRequest = call.receive(CarRequest::class)
                val carId = carRequest.id ?: throw IllegalArgumentException("Car id is null for proceed")

                val identityResult = transaction(db = DatabaseFactory.postgres) {
                    IdentityTable.leftJoin(CarTable, { CarTable.identityId }, { IdentityTable.id })
                        .selectAll()
                        .where { CarTable.id eq carId }
                        .distinct()
                        .singleOrNull() ?: throw IllegalArgumentException("Car identity not found for proceed")
                }

                if (identityResult[IdentityTable.email] == principal.name) {
                    call.attributes[AttributeKey<CarRequest>("carRequest")] = carRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Not authorized")
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/cars") {

            authenticate("basic-auth-admin") {

                get {
                    call.respond(HttpStatusCode.OK, carService.findAll())
                }
            }

            authenticate("basic-auth-all") {

                get("/by-identity") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    call.respond(HttpStatusCode.OK, carService.findByIdentityId(identityId))
                }

                get("/by-id") {
                    val carId = UUID.fromString(call.parameters["carId"])
                    call.respond(HttpStatusCode.OK, carService.findById(carId))
                }

                post("/create") {
                    val carRequest = call.attributes[AttributeKey<CarRequest>("carRequest")]
                    carService.create(carRequest)
                    call.respond(HttpStatusCode.Created)
                }

                put("/update") {
                    val carRequest = call.attributes[AttributeKey<CarRequest>("carRequest")]
                    carService.update(carRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val carId = UUID.fromString(call.parameters["carId"])
                    carService.delete(carId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}