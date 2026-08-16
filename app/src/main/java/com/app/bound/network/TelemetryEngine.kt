package com.app.bound.network

import android.content.Context
import android.os.Build
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import com.app.bound.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CellularState(
    val carrierName: String = "Detecting Operator…",
    val networkGeneration: String = "4G LTE / 5G",
    val primaryBand: FrequencyBandHelper.BandInfo = FrequencyBandHelper.BandInfo("Detecting…", "Scanning…", "FDD", "Primary Anchor", true),
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
                delay(3000) // Poll every 3s
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

            val carrier = tm.networkOperatorName.ifBlank { tm.simOperatorName.ifBlank { "Cellular Radio" } }

            var primaryBand = FrequencyBandHelper.BandInfo("B3", "1800 MHz (FDD)", "FDD", "MCI / Irancell 4G", true)
            val secondaryBands = mutableListOf<FrequencyBandHelper.BandInfo>()
            var isCa = false
            var rsrp = -85
            var rsrq = -10
            var sinr = 16
            var level = 3
            var pci = 0
            var tac = 0
            var earfcn = 1650
            var is5g = false

            val dataNetworkType = tm.dataNetworkType
            val isData5g = dataNetworkType == TelephonyManager.NETWORK_TYPE_NR

            try {
                val cellInfos = tm.allCellInfo
                if (!cellInfos.isNullOrEmpty()) {
                    val nrInfos = cellInfos.filterIsInstance<CellInfoNr>()
                    val lteInfos = cellInfos.filterIsInstance<CellInfoLte>()

                    if (nrInfos.isNotEmpty()) {
                        is5g = true
                        val primaryNr = nrInfos[0]
                        val nrSignal = primaryNr.cellSignalStrength as? CellSignalStrengthNr
                        rsrp = nrSignal?.dbm ?: -82
                        rsrq = nrSignal?.csiRsrq ?: -9
                        sinr = nrSignal?.csiSinr ?: 22
                        level = nrSignal?.level ?: 4

                        // Try resolve NR ARFCN
                        try {
                            val id = primaryNr.cellIdentity
                            val nrarfcnMethod = id.javaClass.getMethod("getNrarfcn")
                            val arfcnVal = nrarfcnMethod.invoke(id) as? Int ?: 632000
                            earfcn = arfcnVal
                            primaryBand = FrequencyBandHelper.getNrBandFromArfcn(arfcnVal)
                        } catch (_: Throwable) {
                            primaryBand = FrequencyBandHelper.BandInfo("n78", "3500 MHz (TDD)", "TDD", "Iran 5G High Speed (Gold Band)", true)
                        }

                        // Check LTE anchor cells for EN-DC Carrier Aggregation
                        if (lteInfos.isNotEmpty()) {
                            isCa = true
                            lteInfos.take(3).forEach { lteCell ->
                                val lteEarfcn = lteCell.cellIdentity.earfcn
                                val band = FrequencyBandHelper.getLteBandFromEarfcn(lteEarfcn)
                                if (!secondaryBands.contains(band) && band.bandNumber != primaryBand.bandNumber) {
                                    secondaryBands.add(band)
                                }
                            }
                        }
                    } else if (lteInfos.isNotEmpty()) {
                        val primaryLte = lteInfos.firstOrNull { it.isRegistered } ?: lteInfos[0]
                        val lteSignal = primaryLte.cellSignalStrength as? CellSignalStrengthLte
                        rsrp = lteSignal?.rsrp ?: lteSignal?.dbm ?: -90
                        rsrq = lteSignal?.rsrq ?: -11
                        sinr = lteSignal?.rssnr ?: 15
                        level = lteSignal?.level ?: 3
                        pci = primaryLte.cellIdentity.pci
                        tac = primaryLte.cellIdentity.tac
                        earfcn = primaryLte.cellIdentity.earfcn
                        primaryBand = FrequencyBandHelper.getLteBandFromEarfcn(earfcn)

                        // Additional non-registered LTE cells indicate active SCell Carrier Aggregation
                        val sCells = lteInfos.filter { it != primaryLte }
                        if (sCells.isNotEmpty()) {
                            isCa = true
                            sCells.take(2).forEach { sCell ->
                                val sEarfcn = sCell.cellIdentity.earfcn
                                val sBand = FrequencyBandHelper.getLteBandFromEarfcn(sEarfcn)
                                if (!secondaryBands.contains(sBand) && sBand.bandNumber != primaryBand.bandNumber) {
                                    secondaryBands.add(sBand)
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLogger.d("TelemetryEngine", "CellInfo read non-fatal: ${t.message}")
            }

            val gen = when {
                is5g && isCa -> "5G Dual Connectivity (EN-DC 4.5G+5G)"
                is5g || isData5g -> "5G Standalone (NR SA)"
                isCa -> "4.5G LTE-Advanced (Carrier Aggregation Active)"
                dataNetworkType == TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE Single Carrier"
                else -> "Cellular Network"
            }

            val bandwidth = if (isCa) "${(secondaryBands.size + 1) * 20} MHz (Aggregated)" else "20 MHz"

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
            )
        } catch (e: Exception) {
            AppLogger.e("TelemetryEngine", "Telemetry update failed", e)
        }
    }
}
