package com.controlefinanceiro.app.data.sheets

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.controlefinanceiro.app.FinanceApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Queues a month to be sent to the spreadsheet. Several edits in a row become a single send. */
object SheetsSync {
    private const val KEY_MONTH = "month_id"

    fun schedule(context: Context, monthId: Long, delaySeconds: Long = 5) {
        val request = OneTimeWorkRequestBuilder<SheetsSyncWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_MONTH to monthId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("sheets-month-$monthId", ExistingWorkPolicy.REPLACE, request)
    }

    internal fun monthId(params: WorkerParameters) = params.inputData.getLong(KEY_MONTH, -1)
}

class SheetsSyncWorker(context: Context, private val params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FinanceApp).container
        val snapshot = container.finance.snapshot(SheetsSync.monthId(params)) ?: return Result.success()
        val userId = snapshot.month.userId
        val config = container.session.sheetsConfig(userId).first()
        if (!config.enabled) return Result.success()

        val tab = "${TAB_PREFIXES[snapshot.month.month - 1]}${snapshot.month.year % 100}"
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        return try {
            val reply = SheetsClient.post(config.url, SheetsPayload.build(snapshot, config.key))
            if (reply.ok) {
                container.session.setSheetsStatus(userId, "${reply.tab ?: tab} enviado às $time")
                Result.success()
            } else {
                // The script answered but refused (wrong key, broken sheet): retrying will not help.
                container.session.setSheetsStatus(userId, "Erro ao enviar $tab: ${reply.error ?: "resposta inválida"}")
                Result.failure()
            }
        } catch (e: IOException) {
            container.session.setSheetsStatus(userId, "Sem conexão com a planilha ($tab), tentando de novo")
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
        /** Same tab names as the spreadsheet: Jan26, Fev26... */
        val TAB_PREFIXES = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
    }
}

/** Posts the month to the Apps Script web app and reads its JSON reply. */
internal object SheetsClient {
    data class Reply(val ok: Boolean, val tab: String?, val error: String?)

    suspend fun post(url: String, body: String): Reply = withContext(Dispatchers.IO) {
        val first = open(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        // Apps Script runs the POST, then redirects to another host where the reply is read with GET.
        var connection = first
        var hops = 0
        while (connection.responseCode in 301..308 && hops < 5) {
            val location = connection.getHeaderField("Location") ?: break
            connection.disconnect()
            connection = open(URL(URL(url), location).toString())
            hops++
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw IOException("HTTP $code")
        parse(text)
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 20_000
        readTimeout = 60_000
    }

    /** Reads {"ok":true,"aba":"Out26"} or {"ok":false,"erro":"..."} without a JSON library. */
    fun parse(text: String): Reply {
        fun field(name: String) = Regex("\"$name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(text)?.groupValues?.get(1)
        val ok = Regex("\"ok\"\\s*:\\s*true").containsMatchIn(text)
        val error = field("erro") ?: if (!ok && text.isNotBlank() && !text.trimStart().startsWith("{")) {
            "o link não é do script (abra o link no navegador para conferir)"
        } else null
        return Reply(ok, field("aba"), error)
    }
}
