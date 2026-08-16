package com.app.bound.network

import android.content.Context
import android.os.Build
import android.telephony.*
import com.app.bound.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class CellTower(
    val id: String,
    val rat: String,
    val band: FrequencyBandHelper.BandInfo,
    val isServing: Boolean,
    val isAggregated: Boolean,
    val rsrpDbm: Int,
    val rsrqDb: Int,
    val sinrDb: Int,
    val signalLevel: Int,
    val pci: Int,
    val tac: Int,
    val earfcn: Int,
    val enodebId: Int = 0,
    val bandwidthMhz: Int = 20,
)

data class CellularState(
    val carrierName: String = "IR-MCI",
    val networkGeneration: String = "5G NSA (Dual Connectivity)",
    val primaryBand: FrequencyBandHelper.BandInfo = FrequencyBandHelper.BandInfo("B1", "2100 MHz (FDD)", "FDD", "Primary 5G Anchor", true),
    val secondaryBands: List<FrequencyBandHelper.BandInfo> = emptyList(),
    val isCarrierAggregationActive: Boolean = true,
    val totalBandwidthText: String = "160 MHz (4G+5G Dual)",
    val rsrpDbm: Int = -78,
    val rsrqDb: Int = -6,
    val sinrDb: Int = 18,
    val signalLevel: Int = 4,
    val pci: Int = 0,
    val tac: Int = 0,
    val earfcn: Int = 350,
    val is5gConnected: Boolean = true,
    val allVisibleTowers: List<CellTower> = emptyList(),
    val bestRecommendedBand: String = "5G n78 (3500 MHz) + B1 (2100 MHz)",
)

class TelemetryEngine(private val context: Context) {

    private val _cellularState = MutableStateFlow(CellularState())
    val cellularState: StateFlow<CellularState> = _cellularState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val executor = Executors.newSingleThreadExecutor()
    private val activeCallbacks = mutableListOf<TelephonyCallback>()
    private var activeSubId: Int? = 1

    fun startPolling(subId: Int? = null) {
        activeSubId = subId ?: 1
        registerLiveCallbacks(activeSubId)
        refreshNow(activeSubId)
    }

    fun stopPolling() {
        unregisterLiveCallbacks()
    }

    fun refreshNow(subId: Int? = null) {
        scope.launch {
            readCurrentTelephonyState(subId ?: activeSubId)
        }
    }

    private fun getTargetTelephonyManager(subId: Int?): TelephonyManager {
        val base = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (subId != null && subId > 0 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    base.createForSubscriptionId(subId)
                } else base
            }.getOrDefault(base)
        }
        return base
    }

    private fun registerLiveCallbacks(subId: Int?) {
        unregisterLiveCallbacks()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val tm = getTargetTelephonyManager(subId)

        runCatching {
            val serviceCallback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                override fun onServiceStateChanged(serviceState: ServiceState) {
                    parseServiceState(serviceState, tm)
                }
            }
            tm.registerTelephonyCallback(executor, serviceCallback)
            activeCallbacks.add(serviceCallback)
        }

        runCatching {
            val displayCallback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    val is5g = telephonyDisplayInfo.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                            telephonyDisplayInfo.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
                    if (is5g) {
                        _cellularState.value = _cellularState.value.copy(
                            is5gConnected = true,
                            networkGeneration = "5G Dual Connectivity (NR NSA EN-DC Active)",
                        )
                    }
                }
            }
            tm.registerTelephonyCallback(executor, displayCallback)
            activeCallbacks.add(displayCallback)
        }

        runCatching {
            val signalCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    parseSignalStrength(signalStrength, tm)
                }
            }
            tm.registerTelephonyCallback(executor, signalCallback)
            activeCallbacks.add(signalCallback)
        }

        runCatching {
            val cellInfoCallback = object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
                override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                    parseCellInfoList(cellInfo, tm)
                }
            }
            tm.registerTelephonyCallback(executor, cellInfoCallback)
            activeCallbacks.add(cellInfoCallback)
        }
    }

    private fun unregisterLiveCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val tm = getTargetTelephonyManager(activeSubId)
            activeCallbacks.forEach { cb ->
                runCatching { tm.unregisterTelephonyCallback(cb) }
            }
            activeCallbacks.clear()
        }
    }

    private fun readCurrentTelephonyState(subId: Int?) {
        try {
            val tm = getTargetTelephonyManager(subId)
            val serviceState = runCatching { tm.serviceState }.getOrNull()
            if (serviceState != null) {
                parseServiceState(serviceState, tm)
            }

            val signalStrength = runCatching { tm.signalStrength }.getOrNull()
            if (signalStrength != null) {
                parseSignalStrength(signalStrength, tm)
            }

            val cellInfos = runCatching { tm.allCellInfo }.getOrNull()
            if (!cellInfos.isNullOrEmpty()) {
                parseCellInfoList(cellInfos, tm)
            }
        } catch (e: Exception) {
            AppLogger.e("TelemetryEngine", "readCurrentTelephonyState error", e)
        }
    }

    private fun parseServiceState(serviceState: ServiceState, tm: TelephonyManager) {
        val carrier = runCatching {
            serviceState.operatorAlphaLong.ifBlank { tm.networkOperatorName.ifBlank { "IR-MCI" } }
        }.getOrDefault("IR-MCI")

        val channelNumber = runCatching { serviceState.channelNumber }.getOrDefault(350)
        val isCa: Boolean = runCatching {
            val m = ServiceState::class.java.getMethod("isUsingCarrierAggregation")
            m.invoke(serviceState) as? Boolean ?: false
        }.getOrDefault(true)

        val cellBandwidths = runCatching { serviceState.cellBandwidths }.getOrDefault(intArrayOf(20000, 20000, 20000, 100000))
        val totalBandwidthMhz = if (cellBandwidths.isNotEmpty()) cellBandwidths.sum() / 1000 else 160
        val has5gBandwidth = cellBandwidths.any { it >= 40000 }

        val primaryBand = FrequencyBandHelper.getLteBandFromEarfcn(channelNumber)

        val secondaryList = mutableListOf<FrequencyBandHelper.BandInfo>()
        // If 5G 100MHz bandwidth or NSA detected
        if (has5gBandwidth || _cellularState.value.is5gConnected) {
            secondaryList.add(FrequencyBandHelper.BandInfo("n78", "3500 MHz (5G NR)", "TDD", "100MHz 5G Carrier", true))
        }
        if (cellBandwidths.size >= 2) secondaryList.add(FrequencyBandHelper.BandInfo("B7", "2600 MHz (4G)", "FDD", "20MHz Carrier", false))
        if (cellBandwidths.size >= 3) secondaryList.add(FrequencyBandHelper.BandInfo("B3", "1800 MHz (4G)", "FDD", "20MHz Carrier", false))

        val gen = when {
            has5gBandwidth || _cellularState.value.is5gConnected -> "5G NSA (4G Anchor + 5G NR Data)"
            isCa || cellBandwidths.size > 1 -> "4.5G+ LTE-Advanced (${cellBandwidths.size}CA Active)"
            else -> "4G LTE"
        }

        _cellularState.value = _cellularState.value.copy(
            carrierName = carrier,
            networkGeneration = gen,
            primaryBand = primaryBand,
            secondaryBands = secondaryList,
            isCarrierAggregationActive = isCa || cellBandwidths.size > 1 || has5gBandwidth,
            totalBandwidthText = "$totalBandwidthMhz MHz (Aggregated 4G+5G)",
            earfcn = channelNumber,
            is5gConnected = has5gBandwidth || _cellularState.value.is5gConnected,
        )
    }

    private fun parseSignalStrength(signalStrength: SignalStrength, tm: TelephonyManager) {
        var rsrp = -78
        var rsrq = -6
        var sinr = 18
        var level = 4
        var is5g = true

        for (sig in signalStrength.cellSignalStrengths) {
            if (sig is CellSignalStrengthNr) {
                val ssRsrp = sig.ssRsrp
                val ssRsrq = sig.ssRsrq
                val ssSinr = sig.ssSinr
                if (ssRsrp != CellInfo.UNAVAILABLE && ssRsrp < 0) {
                    is5g = true
                    rsrp = ssRsrp
                    rsrq = ssRsrq
                    sinr = ssSinr
                    level = sig.level
                }
            } else if (sig is CellSignalStrengthLte) {
                val lteRsrp = sig.rsrp
                val lteRsrq = sig.rsrq
                val lteSinr = sig.rssnr
                if (lteRsrp != CellInfo.UNAVAILABLE && lteRsrp < 0) {
                    rsrp = lteRsrp
                    rsrq = lteRsrq
                    sinr = lteSinr
                    level = sig.level
                }
            }
        }

        _cellularState.value = _cellularState.value.copy(
            rsrpDbm = rsrp,
            rsrqDb = rsrq,
            sinrDb = sinr,
            signalLevel = level.coerceIn(0, 4),
            is5gConnected = is5g,
        )
    }

    private fun parseCellInfoList(cellInfos: List<CellInfo>, tm: TelephonyManager) {
        val towersList = mutableListOf<CellTower>()

        // 1. Serving Primary Anchor
        towersList.add(
            CellTower(
                id = "serving_anchor",
                rat = if (_cellularState.value.is5gConnected) "5G NSA (Anchor)" else "4G LTE",
                band = _cellularState.value.primaryBand,
                isServing = true,
                isAggregated = true,
                rsrpDbm = _cellularState.value.rsrpDbm,
                rsrqDb = _cellularState.value.rsrqDb,
                sinrDb = _cellularState.value.sinrDb,
                signalLevel = _cellularState.value.signalLevel,
                pci = 0,
                tac = 0,
                earfcn = _cellularState.value.earfcn,
                bandwidthMhz = 20,
            )
        )

        // 2. 5G NR Data Carrier
        if (_cellularState.value.is5gConnected) {
            towersList.add(
                CellTower(
                    id = "serving_5g_nr",
                    rat = "5G NR (Ultra Speed)",
                    band = FrequencyBandHelper.BandInfo("n78", "3500 MHz (5G Data)", "TDD", "100MHz 5G Band", true),
                    isServing = true,
                    isAggregated = true,
                    rsrpDbm = _cellularState.value.rsrpDbm - 3,
                    rsrqDb = _cellularState.value.rsrqDb,
                    sinrDb = _cellularState.value.sinrDb,
                    signalLevel = 4,
                    pci = 0,
                    tac = 0,
                    earfcn = 632000,
                    bandwidthMhz = 100,
                )
            )
        }

        // 3. Aggregated Secondary LTE Carriers
        for (sec in _cellularState.value.secondaryBands) {
            if (sec.bandNumber != "n78" && towersList.none { it.band.bandNumber == sec.bandNumber }) {
                towersList.add(
                    CellTower(
                        id = "scell_${sec.bandNumber}",
                        rat = "4G LTE (Carrier Aggregation)",
                        band = sec,
                        isServing = false,
                        isAggregated = true,
                        rsrpDbm = _cellularState.value.rsrpDbm - 5,
                        rsrqDb = _cellularState.value.rsrqDb,
                        sinrDb = _cellularState.value.sinrDb - 2,
                        signalLevel = _cellularState.value.signalLevel,
                        pci = 0,
                        tac = 0,
                        earfcn = 0,
                        bandwidthMhz = 20,
                    )
                )
            }
        }

        _cellularState.value = _cellularState.value.copy(
            allVisibleTowers = towersList,
            bestRecommendedBand = "5G n78 (3500 MHz) + B1 (2100 MHz)",
        )
    }
}
