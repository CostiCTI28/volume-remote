package com.costi.volumeremote

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sendRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ipInput = findViewById<EditText>(R.id.ipInput)
        val tokenInput = findViewById<EditText>(R.id.tokenInput)
        val seekBar = findViewById<SeekBar>(R.id.volumeSeekBar)
        val statusText = findViewById<TextView>(R.id.statusText)
        val muteButton = findViewById<Button>(R.id.muteButton)
        val unmuteButton = findViewById<Button>(R.id.unmuteButton)
        val prefs = getSharedPreferences("volume_remote", MODE_PRIVATE)

        ipInput.setText(prefs.getString("ip", "192.168.1.100:5050"))
        tokenInput.setText(prefs.getString("token", "schimba-ma"))

        fun baseUrl() = "http://${ipInput.text}"
        fun token() = tokenInput.text.toString()

        fun saveSettings() {
            prefs.edit()
                .putString("ip", ipInput.text.toString())
                .putString("token", tokenInput.text.toString())
                .apply()
        }

        fun sendGet(path: String, onResult: (String?) -> Unit) {
            executor.execute {
                try {
                    val url = URL("${baseUrl()}$path")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    val code = conn.responseCode
                    val text = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    mainHandler.post { onResult(if (code == 200) text else null) }
                } catch (e: Exception) {
                    mainHandler.post { onResult(null) }
                }
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                sendRunnable?.let { mainHandler.removeCallbacks(it) }
                val runnable = Runnable {
                    saveSettings()
                    sendGet("/volume?level=$value&token=${token()}") { result ->
                        statusText.text = if (result != null) "Volum: $value%" else "Eroare de conexiune"
                    }
                }
                sendRunnable = runnable
                mainHandler.postDelayed(runnable, 150)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        muteButton.setOnClickListener {
            saveSettings()
            sendGet("/mute?token=${token()}") { result ->
                statusText.text = if (result != null) "Mut activat" else "Eroare de conexiune"
            }
        }

        unmuteButton.setOnClickListener {
            saveSettings()
            sendGet("/unmute?token=${token()}") { result ->
                statusText.text = if (result != null) "Mut dezactivat" else "Eroare de conexiune"
            }
        }

        sendGet("/volume?token=${token()}") { result ->
            if (result != null) {
                val level = Regex("\"volume\"\\s*:\\s*(\\d+)").find(result)?.groupValues?.get(1)?.toIntOrNull()
                if (level != null) {
                    seekBar.progress = level
                    statusText.text = "Volum: $level%"
                }
            }
        }
    }
}
