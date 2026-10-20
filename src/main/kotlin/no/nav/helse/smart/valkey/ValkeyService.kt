package no.nav.helse.smart.valkey

import glide.api.GlideClient
import glide.api.models.commands.SetOptions
import java.time.Instant
import kotlinx.coroutines.future.await
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private const val AUTH_CODE_TTL_SECONDS = 60L
private const val LAUNCH_CONTEXT_TTL_SECONDS = 300L
private const val ACTIVE_PATIENT_TTL_SECONDS = 8 * 60 * 60L

data class ActivePatient(val patientId: String, val expiresAt: Instant)

class ValkeyService(private val glideClient: GlideClient) {
    private val mapper = jacksonObjectMapper()

    suspend fun saveLaunchContext(launchId: String, context: LaunchContext) {
        glideClient
            .set(
                launchKey(launchId),
                mapper.writeValueAsString(context),
                SetOptions.builder()
                    .expiry(SetOptions.Expiry.Seconds(LAUNCH_CONTEXT_TTL_SECONDS))
                    .build(),
            )
            .await()
    }

    suspend fun getAndDeleteLaunchContext(launchId: String): LaunchContext? =
        glideClient.getdel(launchKey(launchId)).await()?.let { mapper.readValue<LaunchContext>(it) }

    suspend fun setActivePatient(hpr: String, orgnummer: String, patientId: String) {
        glideClient
            .set(
                activePatientKey(hpr, orgnummer),
                patientId,
                SetOptions.builder()
                    .expiry(SetOptions.Expiry.Seconds(ACTIVE_PATIENT_TTL_SECONDS))
                    .build(),
            )
            .await()
    }

    suspend fun clearActivePatient(hpr: String, orgnummer: String) {
        glideClient.del(arrayOf(activePatientKey(hpr, orgnummer))).await()
    }

    suspend fun getActivePatient(hpr: String, orgnummer: String): String? =
        glideClient.get(activePatientKey(hpr, orgnummer)).await()

    suspend fun getActivePatientWithExpiry(hpr: String, orgnummer: String): ActivePatient? {
        val patientId = getActivePatient(hpr, orgnummer) ?: return null
        val remainingSeconds = glideClient.ttl(activePatientKey(hpr, orgnummer)).await()
        if (remainingSeconds <= 0) return null
        return ActivePatient(patientId, Instant.now().plusSeconds(remainingSeconds))
    }

    suspend fun setSessionOrg(hpr: String, orgnummer: String) {
        glideClient
            .set(
                sessionOrgKey(hpr),
                orgnummer,
                SetOptions.builder()
                    .expiry(SetOptions.Expiry.Seconds(ACTIVE_PATIENT_TTL_SECONDS))
                    .build(),
            )
            .await()
    }

    suspend fun getSessionOrg(hpr: String): String? = glideClient.get(sessionOrgKey(hpr)).await()

    suspend fun saveAuthCode(code: String, authCode: AuthCodeContext) {
        glideClient
            .set(
                authCodeKey(code),
                mapper.writeValueAsString(authCode),
                SetOptions.builder()
                    .expiry(SetOptions.Expiry.Seconds(AUTH_CODE_TTL_SECONDS))
                    .build(),
            )
            .await()
    }

    suspend fun getAndDeleteAuthCode(code: String): AuthCodeContext? =
        glideClient.getdel(authCodeKey(code)).await()?.let { mapper.readValue<AuthCodeContext>(it) }

    suspend fun get(key: String): String? = glideClient.get(key).await()

    suspend fun setIfAbsent(key: String, value: String, ttlSeconds: Long): Boolean =
        glideClient
            .set(
                key,
                value,
                SetOptions.builder()
                    .conditionalSet(SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST)
                    .expiry(SetOptions.Expiry.Seconds(ttlSeconds))
                    .build(),
            )
            .await() != null
}

private fun authCodeKey(code: String) = "smart:code:$code"

private fun launchKey(launchId: String) = "smart:launch:$launchId"

private fun activePatientKey(hpr: String, orgnummer: String) =
    "smart:active-patient:$hpr:$orgnummer"

private fun sessionOrgKey(hpr: String) = "epj:session-org:$hpr"
