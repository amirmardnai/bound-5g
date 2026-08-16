package com.app.bound.network

import android.content.Context
import android.telephony.*
import com.app.bound.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val rsrpDbm: Int = -85,
    val rsrqDb: Int = -10,
    val sinrDb: Int = 18,
    val signalLevel: Int = 3,
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
    private var isPolling = false

    fun startPolling(subId: Int? = null) {
        if (isPolling) return
        isPolling = true

        scope.launch {
            while (isActive && isPolling) {
                pollSignal(subId)
                delay(2500) // Poll every 2.5s
            }
        }
    }

    fun stopPolling() {
        isPolling = false
    }

    fun refreshNow(subId: Int? = null) {
        scope.launch { pollSignal(subId) }
    }

    private fun pollSignal(subId: Int?) {
        try {
            val tm = (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.let { base ->
                if (subId != null && subId > 0 && subId != 2147483647) {
                    try {
                        val m = TelephonyManager::class.java.getMethod("createForSubscriptionId", Int::class.javaPrimitiveType)
                        m.invoke(base, subId) as? TelephonyManager ?: base
                    } catch (_: Throwable) { base }
                } else base
            } ?: return

            val carrier = runCatching {
                tm.networkOperatorName.ifBlank { tm.simOperatorName.ifBlank { "Cellular Network" } }
            }.getOrDefault("Cellular Network")

            var primaryBand = FrequencyBandHelper.BandInfo("B3", "1800 MHz (FDD)", "FDD", "Primary LTE Anchor", true)
            val secondaryBands = mutableListOf<FrequencyBandHelper.BandInfo>()
            val towersList = mutableListOf<CellTower>()
            var isCa = false
            var rsrp = -85
            var rsrq = -10
            var sinr = 16
            var level = 3
            var pci = 0
            var tac = 0
            var earfcn = 1650
            var is5g = false

            // Safe read data network type without throwing SecurityException
            val dataNetworkType = runCatching { tm.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
            val isData5g = dataNetworkType == TelephonyManager.NETWORK_TYPE_NR

            try {
                val cellInfos = runCatching { tm.allCellInfo }.getOrNull()
                if (!cellInfos.isNullOrEmpty()) {
                    for ((index, info) in cellInfos.withIndex()) {
                        when (info) {
                            is CellInfoNr -> {
                                is5g = true
                                val id = info.cellIdentity as? CellIdentityNr
                                val sig = info.cellSignalStrength as? CellSignalStrengthNr
                                val cellRsrp = sig?.dbm ?: -85
                                val cellRsrq = sig?.csiRsrq ?: -10
                                val cellSinr = sig?.csiSinr ?: 20
                                val cellPci = id?.pci ?: 0
                                val cellTac = id?.tac ?: 0

                                val cellArfcn = runCatching {
                                    val m = id?.javaClass?.getMethod("getNrarfcn")
                                    m?.invoke(id) as? Int ?: 632000
                                }.getOrDefault(632000)

                                val nrBand = FrequencyBandHelper.getNrBandFromArfcn(cellArfcn)

                                if (info.isRegistered) {
                                    rsrp = cellRsrp
                                    rsrq = cellRsrq
                                    sinr = cellSinr
                                    level = sig?.level ?: 4
                                    pci = cellPci
                                    tac = cellTac
                                    earfcn = cellArfcn
                                    primaryBand = nrBand
                                }

                                towersList.add(
                                    CellTower(
                                        id = "nr_$index",
                                        rat = "5G NR",
                                        band = nrBand,
                                        isServing = info.isRegistered,
                                        isAggregated = isCa,
                                        rsrpDbm = cellRsrp,
                                        rsrqDb = cellRsrq,
                                        sinrDb = cellSinr,
                                        signalLevel = sig?.level ?: 3,
                                        pci = cellPci,
                                        tac = cellTac,
                                        earfcn = cellArfcn,
                                    )
                                )
                            }
                            is CellInfoLte -> {
                                val id = info.cellIdentity
                                val sig = info.cellSignalStrength
                                val cellRsrp = sig.rsrp
                                val cellRsrq = sig.rsrq
                                val cellSinr = sig.rssnr
                                val cellPci = id.pci
                                val cellTac = id.tac
                                val cellEarfcn = id.earfcn
                                val cellBand = FrequencyBandHelper.getLteBandFromEarfcn(cellEarfcn)
                                val enb = if (id.ci > 0) id.ci shr 8 else 0

                                if (info.isRegistered) {
                                    if (!is5g) {
                                        rsrp = cellRsrp
                                        rsrq = cellRsrq
                                        sinr = cellSinr
                                        level = sig.level
                                        pci = cellPci
                                        tac = cellTac
                                        earfcn = cellEarfcn
                                        primaryBand = cellBand
                                    }
                                } else {
                                    // Neighbor or Secondary Component Carrier
                                    if (!secondaryBands.contains(cellBand) && cellBand.bandNumber != primaryBand.bandNumber) {
                                        secondaryBands.add(cellBand)
                                        isCa = true
                                    }
                                }

                                towersList.add(
                                    CellTower(
                                        id = "lte_$index",
                                        rat = "4G LTE",
                                        band = cellBand,
                                        isServing = info.isRegistered,
                                        isAggregated = !info.isRegistered && isCa,
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
                }
            } catch (t: Throwable) {
                AppLogger.d("TelemetryEngine", "CellInfo read non-fatal: ${t.message}")
            }

            // If list empty, create placeholder based on primary
            if (towersList.isEmpty()) {
                towersList.add(
                    CellTower(
                        id = "primary_default",
                        rat = if (is5g || isData5g) "5G NR" else "4G LTE",
                        band = primaryBand,
                        isServing = true,
                        isAggregated = isCa,
                        rsrpDbm = rsrp,
                        rsrqDb = rsrq,
                        sinrDb = sinr,
                        signalLevel = level,
                        pci = pci,
                        tac = tac,
                        earfcn = earfcn,
                    )
                )
            }

            val gen = when {
                is5g && isCa -> "5G Dual Connectivity (EN-DC 4.5G+5G)"
                is5g || isData5g -> "5G Standalone (NR SA)"
                isCa -> "4.5G LTE-Advanced (Carrier Aggregation Active)"
                dataNetworkType == TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                else -> "Cellular Network"
            }

            val bandwidth = if (isCa) "${(secondaryBands.size + 1) * 20} MHz (Aggregated)" else "20 MHz"

            val strongestTower = towersList.maxByOrNull { it.rsrpDbm }
            val recommendation = if (strongestTower != null) {
                "${strongestTower.band.bandNumber} (${strongestTower.band.frequencyMhz}) with ${strongestTower.rsrpDbm} dBm"
            } else "B3 (1800 MHz) + B7 (2600 MHz)"

            _cellularState.value = CellularState(
                carrierName = carrier,
                networkGeneration = gen,
                primaryBand = primaryBand,
                secondaryBands = secondaryBands,
                isCarrierAggregationActive = isCa,
                totalBandwidthText = bandwidth,
                rsrpDbm = rsrp,
                rsrqDb = rsrq,
                sinrDb = sinr,
                signalLevel = level.coerceIn(0, 4),
                pci = pci,
                tac = tac,
                earfcn = earfcn,
                is5gConnected = is5g || isData5g,
                allVisibleTowers = towersList.sortedByDescending { it.isServing },
                bestRecommendedBand = recommendation,
            )
        } catch (e: Exception) {
            AppLogger.e("TelemetryEngine", "Telemetry update failed", e)
        }
    }
}
