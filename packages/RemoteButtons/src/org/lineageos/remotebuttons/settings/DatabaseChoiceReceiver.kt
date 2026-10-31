/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.remotebuttons.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.lineageos.remotebuttons.repository.SetupRepository

/** Applies the IR code database picked on the TV Settings page. */
class DatabaseChoiceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.hasExtra(EXTRA_USE_IMPORTED)) return
        SetupRepository(context).useImportedDatabase = intent.getBooleanExtra(EXTRA_USE_IMPORTED, false)
    }

    companion object {
        const val EXTRA_USE_IMPORTED = "use_imported"
    }
}
