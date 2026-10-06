package app.otium.domain.model

/** Which cover a saved book should end up with, as chosen on the confirm screen. */
sealed interface CoverChoice {
    /** No cover; an edited book loses the one it had. */
    data object None : CoverChoice

    /** A cover offered by a metadata service. It is downloaded once, when the book is saved. */
    data class Remote(val url: String) : CoverChoice

    /**
     * A photo the user picked from the gallery or took with the camera, at [uri] on the device.
     * It is copied into the cover store when the book is saved.
     */
    data class Picked(val uri: String) : CoverChoice

    /** Editing only: keep the cover the book already has. Adding treats it as [None]. */
    data object Unchanged : CoverChoice
}
