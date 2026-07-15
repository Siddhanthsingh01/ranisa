package com.example.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Firm
import com.example.ui.theme.AppCorners
import com.example.ui.theme.AppSpacing

@Composable
fun FirmSelectionScreen(
    onFirmSelected: (Firm) -> Unit,
    onLogout: () -> Unit,
    viewModel: RanisaViewModel
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ranisa_prefs", Context.MODE_PRIVATE) }
    val assignedFirmsStr = prefs.getString("saved_firm_access", "Lalit Rice Broker, Hare Krishna Rice Broker") ?: "Lalit Rice Broker, Hare Krishna Rice Broker"
    val assignedFirmsList = assignedFirmsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

    // Synchronize current firms with local database to support unlimited firms
    LaunchedEffect(assignedFirmsList) {
        assignedFirmsList.forEach { nameOrId ->
            val id = when {
                nameOrId == "F002" || nameOrId.contains("Krishna", ignoreCase = true) -> "F002"
                nameOrId == "F001" || nameOrId.contains("Lalit", ignoreCase = true) -> "F001"
                else -> nameOrId
            }
            val realName = when (id) {
                "F001" -> "Lalit Rice Broker"
                "F002" -> "Hare Krishna Rice Broker"
                else -> nameOrId
            }
            viewModel.insertFirmDirect(Firm(id = id, name = realName))
        }
    }

    val dbFirms by viewModel.firms.collectAsState()
    val displayedFirms = remember(dbFirms, assignedFirmsList) {
        dbFirms.filter { firm ->
            assignedFirmsList.any { assigned ->
                assigned.equals(firm.id, ignoreCase = true) || assigned.equals(firm.name, ignoreCase = true)
            }
        }.ifEmpty {
            assignedFirmsList.map { nameOrId ->
                val id = when {
                    nameOrId == "F002" || nameOrId.contains("Krishna", ignoreCase = true) -> "F002"
                    nameOrId == "F001" || nameOrId.contains("Lalit", ignoreCase = true) -> "F001"
                    else -> nameOrId
                }
                val realName = when (id) {
                    "F001" -> "Lalit Rice Broker"
                    "F002" -> "Hare Krishna Rice Broker"
                    else -> nameOrId
                }
                Firm(id = id, name = realName)
            }.distinctBy { it.id }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F172A) // Sleek slate-dark theme background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Subtitle / Greeting icon
            Icon(
                imageVector = Icons.Default.Business,
                contentDescription = "Brokerage Logo",
                tint = Color(0xFF38C194),
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 16.dp)
            )

            Text(
                text = "Select Brokerage Firm",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Please choose an active firm to view and manage billing ledger records.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            )

            if (displayedFirms.isEmpty()) {
                // Fallback / Loading state
                CircularProgressIndicator(color = Color(0xFF38C194))
            } else {
                displayedFirms.forEach { firm ->
                    Card(
                        onClick = { onFirmSelected(firm) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("firm_card_${firm.name.replace(" ", "_")}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1E293B) // Dark item container
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = Color(0xFF322659),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🏢 ${firm.name}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Click to open billing books.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onLogout,
                modifier = Modifier.testTag("logout_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Logout / Switch Account",
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
