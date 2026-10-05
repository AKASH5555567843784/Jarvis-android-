package com.jarvis.ai

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var llm: LlmInference? = null
    private val bg = Executors.newSingleThreadExecutor()
    @Volatile private var busy = false
    private var lastState = "loading"
    private val modelFile get() = File(filesDir, "model.task")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#02070D")
        window.navigationBarColor = Color.parseColor("#02070D")
        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#02070D"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(Bridge(), "AndroidLLM")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView?, url: String?) = js("onModelState('$lastState')")
            }
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(web)
        initLlm()
    }

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }
    private fun state(s: String) { lastState = s; js("window.onModelState&&onModelState('$s')") }

    private fun initLlm() = bg.execute {
        if (!modelFile.exists()) { state("nomodel"); return@execute }
        state("loading")
        try {
            llm?.close()
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(384)
                .setMaxTopK(40)
                .setResultListener { part, done ->
                    js("onLlmToken("+JSONObject.quote(part ?: "")+","+done+")")
                    if (done) busy = false
                }
                .setErrorListener { e ->
                    busy = false
                    js("onLlmToken("+JSONObject.quote("Error: " + e.message)+",true)")
                }
                .build()
            llm = LlmInference.createFromOptions(this, opts)
            state("ready")
        } catch (t: Throwable) { llm = null; state("error") }
    }

    private fun prompt(q: String) =
        "You are JARVIS, a brief, helpful offline assistant. Reply in the user's language " +
        "(Hindi, Hinglish or English) in at most 3 short sentences.\nUser: $q\nJARVIS:"

    inner class Bridge {
        @JavascriptInterface fun status() = lastState
        @JavascriptInterface fun pickModel() = runOnUiThread {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, 7)
        }
        @JavascriptInterface fun ask(q: String) {
            val m = llm ?: return
            if (busy) return
            busy = true
            bg.execute {
                try { m.generateResponseAsync(prompt(q)) }
                catch (t: Throwable) { busy = false; js("onLlmToken('Error',true)") }
            }
        }
    }

    @Deprecated("simple picker")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (req != 7 || res != RESULT_OK) return
        state("loading")
        bg.execute {
            try {
                contentResolver.openInputStream(uri)?.use { i -> modelFile.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
                initLlm()
            } catch (t: Throwable) { state("error") }
        }
    }

    override fun onDestroy() { llm?.close(); super.onDestroy() }
}