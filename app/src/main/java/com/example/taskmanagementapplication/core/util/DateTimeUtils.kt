package com.example.taskmanagementapplication.core.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Centralized Date & Time Utility for the Field Service Application.
 * Authoritatively converts all UTC / ISO-8601 timestamps from Supabase into
 * Indian Local Time (Asia/Kolkata).
 *
 * Guarantees:
 * - Timezone: Asia/Kolkata (+05:30)
 * - Preferred format: "15 Sep 2026, 04:08 PM" (dd MMM yyyy, hh:mm a)
 * - Strictly NO "+00:00", "UTC", "Z", "GMT", or "IST" in UI output.
 */
object DateTimeUtils {

    val INDIA_ZONE_ID: ZoneId = ZoneId.of("Asia/Kolkata")
    val INDIA_TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    const val PATTERN_STANDARD = "dd MMM yyyy, hh:mm a"
    const val PATTERN_DATE_ONLY = "dd MMM yyyy"
    const val PATTERN_TIME_ONLY = "hh:mm a"

    /**
     * Formats any server / database timestamp to Indian Local Time: "15 Sep 2026, 04:08 PM".
     */
    fun formatToIndiaTime(rawTimestamp: String?): String {
        if (rawTimestamp.isNullOrBlank()) return "-"
        val trimmed = rawTimestamp.trim()

        // 1. Try Java 8 Time APIs (handles ISO-8601 with offset, Z, subseconds)
        try {
            val zonedDateTime = parseToZonedDateTime(trimmed)
            if (zonedDateTime != null) {
                val inIndia = zonedDateTime.withZoneSameInstant(INDIA_ZONE_ID)
                return inIndia.format(DateTimeFormatter.ofPattern(PATTERN_STANDARD, Locale.US))
            }
        } catch (_: Throwable) {}

        // 2. Fallback to SimpleDateFormat
        return parseWithSimpleDateFormat(trimmed, PATTERN_STANDARD) ?: cleanRawString(trimmed)
    }

    /**
     * Formats date portion only in Indian Local Time: "15 Sep 2026".
     */
    fun formatToIndiaDate(rawTimestamp: String?): String {
        if (rawTimestamp.isNullOrBlank()) return "-"
        val trimmed = rawTimestamp.trim()

        // Pure YYYY-MM-DD
        if (trimmed.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
            try {
                val localDate = LocalDate.parse(trimmed)
                return localDate.format(DateTimeFormatter.ofPattern(PATTERN_DATE_ONLY, Locale.US))
            } catch (_: Throwable) {}
        }

        try {
            val zonedDateTime = parseToZonedDateTime(trimmed)
            if (zonedDateTime != null) {
                val inIndia = zonedDateTime.withZoneSameInstant(INDIA_ZONE_ID)
                return inIndia.format(DateTimeFormatter.ofPattern(PATTERN_DATE_ONLY, Locale.US))
            }
        } catch (_: Throwable) {}

        return parseWithSimpleDateFormat(trimmed, PATTERN_DATE_ONLY) ?: cleanRawString(trimmed)
    }

    /**
     * Formats time portion only in Indian Local Time: "04:08 PM".
     */
    fun formatToIndiaTimeOnly(rawTimestamp: String?): String {
        if (rawTimestamp.isNullOrBlank()) return "-"
        val trimmed = rawTimestamp.trim()

        try {
            val zonedDateTime = parseToZonedDateTime(trimmed)
            if (zonedDateTime != null) {
                val inIndia = zonedDateTime.withZoneSameInstant(INDIA_ZONE_ID)
                return inIndia.format(DateTimeFormatter.ofPattern(PATTERN_TIME_ONLY, Locale.US))
            }
        } catch (_: Throwable) {}

        return parseWithSimpleDateFormat(trimmed, PATTERN_TIME_ONLY) ?: cleanRawString(trimmed)
    }

    /**
     * Current Indian date and time: e.g. "15 Sep 2026, 04:08 PM"
     */
    fun currentIndiaFormatted(): String {
        val now = ZonedDateTime.now(INDIA_ZONE_ID)
        return now.format(DateTimeFormatter.ofPattern(PATTERN_STANDARD, Locale.US))
    }

    /**
     * Current Indian date only: e.g. "15 Sep 2026"
     */
    fun currentIndiaDateFormatted(): String {
        val now = ZonedDateTime.now(INDIA_ZONE_ID)
        return now.format(DateTimeFormatter.ofPattern(PATTERN_DATE_ONLY, Locale.US))
    }

    /**
     * Calculates and formats actual field duration between two timestamps.
     * Output examples: "57s", "2m 55s", "1h 14m"
     */
    fun formatFieldDuration(startTime: String?, endTime: String?): String {
        if (startTime.isNullOrBlank() || endTime.isNullOrBlank()) return "00:00"
        val t1 = parseToMillis(startTime)
        val t2 = parseToMillis(endTime)
        if (t1 == null || t2 == null || t2 < t1) return "00:00"

        val diffSec = (t2 - t1) / 1000
        val hours = diffSec / 3600
        val mins = (diffSec % 3600) / 60
        val secs = diffSec % 60

        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 && secs > 0 -> "${mins}m ${secs}s"
            mins > 0 -> "${mins}m"
            else -> "${secs}s"
        }
    }

    private fun parseToZonedDateTime(input: String): ZonedDateTime? {
        // 1. Try ISO instant or zoned
        try {
            return ZonedDateTime.parse(input)
        } catch (_: Throwable) {}

        // 2. Try Instant (e.g. 2026-09-15T10:36:50.919155Z)
        try {
            val instant = Instant.parse(input)
            return instant.atZone(ZoneId.of("UTC"))
        } catch (_: Throwable) {}

        // 3. Try ISO LocalDateTime without offset (assume UTC if server generated)
        try {
            val localDt = LocalDateTime.parse(input)
            return localDt.atZone(ZoneId.of("UTC"))
        } catch (_: Throwable) {}

        return null
    }

    private fun parseToMillis(input: String): Long? {
        // Direct epoch millis
        input.toLongOrNull()?.let { return it }

        parseToZonedDateTime(input)?.let {
            return it.toInstant().toEpochMilli()
        }

        // Try standard SimpleDateFormat
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(input)
                if (date != null) return date.time
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun parseWithSimpleDateFormat(input: String, pattern: String): String? {
        val inFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd HH:mm:ss.SSSSSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )

        for (inFmt in inFormats) {
            try {
                val inSdf = SimpleDateFormat(inFmt, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = inSdf.parse(input)
                if (date != null) {
                    val outSdf = SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = INDIA_TIMEZONE
                    }
                    return outSdf.format(date)
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun cleanRawString(input: String): String {
        // Strip any trailing UTC offsets or Z to avoid showing +00:00 or Z
        return input.replace(Regex("[+-]\\d{2}:?\\d{2}$"), "")
            .replace(Regex("Z$"), "")
            .replace("T", " ")
    }
}
