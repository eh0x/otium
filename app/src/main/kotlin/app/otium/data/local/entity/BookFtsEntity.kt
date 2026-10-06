package app.otium.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * Full-text index over the book table. Room keeps it in sync with triggers.
 * unicode61 gives case and diacritic insensitive matching ("garcia" finds "García").
 */
@Fts4(contentEntity = BookEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "book_fts")
data class BookFtsEntity(val title: String, val subtitle: String?, val authors: String)
