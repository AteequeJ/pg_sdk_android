package com.pgsdk.ui.checkout

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pgsdk.handler.PGUpiApp
import com.pgsdk.handler.PGUpiAppResolver
import com.pgsdk.handler.PGUpiIntentBuilder
import com.pgsdk.model.PGError
import com.pgsdk.model.PGErrorCode
import com.pgsdk.model.PGPaymentMethod
import com.pgsdk.model.PGPaymentRequest
import com.pgsdk.model.PGPaymentResult
import com.pgsdk.model.pgPaymentMethodFromWireValue
import com.pgsdk.network.PGApiResult
import com.pgsdk.network.PGPaymentRepository
import com.pgsdk.network.dto.BankDto
import com.pgsdk.network.dto.PGAttemptStatus
import com.pgsdk.network.dto.PGStatusResponse
import com.pgsdk.storage.PGSecureStorage
import com.pgsdk.util.PGLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Owns the checkout flow's state machine, orchestrating the gateway's `payment_attempts`
 * API end-to-end (docs/API.md): create an attempt for the chosen method, drive the
 * method-specific step (UPI intent/collect, card tokenize, net banking initiate), then
 * poll for a terminal status. Survives configuration changes via the normal [ViewModel]
 * lifecycle; all network calls run in [viewModelScope] and are cancelled automatically if
 * the host Activity is finished mid-flight.
 *
 * The in-flight attempt id is persisted to [PGSecureStorage] so the flow can resume if the
 * host process is killed while the user is away (e.g. switched to a UPI app) -- mirrors
 * the iOS SDK's Keychain-backed session resumption.
 */
internal class PGCheckoutViewModel(
    private val appContext: Context,
    private val request: PGPaymentRequest,
    private val allowedMethods: Set<PGPaymentMethod>,
    private val merchantDisplayName: String,
    private val repository: PGPaymentRepository,
    private val secureStorage: PGSecureStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow<PGCheckoutUiState>(PGCheckoutUiState.Loading)
    val uiState: StateFlow<PGCheckoutUiState> = _uiState.asStateFlow()

    private val _events = Channel<PGCheckoutEvent>(Channel.BUFFERED)
    val events: Flow<PGCheckoutEvent> = _events.receiveAsFlow()

    /** The attempt currently awaiting an external outcome (UPI app / redirect). */
    private var pendingAttemptId: String? = null

    /** Cancelled whenever a new flow starts, so a stale poll can't overwrite a newer state. */
    private var flowJob: Job? = null

    init {
        val previousOrderId = secureStorage.getString(PGSecureStorage.KEY_ACTIVE_ORDER_ID)
        val resumableAttemptId = secureStorage.getString(PGSecureStorage.KEY_ACTIVE_ATTEMPT_ID)
        secureStorage.putString(PGSecureStorage.KEY_ACTIVE_ORDER_ID, request.orderId)

        if (previousOrderId == request.orderId && resumableAttemptId != null) {
            pendingAttemptId = resumableAttemptId
            pollAndFinish(resumableAttemptId, "Confirming your payment...")
        } else {
            secureStorage.remove(PGSecureStorage.KEY_ACTIVE_ATTEMPT_ID)
            showMethodSelection()
        }
    }

    fun retry() = showMethodSelection()

    fun backToMethodSelection() = showMethodSelection()

    private fun showMethodSelection() {
        var methods = allowedMethods
        if (!request.currency.equals("INR", ignoreCase = true)) {
            methods = methods - PGPaymentMethod.UPI
        }

        if (methods.isEmpty()) {
            _uiState.value = PGCheckoutUiState.Failed(
                PGError(
                    PGErrorCode.INVALID_CONFIG,
                    "No supported payment methods are available for this order.",
                    isRetryable = false
                )
            )
            return
        }

        _uiState.value = PGCheckoutUiState.MethodSelection(
            methods = methods.sortedBy { it.ordinal },
            merchantDisplayName = merchantDisplayName,
            amountMinor = request.amountMinor,
            currency = request.currency
        )
    }

    fun selectMethod(method: PGPaymentMethod) {
        when (method) {
            PGPaymentMethod.UPI -> showUpiOptions()
            PGPaymentMethod.CARD -> _uiState.value = PGCheckoutUiState.CardEntry
            PGPaymentMethod.NET_BANKING -> showBankSelection()
        }
    }

    private fun showUpiOptions() {
        val apps = runCatching { PGUpiAppResolver.resolveInstalledApps(appContext) }.getOrDefault(emptyList())
        _uiState.value = PGCheckoutUiState.UpiOptions(installedApps = apps, allowManualVpa = true)
    }

    private fun showBankSelection() {
        _uiState.value = PGCheckoutUiState.Processing("Loading banks...")
        startFlow {
            when (val result = repository.listBanks()) {
                is PGApiResult.Ok -> _uiState.value = PGCheckoutUiState.BankSelection(result.value)
                is PGApiResult.Err -> _uiState.value = PGCheckoutUiState.Failed(result.error)
            }
        }
    }

    /** [app] is null for the "choose from system chooser" case. */
    fun launchUpiApp(app: PGUpiApp?) {
        _uiState.value = PGCheckoutUiState.Processing("Opening UPI app...")
        startFlow {
            val attemptId = createAttemptOrFail(PGPaymentMethod.UPI) ?: return@startFlow
            when (val result = repository.createUpiIntent(attemptId)) {
                is PGApiResult.Ok -> {
                    pendingAttemptId = attemptId
                    val intent = PGUpiIntentBuilder.build(result.value.intentUrl, app?.packageName)
                    _events.send(PGCheckoutEvent.LaunchUpiApp(intent))
                }

                is PGApiResult.Err -> _uiState.value = PGCheckoutUiState.Failed(result.error)
            }
        }
    }

    fun submitUpiVpa(vpa: String) {
        if (!isValidVpa(vpa)) {
            _events.trySend(PGCheckoutEvent.ShowToast("Enter a valid UPI ID, e.g. name@bank"))
            return
        }
        _uiState.value = PGCheckoutUiState.Processing("Sending a payment request to $vpa...")
        startFlow {
            val attemptId = createAttemptOrFail(PGPaymentMethod.UPI) ?: return@startFlow
            pendingAttemptId = attemptId
            when (val result = repository.collectUpi(attemptId, vpa)) {
                is PGApiResult.Ok -> pollAndFinish(attemptId, "Waiting for you to approve in your UPI app...")
                is PGApiResult.Err -> _uiState.value = PGCheckoutUiState.Failed(result.error)
            }
        }
    }

    fun submitCard(
        cardNumber: String,
        expiryMonth: String,
        expiryYear: String,
        cvv: String,
        cardholderName: String
    ) {
        _uiState.value = PGCheckoutUiState.Processing("Processing your card...")
        startFlow {
            val attemptId = createAttemptOrFail(PGPaymentMethod.CARD) ?: return@startFlow
            pendingAttemptId = attemptId
            val result = repository.tokenizeCard(
                attemptId = attemptId,
                cardNumber = cardNumber,
                expiryMonth = expiryMonth,
                expiryYear = expiryYear,
                cvv = cvv,
                cardholderName = cardholderName
            )
            when (result) {
                is PGApiResult.Ok -> {
                    val redirectUrl = result.value.threeDsRedirectUrl
                    if (redirectUrl != null) {
                        _uiState.value = PGCheckoutUiState.WebRedirect(redirectUrl)
                        awaitTerminal(attemptId)
                    } else {
                        pollAndFinish(attemptId, "Confirming your payment...")
                    }
                }

                is PGApiResult.Err -> _uiState.value = PGCheckoutUiState.Failed(result.error)
            }
        }
    }

    fun selectBank(bank: BankDto) {
        _uiState.value = PGCheckoutUiState.Processing("Preparing secure checkout...")
        startFlow {
            val attemptId = createAttemptOrFail(PGPaymentMethod.NET_BANKING) ?: return@startFlow
            pendingAttemptId = attemptId
            when (val result = repository.initiateNetBanking(attemptId, bank.id)) {
                is PGApiResult.Ok -> {
                    _uiState.value = PGCheckoutUiState.WebRedirect(result.value.redirectUrl)
                    awaitTerminal(attemptId)
                }

                is PGApiResult.Err -> _uiState.value = PGCheckoutUiState.Failed(result.error)
            }
        }
    }

    /** Called when control returns to the host Activity after a UPI app launch. */
    fun onExternalFlowReturned() {
        val attemptId = pendingAttemptId
        if (attemptId == null) {
            cancel()
            return
        }
        pollAndFinish(attemptId, "Confirming your payment...")
    }

    private suspend fun createAttemptOrFail(method: PGPaymentMethod): String? {
        val result = repository.createAttempt(
            orderToken = request.orderToken,
            method = method,
            amountMinor = request.amountMinor,
            currency = request.currency,
            customer = request.customer,
            metadata = request.notes + ("order_id" to request.orderId)
        )
        return when (result) {
            is PGApiResult.Ok -> {
                secureStorage.putString(PGSecureStorage.KEY_ACTIVE_ATTEMPT_ID, result.value.attemptId)
                result.value.attemptId
            }

            is PGApiResult.Err -> {
                _uiState.value = PGCheckoutUiState.Failed(result.error)
                null
            }
        }
    }

    private fun pollAndFinish(attemptId: String, message: String) {
        _uiState.value = PGCheckoutUiState.Processing(message)
        startFlow { awaitTerminal(attemptId) }
    }

    private suspend fun awaitTerminal(attemptId: String) {
        when (val result = repository.pollUntilTerminal(attemptId)) {
            is PGApiResult.Ok -> emitTerminal(result.value)
            is PGApiResult.Err -> {
                PGLogger.w("Status polling did not reach a terminal state", subTag = "Checkout")
                finishWith(PGPaymentResult.Pending(request.orderId, attemptId))
            }
        }
    }

    private fun emitTerminal(status: PGStatusResponse) {
        val method = pgPaymentMethodFromWireValue(status.method)
        val result = when (status.status) {
            PGAttemptStatus.SUCCEEDED -> PGPaymentResult.Success(
                orderId = request.orderId,
                paymentId = status.attemptId,
                method = method,
                rawReference = status.verificationReference
            )

            PGAttemptStatus.FAILED -> PGPaymentResult.Failure(
                orderId = request.orderId,
                error = PGError(
                    code = PGErrorCode.PAYMENT_DECLINED,
                    message = status.failureReason ?: "Your payment could not be completed.",
                    isRetryable = true
                )
            )

            PGAttemptStatus.CANCELLED -> PGPaymentResult.Cancelled(request.orderId)

            else -> PGPaymentResult.Pending(request.orderId, status.attemptId)
        }
        finishWith(result)
    }

    fun cancel() {
        flowJob?.cancel()
        finishWith(PGPaymentResult.Cancelled(request.orderId))
    }

    private fun finishWith(result: PGPaymentResult) {
        secureStorage.clearSession()
        _uiState.value = PGCheckoutUiState.Terminal(result)
    }

    /** Cancels any previous in-flight flow before starting a new one. */
    private fun startFlow(block: suspend () -> Unit) {
        flowJob?.cancel()
        flowJob = viewModelScope.launch { block() }
    }

    private companion object {
        val VPA_REGEX = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$")
        fun isValidVpa(vpa: String) = VPA_REGEX.matches(vpa)
    }
}
