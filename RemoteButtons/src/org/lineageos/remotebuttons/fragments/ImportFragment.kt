/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.remotebuttons.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.fragment.app.activityViewModels
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.lineageos.remotebuttons.R
import org.lineageos.remotebuttons.viewmodels.SetupViewModel

/**
 * Imports the IR database of the stock Xiaomi firmware, which covers many more brands than the
 * bundled one. The file has to come from the user's own firmware.
 */
class ImportFragment : GuidedStepSupportFragment() {

    private val viewModel: SetupViewModel by activityViewModels()

    override fun onCreateGuidance(savedInstanceState: Bundle?) = GuidanceStylist.Guidance(
        getString(R.string.import_title),
        getString(R.string.import_description),
        getString(R.string.app_name),
        null,
    )

    private var busy = false

    override fun onResume() {
        super.onResume()
        // Back from the all files access toggle, or a first start.
        if (!busy) actions = choices()
    }

    private fun choices() = buildList {
        add(GuidedAction.Builder(requireContext()).id(ACTION_IMPORT).title(R.string.action_import).build())
        if (!Environment.isExternalStorageManager()) {
            add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ALLOW_FILES)
                    .title(R.string.action_allow_files)
                    .description(R.string.action_allow_files_description)
                    .build(),
            )
        }
        add(GuidedAction.Builder(requireContext()).clickAction(GuidedAction.ACTION_ID_CANCEL).build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_IMPORT -> import()
            ACTION_ALLOW_FILES -> startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.fromParts("package", requireContext().packageName, null),
                ),
            )
            else -> finishStep()
        }
    }

    private fun import() {
        busy = true
        guidanceStylist.titleView?.setText(R.string.import_busy)
        actions = emptyList()
        lifecycleScope.launch {
            viewModel.importDatabase().fold(
                onSuccess = {
                    viewModel.repository.useImportedDatabase = true
                    guidanceStylist.titleView?.setText(R.string.import_done_title)
                    guidanceStylist.descriptionView?.text =
                        getString(R.string.import_done_description, it.brands, it.codeSets)
                },
                onFailure = {
                    guidanceStylist.titleView?.setText(R.string.import_failed_title)
                    guidanceStylist.descriptionView?.text = it.message
                },
            )
            actions = listOf(GuidedAction.Builder(requireContext()).clickAction(GuidedAction.ACTION_ID_OK).build())
        }
    }

    private companion object {
        const val ACTION_IMPORT = 1L
        const val ACTION_ALLOW_FILES = 2L
    }
}
