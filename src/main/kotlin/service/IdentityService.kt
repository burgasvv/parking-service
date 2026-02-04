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
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.util.*

fun IdentityEntity.insert(identityRequest: IdentityRequest) {
    this.authority = identityRequest.authority ?: throw IllegalArgumentException("Identity authority is null")
    this.username = identityRequest.username ?: throw IllegalArgumentException("Identity username is null")
    val newPassword = if (identityRequest.password.isNullOrEmpty())
        throw IllegalArgumentException("Identity password is null or empty")
    else
        identityRequest.password
    this.password = BCrypt.hashpw(newPassword, BCrypt.gensalt())
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
            IdentityEntity.findByIdAndUpdate(identityId) { identityEntity -> identityEntity.update(identityRequest) }
        }
    }

    suspend fun findAll(): List<IdentityShortResponse> = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres) {
            IdentityEntity.all().map { identityEntity -> identityEntity.toIdentityShortResponse() }
        }
    }

    suspend fun findById(identityId: UUID): IdentityFullResponse = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres) {
            (IdentityEntity.findById(identityId) ?: throw IllegalArgumentException("Identity not found"))
                .load(IdentityEntity::cars)
                .toIdentityFullResponse()
        }
    }

    suspend fun delete(identityId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (IdentityEntity.findById(identityId) ?: throw IllegalArgumentException("Identity not found")).delete()
        }
    }

    suspend fun changePassword(identityRequest: IdentityRequest) = withContext(Dispatchers.Default) {
        if (identityRequest.id == null) {
            throw IllegalArgumentException("Identity id is null")
        }
        if (identityRequest.password == null) {
            throw IllegalArgumentException("Identity password is null")
        }
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val identityEntity =
                IdentityEntity.findById(identityRequest.id) ?: throw IllegalArgumentException("Identity not found")
            if (BCrypt.checkpw(identityRequest.password, identityEntity.password)) {
                throw IllegalArgumentException("Passwords matched")
            }
            identityEntity.apply {
                this.password = BCrypt.hashpw(identityRequest.password, BCrypt.gensalt())
            }
        }
    }

    suspend fun changeStatus(identityRequest: IdentityRequest) = withContext(Dispatchers.Default) {
        if (identityRequest.id == null) {
            throw IllegalArgumentException("Identity id is null")
        }
        if (identityRequest.enabled == null) {
            throw IllegalArgumentException("Identity status is null")
        }
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val identityEntity =
                IdentityEntity.findById(identityRequest.id) ?: throw IllegalArgumentException("Identity not found")
            if (identityEntity.enabled == identityRequest.enabled) {
                throw IllegalArgumentException("identity statuses matched")
            }
            identityEntity.apply {
                this.enabled = identityRequest.enabled
            }
        }
    }
}

fun Application.configureIdentityRoutes() {

    val identityService = IdentityService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {
            if (
                call.request.path().equals("/api/v1/identities/by-id", false) ||
                call.request.path().equals("/api/v1/identities/delete", false)
            ) {
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
                call.request.path().equals("/api/v1/identities/update", false) ||
                call.request.path().equals("/api/v1/identities/change-password", false)
            ) {
                val principal =
                    call.principal<UserPasswordCredential>() ?: throw IllegalArgumentException("Not authenticated")

                val identityRequest = call.receive(IdentityRequest::class)
                val identityId =
                    identityRequest.id ?: throw IllegalArgumentException("Identity id is null, not proceeded")

                val identityEntity = transaction(db = DatabaseFactory.postgres) {
                    IdentityEntity.findById(identityId)
                        ?: throw IllegalArgumentException("Identity not found for proceed")
                }

                if (identityEntity.email == principal.name) {
                    call.attributes[AttributeKey<IdentityRequest>("identityRequest")] = identityRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Not authorized")
                }
            }
            proceed()
        }

        route("/api/v1/identities") {

            post("/create") {
                val identityRequest = call.receive(IdentityRequest::class)
                identityService.create(identityRequest)
                call.respond(HttpStatusCode.Created)
            }

            authenticate("basic-auth-all") {

                get("/by-id") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    call.respond(HttpStatusCode.OK, identityService.findById(identityId))
                }

                put("/update") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    identityService.update(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.delete(identityId)
                    call.respond(HttpStatusCode.OK)
                }

                put("/change-password") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    identityService.changePassword(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }

            authenticate("basic-auth-admin") {

                get {
                    call.respond(HttpStatusCode.OK, identityService.findAll())
                }

                put("/change-status") {
                    val identityRequest = call.receive(IdentityRequest::class)
                    identityService.changeStatus(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}