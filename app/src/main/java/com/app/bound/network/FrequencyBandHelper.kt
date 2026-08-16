package com.app.bound.network

/**
 * 3GPP EARFCN (LTE) and NR-ARFCN (5G NR) Frequency & Band Resolution Engine.
 * Accurately translates raw radio channel numbers into Human-readable Band names,
 * duplex modes (FDD/TDD), center frequencies, and Iranian telecom operator alignments.
 */
object FrequencyBandHelper {

    data class BandInfo(
        val bandNumber: String,        // e.g. "B3", "B7", "B42", "n78"
        val frequencyMhz: String,      // e.g. "1800 MHz", "2600 MHz", "3500 MHz"
        val duplexMode: String,        // "FDD" or "TDD"
        val operatorTag: String,       // e.g. "MCI / Irancell Primary", "TD-LTE Only", "5G High Speed"
        val isCaAnchor: Boolean = true // Whether this is commonly used for Carrier Aggregation
    )

    fun getLteBandFromEarfcn(earfcn: Int): BandInfo {
        return when (earfcn) {
            in 0..599 -> BandInfo("B1", "2100 MHz (FDD)", "FDD", "MCI / Irancell 4G & 5G DSS", true)
            in 600..1199 -> BandInfo("B2", "1900 MHz (FDD)", "FDD", "Global Band", false)
            in 1200..1949 -> BandInfo("B3", "1800 MHz (FDD)", "FDD", "MCI / Irancell High Speed Backbone", true)
            in 1950..2399 -> BandInfo("B4", "1700/2100 MHz", "FDD", "AWS Band", false)
            in 2400..2649 -> BandInfo("B5", "850 MHz (FDD)", "FDD", "Low Band Rural Coverage", false)
            in 2750..3449 -> BandInfo("B7", "2600 MHz (FDD)", "FDD", "MCI / Irancell Max Capacity CA", true)
            in 3450..3799 -> BandInfo("B8", "900 MHz (FDD)", "FDD", "Hamrah-e-Avval 4G/2G", false)
            in 6000..6599 -> BandInfo("B20", "800 MHz (FDD)", "FDD", "Rightel / High Penetration", false)
            in 9210..9659 -> BandInfo("B28", "700 MHz (FDD)", "FDD", "Ultra Long Range", true)
            in 37750..38249 -> BandInfo("B38", "2600 MHz (TDD)", "TDD", "TDD Capacity", true)
            in 38650..39649 -> BandInfo("B40", "2300 MHz (TDD)", "TDD", "Shatel / High Speed TDD", true)
            in 39650..41589 -> BandInfo("B41", "2500 MHz (TDD)", "TDD", "Wideband TDD", true)
            in 41590..43589 -> BandInfo("B42", "3500 MHz (TDD)", "TDD", "TD-LTE (Irancell / Mobinnet / Zitel)", true)
            in 43590..45589 -> BandInfo("B43", "3700 MHz (TDD)", "TDD", "TD-LTE Wireless Fixed", true)
            else -> BandInfo("LTE ($earfcn)", "Cellular LTE", "LTE", "Active LTE Carrier", false)
        }
    }

    fun getNrBandFromArfcn(nrarfcn: Int): BandInfo {
        return when (nrarfcn) {
            in 422000..434000 -> BandInfo("n1", "2100 MHz (FDD)", "FDD", "5G Sub-6 NSA / DSS", true)
            in 361000..376000 -> BandInfo("n3", "1800 MHz (FDD)", "FDD", "5G Sub-6 NSA", true)
            in 524000..538000 -> BandInfo("n7", "2600 MHz (FDD)", "FDD", "5G High Speed Capacity", true)
            in 140000..160000 -> BandInfo("n28", "700 MHz (FDD)", "FDD", "5G Wide Range Low Band", false)
            in 499200..537999 -> BandInfo("n41", "2500 MHz (TDD)", "TDD", "5G Mid-Band", true)
            in 620000..653333 -> BandInfo("n78", "3500 MHz (TDD)", "TDD", "Iran 5G Gold Band (Irancell & MCI 1Gbps+)", true)
            in 620000..680000 -> BandInfo("n77", "3700 MHz (TDD)", "TDD", "5G C-Band Ultra Wideband", true)
            else -> BandInfo("5G NR ($nrarfcn)", "5G Sub-6", "TDD/FDD", "Active 5G NR Carrier", true)
        }
    }
}
