package com.pgsdk.ui.upi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.pgsdk.databinding.PgFragmentUpiOptionsBinding
import com.pgsdk.ui.checkout.PGCheckoutUiState
import com.pgsdk.ui.checkout.PGCheckoutViewModel
import kotlinx.coroutines.launch

internal class PGUpiOptionsFragment : Fragment() {

    private var _binding: PgFragmentUpiOptionsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: PGCheckoutViewModel by activityViewModels()
    private val adapter = PGUpiAppAdapter { app -> viewModel.launchUpiApp(app) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PgFragmentUpiOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pgUpiAppsRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.pgUpiAppsRecyclerView.adapter = adapter

        binding.pgVpaSubmitButton.setOnClickListener {
            val vpa = binding.pgVpaInput.text?.toString().orEmpty().trim()
            viewModel.submitUpiVpa(vpa)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is PGCheckoutUiState.UpiOptions) render(state)
                }
            }
        }
    }

    private fun render(state: PGCheckoutUiState.UpiOptions) {
        val hasApps = state.installedApps.isNotEmpty()
        binding.pgUpiAppsRecyclerView.isVisible = hasApps
        binding.pgUpiNoAppsMessage.isVisible = !hasApps
        if (hasApps) adapter.submit(state.installedApps)
    }

    override fun onDestroyView() {
        binding.pgUpiAppsRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = PGUpiOptionsFragment()
    }
}
