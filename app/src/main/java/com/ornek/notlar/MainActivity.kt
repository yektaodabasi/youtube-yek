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
    private var sadeceVideo = true
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

        sadeceVideo = prefs.getBoolean("sadeceVideo", true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView, url: String?) = enjekte(view)
            override fun onPageFinished(view: WebView, url: String?) = enjekte(view)
        }
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

        val btnSade = findViewById<ImageButton>(R.id.btnSade)
        btnSade.alpha = if (sadeceVideo) 1f else 0.4f
        btnSade.setOnClickListener {
            sadeceVideo = !sadeceVideo
            prefs.edit().putBoolean("sadeceVideo", sadeceVideo).apply()
            btnSade.alpha = if (sadeceVideo) 1f else 0.4f
            webView.reload()
        }

        if (siteler.isEmpty()) siteEkle()
    }

    private fun enjekte(v: WebView) {
        if (sadeceVideo) v.evaluateJavascript(SADECE_VIDEO_JS, null)
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

    companion object {
        private val SADECE_VIDEO_JS = """
(function () {
  if (window.__sv) return;
  window.__sv = true;

  var EMBED   = /youtube|youtu\.be|vimeo|dailymotion|twitch|rutube|ok\.ru|streamable|embed|player|video/i;
  var REKLAM  = /doubleclick|googlesyndication|adservice|adsystem|recaptcha|hcaptcha|facebook\.com\/plugins|disqus/i;
  var VIDLINK = /watch|video|\/v\/|embed|izle|clip|episode|bolum|film|dizi|shorts/i;

  var css =
    'html,body{overflow:hidden!important;background:#000!important;margin:0!important}' +
    'body>:not(#__sv){visibility:hidden!important}' +
    '#__sv{position:fixed!important;top:0!important;left:0!important;right:0!important;bottom:0!important;' +
      'z-index:2147483647!important;overflow-y:auto!important;background:#000!important;' +
      'visibility:visible!important;display:block!important;margin:0!important;padding:0 0 40px!important}' +
    '#__sv video,#__sv iframe{display:block!important;width:100vw!important;max-width:100vw!important;' +
      'margin:0 0 8px!important;position:static!important;transform:none!important;opacity:1!important;' +
      'visibility:visible!important;border:0!important;background:#000!important}' +
    '#__sv video{height:auto!important;min-height:56.25vw!important;max-height:85vh!important}' +
    '#__sv iframe{height:56.25vw!important}' +
    '#__sv_l a{display:block;color:#ddd;padding:12px 16px;border-bottom:1px solid #222;' +
      'text-decoration:none;font:16px/1.3 sans-serif}' +
    '#__sv_m{color:#888;font:14px sans-serif;text-align:center;padding:40px 16px}';

  function kap() {
    var k = document.getElementById('__sv');
    if (!k) {
      k = document.createElement('div');
      k.id = '__sv';
      k.innerHTML = '<div id="__sv_v"></div><div id="__sv_l"></div>';
      document.body.appendChild(k);
    }
    if (!document.getElementById('__sv_css')) {
      var s = document.createElement('style');
      s.id = '__sv_css';
      s.textContent = css;
      (document.head || document.documentElement).appendChild(s);
    }
    return k;
  }

  function videoMu(el) {
    if (el.tagName === 'VIDEO') return true;
    var src = el.getAttribute('src') || el.getAttribute('data-src') || '';
    return EMBED.test(src) && !REKLAM.test(src);
  }

  function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
  }

  function linkler(l) {
    var gor = {}, html = '', n = 0;
    var host = location.hostname.replace(/^www\./, '');
    var as = document.getElementsByTagName('a');
    for (var i = 0; i < as.length && n < 300; i++) {
      var a = as[i], h = a.href;
      if (!h || h.indexOf('http') !== 0) continue;
      if (a.hostname.replace(/^www\./, '') !== host) continue;
      h = h.split('#')[0];
      if (h === location.href.split('#')[0] || gor[h]) continue;
      var img = a.querySelector('img,picture,video');
      if (!img && !VIDLINK.test(a.pathname + a.search)) continue;
      var b = (a.getAttribute('title') || a.getAttribute('aria-label') ||
               (img && img.getAttribute('alt')) || a.textContent || '')
               .replace(/\s+/g, ' ').trim();
      if (!b) continue;
      gor[h] = 1; n++;
      html += '<a href="' + esc(h) + '">' + esc(b.slice(0, 120)) + '</a>';
    }
    l.innerHTML = n ? html : '<div id="__sv_m">Bu sayfada video bulunamadı</div>';
  }

  var hazir = false, t;

  function topla() {
    if (!document.body) return;
    var k = kap();
    var v = document.getElementById('__sv_v'), l = document.getElementById('__sv_l');
    var adaylar = document.querySelectorAll('video, iframe'), sayi = 0;
    for (var i = 0; i < adaylar.length; i++) {
      var el = adaylar[i];
      if (k.contains(el)) { sayi++; continue; }
      if (!videoMu(el)) continue;
      el.removeAttribute('width'); el.removeAttribute('height');
      if (el.tagName === 'VIDEO') {
        el.controls = true;
        el.setAttribute('playsinline', '');
      } else {
        if (!el.getAttribute('src') && el.getAttribute('data-src')) el.src = el.getAttribute('data-src');
        el.setAttribute('allow', 'autoplay; fullscreen; encrypted-media; picture-in-picture');
        el.setAttribute('allowfullscreen', '');
      }
      v.appendChild(el); sayi++;
    }
    if (sayi > 0) { if (l.firstChild) l.innerHTML = ''; }
    else if (hazir && !l.querySelector('a')) linkler(l);
  }

  function basla() {
    topla();
    new MutationObserver(function () {
      clearTimeout(t); t = setTimeout(topla, 300);
    }).observe(document.documentElement,
      { childList: true, subtree: true, attributes: true, attributeFilter: ['src', 'data-src'] });
    setTimeout(function () { hazir = true; topla(); }, 1500);
    var son = location.href;
    setInterval(function () {
      if (location.href !== son) {
        son = location.href; hazir = false;
        var l = document.getElementById('__sv_l'); if (l) l.innerHTML = '';
        setTimeout(function () { hazir = true; topla(); }, 1500);
      }
    }, 500);
  }

  if (document.body) basla(); else document.addEventListener('DOMContentLoaded', basla);
})();
""".trimIndent()
    }
}
