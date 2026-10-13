package app.otium.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book",
    indices = [Index(value = ["isbn13"], unique = true)],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Unique when present. SQLite allows many NULLs in a unique index. */
    val isbn13: String?,
    val isbn10: String?,
    val title: String,
    val subtitle: String?,
    /** Display string. The normalized authors live in the author and book_author tables. */
    val authors: String,
    val publisher: String?,
    val publishedYear: Int?,
    val language: String?,
    val description: String?,
    val totalPages: Int,
    val currentPage: Int,
    val coverPath: String?,
    val coverSourceUrl: String?,
    val source: String,
    val addedAt: Long,
    val updatedAt: Long,
    val userEdited: Boolean,
    val needsLookup: Boolean,
    /** Added in schema version 2, so the columns sit after the version 1 ones. */
    val series: String? = null,
    val seriesIndex: Double? = null,
)
