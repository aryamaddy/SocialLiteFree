package com.socialitefree.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialitefree.app.BuildConfig
import com.socialitefree.app.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBlockerScreen(
    prefsManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = rememberAppSettings(prefsManager)

    val installedApps = remember {
        NativeAppHelper.getInstalledLaunchableApps(context)
    }
    var searchQuery by remember { mutableStateOf("") }
    var editingApp by remember { mutableStateOf<InstalledAppSummary?>(null) }

    val blockedMap = remember(settings.blockedApps) {
        settings.blockedApps.associateBy { it.packageName }
    }

    val filteredApps = remember(installedApps, searchQuery, blockedMap) {
        val list = if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
        list.sortedWith(compareByDescending<InstalledAppSummary> { blockedMap[it.packageName]?.enabled == true }
            .thenBy { it.appName.lowercase() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Blocker & Daily Limits", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (!BuildConfig.SUPPORTS_NATIVE_BLOCKER) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "WebsiteClean edition saves your rules here, while system-wide external app locking runs in OriginalAppBlocker (using Accessibility Service).",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search installed apps...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            val activeCount = settings.blockedApps.count { it.enabled }
            Text(
                text = "$activeCount apps restricted · 100% Free Unlimited",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val rule = blockedMap[app.packageName]
                    val isEnabled = rule?.enabled == true
                    val limitMins = rule?.dailyLimitMinutes ?: 0

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingApp = app }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                val sub = when {
                                    !isEnabled -> "Not restricted · Tap to set daily limit or lock"
                                    limitMins <= 0 -> "Strict Lock (Ask unlock timer on open)"
                                    else -> "Daily limit: ${limitMins}m · Used ${rule!!.usedTodaySeconds / 60}m today"
                                }
                                Text(
                                    text = sub,
                                    fontSize = 12.sp,
                                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val mutable = settings.blockedApps.toMutableList()
                                        mutable.removeAll { it.packageName == app.packageName }
                                        if (checked) {
                                            mutable.add(
                                                BlockedAppRule(
                                                    packageName = app.packageName,
                                                    appName = app.appName,
                                                    dailyLimitMinutes = rule?.dailyLimitMinutes ?: 0,
                                                    enabled = true
                                                )
                                            )
                                        }
                                        prefsManager.setBlockedApps(mutable)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    editingApp?.let { app ->
        val existingRule = blockedMap[app.packageName]
        var limitMins by remember(app.packageName) { mutableStateOf(existingRule?.dailyLimitMinutes ?: 0) }
        val mood = MindfulContent.dailyLimitMood(limitMins)

        AlertDialog(
            onDismissRequest = { editingApp = null },
            title = { Text("Limit ${app.appName}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(mood.emoji, fontSize = 24.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(mood.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(mood.description, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Daily Allowance:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Lock", 15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h").forEach { (m, label) ->
                            val sel = limitMins == m
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { limitMins = m }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val mutable = settings.blockedApps.toMutableList()
                        mutable.removeAll { it.packageName == app.packageName }
                        mutable.add(
                            BlockedAppRule(
                                packageName = app.packageName,
                                appName = app.appName,
                                dailyLimitMinutes = limitMins,
                                usedTodaySeconds = existingRule?.usedTodaySeconds ?: 0,
                                usedDate = existingRule?.usedDate ?: "",
                                enabled = true
                            )
                        )
                        prefsManager.setBlockedApps(mutable)
                    }
                    editingApp = null
                }) {
                    Text("Save Limit")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingApp = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteBlockerScreen(
    prefsManager: PreferencesManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings = rememberAppSettings(prefsManager)

    var newPattern by remember { mutableStateOf("") }
    var matchType by remember { mutableStateOf(WebMatchType.DOMAIN) }
    var blockAction by remember { mutableStateOf(BlockActionMode.PRESS_BACK) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Website & Keyword Blocker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Block Browser Shorts & Reels", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "Blocks youtube.com/shorts, instagram.com/reels, tiktok.com across 30+ browsers & in-app browser",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.nativeBlockBrowserShorts,
                            onCheckedChange = { v ->
                                scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_BROWSER_SHORTS, v) }
                            }
                        )
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Add Unlimited Site or Keyword", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPattern,
                            onValueChange = { newPattern = it },
                            placeholder = { Text("e.g. youtube.com/shorts or newsfeed") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WebMatchType.entries.forEach { mt ->
                                val sel = matchType == mt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { matchType = mt }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        mt.label,
                                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(matchType.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BlockActionMode.entries.forEach { act ->
                                val sel = blockAction == act
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { blockAction = act }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        act.label,
                                        color = if (sel) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val cleaned = newPattern.trim()
                                if (cleaned.isNotEmpty()) {
                                    scope.launch {
                                        val updated = listOf(BlockedSiteRule(cleaned, matchType, true, blockAction)) +
                                            settings.blockedSites.filterNot { it.pattern.equals(cleaned, ignoreCase = true) }
                                        prefsManager.setBlockedSites(updated)
                                    }
                                    newPattern = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Block Website / Keyword", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (settings.blockedSites.isEmpty()) {
                item {
                    Text(
                        "No custom sites blocked yet. Add unlimited domains or keywords above.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(settings.blockedSites, key = { "${it.matchType.id}:${it.pattern}" }) { rule ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.pattern, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "${rule.matchType.label} · ${rule.action.label}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { en ->
                                    scope.launch {
                                        val updated = settings.blockedSites.map {
                                            if (it.pattern == rule.pattern && it.matchType == rule.matchType) it.copy(enabled = en) else it
                                        }
                                        prefsManager.setBlockedSites(updated)
                                    }
                                }
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    prefsManager.setBlockedSites(
                                        settings.blockedSites.filterNot { it.pattern == rule.pattern && it.matchType == rule.matchType }
                                    )
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinLockScreen(
    prefsManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = rememberAppSettings(prefsManager)

    var customPinDraft by remember(settings.customPin) { mutableStateOf(settings.customPin) }
    var pinSavedNote by remember { mutableStateOf<String?>(null) }
    var uninstallAdminActive by remember { mutableStateOf(NativeAppHelper.isUninstallGuardActive(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PIN Lock & Uninstall Guard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Choose PIN Lock Type", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        PinType.entries.forEach { pt ->
                            val selected = settings.pinType == pt
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        scope.launch {
                                            prefsManager.setString(PreferencesManager.PIN_TYPE, pt.id)
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = {
                                    scope.launch { prefsManager.setString(PreferencesManager.PIN_TYPE, pt.id) }
                                })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(pt.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(pt.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        if (settings.pinType == PinType.CUSTOM) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customPinDraft,
                                onValueChange = {
                                    customPinDraft = it.filter { ch -> ch.isDigit() }.take(8)
                                    pinSavedNote = null
                                },
                                label = { Text("Custom 4–8 digit PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (customPinDraft.length >= 4) {
                                        scope.launch { prefsManager.setString(PreferencesManager.CUSTOM_PIN, customPinDraft) }
                                        pinSavedNote = "PIN updated successfully!"
                                    } else {
                                        pinSavedNote = "PIN must be at least 4 digits"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Custom PIN")
                            }
                            pinSavedNote?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (settings.pinType != PinType.NONE) {
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Protected Sections", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            PinToggleRow("Protect SocialLite Settings", settings.pinProtectSettings) {
                                scope.launch { prefsManager.setBoolean(PreferencesManager.PIN_PROTECT_SETTINGS, it) }
                            }
                            PinToggleRow("Protect Blocker Plan Switching", settings.pinProtectPlanSwitch) {
                                scope.launch { prefsManager.setBoolean(PreferencesManager.PIN_PROTECT_PLAN_SWITCH, it) }
                            }
                            if (BuildConfig.SUPPORTS_NATIVE_BLOCKER) {
                                PinToggleRow("Require PIN to Temporarily Unlock Apps", settings.pinProtectAppLocks) {
                                    scope.launch { prefsManager.setBoolean(PreferencesManager.PIN_PROTECT_APP_LOCKS, it) }
                                }
                                PinToggleRow("Protect Phone Settings App (Anti-Tamper)", settings.pinProtectSystemSettings) {
                                    scope.launch { prefsManager.setBoolean(PreferencesManager.PIN_PROTECT_SYSTEM_SETTINGS, it) }
                                }
                            }
                        }
                    }
                }
            }

            if (BuildConfig.SUPPORTS_NATIVE_BLOCKER) {
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Accidental Uninstall Protection", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "Prevents impulsive uninstallation until deactivated here first.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uninstallAdminActive,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        NativeAppHelper.enableUninstallGuard(context)
                                    } else {
                                        NativeAppHelper.disableUninstallGuard(context)
                                    }
                                    uninstallAdminActive = NativeAppHelper.isUninstallGuardActive(context)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinToggleRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}
