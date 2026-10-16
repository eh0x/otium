package app.otium.domain.model

/** Which way a book went: out to someone, or in from someone. */
enum class LoanDirection { LENT, BORROWED }

/**
 * A book lent out or borrowed in (the lending ledger). The name is typed by the owner: no contact
 * is read and nothing is sent to the other person. Dates are epoch days, so a date means the same
 * day wherever the phone is. A loan is open until [returnedOn] is set; a returned loan stays as
 * the book's history. The name is personal data about someone else, so it follows the rules of
 * notes: private, and out of the phone only in a backup or an export the owner makes.
 */
data class Loan(
    val id: Long = 0L,
    val bookId: Long,
    val counterparty: String,
    val direction: LoanDirection,
    val lentOn: Long,
    val remindOn: Long? = null,
    val returnedOn: Long? = null,
) {
    val isOpen: Boolean get() = returnedOn == null

    companion object {
        const val MAX_NAME_LENGTH = 100

        /** [name] trimmed, or null when it is blank or longer than [MAX_NAME_LENGTH]. */
        fun cleanName(name: String): String? =
            name.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }

        /**
         * Whether the dates make sense together: a reminder comes after the day the book went,
         * and a return does not come before it.
         */
        fun areDatesValid(lentOn: Long, remindOn: Long?, returnedOn: Long?): Boolean =
            (remindOn == null || remindOn > lentOn) && (returnedOn == null || returnedOn >= lentOn)

        /** A book's loans, newest first. */
        val NEWEST_FIRST: Comparator<Loan> =
            compareByDescending<Loan> { it.lentOn }.thenByDescending { it.id }
    }
}

/** An open loan with enough of its book to list it on the Lent out screen. */
data class OpenLoan(
    val loan: Loan,
    val title: String,
    /** Display string, authors joined for presentation. */
    val authors: String,
    val coverPath: String?,
    val coverSourceUrl: String?,
)
