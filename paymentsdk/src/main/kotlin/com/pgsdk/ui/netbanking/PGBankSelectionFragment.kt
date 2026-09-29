package com.pgsdk.ui.netbanking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.pgsdk.databinding.PgFragmentBankSelectionBinding
import com.pgsdk.ui.checkout.PGCheckoutUiState
import com.pgsdk.ui.checkout.PGCheckoutViewModel
import kotlinx.coroutines.launch

internal class PGBankSelectionFragment : Fragment() {

    private var _binding: PgFragmentBankSelectionBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: PGCheckoutViewModel by activityViewModels()
    private val adapter = PGBankAdapter { bank -> viewModel.selectBank(bank) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PgFragmentBankSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pgBanksRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.pgBanksRecyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is PGCheckoutUiState.BankSelection) adapter.submit(state.banks)
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.pgBanksRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = PGBankSelectionFragment()
    }
}
