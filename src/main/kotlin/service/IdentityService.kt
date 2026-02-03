package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.burgas.database.*
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.util.*

fun IdentityEntity.insert(identityRequest: IdentityRequest) {
    this.authority = identityRequest.authority ?: throw IllegalArgumentException("Identity authority is null")
    this.username = identityRequest.username ?: throw IllegalArgumentException("Identity username is null")
    this.password = identityRequest.password ?: throw IllegalArgumentException("Identity password is null")
    this.email = identityRequest.email ?: throw IllegalArgumentException("Identity email is null")
    this.enabled = identityRequest.enabled ?: true
    this.firstname = identityRequest.firstname ?: throw IllegalArgumentException("Identity firstname is null")
    this.lastname = identityRequest.lastname ?: throw IllegalArgumentException("Identity lastname is null")
    this.patronymic = identityRequest.patronymic ?: throw IllegalArgumentException("Identity patronymic is null")
}

fun IdentityEntity.update(identityRequest: IdentityRequest) {
    this.authority = identityRequest.authority ?: this.authority
    this.username = identityRequest.username ?: this.username
    this.email = identityRequest.email ?: this.email
    this.firstname = identityRequest.firstname ?: this.firstname
    this.lastname = identityRequest.lastname ?: this.lastname
    this.patronymic = identityRequest.patronymic ?: this.patronymic
}

fun IdentityEntity.toIdentityShortResponse(): IdentityShortResponse {
    return IdentityShortResponse(
        id = this.id.value,
        username = this.username,
        email = this.email,
        firstname = this.firstname,
        lastname = this.lastname,
        patronymic = this.patronymic
    )
}

fun IdentityEntity.toIdentityFullResponse(): IdentityFullResponse {
    return IdentityFullResponse(
        id = this.id.value,
        username = this.username,
        email = this.email,
        firstname = this.firstname,
        lastname = this.lastname,
        patronymic = this.patronymic,
        cars = this.cars.map { it.toCarShortResponse() }
    )
}

class IdentityService {

    suspend fun create(identityRequest: IdentityRequest) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            IdentityEntity.new { this.insert(identityRequest) }
        }
    }

    suspend fun update(identityRequest: IdentityRequest) = withContext(Dispatchers.Default) {
        val identityId = identityRequest.id ?: throw IllegalArgumentException("Identity id is null")
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            IdentityEntity.findByIdAndUpdate(identityId) {
                identityEntity -> identityEntity.update(identityRequest)
            }
        }
        val redis = DatabaseFactory.redis
        if (redis.exists("identityFullResponse::$identityId")) {
            redis.del("identityFullResponse::$identityId")
        }
    }

    suspend fun findAll(): List<IdentityShortResponse> = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres) {
            IdentityEntity.all().map { identityEntity -> identityEntity.toIdentityShortResponse() }
        }
    }

    suspend fun findById(identityId: UUID): IdentityFullResponse = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres) {
            val redis = DatabaseFactory.redis
            val identityString = redis.get("identityFullResponse::$identityId")
            if (identityString != null) {
                Json.decodeFromString<IdentityFullResponse>(identityString)
            } else {
                val identityFullResponse =
                    (IdentityEntity.findById(identityId) ?: throw IllegalArgumentException("Identity not found"))
                        .load(IdentityEntity::cars)
                        .toIdentityFullResponse()
                redis.set("identityFullResponse::${identityFullResponse.id}", Json.encodeToString(identityFullResponse))
                identityFullResponse
            }
        }
    }

    suspend fun delete(identityId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (IdentityEntity.findById(identityId) ?: throw IllegalArgumentException("Identity not found")).delete()
        }
        val redis = DatabaseFactory.redis
        if (redis.exists("identityFullResponse::$identityId")) {
            redis.del("identityFullResponse::$identityId")
        }
    }
}

fun Application.configureIdentityRoutes() {

    val identityService = IdentityService()

    routing {

        route("/api/v1/identities") {

            get {
                call.respond(HttpStatusCode.OK, identityService.findAll())
            }

            get("/by-id") {
                val identityId = UUID.fromString(call.parameters["identityId"])
                call.respond(HttpStatusCode.OK, identityService.findById(identityId))
            }

            post("/create") {
                val identityRequest = call.receive(IdentityRequest::class)
                identityService.create(identityRequest)
                call.respond(HttpStatusCode.Created)
            }

            put("/update") {
                val identityRequest = call.receive(IdentityRequest::class)
                identityService.update(identityRequest)
                call.respond(HttpStatusCode.OK)
            }

            delete("/delete") {
                val identityId = UUID.fromString(call.parameters["identityId"])
                identityService.delete(identityId)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}