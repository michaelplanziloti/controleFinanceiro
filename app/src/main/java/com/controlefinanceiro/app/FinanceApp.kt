package com.controlefinanceiro.app

import android.app.Application
import com.controlefinanceiro.app.data.AuthRepository
import com.controlefinanceiro.app.data.FinanceRepository
import com.controlefinanceiro.app.data.SessionStore
import com.controlefinanceiro.app.data.db.AppDatabase
import com.controlefinanceiro.app.data.sheets.SheetsSync

/** Manual dependency container; small enough that a DI framework is not needed yet. */
class AppContainer(private val app: Application) {
    private val database = AppDatabase.build(app)
    val session = SessionStore(app)
    val auth = AuthRepository(database, session)
    val finance = FinanceRepository(database) { monthId -> SheetsSync.schedule(app, monthId) }

    /** Sends a month to the spreadsheet right away (the "Enviar todos os meses" button). */
    fun sendToSheets(monthId: Long) = SheetsSync.schedule(app, monthId, delaySeconds = 0)
}

class FinanceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
