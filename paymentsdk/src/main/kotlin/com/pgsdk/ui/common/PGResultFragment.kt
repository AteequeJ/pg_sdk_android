package com.pgsdk.ui.common

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
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
        val style = when (result) {
            is PGPaymentResult.Success -> ResultStyle(
                R.drawable.pg_ic_success, R.color.pg_success_container,
                R.string.pg_result_success_title, result.paymentId
            )

            is PGPaymentResult.Pending -> ResultStyle(
                R.drawable.pg_ic_pending, R.color.pg_pending_container,
                R.string.pg_result_pending_title, result.message
            )

            is PGPaymentResult.Failure -> ResultStyle(
                R.drawable.pg_ic_error, R.color.pg_error_container,
                R.string.pg_result_failed_title, result.error.message
            )

            is PGPaymentResult.Cancelled -> ResultStyle(
                R.drawable.pg_ic_error, R.color.pg_error_container,
                R.string.pg_result_cancelled_title, null
            )
        }

        binding.pgResultIcon.setImageResource(style.iconRes)
        binding.pgResultIconContainer.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), style.containerColorRes)
        binding.pgResultTitle.setText(style.titleRes)
        binding.pgResultMessage.isVisible = style.message != null
        binding.pgResultMessage.text = style.message

        animateIn()
    }

    /** Pops the status badge in and fades the text up; kept short since the screen auto-closes. */
    private fun animateIn() {
        binding.pgResultIconContainer.apply {
            scaleX = 0.6f
            scaleY = 0.6f
            alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(ANIM_DURATION_MILLIS)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
        listOf(binding.pgResultTitle, binding.pgResultMessage).forEach { view ->
            view.alpha = 0f
            view.translationY = resources.displayMetrics.density * 8
            view.animate().alpha(1f).translationY(0f)
                .setStartDelay(ANIM_DURATION_MILLIS / 2)
                .setDuration(ANIM_DURATION_MILLIS)
                .start()
        }
    }

    private data class ResultStyle(
        @DrawableRes val iconRes: Int,
        @ColorRes val containerColorRes: Int,
        @StringRes val titleRes: Int,
        val message: String?
    )

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
        private const val ANIM_DURATION_MILLIS = 320L

        fun newInstance(result: PGPaymentResult) = PGResultFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_RESULT, result) }
        }
    }
}
