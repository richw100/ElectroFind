package com.richwatson.electrofind.api.models

import com.google.gson.annotations.SerializedName

data class OcmPoi(
    @SerializedName("ID") val id: Long,
    @SerializedName("UUID") val uuid: String?,
    @SerializedName("AddressInfo") val addressInfo: OcmAddressInfo?,
    @SerializedName("Connections") val connections: List<OcmConnection>?,
    @SerializedName("OperatorInfo") val operatorInfo: OcmOperatorInfo?,
    @SerializedName("UsageCost") val usageCost: String?,
    @SerializedName("StatusType") val statusType: OcmStatusType?
)

data class OcmAddressInfo(
    @SerializedName("Title") val title: String?,
    @SerializedName("AddressLine1") val addressLine1: String?,
    @SerializedName("Town") val town: String?,
    @SerializedName("Postcode") val postcode: String?,
    @SerializedName("Latitude") val latitude: Double,
    @SerializedName("Longitude") val longitude: Double
)

data class OcmConnection(
    @SerializedName("ConnectionType") val connectionType: OcmConnectionType?,
    @SerializedName("PowerKW") val powerKW: Double?,
    @SerializedName("StatusType") val statusType: OcmStatusType?
)

data class OcmConnectionType(@SerializedName("Title") val title: String?)

data class OcmOperatorInfo(@SerializedName("Title") val title: String?)

data class OcmStatusType(@SerializedName("IsOperational") val isOperational: Boolean?)
