# Bound 5G — MediaTek Band & Carrier Aggregation Controller

<div align="center">
  <h2>Bound 5G</h2>
  <h3>MediaTek Dimensity 5G/4G Band Selector & Carrier Aggregation Controller for Android (Non-Rooted)</h3>
</div>

---

## 🚀 Overview
**Bound 5G** is a specialized Android application designed for **MediaTek Dimensity devices (such as Xiaomi Poco X7 Pro running HyperOS / MIUI)** and unrooted Android smartphones.

It provides:
1. **Direct 1-Tap MediaTek BandSelect Launch**: Bypasses system restrictions via Shizuku shell IPC to launch hidden `com.mediatek.engineermode.bandselect.BandSelect` where users can check/uncheck B1, B3, B7, B42, and n78 bands directly on their phone.
2. **MediaTek Carrier Aggregation Config**: Direct 1-tap jump to MTK Carrier Aggregation (`CaActivity`) settings.
3. **Live Cellular & CA Telemetry**: Real-time detection of Primary Serving Cell (PCell), Aggregated Secondary Carriers (SCells), Bandwidth, RSRP, RSRQ, SINR, PCI, and TAC.
4. **Operator Presets for Iran**: Ready-made frequency recipes for **MCI (همراه اول)**, **Irancell (ایرانسل)**, **TD-LTE (مبین‌نت / زیتل)**, and **Low Latency Gaming**.
5. **Quick Settings Tiles & Home Screen Widget**: 1-tap band mode launching and 5G mode cycling directly from the notification shade and home screen.

---

## 🛠️ How It Works (Without Root)
Standard Android prohibits unprivileged third-party apps from modifying radio band bitmasks directly. 
Bound 5G solves this by:
* Leveraging **Shizuku's Wireless Debugging** (UID 2000 `shell`) to execute privileged activity start commands (`am start -n com.mediatek.engineermode...`), granting users instant access to MediaTek's native hardware band toggle interface without root or bootloader unlocking!
* Providing AOSP RadioInfo (`*#*#4636#*#*`) and MediaTek secret dialer (`*#*#3646633#*#*`) fallbacks.

---

## 📱 How to Use on Xiaomi / Poco (HyperOS)
1. Install **Shizuku** from [shizuku.rikka.app](https://shizuku.rikka.app).
2. In **Developer Options**, turn on:
   * **Wireless Debugging**
   * **Install via USB**
   * **USB debugging (Security settings)**
3. Pair and Start Shizuku via Wireless Debugging.
4. Open **Bound 5G** and tap **"MTK BandMode"**.
5. Select your SIM card, check your desired bands (e.g. B3 + B7 + n78), and tap **SET**!

---

## 📜 License
Distributed under the MIT License.
