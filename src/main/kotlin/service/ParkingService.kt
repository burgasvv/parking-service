package org.burgas.service

import io.github.flaxoos.ktor.server.plugins.kafka.components.toRecord
import io.github.flaxoos.ktor.server.plugins.kafka.kafkaProducer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.ProducerRecord
import org.burgas.database.*
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
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
            this.address =
                AddressEntity.findById(addressRequest.id) ?: throw IllegalArgumentException("Address not found")
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

    private val redis = DatabaseFactory.redis
    private val parkingKey = "parkingFullResponse::%s"
    private val carKey = "carFullResponse::%s"

    suspend fun create(parkingRequest: ParkingRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        ParkingEntity.new { this.insert(parkingRequest) }
            .load(ParkingEntity::address, ParkingEntity::cars)
            .toParkingFullResponse()
    }

    suspend fun findAll(): List<ParkingWithAddressResponse> = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        ParkingEntity.all().with(ParkingEntity::address).map { it.toParkingWithAddressResponse() }
    }

    suspend fun findById(parkingId: UUID): ParkingFullResponse = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val parkingKey = parkingKey.format(parkingId)
        val parkingString = redis.get(parkingKey)

        if (parkingString != null) {
            Json.decodeFromString<ParkingFullResponse>(parkingString)

        } else {
            val parkingFullResponse =
                (ParkingEntity.findById(parkingId) ?: throw IllegalArgumentException("Parking not found"))
                    .load(ParkingEntity::address, ParkingEntity::cars)
                    .toParkingFullResponse()
            redis.set(parkingKey, Json.encodeToString(parkingFullResponse))
            parkingFullResponse
        }
    }

    suspend fun update(parkingRequest: ParkingRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val parkingId = parkingRequest.id ?: throw IllegalArgumentException("Parking id is null")
        val parkingEntity = ((ParkingEntity.findByIdAndUpdate(parkingId) { it.update(parkingRequest) })
            ?: throw IllegalArgumentException("Parking not found"))

        handleCacheWithCars(parkingEntity)
    }

    suspend fun delete(parkingId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val parkingEntity = (ParkingEntity.findById(parkingId) ?: throw IllegalArgumentException("Parking not found"))

        handleCacheWithCars(parkingEntity)
        parkingEntity.delete()
    }

    private fun handleCacheWithCars(parkingEntity: ParkingEntity) {
        val parkingKey = parkingKey.format(parkingEntity.id)
        if (redis.exists(parkingKey)) redis.del(parkingKey)

        if (!parkingEntity.cars.empty()) {

            parkingEntity.cars.forEach { carEntity ->
                val carKey = carKey.format(carEntity.id)
                if (redis.exists(carKey)) redis.del(carKey)
            }
        }
    }

    suspend fun addCars(parkingCarRequests: List<ParkingCarRequest>) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        parkingCarRequests.forEach { parkingCarRequest ->

            val parkingEntity = ParkingEntity.find { ParkingTable.id eq parkingCarRequest.parkingId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull() ?: throw IllegalArgumentException("Parking not found")

            val carEntity = CarEntity.find { CarTable.id eq parkingCarRequest.carId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull() ?: throw IllegalArgumentException("Car not found")

            handleCache(parkingEntity, carEntity)

            if (!parkingEntity.cars.contains(carEntity)) {
                parkingEntity.cars = SizedCollection(parkingEntity.cars + carEntity)
            }
        }
    }

    suspend fun removeCars(parkingCarRequests: List<ParkingCarRequest>) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        parkingCarRequests.forEach { parkingCarRequest ->

            val parkingEntity = ParkingEntity.find { ParkingTable.id eq parkingCarRequest.parkingId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull() ?: throw IllegalArgumentException("Parking not found")

            val carEntity = CarEntity.find { CarTable.id eq parkingCarRequest.carId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull() ?: throw IllegalArgumentException("Car not found")

            handleCache(parkingEntity, carEntity)

            if (parkingEntity.cars.contains(carEntity)) {
                parkingEntity.cars = SizedCollection(parkingEntity.cars - carEntity)
            }
        }
    }

    private fun handleCache(parkingEntity: ParkingEntity, carEntity: CarEntity) {
        val parkingKey = parkingKey.format(parkingEntity.id)
        if (redis.exists(parkingKey)) redis.del(parkingKey)

        val carKey = carKey.format(carEntity.id)
        if (redis.exists(carKey)) redis.del(carKey)
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
                    val parkingFullResponse = parkingService.create(parkingRequest)
                    val producerRecord = ProducerRecord(
                        "parking-topic", "Create Parking", parkingFullResponse.toRecord()
                    )
                    kafkaProducer?.send(producerRecord)?.get()
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