package app.otium.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// Join tables keep a position so labels come back in the order the user entered them.

@Entity(
    tableName = "book_author",
    primaryKeys = ["bookId", "authorId"],
    foreignKeys = [
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AuthorEntity::class, ["id"], ["authorId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("authorId")],
)
data class BookAuthorCrossRef(val bookId: Long, val authorId: Long, val position: Int)

@Entity(
    tableName = "book_category",
    primaryKeys = ["bookId", "categoryId"],
    foreignKeys = [
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId")],
)
data class BookCategoryCrossRef(val bookId: Long, val categoryId: Long, val position: Int)

@Entity(
    tableName = "book_tag",
    primaryKeys = ["bookId", "tagId"],
    foreignKeys = [
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class BookTagCrossRef(val bookId: Long, val tagId: Long, val position: Int)
