/*
    MIT License

    Copyright (c) 2018 James58899

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
 */

package com.antony.muzei.pixiv.login

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.antony.muzei.pixiv.PixivInstrumentation
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.common.PixivMuzeiActivity
import com.antony.muzei.pixiv.databinding.ActivityLoginWebviewBinding
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.security.SecureRandom

class LoginActivityWebview : PixivMuzeiActivity(), CoroutineScope by CoroutineScope(Dispatchers.Main + SupervisorJob()) {
    companion object {
        private val allowDomain = listOf("app-api.pixiv.net", "accounts.pixiv.net", "oauth.secure.pixiv.net")
    }

    private lateinit var code_verifier: String
    private var bypassDomainCheck = false

    private var mBinding: ActivityLoginWebviewBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityLoginWebviewBinding.inflate(layoutInflater)
        setContentView(mBinding!!.root)

        val webView: WebView = findViewById(R.id.webview)
        @SuppressLint("SetJavaScriptEnabled")
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = webView.settings.userAgentString.replace(Regex("Version/\\d\\.\\d\\s"), "") // Hide WebView version
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url: Uri = request.url
                if (url.scheme.equals("pixiv")) {
                    launch(Dispatchers.IO) {
                        val oauthResponse = PixivInstrumentation.login(code_verifier, url.getQueryParameter("code")!!)
                        if (!oauthResponse.isHas_error) {
                            withContext(Dispatchers.Main) {
                                PixivInstrumentation.updateTokenLocal(applicationContext, oauthResponse.pixivOauthResponse)
                                webView.destroy()
                                // Returns the username for immediate consumption by MainPreferenceFragment
                                // Sets the "Logged in as XXX" preference summary
                                val username: Intent = Intent().putExtra("username", oauthResponse.pixivOauthResponse.user.name)
                                setResult(RESULT_OK, username)
                                finish()
                            }
                        }
                    }

                    return true
                }
                // Disallow user use WebView browser other page
                if (url.host !in allowDomain && !url.toString().startsWith("https://www.pixiv.net/logout.php")) {
                    if (url.host == "socialize.gigya.com") bypassDomainCheck = true else if (!bypassDomainCheck) {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                        return true
                    }
                } else if (bypassDomainCheck) {
                    bypassDomainCheck = false
                } // Enable check if back to pixiv.
                return false
            }
        }
        val (code, hash) = generateCodeAndHash()
        this.code_verifier = code
        webView.loadUrl("https://app-api.pixiv.net/web/v1/login?code_challenge=$hash&code_challenge_method=S256&client=pixiv-android")
    }

    private fun generateCodeAndHash(): Pair<String, String> {
        val code = ByteArray(32).apply(SecureRandom()::nextBytes).let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }

        return code to Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(code.encodeToByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
