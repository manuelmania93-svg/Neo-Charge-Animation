package com.redmagic.neocharge.animation.core

data class TelemetryState(
    val basePercent: Int = 0,
    val displayPercentage: Float = 0.0f,
    val watts: Float = 0.0f,
    val tempCelsius: Float = 0.0f,
    val isConnected: Boolean = false,
    val chargeGrade: String = "CHARGING"
)
