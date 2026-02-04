package org.burgas.service

import org.burgas.database.AddressEntity
import org.burgas.database.AddressFullResponse
import org.burgas.database.AddressShortResponse

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