/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.remotebuttons.settings

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.slice.Slice
import androidx.slice.SliceProvider
import com.android.tv.twopanelsettings.slices.builders.PreferenceSliceBuilder
import com.android.tv.twopanelsettings.slices.builders.PreferenceSliceBuilder.RowBuilder
import org.lineageos.remotebuttons.MainActivity
import org.lineageos.remotebuttons.R
import org.lineageos.remotebuttons.repository.SetupRepository

/**
 * The "Set up remote buttons" page of TV Settings > Remotes & Accessories, and its IR code
 * database sub-page.
 *
 * TV Settings shows the entry when its `bluetooth_ir_entry_slice_uri` string points at this
 * provider (set with a resource overlay in the device tree) and renders these slices in its
 * panels. TV Settings holds WRITE_SECURE_SETTINGS, so it is granted access without a prompt.
 */
class IrSliceProvider : SliceProvider(Manifest.permission.WRITE_SECURE_SETTINGS) {

    override fun onCreateSliceProvider() = true

    override fun onBindSlice(sliceUri: Uri): Slice? {
        val context = context ?: return null
        // Slices are bound under a StrictMode policy that forbids disk access, but the page
        // shows saved state; like AOSP Settings' slice provider, lift it while building.
        val oldPolicy = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        return try {
            val repository = SetupRepository(context)
            when (sliceUri.path) {
                PATH_MAIN -> mainSlice(context, sliceUri, repository)
                PATH_DATABASE -> databaseSlice(context, sliceUri, repository)
                else -> null
            }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    private fun mainSlice(context: Context, sliceUri: Uri, repository: SetupRepository): Slice {
        return PreferenceSliceBuilder(context, sliceUri)
            .addScreenTitle(RowBuilder().setTitle(context.getString(R.string.settings_title)))
            .addPreference(
                RowBuilder()
                    .setKey(KEY_SETUP)
                    .setTitle(context.getString(R.string.action_set_up))
                    .setSubtitle(
                        repository.configured?.let { context.getString(R.string.status_configured, it) }
                            ?: context.getString(R.string.status_not_configured),
                    )
                    .setPendingIntent(launch(context, MainActivity.START_SETUP)),
            )
            // Always offered: the remote may hold codes set up elsewhere, e.g. by the stock firmware.
            .addPreference(
                RowBuilder()
                    .setKey(KEY_CLEAR)
                    .setTitle(context.getString(R.string.action_clear))
                    .setPendingIntent(launch(context, MainActivity.START_CLEAR)),
            )
            .addPreference(
                RowBuilder()
                    .setKey(KEY_DATABASE)
                    .setTitle(context.getString(R.string.action_database))
                    .setSubtitle(
                        context.getString(if (repository.useXiaomiDatabase) R.string.database_xiaomi else R.string.database_open),
                    )
                    .setTargetSliceUri(uri(context, PATH_DATABASE).toString()),
            )
            .build()
    }

    private fun databaseSlice(context: Context, sliceUri: Uri, repository: SetupRepository): Slice {
        return PreferenceSliceBuilder(context, sliceUri)
            .addScreenTitle(RowBuilder().setTitle(context.getString(R.string.action_database)))
            .addPreference(
                RowBuilder()
                    .setKey(KEY_USE_OPEN)
                    .setTitle(context.getString(R.string.database_open))
                    .addRadioButton(choose(context, useXiaomi = false), !repository.useXiaomiDatabase, RADIO_GROUP_DATABASE),
            )
            .addPreference(
                RowBuilder()
                    .setKey(KEY_USE_XIAOMI)
                    .setTitle(context.getString(R.string.database_xiaomi))
                    .addRadioButton(choose(context, useXiaomi = true), repository.useXiaomiDatabase, RADIO_GROUP_DATABASE),
            )
            .build()
    }

    private fun launch(context: Context, start: String): PendingIntent = PendingIntent.getActivity(
        context,
        start.hashCode(),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_START, start)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun choose(context: Context, useXiaomi: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (useXiaomi) 1 else 0,
        Intent(context, DatabaseChoiceReceiver::class.java)
            .putExtra(DatabaseChoiceReceiver.EXTRA_USE_XIAOMI, useXiaomi),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val PATH_MAIN = "/ir"
        private const val PATH_DATABASE = "/ir/database"
        private const val KEY_SETUP = "setup"
        private const val KEY_CLEAR = "clear"
        private const val KEY_DATABASE = "database"
        private const val KEY_USE_OPEN = "use_open"
        private const val KEY_USE_XIAOMI = "use_xiaomi"
        private const val RADIO_GROUP_DATABASE = "database"

        private fun uri(context: Context, path: String): Uri =
            Uri.parse("content://${context.getString(R.string.slice_authority)}$path")

        /** Asks TV Settings to redraw the pages. */
        fun notifyChanged(context: Context) {
            listOf(PATH_MAIN, PATH_DATABASE).forEach {
                context.contentResolver.notifyChange(uri(context, it), null)
            }
        }
    }
}
