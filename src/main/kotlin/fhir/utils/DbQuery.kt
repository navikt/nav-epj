package no.nav.helse.fhir.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

suspend fun <T> dbQuery(statement: suspend R2dbcTransaction.() -> T): T =
  withContext(Dispatchers.IO) { suspendTransaction { statement() } }
