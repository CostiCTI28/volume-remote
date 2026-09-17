package com.costi.volumeremote

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSend: Runnable? = null
    private var repeatRunnable: Runnable? = null

    private var currentVolume = 0

    // repetare la apasare lunga
    private val holdDelay = 400L      // cat astepti pana porneste repetarea
    private val fastInterval = 60L    // cat de des se repeta dupa aceea

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ipInput = findViewById<EditText>(R.id.ipInput)
        val tokenInput = findViewById<EditText>(R.id.tokenInput)
        val volumeValue = findViewById<TextView>(R.id.volumeValue)
        val volumeBar = findViewById<ProgressBar>(R.id.volumeBar)
        val hostLabel = findViewById<TextView>(R.id.hostLabel)
        val statusText = findViewById<TextView>(R.id.statusText)
        val plusButton = findViewById<Button>(R.id.plusButton)
        val minusButton = findViewById<Button>(R.id.minusButton)
        val muteButton = findViewById<Button>(R.id.muteButton)
        val unmuteButton = findViewById<Button>(R.id.unmuteButton)
        val testButton = findViewById<Button>(R.id.testButton)

        val prefs = getSharedPreferences("volume_remote", MODE_PRIVATE)
        ipInput.setText(prefs.getString("ip", ""))
        tokenInput.setText(prefs.getString("token", "schimba-ma"))

        fun baseUrl() = "http://${ipInput.text.toString().trim()}"
        fun token() = tokenInput.text.toString().trim()

        fun saveSettings() {
            prefs.edit()
                .putString("ip", ipInput.text.toString().trim())
                .putString("token", token())
                .apply()
        }

        fun render() {
            volumeValue.text = currentVolume.toString()
            volumeBar.progress = currentVolume
        }

        fun sendGet(path: String, onResult: (String?, String?) -> Unit) {
            executor.execute {
                try {
                    val conn = URL("${baseUrl()}$path").openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val code = conn.responseCode
                    val text = if (code == 200) {
                        conn.inputStream.bufferedReader().readText()
                    } else {
                        conn.errorStream?.bufferedReader()?.readText() ?: ""
                    }
                    conn.disconnect()
                    mainHandler.post {
                        if (code == 200) onResult(text, null)
                        else onResult(null, "HTTP $code — $text")
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        onResult(null, "${e.javaClass.simpleName}: ${e.message}\n${baseUrl()}$path")
                    }
                }
            }
        }

        // trimite volumul, dar nu mai des decat e nevoie (ultima valoare castiga)
        fun pushVolume() {
            pendingSend?.let { mainHandler.removeCallbacks(it) }
            val r = Runnable {
                saveSettings()
                sendGet("/volume?level=$currentVolume&token=${token()}") { result, err ->
                    if (result != null) {
                        statusText.text = ""
                        hostLabel.text = "conectat"
                    } else {
                        statusText.text = err
                        hostLabel.text = "fără conexiune"
                    }
                }
            }
            pendingSend = r
            mainHandler.postDelayed(r, 80)
        }

        fun step(delta: Int) {
            currentVolume = (currentVolume + delta).coerceIn(0, 100)
            render()
            pushVolume()
        }

        fun stopRepeat() {
            repeatRunnable?.let { mainHandler.removeCallbacks(it) }
            repeatRunnable = null
        }

        // apasare simpla = 1%, apasare lunga = repeta continuu
        fun bindHold(button: View, delta: Int) {
            button.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        step(delta)
                        val repeat = object : Runnable {
                            override fun run() {
                                step(delta)
                                mainHandler.postDelayed(this, fastInterval)
                            }
                        }
                        repeatRunnable = repeat
                        mainHandler.postDelayed(repeat, holdDelay)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        stopRepeat()
                        v.performClick()
                        true
                    }
                    else -> false
                }
            }
        }

        bindHold(plusButton, 1)
        bindHold(minusButton, -1)

        fun refresh() {
            saveSettings()
            sendGet("/volume?token=${token()}") { result, err ->
                if (result != null) {
                    Regex("\"volume\"\\s*:\\s*(\\d+)").find(result)?.groupValues?.get(1)?.toIntOrNull()
                        ?.let { currentVolume = it; render() }
                    hostLabel.text = "conectat"
                    statusText.text = ""
                } else {
                    hostLabel.text = "fără conexiune"
                    statusText.text = err
                }
            }
        }

        muteButton.setOnClickListener {
            saveSettings()
            sendGet("/mute?token=${token()}") { result, err ->
                statusText.text = if (result != null) "Sunet oprit" else err
            }
        }

        unmuteButton.setOnClickListener {
            saveSettings()
            sendGet("/unmute?token=${token()}") { result, err ->
                statusText.text = if (result != null) "Sunet pornit" else err
            }
        }

        testButton.setOnClickListener {
            saveSettings()
            statusText.text = "Verific..."
            sendGet("/ping?token=${token()}") { result, err ->
                if (result != null) {
                    val host = Regex("\"host\"\\s*:\\s*\"([^\"]+)\"").find(result)?.groupValues?.get(1)
                    hostLabel.text = host ?: "conectat"
                    statusText.text = "Conexiune reușită"
                    refresh()
                } else {
                    hostLabel.text = "fără conexiune"
                    statusText.text = err
                }
            }
        }

        render()
        if (ipInput.text.isNotBlank()) refresh()
    }

    override fun onPause() {
        super.onPause()
        repeatRunnable?.let { mainHandler.removeCallbacks(it) }
        repeatRunnable = null
    }
}
