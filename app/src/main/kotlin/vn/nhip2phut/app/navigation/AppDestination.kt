package vn.nhip2phut.app.navigation

sealed interface AppDestination {
    val route: String

    data object Foundation : AppDestination {
        override val route: String = "foundation"
    }

    companion object {
        val Start: AppDestination = Foundation
        val Entries: List<AppDestination> = listOf(Foundation)
    }
}
