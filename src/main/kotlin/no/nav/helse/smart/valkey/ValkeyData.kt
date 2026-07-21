package no.nav.helse.smart.valkey

import kotlinx.serialization.Serializable

@Serializable data class LaunchContext(val patientId: String?, val encounterId: String?)

@Serializable
data class AuthCodeContext(
  val username: String,
  val redirectUrl: String,
  val launch: LaunchContext,
  val hpr: String?,
  val scope: String,
  val clientId: String,
  val codeChallenge: String,
)
