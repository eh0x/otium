package app.otium.data.local.row

/** Room projection for one series and the owner's progress through it. */
data class SeriesProgressRow(val name: String, val books: Int, val read: Int, val fraction: Double)
