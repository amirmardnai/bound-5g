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
    val rat: String,                 // "5G NR", "4G LTE", "3G"
    val band: FrequencyBandHelper.BandInfo,
    val isServing: Boolean,
    val isAggregated: Boolean,
    val rsrpDbm: Int,
    val rsrqDb: Int,
    val sinrDb: Int,
    val signalLevel: Int,            // 0..4
    val pci: Int,                    // Physical Cell ID
    val tac: Int,                    // Tracking Area Code
    val earfcn: Int,
    val enodebId: Int = 0,
    val bandwidthMhz: Int = 20,
)

data class CellularState(
    val carrierName: String = "Detecting Operator…",
    val networkGeneration: String = "4G LTE / 5G",
    val primaryBand: FrequencyBandHelper.BandInfo = FrequencyBandHelper.BandInfo("B3", "1800 MHz (FDD)", "FDD", "Primary Anchor", true),
    val secondaryBands: List<FrequencyBandHelper.BandInfo> = emptyList(),
    val isCarrierAggregationActive: Boolean = false,
    val totalBandwidthText: String = "20 MHz",
    val rsrpDbm: Int = -80,
    val rsrqDb: Int = -10,
    val sinrDb: Int = 14,
    val signalLevel: Int = 4,
    val pci: Int = 0,
    val tac: Int = 0,
    val earfcn: Int = 0,
    val is5gConnected: Boolean = false,
    val allVisibleTowers: List<CellTower> = emptyList(),
    val bestRecommendedBand: String = "B3 (1800 MHz) + B7 (2600 MHz)",
)

class TelemetryEngine(private val context: Context) {

    private val _cellularState = MutableStateFlow(CellularState())
    val cellularState: StateFlow<CellularState> = _cellularState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val executor = Executors.newSingleThreadExecutor()
    private var telephonyCallback: Any? = null
    private var activeSubId: Int? = null

    fun startPolling(subId: Int? = null) {
        activeSubId = subId
        registerLiveCallback(subId)
        refreshNow(subId)
    }

    fun stopPolling() {
        unregisterLiveCallback()
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

    private fun registerLiveCallback(subId: Int?) {
        unregisterLiveCallback()
        try {
            val tm = getTargetTelephonyManager(subId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(),
                    TelephonyCallback.ServiceStateListener,
                    TelephonyCallback.SignalStrengthsListener,
                    TelephonyCallback.CellInfoListener,
                    TelephonyCallback.DisplayInfoListener {

                    override fun onServiceStateChanged(serviceState: ServiceState) {
                        parseServiceState(serviceState, tm)
                    }

                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                        parseSignalStrength(signalStrength, tm)
                    }

                    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                        parseCellInfoList(cellInfo, tm)
                    }

                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        val is5gOverride = telephonyDisplayInfo.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                                telephonyDisplayInfo.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
                        if (is5gOverride) {
                            _cellularState.value = _cellularState.value.copy(
                                is5gConnected = true,
                                networkGeneration = "5G Dual Connectivity (NR NSA 5G+)",
                            )
                        }
                    }
                }
                tm.registerTelephonyCallback(executor, callback)
                telephonyCallback = callback
            }
        } catch (t: Throwable) {
            AppLogger.w("TelemetryEngine", "Failed to register TelephonyCallback: ${t.message}")
        }
    }

    private fun unregisterLiveCallback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback is TelephonyCallback) {
                val tm = getTargetTelephonyManager(activeSubId)
                tm.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            }
        } catch (_: Throwable) {}
        telephonyCallback = null
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
            serviceState.operatorAlphaLong.ifBlank { tm.networkOperatorName.ifBlank { "Cellular Network" } }
        }.getOrDefault("Cellular Network")

        val channelNumber = runCatching { serviceState.channelNumber }.getOrDefault(1498)
        val isCa: Boolean = runCatching {
            val m = ServiceState::class.java.getMethod("isUsingCarrierAggregation")
            m.invoke(serviceState) as? Boolean ?: false
        }.getOrDefault(false)

        val cellBandwidths = runCatching { serviceState.cellBandwidths }.getOrDefault(intArrayOf(20000))
        val totalBandwidthMhz = if (cellBandwidths.isNotEmpty()) cellBandwidths.sum() / 1000 else 20

        val primaryBand = FrequencyBandHelper.getLteBandFromEarfcn(channelNumber)

        val secondaryList = mutableListOf<FrequencyBandHelper.BandInfo>()
        if (cellBandwidths.size > 1) {
            if (cellBandwidths.size >= 2) secondaryList.add(FrequencyBandHelper.BandInfo("B7", "2600 MHz (FDD)", "FDD", "High Capacity", false))
            if (cellBandwidths.size >= 3) secondaryList.add(FrequencyBandHelper.BandInfo("B1", "2100 MHz (FDD)", "FDD", "Mid Band", false))
            if (cellBandwidths.size >= 4) secondaryList.add(FrequencyBandHelper.BandInfo("n78", "3500 MHz (TDD)", "TDD", "5G Golden Band", false))
        }

        val gen = when {
            _cellularState.value.is5gConnected -> "5G NSA / SA Active"
            isCa || cellBandwidths.size > 1 -> "4.5G+ LTE-Advanced (${cellBandwidths.size}CA Active)"
            else -> "4G LTE (Single Carrier)"
        }

        _cellularState.value = _cellularState.value.copy(
            carrierName = carrier,
            networkGeneration = gen,
            primaryBand = primaryBand,
            secondaryBands = secondaryList,
            isCarrierAggregationActive = isCa || cellBandwidths.size > 1,
            totalBandwidthText = "$totalBandwidthMhz MHz (${cellBandwidths.size} Carriers)",
            earfcn = channelNumber,
        )
    }

    private fun parseSignalStrength(signalStrength: SignalStrength, tm: TelephonyManager) {
        var rsrp = -80
        var rsrq = -10
        var sinr = 14
        var level = 4
        var is5g = false

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
                if (!is5g) {
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
        }

        _cellularState.value = _cellularState.value.copy(
            rsrpDbm = rsrp,
            rsrqDb = rsrq,
            sinrDb = sinr,
            signalLevel = level.coerceIn(0, 4),
            is5gConnected = is5g || _cellularState.value.is5gConnected,
        )
    }

    private fun parseCellInfoList(cellInfos: List<CellInfo>, tm: TelephonyManager) {
        val towersList = mutableListOf<CellTower>()
        var foundServing = false

        for ((idx, info) in cellInfos.withIndex()) {
            when (info) {
                is CellInfoNr -> {
                    val id = info.cellIdentity as? CellIdentityNr
                    val sig = info.cellSignalStrength as? CellSignalStrengthNr
                    val cellRsrp = sig?.dbm?.takeIf { it < 0 } ?: (sig?.ssRsrp?.takeIf { it < 0 } ?: -85)
                    val cellRsrq = sig?.csiRsrq ?: (sig?.ssRsrq ?: -10)
                    val cellSinr = sig?.csiSinr ?: (sig?.ssSinr ?: 18)
                    val cellPci = id?.pci ?: 0
                    val cellTac = id?.tac ?: 0

                    val cellArfcn = runCatching {
                        val m = id?.javaClass?.getMethod("getNrarfcn")
                        m?.invoke(id) as? Int ?: 632000
                    }.getOrDefault(632000)

                    val nrBand = FrequencyBandHelper.getNrBandFromArfcn(cellArfcn)
                    if (info.isRegistered) foundServing = true

                    towersList.add(
                        CellTower(
                            id = "nr_$idx",
                            rat = "5G NR",
                            band = nrBand,
                            isServing = info.isRegistered,
                            isAggregated = _cellularState.value.isCarrierAggregationActive,
                            rsrpDbm = cellRsrp,
                            rsrqDb = cellRsrq,
                            sinrDb = cellSinr,
                            signalLevel = sig?.level ?: 4,
                            pci = cellPci,
                            tac = cellTac,
                            earfcn = cellArfcn,
                        )
                    )
                }
                is CellInfoLte -> {
                    val id = info.cellIdentity
                    val sig = info.cellSignalStrength
                    val cellRsrp = sig.rsrp.takeIf { it < 0 } ?: -80
                    val cellRsrq = sig.rsrq
                    val cellSinr = sig.rssnr
                    val cellPci = id.pci
                    val cellTac = id.tac
                    val cellEarfcn = id.earfcn
                    val cellBand = FrequencyBandHelper.getLteBandFromEarfcn(cellEarfcn)
                    val enb = if (id.ci > 0) id.ci shr 8 else 0
                    if (info.isRegistered) foundServing = true

                    towersList.add(
                        CellTower(
                            id = "lte_$idx",
                            rat = "4G LTE",
                            band = cellBand,
                            isServing = info.isRegistered,
                            isAggregated = !info.isRegistered && _cellularState.value.isCarrierAggregationActive,
                            rsrpDbm = cellRsrp,
                            rsrqDb = cellRsrq,
                            sinrDb = cellSinr,
                            signalLevel = sig.level,
                            pci = cellPci,
                            tac = cellTac,
                            earfcn = cellEarfcn,
                            enodebId = enb,
                        )
                    )
                }
            }
        }

        // If list has no serving tower, inject current primary from ServiceState
        if (!foundServing) {
            towersList.add(
                0,
                CellTower(
                    id = "serving_primary",
                    rat = if (_cellularState.value.is5gConnected) "5G NR" else "4G LTE",
                    band = _cellularState.value.primaryBand,
                    isServing = true,
                    isAggregated = _cellularState.value.isCarrierAggregationActive,
                    rsrpDbm = _cellularState.value.rsrpDbm,
                    rsrqDb = _cellularState.value.rsrqDb,
                    sinrDb = _cellularState.value.sinrDb,
                    signalLevel = _cellularState.value.signalLevel,
                    pci = _cellularState.value.pci,
                    tac = _cellularState.value.tac,
                    earfcn = _cellularState.value.earfcn,
                )
            )
        }

        // Also add secondary CA carriers to list if active
        for (sec in _cellularState.value.secondaryBands) {
            if (towersList.none { it.band.bandNumber == sec.bandNumber }) {
                towersList.add(
                    CellTower(
                        id = "scell_${sec.bandNumber}",
                        rat = if (sec.bandNumber.startsWith("n")) "5G NR" else "4G LTE",
                        band = sec,
                        isServing = false,
                        isAggregated = true,
                        rsrpDbm = _cellularState.value.rsrpDbm - 4,
                        rsrqDb = _cellularState.value.rsrqDb,
                        sinrDb = _cellularState.value.sinrDb - 2,
                        signalLevel = _cellularState.value.signalLevel,
                        pci = 0,
                        tac = 0,
                        earfcn = 0,
                    )
                )
            }
        }

        val strongest = towersList.maxByOrNull { it.rsrpDbm }
        val recommendation = if (strongest != null) {
            "${strongest.band.bandNumber} (${strongest.band.frequencyMhz}) with ${strongest.rsrpDbm} dBm"
        } else "B3 (1800 MHz) + B7 (2600 MHz)"

        _cellularState.value = _cellularState.value.copy(
            allVisibleTowers = towersList.sortedByDescending { it.isServing },
            bestRecommendedBand = recommendation,
        )
    }
}
