package org.burgas.service

import org.burgas.database.ParkingEntity
import org.burgas.database.ParkingFullResponse
import org.burgas.database.ParkingShortResponse

fun ParkingEntity.toParkingShortResponse(): ParkingShortResponse {
    return ParkingShortResponse(
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