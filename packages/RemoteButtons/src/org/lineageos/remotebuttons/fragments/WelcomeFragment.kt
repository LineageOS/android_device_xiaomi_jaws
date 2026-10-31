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

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions += action(ACTION_SETUP, R.string.action_set_up)
        // Always offered: the remote may hold codes set up elsewhere, e.g. by the stock firmware.
        actions += action(ACTION_CLEAR, R.string.action_clear)
    }

    override fun onResume() {
        super.onResume()
        guidanceStylist.descriptionView?.text = getString(R.string.welcome_description) + "\n\n" +
            (viewModel.repository.configured?.let { getString(R.string.status_configured, it) }
                ?: getString(R.string.status_not_configured))
    }

    private fun action(id: Long, title: Int) = GuidedAction.Builder(requireContext()).id(id).title(title).build()

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_SETUP -> add(parentFragmentManager, NoticeFragment())
            ACTION_CLEAR -> add(parentFragmentManager, ClearFragment())
        }
    }

    private companion object {
        const val ACTION_SETUP = 1L
        const val ACTION_CLEAR = 2L
    }
}
