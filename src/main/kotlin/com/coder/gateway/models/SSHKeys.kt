package com.coder.gateway.models

import com.google.gson.annotations.SerializedName

data class SSHKeys(@SerializedName("public_key") val publicKey: String, @SerializedName("private_key") val privateKey: String)