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

    val handleLoginSuccess = { u: String, r: String ->
        onLoginSuccess(u, r)
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

    // Main scrollable form
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle
                Text(
                    text = "Rice Broker Accounting System",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    label = { Text("Enter Username") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Username Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary
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
                    label = { Text("Enter Password") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Password Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Hide Password" else "Show Password",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary
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
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("remember_me_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Remember Me",
                        color = MaterialTheme.colorScheme.onSurface,
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
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
