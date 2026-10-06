package app.otium.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. The detail screen reads its arguments from [DetailRoute]. */
@Serializable
data object LibraryRoute

@Serializable
data class DetailRoute(val bookId: Long)

/** Stub until the add flow is built. */
@Serializable
data object AddRoute

/** Debug builds only: the theme catalog. */
@Serializable
data object CatalogRoute
