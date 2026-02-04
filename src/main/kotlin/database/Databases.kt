package org.burgas.database

import kotlinx.serialization.Serializable
import org.burgas.serialization.UUIDSerializer
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@Suppress("unused")
enum class Authority {
    ADMIN, USER
}

object IdentityTable : UUIDTable(name = "identity") {
    val authority = enumerationByName<Authority>("authority", 100)
    val username = varchar("username", 100).uniqueIndex()
    val password = varchar("password", 100)
    val email = varchar("email", 100).uniqueIndex()
    val enabled = bool("enabled").default(true)
    val firstname = varchar("firstname", 100)
    val lastname = varchar("lastname", 100)
    val patronymic = varchar("patronymic", 100)
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
    val brand = varchar("brand", 100)
    val model = varchar("model", 100).uniqueIndex()
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

    var address by AddressEntity referencedOn ParkingTable.addressId
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

@OptIn(ExperimentalUuidApi::class)
fun configureDatabases() {
    transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
        SchemaUtils.create(IdentityTable, CarTable, AddressTable, ParkingTable, ParkingCarTable)

        val identityId = Uuid.parse("c009193f-54bc-4360-924c-893c2856f201").toJavaUuid()
        val identityEntity = IdentityEntity.findById(identityId) ?: IdentityEntity.new(identityId) {
            this.authority = Authority.ADMIN
            this.username = "burgasvv"
            this.password = BCrypt.hashpw("burgasvv", BCrypt.gensalt())
            this.email = "burgasvv@gmail.com"
            this.enabled = true
            this.firstname = "Бургас"
            this.lastname = "Вячеслав"
            this.patronymic = "Васильевич"
        }

        val carId = Uuid.parse("a16206b2-d498-4878-92d5-607495becf91").toJavaUuid()
        val carEntity = CarEntity.findById(carId) ?: CarEntity.new(carId) {
            this.brand = "BMW"
            this.model = "M5"
            this.description = "Описание и характеристики автомобиля BMW M5"
            this.identity = identityEntity
        }

        val addressId = Uuid.parse("50cbc8c9-f8c5-45fe-8e46-c36406a16066").toJavaUuid()
        val addressEntity = AddressEntity.findById(addressId) ?: AddressEntity.new(addressId) {
            this.city = "Новосибирск"
            this.street = "Иванова"
            this.house = "56a"
        }

        val parkingId = Uuid.parse("727a39a2-3ce0-4d4c-b489-bc8a9c1a2827").toJavaUuid()
        val parkingEntity = ParkingEntity.findById(parkingId) ?: ParkingEntity.new(parkingId) {
            this.address = addressEntity
            this.price = 2300.50
        }

        if (!parkingEntity.cars.contains(carEntity)) {
            parkingEntity.cars = SizedCollection(parkingEntity.cars + carEntity)
        }
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
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val identityId: UUID? = null
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
    val parking: List<ParkingWithAddressResponse>? = null
)

@Serializable
data class AddressRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val city: String? = null,
    val street: String? = null,
    val house: String? = null
)

@Serializable
data class AddressShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val city: String? = null,
    val street: String? = null,
    val house: String? = null
)

@Serializable
data class AddressFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val city: String? = null,
    val street: String? = null,
    val house: String? = null,
    val parking: ParkingShortResponse? = null
)

@Serializable
data class ParkingRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val address: AddressRequest? = null,
    val price: Double? = null
)

@Serializable
data class ParkingShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val price: Double? = null
)

@Serializable
data class ParkingWithAddressResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val address: AddressShortResponse? = null,
    val price: Double? = null
)

@Serializable
data class ParkingFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val address: AddressShortResponse? = null,
    val price: Double? = null,
    val cars: List<CarShortResponse>? = null
)

@Serializable
data class ParkingCarRequest(
    @Serializable(with = UUIDSerializer::class)
    val parkingId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val carId: UUID
)