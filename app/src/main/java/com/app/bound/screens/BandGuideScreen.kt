package com.app.bound.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.bound.network.MTKBandResolver
import com.app.bound.network.ShizukuBandManager
import com.app.bound.ui.components.bouncyClickable

@Composable
fun BandGuideScreen(
    shizukuManager: ShizukuBandManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column {
                Text(
                    text = "Band & CA Guide",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Iranian Operator Presets & Carrier Aggregation Tutorials",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // How MTK BandMode Works on Poco X7 Pro Card
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "How to Lock Bands in MediaTek EngineerMode:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    text = "1. Tap the button below to open MediaTek's internal BandSelect menu.\n" +
                            "2. Select SIM 1 or SIM 2.\n" +
                            "3. Under 'LTE', UNCHECK slow bands (e.g. Band 8, Band 20) and keep only high-speed bands (Band 3 + Band 7 + Band 1) checked.\n" +
                            "4. Under 'NR', keep n78 (3500MHz) and n1/n3 checked for 5G.\n" +
                            "5. Tap 'SET' at the bottom to reboot modem radio and lock aggregation!",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                )

                Button(
                    onClick = {
                        MTKBandResolver.launchFirstWorking(context, MTKBandResolver.MTK_BAND_COMPONENTS, shizukuManager) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().bouncyClickable {},
                ) {
                    Icon(Icons.Rounded.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open MTK BandSelect Now", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        Text(
            text = "Operator Optimized Presets (Iran):",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )

        // Operator Preset 1: MCI (همراه اول)
        OperatorPresetCard(
            operatorName = "همراه اول (Hamrah-e-Avval / MCI)",
            recommendedBands = "B3 (1800) + B7 (2600) + B1 (2100) + n78 (5G)",
            strategy = "حداکثر سرعت تجمیع ۳CA در 4.5G + نسل ۵ طلایی (n78)",
            details = "باندهای اصلی دکل‌های همراه اول B3 و B7 هستند. برای جلوگیری از افت سرعت روی باند ۸، در منوی BandSelect تیک Band 8 را بردارید و فقط B3 و B7 و B1 را نگه دارید.",
            accentColor = Color(0xFF00A859),
        )

        // Operator Preset 2: Irancell (ایرانسل)
        OperatorPresetCard(
            operatorName = "ایرانسل (Irancell / MTN)",
            recommendedBands = "B3 (1800) + B7 (2600) + B1 (2100) + n78 (5G)",
            strategy = "تجمیع باند پرظرفیت شهری و بالاترین پهنای باند 5G",
            details = "ایرانسل در اکثر شهرهای بزرگ بیشترین تعداد سایت‌های 5G n78 را دارد. تجمیع B3+B7 سرعت‌های بالای ۲۰۰ مگابیت بر ثانیه را به همراه دارد.",
            accentColor = Color(0xFFFFB800),
        )

        // Operator Preset 3: TD-LTE
        OperatorPresetCard(
            operatorName = "اینترنت ثابت TD-LTE (ایرانسل / مبین‌نت / زیتل)",
            recommendedBands = "B42 (3500 MHz) + B43 (3700 MHz)",
            strategy = "قفل روی فرکانس اختصاصی TD-LTE بدون تداخل FDD",
            details = "اگر از سیم‌کارت‌های دیتا TD-LTE استفاده می‌کنید، تیک تمام باندهای FDD را بردارید و فقط تیک Band 42 و Band 43 را بگذارید.",
            accentColor = Color(0xFF0066FF),
        )

        // Operator Preset 4: Gaming
        OperatorPresetCard(
            operatorName = "پینگ پایین گیمینگ (Ultra Low Latency / No Jitter)",
            recommendedBands = "فقط Band 3 (1800) یا فقط Band 7 (2600)",
            strategy = "جلوگیری از پرش باند (Band Hopping) و کاهش شدید جیتر",
            details = "هنگام بازی آنلاین، جابجایی خودکار بین دکل‌ها باعث افت موقت پینگ می‌شود. با قفل کردن روی تک‌باند B3 یا B7 پینگ کاملاً ثابت می‌ماند.",
            accentColor = Color(0xFFE91E63),
        )
    }
}

@Composable
private fun OperatorPresetCard(
    operatorName: String,
    recommendedBands: String,
    strategy: String,
    details: String,
    accentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier.size(12.dp).clip(CircleShape).background(accentColor),
                )
                Text(
                    text = operatorName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "Bands: $recommendedBands",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Text(
                text = strategy,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}
