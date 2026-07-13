package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.theme.AppCorners
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.ColorSuccess
import com.example.ui.theme.ColorError
import com.example.util.BiometricHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("ranisa_prefs", android.content.Context.MODE_PRIVATE) }
    var isBiometricEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("biometric_lock_enabled", false)) }
    val activity = context as? androidx.fragment.app.FragmentActivity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("security_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(AppSpacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // Header Banner (Premium Vault Accent)
            Card(
                shape = AppCorners.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security Status",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ranisa Vault Security",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Protect your ledger transactions and cash accounting entries with device biometrics.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Fingerprint Lock Configuration Card
            Card(
                shape = AppCorners.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint Option",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "Fingerprint App Lock",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Require biometric verification when starting the app",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (activity == null) {
                                        Toast.makeText(context, "Error: Biometric context not available", Toast.LENGTH_SHORT).show()
                                        return@Switch
                                    }
                                    val status = BiometricHelper.getBiometricStatus(context)
                                    if (status == "NO_HARDWARE" || status == "HW_UNAVAILABLE") {
                                        Toast.makeText(context, "This device does not support biometric authentication.", Toast.LENGTH_LONG).show()
                                    } else if (status == "NONE_ENROLLED") {
                                        Toast.makeText(context, "No biometrics enrolled. Please register a fingerprint in your device Settings first.", Toast.LENGTH_LONG).show()
                                    } else if (status == "SUCCESS") {
                                        BiometricHelper.authenticate(
                                            activity = activity,
                                            title = "Ranisa Security",
                                            subtitle = "Verify fingerprint to enable App Lock",
                                            onSuccess = {
                                                isBiometricEnabled = true
                                                sharedPrefs.edit().putBoolean("biometric_lock_enabled", true).apply()
                                                Toast.makeText(context, "Fingerprint Lock enabled successfully.", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { _, errString ->
                                                Toast.makeText(context, "Setup failed: $errString", Toast.LENGTH_SHORT).show()
                                            },
                                            onFailed = {
                                                Toast.makeText(context, "Authentication failed. Try again.", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        Toast.makeText(context, "Biometric authentication is currently unavailable (Status: $status).", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    isBiometricEnabled = false
                                    sharedPrefs.edit().putBoolean("biometric_lock_enabled", false).apply()
                                    Toast.makeText(context, "Fingerprint Lock disabled.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("security_biometric_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Diagnostic status info
                    val rawStatus = BiometricHelper.getBiometricStatus(context)
                    val statusText = when (rawStatus) {
                        "SUCCESS" -> "Available & Configured"
                        "NONE_ENROLLED" -> "Available but no fingerprints enrolled"
                        "NO_HARDWARE" -> "No biometric hardware detected"
                        "HW_UNAVAILABLE" -> "Hardware is currently busy or unavailable"
                        "SECURITY_UPDATE_REQUIRED" -> "Security update required"
                        else -> "Unavailable ($rawStatus)"
                    }
                    val statusColor = if (rawStatus == "SUCCESS") ColorSuccess else ColorError

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Device Biometric Status",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = statusText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Test Verification Card
            if (isBiometricEnabled) {
                Card(
                    shape = AppCorners.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(AppSpacing.md)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Verify System Diagnostics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Test your fingerprint lock configuration instantly to ensure everything is ready.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (activity != null) {
                                    BiometricHelper.authenticate(
                                        activity = activity,
                                        title = "Security Test",
                                        subtitle = "Testing your biometric sensor",
                                        onSuccess = {
                                            Toast.makeText(context, "✅ Security Diagnostic Check Succeeded!", Toast.LENGTH_LONG).show()
                                        },
                                        onError = { _, errString ->
                                            Toast.makeText(context, "❌ Security Check Failed: $errString", Toast.LENGTH_SHORT).show()
                                        },
                                        onFailed = {
                                            Toast.makeText(context, "❌ Biometric verification failed.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    Toast.makeText(context, "Error: Biometric context not available", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = AppCorners.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_biometric_button")
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Test Lock")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Fingerprint Unlock", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Ranisa respects your privacy. All biometric credentials remain securely encrypted inside your device's hardware enclave (TEE) and are never uploaded to any servers.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }
    }
}
