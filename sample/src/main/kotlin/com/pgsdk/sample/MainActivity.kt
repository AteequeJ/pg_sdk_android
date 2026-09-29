package com.pgsdk.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pgsdk.core.PGPaymentLauncher
import com.pgsdk.model.PGPaymentRequest
import com.pgsdk.model.PGPaymentResult
import com.pgsdk.sample.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val paymentLauncher = PGPaymentLauncher.create(this) { result -> onResult(result) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.payButton.setOnClickListener { startDemoPayment() }
    }

    private fun startDemoPayment() {
        binding.resultText.text = getString(R.string.pg_sample_status_launching)
        lifecycleScope.launch {
            // In a real app this hits *your* backend, which creates the order with your
            // payment gateway using server-side secret credentials -- never generate
            // orderId/orderToken on-device. See MerchantBackendClient.
            val order = MerchantBackendClient.createOrder(amountMinor = 49_900, currency = "INR")
            val request = PGPaymentRequest(
                orderId = order.orderId,
                orderToken = order.orderToken,
                amountMinor = order.amountMinor,
                currency = order.currency,
                description = "Sample order"
            )
            paymentLauncher.launch(request)
        }
    }

    private fun onResult(result: PGPaymentResult) {
        binding.resultText.text = when (result) {
            is PGPaymentResult.Success -> getString(R.string.pg_sample_result_success, result.paymentId)
            is PGPaymentResult.Pending -> getString(R.string.pg_sample_result_pending)
            is PGPaymentResult.Failure -> getString(R.string.pg_sample_result_failed, result.error.message)
            is PGPaymentResult.Cancelled -> getString(R.string.pg_sample_result_cancelled)
        }
    }
}
