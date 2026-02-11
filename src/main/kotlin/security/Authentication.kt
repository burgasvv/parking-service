package org.burgas.security

import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.burgas.database.Authority
import org.burgas.database.DatabaseFactory
import org.burgas.database.IdentityEntity
import org.burgas.database.IdentityTable
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

fun Application.configureAuthentication() {

    authentication {

        basic(name = "basic-auth-all") {
            validate { credentials ->
                val identityEntity = transaction(db = DatabaseFactory.postgres) {
                    IdentityEntity.find { IdentityTable.email eq credentials.name }.singleOrNull()
                }
                if (
                    identityEntity != null && identityEntity.enabled &&
                    BCrypt.checkpw(credentials.password, identityEntity.password)
                ) {
                    UserPasswordCredential(credentials.name, credentials.password)

                } else {
                    null
                }
            }
        }

        basic(name = "basic-auth-admin") {
            validate { credentials ->
                val identityEntity = transaction(db = DatabaseFactory.postgres) {
                    IdentityEntity.find { IdentityTable.email eq credentials.name }.singleOrNull()
                }
                if (
                    identityEntity != null && identityEntity.enabled &&
                    BCrypt.checkpw(credentials.password, identityEntity.password) &&
                    identityEntity.authority == Authority.ADMIN
                ) {
                    UserPasswordCredential(credentials.name, credentials.password)

                } else {
                    null
                }
            }
        }
    }
}