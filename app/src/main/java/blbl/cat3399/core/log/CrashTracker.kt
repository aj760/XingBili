package blbl.cat3399.core.log

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale

object CrashTracker {
    private const val TAG = "CrashTracker"

    private const val CRASH_FILE_NAME = "last_crash.txt"
    private const val PROMPT_MARKER_FILE_NAME = "last_crash_prompted.txt"

    data class CrashInfo(
        val crashAtMs: Long,
        val threadName: String,
        val stacktrace: String,
    )

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                writeCrashFile(appContext, thread = thread, throwable = throwable)
            }.onFailure {
                Log.w("$TAG", "write crash file failed", it)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun crashFile(context: Context): File {
        return File(AppLog.logDir(context), CRASH_FILE_NAME)
    }

    fun loadLastCrash(context: Context): CrashInfo? {
        val file = crashFile(context)
        if (!file.exists()) return null
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()?.trimEnd().orEmpty()
        if (raw.isBlank()) return null

        val lines = raw.lineSequence().toList()
        val crashAtMs = lines.firstOrNull()?.substringAfter("crashAtMs=", "")?.trim()?.toLongOrNull() ?: return null
        val thread = lines.getOrNull(1)?.substringAfter("thread=", "")?.trim()?.ifBlank { "unknown" } ?: "unknown"
        val stack =
            raw.substringAfter("\n\n", missingDelimiterValue = "").takeIf { it.isNotBlank() }
                ?: raw
        return CrashInfo(crashAtMs = crashAtMs, threadName = thread, stacktrace = stack)
    }

    fun wasPrompted(context: Context, crashAtMs: Long): Boolean {
        val marker = File(AppLog.logDir(context), PROMPT_MARKER_FILE_NAME)
        if (!marker.exists()) return false
        val raw = runCatching { marker.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
        val marked = raw.substringAfter("crashAtMs=", "").trim().toLongOrNull() ?: return false
        return marked == crashAtMs
    }

    fun markPrompted(context: Context, crashAtMs: Long) {
        val dir = AppLog.logDir(context)
        runCatching { dir.mkdirs() }
        val marker = File(dir, PROMPT_MARKER_FILE_NAME)
        runCatching { marker.writeText("crashAtMs=$crashAtMs\n", Charsets.UTF_8) }
    }

    private fun writeCrashFile(context: Context, thread: Thread, throwable: Throwable) {
        val nowMs = System.currentTimeMillis()
        val dir = AppLog.logDir(context)
        runCatching { dir.mkdirs() }
        val file = File(dir, CRASH_FILE_NAME)

        val sw = StringWriter(8 * 1024)
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        val stack = sw.toString().trimEnd()

        val body =
            buildString(stack.length + 128) {
                append("crashAtMs=")
                append(nowMs)
                append('\n')
                append("thread=")
                append(thread.name)
                append('\n')
                append(
                    String.format(
                        Locale.US,
                        "throwable=%s: %s",
                        throwable.javaClass.name,
                        throwable.message.orEmpty(),
                    ),
                )
                append('\n')
                append('\n')
                append(stack)
                append('\n')
            }

        runCatching { file.writeText(body, Charsets.UTF_8) }
    }
}

