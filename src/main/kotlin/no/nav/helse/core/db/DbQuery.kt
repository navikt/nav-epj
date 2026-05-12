package no.nav.helse.core.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseConnection {
  lateinit var database: Database
}

suspend fun <T> dbQuery(statement: () -> T): T =
  withContext(Dispatchers.IO) { transaction(DatabaseConnection.database) { statement() } }
