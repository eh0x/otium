package app.otium.domain.model

/** Derived from current and total pages, never stored (see DeriveStatus). */
enum class BookStatus { NOT_STARTED, IN_PROGRESS, READ }

enum class BookSource { GOOGLE, OPEN_LIBRARY, MANUAL }
