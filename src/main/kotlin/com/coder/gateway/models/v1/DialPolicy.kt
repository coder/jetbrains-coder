package com.coder.gateway.models.v1

import com.google.gson.annotations.SerializedName

/**
 * DialPolicy a single network + address + port combinations that a connection is permitted to use.
 * @param network if empty it applies to all networks
 * @param host the IP or hostname of the address. It should not contain the port. If empty, it applies to all hosts. "localhost", [::1], and any IPv4
 * address under "127.0.0.0/8" can be used interchangeably.
 * @param port it applies to all ports if value is 0
 */
data class DialPolicy(@SerializedName("network") val network: String?, @SerializedName("address") val host: String?, @SerializedName("port") val port: UShort?)
