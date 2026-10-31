/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.remotebuttons.fragments

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.lineageos.remotebuttons.R
import org.lineageos.remotebuttons.viewmodels.SetupViewModel

class WelcomeFragment : GuidedStepSupportFragment() {

    private val viewModel: SetupViewModel by activityViewModels()

    override fun onCreateGuidance(savedInstanceState: Bundle?) = GuidanceStylist.Guidance(
        getString(R.string.app_name),
        getString(R.string.welcome_description),
        null,
        requireContext().getDrawable(R.drawable.ic_remote),
    )

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val repository = viewModel.repository
        guidanceStylist.descriptionView?.text = getString(R.string.welcome_description) + "\n\n" +
            (repository.configured?.let { getString(R.string.status_configured, it) }
                ?: getString(R.string.status_not_configured))
        actions = buildList {
            add(action(ACTION_SETUP, R.string.action_set_up))
            // Always offered: the remote may hold codes set up elsewhere, e.g. by the stock firmware.
            add(action(ACTION_CLEAR, R.string.action_clear))
            add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_DATABASE)
                    .title(R.string.action_database)
                    .description(
                        if (repository.useXiaomiDatabase) R.string.database_xiaomi else R.string.database_open,
                    )
                    .subActions(
                        listOf(
                            database(ACTION_USE_OPEN, R.string.database_open, !repository.useXiaomiDatabase),
                            database(ACTION_USE_XIAOMI, R.string.database_xiaomi, repository.useXiaomiDatabase),
                        ),
                    )
                    .build(),
            )
        }
    }

    private fun action(id: Long, title: Int) = GuidedAction.Builder(requireContext()).id(id).title(title).build()

    private fun database(id: Long, title: Int, checked: Boolean) = GuidedAction.Builder(requireContext())
        .id(id).title(title).checkSetId(DATABASE_GROUP).checked(checked).build()

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_SETUP -> add(parentFragmentManager, NoticeFragment())
            ACTION_CLEAR -> add(parentFragmentManager, ClearFragment())
        }
    }

    override fun onSubGuidedActionClicked(action: GuidedAction): Boolean {
        when (action.id) {
            ACTION_USE_OPEN -> viewModel.repository.useXiaomiDatabase = false
            ACTION_USE_XIAOMI -> viewModel.repository.useXiaomiDatabase = true
        }
        lifecycleScope.launch { refresh() }
        return true
    }

    private companion object {
        const val ACTION_SETUP = 1L
        const val ACTION_CLEAR = 2L
        const val ACTION_DATABASE = 3L
        const val ACTION_USE_OPEN = 4L
        const val ACTION_USE_XIAOMI = 5L
        const val DATABASE_GROUP = 1
    }
}
