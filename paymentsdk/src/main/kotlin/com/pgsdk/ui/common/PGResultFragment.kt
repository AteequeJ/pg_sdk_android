package com.pgsdk.ui.common

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.pgsdk.R
import com.pgsdk.databinding.PgFragmentResultBinding
import com.pgsdk.model.PGPaymentResult

internal class PGResultFragment : Fragment() {

    private var _binding: PgFragmentResultBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PgFragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        render(requireResult())
    }

    private fun render(result: PGPaymentResult) {
        val (iconRes, titleRes, message) = when (result) {
            is PGPaymentResult.Success ->
                Triple(R.drawable.pg_ic_success, R.string.pg_result_success_title, result.paymentId)

            is PGPaymentResult.Pending ->
                Triple(R.drawable.pg_ic_pending, R.string.pg_result_pending_title, result.message)

            is PGPaymentResult.Failure ->
                Triple(R.drawable.pg_ic_error, R.string.pg_result_failed_title, result.error.message)

            is PGPaymentResult.Cancelled ->
                Triple(R.drawable.pg_ic_error, R.string.pg_result_cancelled_title, null)
        }

        binding.pgResultIcon.setImageResource(iconRes)
        binding.pgResultTitle.setText(titleRes)
        binding.pgResultMessage.isVisible = message != null
        binding.pgResultMessage.text = message
    }

    private fun requireResult(): PGPaymentResult {
        val arguments = requireArguments()
        val result = if (Build.VERSION.SDK_INT >= 33) {
            arguments.getParcelable(ARG_RESULT, PGPaymentResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments.getParcelable(ARG_RESULT)
        }
        return requireNotNull(result) { "PGResultFragment requires a PGPaymentResult argument" }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_RESULT = "pg_arg_result"

        fun newInstance(result: PGPaymentResult) = PGResultFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_RESULT, result) }
        }
    }
}
