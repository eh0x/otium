package app.otium.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Added in schema version 4. A book's loans are deleted with it. A loan is open while
 * [returnedOn] is null; dates are epoch days.
 */
@Entity(
    tableName = "loan",
    foreignKeys = [
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val counterparty: String,
    /** The name of a [app.otium.domain.model.LoanDirection]. */
    val direction: String,
    val lentOn: Long,
    val remindOn: Long?,
    val returnedOn: Long?,
)
