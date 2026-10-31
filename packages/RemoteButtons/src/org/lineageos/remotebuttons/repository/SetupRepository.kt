/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.remotebuttons.repository

import android.content.Context
import androidx.core.content.edit
import org.lineageos.remotebuttons.database.IrDatabase
import org.lineageos.remotebuttons.database.OpenIrDatabase
import org.lineageos.remotebuttons.database.xiaomi.XiaomiIrDatabase
import org.lineageos.remotebuttons.settings.IrSliceProvider

/** Persistent setup state, shared by the wizard and the TV Settings page. */
class SetupRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val openDatabase by lazy { OpenIrDatabase(appContext) }
    val xiaomiDatabase by lazy { XiaomiIrDatabase.get(appContext) }

    /** Address of the remote that was last programmed. */
    var remoteAddress: String?
        get() = prefs.getString(KEY_REMOTE, null)
        set(value) = prefs.edit { putString(KEY_REMOTE, value) }

    /** Human-readable description of what the remote currently controls, or null. */
    val configured: String? get() = prefs.getString(KEY_CONFIGURED, null)

    /** Whether to use the bundled Xiaomi database instead of the open database. */
    var useXiaomiDatabase: Boolean
        get() = prefs.getBoolean(KEY_USE_XIAOMI, true)
        set(value) {
            prefs.edit(commit = true) { putBoolean(KEY_USE_XIAOMI, value) }
            IrSliceProvider.notifyChanged(appContext)
        }

    suspend fun database(): IrDatabase = if (useXiaomiDatabase) xiaomiDatabase else openDatabase

    fun setConfigured(description: String?) {
        prefs.edit(commit = true) { putString(KEY_CONFIGURED, description) }
        IrSliceProvider.notifyChanged(appContext)
    }

    private companion object {
        const val PREFS = "setup"
        const val KEY_REMOTE = "remote_address"
        const val KEY_CONFIGURED = "configured"
        const val KEY_USE_XIAOMI = "use_xiaomi_database"
    }
}
