package com.alazab.assistant.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alazab.assistant.AlazabEnterpriseConfig
import com.alazab.assistant.GatewayConnectionTelemetry
import com.alazab.assistant.GatewayState

/**
 * A dedicated, real-time Material 3 status card that monitors and displays the operational state
 * of the SMS Gateway specifically tied to the official enterprise phone (+201004006620).
 *
 * States supported:
 * - Ready (جاهزة ومتصلة)
 * - Sending (جاري الإرسال)
 * - Error (خطأ أو تعطل)
 * - Stopped / Disabled (معطلة)
 */
@Composable
fun SmsGatewayStatusCard(
    telemetry: GatewayConnectionTelemetry,
    isServiceEnabled: Boolean,
    onToggleService: (Boolean) -> Unit,
    onRestartGateway: () -> Unit,
    onCheckConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Pulsing animation for active live beacon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_beacon")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Palette & attributes resolution according to current state
    val statusColors = when (val state = telemetry.state) {
        is GatewayState.Ready -> StatusTheme(
            badgeBg = Color(0xFFE8F5E9),
            accentColor = Color(0xFF2E7D32),
            cardBorder = Color(0xFF81C784),
            indicatorColor = Color(0xFF4CAF50),
            icon = Icons.Default.CheckCircle,
            statusLabel = "جاهزة • READY",
            description = "البوابة نشطة ومستعدة فورياً للإرسال والاستقبال عبر الخط الرسمي"
        )
        is GatewayState.Sending -> StatusTheme(
            badgeBg = Color(0xFFFFF8E1),
            accentColor = Color(0xFFE65100),
            cardBorder = Color(0xFFFFB74D),
            indicatorColor = Color(0xFFFF9800),
            icon = Icons.AutoMirrored.Filled.Send,
            statusLabel = "جاري الإرسال • SENDING",
            description = "يتم إرسال رسالة (${state.partCount} أجزاء) إلى ${state.recipient}..."
        )
        is GatewayState.Error -> StatusTheme(
            badgeBg = Color(0xFFFFEBEE),
            accentColor = Color(0xFFC62828),
            cardBorder = Color(0xFFE57373),
            indicatorColor = Color(0xFFEF5350),
            icon = Icons.Default.ErrorOutline,
            statusLabel = "تنبيه خطأ • ERROR",
            description = state.errorMessage
        )
        is GatewayState.Stopped -> StatusTheme(
            badgeBg = Color(0xFFECEFF1),
            accentColor = Color(0xFF455A64),
            cardBorder = Color(0xFFCFD8DC),
            indicatorColor = Color(0xFF78909C),
            icon = Icons.Default.PowerSettingsNew,
            statusLabel = "معطلة • DISABLED",
            description = "البوابة متوقفة حالياً. قم بتفعيل المفتاح لتشغيل الإرسال التلقائي"
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sms_gateway_status_card")
            .border(1.5.dp, statusColors.cardBorder, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 1. Header Bar: Official line title + Live Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF030957)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = "Cell Tower",
                            tint = Color(0xFFFFB900),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "بوابة الرسائل SMS المعتمدة",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF030957)
                        )
                        Text(
                            text = "مؤسسة العزب • نظام التشغيل المباشر",
                            fontSize = 10.sp,
                            color = Color(0xFF5E5E62)
                        )
                    }
                }

                // Live Status Badge with Animated Glowing Beacon
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(statusColors.badgeBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (telemetry.state is GatewayState.Ready || telemetry.state is GatewayState.Sending) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(statusColors.indicatorColor)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColors.indicatorColor)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusColors.statusLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColors.accentColor,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Official Enterprise Line Highlight Banner (+201004006620)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6FB)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFDCE2F0), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Official SIM Phone",
                            tint = Color(0xFF030957),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF030957)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "معتمد رسمياً",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "شبكة فودافون مصر (Vodafone Egypt) • الخط الأساسي",
                                fontSize = 10.sp,
                                color = Color(0xFF49454F)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Official Phone", AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "تم نسخ رقم المؤسسة (+201004006620)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("copy_official_phone_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy official number",
                            tint = Color(0xFF030957),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Operational State Explanation Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColors.badgeBg.copy(alpha = 0.6f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (telemetry.state is GatewayState.Sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = statusColors.accentColor
                    )
                } else {
                    Icon(
                        imageVector = statusColors.icon,
                        contentDescription = "Status Icon",
                        tint = statusColors.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusColors.description,
                    fontSize = 11.sp,
                    color = statusColors.accentColor,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Telemetry Metrics Matrix (Queue depth, Sent Today, Quota, SIM state)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pending Queue Metric
                MetricMiniCard(
                    title = "في الانتظار",
                    value = "${telemetry.pendingQueueCount}",
                    subText = "رسالة بالصادر",
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f)
                )

                // Today Sent Quota Metric
                MetricMiniCard(
                    title = "مرسل اليوم",
                    value = "${telemetry.sentTodayCount} / ${telemetry.dailyLimit}",
                    subText = "الحد اليومي",
                    icon = Icons.Default.Assessment,
                    modifier = Modifier.weight(1f)
                )

                // SIM Telephony State
                MetricMiniCard(
                    title = "حالة الشريحة",
                    value = if (telemetry.isSimReady) "جاهزة" else "فحص...",
                    subText = "SIM1 فودافون",
                    icon = Icons.Default.SimCard,
                    isPositive = telemetry.isSimReady,
                    modifier = Modifier.weight(1f)
                )
            }

            // 5. Last Dispatch Telemetry Note (if available)
            if (!telemetry.lastDispatchedRecipient.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "آخر إرسال: ${telemetry.lastDispatchedRecipient} (${telemetry.lastDispatchedStatus ?: "DISPATCHED"})",
                        fontSize = 10.sp,
                        color = Color(0xFF5E5E62),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = telemetry.lastDispatchedTime ?: "",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF757575)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6. Action Control Bar (Toggle Switch, Ping Check, Restart)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Toggle Gateway Service
                Button(
                    onClick = { onToggleService(!isServiceEnabled) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceEnabled) Color(0xFF030957) else Color(0xFF546E7A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(44.dp)
                        .testTag("gateway_toggle_switch")
                ) {
                    Icon(
                        imageVector = if (isServiceEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Service",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceEnabled) "إيقاف مؤقت" else "تفعيل البوابة",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Check Line Connection
                OutlinedButton(
                    onClick = onCheckConnection,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFDCE2F0))),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("gateway_ping_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = "Ping Check",
                        tint = Color(0xFF030957),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "فحص الخط",
                        fontSize = 11.sp,
                        color = Color(0xFF030957),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Restart Gateway Service
                IconButton(
                    onClick = onRestartGateway,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F3F9))
                        .testTag("gateway_restart_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restart Gateway",
                        tint = Color(0xFF030957),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    value: String,
    subText: String,
    icon: ImageVector,
    isPositive: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8F9FD))
            .border(1.dp, Color(0xFFE2E7F3), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5E5E62)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isPositive) Color(0xFF030957) else Color(0xFFC62828),
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) Color(0xFF030957) else Color(0xFFC62828)
            )
            Text(
                text = subText,
                fontSize = 8.sp,
                color = Color(0xFF757575)
            )
        }
    }
}

private data class StatusTheme(
    val badgeBg: Color,
    val accentColor: Color,
    val cardBorder: Color,
    val indicatorColor: Color,
    val icon: ImageVector,
    val statusLabel: String,
    val description: String
)
