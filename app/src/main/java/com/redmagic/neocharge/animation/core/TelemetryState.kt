package com.redmagic.neocharge.animation.core

data class TelemetryState(
    val basePercent: Int = 88,
    val displayPercentage: Float = 88.00f,
    val watts: Float = 0.0f,
    val tempCelsius: Float = 0.0f,
    val isConnected: Boolean = false,
    val chargeGrade: String = "FAST CHARGE"
)
