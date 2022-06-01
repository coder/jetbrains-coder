package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName

data class AgentGitSSHKeys(@SerializedName("public_key") val publicKey: String, @SerializedName("private_key") val privateKey: String)