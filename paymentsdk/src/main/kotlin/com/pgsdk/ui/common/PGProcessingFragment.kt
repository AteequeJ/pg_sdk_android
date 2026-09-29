package com.pgsdk.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.pgsdk.databinding.PgFragmentProcessingBinding

internal class PGProcessingFragment : Fragment() {

    private var _binding: PgFragmentProcessingBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PgFragmentProcessingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pgProcessingMessage.text = requireArguments().getString(ARG_MESSAGE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_MESSAGE = "pg_arg_message"

        fun newInstance(message: String) = PGProcessingFragment().apply {
            arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
        }
    }
}
