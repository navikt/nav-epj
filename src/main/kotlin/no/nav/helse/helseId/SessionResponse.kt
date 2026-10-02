package no.nav.helse.helseId

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SessionResponse(
    val idp: String,
    val claims: Map<String, String>,
    val issuedAt: Instant? = null,
    val expiresAt: Instant? = null,
)
