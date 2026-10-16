package app.otium.ui.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** An epoch day as the phone's locale writes a date, such as "3 Oct 2026". */
fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay)
    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
