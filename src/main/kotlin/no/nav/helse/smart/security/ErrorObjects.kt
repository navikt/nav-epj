package no.nav.helse.smart.security

import com.nimbusds.oauth2.sdk.ErrorObject

fun ErrorObject.withDetail(detail: String): ErrorObject = setDescription("$description: $detail")
