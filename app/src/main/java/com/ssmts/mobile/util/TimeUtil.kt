package com.ssmts.mobile.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ISO-8601 parsing/formatting helpers for API date strings. */
object TimeUtil {

    private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d yyyy", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)
    private val isoDateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)

    /** Parses "2026-09-27T00:30:00Z" or without offset (treated as UTC). */
    fun parseUtc(iso: String?): ZonedDateTime? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso).atZone(ZoneId.systemDefault())
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(iso).atZone(ZoneId.of("UTC"))
                    .withZoneSameInstant(ZoneId.systemDefault())
            } catch (_: Exception) {
                null
            }
        }
    }

    fun date(iso: String?): String = parseUtc(iso)?.format(dateFmt) ?: "—"

    fun time(iso: String?): String = parseUtc(iso)?.format(timeFmt) ?: "—"

    fun dateTime(iso: String?): String = parseUtc(iso)?.format(dateTimeFmt) ?: "—"

    /** e.g. "Sat, Sep 27 2026 · 8:00 AM – 9:00 AM" */
    fun slotRange(startIso: String?, endIso: String?): String {
        val start = parseUtc(startIso) ?: return "—"
        val end = parseUtc(endIso)
        val endPart = end?.format(timeFmt)?.let { " – $it" } ?: ""
        return "${start.format(dateFmt)} · ${start.format(timeFmt)}$endPart"
    }

    fun todayIsoDate(): String = LocalDate.now().format(isoDateFmt)

    fun isFuture(iso: String?): Boolean =
        parseUtc(iso)?.toInstant()?.isAfter(Instant.now()) ?: false
}
