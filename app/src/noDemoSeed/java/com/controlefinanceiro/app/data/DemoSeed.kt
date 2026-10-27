package com.controlefinanceiro.app.data

import com.controlefinanceiro.app.data.db.AppDatabase

/** Used when the local, git-ignored src/demoSeed folder is absent (always in release builds). */
object DemoSeed {
    @Suppress("UNUSED_PARAMETER")
    suspend fun ensure(db: AppDatabase) = Unit
}
