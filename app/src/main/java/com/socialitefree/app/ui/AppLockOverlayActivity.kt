package com.socialitefree.app.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialitefree.app.data.*
import com.socialitefree.app.ui.theme.SocialLiteFreeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Overlay guard activity launched by NativeReelsBlockerService when the user opens
 * a restricted app (App Blocker) or System Settings while PIN anti-tamper is enabled.
 */
class AppLockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_pkg"
        const val EXTRA_TARGET_APP_NAME = "extra_target_app_name"
        const val EXTRA_REASON = "extra_reason" // "APP_LOCK", "DAILY_LIMIT", "SETTINGS_GUARD"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
        val appName = intent.getStringExtra(EXTRA_TARGET_APP_NAME) ?: "Restricted App"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "APP_LOCK"
        val prefs = PreferencesManager.getInstance(applicationContext)

        val pinType = PinType.fromId(runBlocking { prefs.pinType.first() })
        val customPin = runBlocking { prefs.customPin.first() }
        val quotes = MindfulContent.loadQuotes(applicationContext)
        val quote = quotes.random()

        setContent {
            SocialLiteFreeTheme {
                val scope = rememberCoroutineScope()
                var pinInput by remember { mutableStateOf("") }
                var pinError by remember { mutableStateOf<String?>(null) }
                var unlockUnlockedState by remember {
                    mutableStateOf(pinType == PinType.NONE)
                }

                val deviceAuthLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    if (res.resultCode == RESULT_OK) {
                        unlockUnlockedState = true
                        pinError = null
                    } else {
                        pinError = "Device verification cancelled"
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0E1420), Color(0xFF151E2E), Color(0xFF0A0F18))
                            )
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2434))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (reason == "DAILY_LIMIT") Icons.Default.Timer else Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF7DD3FC),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = when (reason) {
                                    "SETTINGS_GUARD" -> "Phone Settings Protected"
                                    "DAILY_LIMIT" -> "$appName Limit Reached"
                                    else -> "$appName Is Locked"
                                },
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "\"$quote\"",
                                color = Color(0xFFB6C4D8),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))

                            if (!unlockUnlockedState && pinType != PinType.NONE) {
                                if (pinType == PinType.DEVICE_LOCK) {
                                    Button(
                                        onClick = {
                                            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                                            @Suppress("DEPRECATION")
                                            val authIntent = km?.createConfirmDeviceCredentialIntent(
                                                "Unlock $appName",
                                                "Verify screen lock to continue"
                                            )
                                            if (authIntent != null) {
                                                deviceAuthLauncher.launch(authIntent)
                                            } else {
                                                unlockUnlockedState = true
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("Verify Device PIN / Fingerprint")
                                    }
                                } else {
                                    Text(
                                        text = when (pinType) {
                                            PinType.TIME_12H -> "Enter live 12h clock (HHMM) to unlock"
                                            PinType.TIME_24H -> "Enter live 24h clock (HHMM) to unlock"
                                            PinType.DATE_DDMM -> "Enter today's date (DDMM) to unlock"
                                            else -> "Enter your SocialLite PIN"
                                        },
                                        color = Color(0xFF93C5FD),
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = pinInput,
                                        onValueChange = { pinInput = it.filter { ch -> ch.isDigit() }.take(8) },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        label = { Text("Enter PIN") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    pinError?.let {
                                        Spacer(Modifier.height(4.dp))
                                        Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = {
                                            if (PinValidator.verify(pinInput, pinType, customPin)) {
                                                unlockUnlockedState = true
                                                pinError = null
                                            } else {
                                                pinError = "Incorrect PIN. Try again."
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("Verify PIN", color = Color.White)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            if (unlockUnlockedState) {
                                Text(
                                    text = "Choose temporary access window:",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(1, 5, 10).forEach { mins ->
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    prefs.grantTemporaryAppUnlock(pkg, mins)
                                                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                                                    if (launchIntent != null) {
                                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        startActivity(launchIntent)
                                                    }
                                                    finish()
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("${mins}m", color = Color(0xFF7DD3FC), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                            }

                            Button(
                                onClick = {
                                    val home = Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_HOME)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    startActivity(home)
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Text("Stay Focused (Exit App)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
