package no.nav.tsm.frontend.apps

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class App(
    val id: UUID,
    val name: String,
    val launchUrl: String
)

class AppsRepo {
    fun getAllApps(): List<App> =
        transaction {
            AppTable.selectAll()
                .map { it.toApp() }
        }

    fun getAppById(id: UUID): App? =
        transaction {
            AppTable.selectAll()
                .where(AppTable.id eq id)
                .map { it.toApp() }
                .singleOrNull()
        }

    private fun ResultRow.toApp() = App(
        id = this[AppTable.id],
        name = this[AppTable.text],
        launchUrl = this[AppTable.launchUrl]
    )
}