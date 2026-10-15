package app.otium.data.local.mapper

import app.otium.data.local.entity.NoteEntity
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind

internal fun NoteEntity.toDomain() = Note(
    id = id,
    bookId = bookId,
    kind = NoteKind.entries.firstOrNull { it.name == kind } ?: NoteKind.NOTE,
    text = text,
    page = page,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Note.toEntity() = NoteEntity(
    id = id,
    bookId = bookId,
    kind = kind.name,
    text = text,
    page = page,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
