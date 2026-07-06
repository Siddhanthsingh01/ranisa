package com.example.ui

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.lifecycle.viewModelScope
import com.example.data.FirebaseService
import com.example.data.FirestoreUser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String, String) -> Unit, // Callback passing (username, role)
    viewModel: RanisaViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val prefs = remember(context) { context.getSharedPreferences("ranisa_prefs", android.content.Context.MODE_PRIVATE) }
    var rememberMe by remember { mutableStateOf(prefs.getBoolean("remember_me", false)) }

    var showWelcomeAnimation by remember { mutableStateOf(false) }
    var loggedInUser by remember { mutableStateOf("") }
    var loggedInRole by remember { mutableStateOf("") }

    val handleLoginSuccess = { u: String, r: String ->
        val isGreetingEnabled = prefs.getBoolean("login_greeting_enabled", true)
        if (isGreetingEnabled) {
            loggedInUser = u
            loggedInRole = r
            showWelcomeAnimation = true
        } else {
            onLoginSuccess(u, r)
        }
    }

    // Auto-login at startup
    LaunchedEffect(Unit) {
        val rememberMeSaved = prefs.getBoolean("remember_me", false)
        val savedUsername = prefs.getString("saved_username", null)
        val savedRole = prefs.getString("saved_role", null)
        if (rememberMeSaved && !savedUsername.isNullOrEmpty() && !savedRole.isNullOrEmpty()) {
            viewModel.loginUserLocally(savedUsername, savedRole)
            handleLoginSuccess(savedUsername, savedRole)
        }
    }

    // Fields
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    // UI states
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Detection of Firebase State
    val isFirebaseAvailable = remember { FirebaseService.isFirebaseInitialized(context) }

    // Check if Debug Mode
    val isDebugMode = remember(context) {
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    // Focus Requester to move focus programmatically from Username to Password
    val passwordFocusRequester = remember { FocusRequester() }

    // Brand color: Elegant Purple requested (#6C4CF1)
    val purplePrimary = Color(0xFF6C4CF1)

    if (showWelcomeAnimation) {
        val voicePlayer = remember(context) { GreetingVoicePlayer(context) }
        
        DisposableEffect(Unit) {
            voicePlayer.playGreeting(loggedInUser) {
                onLoginSuccess(loggedInUser, loggedInRole)
            }
            onDispose {
                voicePlayer.shutdown()
            }
        }

        WelcomeGreetingOverlay(username = loggedInUser)
    } else {
        // Main scrollable form
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // Top Spacing / Logo / Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp)
            ) {
                // App Logo matching design guidelines
                Image(
                    painter = painterResource(id = R.drawable.ic_ranisa_logo),
                    contentDescription = "Ranisa Logo",
                    modifier = Modifier
                        .size(100.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // App Name
                Text(
                    text = "Ranisa",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF322659),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle
                Text(
                    text = "Rice Broker Accounting System",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF322659).copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )
            }

            // Middle Section / Form Fields and Login Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                
                // Show Error Message Banner if any
                errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ $error",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Show warning if Firebase is unavailable
                if (!isFirebaseAvailable && isDebugMode) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3CD),
                            contentColor = Color(0xFF856404)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFEEBA))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Firebase Not Initialized",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Missing google-services.json configuration. App is running in Local Offline Mode for sandbox testing.",
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Username TextField
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null // reset error on type
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input"),
                    label = { Text("Enter Username", color = Color(0xFF8E8E93)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Username Icon",
                            tint = purplePrimary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = LocalTextStyle.current.copy(color = Color.Black),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = purplePrimary,
                        unfocusedBorderColor = Color.LightGray,
                        focusedLabelColor = Color(0xFF8E8E93),
                        unfocusedLabelColor = Color(0xFF8E8E93),
                        cursorColor = purplePrimary
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() }
                    ),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password TextField
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null // reset error on type
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester)
                        .testTag("password_input"),
                    label = { Text("Enter Password", color = Color(0xFF8E8E93)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Password Icon",
                            tint = purplePrimary
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Hide Password" else "Show Password",
                                tint = purplePrimary.copy(alpha = 0.7f)
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    textStyle = LocalTextStyle.current.copy(color = Color.Black),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = purplePrimary,
                        unfocusedBorderColor = Color.LightGray,
                        focusedLabelColor = Color(0xFF8E8E93),
                        unfocusedLabelColor = Color(0xFF8E8E93),
                        cursorColor = purplePrimary
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            Log.d("LoginScreen", "Login button pressed")
                            if (username.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter both username and password."
                            } else {
                                performLogin(
                                    context = context,
                                    usernameInput = username,
                                    passwordInput = password,
                                    isFirebaseAvailable = isFirebaseAvailable,
                                    viewModel = viewModel,
                                    setLoading = { isLoading = it },
                                    setError = { errorMessage = it },
                                    onSuccess = { u, r ->
                                        val editor = prefs.edit()
                                        if (rememberMe) {
                                            editor.putBoolean("remember_me", true)
                                            editor.putString("saved_username", u)
                                            editor.putString("saved_role", r)
                                        } else {
                                            editor.putBoolean("remember_me", false)
                                            editor.remove("saved_username")
                                            editor.remove("saved_role")
                                        }
                                        editor.apply()
                                        handleLoginSuccess(u, r)
                                    }
                                )
                            }
                        }
                    ),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Remember Me Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = purplePrimary,
                            uncheckedColor = Color.Gray,
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.testTag("remember_me_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Remember Me",
                        color = Color(0xFF322659),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { rememberMe = !rememberMe }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // LOGIN Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        Log.d("LoginScreen", "Login button pressed")
                        if (username.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter both username and password."
                            return@Button
                        }
                        performLogin(
                            context = context,
                            usernameInput = username,
                            passwordInput = password,
                            isFirebaseAvailable = isFirebaseAvailable,
                            viewModel = viewModel,
                            setLoading = { isLoading = it },
                            setError = { errorMessage = it },
                            onSuccess = { u, r ->
                                val editor = prefs.edit()
                                if (rememberMe) {
                                    editor.putBoolean("remember_me", true)
                                    editor.putString("saved_username", u)
                                    editor.putString("saved_role", r)
                                } else {
                                    editor.putBoolean("remember_me", false)
                                    editor.remove("saved_username")
                                    editor.remove("saved_role")
                                }
                                editor.apply()
                                handleLoginSuccess(u, r)
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("login_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = purplePrimary,
                        contentColor = Color.White
                    ),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "LOGIN",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Bottom Section / Version Text
            Text(
                text = "Version 1.0",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF322659).copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
    }
}

@Composable
fun WelcomeGreetingOverlay(username: String) {
    val displayName = remember(username) {
        if (username.contains("(")) username.substringBefore("(").trim() else username
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E1B4B), // Elegant indigo/midnight purple
                        Color(0xFF311042)  // Deep rich royal purple
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Pulsing aura circle around center icon
            PulsingAuraCircle()
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Devotional greeting
            Text(
                text = "हरे कृष्ण",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700), // Gold accent color
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // User Name
            Text(
                text = "$displayName जी",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Welcome Message
            Text(
                text = "रानीसा में आपका हार्दिक स्वागत है।",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle / Wishing
            Text(
                text = "आपका दिन मंगलमय हो।",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Beautiful voice waveform animation
            VoiceWaveform()
        }
    }
}

@Composable
fun PulsingAuraCircle(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale1"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )
    
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = 750, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale2"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = 750, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha2"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Outer aura 1
        Box(
            Modifier
                .size(100.dp)
                .graphicsLayer(scaleX = scale1, scaleY = scale1, alpha = alpha1)
                .background(Color(0xFF6C4CF1).copy(alpha = 0.3f), shape = CircleShape)
        )
        // Outer aura 2
        Box(
            Modifier
                .size(100.dp)
                .graphicsLayer(scaleX = scale2, scaleY = scale2, alpha = alpha2)
                .background(Color(0xFF6C4CF1).copy(alpha = 0.3f), shape = CircleShape)
        )
        // Central icon container
        Box(
            Modifier
                .size(100.dp)
                .background(Color(0xFF6C4CF1), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Welcome Icon",
                tint = Color.White,
                modifier = Modifier.size(50.dp)
            )
        }
    }
}

@Composable
fun VoiceWaveform() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    @Composable
    fun WaveBar(duration: Int) {
        val heightMultiplier by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "barHeight"
        )
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(40.dp * heightMultiplier)
                .background(Color(0xFFFFD700), shape = RoundedCornerShape(3.dp))
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(50.dp)
    ) {
        WaveBar(350)
        WaveBar(550)
        WaveBar(450)
        WaveBar(600)
        WaveBar(400)
    }
}

/**
 * Handle Auth sequence
 */
private fun performLogin(
    context: android.content.Context,
    usernameInput: String,
    passwordInput: String,
    isFirebaseAvailable: Boolean,
    viewModel: RanisaViewModel,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    onSuccess: (String, String) -> Unit
) {
    val cleanUsername = usernameInput.trim()
    val cleanPassword = passwordInput.trim()

    setLoading(true)
    setError(null)

    // Launch coroutine in ViewModelScope to survive configuration changes
    viewModel.viewModelScope.launch {
        try {
            if (isFirebaseAvailable) {
                Log.d("LoginScreen", "Firebase request started")
                val result = FirebaseService.authenticateUser(context, cleanUsername, cleanPassword)
                result.fold(
                    onSuccess = { firestoreUser ->
                        Log.d("LoginScreen", "Users loaded")
                        Log.d("LoginScreen", "User matched")
                        Log.d("LoginScreen", "Login success")
                        
                        // Log Audit to RTDB
                        FirebaseService.saveLoginAuditLog(context, firestoreUser.username)
                        
                        // Sync / Log Locally in Room as well
                        viewModel.loginUserLocally(firestoreUser.username, firestoreUser.role)
                        
                        Toast.makeText(context, "Welcome back, ${firestoreUser.fullName}!", Toast.LENGTH_SHORT).show()
                        onSuccess(firestoreUser.username, firestoreUser.role)
                    },
                    onFailure = { error ->
                        Log.d("LoginScreen", "Login failed")
                        Log.d("LoginScreen", "Exception message: ${error.message}")
                        setError(error.message ?: "Authentication failed.")
                    }
                )
            } else {
                // Fallback Mode (validating against default predefined Room users)
                val matchingLocalUser = when {
                    cleanUsername.contains("sidhant", ignoreCase = true) || cleanUsername.contains("admin", ignoreCase = true) -> Pair("Sidhant (Admin)", "Admin")
                    cleanUsername.contains("lalit", ignoreCase = true) || cleanUsername.contains("broker", ignoreCase = true) -> Pair("Lalit (Broker)", "Broker")
                    cleanUsername.contains("krishna", ignoreCase = true) || cleanUsername.contains("accountant", ignoreCase = true) -> Pair("Krishna (Accountant)", "Accountant")
                    cleanUsername.contains("guest", ignoreCase = true) || cleanUsername.contains("viewer", ignoreCase = true) -> Pair("Guest (Viewer)", "Viewer")
                    else -> null
                }

                if (matchingLocalUser != null) {
                    viewModel.loginUserLocally(matchingLocalUser.first, matchingLocalUser.second)
                    Toast.makeText(context, "Logged in locally as ${matchingLocalUser.first}", Toast.LENGTH_SHORT).show()
                    onSuccess(matchingLocalUser.first, matchingLocalUser.second)
                } else {
                    setError("Invalid Username or Password. Use 'sidhant', 'lalit', 'krishna', or 'guest' for local testing.")
                }
            }
        } catch (e: Exception) {
            Log.d("LoginScreen", "Login failed")
            Log.d("LoginScreen", "Exception message: ${e.message}")
            setError(e.message ?: "An unexpected error occurred during login.")
        } finally {
            setLoading(false)
        }
    }
}
