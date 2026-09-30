package com.pgsdk.ui.checkout

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pgsdk.R
import com.pgsdk.core.PGPaymentContract
import com.pgsdk.core.PGPaymentSDK
import com.pgsdk.core.PGSdkException
import com.pgsdk.databinding.PgActivityCheckoutBinding
import com.pgsdk.model.PGPaymentRequest
import com.pgsdk.model.PGPaymentResult
import com.pgsdk.ui.card.PGCardEntryFragment
import com.pgsdk.ui.card.PGWebRedirectFragment
import com.pgsdk.ui.common.PGProcessingFragment
import com.pgsdk.ui.common.PGResultFragment
import com.pgsdk.ui.netbanking.PGBankSelectionFragment
import com.pgsdk.ui.selection.PGErrorFragment
import com.pgsdk.ui.selection.PGMethodSelectionFragment
import com.pgsdk.ui.upi.PGUpiOptionsFragment
import kotlinx.coroutines.launch

/**
 * Hosts the entire checkout flow as a single Activity with swapped Fragments driven by
 * [PGCheckoutViewModel.uiState]. Not part of the public API -- launched only via
 * [PGPaymentContract] / [com.pgsdk.core.PGPaymentLauncher].
 */
internal class PGCheckoutActivity : AppCompatActivity() {

    private lateinit var binding: PgActivityCheckoutBinding
    private lateinit var request: PGPaymentRequest

    private val viewModel: PGCheckoutViewModel by viewModels {
        PGCheckoutViewModelFactory(applicationContext, request, PGPaymentSDK.requireConfig())
    }

    private val upiAppLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onExternalFlowReturned()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val extractedRequest = extractRequest()
        if (extractedRequest == null) {
            super.onCreate(savedInstanceState)
            finishCancelled()
            return
        }
        request = extractedRequest

        // Merchant theme override (must extend Theme.PGSdk); applied before super.onCreate
        // so fragments restored from saved state inflate with it too.
        runCatching { PGPaymentSDK.requireConfig().theme.styleRes }.getOrNull()?.let(::setTheme)

        super.onCreate(savedInstanceState)
        binding = PgActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.pgToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        binding.pgToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.pgToolbar.setNavigationOnClickListener { onToolbarNavigation() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onToolbarNavigation()
        })

        observeState()
        observeEvents()
    }

    private fun extractRequest(): PGPaymentRequest? = if (android.os.Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(PGPaymentContract.EXTRA_REQUEST, PGPaymentRequest::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(PGPaymentContract.EXTRA_REQUEST)
    }

    private fun onToolbarNavigation() {
        when (viewModel.uiState.value) {
            is PGCheckoutUiState.UpiOptions,
            is PGCheckoutUiState.BankSelection,
            is PGCheckoutUiState.CardEntry -> viewModel.backToMethodSelection()

            is PGCheckoutUiState.MethodSelection,
            is PGCheckoutUiState.Failed -> confirmCancel()

            is PGCheckoutUiState.WebRedirect,
            is PGCheckoutUiState.Processing -> confirmCancel()

            is PGCheckoutUiState.Loading,
            is PGCheckoutUiState.Terminal -> Unit
        }
    }

    private fun confirmCancel() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pg_cancel_confirm_title)
            .setMessage(R.string.pg_cancel_confirm_message)
            .setPositiveButton(R.string.pg_cancel_confirm_yes) { _, _ -> viewModel.cancel() }
            .setNegativeButton(R.string.pg_cancel_confirm_no, null)
            .show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is PGCheckoutEvent.LaunchUpiApp -> launchUpiApp(event.intent)
                        is PGCheckoutEvent.ShowToast -> Toast.makeText(
                            this@PGCheckoutActivity,
                            event.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun launchUpiApp(intent: Intent) {
        runCatching { upiAppLauncher.launch(intent) }
            .onFailure {
                Toast.makeText(this, getString(R.string.pg_upi_no_apps), Toast.LENGTH_LONG).show()
                viewModel.cancel()
            }
    }

    private fun render(state: PGCheckoutUiState) {
        supportActionBar?.setDisplayHomeAsUpEnabled(state !is PGCheckoutUiState.Loading)

        val fragment: Fragment? = when (state) {
            is PGCheckoutUiState.Loading -> null
            is PGCheckoutUiState.MethodSelection -> PGMethodSelectionFragment.newInstance()
            is PGCheckoutUiState.UpiOptions -> PGUpiOptionsFragment.newInstance()
            is PGCheckoutUiState.BankSelection -> PGBankSelectionFragment.newInstance()
            is PGCheckoutUiState.CardEntry -> PGCardEntryFragment.newInstance()
            is PGCheckoutUiState.WebRedirect -> PGWebRedirectFragment.newInstance(state.url)
            is PGCheckoutUiState.Processing -> PGProcessingFragment.newInstance(state.message)
            is PGCheckoutUiState.Failed -> PGErrorFragment.newInstance(state.error)
            is PGCheckoutUiState.Terminal -> {
                showFragment(PGResultFragment.newInstance(state.result))
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(RESULT_DISPLAY_MILLIS)
                    finishWithResult(state.result)
                }
                return
            }
        }

        if (fragment != null) showFragment(fragment)
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.pgFragmentContainer, fragment)
            .commitAllowingStateLoss()
    }

    private fun finishWithResult(result: PGPaymentResult) {
        val data = Intent().putExtra(PGPaymentContract.EXTRA_RESULT, result)
        setResult(RESULT_OK, data)
        finish()
    }

    private fun finishCancelled() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val RESULT_DISPLAY_MILLIS = 1400L
    }
}
