package com.example.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Firm
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToFirmSelection: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: RanisaViewModel
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ranisa_prefs", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        // Show splash screen for 1.5 seconds for branding and loading smoothness
        delay(1500)

        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val email = currentUser.email ?: ""
            val profileResult = com.example.data.FirebaseService.fetchUserProfile(currentUser.uid, email)
            if (profileResult.isSuccess) {
                val firestoreUser = profileResult.getOrThrow()
                viewModel.loginUserLocally(firestoreUser.email, "Admin")
                
                val assigned = firestoreUser.assignedFirms.filter { it.isNotBlank() }
                if (assigned.size == 1) {
                    val singleFirmNameOrId = assigned.first()
                    val id = when {
                        singleFirmNameOrId == "F002" || singleFirmNameOrId.contains("Krishna", ignoreCase = true) -> "F002"
                        singleFirmNameOrId == "F001" || singleFirmNameOrId.contains("Lalit", ignoreCase = true) -> "F001"
                        else -> singleFirmNameOrId
                    }
                    val realName = when (id) {
                        "F001" -> "Lalit Rice Broker"
                        "F002" -> "Hare Krishna Rice Broker"
                        else -> singleFirmNameOrId
                    }
                    
                    prefs.edit()
                        .putString("saved_username", firestoreUser.email)
                        .putString("CurrentUser", firestoreUser.email)
                        .putString("current_firm_id", id)
                        .putString("current_firm_name", realName)
                        .putString("CurrentFirmId", id)
                        .apply()

                    viewModel.selectFirm(Firm(id = id, name = realName))
                    onNavigateToHome()
                } else {
                    onNavigateToFirmSelection()
                }
            } else {
                auth.signOut()
                onNavigateToLogin()
            }
        } else {
            onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF322659)), // Deep indigo brand background
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_ranisa_logo),
                contentDescription = "Ranisa Logo",
                modifier = Modifier
                    .size(130.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Ranisa",
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "हिसाब आपके साथ",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = Color(0xFF38C194),
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
