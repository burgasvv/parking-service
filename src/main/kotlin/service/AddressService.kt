package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import org.burgas.database.*
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

fun AddressEntity.insert(addressRequest: AddressRequest) {
    this.city = addressRequest.city ?: throw IllegalArgumentException("Address city is null")
    this.street = addressRequest.street ?: throw IllegalArgumentException("Address street is null")
    this.house = addressRequest.house ?: throw IllegalArgumentException("Address house is null")
}

fun AddressEntity.update(addressRequest: AddressRequest) {
    this.city = addressRequest.city ?: this.city
    this.street = addressRequest.street ?: this.street
    this.house = addressRequest.house ?: this.house
}

fun AddressEntity.toAddressShortResponse(): AddressShortResponse {
    return AddressShortResponse(
        id = this.id.value,
        city = this.city,
        street = this.street,
        house = this.house
    )
}

fun AddressEntity.toAddressFullResponse(): AddressFullResponse {
    return AddressFullResponse(
        id = this.id.value,
        city = this.city,
        street = this.street,
        house = this.house,
        parking = this.parking?.toParkingShortResponse()
    )
}

class AddressService {

    suspend fun create(addressRequest: AddressRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        AddressEntity.new { this.insert(addressRequest) }
    }

    suspend fun findAll(): List<AddressShortResponse> = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        AddressEntity.all().map { it.toAddressShortResponse() }
    }

    suspend fun findById(addressId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        (AddressEntity.findById(addressId) ?: throw IllegalArgumentException("identity not found"))
            .load(AddressEntity::parking)
            .toAddressFullResponse()
    }

    suspend fun update(addressRequest: AddressRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val addressId = addressRequest.id ?: throw IllegalArgumentException("Address id is null")
        (AddressEntity.findByIdAndUpdate(addressId) { it.update(addressRequest) })
            ?: throw IllegalArgumentException("Address not found")
    }

    suspend fun delete(addressId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        (AddressEntity.findById(addressId) ?: throw IllegalArgumentException("identity not found")).delete()
    }
}

fun Application.configureAddressRoutes() {

    val addressService = AddressService()

    routing {

        route("/api/v1/addresses") {

            authenticate("basic-auth-all") {

                get {
                    call.respond(HttpStatusCode.OK, addressService.findAll())
                }

                get("/by-id") {
                    val addressId = UUID.fromString(call.parameters["addressId"])
                    call.respond(HttpStatusCode.OK, addressService.findById(addressId))
                }
            }

            authenticate("basic-auth-admin") {

                post("/create") {
                    val addressRequest = call.receive(AddressRequest::class)
                    addressService.create(addressRequest)
                    call.respond(HttpStatusCode.Created)
                }

                put("/update") {
                    val addressRequest = call.receive(AddressRequest::class)
                    addressService.update(addressRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val addressId = UUID.fromString(call.parameters["addressId"])
                    addressService.delete(addressId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}