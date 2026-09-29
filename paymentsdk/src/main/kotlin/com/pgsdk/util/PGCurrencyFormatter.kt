package com.pgsdk.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

internal object PGCurrencyFormatter {

    private val SYMBOLS = mapOf(
        "INR" to "₹",
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£"
    )

    fun format(amountMinor: Long, currencyCode: String): String {
        val fractionDigits = runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }
            .getOrDefault(2)
            .coerceAtLeast(0)
        val divisor = BigDecimal.TEN.pow(fractionDigits)
        val amount = BigDecimal(amountMinor).divide(divisor)
            .setScale(fractionDigits, RoundingMode.HALF_UP)
        val symbol = SYMBOLS[currencyCode.uppercase(Locale.ROOT)] ?: "$currencyCode "
        return "$symbol${amount.toPlainString()}"
    }
}
