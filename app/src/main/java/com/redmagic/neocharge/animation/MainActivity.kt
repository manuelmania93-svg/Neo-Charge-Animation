package com.redmagic.neocharge.animation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 100, 60, 60)
        }

        // Button 1: Overlay Permission
        val btnOverlay = Button(this).apply {
            text = "1. Enable 'Appear On Top' Permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Already granted!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Button 2: Battery Exemption (Crucial for Samsung/Xiaomi/OnePlus)
        val btnBattery = Button(this).apply {
            text = "2. Disable Battery Optimization"
            setOnClickListener {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Already unconstrained!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Button 3: Start Service
        val btnStart = Button(this).apply {
            text = "3. Activate NeoCharge Engine"
            setOnClickListener {
                val intent = Intent(this@MainActivity, ChargingService::class.java)
                ContextCompat.startForegroundService(this@MainActivity, intent)
                Toast.makeText(this@MainActivity, "NeoCharge is armed! Plug in your charger.", Toast.LENGTH_LONG).show()
            }
        }

        layout.addView(btnOverlay)
        layout.addView(btnBattery)
        layout.addView(btnStart)
        setContentView(layout)
    }
}
