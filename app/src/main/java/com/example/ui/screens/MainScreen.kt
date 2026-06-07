package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IpEntity
import com.example.service.LakDnsVpnService
import com.example.viewmodel.LakDnsViewModel

// Colors following the pristine, premium Sleek Interface palette
val DeepSpaceDb = Color(0xFFF7F8FD)
val CardBackgroundDb = Color(0xFFFFFFFF)
val CyberGold = Color(0xFFF59E0B)
val CyberCyan = Color(0xFF4F46E5)
val ActiveGreen = Color(0xFF10B981)
val PassiveRed = Color(0xFFEF4444)
val SoftText = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: LakDnsViewModel) {
    val context = LocalContext.current
    val allIps by viewModel.allIps.collectAsState()
    val selectedIp by viewModel.selectedIpFlow.collectAsState()
    val isVpnRunning by viewModel.isVpnRunning.collectAsState()
    val activeIpAddress by viewModel.activeIp.collectAsState()
    val activeIpLabel by viewModel.activeLabel.collectAsState()
    val isSpeedTesting by viewModel.isSpeedTesting.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    // Launcher for Android system VPN request Dialog
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User agreed, let's start VPN
            selectedIp?.let {
                triggerVpnConnection(context, it)
                Toast.makeText(context, "سرویس فعال شد | LakDNS Started", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "حق دسترسی داده نشد | Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Logo",
                                tint = CyberCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "lakdns",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B1B1F),
                                letterSpacing = 2.sp,
                                modifier = Modifier.testTag("app_title")
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.testAllSpeeds() },
                            modifier = Modifier.testTag("refresh_speeds_button")
                        ) {
                            val rotationAnim by animateFloatAsState(
                                targetValue = if (isSpeedTesting) 360f else 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "SpeedTestRotate"
                            )
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تست پینگ همه",
                                tint = if (isSpeedTesting) CyberCyan else Color(0xFF1B1B1F),
                                modifier = Modifier.rotate(rotationAnim)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = DeepSpaceDb
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = CyberCyan,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_ip_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "افزودن آی‌پی",
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            containerColor = DeepSpaceDb
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Connection Dashboard Card
                ConnectionStatusDashboard(
                    isVpnRunning = isVpnRunning,
                    activeIp = activeIpAddress,
                    activeLabel = activeIpLabel,
                    selectedIp = selectedIp,
                    onToggleClick = {
                        if (isVpnRunning) {
                            // Stop current VPN
                            val intent = Intent(context, LakDnsVpnService::class.java).apply {
                                action = LakDnsVpnService.ACTION_DISCONNECT
                            }
                            context.startService(intent)
                        } else {
                            // Check if an IP is selected
                            if (selectedIp == null) {
                                Toast.makeText(context, "لطفاً ابتدا یک سرور را انتخاب کنید", Toast.LENGTH_SHORT).show()
                            } else {
                                // Request VPN Permission
                                val vpnIntent = VpnService.prepare(context)
                                if (vpnIntent != null) {
                                    vpnPrepareLauncher.launch(vpnIntent)
                                } else {
                                    // Already permitted, start immediately
                                    triggerVpnConnection(context, selectedIp!!)
                                    Toast.makeText(context, "لاکت دی‌ان‌اس متصل شد", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section Headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سرورهای دی‌ان‌اس و کلودها",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B1B1F),
                        fontSize = 18.sp
                    )

                    if (isSpeedTesting) {
                        Text(
                            text = "درحال بررسی سرعت...",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Server IP List
                if (allIps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = PassiveRed,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "هیچ آی‌پی پیدا نشد. برای ری‌استارت برنامه اقدام یا با دکمه + افزوده کنید.",
                                color = SoftText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ip_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(allIps, key = { it.id }) { ipEntity ->
                            IpServerItemCard(
                                ipEntity = ipEntity,
                                isSelected = ipEntity.isSelected,
                                isCurrentActive = isVpnRunning && ipEntity.ip == activeIpAddress,
                                onSelect = { viewModel.selectIp(ipEntity.id) },
                                onDelete = { viewModel.deleteIp(ipEntity.id) },
                                onPingSingle = { viewModel.testSingleIpSpeed(ipEntity) }
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddIpDialog(
                onDismiss = { showAddDialog = false },
                onAddSingle = { ip, label, cat ->
                    viewModel.addSingleIp(ip, label, cat)
                    showAddDialog = false
                    Toast.makeText(context, "سند با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                },
                onAddBulk = { bulk ->
                    viewModel.addBulkIps(bulk)
                    showAddDialog = false
                    Toast.makeText(context, "ثبت گروهی انجام شد", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun ConnectionStatusDashboard(
    isVpnRunning: Boolean,
    activeIp: String?,
    activeLabel: String?,
    selectedIp: IpEntity?,
    onToggleClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (isVpnRunning) 0.95f else 1.0f,
        targetValue = if (isVpnRunning) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color(0xFF4F46E5).copy(alpha = 0.12f),
                spotColor = Color(0xFF4F46E5).copy(alpha = 0.12f)
            )
            .testTag("connection_dashboard"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundDb),
        border = BorderStroke(1.2.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Tag
            Text(
                text = "وضعیت اتصال | status",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = SoftText.copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Sleek Hero Pulse Circle & Power Toggle Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(160.dp)
            ) {
                // Pulse Ring (Animated/Visual)
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .clip(CircleShape)
                        .background(
                            if (isVpnRunning) ActiveGreen.copy(alpha = 0.1f)
                            else CyberCyan.copy(alpha = 0.06f)
                        )
                )

                // Main Toggle Button
                Box(
                    modifier = Modifier
                        .size(115.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            clip = false,
                            spotColor = if (isVpnRunning) ActiveGreen.copy(alpha = 0.35f) else Color(0xFF4F46E5).copy(alpha = 0.25f)
                        )
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onToggleClick() }
                        .border(
                            BorderStroke(
                                8.dp,
                                if (isVpnRunning) Color(0xFFECFDF5) else Color(0xFFEEF2FF)
                            ),
                            CircleShape
                        )
                        .testTag("power_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVpnRunning) Icons.Default.Check else Icons.Default.PlayArrow,
                        contentDescription = "کلید اتصال",
                        tint = if (isVpnRunning) ActiveGreen else CyberCyan,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isVpnRunning) "لاکت دی‌ان‌اس متصل است" else "آماده اتصال به دی‌ان‌اس",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (isVpnRunning) ActiveGreen else Color(0xFF1B1B1F),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            val subtitleText = if (isVpnRunning) {
                "در حال مسیریابی فعال از: ${activeLabel ?: "تعیین نشده"} (${activeIp ?: ""})"
            } else if (selectedIp != null) {
                "سرور کلاد انتخابی: ${selectedIp.label} (${selectedIp.ip})"
            } else {
                "سرویسی برای اتصال انتخاب نشده است"
            }

            Text(
                text = subtitleText,
                color = SoftText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun IpServerItemCard(
    ipEntity: IpEntity,
    isSelected: Boolean,
    isCurrentActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onPingSingle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("ip_card_${ipEntity.ip}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color.White else Color(0xFFF1F5F9).copy(alpha = 0.6f)
        ),
        border = BorderStroke(
            width = 1.2.dp,
            color = if (isCurrentActive) ActiveGreen
            else if (isSelected) CyberCyan
            else Color(0xFFE2E8F0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelect() },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = CyberCyan,
                        unselectedColor = SoftText
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ipEntity.label,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B1B1F),
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberCyan.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = ipEntity.category,
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ipEntity.ip,
                        fontFamily = FontFamily.Monospace,
                        color = SoftText,
                        fontSize = 13.sp,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Connection and Speed Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                ipEntity.latency > 0 && ipEntity.latency < 120 -> ActiveGreen.copy(alpha = 0.12f)
                                ipEntity.latency >= 120 -> CyberGold.copy(alpha = 0.12f)
                                ipEntity.latency == -2 -> PassiveRed.copy(alpha = 0.12f)
                                else -> SoftText.copy(alpha = 0.12f)
                            }
                        )
                        .clickable { onPingSingle() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val statusText = when (ipEntity.latency) {
                        -1 -> "تست پینگ"
                        -2 -> "آفلاین"
                        else -> "${ipEntity.latency} میلی‌ثانیه"
                    }
                    val textColor = when {
                        ipEntity.latency > 0 && ipEntity.latency < 120 -> ActiveGreen
                        ipEntity.latency >= 120 -> CyberGold
                        ipEntity.latency == -2 -> PassiveRed
                        else -> SoftText
                    }
                    Text(
                        text = statusText,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (ipEntity.isCustom) {
                    IconButton(
                        onClick = { onDelete() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف آی‌پی",
                            tint = PassiveRed.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIpDialog(
    onDismiss: () -> Unit,
    onAddSingle: (ip: String, label: String, category: String) -> Unit,
    onAddBulk: (bulkText: String) -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Single, 1: Bulk

    AlertDialog(
        onDismissRequest = { onDismiss() },
        containerColor = CardBackgroundDb,
        title = {
            Text(
                text = "افزودن دی‌ان‌اس جدید",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1F),
                fontSize = 18.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab select
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DeepSpaceDb),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeTab = 0 }
                            .background(if (activeTab == 0) CyberCyan else Color.Transparent)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "افزودن تکی",
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == 0) Color.White else Color(0xFF64748B),
                            fontSize = 14.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeTab = 1 }
                            .background(if (activeTab == 1) CyberCyan else Color.Transparent)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "افزودن گروهی",
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == 1) Color.White else Color(0xFF64748B),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (activeTab == 0) {
                    var ipText by remember { mutableStateOf("") }
                    var labelText by remember { mutableStateOf("") }
                    var selectedCat by remember { mutableStateOf("شخصی") }

                    Text(
                        text = "آدرس آی‌پی یا دامنه:",
                        color = Color(0xFF475569),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = ipText,
                        onValueChange = { ipText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ip_input"),
                        placeholder = { Text("مثال: 1.1.1.1 یا ipcdn", color = SoftText) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SoftText.copy(alpha = 0.5f),
                            focusedTextColor = Color(0xFF1B1B1F),
                            unfocusedTextColor = Color(0xFF1B1B1F)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "نام یا برچسب:",
                        color = Color(0xFF475569),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("label_input"),
                        placeholder = { Text("مثل: کلادفلر گیمینگ", color = SoftText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SoftText.copy(alpha = 0.5f),
                            focusedTextColor = Color(0xFF1B1B1F),
                            unfocusedTextColor = Color(0xFF1B1B1F)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "دسته‌بندی:",
                        color = Color(0xFF475569),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    // Simple text input for custom category
                    OutlinedTextField(
                        value = selectedCat,
                        onValueChange = { selectedCat = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("category_input"),
                        placeholder = { Text("پیش‌فرض: شخصی", color = SoftText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SoftText.copy(alpha = 0.5f),
                            focusedTextColor = Color(0xFF1B1B1F),
                            unfocusedTextColor = Color(0xFF1B1B1F)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (ipText.isNotBlank()) {
                                onAddSingle(ipText, labelText, selectedCat)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_single_ip_button")
                    ) {
                        Text("ثبت سرور دی‌ان‌اس", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    var bulkText by remember { mutableStateOf("") }

                    Text(
                        text = "لیست آی‌پی ها را وارد کنید (هر خط یک آی‌پی):",
                        color = Color(0xFF475569),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = bulkText,
                        onValueChange = { bulkText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag("bulk_input"),
                        placeholder = {
                            Text(
                                "مثال:\n1.1.1.1 Cloudflare\n8.8.8.8 Google\n94.232.174.194 Electro",
                                color = SoftText
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SoftText.copy(alpha = 0.5f),
                            focusedTextColor = Color(0xFF1B1B1F),
                            unfocusedTextColor = Color(0xFF1B1B1F)
                        ),
                        maxLines = 15
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (bulkText.isNotBlank()) {
                                onAddBulk(bulkText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_bulk_ips_button")
                    ) {
                        Text("ثبت گروهی لیست", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("بستن | Close", color = CyberCyan)
            }
        }
    )
}

// Utility to dispatch the connection intent to local VpnService
private fun triggerVpnConnection(context: Context, ipEntity: IpEntity) {
    val intent = Intent(context, LakDnsVpnService::class.java).apply {
        action = LakDnsVpnService.ACTION_CONNECT
        putExtra(LakDnsVpnService.EXTRA_DNS_IP, ipEntity.ip)
        putExtra(LakDnsVpnService.EXTRA_DNS_LABEL, ipEntity.label)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}
