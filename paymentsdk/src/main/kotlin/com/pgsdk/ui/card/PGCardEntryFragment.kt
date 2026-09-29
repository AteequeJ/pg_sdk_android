package com.pgsdk.ui.card

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pgsdk.R
import com.pgsdk.databinding.PgFragmentCardEntryBinding
import com.pgsdk.ui.checkout.PGCheckoutViewModel
import java.util.Calendar

/**
 * Collects card details for tokenization. Values entered here go straight to
 * [PGCheckoutViewModel.submitCard] -> the gateway's `card/tokenize` endpoint and are
 * never logged (HTTP logging is BASIC-level only) or persisted anywhere by the SDK.
 */
internal class PGCardEntryFragment : Fragment() {

    private var _binding: PgFragmentCardEntryBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: PGCheckoutViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PgFragmentCardEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pgCardPayButton.setOnClickListener { submit() }
    }

    private fun submit() {
        val number = binding.pgCardNumberInput.text?.toString().orEmpty().replace(" ", "")
        val expiry = binding.pgCardExpiryInput.text?.toString().orEmpty()
        val cvv = binding.pgCardCvvInput.text?.toString().orEmpty()
        val name = binding.pgCardNameInput.text?.toString()?.trim().orEmpty()
        val (month, year) = parseExpiry(expiry)

        val errorRes = when {
            !isValidCardNumber(number) -> R.string.pg_card_error_number
            month == null || year == null || !isValidExpiry(month, year) -> R.string.pg_card_error_expiry
            cvv.length !in 3..4 -> R.string.pg_card_error_cvv
            name.isBlank() -> R.string.pg_card_error_name
            else -> null
        }

        if (errorRes != null) {
            binding.pgCardErrorText.setText(errorRes)
            binding.pgCardErrorText.isVisible = true
            return
        }
        binding.pgCardErrorText.isVisible = false

        viewModel.submitCard(
            cardNumber = number,
            expiryMonth = month!!.padStart(2, '0'),
            expiryYear = normalizeYear(year!!),
            cvv = cvv,
            cardholderName = name
        )

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = PGCardEntryFragment()

        private fun parseExpiry(expiry: String): Pair<String?, String?> {
            val parts = expiry.split("/")
            val month = parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() }
            val year = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            return month to year
        }

        private fun normalizeYear(year: String): String = when (year.length) {
            2 -> "20$year"
            else -> year
        }

        private fun isValidCardNumber(number: String): Boolean {
            if (number.length !in 12..19 || !number.all { it.isDigit() }) return false
            var sum = 0
            var doubleDigit = false
            for (i in number.length - 1 downTo 0) {
                var digit = number[i] - '0'
                if (doubleDigit) {
                    digit *= 2
                    if (digit > 9) digit -= 9
                }
                sum += digit
                doubleDigit = !doubleDigit
            }
            return sum % 10 == 0
        }

        private fun isValidExpiry(month: String, year: String): Boolean {
            val monthValue = month.toIntOrNull() ?: return false
            val yearValue = normalizeYear(year).toIntOrNull() ?: return false
            if (monthValue !in 1..12) return false

            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            val currentMonth = calendar.get(Calendar.MONTH) + 1

            return yearValue > currentYear || (yearValue == currentYear && monthValue >= currentMonth)
        }
    }
}
