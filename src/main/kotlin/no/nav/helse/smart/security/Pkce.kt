package no.nav.helse.smart.security

import java.security.MessageDigest
import java.util.Base64

/**
 * PKCE S256 code challenge (RFC 7636): `BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))`, unpadded.
 *
 * Only `/oidc/token` calls this, recomputing the challenge from the app's `code_verifier` and
 * comparing it to the `code_challenge` stored at `/oidc/authorize`. That comparison is what makes
 * PKCE effective.
 */
fun codeChallengeS256(codeVerifier: String): String {
  val digest =
    MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
  return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}
