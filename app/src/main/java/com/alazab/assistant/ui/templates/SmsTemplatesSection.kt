package com.alazab.assistant.ui.templates

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alazab.assistant.AlazabEnterpriseConfig
import com.alazab.assistant.MainViewModel
import com.alazab.assistant.templates.SmsTemplate
import com.alazab.assistant.templates.SmsTemplateCategory
import com.alazab.assistant.templates.SmsTemplateEngine

/**
 * Operational SMS Templates & Variables Workshop for Alazab Enterprise.
 * Connected to official line +201004006620.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmsTemplatesSection(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allTemplates by viewModel.allTemplates.collectAsState()
    val selectedTemplate by viewModel.selectedTemplate.collectAsState()
    val variableValues by viewModel.variableValues.collectAsState()
    val isSending by viewModel.isSendingFromTemplate.collectAsState()

    var selectedCategory by remember { mutableStateOf(SmsTemplateCategory.ALL) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredTemplates = remember(allTemplates, selectedCategory) {
        if (selectedCategory == SmsTemplateCategory.ALL) {
            allTemplates
        } else {
            allTemplates.filter { it.category == selectedCategory }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Card: Alazab Enterprise Templates Workshop Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF030957)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color(0xFFFFB900), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFB900)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Templates",
                                tint = Color(0xFF030957),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "قوالب الإرسال ونظام المتغيرات",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "الخط الرسمي: ${AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFFB900)
                            )
                        }
                    }

                    // Add custom template button
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB900)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Template",
                            tint = Color(0xFF030957),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "قالب جديد",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF030957)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "منظومة متكاملة لإنشاء وتوليد الرسائل النصية القصيرة لرموز المصادقة (OTP)، استعادة المرور، إشعارات الصيانة، وتأكيدات الدفع مع التعويض اللحظي للمتغيرات.",
                    fontSize = 12.sp,
                    color = Color(0xFFE1E2EC),
                    lineHeight = 17.sp
                )
            }
        }

        // 2. Category Filter Chips (Horizontal Scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmsTemplateCategory.values().forEach { category ->
                val isSelected = selectedCategory == category
                val count = if (category == SmsTemplateCategory.ALL) allTemplates.size else allTemplates.count { it.category == category }

                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = category },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = category.titleAr,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF030957) else Color(0xFFE0E0E0))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$count",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFF424242)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFB900),
                        selectedLabelColor = Color(0xFF030957),
                        containerColor = Color.White,
                        labelColor = Color(0xFF1B1B1F)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) Color(0xFFFFB900) else Color(0xFFE1E2EC)
                    )
                )
            }
        }

        // 3. Horizontal Carousel of Templates in Active Category
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "اختر قالباً للتخصيص والإرسال (${filteredTemplates.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF030957)
                )

                if (filteredTemplates.any { !it.isBuiltIn }) {
                    TextButton(
                        onClick = { viewModel.resetTemplatesToDefaults() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("استعادة الافتراضي", fontSize = 11.sp, color = Color(0xFF1565C0))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                filteredTemplates.forEach { template ->
                    val isCurrent = selectedTemplate?.id == template.id
                    TemplateCardItem(
                        template = template,
                        isSelected = isCurrent,
                        onClick = { viewModel.selectTemplate(template) },
                        onDelete = if (!template.isBuiltIn) {
                            { viewModel.deleteCustomTemplate(template.id) }
                        } else null
                    )
                }
            }
        }

        // 4. Active Template Editor, Live Preview & Variables Panel
        selectedTemplate?.let { currentTemplate ->
            ActiveTemplateWorkshop(
                template = currentTemplate,
                variablesMap = variableValues,
                isSending = isSending,
                onUpdateVariable = { k, v -> viewModel.updateVariable(k, v) },
                onGenerateOtp = { digits -> viewModel.generateOtpForVariable("otp", digits) },
                onGenerateOrderId = { viewModel.generateOrderIdForVariable("order_id", "AZ") },
                onSetEnterprisePhone = { viewModel.setEnterprisePhoneForVariable("phone") },
                onSetCurrentDate = { viewModel.setCurrentDateForVariable("date") },
                onSend = { viewModel.sendFromTemplate(context) }
            )
        }
    }

    // Add Custom Template Dialog
    if (showAddDialog) {
        AddCustomTemplateDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, category, rawBody, entityId ->
                viewModel.addCustomTemplate(title, category, rawBody, entityId)
                showAddDialog = false
            }
        )
    }
}

/**
 * Single Template Item Card in the selection list.
 */
@Composable
fun TemplateCardItem(
    template: SmsTemplate,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFFB900) else Color(0xFFE1E2EC),
        label = "border_color"
    )
    val containerColor = if (isSelected) Color(0xFFFFFDE7) else Color.White

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(260.dp)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(0xFF030957) else Color(0xFFEDE7F6))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = template.category.titleAr,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFFFFB900) else Color(0xFF4527A0)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!template.isBuiltIn) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE0F2F1))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("مخصص", fontSize = 8.sp, color = Color(0xFF00695C), fontWeight = FontWeight.Bold)
                        }
                        if (onDelete != null) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(14.dp))
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE8F5E9))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("رسمي", fontSize = 8.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = template.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF030957),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = template.rawBody,
                fontSize = 11.sp,
                color = Color(0xFF5E5E62),
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Detected Variables Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                template.variables.take(3).forEach { variable ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE3F2FD))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "{$variable}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0)
                        )
                    }
                }
                if (template.variables.size > 3) {
                    Text(
                        text = "+${template.variables.size - 3}",
                        fontSize = 9.sp,
                        color = Color(0xFF757575)
                    )
                }
            }
        }
    }
}

/**
 * Active Template Live Simulator & Variables Tuning Workshop.
 */
@Composable
fun ActiveTemplateWorkshop(
    template: SmsTemplate,
    variablesMap: Map<String, String>,
    isSending: Boolean,
    onUpdateVariable: (String, String) -> Unit,
    onGenerateOtp: (Int) -> Unit,
    onGenerateOrderId: () -> Unit,
    onSetEnterprisePhone: () -> Unit,
    onSetCurrentDate: () -> Unit,
    onSend: () -> Unit
) {
    val context = LocalContext.current
    val renderedText = remember(template, variablesMap) {
        SmsTemplateEngine.renderMessage(template.rawBody, variablesMap)
    }
    val encodingInfo = remember(renderedText) {
        AlazabEnterpriseConfig.analyzeSmsEncoding(renderedText)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Workshop Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Tuning",
                        tint = Color(0xFF030957),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "محرر المتغيرات والمعاينة الحية",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF030957)
                    )
                }

                // Reference ID badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F3F9))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Ref: ${template.entityIdDefault}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF49454F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Authentic Live SMS Preview Mockup Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F8)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD3E3FD), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header of SMS bubble
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E7D32))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "رسالة نصية من: ${AlazabEnterpriseConfig.ENTERPRISE_NAME} (${AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF030957)
                            )
                        }

                        // Copy button
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("rendered_sms", renderedText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ نص الرسالة المجهزة", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy message",
                                tint = Color(0xFF030957),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live rendered message body
                    Text(
                        text = renderedText,
                        fontSize = 13.sp,
                        color = Color(0xFF1B1B1F),
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Encoding and Multipart telemetry row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (encodingInfo.isUnicodeArabic) Color(0xFFE3F2FD) else Color(0xFFEDE7F6))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (encodingInfo.isUnicodeArabic) "عربي (UCS-2)" else "GSM-7",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (encodingInfo.isUnicodeArabic) Color(0xFF1565C0) else Color(0xFF512DA8)
                                )
                            }
                            Text(
                                text = "${encodingInfo.totalCharacters} حرف",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF49454F)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (encodingInfo.partCount > 1) Color(0xFFFFF3E0) else Color(0xFFE8F5E9))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${encodingInfo.partCount} ${if (encodingInfo.partCount == 1) "رسالة" else "أجزاء مقسمة"}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (encodingInfo.partCount > 1) Color(0xFFE65100) else Color(0xFF2E7D32)
                                )
                            }
                            Text(
                                text = "متبقي بالجزء: ${encodingInfo.charactersRemainingInCurrentPart}",
                                fontSize = 9.sp,
                                color = Color(0xFF757575)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Dynamic Variable Inputs Grid
            Text(
                text = "المتغيرات المطلوبة في هذا القالب (${template.variables.size}):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF030957)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // If template does not contain 'phone', still show the phone field for sending target!
                val allRequiredKeys = if (template.variables.contains("phone")) {
                    template.variables
                } else {
                    listOf("phone") + template.variables
                }

                allRequiredKeys.forEach { varKey ->
                    val normalizedKey = SmsTemplateEngine.normalizeVariableKey(varKey)
                    val descriptor = SmsTemplateEngine.STANDARD_VARIABLES.find { it.key == normalizedKey }
                    val currentValue = variablesMap[normalizedKey] ?: ""

                    VariableInputField(
                        key = normalizedKey,
                        label = descriptor?.labelAr ?: varKey,
                        value = currentValue,
                        onValueChange = { newVal -> onUpdateVariable(normalizedKey, newVal) },
                        onGenerateOtp6 = { onGenerateOtp(6) },
                        onGenerateOtp4 = { onGenerateOtp(4) },
                        onGenerateOrderId = onGenerateOrderId,
                        onSetEnterprisePhone = onSetEnterprisePhone,
                        onSetCurrentDate = onSetCurrentDate
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Action Buttons Row (Send Now via Official Line, Copy, Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Send SMS Button
                Button(
                    onClick = onSend,
                    modifier = Modifier.weight(1.5f),
                    enabled = !isSending,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF030957),
                        contentColor = Color.White
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = Color(0xFFFFB900),
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري الإرسال...", fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color(0xFFFFB900),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إرسال عبر الخط الرسمي",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Share Button
                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, renderedText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة رسالة مؤسسة العزب"))
                    },
                    modifier = Modifier.weight(0.7f),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE1E2EC))
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color(0xFF030957),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Variable Input Field with quick generator shortcuts.
 */
@Composable
fun VariableInputField(
    key: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onGenerateOtp6: () -> Unit,
    onGenerateOtp4: () -> Unit,
    onGenerateOrderId: () -> Unit,
    onSetEnterprisePhone: () -> Unit,
    onSetCurrentDate: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE3F2FD))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "{$key}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F)
                )
            }

            // Quick Generator Action Buttons for specific variable types
            when (key) {
                "otp" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = onGenerateOtp6,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("كود 6 أرقام", fontSize = 10.sp, color = Color(0xFF1565C0))
                        }
                        TextButton(
                            onClick = onGenerateOtp4,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("كود 4 أرقام", fontSize = 10.sp, color = Color(0xFF1565C0))
                        }
                    }
                }
                "order_id" -> {
                    TextButton(
                        onClick = onGenerateOrderId,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("توليد كود طلب", fontSize = 10.sp, color = Color(0xFF1565C0))
                    }
                }
                "phone" -> {
                    TextButton(
                        onClick = onSetEnterprisePhone,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("رقم المؤسسة (+201004006620)", fontSize = 10.sp, color = Color(0xFF1565C0))
                    }
                }
                "date" -> {
                    TextButton(
                        onClick = onSetCurrentDate,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("الوقت الآن", fontSize = 10.sp, color = Color(0xFF1565C0))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = key != "message",
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF030957),
                unfocusedBorderColor = Color(0xFFE1E2EC)
            )
        )
    }
}

/**
 * Dialog to add a custom SMS template with variable insertion helpers.
 */
@Composable
fun AddCustomTemplateDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: SmsTemplateCategory, rawBody: String, entityId: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var rawBody by remember { mutableStateOf("") }
    var entityId by remember { mutableStateOf("CUSTOM_ORDER") }
    var selectedCategory by remember { mutableStateOf(SmsTemplateCategory.MAINTENANCE) }
    var categoryDropdownOpen by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إضافة قالب رسائل جديد",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF030957)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF757575))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Template Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان القالب (مثال: تنبيه فحص أسبوعي)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCategory.titleAr,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("تصنيف القالب") },
                        trailingIcon = {
                            IconButton(onClick = { categoryDropdownOpen = !categoryDropdownOpen }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Category")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = categoryDropdownOpen,
                        onDismissRequest = { categoryDropdownOpen = false }
                    ) {
                        SmsTemplateCategory.values().filter { it != SmsTemplateCategory.ALL }.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.titleAr) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Reference ID
                OutlinedTextField(
                    value = entityId,
                    onValueChange = { entityId = it },
                    label = { Text("معرف المرجع (Entity ID)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Variables Quick Inserter Row
                Text(
                    text = "انقر لإدراج متغير في نص الرسالة:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5E5E62)
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("phone", "otp", "order_id", "name", "tech_name", "amount", "date", "link").forEach { placeholder ->
                        AssistChip(
                            onClick = {
                                rawBody += " {$placeholder} "
                            },
                            label = { Text("{$placeholder}", fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Template Body
                OutlinedTextField(
                    value = rawBody,
                    onValueChange = { rawBody = it },
                    label = { Text("نص القالب مع الأقواس {متغير}") },
                    minLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Confirm and Cancel buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء")
                    }

                    Button(
                        onClick = {
                            if (title.isNotBlank() && rawBody.isNotBlank()) {
                                onConfirm(title, selectedCategory, rawBody, entityId)
                            }
                        },
                        enabled = title.isNotBlank() && rawBody.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF030957)),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("حفظ القالب")
                    }
                }
            }
        }
    }
}
