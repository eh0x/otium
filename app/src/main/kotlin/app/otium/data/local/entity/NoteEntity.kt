package app.otium.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Added in schema version 3. A book's notes are deleted with it. */
@Entity(
    tableName = "note",
    foreignKeys = [
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    /** The name of a [app.otium.domain.model.NoteKind]. */
    val kind: String,
    val text: String,
    val page: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)
