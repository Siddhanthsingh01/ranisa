package com.example.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.FirebaseService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String, String, List<String>) -> Unit, // Callback passing (username, role, assignedFirms)
    viewModel: RanisaViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val prefs = remember(context) { context.getSharedPreferences("ranisa_prefs", android.content.Context.MODE_PRIVATE) }
    var rememberMe by remember { mutableStateOf(prefs.getBoolean("remember_me", false)) }

    val handleLoginSuccess = { u: String, r: String, firms: List<String> ->
        onLoginSuccess(u, r, firms)
    }

    // Fields
    var email by remember { mutableStateOf(prefs.getString("saved_username", "") ?: "") }
    var password by remember { mutableStateOf("") }
    
    // UI states
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerificationPending by remember { mutableStateOf(false) }
    
    // Focus Requester to move focus programmatically from Email to Password
    val passwordFocusRequester = remember { FocusRequester() }

    // Brand color
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
                    modifier = Modifier.size(100.dp)
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

                if (isVerificationPending) {
                    // Email Verification Screen (Requirement 6)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email Verification",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Please verify your email before logging in.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "We sent a verification link to $email. Please check your inbox and spam folders.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Button(
                                onClick = {
                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        val res = FirebaseService.sendVerificationEmail()
                                        isLoading = false
                                        res.fold(
                                            onSuccess = {
                                                Toast.makeText(context, "Verification email resent successfully!", Toast.LENGTH_SHORT).show()
                                            },
                                            onFailure = { err ->
                                                errorMessage = err.message ?: "Failed to resend verification email."
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("resend_verification_button"),
                                shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
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
                                    Text("Send Verification Email Again")
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedButton(
                                onClick = {
                                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                                    isVerificationPending = false
                                    password = ""
                                    errorMessage = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("logout_verification_button"),
                                shape = RoundedCornerShape(25.dp),
                                enabled = !isLoading
                            ) {
                                Text("Logout")
                            }
                        }
                    }
                } else {
                    // Email TextField (Requirement 3)
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input"),
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email Icon",
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
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { passwordFocusRequester.requestFocus() }
                        ),
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password TextField (Requirement 3)
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
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
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Please enter both Email and Password."
                                } else {
                                    performLogin(
                                        context = context,
                                        emailInput = email,
                                        passwordInput = password,
                                        viewModel = viewModel,
                                        setLoading = { isLoading = it },
                                        setError = { errorMessage = it },
                                        setVerificationPending = { isVerificationPending = it },
                                        onSuccess = { u, r, firms ->
                                            val editor = prefs.edit()
                                            if (rememberMe) {
                                                editor.putBoolean("remember_me", true)
                                                editor.putString("saved_username", u)
                                                editor.putString("saved_role", r)
                                                editor.putString("CurrentUser", u)
                                            } else {
                                                editor.putBoolean("remember_me", false)
                                                editor.remove("saved_username")
                                                editor.remove("saved_role")
                                                editor.remove("CurrentUser")
                                            }
                                            editor.apply()
                                            handleLoginSuccess(u, r, firms)
                                        }
                                    )
                                }
                            }
                        ),
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Remember Me and Forgot Password Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { rememberMe = !rememberMe }
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
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Remember Me",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Forgot Password (Requirement 7)
                        Text(
                            text = "Forgot Password?",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable {
                                    if (email.isBlank()) {
                                        errorMessage = "Please enter your Email Address to reset password."
                                    } else {
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            val res = FirebaseService.sendPasswordResetEmail(email)
                                            isLoading = false
                                            res.fold(
                                                onSuccess = {
                                                    Toast.makeText(context, "Password reset email sent to $email successfully!", Toast.LENGTH_LONG).show()
                                                },
                                                onFailure = { err ->
                                                    errorMessage = err.message ?: "Failed to send password reset email."
                                                }
                                            )
                                        }
                                    }
                                }
                                .testTag("forgot_password_button")
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // LOGIN Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter both Email and Password."
                                return@Button
                            }
                            performLogin(
                                context = context,
                                emailInput = email,
                                passwordInput = password,
                                viewModel = viewModel,
                                setLoading = { isLoading = it },
                                setError = { errorMessage = it },
                                setVerificationPending = { isVerificationPending = it },
                                onSuccess = { u, r, firms ->
                                    val editor = prefs.edit()
                                    if (rememberMe) {
                                        editor.putBoolean("remember_me", true)
                                        editor.putString("saved_username", u)
                                        editor.putString("saved_role", r)
                                        editor.putString("CurrentUser", u)
                                    } else {
                                        editor.putBoolean("remember_me", false)
                                        editor.remove("saved_username")
                                        editor.remove("saved_role")
                                        editor.remove("CurrentUser")
                                    }
                                    editor.apply()
                                    handleLoginSuccess(u, r, firms)
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
 * Handle Auth sequence using Firebase Auth
 */
private fun performLogin(
    context: android.content.Context,
    emailInput: String,
    passwordInput: String,
    viewModel: RanisaViewModel,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    setVerificationPending: (Boolean) -> Unit,
    onSuccess: (String, String, List<String>) -> Unit
) {
    val cleanEmail = emailInput.trim()
    val cleanPassword = passwordInput.trim()
    val prefs = context.getSharedPreferences("ranisa_prefs", android.content.Context.MODE_PRIVATE)

    setLoading(true)
    setError(null)

    viewModel.viewModelScope.launch {
        try {
            Log.d("LoginScreen", "Firebase Auth request started")
            val result = FirebaseService.authenticateUser(context, cleanEmail, cleanPassword)
            result.fold(
                onSuccess = { firestoreUser ->
                    Log.d("LoginScreen", "Login success")
                    
                    val editor = prefs.edit()
                    editor.putString("saved_firm_access", firestoreUser.firmAccess)
                    editor.putString("saved_username", firestoreUser.email)
                    editor.putString("saved_role", "Admin")
                    editor.putString("CurrentUser", firestoreUser.email)
                    editor.apply()

                    FirebaseService.saveLoginAuditLog(context, firestoreUser.email)
                    viewModel.loginUserLocally(firestoreUser.email, "Admin")
                    
                    Toast.makeText(context, "Welcome back, ${firestoreUser.fullName}!", Toast.LENGTH_SHORT).show()
                    onSuccess(firestoreUser.email, "Admin", firestoreUser.assignedFirms)
                },
                onFailure = { error ->
                    Log.d("LoginScreen", "Login failed: ${error.message}")
                    if (error.message == "Please verify your email before logging in.") {
                        setVerificationPending(true)
                    } else {
                        setError(error.message ?: "Authentication failed.")
                    }
                }
            )
        } catch (e: Exception) {
            Log.d("LoginScreen", "Login exception: ${e.message}")
            setError(e.message ?: "An unexpected error occurred during login.")
        } finally {
            setLoading(false)
        }
    }
}
