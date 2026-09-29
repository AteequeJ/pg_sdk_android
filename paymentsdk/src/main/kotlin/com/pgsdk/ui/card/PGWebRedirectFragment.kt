package com.pgsdk.ui.card

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.pgsdk.databinding.PgFragmentWebRedirectBinding

/**
 * Hosts a bank/3DS redirect URL in a [WebView]. Purely presentational -- this fragment
 * never inspects the URL itself; [PGCheckoutViewModel] polls the payment attempt's
 * status in the background and moves off this screen once a terminal result is known.
 */
internal class PGWebRedirectFragment : Fragment() {

    private var _binding: PgFragmentWebRedirectBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PgFragmentWebRedirectBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pgWebView.settings.javaScriptEnabled = true
        binding.pgWebView.settings.domStorageEnabled = true
        binding.pgWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                binding.pgWebProgressBar.isVisible = false
            }
        }

        if (savedInstanceState == null) {
            binding.pgWebView.loadUrl(requireArguments().getString(ARG_URL)!!)
        }
    }

    override fun onDestroyView() {
        binding.pgWebView.apply {
            stopLoading()
            webViewClient = WebViewClient()
        }
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_URL = "pg_arg_url"

        fun newInstance(url: String) = PGWebRedirectFragment().apply {
            arguments = Bundle().apply { putString(ARG_URL, url) }
        }
    }
}
