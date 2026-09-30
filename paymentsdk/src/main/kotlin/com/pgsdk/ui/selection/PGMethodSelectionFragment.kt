package com.pgsdk.ui.selection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pgsdk.R
import com.pgsdk.databinding.PgFragmentMethodSelectionBinding
import com.pgsdk.databinding.PgItemMethodBinding
import com.pgsdk.model.PGPaymentMethod
import com.pgsdk.ui.checkout.PGCheckoutUiState
import com.pgsdk.ui.checkout.PGCheckoutViewModel
import com.pgsdk.util.PGCurrencyFormatter
import kotlinx.coroutines.launch

internal class PGMethodSelectionFragment : Fragment() {

    private var _binding: PgFragmentMethodSelectionBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: PGCheckoutViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PgFragmentMethodSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is PGCheckoutUiState.MethodSelection) render(state)
                }
            }
        }
    }

    private fun render(state: PGCheckoutUiState.MethodSelection) {
        binding.pgMerchantName.text = state.merchantDisplayName
        binding.pgAmount.text = PGCurrencyFormatter.format(state.amountMinor, state.currency)

        binding.pgMethodsContainer.removeAllViews()
        state.methods.forEach { method ->
            val row = PgItemMethodBinding.inflate(layoutInflater, binding.pgMethodsContainer, false)
            val (title, subtitle) = titleAndSubtitleFor(method)
            row.pgMethodTitle.text = title
            row.pgMethodSubtitle.text = subtitle
            row.pgMethodIcon.setImageResource(iconFor(method))
            row.root.setOnClickListener { viewModel.selectMethod(method) }
            binding.pgMethodsContainer.addView(row.root)
        }
    }

    private fun titleAndSubtitleFor(method: PGPaymentMethod): Pair<String, String> = when (method) {
        PGPaymentMethod.UPI -> getString(R.string.pg_method_upi) to getString(R.string.pg_method_upi_desc)
        PGPaymentMethod.CARD -> getString(R.string.pg_method_card) to getString(R.string.pg_method_card_desc)
        PGPaymentMethod.NET_BANKING ->
            getString(R.string.pg_method_net_banking) to getString(R.string.pg_method_net_banking_desc)
    }

    private fun iconFor(method: PGPaymentMethod): Int = when (method) {
        PGPaymentMethod.UPI -> R.drawable.pg_ic_upi
        PGPaymentMethod.CARD -> R.drawable.pg_ic_card
        PGPaymentMethod.NET_BANKING -> R.drawable.pg_ic_bank_placeholder
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = PGMethodSelectionFragment()
    }
}
