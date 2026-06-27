package android.kimyona.jammer.core.crash

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CrashReporter — BULLETPROOF EDITION.
 *
 * Correções:
 * - NÃO mata o processo (deixa Android coletar nativamente)
 * - Salva em filesDir (interno, sem permissão)
 * - Try/catch no handler pra não dar loop infinito
 */
class CrashReporter(private val context: Context) {

    companion object {
        private const val TAG = "JammerCrash"
    }

    fun install() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val reportFile = saveCrash(throwable)
                Log.i(TAG, "Crash salvo em: ${reportFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao salvar crash report: ${e.message}")
            }
            // Chama o handler padrão para que o Android colete o crash nativamente
            // NÃO mata o processo manualmente
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrash(throwable: Throwable): File {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

        // PASTA INTERNA — não precisa de permissão de storage
        val crashDir = File(context.filesDir, "crash-reports").apply { mkdirs() }

        val file = File(crashDir, "crash_$timestamp.json")
        val json = JSONObject().apply {
            put("device", Build.MODEL)
            put("model", Build.DEVICE)
            put("codename", Build.BOARD)
            put("api", Build.VERSION.SDK_INT)
            put("build", Build.DISPLAY)
            put("timestamp", timestamp)
            put("app_version", "1.0.0-Tidal")
            put("stacktrace", throwable.stackTraceToString())
        }
        file.writeText(json.toString(2))
        return file
    }
}
