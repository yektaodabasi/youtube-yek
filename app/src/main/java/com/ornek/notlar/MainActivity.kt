package com.ornek.notlar

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    data class Site(val ad: String, val url: String)

    private lateinit var kok: View
    private lateinit var webView: WebView
    private lateinit var spinner: Spinner
    private lateinit var adapter: ArrayAdapter<String>
    private var fullscreenView: View? = null
    private var kilitli = true
    private val siteler = mutableListOf<Site>()
    private val prefs by lazy { getSharedPreferences("ayar", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)
        kok = findViewById(R.id.kok)
        webView = findViewById(R.id.webView)
        spinner = findViewById(R.id.siteSpinner)
        kok.visibility = View.INVISIBLE

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullscreenView != null -> tamEkranKapat()
                    ::adapter.isInitialized && webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (kilitli) kilitAc()
    }

    override fun onStop() {
        super.onStop()
        kilitli = true
        kok.visibility = View.INVISIBLE
        webView.clearHistory()
        webView.clearCache(true)
        webView.clearFormData()
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
    }

    private fun kilitAc() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                    kilitli = false
                    kok.visibility = View.VISIBLE
                    arayuzuKur()
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) { finish() }
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Kilit")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build())
    }

    private fun arayuzuKur() {
        if (::adapter.isInitialized) return

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                fullscreenView = view
                (window.decorView as FrameLayout).addView(view)
                kok.visibility = View.GONE
            }
            override fun onHideCustomView() { tamEkranKapat() }
        }

        siteleriYukle()
        adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            siteler.map { it.ad }.toMutableList())
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                webView.loadUrl(siteler[pos].url)
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        findViewById<ImageButton>(R.id.btnEkle).setOnClickListener { siteEkle() }
        findViewById<ImageButton>(R.id.btnSil).setOnClickListener { siteSil() }
        if (siteler.isEmpty()) siteEkle()
    }

    private fun tamEkranKapat() {
        val v = fullscreenView ?: return
        (window.decorView as FrameLayout).removeView(v)
        fullscreenView = null
        kok.visibility = View.VISIBLE
    }

    private fun siteleriYukle() {
        siteler.clear()
        val dizi = JSONArray(prefs.getString("siteler", "[]"))
        for (i in 0 until dizi.length()) {
            val o = dizi.getJSONObject(i)
            siteler += Site(o.getString("ad"), o.getString("url"))
        }
    }

    private fun siteleriKaydet() {
        val dizi = JSONArray()
        siteler.forEach { dizi.put(JSONObject().put("ad", it.ad).put("url", it.url)) }
        prefs.edit().putString("siteler", dizi.toString()).apply()
        adapter.clear()
        adapter.addAll(siteler.map { it.ad })
    }

    private fun siteEkle() {
        val kutu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val adGir = EditText(this).apply { hint = "Ad" }
        val urlGir = EditText(this).apply {
            hint = "site.com"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        kutu.addView(adGir)
        kutu.addView(urlGir)
        AlertDialog.Builder(this).setTitle("Site ekle").setView(kutu)
            .setPositiveButton("Ekle") { _, _ ->
                var url = urlGir.text.toString().trim()
                if (url.isEmpty()) return@setPositiveButton
                if (!url.startsWith("http")) url = "https://$url"
                val ad = adGir.text.toString().trim()
                    .ifEmpty { url.removePrefix("https://").removePrefix("http://") }
                siteler += Site(ad, url)
                siteleriKaydet()
                spinner.setSelection(siteler.size - 1)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun siteSil() {
        val i = spinner.selectedItemPosition
        if (i < 0 || i >= siteler.size) return
        AlertDialog.Builder(this).setMessage("${siteler[i].ad} silinsin mi?")
            .setPositiveButton("Sil") { _, _ ->
                siteler.removeAt(i)
                siteleriKaydet()
                if (siteler.isEmpty()) webView.loadUrl("about:blank")
            }
            .setNegativeButton("İptal", null)
            .show()
    }
}
