package com.controlefinanceiro.app.data.sheets

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.controlefinanceiro.app.FinanceApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Two-way sync with the spreadsheet, one month (tab) at a time:
 * - a change in the app marks the month dirty and queues a send ([SheetsSyncWorker]);
 * - [SheetsPullWorker] checks the tabs' edit times and downloads the ones the app has not seen.
 * The sheet wins when both changed: the script refuses a send based on an older version of the tab.
 */
object SheetsSync {
    private const val KEY_MONTH = "month_id"
    private const val PULL_NOW = "sheets-pull-now"
    private val network = Constraints(requiredNetworkType = NetworkType.CONNECTED)

    fun schedule(context: Context, monthId: Long, delaySeconds: Long = 5) {
        val request = OneTimeWorkRequestBuilder<SheetsSyncWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_MONTH to monthId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("sheets-month-$monthId", ExistingWorkPolicy.REPLACE, request)
    }

    /** Checks the spreadsheet every 15 minutes (the shortest Android allows) while there is network. */
    fun schedulePeriodicPull(context: Context) {
        val request = PeriodicWorkRequestBuilder<SheetsPullWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("sheets-pull", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun pullNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SheetsPullWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(PULL_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Pulls now and waits until it is done, or gives up waiting after 30 s (e.g. no network). */
    suspend fun pullNowAndWait(context: Context) {
        pullNow(context)
        withTimeoutOrNull(30_000) {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PULL_NOW)
                .first { infos -> infos.isNotEmpty() && infos.all { it.state.isFinished } }
        }
    }

    internal fun monthId(params: WorkerParameters) = params.inputData.getLong(KEY_MONTH, -1)

    internal fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    /** Same tab names as the spreadsheet: Jan26, Fev26... */
    internal fun tabName(year: Int, month: Int) =
        listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")[month - 1] + (year % 100)
}

/** Sends one month to its tab, unless the tab changed since the version the app knows. */
class SheetsSyncWorker(context: Context, private val params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FinanceApp).container
        val snapshot = container.finance.snapshot(SheetsSync.monthId(params)) ?: return Result.success()
        val month = snapshot.month
        val config = container.session.sheetsConfig(month.userId).first()
        if (!config.enabled || !month.dirty) return Result.success()

        val tab = SheetsSync.tabName(month.year, month.month)
        suspend fun status(text: String) = container.session.setSheetsStatus(month.userId, text)
        return try {
            val reply = SheetsClient.post(config.url, SheetsPayload.build(snapshot, config.key))
            when {
                reply.ok -> {
                    container.finance.markSent(month.id, reply.editedAt)
                    status("$tab enviado às ${SheetsSync.now()}")
                    Result.success()
                }
                reply.conflict -> {
                    // The tab changed in the spreadsheet (or was never downloaded): the sheet wins.
                    SheetsSync.pullNow(applicationContext)
                    Result.success()
                }
                else -> {
                    status("Erro ao enviar $tab: ${reply.error ?: "resposta inválida"}")
                    Result.failure()
                }
            }
        } catch (e: IOException) {
            status("Sem conexão com a planilha ($tab), tentando de novo")
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}

/**
 * For every user with the spreadsheet set up: downloads each tab whose edit time differs from the
 * version the app has (new tabs become new months), then sends the months still changed in the app.
 */
class SheetsPullWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FinanceApp).container
        var offline = false
        for (userId in container.finance.userIds()) {
            val config = container.session.sheetsConfig(userId).first()
            if (!config.enabled) continue
            try {
                pull(userId, config)
            } catch (e: IOException) {
                offline = true
                container.session.setSheetsStatus(userId, "Sem conexão com a planilha às ${SheetsSync.now()}")
            }
        }
        return if (offline && runAttemptCount < 3) Result.retry() else Result.success()
    }

    private suspend fun pull(userId: Long, config: com.controlefinanceiro.app.data.SheetsConfig) {
        val container = (applicationContext as FinanceApp).container
        val finance = container.finance
        val list = SheetsClient.get(config.url, mapOf("key" to config.key))
        if (!list.ok) {
            container.session.setSheetsStatus(userId, "Erro ao ler a planilha: ${list.error ?: "resposta inválida"}")
            return
        }
        val tabs = SheetsReader.tabs(list.json)
        val local = finance.months(userId).associateBy { it.year to it.month }
        val updated = mutableListOf<String>()
        val overwritten = mutableListOf<String>()
        for (tab in tabs) {
            val month = local[tab.year to tab.month]
            if (month != null && month.sheetVersion == tab.editedAt) continue
            val reply = SheetsClient.get(config.url, mapOf("key" to config.key, "aba" to tab.name))
            if (!reply.ok) continue
            finance.importFromSheet(userId, SheetsReader.month(reply.json, tab))
            updated += tab.name
            if (month?.dirty == true) overwritten += tab.name
        }
        // Months changed in the app that the sheet did not touch go up now.
        val known = tabs.associateBy { it.year to it.month }
        finance.months(userId).filter { it.dirty }.forEach { month ->
            val tab = known[month.year to month.month]
            if (tab == null || tab.editedAt == month.sheetVersion) SheetsSync.schedule(applicationContext, month.id, delaySeconds = 0)
        }

        val time = SheetsSync.now()
        container.session.setSheetsStatus(
            userId,
            when {
                overwritten.isNotEmpty() -> "Planilha conferida às $time. ${overwritten.joinToString()} mudou na planilha e no app: " +
                    "ficou a versão da planilha."
                updated.isNotEmpty() -> "Planilha conferida às $time. Atualizado: ${updated.joinToString()}."
                else -> "Planilha conferida às $time. Nada novo."
            },
        )
    }
}

/** Talks to the Apps Script web app and reads its JSON replies. */
internal object SheetsClient {
    data class Reply(val json: JSONObject, val ok: Boolean, val conflict: Boolean, val tab: String?, val editedAt: Long, val error: String?)

    suspend fun post(url: String, body: String): Reply = request(url, body)

    suspend fun get(url: String, params: Map<String, String>): Reply {
        val query = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return request(if ('?' in url) "$url&$query" else "$url?$query", body = null)
    }

    private suspend fun request(url: String, body: String?): Reply = withContext(Dispatchers.IO) {
        var connection = open(url)
        if (body != null) {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        // Apps Script answers on another host: follow its redirect with GET to read the reply.
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

    fun parse(text: String): Reply {
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return Reply(JSONObject(), false, false, null, 0, "o link não é do script (abra o link no navegador para conferir)")
        }
        return Reply(
            json = json,
            ok = json.optBoolean("ok"),
            conflict = json.optBoolean("conflito"),
            tab = json.optString("aba").takeIf { it.isNotEmpty() },
            editedAt = json.optLong("editedAt"),
            error = json.optString("erro").takeIf { it.isNotEmpty() },
        )
    }
}
