package no.nav.helse.auth

import kotlinx.serialization.Serializable

@Serializable data class UserInfo(val id: String, val name: String)
