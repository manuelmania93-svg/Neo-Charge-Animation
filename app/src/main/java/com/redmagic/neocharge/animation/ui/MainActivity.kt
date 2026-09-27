package com.redmagic.neocharge.animation.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.redmagic.neocharge.animation.service.ChargingService

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("neocharge_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#09090C"))
            setPadding(50, 70, 50, 50)
        }

        val scroll = ScrollView(this).apply {
            addView(root)
            setBackgroundColor(Color.parseColor("#09090C"))
        }

        val title = TextView(this).apply {
            text = "\u26A1 NEO CHARGE"
            textSize = 28f
            setTextColor(Color.parseColor("#FF2244"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setShadowLayer(25f, 0f, 0f, Color.parseColor("#FF0033"))
        }

        val subtitle = TextView(this).apply {
            text = "HARDWARE TELEMETRY ENGINE"
            textSize = 12f
            setTextColor(Color.parseColor("#888899"))
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = 0.2f
            setPadding(0, 8, 0, 45)
        }

        root.addView(title)
        root.addView(subtitle)

        val permCard = createCyberCard()
        permCard.addView(createSectionTitle("SYSTEM PERMISSIONS"))

        val btnOverlay = createCyberButton("1. ALLOW 'APPEAR ON TOP'", "#FF1E38") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Appear On Top Granted! \u2713", Toast.LENGTH_SHORT).show()
            }
        }
        permCard.addView(btnOverlay)

        val btnBattery = createCyberButton("2. DISABLE BATTERY LIMITS", "#22222A") {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Battery Exemption Active! \u2713", Toast.LENGTH_SHORT).show()
            }
        }
        permCard.addView(btnBattery)
        root.addView(permCard)

        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 35) })

        val optCard = createCyberCard()
        optCard.addView(createSectionTitle("OVERLAY BEHAVIOR"))

        val switchPermanent = Switch(this).apply {
            text = "Stay Visible While Charging"
            setTextColor(Color.WHITE)
            textSize = 14f
            isChecked = prefs.getBoolean("perm_mode", true)
            setPadding(0, 15, 0, 15)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("perm_mode", isChecked).apply()
            }
        }
        optCard.addView(switchPermanent)

        val descText = TextView(this).apply {
            text = "Stays glowing until unplugged (tap anywhere or unplug to close)."
            textSize = 11f
            setTextColor(Color.parseColor("#777788"))
            setPadding(0, 0, 0, 20)
        }
        optCard.addView(descText)
        root.addView(optCard)

        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 40) })

        val btnActivate = createCyberButton("ARM NEOCHARGE ENGINE", "#E60026") {
            val intent = Intent(this, ChargingService::class.java)
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "\u26A1 NeoCharge Armed & Ready!", Toast.LENGTH_LONG).show()
        }
        root.addView(btnActivate)

        val btnTest = createCyberButton("TEST ANIMATION PREVIEW", "#16161E") {
            val intent = Intent(this, ChargingService::class.java).apply {
                action = "PREVIEW_ANIMATION"
            }
            ContextCompat.startForegroundService(this, intent)
        }
        root.addView(btnTest)

        setContentView(scroll)
    }

    private fun createCyberCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121218"))
                setStroke(3, Color.parseColor("#331822"))
                cornerRadius = 24f
            }
        }
    }

    private fun createSectionTitle(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FF2244"))
            letterSpacing = 0.15f
            setPadding(0, 0, 0, 25)
        }
    }

    private fun createCyberButton(txt: String, hexColor: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = txt
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(30, 35, 30, 35)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 15) }

            background = GradientDrawable().apply {
                setColor(Color.parseColor(hexColor))
                cornerRadius = 18f
                setStroke(2, Color.parseColor("#FF2244"))
            }
            setOnClickListener { onClick() }
        }
    }
}
