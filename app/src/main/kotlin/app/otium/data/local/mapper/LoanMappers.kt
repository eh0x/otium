package app.otium.data.local.mapper

import app.otium.data.cover.CoverFiles
import app.otium.data.local.entity.LoanEntity
import app.otium.data.local.row.OpenLoanRow
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.OpenLoan

/** A direction this app does not know reads as lent, as an unknown note kind reads as a note. */
internal fun LoanEntity.toDomain() = Loan(
    id = id,
    bookId = bookId,
    counterparty = counterparty,
    direction = LoanDirection.entries.firstOrNull { it.name == direction } ?: LoanDirection.LENT,
    lentOn = lentOn,
    remindOn = remindOn,
    returnedOn = returnedOn,
)

internal fun Loan.toEntity() = LoanEntity(
    id = id,
    bookId = bookId,
    counterparty = counterparty,
    direction = direction.name,
    lentOn = lentOn,
    remindOn = remindOn,
    returnedOn = returnedOn,
)

internal fun OpenLoanRow.toDomain(covers: CoverFiles) = OpenLoan(
    loan = loan.toDomain(),
    title = title,
    authors = authors,
    coverPath = covers.pathOf(coverPath),
    coverSourceUrl = coverSourceUrl,
)
