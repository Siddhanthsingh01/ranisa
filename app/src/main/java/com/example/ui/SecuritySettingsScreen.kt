package com.example.ui

import android.widget.Toast
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
                title = { Text("Security Settings", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("security_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF322659), // Ranisa Rich Purple
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9F9FB)) // Clean, modern light gray background
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECE6F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF322659), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security Status",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ranisa Vault Security",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF322659)
                        )
                        Text(
                            text = "Protect your ledger transactions and cash accounting entries with device biometrics.",
                            fontSize = 12.sp,
                            color = Color(0xFF322659).copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Fingerprint Lock Configuration Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint Option",
                                tint = Color(0xFF322659),
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "Fingerprint App Lock",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.Black
                                )
                                Text(
                                    text = "Require biometric verification when starting the app",
                                    fontSize = 12.sp,
                                    color = Color.Gray
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
                    HorizontalDivider(color = Color(0xFFF1F1F1))
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
                    val statusColor = if (rawStatus == "SUCCESS") Color(0xFF4CAF50) else Color(0xFFE53935)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Device Biometric Status",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.DarkGray
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Verify System Diagnostics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Test your fingerprint lock configuration instantly to ensure everything is ready.",
                            fontSize = 12.sp,
                            color = Color.Gray,
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
                                containerColor = Color(0xFF322659),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
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
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }
    }
}
