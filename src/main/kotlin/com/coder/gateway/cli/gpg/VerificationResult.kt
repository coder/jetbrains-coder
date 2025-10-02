package com.coder.gateway.cli.gpg

/**
 * Result of signature verification
 */
sealed class VerificationResult {
    object Valid : VerificationResult()
    data class Invalid(val reason: String? = null) : VerificationResult()
    data class Failed(val error: Exception) : VerificationResult()
    object SignatureNotFound : VerificationResult()

    fun isValid(): Boolean = this == Valid
    fun isInvalid(): Boolean = this is Invalid
    fun signatureIsNotFound(): Boolean = this == SignatureNotFound
}
