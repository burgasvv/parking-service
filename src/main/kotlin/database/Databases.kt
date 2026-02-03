package org.burgas.database

import kotlinx.serialization.Serializable
import org.burgas.serialization.UUIDSerializer
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

@Suppress("unused")
enum class Authority {
    ADMIN, USER
}

object IdentityTable : UUIDTable(name = "identity") {
    val authority = enumerationByName<Authority>("authority", 50)
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 50)
    val email = varchar("email", 100).uniqueIndex()
    val enabled = bool("enabled").default(true)
    val firstname = varchar("firstname", 50)
    val lastname = varchar("lastname", 50)
    val patronymic = varchar("patronymic", 50)
}

class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, IdentityEntity>(IdentityTable)

    var authority by IdentityTable.authority
    var username by IdentityTable.username
    var password by IdentityTable.password
    var email by IdentityTable.email
    var enabled by IdentityTable.enabled
    var firstname by IdentityTable.firstname
    var lastname by IdentityTable.lastname
    var patronymic by IdentityTable.patronymic
    val cars by CarEntity referrersOn CarTable.identityId
}

object CarTable : UUIDTable(name = "car") {
    val brand = varchar("brand", 50)
    val model = varchar("model", 50).uniqueIndex()
    val description = text("description").uniqueIndex()
    val identityId = reference(
        "identity_id", IdentityTable.id,
        onUpdate = ReferenceOption.CASCADE,
        onDelete = ReferenceOption.CASCADE
    )
}

class CarEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, CarEntity>(CarTable)

    var brand by CarTable.brand
    var model by CarTable.model
    var description by CarTable.description
    var identity by IdentityEntity referencedOn CarTable.identityId
    var parking by ParkingEntity via ParkingCarTable
}

object AddressTable : UUIDTable(name = "address") {
    val city = varchar("city", 100)
    val street = varchar("street", 100)
    val house = varchar("house", 100)
}

class AddressEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, AddressEntity>(AddressTable)

    var city by AddressTable.city
    var street by AddressTable.street
    var house by AddressTable.house
    val parking by ParkingEntity optionalBackReferencedOn ParkingTable.addressId
}

object ParkingTable : UUIDTable(name = "parking") {
    val addressId = reference(
        name = "address_id", refColumn = AddressTable.id,
        onDelete = ReferenceOption.SET_NULL,
        onUpdate = ReferenceOption.CASCADE
    ).uniqueIndex()
    val price = double("price").default(0.0).check { price -> price.greaterEq(0.0) }
}

class ParkingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, ParkingEntity>(ParkingTable)

    val address by AddressEntity referencedOn ParkingTable.addressId
    var price by ParkingTable.price
    var cars by CarEntity via ParkingCarTable
}

object ParkingCarTable : Table(name = "parking_car") {
    val parkingId = reference(
        name = "parking_id", refColumn = ParkingTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    val carId = reference(
        name = "car_id", refColumn = CarTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(parkingId, carId))
}

fun configureDatabases() {
    transaction(db = DatabaseFactory.postgres) {
        SchemaUtils.create(IdentityTable, CarTable, AddressTable, ParkingTable, ParkingCarTable)
    }
}

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val authority: Authority? = null,
    val username: String? = null,
    val password: String? = null,
    val email: String? = null,
    val enabled: Boolean? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null
)

@Serializable
data class IdentityShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val username: String? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null
)

@Serializable
data class IdentityFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val username: String? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val cars: List<CarShortResponse>? = null
)

@Serializable
data class CarRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val brand: String? = null,
    val model: String? = null,
    val description: String? = null
)

@Serializable
data class CarShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val brand: String? = null,
    val model: String? = null,
    val description: String? = null,
)

@Serializable
data class CarFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val brand: String? = null,
    val model: String? = null,
    val description: String? = null,
    val identity: IdentityShortResponse? = null,
    val parking: List<ParkingShortResponse>? = null
)

@Serializable
data class ParkingRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val address: String? = null,
    val price: String? = null
)

@Serializable
data class ParkingShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val address: String? = null,
    val price: String? = null
)

@Serializable
data class ParkingFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val address: String? = null,
    val price: String? = null,
    val cars: List<CarShortResponse>? = null
)