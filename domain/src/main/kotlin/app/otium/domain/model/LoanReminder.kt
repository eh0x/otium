package app.otium.domain.model

/** What the reminder of an open loan says: which book, who has it (or lent it), and since when. */
data class LoanReminder(
    val loanId: Long,
    val bookTitle: String,
    val counterparty: String,
    val direction: LoanDirection,
    /** The day the book went, as an epoch day. */
    val lentOn: Long,
)
