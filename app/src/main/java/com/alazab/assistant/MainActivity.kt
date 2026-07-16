package com.alazab.assistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.alazab.assistant.data.AppDatabase
import com.alazab.assistant.data.CapturedEvent
import com.alazab.assistant.data.CapturedEventRepository
import com.alazab.assistant.network.HookClient
import com.alazab.assistant.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val hookClient by lazy { HookClient() }
    private val repository by lazy { CapturedEventRepository(database.capturedEventDao(), database.smsTaskDao(), hookClient, this) }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set default configurations if not set
        val prefs = getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("backend_url")) {
            prefs.edit()
                .putString("backend_url", "https://ais-dev-qzkqa7lcuz6u5kqe52owcv-6579453338.europe-west1.run.app/payment-phone-hook")
                .putString("api_key", "azab-secret-api-key-2026")
                .apply()
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        SleekTopAppBar()
                    }
                ) { innerPadding ->
                    AzabDiagnosticsScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun SleekTopAppBar() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF030957))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .border(width = 0.dp, color = Color.Transparent)
            .drawBehindDivider(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFB900))
                    .border(1.5.dp, Color.White, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AZ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF030957),
                    letterSpacing = (-0.5).sp
                )
            }

            Column {
                Text(
                    text = "Azab Assistant",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("package", "com.alazab.assistant")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "تم نسخ اسم الحزمة", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "COM.ALAZAB.ASSISTANT",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC5C6D0),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy package",
                        tint = Color(0xFFC5C6D0),
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color(0xFFFFB900))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF030957))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ONLINE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF030957)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "azab-payment-phone-01",
                fontSize = 10.sp,
                color = Color(0xFFC5C6D0)
            )
        }
    }
}

// Helper extension to draw a beautiful thin bottom line division
fun Modifier.drawBehindDivider() = this.drawBehind {
    val strokeWidth = 1.dp.toPx()
    val y = size.height - strokeWidth / 2
    drawLine(
        color = Color(0x1EFFFFFF),
        start = androidx.compose.ui.geometry.Offset(0f, y),
        end = androidx.compose.ui.geometry.Offset(size.width, y),
        strokeWidth = strokeWidth
    )
}

// Custom canvas drawing helper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AzabDiagnosticsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Preferences configuration state
    val prefs = remember { context.getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE) }
    var backendUrl by remember { mutableStateOf(prefs.getString("backend_url", "") ?: "") }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }

    var lastSmsText by remember { mutableStateOf(prefs.getString("last_sms_text", "No SMS captured yet") ?: "") }
    var lastNotificationText by remember { mutableStateOf(prefs.getString("last_notification_text", "No notification captured yet") ?: "") }
    var lastSyncStatus by remember { mutableStateOf(prefs.getString("last_sync_status", "Never synced") ?: "") }

    var isSettingsExpanded by remember { mutableStateOf(false) }
    var isSupabaseSettingsExpanded by remember { mutableStateOf(false) }
    var isSmsFormExpanded by remember { mutableStateOf(false) }
    var isGatewayDashboardExpanded by remember { mutableStateOf(false) }

    // Pref snapshot for limits and pull enabled
    var smsPullEnabled by remember { mutableStateOf(prefs.getBoolean("sms_pull_enabled", false)) }
    var supabaseSyncEnabled by remember { mutableStateOf(prefs.getBoolean("supabase_sync_enabled", false)) }
    var supabaseUrl by remember { mutableStateOf(prefs.getString("supabase_url", "https://bxuhcbfdoaflsgbxiqei.supabase.co") ?: "https://bxuhcbfdoaflsgbxiqei.supabase.co") }
    var supabaseKey by remember { mutableStateOf(prefs.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ4dWhjYmZkb2FmbHNnYnhpcWVpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMTU1MDIsImV4cCI6MjA4NDU5MTUwMn0.W60YuL2zn9De95Jwgc7jn4_NLMdOVINHO6NUWY6yRlQ") ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ4dWhjYmZkb2FmbHNnYnhpcWVpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMTU1MDIsImV4cCI6MjA4NDU5MTUwMn0.W60YuL2zn9De95Jwgc7jn4_NLMdOVINHO6NUWY6yRlQ") }
    var smsPullIntervalSec by remember { mutableStateOf(prefs.getInt("sms_pull_interval_sec", 30).toString()) }
    var deviceRegId by remember { mutableStateOf(prefs.getString("device_registration_id", "azab-phone-gateway") ?: "azab-phone-gateway") }

    val defaultBase = prefs.getString("backend_url", "") ?: ""
    val defaultDerivedPull = defaultBase.replace("/payment-phone-hook", "") + "/sms/pull"
    val defaultDerivedReport = defaultBase.replace("/payment-phone-hook", "") + "/sms/report"

    var smsPullUrl by remember { mutableStateOf(prefs.getString("sms_pull_url", defaultDerivedPull) ?: defaultDerivedPull) }
    var smsReportUrl by remember { mutableStateOf(prefs.getString("sms_report_url", defaultDerivedReport) ?: defaultDerivedReport) }

    var dailyLimit by remember { mutableStateOf(prefs.getInt("daily_sms_limit", 100).toString()) }
    var monthlyLimit by remember { mutableStateOf(prefs.getInt("monthly_sms_limit", 2000).toString()) }

    var sentToday by remember { mutableStateOf(prefs.getInt("sms_sent_today_count", 0)) }
    var sentThisMonth by remember { mutableStateOf(prefs.getInt("sms_sent_this_month_count", 0)) }

    val smsTasks by viewModel.allSmsTasks.collectAsState()

    // Periodically update preferences snapshot
    LaunchedEffect(Unit) {
        while (true) {
            lastSmsText = prefs.getString("last_sms_text", "No SMS captured yet") ?: ""
            lastNotificationText = prefs.getString("last_notification_text", "No notification captured yet") ?: ""
            lastSyncStatus = prefs.getString("last_sync_status", "Never synced") ?: ""
            
            smsPullEnabled = prefs.getBoolean("sms_pull_enabled", false)
            supabaseSyncEnabled = prefs.getBoolean("supabase_sync_enabled", false)
            sentToday = prefs.getInt("sms_sent_today_count", 0)
            sentThisMonth = prefs.getInt("sms_sent_this_month_count", 0)
            
            kotlinx.coroutines.delay(2000)
        }
    }

    // Permission check state
    var receiveSmsGranted by remember { mutableStateOf(false) }
    var readSmsGranted by remember { mutableStateOf(false) }
    var sendSmsGranted by remember { mutableStateOf(false) }
    var postNotificationsGranted by remember { mutableStateOf(false) }
    var listenerEnabled by remember { mutableStateOf(false) }

    fun checkPermissions() {
        receiveSmsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        readSmsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        sendSmsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        postNotificationsGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        listenerEnabled = isNotificationServiceEnabled(context)
    }

    // Check permissions on start
    LaunchedEffect(Unit) {
        checkPermissions()
    }

    // Listener settings launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        checkPermissions()
    }

    // Handle Toast requests from VM
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // UI state observers
    val events by viewModel.allEvents.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val pingStatus by viewModel.pingResult.collectAsState()
    val smsResult by viewModel.smsSendResult.collectAsState()

    // SMS Input Fields
    var smsRecipient by remember { mutableStateOf("") }
    var smsEntityId by remember { mutableStateOf("") }
    var smsMessageText by remember { mutableStateOf("") }
    var smsUseCase by remember { mutableStateOf("رمز التحقق لمرة واحدة (OTP)") }
    var expandedDropdown by remember { mutableStateOf(false) }

    val useCases = listOf(
        "رمز التحقق لمرة واحدة (OTP)",
        "استلام طلب صيانة",
        "تعيين فني",
        "إشعار بزيارة/تصريح الفني",
        "الفني في طريقه",
        "إغلاق طلب الصيانة",
        "تأكيد الدفع",
        "تنبيه فني عاجل"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF2F3F7))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Description and general portal statement
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF030957)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color(0xFFFFB900), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Operational Hub",
                            tint = Color(0xFFFFB900),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "بوابة اتصال تشغيلية محلية",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "محطة فنية لإرسال واستلام إثباتات الدفع الفورية (InstaPay / فودافون كاش) وإشعارات النظام لشركة Alazab لتحديث العمليات تلقائياً.",
                        fontSize = 12.sp,
                        color = Color(0xFFE1E2EC),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Section 1: Grid row (Permissions & Sync Queue status)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Permissions Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp)
                        .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PERMISSIONS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5E5E62),
                            letterSpacing = 0.5.sp
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PermissionIndicatorRow(
                                name = "Receive SMS",
                                granted = receiveSmsGranted,
                                onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS)) }
                            )
                            PermissionIndicatorRow(
                                name = "Send SMS",
                                granted = sendSmsGranted,
                                onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS)) }
                            )
                            PermissionIndicatorRow(
                                name = "Notifications",
                                granted = postNotificationsGranted,
                                onClick = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                    }
                                }
                            )
                        }
                    }
                }

                // Sync Queue Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp)
                        .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SYNC QUEUE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5E5E62),
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "$unsyncedCount",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Light,
                                color = Color(0xFF030957)
                            )
                            Text(
                                text = "Pending Events",
                                fontSize = 10.sp,
                                color = Color(0xFF5E5E62)
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Active Notification Listener Card (Dark Navy Style)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF030957)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NOTIFICATION LISTENER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 0.8.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color(0xFFFFB900))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clickable {
                                    try {
                                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "الرجاء تفعيل مستمع الإشعارات في الإعدادات", Toast.LENGTH_LONG).show()
                                    }
                                }
                        ) {
                            Text(
                                text = if (listenerEnabled) "ACTIVE SERVICE" else "TAP TO ENABLE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF030957)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Alerts",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Last Notification Detected",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = lastNotificationText,
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "LAST SYNC ACTION: $lastSyncStatus",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        // Section 3: Last SMS Evidence Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LAST SMS EVIDENCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5E5E62),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF1F3F9))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Automated Message",
                                    fontSize = 11.sp,
                                    color = Color(0xFF030957),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "SMS Feed",
                                    fontSize = 9.sp,
                                    color = Color(0xFF5E5E62)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = lastSmsText,
                                fontSize = 12.sp,
                                color = Color(0xFF1B1B1F),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .drawBehind {
                                drawLine(
                                    color = Color(0xFFE1E2EC),
                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Hook Connection status",
                                fontSize = 10.sp,
                                color = Color(0xFF5E5E62)
                            )
                            Text(
                                text = "Webhook Server Online",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1B1B1F)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34A853))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Connected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34A853)
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Modern Quick Simulator Buttons Row
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "QUICK SIMULATION TOOLS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5E5E62),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Button 1: Hook Test
                        SimulatorButton(
                            label = "Hook Test",
                            icon = Icons.Default.CloudSync,
                            onClick = { viewModel.pingHook(backendUrl, apiKey) },
                            modifier = Modifier.weight(1f)
                        )

                        // Button 2: Test Event
                        SimulatorButton(
                            label = "Test Event",
                            icon = Icons.AutoMirrored.Filled.Send,
                            onClick = {
                                viewModel.sendTestEvent(
                                    "Vodafone Cash",
                                    "تم إرسال التحويل بنجاح بقيمة 500 جنيه على رقم محفظتك. رقم التحويل: 1987364",
                                    context
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // Button 3: Manual Sync
                        SimulatorButton(
                            label = "Sync Queue",
                            icon = Icons.Default.Sync,
                            onClick = { viewModel.syncQueue() },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    pingStatus?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sim status: $it",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (it == "Success") Color(0xFF34A853) else Color.Red
                        )
                    }
                }
            }
        }

        // Section 5: Configurations Panel (Expandable)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSettingsExpanded = !isSettingsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF030957), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "إعدادات الربط وعنوان نقطة النهاية",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF030957)
                            )
                        }
                        Icon(
                            imageVector = if (isSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = Color(0xFF5E5E62)
                        )
                    }

                    AnimatedVisibility(
                        visible = isSettingsExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                text = "عنوان نقطة النهاية (Alazab Hook URL)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF5E5E62)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = backendUrl,
                                onValueChange = {
                                    backendUrl = it
                                    prefs.edit().putString("backend_url", it).apply()
                                },
                                label = { Text("Webhook URL") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF030957),
                                    unfocusedBorderColor = Color(0xFFE1E2EC)
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "مفتاح المصادقة والتحقق (x-api-key)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF5E5E62)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = {
                                    apiKey = it
                                    prefs.edit().putString("api_key", it).apply()
                                },
                                label = { Text("x-api-key") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF030957),
                                    unfocusedBorderColor = Color(0xFFE1E2EC)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Section: Supabase Integration Settings (Expandable)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSupabaseSettingsExpanded = !isSupabaseSettingsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = "Supabase",
                                tint = Color(0xFF030957),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "التكامل والربط مع قاعدة بيانات Supabase",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF030957)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (supabaseSyncEnabled) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("نشط", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Icon(
                                imageVector = if (isSupabaseSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = Color(0xFF5E5E62)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isSupabaseSettingsExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            // Sync Switch Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F3F9))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("تفعيل المزامنة المباشرة لـ Supabase", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF001D35))
                                    Text(
                                        if (supabaseSyncEnabled) "يتم مزامنة الأحداث والمهام مباشرة" else "المزامنة معطلة حالياً",
                                        fontSize = 10.sp,
                                        color = if (supabaseSyncEnabled) Color(0xFF2E7D32) else Color(0xFF5E5E62)
                                    )
                                }
                                Switch(
                                    checked = supabaseSyncEnabled,
                                    onCheckedChange = { isChecked ->
                                        supabaseSyncEnabled = isChecked
                                        prefs.edit().putBoolean("supabase_sync_enabled", isChecked).apply()
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "رابط المشروع (Supabase URL)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF5E5E62)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = supabaseUrl,
                                onValueChange = {
                                    supabaseUrl = it
                                    prefs.edit().putString("supabase_url", it).apply()
                                },
                                label = { Text("Supabase URL") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF030957),
                                    unfocusedBorderColor = Color(0xFFE1E2EC)
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "مفتاح الوصول العام (Anon Key)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF5E5E62)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = supabaseKey,
                                onValueChange = {
                                    supabaseKey = it
                                    prefs.edit().putString("supabase_key", it).apply()
                                },
                                label = { Text("Supabase Anon Key") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF030957),
                                    unfocusedBorderColor = Color(0xFFE1E2EC)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Informational banner about the table names
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F9)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "ℹ️ للتنبيه: يجب تهيئة الجداول التالية في Supabase للعمل بالشكل المطلوب:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF001D35)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "• captured_events (لتأكيد وإرسال المدفوعات الواردة)\n• sms_tasks (لسحب وإرسال رسائل الـ SMS تلقائياً)\n(راجع الملف INTEGRATION_GUIDE.md لمعرفة الكود الهيكلي SQL كامل)",
                                        fontSize = 9.sp,
                                        color = Color(0xFF333333),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Automated SMS Gateway Dashboard (Expandable)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isGatewayDashboardExpanded = !isGatewayDashboardExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = "Gateway",
                                tint = Color(0xFF030957),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "بوابة سحب وإرسال الرسائل التلقائية",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF030957)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (smsPullEnabled) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("نشطة", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Icon(
                                imageVector = if (isGatewayDashboardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = Color(0xFF5E5E62)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isGatewayDashboardExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            // 1. Switch Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F3F9))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("تفعيل سحب الرسائل تلقائياً", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF001D35))
                                    Text(
                                        if (smsPullEnabled) "البوابة تعمل وتسحب المهام بالخلفية" else "البوابة معطلة حالياً",
                                        fontSize = 10.sp,
                                        color = if (smsPullEnabled) Color(0xFF2E7D32) else Color(0xFF5E5E62)
                                    )
                                }
                                Switch(
                                    checked = smsPullEnabled,
                                    onCheckedChange = { isChecked ->
                                        smsPullEnabled = isChecked
                                        prefs.edit().putBoolean("sms_pull_enabled", isChecked).apply()
                                        if (isChecked) {
                                            SmsGatewayService.start(context)
                                        } else {
                                            SmsGatewayService.stop(context)
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. Limits and gauges
                            Text("الاستهلاك والحدود اليومية/الشهرية", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5E5E62))
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            val parsedDaily = dailyLimit.toIntOrNull() ?: 100
                            val parsedMonthly = monthlyLimit.toIntOrNull() ?: 2000
                            
                            val dailyProgress = if (parsedDaily > 0) sentToday.toFloat() / parsedDaily else 0f
                            val monthlyProgress = if (parsedMonthly > 0) sentThisMonth.toFloat() / parsedMonthly else 0f

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F6FA)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Daily Meter
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("الحد اليومي المتاح:", fontSize = 11.sp, color = Color(0xFF030957))
                                        Text("$sentToday / $parsedDaily رسالة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF030957))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { dailyProgress.coerceIn(0f, 1f) },
                                        color = Color(0xFF030957),
                                        trackColor = Color(0xFFE1E2EC),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Monthly Meter
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("الحد الشهري المتاح:", fontSize = 11.sp, color = Color(0xFF030957))
                                        Text("$sentThisMonth / $parsedMonthly رسالة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF030957))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { monthlyProgress.coerceIn(0f, 1f) },
                                        color = Color(0xFFFFB900),
                                        trackColor = Color(0xFFE1E2EC),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Fields to adjust limits
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = dailyLimit,
                                    onValueChange = {
                                        dailyLimit = it
                                        val v = it.toIntOrNull() ?: 100
                                        prefs.edit().putInt("daily_sms_limit", v).apply()
                                    },
                                    label = { Text("الحد اليومي", fontSize = 10.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = monthlyLimit,
                                    onValueChange = {
                                        monthlyLimit = it
                                        val v = it.toIntOrNull() ?: 2000
                                        prefs.edit().putInt("monthly_sms_limit", v).apply()
                                    },
                                    label = { Text("الحد الشهري", fontSize = 10.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 3. Device ID & Polling Interval
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = deviceRegId,
                                    onValueChange = {
                                        deviceRegId = it
                                        prefs.edit().putString("device_registration_id", it).apply()
                                    },
                                    label = { Text("معرف الجهاز (Device ID)", fontSize = 10.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.3f)
                                )
                                OutlinedTextField(
                                    value = smsPullIntervalSec,
                                    onValueChange = {
                                        smsPullIntervalSec = it
                                        val v = it.toIntOrNull() ?: 30
                                        prefs.edit().putInt("sms_pull_interval_sec", v).apply()
                                    },
                                    label = { Text("الفاصل (ثانية)", fontSize = 10.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 4. Custom Endpoints
                            Text("عناوين السحب والتقرير المخصصة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5E5E62))
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = smsPullUrl,
                                onValueChange = {
                                    smsPullUrl = it
                                    prefs.edit().putString("sms_pull_url", it).apply()
                                },
                                label = { Text("رابط سحب المهام (Pull URL)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = smsReportUrl,
                                onValueChange = {
                                    smsReportUrl = it
                                    prefs.edit().putString("sms_report_url", it).apply()
                                },
                                label = { Text("رابط تقرير الحالة (Report URL)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 5. Tasks Logs / Outbox
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("سجل المهام والرسائل المرسلة (${smsTasks.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF001D35))
                                TextButton(onClick = { viewModel.clearAllSmsTasks() }) {
                                    Text("مسح سجل الصادر", color = Color.Red, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            if (smsTasks.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("لا توجد مهام صادرة مضافة حالياً", fontSize = 11.sp, color = Color(0xFF5E5E62))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    smsTasks.take(8).forEach { task ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FF)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(task.phoneNumber, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF001D35))
                                                    
                                                    val (badgeBg, badgeText, label) = when (task.status) {
                                                        "SENT" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "تم الإرسال")
                                                        "PENDING" -> Triple(Color(0xFFFFFDE7), Color(0xFFF57F17), "قيد الانتظار")
                                                        else -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "فشل")
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(50.dp))
                                                            .background(badgeBg)
                                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = badgeText)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(task.messageText, fontSize = 11.sp, color = Color(0xFF333333))
                                                if (!task.errorMessage.isNullOrEmpty()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text("سبب الفشل: ${task.errorMessage}", fontSize = 10.sp, color = Color(0xFFC62828))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 6: Operational SMS Sending Card (Expandable)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSmsFormExpanded = !isSmsFormExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sms, contentDescription = "SMS", tint = Color(0xFF030957), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "إرسال رسالة نصية تشغيلية",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF030957)
                            )
                        }
                        Icon(
                            imageVector = if (isSmsFormExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = Color(0xFF5E5E62)
                        )
                    }

                    AnimatedVisibility(
                        visible = isSmsFormExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                text = "يحظر تماماً استخدام هذا القسم للحملات التسويقية أو الإعلانية غير المصرح بها.",
                                fontSize = 11.sp,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Recipient Field
                            OutlinedTextField(
                                value = smsRecipient,
                                onValueChange = { smsRecipient = it },
                                label = { Text("رقم المستلم (Phone Number)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF030957),
                                    unfocusedBorderColor = Color(0xFFE1E2EC)
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Entity ID
                            OutlinedTextField(
                                value = smsEntityId,
                                onValueChange = { smsEntityId = it },
                                label = { Text("معرف الكيان (Entity ID) - إجباري") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                isError = smsEntityId.isBlank(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF030957),
                                    unfocusedBorderColor = Color(0xFFE1E2EC)
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Use Case Dropdown
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = smsUseCase,
                                    onValueChange = {},
                                    label = { Text("حالة الاستخدام التشغيلية") },
                                    readOnly = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        IconButton(onClick = { expandedDropdown = !expandedDropdown }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Use Case")
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF030957),
                                        unfocusedBorderColor = Color(0xFFE1E2EC)
                                    )
                                )
                                DropdownMenu(
                                    expanded = expandedDropdown,
                                    onDismissRequest = { expandedDropdown = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    useCases.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(item) },
                                            onClick = {
                                                smsUseCase = item
                                                expandedDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Message Field
                            OutlinedTextField(
                                value = smsMessageText,
                                onValueChange = { smsMessageText = it },
                                label = { Text("نص الرسالة التشغيلية") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF030957),
                                    unfocusedBorderColor = Color(0xFFE1E2EC)
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    viewModel.sendOperationalSms(
                                        context = context,
                                        recipient = smsRecipient,
                                        entityId = smsEntityId,
                                        useCase = smsUseCase,
                                        messageText = smsMessageText
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF030957)),
                                enabled = smsRecipient.isNotBlank() && smsEntityId.isNotBlank() && smsMessageText.isNotBlank()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إرسال رسالة تشغيلية")
                            }

                            smsResult?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "النتيجة: $it",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (it.contains("successfully")) Color(0xFF34A853) else Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }

        // Captured Events Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سجل الأحداث الأخيرة (${events.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF030957)
                )
                TextButton(
                    onClick = { viewModel.clearLogs() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF030957))
                ) {
                    Text("مسح السجل")
                }
            }
        }

        if (events.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(24.dp))
                ) {
                    Text(
                        text = "لم يتم التقاط أي أحداث بعد. سيتم تسجيل أي إشعارات أو رسائل دفع هنا تلقائياً.",
                        fontSize = 12.sp,
                        color = Color(0xFF5E5E62),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 16.dp)
                    )
                }
            }
        } else {
            items(events) { event ->
                SleekEventLogItem(event = event)
            }
        }
    }
}

@Composable
fun PermissionIndicatorRow(name: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = 12.sp,
            color = Color(0xFF1B1B1F)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (granted) Color(0xFF34A853) else Color(0xFFC62828))
        )
    }
}

@Composable
fun SimulatorButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFB900)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .height(54.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF030957),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF030957)
            )
        }
    }
}

@Composable
fun SleekEventLogItem(event: CapturedEvent) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (event.channel == "sms") Icons.Default.Sms else Icons.Default.Notifications,
                        contentDescription = event.channel,
                        tint = Color(0xFF030957),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = event.provider,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF030957)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.classification.isNotEmpty() && event.classification != "UNKNOWN") {
                        val (bgCol, textCol, label) = when (event.classification) {
                            "STRONG_PROOF" -> Triple(Color(0xFFE8EAF6), Color(0xFF3F51B5), "إثبات مؤكد")
                            "SUPPORTING_PROOF" -> Triple(Color(0xFFE0F7FA), Color(0xFF00838F), "إثبات مساعد")
                            "SYSTEM" -> Triple(Color(0xFFECEFF1), Color(0xFF37474F), "نظام")
                            else -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), event.classification)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(bgCol)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (event.isSynced) Color(0xFFE8F5E9) else Color(0xFFFFFDE7))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (event.isSynced) "مُزامن" else "في الانتظار",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (event.isSynced) Color(0xFF2E7D32) else Color(0xFFF57F17)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = event.rawText,
                fontSize = 12.sp,
                color = Color(0xFF1B1B1F),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "من: ${event.sender}",
                    fontSize = 10.sp,
                    color = Color(0xFF5E5E62)
                )
                Text(
                    text = event.receivedAtDevice,
                    fontSize = 10.sp,
                    color = Color(0xFF5E5E62)
                )
            }

            if (event.entityId != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "معرف الكيان: ${event.entityId}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF030957)
                )
            }
        }
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val cn = ComponentName(context, PaymentNotificationListener::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(cn.flattenToString())
}
