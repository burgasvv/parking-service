package org.burgas.service

import org.burgas.database.CarEntity
import org.burgas.database.CarFullResponse
import org.burgas.database.CarShortResponse

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
        parking = this.parking.map { it.toParkingShortResponse() }
    )
}