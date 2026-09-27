# NeoCharge Animation ⚡

A universal, futuristic cyber-hexagon charging HUD for **any Android device** (Samsung, Pixel, Xiaomi, OnePlus, REDMAGIC, and more).

Inspired by the iconic REDMAGIC fast-charge screen, **NeoCharge Animation** displays a high-tech glowing hexagon overlay with a live 4-digit decimal ticker (`XX.YY%`) whenever your phone is connected to power.

### 🚀 Highlights
- **Universal Compatibility:** Works across all Android OEM skins (One UI, HyperOS, OxygenOS, Stock Pixel, and REDMAGIC OS).
- **True 4-Digit Decimal Ticker:** Real-time micro-percentage ticks (e.g., `60.16%`) simulate rapid power flow.
- **Smart Charge Detection:** Automatically switches between `FAST CHARGE`, `WIRELESS CHARGE`, `USB CHARGE`, and `CHARGING` based on input current.
- **Live Crackle-Glow Animation:** ~90 short-lived electric veins spawn white-hot and cool to deep red as they age, radiating from the hex outline inward but never crossing into the clean percentage readout. A pulsing glow halo breathes behind the hexagon, and a wave line ripples under the NEOCHARGE wordmark.
- **Original Visual Identity:** Distinct from stock "charging animation" apps — branching crackle veins instead of a single lightning ring, own wordmark and color treatment instead of reskinning any existing app's look.
- **AMOLED-Optimized:** Built-in black dimming keeps power consumption minimal while looking ultra-clean on OLED displays.
- **Zero Lockscreen Replacement:** Lightweight `WindowManager` overlay that disappears after 7 seconds or with a single tap.

### Performance note
The vein count (`targetVeinCount` in `HexagonOverlayView.kt`) is set to 90, tuned as a balance between visual density and real-device GPU/battery cost — each vein draws two blurred `Path` strokes per frame. Raise it for a denser look during testing, or lower it if you see frame drops on lower-end hardware.

### Setup
1. Enable "Appear on top" permission
2. Disable battery optimization for the app
3. Activate the NeoCharge engine

### Permissions required
- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `RECEIVE_BOOT_COMPLETED`

### Topics
android, kotlin, charging-animation, lockscreen-overlay, redmagic, cyberpunk, battery-indicator, android-customization
