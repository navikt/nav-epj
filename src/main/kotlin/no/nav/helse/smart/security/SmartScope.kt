package no.nav.helse.smart.security

enum class ScopeContext(val prefix: String) {
  PATIENT("patient"),
  USER("user"),
  SYSTEM("system");

  companion object {
    fun from(s: String) = entries.find { it.prefix == s }
  }
}

// Do not reorder. Spec requirement
enum class Interaction(val code: Char) {
  CREATE('c'),
  READ('r'),
  UPDATE('u'),
  DELETE('d'),
  SEARCH('s'),
}

private val OTHER_SCOPES =
  setOf(
    "openid",
    "fhirUser",
    "profile",
    "launch",
    "launch/patient",
    "launch/encounter",
    "offline_access",
    "online_access",
  )

sealed interface SmartScope {
  data class Fhir(
    val context: ScopeContext,
    val resourceType: String, // (Patient | Encounter | DocumentReference | *)
    val interactions: Set<Interaction>,
  ) : SmartScope {
    override fun toString() =
      "${context.prefix}/$resourceType." +
        Interaction.entries.filter { it in interactions }.joinToString("") { "${it.code}" }
  }

  data class Other(val value: String) : SmartScope {
    override fun toString() = value
  }
}

fun parseScopes(scopes: String): Set<SmartScope> =
  scopes.split(" ").filter { it.isNotBlank() }.mapNotNullTo(mutableSetOf()) { parseScope(it) }

internal fun parseScope(s: String): SmartScope? {
  val slash = s.indexOf('/')
  val context = if (slash < 0) null else ScopeContext.from(s.substring(0, slash))
  if (context == null) return SmartScope.Other(s) // openid, launch, launch/patient, etc

  val rest = s.substring(slash + 1)
  val dot = rest.lastIndexOf('.')
  if (dot < 0) return null
  val resource = rest.substring(0, dot)
  if (resource.isEmpty()) return null
  val interactions = parseInteractions(rest.substring(dot + 1)) ?: return null
  return SmartScope.Fhir(context, resource, interactions)
}

fun parseRegisteredScopes(scopes: List<String>): Set<SmartScope> =
  scopes.mapTo(mutableSetOf()) { s ->
    val parsed = parseScope(s) ?: throw IllegalArgumentException("unparseable scope $s")
    require(parsed !is SmartScope.Other || parsed.value in OTHER_SCOPES) { "unknown scope $s" }
    parsed
  }

private fun parseInteractions(suffix: String): Set<Interaction>? =
  when (suffix) {
    "read" -> setOf(Interaction.READ, Interaction.SEARCH) // v1 .read = v2 .rs
    "write" ->
      setOf(Interaction.CREATE, Interaction.UPDATE, Interaction.DELETE) // v1 .write = v2 .cud
    "*" -> Interaction.entries.toSet() // v1 .* = v2 .cruds
    else -> parseV2Suffix(suffix)
  }

private fun parseV2Suffix(suffix: String): Set<Interaction>? {
  if (suffix.isEmpty()) return null
  val result = LinkedHashSet<Interaction>()
  var cursor = 0
  for (c in suffix) {
    val i = Interaction.entries.indexOfFirst { it.code == c }
    if (i < 0 || i < cursor) return null
    result += Interaction.entries[i]
    cursor = i + 1
  }
  return result
}

fun grantScopes(requested: Set<SmartScope>, allowed: Set<SmartScope>): Set<SmartScope> {
  val grantedOther = requested.filterIsInstance<SmartScope.Other>().filter { it in allowed }

  val allowedFhir = allowed.filterIsInstance<SmartScope.Fhir>()
  val grantedFhir =
    requested.filterIsInstance<SmartScope.Fhir>().flatMap { r ->
      allowedFhir.filter { it.context == r.context }.mapNotNull { a -> grantFhirScope(r, a) }
    }

  return (grantedOther + grantedFhir).toSet()
}

private fun grantFhirScope(requested: SmartScope.Fhir, allowed: SmartScope.Fhir): SmartScope.Fhir? {
  val resource =
    when {
      requested.resourceType == "*" -> allowed.resourceType // narrow request to what's allowed
      allowed.resourceType == "*" -> requested.resourceType // allowed covers anything
      allowed.resourceType == requested.resourceType -> requested.resourceType
      else -> return null
    }
  val interactions = requested.interactions intersect allowed.interactions
  if (interactions.isEmpty()) return null
  return SmartScope.Fhir(requested.context, resource, interactions)
}

fun Set<SmartScope>.serialize() = joinToString(" ")
