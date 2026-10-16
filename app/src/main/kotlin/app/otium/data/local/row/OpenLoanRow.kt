package app.otium.data.local.row

import androidx.room.Embedded
import app.otium.data.local.entity.LoanEntity

/** Room projection of an open loan with the book it is for. Mapped by the repository. */
data class OpenLoanRow(
    @Embedded val loan: LoanEntity,
    val title: String,
    val authors: String,
    val coverPath: String?,
    val coverSourceUrl: String?,
)
