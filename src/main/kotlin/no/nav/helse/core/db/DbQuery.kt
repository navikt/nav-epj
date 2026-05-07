package no.nav.helse.core.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object DatabaseConnection {
  lateinit var database: R2dbcDatabase
}

suspend fun <T> dbQuery(statement: suspend R2dbcTransaction.() -> T): T =
  withContext(Dispatchers.IO) { suspendTransaction(DatabaseConnection.database) { statement() } }
