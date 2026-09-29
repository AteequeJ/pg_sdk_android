package com.pgsdk.ui.selection

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pgsdk.databinding.PgFragmentErrorBinding
import com.pgsdk.model.PGError
import com.pgsdk.model.PGErrorCode
import com.pgsdk.ui.checkout.PGCheckoutViewModel

internal class PGErrorFragment : Fragment() {

    private var _binding: PgFragmentErrorBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: PGCheckoutViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PgFragmentErrorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val error = requireError()
        binding.pgErrorMessage.text = error.message
        binding.pgErrorRetryButton.isVisible = error.isRetryable
        binding.pgErrorRetryButton.setOnClickListener { viewModel.retry() }
        binding.pgErrorCancelButton.setOnClickListener { viewModel.cancel() }
    }

    private fun requireError(): PGError {
        val arguments = requireArguments()
        val error = if (Build.VERSION.SDK_INT >= 33) {
            arguments.getParcelable(ARG_ERROR, PGError::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments.getParcelable(ARG_ERROR)
        }
        return error ?: PGError(PGErrorCode.UNKNOWN, "An unknown error occurred.")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ERROR = "pg_arg_error"

        fun newInstance(error: PGError) = PGErrorFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_ERROR, error) }
        }
    }
}
