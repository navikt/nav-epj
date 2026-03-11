package no.nav.tsm.frontend.apps

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object AppTable : Table("apps") {
    val id = javaUUID("id")
    val text = text("name")
    val launchUrl = text("launch_url")
}
