package com.pgsdk.ui.checkout

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pgsdk.core.PGConfig
import com.pgsdk.di.PGServiceLocator
import com.pgsdk.model.PGPaymentRequest

internal class PGCheckoutViewModelFactory(
    private val appContext: Context,
    private val request: PGPaymentRequest,
    private val config: PGConfig
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PGCheckoutViewModel::class.java)) {
            "Unknown ViewModel class: $modelClass"
        }
        return PGCheckoutViewModel(
            appContext = appContext,
            request = request,
            allowedMethods = config.allowedPaymentMethods,
            merchantDisplayName = config.merchantDisplayName,
            repository = PGServiceLocator.requireRepository(),
            secureStorage = PGServiceLocator.requireSecureStorage()
        ) as T
    }
}
