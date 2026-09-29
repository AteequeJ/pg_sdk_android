package com.pgsdk.ui.checkout

import com.pgsdk.handler.PGUpiApp
import com.pgsdk.model.PGError
import com.pgsdk.model.PGPaymentMethod
import com.pgsdk.model.PGPaymentResult
import com.pgsdk.network.dto.BankDto

/** Drives which screen [PGCheckoutActivity] shows. Exposed via [PGCheckoutViewModel.uiState]. */
internal sealed interface PGCheckoutUiState {

    data object Loading : PGCheckoutUiState

    data class MethodSelection(
        val methods: List<PGPaymentMethod>,
        val merchantDisplayName: String,
        val amountMinor: Long,
        val currency: String
    ) : PGCheckoutUiState

    data class UpiOptions(
        val installedApps: List<PGUpiApp>,
        val allowManualVpa: Boolean
    ) : PGCheckoutUiState

    data class BankSelection(val banks: List<BankDto>) : PGCheckoutUiState

    data object CardEntry : PGCheckoutUiState

    /**
     * Hosts a bank/3DS redirect URL in a WebView. Purely presentational -- the ViewModel
     * polls the attempt's status in the background and moves off this screen once a
     * terminal result is known; nobody inspects the URL itself.
     */
    data class WebRedirect(val url: String) : PGCheckoutUiState

    data class Processing(val message: String) : PGCheckoutUiState

    data class Failed(val error: PGError) : PGCheckoutUiState

    data class Terminal(val result: PGPaymentResult) : PGCheckoutUiState
}

/** One-off effects that need an Activity (e.g. launching an external app intent). */
internal sealed interface PGCheckoutEvent {
    data class LaunchUpiApp(val intent: android.content.Intent) : PGCheckoutEvent
    data class ShowToast(val message: String) : PGCheckoutEvent
}
