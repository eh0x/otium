package app.otium.domain.port

/**
 * Photos the camera app took for a cover. They are the user's own pictures and only needed until
 * the cover store has copied one or the user has left the screen, so they are not kept.
 */
fun interface CapturedPhotos {
    /**
     * Deletes the photos at [uris] that the camera took for the app, in the background. Any other
     * location, such as a picture picked from the gallery, is left alone.
     */
    fun discard(uris: Collection<String>)
}
