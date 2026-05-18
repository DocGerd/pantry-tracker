package de.docgerdsoft.pantrytracker.util

import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Test helper that captures `java.util.logging` records emitted by a named
 * logger. Use with `.use { ... }` so the handler is detached even if the test
 * body throws.
 *
 * The project's production loggers all use `Logger.getLogger("ClassName")`
 * (see `OffApiClient.kt`, `ProductRepositoryImpl.kt`, `ScanViewModel.kt`),
 * so passing the simple class name as `loggerName` matches everywhere.
 *
 * `messages()` concatenates the formatted message and the throwable message
 * (when present) so redaction assertions catch both:
 * `assertFalse(capture.messages().any { it.contains("5449000000996") })`.
 */
class JulLogCapture(loggerName: String) : AutoCloseable {
    private val logger: Logger = Logger.getLogger(loggerName)
    private val records: MutableList<LogRecord> = mutableListOf()
    private val originalLevel: Level? = logger.level

    private val handler: Handler = object : Handler() {
        override fun publish(record: LogRecord?) {
            if (record != null) records += record
        }
        override fun flush() {}
        override fun close() {}
    }

    init {
        // Force the handler to see every level the production code might emit;
        // JUL's default logger level is INHERIT, which can mute INFO depending
        // on the parent chain.
        logger.level = Level.ALL
        logger.addHandler(handler)
    }

    /** Concatenated message + throwable-message strings for every captured record. */
    fun messages(): List<String> = records.map { rec ->
        buildString {
            append(rec.message ?: "")
            rec.thrown?.message?.let { append(" ").append(it) }
        }
    }

    /** All captured records, in emission order. */
    fun records(): List<LogRecord> = records.toList()

    override fun close() {
        logger.removeHandler(handler)
        logger.level = originalLevel
    }
}
