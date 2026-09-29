package com.pgsdk.core

import androidx.annotation.ColorInt
import androidx.annotation.StyleRes

/**
 * Minimal theming hook for the checkout UI. Pass a custom [styleRes] (extending
 * `Theme.PGSdk`) for full control, or override individual brand colors for the common
 * case.
 */
data class PGCheckoutTheme(
    @StyleRes val styleRes: Int? = null,
    @ColorInt val primaryColor: Int? = null,
    @ColorInt val onPrimaryColor: Int? = null
) {
    companion object {
        val Default = PGCheckoutTheme()
    }
}
