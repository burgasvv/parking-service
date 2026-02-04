package org.burgas.service

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.burgas.database.*
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import java.sql.Connection
import java.util.*

fun ParkingEntity.insert(parkingRequest: ParkingRequest) {
    val addressRequest = parkingRequest.address ?: throw IllegalArgumentException("Parking address request is null")
    if (addressRequest.id != null) {
        this.address = AddressEntity.findById(addressRequest.id) ?: throw IllegalArgumentException("Address not found")
    } else {
        this.address = AddressEntity.new { this.insert(addressRequest) }
    }
    this.price = parkingRequest.price ?: throw IllegalArgumentException("Parking price is null")
}

fun ParkingEntity.update(parkingRequest: ParkingRequest) {
    val addressRequest = parkingRequest.address
    if (addressRequest != null) {

        if (addressRequest.id != null) {
            this.address = AddressEntity.findById(addressRequest.id) ?: throw IllegalArgumentException("Address not found")
        } else {
            this.address = AddressEntity.new { this.insert(addressRequest) }
        }
    }
    this.price = parkingRequest.price ?: this.price
}

fun ParkingEntity.toParkingShortResponse(): ParkingShortResponse {
    return ParkingShortResponse(
        id = this.id.value,
        price = this.price
    )
}

fun ParkingEntity.toParkingWithAddressResponse(): ParkingWithAddressResponse {
    return ParkingWithAddressResponse(
        id = this.id.value,
        address = this.address.toAddressShortResponse(),
        price = this.price
    )
}

fun ParkingEntity.toParkingFullResponse(): ParkingFullResponse {
    return ParkingFullResponse(
        id = this.id.value,
        address = this.address.toAddressShortResponse(),
        price = this.price,
        cars = this.cars.map { it.toCarShortResponse() }
    )
}

class ParkingService {

    suspend fun create(parkingRequest: ParkingRequest) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            ParkingEntity.new { this.insert(parkingRequest) }
        }
    }

    suspend fun findAll(): List<ParkingWithAddressResponse> = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres) {
            ParkingEntity.all().with(ParkingEntity::address).map { it.toParkingWithAddressResponse() }
        }
    }

    suspend fun findById(parkingId: UUID): ParkingFullResponse = withContext(Dispatchers.Default) {
        val redis = DatabaseFactory.redis
        val parkingString = redis.get("parkingFullResponse::$parkingId")
        if (parkingString != null) {
            Json.decodeFromString<ParkingFullResponse>(parkingString)
        } else {
            transaction(db = DatabaseFactory.postgres) {
                val parkingFullResponse =
                    (ParkingEntity.findById(parkingId) ?: throw IllegalArgumentException("Parking not found"))
                        .toParkingFullResponse()
                redis.set("parkingFullResponse::${parkingFullResponse.id}", Json.encodeToString(parkingFullResponse))
                parkingFullResponse
            }
        }
    }

    suspend fun update(parkingRequest: ParkingRequest) = withContext(Dispatchers.Default) {
        val parkingId = parkingRequest.id ?: throw IllegalArgumentException("Parking id is null")
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            ParkingEntity.findByIdAndUpdate(parkingId) { it.update(parkingRequest) }
            val redis = DatabaseFactory.redis
            if (redis.exists("parkingFullResponse::$parkingId")) {
                redis.del("parkingFullResponse::$parkingId")
            }
        }
    }

    suspend fun delete(parkingId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (ParkingEntity.findById(parkingId) ?: throw IllegalArgumentException("Parking not found")).delete()
            val redis = DatabaseFactory.redis
            if (redis.exists("parkingFullResponse::$parkingId")) {
                redis.del("parkingFullResponse::$parkingId")
            }
        }
    }

    suspend fun addCars(parkingCarRequests: List<ParkingCarRequest>) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            parkingCarRequests.forEach { parkingCarRequest ->

                val parkingEntity = ParkingEntity.find { ParkingTable.id eq parkingCarRequest.parkingId }
                    .forUpdate(ForUpdateOption.ForUpdate)
                    .singleOrNull() ?: throw IllegalArgumentException("Parking not found")

                val carEntity = CarEntity.find { CarTable.id eq parkingCarRequest.carId }
                    .forUpdate(ForUpdateOption.ForUpdate)
                    .singleOrNull() ?: throw IllegalArgumentException("Car not found")

                if (!parkingEntity.cars.contains(carEntity)) {
                    parkingEntity.cars = SizedCollection(parkingEntity.cars + carEntity)
                }
            }
        }
    }

    suspend fun removeCars(parkingCarRequests: List<ParkingCarRequest>) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            parkingCarRequests.forEach { parkingCarRequest ->

                val parkingEntity = ParkingEntity.find { ParkingTable.id eq parkingCarRequest.parkingId }
                    .forUpdate(ForUpdateOption.ForUpdate)
                    .singleOrNull() ?: throw IllegalArgumentException("Parking not found")

                val carEntity = CarEntity.find { CarTable.id eq parkingCarRequest.carId }
                    .forUpdate(ForUpdateOption.ForUpdate)
                    .singleOrNull() ?: throw IllegalArgumentException("Car not found")

                if (parkingEntity.cars.contains(carEntity)) {
                    parkingEntity.cars = SizedCollection(parkingEntity.cars - carEntity)
                }
            }
        }
    }
}

fun Application.configureParkingRoutes() {

    val parkingService = ParkingService()

    routing {

        route("/api/v1/parking") {

            authenticate("basic-auth-all") {

                get {
                    call.respond(HttpStatusCode.OK, parkingService.findAll())
                }

                get("/by-id") {
                    val parkingId = UUID.fromString(call.parameters["parkingId"])
                    call.respond(HttpStatusCode.OK, parkingService.findById(parkingId))
                }
            }

            authenticate("basic-auth-admin") {

                post("/create") {
                    val parkingRequest = call.receive(ParkingRequest::class)
                    parkingService.create(parkingRequest)
                    call.respond(HttpStatusCode.Created)
                }

                put("/update") {
                    val parkingRequest = call.receive(ParkingRequest::class)
                    parkingService.update(parkingRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val parkingId = UUID.fromString(call.parameters["parkingId"])
                    parkingService.delete(parkingId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/add-cars") {
                    val parkingCarRequests = call.receive<List<ParkingCarRequest>>()
                    parkingService.addCars(parkingCarRequests)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-cars") {
                    val parkingCarRequests = call.receive<List<ParkingCarRequest>>()
                    parkingService.removeCars(parkingCarRequests)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}