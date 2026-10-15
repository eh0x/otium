package app.otium.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.otium.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /** Reading order: by page, then oldest first, notes without a page last. */
    @Query("SELECT * FROM note WHERE bookId = :bookId ORDER BY page IS NULL, page, createdAt, id")
    fun observeNotes(bookId: Long): Flow<List<NoteEntity>>

    /** Fails with a constraint error when the book does not exist. */
    @Insert
    suspend fun insert(note: NoteEntity): Long

    /** The number of rows changed: 0 when the note does not exist. */
    @Query(
        "UPDATE note SET kind = :kind, text = :text, page = :page, updatedAt = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun updateContent(
        id: Long,
        kind: String,
        text: String,
        page: Int?,
        updatedAt: Long,
    ): Int

    /** The number of rows deleted: 0 when the note does not exist. */
    @Query("DELETE FROM note WHERE id = :id")
    suspend fun delete(id: Long): Int
}
