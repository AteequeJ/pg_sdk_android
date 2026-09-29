package com.pgsdk.handler

import android.graphics.drawable.Drawable

/** A UPI-capable app installed on the device, as resolved by [PGUpiAppResolver]. */
internal data class PGUpiApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)
