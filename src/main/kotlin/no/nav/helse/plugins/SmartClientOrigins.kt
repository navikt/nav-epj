package no.nav.helse.plugins

import java.net.URI

internal fun originOf(uri: String): String =
    URI(uri).let {
        val host = requireNotNull(it.host) { "No host in SMART client uri $uri" }
        "${it.scheme}://$host${if (it.port == -1) "" else ":${it.port}"}"
    }
