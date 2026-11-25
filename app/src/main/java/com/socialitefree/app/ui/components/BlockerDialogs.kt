package com.socialitefree.app.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.socialitefree.app.data.*
import kotlinx.coroutines.delay

@Composable
fun MindfulWaitPauseDialog(
    initialWaitSeconds: Int,
    defaultDurationMinutes: Int,
    cooldownUntilMs: Long,
    onDismiss: () -> Unit,
    onConfirmPause: (minutes: Int) -> Unit
) {
    val context = LocalContext.current
    val quotes = remember { MindfulContent.loadQuotes(context) }
    val quote = remember { quotes.random() }

    val totalWait = initialWaitSeconds.coerceIn(0, 60)
    var remainingWait by remember(totalWait) { mutableStateOf(totalWait) }
    var selectedMinutes by remember { mutableStateOf(defaultDurationMinutes.coerceIn(1, 120)) }

    val now = System.currentTimeMillis()
    val inPauseCooldown = cooldownUntilMs > now
    val cooldownSecsLeft = ((cooldownUntilMs - now) / 1000L).coerceAtLeast(0L)

    LaunchedEffect(totalWait) {
        while (remainingWait > 0) {
            delay(1000L)
            remainingWait--
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171E2C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.SelfImprovement,
                    contentDescription = null,
                    tint = Color(0xFF7DD3FC),
                    modifier = Modifier.size(38.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (remainingWait > 0) "Mindful Wait (${remainingWait}s)" else "Pause SocialLite",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "\"$quote\"",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center
                )

                if (totalWait > 0 && remainingWait > 0) {
                    Spacer(Modifier.height(14.dp))
                    val progress = 1f - (remainingWait.toFloat() / totalWait.toFloat())
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF38BDF8),
                        trackColor = Color(0xFF283449)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Take a breath — resist the impulse or choose pause duration below.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                if (inPauseCooldown) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFF3B1D26),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Pause Lockout active (${cooldownSecsLeft / 60}m ${cooldownSecsLeft % 60}s left before next pause).",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Pause blocking for:",
                        color = Color(0xFF93C5FD),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 15, 30).forEach { mins ->
                            val selected = selectedMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color(0xFF2563EB) else Color(0xFF232D3F))
                                    .clickable { selectedMinutes = mins }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${mins}m",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Text("Stay Focused (Keep Blocker On)", fontWeight = FontWeight.Bold)
                }
                if (!inPauseCooldown) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { onConfirmPause(selectedMinutes) },
                        enabled = remainingWait == 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (remainingWait > 0) "Wait ${remainingWait}s to Pause..." else "Pause for $selectedMinutes min",
                            color = if (remainingWait == 0) Color(0xFFF87171) else Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CuriousConfigDialog(
    initialSessionMins: Int,
    initialCooldownMins: Int,
    initialLockSwitch: Boolean,
    initialAllowOne: Boolean,
    onDismiss: () -> Unit,
    onSaveAndActivate: (sessionMins: Int, cooldownMins: Int, lockSwitch: Boolean, allowOne: Boolean) -> Unit
) {
    var sessionMins by remember { mutableStateOf(initialSessionMins.coerceIn(1, 60)) }
    var cooldownMins by remember { mutableStateOf(initialCooldownMins.coerceIn(1, 60)) }
    var lockSwitch by remember { mutableStateOf(initialLockSwitch) }
    var allowOne by remember { mutableStateOf(initialAllowOne) }

    val mood = MindfulContent.curiousMood(sessionMins)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171E2C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Curious Mode Timer",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))

                Surface(
                    color = Color(0xFF1F2A3C),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(mood.emoji, fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(mood.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(mood.description, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Watch Session Duration", color = Color(0xFF93C5FD), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 5, 10, 15).forEach { m ->
                        val sel = sessionMins == m
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) Color(0xFF2563EB) else Color(0xFF232D3F))
                                .clickable { sessionMins = m }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${m}m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Enforced Cooldown Lock", color = Color(0xFF93C5FD), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 5, 10, 15).forEach { m ->
                        val sel = cooldownMins == m
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) Color(0xFF0284C7) else Color(0xFF232D3F))
                                .clickable { cooldownMins = m }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${m}m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Lock plan switch during cooldown", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = lockSwitch, onCheckedChange = { lockSwitch = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Allow 1 reel during cooldown", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = allowOne, onCheckedChange = { allowOne = it })
                }

                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = Color(0xFFCBD5E1))
                    }
                    Button(
                        onClick = { onSaveAndActivate(sessionMins, cooldownMins, lockSwitch, allowOne) },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Activate Curious", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PinVerifyDialog(
    title: String,
    pinType: PinType,
    customPin: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val deviceAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            onVerified()
        } else {
            error = "Device authentication cancelled"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171E2C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF7DD3FC), modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(8.dp))
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (pinType) {
                        PinType.TIME_12H -> "Enter live 12h time (HHMM)"
                        PinType.TIME_24H -> "Enter live 24h time (HHMM)"
                        PinType.DATE_DDMM -> "Enter today's date (DDMM)"
                        PinType.DEVICE_LOCK -> "Use your device PIN, pattern, or fingerprint"
                        else -> "Enter your SocialLite PIN"
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))

                if (pinType == PinType.DEVICE_LOCK) {
                    Button(
                        onClick = {
                            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                            @Suppress("DEPRECATION")
                            val intent = km?.createConfirmDeviceCredentialIntent(title, "Verify device lock")
                            if (intent != null) {
                                deviceAuthLauncher.launch(intent)
                            } else {
                                onVerified()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Unlock with Device Lock")
                    }
                } else {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it.filter { ch -> ch.isDigit() }.take(8)
                            error = null
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                            Text("Cancel", color = Color(0xFFCBD5E1))
                        }
                        Button(
                            onClick = {
                                if (PinValidator.verify(input, pinType, customPin)) {
                                    onVerified()
                                } else {
                                    error = "Incorrect PIN"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Verify", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
