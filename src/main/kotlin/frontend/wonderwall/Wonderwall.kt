package no.nav.tsm.frontend.wonderwall

data class User(
    val hpr: String,
)

sealed interface Wonderwall {
    fun user(): User
}

class CloudWonderwall : Wonderwall {
    override fun user(): User {
        return User(
            hpr = "12345678901",
        )
    }
}

class LocalWonderwall : Wonderwall {
    override fun user(): User {
        return User(
            hpr = "12345678901",
        )
    }
}