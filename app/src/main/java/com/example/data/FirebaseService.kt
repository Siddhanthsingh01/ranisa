package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.*

/**
 * Firebase Service handling direct interaction with Cloud Firestore
 * for authentication, master data, and audit/action logging persistence.
 */
object FirebaseService {
    private const val TAG = "FirebaseService"

    private var billsListener: ListenerRegistration? = null
    private var sellersListener: ListenerRegistration? = null
    private var buyersListener: ListenerRegistration? = null
    private var brokersListener: ListenerRegistration? = null
    private var paymentsListener: ListenerRegistration? = null
    private var auditLogsListener: ListenerRegistration? = null
    private var firmsListener: ListenerRegistration? = null

    fun getSanitizedFirmId(name: String): String {
        val trimmed = name.trim()
        if (trimmed == "F001" || trimmed.contains("Lalit", ignoreCase = true) || trimmed.replace(" ", "") == "LalitRiceBroker") {
            return "F001"
        }
        if (trimmed == "F002" || trimmed.contains("Krishna", ignoreCase = true) || trimmed.replace(" ", "") == "HareKrishnaRiceBroker") {
            return "F002"
        }
        return trimmed.replace(" ", "").replace("[^a-zA-Z0-9]".toRegex(), "")
    }

    /**
     * Checks if Firebase is initialized.
     */
    fun isFirebaseInitialized(context: Context): Boolean {
        return try {
            FirebaseApp.getInstance()
            true
        } catch (e: IllegalStateException) {
            try {
                FirebaseApp.getApps(context).isNotEmpty()
            } catch (ex: Exception) {
                false
            }
        }
    }

    /**
     * Authenticate a user using Firebase Authentication (Email + Password) and Firestore user profile.
     */
    suspend fun authenticateUser(
        context: Context,
        emailInput: String,
        passwordInput: String
    ): Result<FirestoreUser> {
        var isInitialized = false
        try {
            val app = FirebaseApp.initializeApp(context)
            if (app != null) {
                Log.d(TAG, "Firebase Initialized")
                isInitialized = true
            }
        } catch (e: Exception) {
            try {
                val app = FirebaseApp.getInstance()
                if (app != null) {
                    Log.d(TAG, "Firebase Initialized")
                    isInitialized = true
                }
            } catch (ex: Exception) {
                Log.d(TAG, "Firebase Exception: ${ex.message}")
            }
        }

        if (!isInitialized) {
            return Result.failure(Exception("Firebase initialization failed."))
        }

        val enteredEmail = emailInput.trim()
        val enteredPassword = passwordInput.trim()

        if (enteredEmail.isEmpty()) {
            return Result.failure(Exception("Invalid email"))
        }

        return try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val authResult = auth.signInWithEmailAndPassword(enteredEmail, enteredPassword).await()
            val firebaseUser = authResult.user ?: return Result.failure(Exception("Authentication failed"))

            val db = FirebaseFirestore.getInstance()
            val uid = firebaseUser.uid
            val userDocRef = db.collection("users").document(uid)
            var doc = userDocRef.get().await()

            // 9. On first successful login: If user document does not exist: Create it automatically.
            if (!doc.exists()) {
                val fullName = enteredEmail.substringBefore("@")
                val defaultUser = hashMapOf(
                    "fullName" to fullName,
                    "email" to enteredEmail,
                    "active" to true,
                    "assignedFirms" to emptyList<String>(),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "lastLogin" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                userDocRef.set(defaultUser).await()
                doc = userDocRef.get().await()
            } else {
                userDocRef.update("lastLogin", com.google.firebase.firestore.FieldValue.serverTimestamp()).await()
            }

            // Check if active is false
            val active = doc.getBoolean("active") ?: doc.getBoolean("isActive") ?: true
            if (!active) {
                return Result.failure(Exception("Account Disabled"))
            }

            // Check email verification (Requirement 6)
            if (!firebaseUser.isEmailVerified) {
                return Result.failure(Exception("Please verify your email before logging in."))
            }

            val fullName = doc.getString("fullName") ?: enteredEmail.substringBefore("@")
            val assignedFirms = try {
                doc.get("assignedFirms") as? List<*>
            } catch (e: Exception) {
                null
            }?.filterIsInstance<String>() ?: emptyList()

            val firestoreUser = FirestoreUser(
                fullName = fullName,
                email = enteredEmail,
                active = active,
                assignedFirms = assignedFirms,
                createdAt = doc.getDate("createdAt"),
                lastLogin = doc.getDate("lastLogin") ?: java.util.Date()
            )

            Log.d(TAG, "Login Success for user UID: $uid")
            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "LOGIN",
                    module = "Auth",
                    collectionName = "users",
                    documentId = uid,
                    recordTitle = enteredEmail,
                    newDataMap = mapOf("email" to enteredEmail, "fullName" to fullName),
                    userOverride = fullName,
                    roleOverride = "Admin"
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed login logging", le)
            }
            Result.success(firestoreUser)
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            Result.failure(Exception("User not found"))
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Incorrect password"))
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            Result.failure(Exception("No internet"))
        } catch (e: Exception) {
            val msg = e.message ?: ""
            when {
                msg.contains("disabled", ignoreCase = true) -> Result.failure(Exception("Account Disabled"))
                msg.contains("invalid-email", ignoreCase = true) || msg.contains("badly formatted", ignoreCase = true) -> Result.failure(Exception("Invalid email"))
                msg.contains("wrong-password", ignoreCase = true) -> Result.failure(Exception("Incorrect password"))
                else -> Result.failure(e)
            }
        }
    }

    suspend fun sendVerificationEmail(): Result<Unit> {
        return try {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user != null) {
                user.sendEmailVerification().await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("No user logged in"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(uid: String, email: String): Result<FirestoreUser> {
        return try {
            val db = FirebaseFirestore.getInstance()
            val userDocRef = db.collection("users").document(uid)
            var doc = userDocRef.get().await()

            if (!doc.exists()) {
                val fullName = email.substringBefore("@")
                val defaultUser = hashMapOf(
                    "fullName" to fullName,
                    "email" to email,
                    "active" to true,
                    "assignedFirms" to emptyList<String>(),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "lastLogin" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                userDocRef.set(defaultUser).await()
                doc = userDocRef.get().await()
            }

            val active = doc.getBoolean("active") ?: doc.getBoolean("isActive") ?: true
            if (!active) {
                return Result.failure(Exception("Account Disabled"))
            }

            val fullName = doc.getString("fullName") ?: email.substringBefore("@")
            val assignedFirms = try {
                doc.get("assignedFirms") as? List<*>
            } catch (e: Exception) {
                null
            }?.filterIsInstance<String>() ?: emptyList()

            val firestoreUser = FirestoreUser(
                fullName = fullName,
                email = email,
                active = active,
                assignedFirms = assignedFirms,
                createdAt = doc.getDate("createdAt"),
                lastLogin = doc.getDate("lastLogin") ?: java.util.Date()
            )
            Result.success(firestoreUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Store login audit records in the Firestore "audit_logs" collection (unified to firms/currentFirm/logs).
     */
    suspend fun saveLoginAuditLog(context: Context, username: String) {
        if (!isFirebaseInitialized(context)) return

        try {
            logEnterpriseAudit(
                context = context,
                actionType = "LOGIN",
                module = "Authentication",
                collectionName = "users",
                documentId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
                recordTitle = username,
                userOverride = username,
                roleOverride = "Admin",
                descriptionOverride = "User $username logged in successfully."
            )
            Log.d(TAG, "Audit log saved successfully via unified pipeline for user: $username")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log to Firestore", e)
        }
    }

    fun contractBillToMap(bill: ContractBill?): Map<String, Any>? {
        if (bill == null) return null
        return mapOf(
            "billNumber" to bill.billNumber,
            "date" to bill.date,
            "sellerName" to bill.sellerName,
            "buyerName" to bill.buyerName,
            "gstNo" to bill.gstNo,
            "particulars" to bill.particulars,
            "bags" to bill.bags,
            "quintals" to bill.quintals,
            "rate" to bill.rate,
            "packing" to bill.packing,
            "transport" to bill.transport,
            "delivery" to bill.delivery,
            "lorryNo" to bill.lorryNo,
            "payment" to bill.payment,
            "mobileNo" to bill.mobileNo,
            "brand" to bill.brand,
            "lorryFreight" to bill.lorryFreight,
            "creditDays" to bill.creditDays,
            "amountInWords" to bill.amountInWords,
            "billAmount" to bill.billAmount,
            "balance" to bill.balance,
            "place" to bill.place,
            "bankName" to bill.bankName,
            "remarks" to bill.remarks,
            "ddAmount" to bill.ddAmount,
            "cashCutting" to bill.cashCutting,
            "totalReceived" to bill.totalReceived,
            "remainingBalance" to bill.remainingBalance,
            "paymentStatus" to bill.paymentStatus,
            "lastPaymentDate" to bill.lastPaymentDate,
            "sellerAddress" to bill.sellerAddress,
            "buyerAddress" to bill.buyerAddress,
            "itemsJson" to bill.itemsJson,
            "discountPercent" to bill.discountPercent,
            "commissionPercent" to bill.commissionPercent,
            "remark1" to bill.remark1,
            "remark2" to bill.remark2,
            "brokerName" to bill.brokerName,
            "brokerId" to bill.brokerId,
            "eb" to bill.eb
        )
    }

    fun paymentToMap(payment: Payment?): Map<String, Any>? {
        if (payment == null) return null
        return mapOf(
            "paymentId" to payment.paymentId,
            "billNo" to payment.billNo,
            "firm" to payment.firm,
            "sellerName" to payment.sellerName,
            "buyerName" to payment.buyerName,
            "paymentAmount" to payment.paymentAmount,
            "paymentDate" to payment.paymentDate,
            "paymentMode" to payment.paymentMode,
            "referenceNumber" to payment.referenceNumber,
            "remarks" to payment.remarks,
            "receivedBy" to payment.receivedBy,
            "discountPercent" to payment.discountPercent,
            "discountAmount" to payment.discountAmount,
            "commissionPercent" to payment.commissionPercent,
            "commissionAmount" to payment.commissionAmount,
            "remarks1" to payment.remarks1,
            "remarks2" to payment.remarks2,
            "alreadyPaidAmount" to payment.alreadyPaidAmount,
            "pendingAmount" to payment.pendingAmount,
            "billAmount" to payment.billAmount
        )
    }

    fun sellerToMap(sellerName: String, mobile: String, place: String, gstNo: String, millName: String, address: String): Map<String, Any> {
        return mapOf(
            "sellerName" to sellerName,
            "mobile" to mobile,
            "place" to place,
            "gstNo" to gstNo,
            "millName" to millName,
            "address" to address
        )
    }

    fun buyerToMap(buyerName: String, mobile: String, place: String, gstNo: String, firmName: String, address: String): Map<String, Any> {
        return mapOf(
            "buyerName" to buyerName,
            "mobile" to mobile,
            "place" to place,
            "gstNo" to gstNo,
            "firmName" to firmName,
            "address" to address
        )
    }

    fun brokerToMap(brokerName: String, mobile: String, address: String): Map<String, Any> {
        return mapOf(
            "brokerName" to brokerName,
            "mobile" to mobile,
            "address" to address
        )
    }

    fun getBillNodeKey(billNumber: String): String {
        val digits = billNumber.filter { it.isDigit() }
        val padded = if (digits.isNotEmpty()) {
            val num = digits.toIntOrNull() ?: 0
            String.format("%06d", num)
        } else {
            billNumber
        }
        return "BILL_$padded"
    }

    fun safeMapToJson(map: Map<String, Any?>?): String {
        if (map == null) return ""
        val safeMap = mutableMapOf<String, Any?>()
        for ((key, value) in map) {
            safeMap[key] = when (value) {
                null -> null
                is String, is Number, is Boolean -> value
                is com.google.firebase.firestore.FieldValue -> "[FieldValue.serverTimestamp]"
                is com.google.firebase.Timestamp -> {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(value.toDate())
                }
                is java.util.Date -> {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(value)
                }
                is Map<*, *> -> {
                    try {
                        org.json.JSONObject(value as Map<*, *>).toString()
                    } catch (e: Exception) {
                        value.toString()
                    }
                }
                is List<*> -> {
                    try {
                        org.json.JSONArray(value).toString()
                    } catch (e: Exception) {
                        value.toString()
                    }
                }
                else -> value.toString()
            }
        }
        return try {
            org.json.JSONObject(safeMap).toString()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun logEnterpriseAudit(
        context: Context,
        actionType: String, // CREATE, UPDATE, DELETE, LOGIN, LOGOUT, etc.
        module: String, // Bills, Payments, Sellers, Buyers, Brokers, Auth, Settings, etc.
        collectionName: String = "", // Map to screen or custom usage
        documentId: String = "",
        recordTitle: String = "",
        oldDataMap: Map<String, Any>? = null,
        newDataMap: Map<String, Any>? = null,
        status: String = "Success",
        userOverride: String? = null,
        roleOverride: String? = null,
        customChangedFields: String? = null,
        batch: com.google.firebase.firestore.WriteBatch? = null,
        firmIdOverride: String? = null,
        firmNameOverride: String? = null,
        billNoOverride: String? = null,
        partyNameOverride: String? = null,
        oldValueOverride: String? = null,
        newValueOverride: String? = null,
        descriptionOverride: String? = null
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            
            // Resolve User details
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val finalUserId = currentUser?.uid ?: ""
            var finalUserEmail = currentUser?.email ?: ""
            var finalUserName = userOverride ?: ""
            val finalUserRole = roleOverride ?: com.example.ui.RanisaViewModel.currentUserRoleVal ?: "Admin"
            
            if (currentUser != null) {
                try {
                    val userDoc = db.collection("users").document(currentUser.uid).get().await()
                    if (userDoc.exists()) {
                        val fn = userDoc.getString("fullName")
                        val em = userDoc.getString("email")
                        if (!fn.isNullOrBlank()) {
                            finalUserName = fn
                        }
                        if (!em.isNullOrBlank()) {
                            finalUserEmail = em
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FirebaseService", "Failed to load user profile in audit log", e)
                }
            }

            if (finalUserName.isBlank()) {
                val dn = currentUser?.displayName
                finalUserName = if (!dn.isNullOrBlank()) {
                    dn
                } else {
                    val fallbackName = com.example.ui.RanisaViewModel.currentUsernameVal
                    if (!fallbackName.isNullOrBlank() && fallbackName != "Admin" && fallbackName.contains("@")) {
                        // It is an email, let's use the part before @ as fallback
                        fallbackName.substringBefore("@")
                    } else if (!fallbackName.isNullOrBlank()) {
                        fallbackName
                    } else {
                        currentUser?.email?.substringBefore("@") ?: "Admin"
                    }
                }
            }
            if (finalUserEmail.isBlank()) {
                val fallbackName = com.example.ui.RanisaViewModel.currentUsernameVal
                if (fallbackName.contains("@")) {
                    finalUserEmail = fallbackName
                }
            }

            // Resolve Firm details
            val finalFirmName = firmNameOverride ?: "Lalit Rice Broker"
            val finalFirmId = firmIdOverride ?: getSanitizedFirmId(finalFirmName)
            
            // Map actions and modules to compliant enterprise lists
            val finalAction = when (actionType.uppercase()) {
                "LOGIN", "LOGOUT", "CREATE", "UPDATE", "DELETE", "PRINT", "EXPORT", "IMPORT", "PAYMENT", "SYNC", "ERROR" -> actionType.uppercase()
                "ADD_PAYMENT", "EDIT_PAYMENT", "DELETE_PAYMENT" -> "PAYMENT"
                "ADD_BILL", "EDIT_BILL", "DELETE_BILL" -> "CREATE"
                else -> {
                    if (actionType.contains("CREATE", ignoreCase = true) || actionType.contains("ADD", ignoreCase = true)) "CREATE"
                    else if (actionType.contains("UPDATE", ignoreCase = true) || actionType.contains("EDIT", ignoreCase = true)) "UPDATE"
                    else if (actionType.contains("DELETE", ignoreCase = true) || actionType.contains("REMOVE", ignoreCase = true)) "DELETE"
                    else if (actionType.contains("LOGIN", ignoreCase = true)) "LOGIN"
                    else if (actionType.contains("LOGOUT", ignoreCase = true)) "LOGOUT"
                    else if (actionType.contains("PRINT", ignoreCase = true)) "PRINT"
                    else if (actionType.contains("EXPORT", ignoreCase = true)) "EXPORT"
                    else if (actionType.contains("IMPORT", ignoreCase = true)) "IMPORT"
                    else if (actionType.contains("SYNC", ignoreCase = true)) "SYNC"
                    else if (actionType.contains("ERROR", ignoreCase = true)) "ERROR"
                    else "UPDATE"
                }
            }
            
            val finalModule = when (module) {
                "Bills", "Payments", "Buyers", "Sellers", "Brokers", "Contracts", "Users", "Authentication", "Settings", "System" -> module
                "Auth" -> "Authentication"
                "contract_bills" -> "Bills"
                "payments" -> "Payments"
                "sellers" -> "Sellers"
                "buyers" -> "Buyers"
                "brokers" -> "Brokers"
                "users" -> "Users"
                else -> {
                    if (module.contains("Bill", ignoreCase = true)) "Bills"
                    else if (module.contains("Payment", ignoreCase = true)) "Payments"
                    else if (module.contains("Seller", ignoreCase = true)) "Sellers"
                    else if (module.contains("Buyer", ignoreCase = true)) "Buyers"
                    else if (module.contains("Broker", ignoreCase = true)) "Brokers"
                    else if (module.contains("Contract", ignoreCase = true)) "Contracts"
                    else if (module.contains("User", ignoreCase = true)) "Users"
                    else if (module.contains("Auth", ignoreCase = true)) "Authentication"
                    else if (module.contains("Setting", ignoreCase = true)) "Settings"
                    else "System"
                }
            }
            
            // Map fields
            val finalScreen = when {
                module.isNotBlank() -> module
                collectionName.isNotBlank() -> collectionName
                else -> "System"
            }
            
            // Bill number extraction
            val resolvedBillNo = billNoOverride ?: recordTitle.toIntOrNull()?.toString() ?: ""
            val resolvedPartyName = partyNameOverride ?: when {
                finalModule == "Sellers" || finalModule == "Buyers" || finalModule == "Brokers" -> recordTitle
                else -> ""
            }
            
            // Old / New values
            val resolvedOldValue = oldValueOverride ?: safeMapToJson(oldDataMap)
            val resolvedNewValue = newValueOverride ?: safeMapToJson(newDataMap)
            
            // Compute changed fields
            val changedFieldsMap = mutableMapOf<String, Map<String, Any?>>()
            if (oldDataMap != null && newDataMap != null) {
                for ((key, newVal) in newDataMap) {
                    val oldVal = oldDataMap[key]
                    if (oldVal != newVal) {
                        val oldStr = when (oldVal) {
                            null -> ""
                            is com.google.firebase.firestore.FieldValue -> "serverTimestamp"
                            is com.google.firebase.Timestamp -> java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(oldVal.toDate())
                            is java.util.Date -> java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(oldVal)
                            else -> oldVal.toString()
                        }
                        val newStr = when (newVal) {
                            null -> ""
                            is com.google.firebase.firestore.FieldValue -> "serverTimestamp"
                            is com.google.firebase.Timestamp -> java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(newVal.toDate())
                            is java.util.Date -> java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(newVal)
                            else -> newVal.toString()
                        }
                        if (oldStr != newStr) {
                            changedFieldsMap[key] = mapOf(
                                "before" to oldStr,
                                "after" to newStr
                            )
                        }
                    }
                }
            }
            val resolvedChangedFields = if (changedFieldsMap.isNotEmpty()) {
                org.json.JSONObject(changedFieldsMap as Map<*, *>).toString()
            } else {
                customChangedFields ?: ""
            }

            val resolvedDescription = descriptionOverride ?: "Action $finalAction performed on $finalModule ($recordTitle)"
            
            // Reference to standard single collection
            val logsColl = db.collection("firms").document(finalFirmId).collection("logs")
            val logDocRef = logsColl.document()
            val finalLogId = logDocRef.id
            
            val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val sdfTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val currentDate = sdfDate.format(java.util.Date())
            val currentTime = sdfTime.format(java.util.Date())
            
            val logData = hashMapOf(
                "logId" to finalLogId,
                "timestamp" to FieldValue.serverTimestamp(),
                "date" to currentDate,
                "time" to currentTime,
                "action" to finalAction,
                "actionType" to finalAction,
                "module" to finalModule,
                "screen" to finalScreen,
                "collectionName" to collectionName,
                "userId" to finalUserId,
                "userName" to finalUserName,
                "userEmail" to finalUserEmail,
                "userRole" to finalUserRole,
                "firmId" to finalFirmId,
                "firmName" to finalFirmName,
                "device" to android.os.Build.MODEL,
                "deviceModel" to android.os.Build.MODEL,
                "androidVersion" to android.os.Build.VERSION.RELEASE,
                "appVersion" to "1.0.0",
                "sessionId" to (com.example.ui.RanisaViewModel.currentSessionId ?: "S_UNKNOWN"),
                "ipSessionId" to (com.example.ui.RanisaViewModel.currentSessionId ?: "S_UNKNOWN"),
                "billNo" to resolvedBillNo,
                "partyName" to resolvedPartyName,
                "documentId" to documentId,
                "recordTitle" to recordTitle,
                "oldValue" to resolvedOldValue,
                "newValue" to resolvedNewValue,
                "oldData" to resolvedOldValue,
                "newData" to resolvedNewValue,
                "changedFields" to resolvedChangedFields,
                "description" to resolvedDescription,
                "status" to status
            )
            
            if (batch != null) {
                batch.set(logDocRef, logData)
            } else {
                logDocRef.set(logData).await()
            }
            android.util.Log.d("FirebaseService", "[DEBUG] Firestore path: firms/$finalFirmId/logs/$finalLogId")
            android.util.Log.d("FirebaseService", "[DEBUG] Query: Single log write")
            android.util.Log.d("FirebaseService", "[DEBUG] Document count: 1")
            android.util.Log.d("FirebaseService", "[DEBUG] Snapshot updates: Log write completed successfully.")
        } catch (e: Exception) {
            android.util.Log.e("FirebaseService", "[DEBUG] Firebase exception in logEnterpriseAudit: ${e.message}", e)
        }
    }

    suspend fun getEnterpriseAuditLogs(
        currentFirmId: String,
        isGlobalAdminMode: Boolean,
        limit: Long = 300
    ): Pair<List<com.google.firebase.firestore.DocumentSnapshot>, Boolean> {
        val db = FirebaseFirestore.getInstance()
        
        try {
            val documents = if (isGlobalAdminMode) {
                val firmIds = try {
                    val snapshot = db.collection("firms").get().await()
                    val ids = snapshot.documents.map { it.id }.distinct()
                    if (ids.isEmpty()) listOf("F001", "F002") else ids
                } catch (e: Exception) {
                    android.util.Log.w("FirebaseService", "Firestore firms fetch failed, using fallback.", e)
                    listOf("F001", "F002")
                }
                
                kotlinx.coroutines.coroutineScope {
                    val deferreds = firmIds.map { firmId ->
                        this@coroutineScope.async(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                db.collection("firms").document(firmId).collection("logs")
                                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                                    .limit(limit)
                                    .get()
                                    .await()
                                    .documents
                            } catch (e: Exception) {
                                android.util.Log.w("FirebaseService", "Failed to fetch logs for firm $firmId", e)
                                emptyList<com.google.firebase.firestore.DocumentSnapshot>()
                            }
                        }
                    }
                    deferreds.awaitAll().flatten()
                }
            } else {
                db.collection("firms").document(currentFirmId).collection("logs")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(limit)
                    .get()
                    .await()
                    .documents
            }
            
            val sortedDocuments = documents.sortedWith { doc1, doc2 ->
                fun getSafeTime(doc: com.google.firebase.firestore.DocumentSnapshot): Long {
                    try {
                        val ts = doc.getTimestamp("timestamp") ?: doc.getTimestamp("time")
                        if (ts != null) return ts.seconds
                    } catch (e: Exception) {}
                    
                    try {
                        val dateStr = doc.getString("date") ?: ""
                        val timeStr = doc.getString("time") ?: doc.getString("timeStr") ?: ""
                        if (dateStr.isNotBlank()) {
                            val fullStr = if (timeStr.isNotBlank()) "$dateStr $timeStr" else dateStr
                            val format = if (timeStr.isNotBlank()) {
                                if (timeStr.contains(":")) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd"
                            } else "yyyy-MM-dd"
                            val parsedDate = java.text.SimpleDateFormat(format, java.util.Locale.getDefault()).parse(fullStr)
                            if (parsedDate != null) return parsedDate.time / 1000
                        }
                    } catch (e: Exception) {}
                    
                    return 0L
                }
                
                val t1 = getSafeTime(doc1)
                val t2 = getSafeTime(doc2)
                t2.compareTo(t1) // descending
            }.take(limit.toInt())
            
            return Pair(sortedDocuments, false)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseService", "[DEBUG] Firebase exception in getEnterpriseAuditLogs: ${e.message}", e)
            throw e
        }
    }

    fun getEnterpriseAuditLogsListener(
        currentFirmId: String,
        isGlobalAdminMode: Boolean,
        limit: Long = 300,
        onError: (Exception) -> Unit,
        onUpdate: (List<com.google.firebase.firestore.DocumentSnapshot>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        val db = FirebaseFirestore.getInstance()
        
        if (!isGlobalAdminMode) {
            val query = db.collection("firms").document(currentFirmId).collection("logs")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit)
            return query.addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    android.util.Log.e("FirebaseService", "[DEBUG] Firebase exceptions / Indexes / Permissions: ${exception.message}", exception)
                    onError(exception)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    onUpdate(snapshot.documents)
                }
            }
        } else {
            val lock = Any()
            var isCancelled = false
            val activeRegistrations = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
            val firmDocsMap = mutableMapOf<String, List<com.google.firebase.firestore.DocumentSnapshot>>()
            
            val parentRegistration = object : com.google.firebase.firestore.ListenerRegistration {
                override fun remove() {
                    synchronized(lock) {
                        isCancelled = true
                        activeRegistrations.forEach { it.remove() }
                        activeRegistrations.clear()
                    }
                }
            }
            
            db.collection("firms").get().addOnCompleteListener { task ->
                synchronized(lock) {
                    if (isCancelled) return@addOnCompleteListener
                    
                    val firmIds = if (task.isSuccessful && task.result != null) {
                        task.result.documents.map { it.id }.distinct()
                    } else {
                        android.util.Log.w("FirebaseService", "Firestore firms fetch failed, using fallback.", task.exception)
                        listOf("F001", "F002")
                    }
                    
                    if (firmIds.isEmpty()) {
                        onUpdate(emptyList())
                        return@addOnCompleteListener
                    }
                    
                    for (firmId in firmIds) {
                        val q = db.collection("firms").document(firmId).collection("logs")
                            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                            .limit(limit)
                        
                        val reg = q.addSnapshotListener { subSnapshot, subException ->
                            if (subException != null) {
                                onError(subException)
                                return@addSnapshotListener
                            }
                            if (subSnapshot != null) {
                                synchronized(lock) {
                                    if (isCancelled) return@addSnapshotListener
                                    firmDocsMap[firmId] = subSnapshot.documents
                                    
                                    val mergedList = firmDocsMap.values.flatten()
                                    val sortedList = mergedList.sortedWith { d1, d2 ->
                                        val t1 = d1.getTimestamp("timestamp")?.seconds ?: 0L
                                        val t2 = d2.getTimestamp("timestamp")?.seconds ?: 0L
                                        t2.compareTo(t1)
                                    }
                                    onUpdate(sortedList.take(limit.toInt()))
                                }
                            }
                        }
                        activeRegistrations.add(reg)
                    }
                }
            }
            
            return parentRegistration
        }
    }

    suspend fun getEnterpriseAuditLogsPaginated(
        currentFirmId: String,
        isGlobalAdminMode: Boolean,
        limit: Long = 50,
        startAfterDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    ): List<com.google.firebase.firestore.DocumentSnapshot> {
        val db = FirebaseFirestore.getInstance()
        
        return try {
            val documents = if (isGlobalAdminMode) {
                val firmIds = try {
                    val snapshot = db.collection("firms").get().await()
                    val ids = snapshot.documents.map { it.id }.distinct()
                    if (ids.isEmpty()) listOf("F001", "F002") else ids
                } catch (e: Exception) {
                    android.util.Log.w("FirebaseService", "Firestore firms fetch failed, using fallback.", e)
                    listOf("F001", "F002")
                }
                
                kotlinx.coroutines.coroutineScope {
                    val deferreds = firmIds.map { firmId ->
                        this@coroutineScope.async(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                var q = db.collection("firms").document(firmId).collection("logs")
                                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                                if (startAfterDoc != null) {
                                    q = q.startAfter(startAfterDoc)
                                }
                                q.limit(limit).get().await().documents
                            } catch (e: Exception) {
                                android.util.Log.w("FirebaseService", "Failed to paginate logs for firm $firmId", e)
                                emptyList<com.google.firebase.firestore.DocumentSnapshot>()
                            }
                        }
                    }
                    deferreds.awaitAll().flatten()
                }
            } else {
                var q = db.collection("firms").document(currentFirmId).collection("logs")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                if (startAfterDoc != null) {
                    q = q.startAfter(startAfterDoc)
                }
                q.limit(limit).get().await().documents
            }
            
            val sortedDocuments = documents.sortedWith { doc1, doc2 ->
                fun getSafeTime(doc: com.google.firebase.firestore.DocumentSnapshot): Long {
                    try {
                        val ts = doc.getTimestamp("timestamp") ?: doc.getTimestamp("time")
                        if (ts != null) return ts.seconds
                    } catch (e: Exception) {}
                    return 0L
                }
                val t1 = getSafeTime(doc1)
                val t2 = getSafeTime(doc2)
                t2.compareTo(t1) // descending
            }.take(limit.toInt())
            
            sortedDocuments
        } catch (e: Exception) {
            android.util.Log.e("FirebaseService", "[DEBUG] Pagination query failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun logActionToFirebase(
        context: Context,
        action: String,
        billNo: String,
        firm: String,
        user: String,
        role: String,
        details: String
    ) {
        val mappedAction = when (action.uppercase()) {
            "ADD_PAYMENT", "EDIT_PAYMENT", "DELETE_PAYMENT", "PAYMENT" -> "PAYMENT"
            "ADD_BILL", "EDIT_BILL", "DELETE_BILL" -> "CREATE"
            else -> {
                if (action.contains("CREATE", ignoreCase = true) || action.contains("ADD", ignoreCase = true)) "CREATE"
                else if (action.contains("UPDATE", ignoreCase = true) || action.contains("EDIT", ignoreCase = true)) "UPDATE"
                else if (action.contains("DELETE", ignoreCase = true) || action.contains("REMOVE", ignoreCase = true)) "DELETE"
                else if (action.contains("LOGIN", ignoreCase = true)) "LOGIN"
                else if (action.contains("LOGOUT", ignoreCase = true)) "LOGOUT"
                else if (action.contains("PRINT", ignoreCase = true)) "PRINT"
                else if (action.contains("EXPORT", ignoreCase = true)) "EXPORT"
                else if (action.contains("IMPORT", ignoreCase = true)) "IMPORT"
                else if (action.contains("SYNC", ignoreCase = true)) "SYNC"
                else if (action.contains("ERROR", ignoreCase = true)) "ERROR"
                else "UPDATE"
            }
        }

        val mappedModule = when {
            details.contains("Bill", ignoreCase = true) || action.contains("Bill", ignoreCase = true) -> "Bills"
            details.contains("Payment", ignoreCase = true) || action.contains("Payment", ignoreCase = true) -> "Payments"
            details.contains("Seller", ignoreCase = true) -> "Sellers"
            details.contains("Buyer", ignoreCase = true) -> "Buyers"
            details.contains("Broker", ignoreCase = true) -> "Brokers"
            else -> "System"
        }

        logEnterpriseAudit(
            context = context,
            actionType = mappedAction,
            module = mappedModule,
            collectionName = "logs",
            documentId = billNo,
            recordTitle = "Bill: $billNo",
            userOverride = user,
            roleOverride = role,
            descriptionOverride = details,
            billNoOverride = billNo,
            firmNameOverride = firm
        )
    }

    suspend fun logAuditLogToFirebase(log: AuditLog) {
        logEnterpriseAudit(
            context = FirebaseFirestore.getInstance().app.applicationContext,
            actionType = log.action,
            module = when {
                log.screen.contains("Bill", ignoreCase = true) -> "Bills"
                log.screen.contains("Payment", ignoreCase = true) -> "Payments"
                log.screen.contains("Seller", ignoreCase = true) -> "Sellers"
                log.screen.contains("Buyer", ignoreCase = true) -> "Buyers"
                log.screen.contains("Broker", ignoreCase = true) -> "Brokers"
                else -> "System"
            },
            collectionName = "logs",
            documentId = log.billNo,
            recordTitle = log.partyName,
            userOverride = log.userName,
            roleOverride = log.userRole,
            descriptionOverride = log.oldValue + " -> " + log.newValue,
            billNoOverride = log.billNo,
            partyNameOverride = log.partyName,
            firmNameOverride = log.firmName,
            oldValueOverride = log.oldValue,
            newValueOverride = log.newValue
        )
    }

    suspend fun saveBillToFirebase(
        context: Context,
        bill: ContractBill,
        user: String,
        role: String
    ): Boolean {
        if (!isFirebaseInitialized(context)) return false
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = getSanitizedFirmId(bill.firmName)
            val billKey = getBillNodeKey(bill.billNumber)

            val billData = hashMapOf(
                "billNo" to (bill.billNumber.toIntOrNull() ?: bill.billNumber),
                "billNumber" to bill.billNumber,
                "date" to bill.date,
                "sellerName" to bill.sellerName,
                "buyerName" to bill.buyerName,
                "gstNo" to bill.gstNo,
                "particulars" to bill.particulars,
                "bagsKg" to bill.bags,
                "bags" to bill.bags,
                "qtls" to bill.quintals,
                "quintals" to bill.quintals,
                "rate" to bill.rate,
                "packing" to bill.packing,
                "transport" to bill.transport,
                "delivery" to bill.delivery,
                "lorryNo" to bill.lorryNo,
                "payment" to bill.payment,
                "mobileNo" to bill.mobileNo,
                "brand" to bill.brand,
                "lorryFreight" to bill.lorryFreight,
                "creditDays" to bill.creditDays,
                "amountInWords" to bill.amountInWords,
                "totalWeightKg" to (bill.quintals * 100.0),
                "billAmount" to bill.billAmount,
                "outstandingBalance" to bill.balance,
                "balance" to bill.balance,
                "place" to bill.place,
                "bankName" to bill.bankName,
                "remarks" to bill.remarks,
                "ddAmount" to bill.ddAmount,
                "cashCutting" to bill.cashCutting,
                "sellerSignature" to bill.sellerSignature,
                "totalReceived" to bill.totalReceived,
                "remainingBalance" to bill.remainingBalance,
                "paymentStatus" to bill.paymentStatus,
                "lastPaymentDate" to bill.lastPaymentDate,
                "sellerAddress" to bill.sellerAddress,
                "buyerAddress" to bill.buyerAddress,
                "itemsJson" to bill.itemsJson,
                "items" to deserializeItems(bill.itemsJson).map { item ->
                    mapOf(
                        "particulars" to item.particulars,
                        "bags" to item.bags,
                        "packing" to item.packing,
                        "qtls" to item.qtls,
                        "rate" to item.rate,
                        "itemTotal" to (item.qtls * item.rate),
                        "itemAmount" to (item.qtls * item.rate)
                    )
                },
                "discountPercent" to bill.discountPercent,
                "discountAmount" to bill.discountPercent,
                "commissionPercent" to bill.commissionPercent,
                "commissionAmount" to bill.commissionPercent,
                "finalPayment" to bill.balance,
                "paymentReceived" to bill.totalReceived,
                "outstandingAmount" to bill.remainingBalance,
                "finalPendingAmount" to bill.balance,
                "remark1" to bill.remark1,
                "remark2" to bill.remark2,
                "brokerName" to bill.brokerName,
                "brokerId" to bill.brokerId,
                "eb" to bill.eb,
                "createdBy" to user,
                "createdRole" to role,
                "createdTime" to FieldValue.serverTimestamp(),
                "lastUpdatedBy" to user,
                "lastUpdatedTime" to FieldValue.serverTimestamp(),
                "status" to "Active"
            )

            val batch = db.batch()
            val contractRef = db.collection("firms").document(firmPath)
                .collection("contracts").document(billKey)
            batch.set(contractRef, billData)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "CREATE",
                    module = "Bills",
                    collectionName = "contract_bills",
                    documentId = billKey,
                    recordTitle = "Bill: ${bill.billNumber}",
                    newDataMap = billData,
                    userOverride = user,
                    roleOverride = role,
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside saveBillToFirebase", le)
            }

            batch.commit().await()

            // Automatically check/insert settings autocomplete fields under firm path
            val settingsColl = db.collection("firms").document(firmPath).collection("settings")
            if (bill.transport.isNotBlank()) {
                settingsColl.document("transports").set(mapOf(bill.transport to true), SetOptions.merge())
            }
            if (bill.brand.isNotBlank()) {
                settingsColl.document("brands").set(mapOf(bill.brand to true), SetOptions.merge())
            }
            if (bill.mobileNo.isNotBlank()) {
                settingsColl.document("mobileNumbers").set(mapOf(bill.mobileNo to true), SetOptions.merge())
            }
            if (bill.gstNo.isNotBlank()) {
                settingsColl.document("gstNumbers").set(mapOf(bill.gstNo to true), SetOptions.merge())
            }

            // Write Log Action
            logActionToFirebase(
                context = context,
                action = "CREATE",
                billNo = bill.billNumber,
                firm = bill.firmName,
                user = user,
                role = role,
                details = "Created Contract Bill"
            )

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bill to Firestore", e)
            return false
        }
    }

    suspend fun updateBillInFirebase(
        context: Context,
        oldBill: ContractBill?,
        newBill: ContractBill,
        user: String,
        role: String
    ): Boolean {
        if (!isFirebaseInitialized(context)) return false
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = getSanitizedFirmId(newBill.firmName)
            val billKey = getBillNodeKey(newBill.billNumber)

            val billData = hashMapOf(
                "billNo" to (newBill.billNumber.toIntOrNull() ?: newBill.billNumber),
                "billNumber" to newBill.billNumber,
                "date" to newBill.date,
                "sellerName" to newBill.sellerName,
                "buyerName" to newBill.buyerName,
                "gstNo" to newBill.gstNo,
                "particulars" to newBill.particulars,
                "bagsKg" to newBill.bags,
                "bags" to newBill.bags,
                "qtls" to newBill.quintals,
                "quintals" to newBill.quintals,
                "rate" to newBill.rate,
                "packing" to newBill.packing,
                "transport" to newBill.transport,
                "delivery" to newBill.delivery,
                "lorryNo" to newBill.lorryNo,
                "payment" to newBill.payment,
                "mobileNo" to newBill.mobileNo,
                "brand" to newBill.brand,
                "lorryFreight" to newBill.lorryFreight,
                "creditDays" to newBill.creditDays,
                "amountInWords" to newBill.amountInWords,
                "totalWeightKg" to (newBill.quintals * 100.0),
                "billAmount" to newBill.billAmount,
                "outstandingBalance" to newBill.balance,
                "balance" to newBill.balance,
                "place" to newBill.place,
                "bankName" to newBill.bankName,
                "remarks" to newBill.remarks,
                "ddAmount" to newBill.ddAmount,
                "cashCutting" to newBill.cashCutting,
                "sellerSignature" to newBill.sellerSignature,
                "totalReceived" to newBill.totalReceived,
                "remainingBalance" to newBill.remainingBalance,
                "paymentStatus" to newBill.paymentStatus,
                "lastPaymentDate" to newBill.lastPaymentDate,
                "sellerAddress" to newBill.sellerAddress,
                "buyerAddress" to newBill.buyerAddress,
                "itemsJson" to newBill.itemsJson,
                "items" to deserializeItems(newBill.itemsJson).map { item ->
                    mapOf(
                        "particulars" to item.particulars,
                        "bags" to item.bags,
                        "packing" to item.packing,
                        "qtls" to item.qtls,
                        "rate" to item.rate,
                        "itemTotal" to (item.qtls * item.rate),
                        "itemAmount" to (item.qtls * item.rate)
                    )
                },
                "discountPercent" to newBill.discountPercent,
                "discountAmount" to newBill.discountPercent,
                "commissionPercent" to newBill.commissionPercent,
                "commissionAmount" to newBill.commissionPercent,
                "finalPayment" to newBill.balance,
                "paymentReceived" to newBill.totalReceived,
                "outstandingAmount" to newBill.remainingBalance,
                "finalPendingAmount" to newBill.balance,
                "remark1" to newBill.remark1,
                "remark2" to newBill.remark2,
                "brokerName" to newBill.brokerName,
                "brokerId" to newBill.brokerId,
                "eb" to newBill.eb,
                "createdBy" to (oldBill?.sellerName ?: user),
                "createdRole" to role,
                "createdTime" to FieldValue.serverTimestamp(),
                "lastUpdatedBy" to user,
                "lastUpdatedTime" to FieldValue.serverTimestamp(),
                "status" to "Active"
            )

            val batch = db.batch()
            val contractRef = db.collection("firms").document(firmPath)
                .collection("contracts").document(billKey)
            batch.set(contractRef, billData)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "UPDATE",
                    module = "Bills",
                    collectionName = "contract_bills",
                    documentId = billKey,
                    recordTitle = "Bill: ${newBill.billNumber}",
                    oldDataMap = contractBillToMap(oldBill),
                    newDataMap = billData,
                    userOverride = user,
                    roleOverride = role,
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside updateBillInFirebase", le)
            }

            batch.commit().await()

            // Automatically check/insert settings autocomplete fields under firm path
            val settingsColl = db.collection("firms").document(firmPath).collection("settings")
            if (newBill.transport.isNotBlank()) {
                settingsColl.document("transports").set(mapOf(newBill.transport to true), SetOptions.merge())
            }
            if (newBill.brand.isNotBlank()) {
                settingsColl.document("brands").set(mapOf(newBill.brand to true), SetOptions.merge())
            }
            if (newBill.mobileNo.isNotBlank()) {
                settingsColl.document("mobileNumbers").set(mapOf(newBill.mobileNo to true), SetOptions.merge())
            }
            if (newBill.gstNo.isNotBlank()) {
                settingsColl.document("gstNumbers").set(mapOf(newBill.gstNo to true), SetOptions.merge())
            }

            val rateChanged = oldBill != null && oldBill.rate != newBill.rate
            val details = if (rateChanged) {
                "Changed Rate from ${oldBill?.rate?.toInt() ?: 0} to ${newBill.rate.toInt()}"
            } else {
                "Updated Contract Bill"
            }

            logActionToFirebase(
                context = context,
                action = "UPDATE",
                billNo = newBill.billNumber,
                firm = newBill.firmName,
                user = user,
                role = role,
                details = details
            )

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating bill in Firestore", e)
            return false
        }
    }

    suspend fun deleteBillFromFirebase(
        context: Context,
        bill: ContractBill,
        user: String,
        role: String
    ): Boolean {
        if (!isFirebaseInitialized(context)) return false
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = getSanitizedFirmId(bill.firmName)
            val billKey = getBillNodeKey(bill.billNumber)

            val batch = db.batch()
            val contractRef = db.collection("firms").document(firmPath)
                .collection("contracts").document(billKey)
            batch.delete(contractRef)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "DELETE",
                    module = "Bills",
                    collectionName = "contract_bills",
                    documentId = billKey,
                    recordTitle = "Bill: ${bill.billNumber}",
                    oldDataMap = contractBillToMap(bill),
                    userOverride = user,
                    roleOverride = role,
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside deleteBillFromFirebase", le)
            }

            batch.commit().await()

            logActionToFirebase(
                context = context,
                action = "DELETE",
                billNo = bill.billNumber,
                firm = bill.firmName,
                user = user,
                role = role,
                details = "Deleted Bill"
            )

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting bill in Firestore", e)
            return false
        }
    }

    suspend fun savePaymentToFirebase(
        context: Context,
        payment: Payment,
        user: String,
        role: String
    ): Boolean {
        if (!isFirebaseInitialized(context)) return false
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = getSanitizedFirmId(payment.firm)
            val paymentsColl = db.collection("firms").document(firmPath).collection("payments")

            val paymentId = payment.paymentId.ifBlank {
                paymentsColl.document().id
            }

            val timestamp = if (payment.timestamp > 0L) payment.timestamp else System.currentTimeMillis()
            val createdAt = payment.createdAt.ifBlank {
                payment.paymentDate.ifBlank {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                }
            }

            val paymentData = hashMapOf(
                "paymentId" to paymentId,
                "buyerId" to payment.buyerId.ifBlank { payment.buyerName },
                "buyerName" to payment.buyerName,
                "amount" to payment.paymentAmount,
                "paymentMode" to payment.paymentMode,
                "bankName" to payment.referenceNumber,
                "remarks" to payment.remarks,
                "createdBy" to user,
                "createdByName" to user,
                "createdAt" to createdAt,
                "timestamp" to timestamp,
                "billNo" to payment.billNo,
                "firm" to payment.firm,
                "sellerName" to payment.sellerName,
                "paymentAmount" to payment.paymentAmount,
                "paymentDate" to createdAt,
                "referenceNumber" to payment.referenceNumber,
                "receivedBy" to user,
                "receivedTime" to timestamp,
                "billAmount" to payment.billAmount,
                "receivedAmount" to payment.paymentAmount,
                "discount" to payment.discountAmount,
                "commission" to payment.commissionAmount,
                "remarkAmt" to (payment.remarks1.toDoubleOrNull() ?: 0.0),
                "remark" to payment.remarks2,
                "balanceAmount" to payment.pendingAmount
            )

            val batch = db.batch()
            val paymentRef = paymentsColl.document(paymentId)
            batch.set(paymentRef, paymentData)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "CREATE_PAYMENT",
                    module = "Payments",
                    collectionName = "payments",
                    documentId = paymentId,
                    recordTitle = "Payment: ${payment.paymentAmount} for Bill: ${payment.billNo}",
                    newDataMap = paymentData,
                    userOverride = user,
                    roleOverride = role,
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside savePaymentToFirebase", le)
            }

            batch.commit().await()
            Log.d("FirebaseService", "Payment Saved to Firestore: $paymentId")

            // Also ensure buyer is in settings
            if (payment.buyerName.isNotBlank()) {
                db.collection("firms").document(firmPath)
                    .collection("settings").document("buyers")
                    .set(mapOf(payment.buyerName to true), SetOptions.merge())
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving payment to Firestore", e)
            return false
        }
    }

    suspend fun savePaymentAtomicTransaction(
        context: Context,
        bill: ContractBill,
        newPayment: Payment,
        user: String,
        role: String
    ): Boolean {
        if (!isFirebaseInitialized(context)) return false
        try {
            val db = FirebaseFirestore.getInstance()
            val batch = db.batch()

            val firmPath = getSanitizedFirmId(bill.firmName)
            val billKey = getBillNodeKey(bill.billNumber)

            val billRef = db.collection("firms").document(firmPath)
                .collection("contracts").document(billKey)

            val paymentRef = db.collection("firms").document(firmPath)
                .collection("payments").document(newPayment.paymentId)

            val buyerSettingRef = db.collection("firms").document(firmPath)
                .collection("settings").document("buyers")

            val billData = hashMapOf(
                "billNo" to (bill.billNumber.toIntOrNull() ?: bill.billNumber),
                "billNumber" to bill.billNumber,
                "date" to bill.date,
                "sellerName" to bill.sellerName,
                "buyerName" to bill.buyerName,
                "gstNo" to bill.gstNo,
                "particulars" to bill.particulars,
                "bagsKg" to bill.bags,
                "bags" to bill.bags,
                "qtls" to bill.quintals,
                "quintals" to bill.quintals,
                "rate" to bill.rate,
                "packing" to bill.packing,
                "transport" to bill.transport,
                "delivery" to bill.delivery,
                "lorryNo" to bill.lorryNo,
                "payment" to bill.payment,
                "mobileNo" to bill.mobileNo,
                "brand" to bill.brand,
                "lorryFreight" to bill.lorryFreight,
                "creditDays" to bill.creditDays,
                "amountInWords" to bill.amountInWords,
                "totalWeightKg" to (bill.quintals * 100.0),
                "billAmount" to bill.billAmount,
                "outstandingBalance" to bill.balance,
                "balance" to bill.balance,
                "place" to bill.place,
                "bankName" to bill.bankName,
                "remarks" to bill.remarks,
                "ddAmount" to bill.ddAmount,
                "cashCutting" to bill.cashCutting,
                "sellerSignature" to bill.sellerSignature,
                "totalReceived" to bill.totalReceived,
                "remainingBalance" to bill.remainingBalance,
                "paymentStatus" to bill.paymentStatus,
                "lastPaymentDate" to bill.lastPaymentDate,
                "discountPercent" to bill.discountPercent,
                "discountAmount" to bill.discountPercent,
                "commissionPercent" to bill.commissionPercent,
                "commissionAmount" to bill.commissionPercent,
                "remark1" to bill.remark1,
                "remark2" to bill.remark2,
                "brokerName" to bill.brokerName,
                "brokerId" to bill.brokerId,
                "createdBy" to (bill.sellerName.ifBlank { user }),
                "createdRole" to role,
                "createdTime" to FieldValue.serverTimestamp(),
                "lastUpdatedBy" to user,
                "lastUpdatedTime" to FieldValue.serverTimestamp(),
                "status" to "Active"
            )

            val paymentData = hashMapOf(
                "paymentId" to newPayment.paymentId,
                "buyerId" to newPayment.buyerId.ifBlank { newPayment.buyerName },
                "buyerName" to newPayment.buyerName,
                "amount" to newPayment.paymentAmount,
                "paymentMode" to newPayment.paymentMode,
                "bankName" to newPayment.referenceNumber,
                "remarks" to newPayment.remarks,
                "createdBy" to user,
                "createdByName" to user,
                "createdAt" to newPayment.createdAt,
                "timestamp" to newPayment.timestamp,
                "billNo" to newPayment.billNo,
                "firm" to newPayment.firm,
                "sellerName" to newPayment.sellerName,
                "paymentAmount" to newPayment.paymentAmount,
                "paymentDate" to newPayment.paymentDate.ifBlank { newPayment.createdAt },
                "referenceNumber" to newPayment.referenceNumber,
                "receivedBy" to user,
                "receivedTime" to newPayment.timestamp,
                "paymentReceived" to newPayment.paymentAmount,
                "discountPercent" to newPayment.discountPercent,
                "discountAmount" to newPayment.discountAmount,
                "commissionPercent" to newPayment.commissionPercent,
                "commissionAmount" to newPayment.commissionAmount,
                "remarks1" to newPayment.remarks1,
                "remarks2" to newPayment.remarks2,
                "alreadyPaidAmount" to newPayment.alreadyPaidAmount,
                "pendingAmount" to newPayment.pendingAmount,
                "updatedAt" to newPayment.updatedAt,
                "updatedBy" to newPayment.updatedBy,
                "billAmount" to bill.billAmount,
                "receivedAmount" to newPayment.paymentAmount,
                "discount" to newPayment.discountAmount,
                "commission" to newPayment.commissionAmount,
                "remarkAmt" to (newPayment.remarks1.toDoubleOrNull() ?: 0.0),
                "remark" to newPayment.remarks2,
                "balanceAmount" to newPayment.pendingAmount
            )

            batch.set(billRef, billData)
            batch.set(paymentRef, paymentData)
            batch.set(buyerSettingRef, mapOf(newPayment.buyerName to true), SetOptions.merge())

            // Create Audit log inside current firm's logs subcollection
            val auditLogRef = db.collection("firms").document(firmPath).collection("logs").document()
            val auditData = hashMapOf(
                "userName" to user,
                "date" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                "time" to SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                "screen" to "Party Ledger Dialog",
                "action" to "EDIT_PAYMENT_TRANSACTION",
                "oldValue" to "Received: ${bill.totalReceived - newPayment.paymentAmount}, Pending: ${bill.remainingBalance + newPayment.paymentAmount}",
                "newValue" to "Received: ${bill.totalReceived}, Pending: ${bill.remainingBalance}",
                "device" to "Android SDK " + Build.VERSION.SDK_INT
            )
            batch.set(auditLogRef, auditData)

            batch.commit().await()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Transaction multi-path update failed in Firestore", e)
            return false
        }
    }

    suspend fun deletePaymentFromFirebase(
        context: Context,
        payment: Payment
    ): Boolean {
        if (!isFirebaseInitialized(context)) return false
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = getSanitizedFirmId(payment.firm)
            val paymentKey = payment.paymentId.ifBlank { String.format("PAYMENT_%06d", payment.id) }

            val batch = db.batch()
            val paymentRef = db.collection("firms").document(firmPath)
                .collection("payments").document(paymentKey)
            batch.delete(paymentRef)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "DELETE_PAYMENT",
                    module = "Payments",
                    collectionName = "payments",
                    documentId = paymentKey,
                    recordTitle = "Payment: ${payment.paymentAmount} for Bill: ${payment.billNo}",
                    oldDataMap = paymentToMap(payment),
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside deletePaymentFromFirebase", le)
            }

            batch.commit().await()

            Log.d("FirebaseService", "Payment Deleted: $paymentKey")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting payment in Firestore", e)
            return false
        }
    }

    suspend fun saveSellerToFirebase(
        context: Context,
        sellerName: String,
        mobile: String,
        place: String,
        gstNo: String,
        millName: String,
        address: String,
        user: String,
        firmName: String = ""
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = if (firmName.isNotBlank()) getSanitizedFirmId(firmName) else "F001"
            val sellersColl = db.collection("firms").document(firmPath).collection("sellers")

            val snapshot = sellersColl.get().await()
            for (doc in snapshot.documents) {
                val existingName = doc.getString("sellerName")
                if (existingName != null && existingName.equals(sellerName, ignoreCase = true)) {
                    return Pair(false, "Seller already exists.")
                }
            }

            val newDocRef = sellersColl.document()
            val sellerId = newDocRef.id

            val sellerData = hashMapOf(
                "sellerId" to sellerId,
                "sellerName" to sellerName,
                "mobile" to mobile,
                "place" to place,
                "gstNo" to gstNo,
                "millName" to millName,
                "address" to address,
                "createdBy" to user,
                "createdTime" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(newDocRef, sellerData)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "CREATE",
                    module = "Sellers",
                    collectionName = "sellers",
                    documentId = sellerId,
                    recordTitle = "Seller: $sellerName",
                    newDataMap = sellerData,
                    userOverride = user,
                    roleOverride = "User",
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside saveSellerToFirebase", le)
            }

            batch.commit().await()

            val logDetails = if (millName.isNotBlank()) millName else sellerName
            logActionToFirebase(
                context = context,
                action = "CREATE_SELLER",
                billNo = "",
                firm = firmName,
                user = user,
                role = "User",
                details = "Added Seller Master : $logDetails"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving seller to Firestore", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun saveBrokerToFirebase(
        context: Context,
        brokerName: String,
        mobile: String,
        address: String,
        user: String,
        firmName: String = ""
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = if (firmName.isNotBlank()) getSanitizedFirmId(firmName) else "F001"
            val brokersColl = db.collection("firms").document(firmPath).collection("brokers")

            val snapshot = brokersColl.get().await()
            for (doc in snapshot.documents) {
                val existingName = doc.getString("brokerName")
                if (existingName != null && existingName.equals(brokerName, ignoreCase = true)) {
                    return Pair(false, "Broker already exists.")
                }
            }

            val newDocRef = brokersColl.document()
            val brokerId = newDocRef.id

            val brokerData = hashMapOf(
                "brokerId" to brokerId,
                "brokerName" to brokerName,
                "mobile" to mobile,
                "address" to address,
                "totalBillings" to 0,
                "totalQtls" to 0.0,
                "createdBy" to user,
                "createdTime" to FieldValue.serverTimestamp(),
                "updatedBy" to user,
                "updatedTime" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(newDocRef, brokerData)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "CREATE",
                    module = "Brokers",
                    collectionName = "brokers",
                    documentId = brokerId,
                    recordTitle = "Broker: $brokerName",
                    newDataMap = brokerData,
                    userOverride = user,
                    roleOverride = "User",
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside saveBrokerToFirebase", le)
            }

            batch.commit().await()

            logActionToFirebase(
                context = context,
                action = "CREATE_BROKER",
                billNo = "",
                firm = firmName,
                user = user,
                role = "User",
                details = "Added Broker Master : $brokerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving broker to Firestore", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun updateBrokerInFirebase(
        context: Context,
        brokerId: String,
        brokerName: String,
        mobile: String,
        address: String,
        user: String,
        firmName: String = ""
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = if (firmName.isNotBlank()) getSanitizedFirmId(firmName) else "F001"
            val brokerDoc = db.collection("firms").document(firmPath)
                .collection("brokers").document(brokerId)

            val updates = hashMapOf<String, Any>(
                "brokerName" to brokerName,
                "mobile" to mobile,
                "address" to address,
                "updatedBy" to user,
                "updatedTime" to FieldValue.serverTimestamp()
            )

            val oldDoc = brokerDoc.get().await()
            val oldDataMap = oldDoc.data

            val batch = db.batch()
            batch.update(brokerDoc, updates)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "UPDATE",
                    module = "Brokers",
                    collectionName = "brokers",
                    documentId = brokerId,
                    recordTitle = "Broker: $brokerName",
                    oldDataMap = oldDataMap,
                    newDataMap = updates,
                    userOverride = user,
                    roleOverride = "User",
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside updateBrokerInFirebase", le)
            }

            batch.commit().await()

            logActionToFirebase(
                context = context,
                action = "UPDATE_BROKER",
                billNo = "",
                firm = firmName,
                user = user,
                role = "User",
                details = "Updated Broker Master : $brokerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating broker in Firestore", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun deleteBrokerFromFirebase(
        context: Context,
        brokerId: String,
        brokerName: String,
        user: String,
        firmName: String = ""
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = if (firmName.isNotBlank()) getSanitizedFirmId(firmName) else "F001"
            val brokerDoc = db.collection("firms").document(firmPath)
                .collection("brokers").document(brokerId)

            val oldDoc = brokerDoc.get().await()
            val oldDataMap = oldDoc.data

            val batch = db.batch()
            batch.delete(brokerDoc)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "DELETE",
                    module = "Brokers",
                    collectionName = "brokers",
                    documentId = brokerId,
                    recordTitle = "Broker: $brokerName",
                    oldDataMap = oldDataMap,
                    userOverride = user,
                    roleOverride = "User",
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside deleteBrokerFromFirebase", le)
            }

            batch.commit().await()

            logActionToFirebase(
                context = context,
                action = "DELETE_BROKER",
                billNo = "",
                firm = firmName,
                user = user,
                role = "User",
                details = "Deleted Broker Master : $brokerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting broker from Firestore", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun updateSellerInFirebase(
        context: Context,
        sellerId: String,
        sellerName: String,
        mobile: String,
        place: String,
        gstNo: String,
        millName: String,
        address: String,
        user: String,
        firmName: String = ""
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = if (firmName.isNotBlank()) getSanitizedFirmId(firmName) else "F001"
            val sellerDoc = db.collection("firms").document(firmPath)
                .collection("sellers").document(sellerId)

            val sellerData = hashMapOf(
                "sellerId" to sellerId,
                "sellerName" to sellerName,
                "mobile" to mobile,
                "place" to place,
                "gstNo" to gstNo,
                "millName" to millName,
                "address" to address,
                "updatedBy" to user,
                "updatedTime" to FieldValue.serverTimestamp()
            )

            val oldDoc = sellerDoc.get().await()
            val oldDataMap = oldDoc.data

            val batch = db.batch()
            batch.set(sellerDoc, sellerData, SetOptions.merge())

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "UPDATE",
                    module = "Sellers",
                    collectionName = "sellers",
                    documentId = sellerId,
                    recordTitle = "Seller: $sellerName",
                    oldDataMap = oldDataMap,
                    newDataMap = sellerData,
                    userOverride = user,
                    roleOverride = "User",
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside updateSellerInFirebase", le)
            }

            batch.commit().await()

            logActionToFirebase(
                context = context,
                action = "UPDATE_SELLER",
                billNo = "",
                firm = firmName,
                user = user,
                role = "User",
                details = "Updated Seller Master : $sellerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating seller in Firestore", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun deleteSellerFromFirebase(
        context: Context,
        sellerId: String,
        sellerName: String,
        user: String,
        firmName: String = ""
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = if (firmName.isNotBlank()) getSanitizedFirmId(firmName) else "F001"
            val sellerDoc = db.collection("firms").document(firmPath)
                .collection("sellers").document(sellerId)

            val oldDoc = sellerDoc.get().await()
            val oldDataMap = oldDoc.data

            val batch = db.batch()
            batch.delete(sellerDoc)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "DELETE",
                    module = "Sellers",
                    collectionName = "sellers",
                    documentId = sellerId,
                    recordTitle = "Seller: $sellerName",
                    oldDataMap = oldDataMap,
                    userOverride = user,
                    roleOverride = "User",
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside deleteSellerFromFirebase", le)
            }

            batch.commit().await()

            logActionToFirebase(
                context = context,
                action = "DELETE_SELLER",
                billNo = "",
                firm = firmName,
                user = user,
                role = "User",
                details = "Deleted Seller Master : $sellerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting seller in Firestore", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun updateBuyerInFirebase(
        context: Context,
        buyerId: String,
        buyerName: String,
        mobile: String,
        place: String,
        gstNo: String,
        firmName: String,
        address: String,
        user: String,
        brokerFirmName: String = ""
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = if (brokerFirmName.isNotBlank()) getSanitizedFirmId(brokerFirmName) else "F001"
            val buyerDoc = db.collection("firms").document(firmPath)
                .collection("buyers").document(buyerId)

            val buyerData = hashMapOf(
                "buyerId" to buyerId,
                "buyerName" to buyerName,
                "mobile" to mobile,
                "place" to place,
                "gstNo" to gstNo,
                "firmName" to firmName,
                "address" to address,
                "updatedBy" to user,
                "updatedTime" to FieldValue.serverTimestamp()
            )

            val oldDoc = buyerDoc.get().await()
            val oldDataMap = oldDoc.data

            val batch = db.batch()
            batch.set(buyerDoc, buyerData, SetOptions.merge())

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "UPDATE",
                    module = "Buyers",
                    collectionName = "buyers",
                    documentId = buyerId,
                    recordTitle = "Buyer: $buyerName",
                    oldDataMap = oldDataMap,
                    newDataMap = buyerData,
                    userOverride = user,
                    roleOverride = "User",
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside updateBuyerInFirebase", le)
            }

            batch.commit().await()

            logActionToFirebase(
                context = context,
                action = "UPDATE_BUYER",
                billNo = "",
                firm = brokerFirmName,
                user = user,
                role = "User",
                details = "Updated Buyer Master : $buyerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating buyer in Firestore", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun deleteBuyerFromFirebase(
        context: Context,
        buyerId: String,
        buyerName: String,
        user: String,
        firmName: String = ""
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = if (firmName.isNotBlank()) getSanitizedFirmId(firmName) else "F001"
            val buyerDoc = db.collection("firms").document(firmPath)
                .collection("buyers").document(buyerId)

            val oldDoc = buyerDoc.get().await()
            val oldDataMap = oldDoc.data

            val batch = db.batch()
            batch.delete(buyerDoc)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "DELETE",
                    module = "Buyers",
                    collectionName = "buyers",
                    documentId = buyerId,
                    recordTitle = "Buyer: $buyerName",
                    oldDataMap = oldDataMap,
                    userOverride = user,
                    roleOverride = "User",
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside deleteBuyerFromFirebase", le)
            }

            batch.commit().await()

            logActionToFirebase(
                context = context,
                action = "DELETE_BUYER",
                billNo = "",
                firm = firmName,
                user = user,
                role = "User",
                details = "Deleted Buyer Master : $buyerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting buyer in Firestore", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun saveBuyerToFirebase(
        context: Context,
        buyerName: String,
        mobile: String,
        place: String,
        gstNo: String,
        firmName: String,
        address: String,
        user: String,
        brokerFirmName: String = ""
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = if (brokerFirmName.isNotBlank()) getSanitizedFirmId(brokerFirmName) else "F001"
            val buyersColl = db.collection("firms").document(firmPath).collection("buyers")

            val snapshot = buyersColl.get().await()
            for (doc in snapshot.documents) {
                val existingName = doc.getString("buyerName")
                if (existingName != null && existingName.equals(buyerName, ignoreCase = true)) {
                    return Pair(false, "Buyer already exists.")
                }
            }

            val newDocRef = buyersColl.document()
            val buyerId = newDocRef.id

            val buyerData = hashMapOf(
                "buyerId" to buyerId,
                "buyerName" to buyerName,
                "mobile" to mobile,
                "place" to place,
                "gstNo" to gstNo,
                "firmName" to firmName,
                "address" to address,
                "createdBy" to user,
                "createdTime" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(newDocRef, buyerData)

            try {
                logEnterpriseAudit(
                    context = context,
                    actionType = "CREATE",
                    module = "Buyers",
                    collectionName = "buyers",
                    documentId = buyerId,
                    recordTitle = "Buyer: $buyerName",
                    newDataMap = buyerData,
                    userOverride = user,
                    roleOverride = "User",
                    batch = batch
                )
            } catch (le: Exception) {
                Log.e(TAG, "Failed to build enterprise audit log inside saveBuyerToFirebase", le)
            }

            batch.commit().await()

            val logDetails = if (firmName.isNotBlank()) firmName else buyerName
            logActionToFirebase(
                context = context,
                action = "CREATE_BUYER",
                billNo = "",
                firm = brokerFirmName,
                user = user,
                role = "User",
                details = "Added Buyer Master : $logDetails"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving buyer to Firestore", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    fun startSync(context: Context, appRepository: AppRepository, activeFirm: com.example.data.Firm? = null) {
        val firm = activeFirm ?: appRepository.activeFirm.value
        try {
            if (!isFirebaseInitialized(context)) {
                FirebaseApp.initializeApp(context)
            }
            val db = FirebaseFirestore.getInstance()

            // Dispose previous listeners
            billsListener?.remove()
            sellersListener?.remove()
            buyersListener?.remove()
            brokersListener?.remove()
            paymentsListener?.remove()
            auditLogsListener?.remove()
            firmsListener?.remove()

            // Start listening to all firms dynamically
            firmsListener = db.collection("firms").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error syncing firms from Firestore", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val list = mutableListOf<com.example.data.Firm>()
                            for (doc in snapshot.documents) {
                                val id = doc.id
                                val name = doc.getString("name") ?: continue
                                list.add(com.example.data.Firm(id = id, name = name))
                            }
                            // Save firms to local database
                            for (firmItem in list) {
                                appRepository.insertFirmDirect(firmItem)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing firms from Firestore", e)
                        }
                    }
                }
            }

            if (firm == null) {
                Log.d(TAG, "No active firm selected. Waiting for firm selection.")
                return
            }

            val firmPath = getSanitizedFirmId(firm.name)
            Log.d(TAG, "Starting sync for firm: ${firm.name} (path: $firmPath)")

            // 1. Listen to Contracts / Bills
            billsListener = db.collection("firms").document(firmPath).collection("contracts")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error syncing bills for ${firm.name}", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val docCount = snapshot.size()
                        Log.d(TAG, "[SYNC LOG] Firestore bills document count: $docCount for firm: ${firm.name}")
                        
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                val list = mutableListOf<ContractBill>()
                                val seenKeys = mutableSetOf<String>()
                                val duplicateDocIds = mutableListOf<String>()
                                
                                for (doc in snapshot.documents) {
                                    val b = parseBillSnapshot(doc, firm.id)
                                    if (b != null) {
                                        val uniqueKey = "${b.firmName}_${b.billNumber}".lowercase()
                                        if (seenKeys.contains(uniqueKey)) {
                                            duplicateDocIds.add(doc.id)
                                        } else {
                                            seenKeys.add(uniqueKey)
                                        }
                                        list.add(b)
                                    }
                                }
                                
                                if (duplicateDocIds.isNotEmpty()) {
                                    Log.w(TAG, "[SYNC LOG] Detected duplicate bill numbers in Firestore snapshot: $duplicateDocIds")
                                }
                                Log.d(TAG, "[SYNC LOG] Active listeners: billsListener is non-null = ${billsListener != null}")
                                appRepository.syncBillsFromFirebase(firm.id, list)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing bills for ${firm.name}", e)
                            }
                        }
                    }
                }

            // 2. Listen to Sellers
            sellersListener = db.collection("firms").document(firmPath).collection("sellers")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error syncing sellers for ${firm.name}", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                val list = mutableListOf<Seller>()
                                for (doc in snapshot.documents) {
                                    val sName = doc.getString("sellerName") ?: doc.getString("name") ?: ""
                                    val sPhone = doc.getString("mobile") ?: doc.getString("phone") ?: ""
                                    val sPlace = doc.getString("place") ?: ""
                                    val sAddress = doc.getString("address") ?: ""
                                    if (sName.isNotBlank()) {
                                        list.add(Seller(name = sName, phone = sPhone, place = sPlace, address = sAddress, firmName = firm.id))
                                    }
                                }
                                appRepository.syncSellersFromFirebase(firm.id, list)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing sellers for ${firm.name}", e)
                            }
                        }
                    }
                }

            // 3. Listen to Buyers
            buyersListener = db.collection("firms").document(firmPath).collection("buyers")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error syncing buyers for ${firm.name}", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                val list = mutableListOf<Buyer>()
                                for (doc in snapshot.documents) {
                                    val bName = doc.getString("buyerName") ?: doc.getString("name") ?: ""
                                    val bPhone = doc.getString("mobile") ?: doc.getString("phone") ?: ""
                                    val bPlace = doc.getString("place") ?: ""
                                    val bAddress = doc.getString("address") ?: ""
                                    if (bName.isNotBlank()) {
                                        list.add(Buyer(name = bName, phone = bPhone, place = bPlace, address = bAddress, firmName = firm.id))
                                    }
                                }
                                appRepository.syncBuyersFromFirebase(firm.id, list)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing buyers for ${firm.name}", e)
                            }
                        }
                    }
                }

            // 4. Listen to Brokers
            brokersListener = db.collection("firms").document(firmPath).collection("brokers")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error syncing brokers for ${firm.name}", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                val list = mutableListOf<Broker>()
                                for (doc in snapshot.documents) {
                                    val brName = doc.getString("brokerName") ?: doc.getString("name") ?: ""
                                    val brPhone = doc.getString("mobile") ?: doc.getString("phone") ?: ""
                                    val brAddress = doc.getString("address") ?: ""
                                    if (brName.isNotBlank()) {
                                        list.add(Broker(name = brName, phone = brPhone, address = brAddress, firmName = firm.id))
                                    }
                                }
                                appRepository.syncBrokersFromFirebase(firm.id, list)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing brokers for ${firm.name}", e)
                            }
                        }
                    }
                }

            // 5. Listen to Payments
            paymentsListener = db.collection("firms").document(firmPath).collection("payments")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error syncing payments for ${firm.name}", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                val list = mutableListOf<Payment>()
                                val seenPaymentIds = mutableSetOf<String>()
                                for (doc in snapshot.documents) {
                                    val p = parsePaymentSnapshot(doc)
                                    if (p != null) {
                                        if (p.paymentId.isBlank() || p.buyerName.isBlank()) {
                                            continue
                                        }
                                        if (seenPaymentIds.contains(p.paymentId)) {
                                            continue
                                        }
                                        seenPaymentIds.add(p.paymentId)
                                        list.add(p)
                                    }
                                }
                                list.sortByDescending { it.timestamp }
                                appRepository.syncPaymentsFromFirebase(list)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing payments for ${firm.name}", e)
                            }
                        }
                    }
                }

            // 6. Listen to Audit Logs (Unnecessary as we only use a real-time Firestore pipeline with caching now)
            // auditLogsListener is no longer required for local Room persistence.

        } catch (e: Exception) {
            Log.e(TAG, "Error starting sync", e)
        }
    }

    private fun parseBillSnapshot(child: com.google.firebase.firestore.DocumentSnapshot, defaultFirmName: String): ContractBill? {
        try {
            val billNoVal = child.get("billNo")
            val billNumber = when (billNoVal) {
                is Number -> billNoVal.toLong().toString()
                is String -> billNoVal
                else -> child.getString("billNumber") ?: child.id.replace("BILL_", "")
            }
            if (billNumber.isBlank()) return null

            val date = child.getString("date") ?: ""
            val sellerName = child.getString("sellerName") ?: ""
            val buyerName = child.getString("buyerName") ?: ""
            val gstNo = child.getString("gstNo") ?: ""
            val particulars = child.getString("particulars") ?: "Rice Brokerage Contract booking"

            val bagsVal = child.get("bagsKg") ?: child.get("bags")
            val bags = when (bagsVal) {
                is Number -> bagsVal.toInt()
                is String -> bagsVal.toIntOrNull() ?: 0
                else -> 0
            }

            val qtlsVal = child.get("qtls") ?: child.get("quintals")
            val quintals = when (qtlsVal) {
                is Number -> qtlsVal.toDouble()
                is String -> qtlsVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val rateVal = child.get("rate")
            val rate = when (rateVal) {
                is Number -> rateVal.toDouble()
                is String -> rateVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val packing = child.getString("packing") ?: "Standard 50kg Bags"
            val transport = child.getString("transport") ?: ""
            val delivery = child.getString("delivery") ?: "Immediate Mandi Delivery"
            val lorryNo = child.getString("lorryNo") ?: ""
            val payment = child.getString("payment") ?: "Within 15 Credit Days"
            val mobileNo = child.getString("mobileNo") ?: ""
            val brand = child.getString("brand") ?: ""

            val lorryFreightVal = child.get("lorryFreight")
            val lorryFreight = when (lorryFreightVal) {
                is Number -> lorryFreightVal.toDouble()
                is String -> lorryFreightVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val creditDaysVal = child.get("creditDays")
            val creditDays = when (creditDaysVal) {
                is Number -> creditDaysVal.toInt()
                is String -> creditDaysVal.toIntOrNull() ?: 0
                else -> 15
            }

            val amountInWords = child.getString("amountInWords") ?: ""
            val sellerSignature = child.getString("sellerSignature") ?: "Verified"

            val billAmountVal = child.get("billAmount")
            val billAmount = when (billAmountVal) {
                is Number -> billAmountVal.toDouble()
                is String -> billAmountVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val outstandingBalanceVal = child.get("outstandingBalance") ?: child.get("balance")
            val balance = when (outstandingBalanceVal) {
                is Number -> outstandingBalanceVal.toDouble()
                is String -> outstandingBalanceVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val place = child.getString("place") ?: "Raichur"
            val bankName = child.getString("bankName") ?: ""
            val remarks = child.getString("remarks") ?: ""

            val ddAmountVal = child.get("ddAmount")
            val ddAmount = when (ddAmountVal) {
                is Number -> ddAmountVal.toDouble()
                is String -> ddAmountVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val cashCuttingVal = child.get("cashCutting")
            val cashCutting = when (cashCuttingVal) {
                is Number -> cashCuttingVal.toDouble()
                is String -> cashCuttingVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val firmName = getSanitizedFirmId(child.getString("firmName") ?: defaultFirmName)

            val totalReceivedVal = child.get("totalReceived")
            val totalReceived = when (totalReceivedVal) {
                is Number -> totalReceivedVal.toDouble()
                is String -> totalReceivedVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val remainingBalanceVal = child.get("remainingBalance")
            val remainingBalance = when (remainingBalanceVal) {
                is Number -> remainingBalanceVal.toDouble()
                is String -> remainingBalanceVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val paymentStatus = child.getString("paymentStatus") ?: "Pending"
            val lastPaymentDate = child.getString("lastPaymentDate") ?: ""

            val sellerAddress = child.getString("sellerAddress") ?: ""
            val buyerAddress = child.getString("buyerAddress") ?: ""

            val itemsListObj = child.get("items")
            val parsedItemsList = mutableListOf<ContractItem>()
            if (itemsListObj is List<*>) {
                for (itemObj in itemsListObj) {
                    if (itemObj is Map<*, *>) {
                        val particularsVal = itemObj["particulars"]?.toString() ?: ""
                        val bagsVal = itemObj["bags"]
                        val bagsNum = when (bagsVal) {
                            is Number -> bagsVal.toInt()
                            is String -> bagsVal.toIntOrNull() ?: 0
                            else -> 0
                        }
                        val qtlsVal = itemObj["qtls"]
                        val qtlsNum = when (qtlsVal) {
                            is Number -> qtlsVal.toDouble()
                            is String -> qtlsVal.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }
                        val rateVal = itemObj["rate"]
                        val rateNum = when (rateVal) {
                            is Number -> rateVal.toDouble()
                            is String -> rateVal.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }
                        val packingVal = itemObj["packing"]?.toString() ?: ""
                        parsedItemsList.add(ContractItem(particularsVal, bagsNum, packingVal, qtlsNum, rateNum))
                    }
                }
            }
            val itemsJson = if (parsedItemsList.isNotEmpty()) {
                serializeItems(parsedItemsList)
            } else {
                child.getString("itemsJson") ?: ""
            }

            val discountPercentVal = child.get("discountPercent")
            val discountPercent = when (discountPercentVal) {
                is Number -> discountPercentVal.toDouble()
                is String -> discountPercentVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val commissionPercentVal = child.get("commissionPercent")
            val commissionPercent = when (commissionPercentVal) {
                is Number -> commissionPercentVal.toDouble()
                is String -> commissionPercentVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val remark1 = child.getString("remark1") ?: ""
            val remark2 = child.getString("remark2") ?: ""
            val brokerName = child.getString("brokerName") ?: ""
            val brokerId = child.getString("brokerId") ?: ""
            val ebVal = child.get("eb")
            val eb = when (ebVal) {
                is String -> ebVal
                is Number -> ebVal.toString()
                is Boolean -> ebVal.toString()
                else -> ""
            }

            val key = child.id
            val digits = key.filter { it.isDigit() }
            val idVal = digits.toIntOrNull() ?: (billNumber.toIntOrNull() ?: 0)

            return ContractBill(
                id = idVal,
                firmName = firmName,
                date = date,
                billNumber = billNumber,
                sellerName = sellerName,
                buyerName = buyerName,
                place = place,
                lorryFreight = lorryFreight,
                rate = rate,
                quintals = quintals,
                billAmount = billAmount,
                ddAmount = ddAmount,
                cashCutting = cashCutting,
                balance = balance,
                bankName = bankName,
                remarks = remarks,
                gstNo = gstNo,
                particulars = particulars,
                bags = bags,
                packing = packing,
                transport = transport,
                delivery = delivery,
                lorryNo = lorryNo,
                payment = payment,
                mobileNo = mobileNo,
                brand = brand,
                amountInWords = amountInWords,
                sellerSignature = sellerSignature,
                creditDays = creditDays,
                totalReceived = totalReceived,
                remainingBalance = remainingBalance,
                paymentStatus = paymentStatus,
                lastPaymentDate = lastPaymentDate,
                sellerAddress = sellerAddress,
                buyerAddress = buyerAddress,
                itemsJson = itemsJson,
                discountPercent = discountPercent,
                commissionPercent = commissionPercent,
                remark1 = remark1,
                remark2 = remark2,
                brokerName = brokerName,
                brokerId = brokerId,
                eb = eb
            )
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error parsing bill snapshot", e)
            return null
        }
    }

    private fun parsePaymentSnapshot(child: com.google.firebase.firestore.DocumentSnapshot): Payment? {
        try {
            val paymentId = child.getString("paymentId") ?: child.id
            val buyerName = child.getString("buyerName") ?: ""

            if (paymentId.isBlank() || buyerName.isBlank()) {
                return null
            }

            val billNo = child.getString("billNo") ?: ""
            val firm = child.getString("firm") ?: child.getString("firmName") ?: ""
            val sellerName = child.getString("sellerName") ?: ""
            val buyerId = child.getString("buyerId") ?: buyerName

            val amtVal = child.get("amount") ?: child.get("paymentAmount") ?: child.get("receivedAmount")
            val paymentAmount = when (amtVal) {
                is Number -> amtVal.toDouble()
                is String -> amtVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val createdAt = child.getString("createdAt") ?: child.getString("paymentDate") ?: child.getString("date") ?: ""
            val paymentMode = child.getString("paymentMode") ?: "Cash"
            val referenceNumber = child.getString("bankName") ?: child.getString("referenceNumber") ?: ""
            val remarks = child.getString("remarks") ?: ""
            val createdBy = child.getString("createdBy") ?: child.getString("receivedBy") ?: ""
            val createdByName = child.getString("createdByName") ?: createdBy

            val tsVal = child.get("timestamp") ?: child.get("receivedTime")
            val timestamp = when (tsVal) {
                is Number -> tsVal.toLong()
                is String -> tsVal.toLongOrNull() ?: 0L
                else -> 0L
            }

            val key = child.id
            val idVal = key.filter { it.isDigit() }.toIntOrNull() ?: 0

            val discountPercent = child.getDouble("discountPercent") ?: 0.0
            val discountAmount = child.getDouble("discountAmount")
                ?: child.getDouble("discount")
                ?: 0.0
            val commissionPercent = child.getDouble("commissionPercent") ?: 0.0
            val commissionAmount = child.getDouble("commissionAmount")
                ?: child.getDouble("commission")
                ?: 0.0

            var remarks1 = child.getString("remarks1") ?: ""
            if (remarks1.isEmpty()) {
                remarks1 = child.get("remarkAmt")?.toString() ?: ""
            }

            var remarks2 = child.getString("remarks2") ?: ""
            if (remarks2.isEmpty()) {
                remarks2 = child.getString("remark") ?: ""
            }

            val alreadyPaidAmount = child.getDouble("alreadyPaidAmount") ?: 0.0
            val pendingAmount = child.getDouble("pendingAmount")
                ?: child.getDouble("balanceAmount")
                ?: 0.0
            val updatedAt = child.getLong("updatedAt") ?: 0L
            val updatedBy = child.getString("updatedBy") ?: ""
            val billAmount = child.getDouble("billAmount") ?: 0.0

            val p = Payment(
                paymentId = paymentId,
                id = idVal,
                billNo = billNo,
                firm = firm,
                sellerName = sellerName,
                buyerId = buyerId,
                buyerName = buyerName,
                paymentAmount = paymentAmount,
                paymentDate = createdAt,
                paymentMode = paymentMode,
                referenceNumber = referenceNumber,
                remarks = remarks,
                receivedBy = createdBy,
                createdBy = createdBy,
                createdByName = createdByName,
                createdAt = createdAt,
                timestamp = timestamp,
                discountPercent = discountPercent,
                discountAmount = discountAmount,
                commissionPercent = commissionPercent,
                commissionAmount = commissionAmount,
                remarks1 = remarks1,
                remarks2 = remarks2,
                alreadyPaidAmount = alreadyPaidAmount,
                pendingAmount = pendingAmount,
                updatedAt = updatedAt,
                updatedBy = updatedBy,
                billAmount = billAmount
            )
            Log.d("FirebaseService", "Payment Loaded: ${p.paymentId}")
            return p
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error parsing payment snapshot", e)
            return null
        }
    }

    suspend fun deleteLedgerFromFirebase(
        context: Context,
        partyId: String,
        partyName: String,
        partyType: String, // "seller", "buyer", "broker"
        firmName: String
    ): LedgerDeleteResult {
        if (!isFirebaseInitialized(context)) {
            return LedgerDeleteResult(partyId, emptyList(), 0, 0, "Firebase not initialized")
        }
        val db = FirebaseFirestore.getInstance()
        val firmPath = getSanitizedFirmId(firmName)
        val collectionPaths = mutableListOf<String>()
        val docsToDelete = mutableListOf<com.google.firebase.firestore.DocumentReference>()
        
        try {
            val contractsCollection = db.collection("firms").document(firmPath).collection("contracts")
            collectionPaths.add(contractsCollection.path)
            
            val contractsSnapshot = when (partyType.lowercase()) {
                "seller" -> contractsCollection.whereEqualTo("sellerName", partyName).get().await()
                "buyer" -> contractsCollection.whereEqualTo("buyerName", partyName).get().await()
                "broker" -> {
                    if (partyId.isNotBlank()) {
                        contractsCollection.whereEqualTo("brokerId", partyId).get().await()
                    } else {
                        contractsCollection.whereEqualTo("brokerName", partyName).get().await()
                    }
                }
                else -> throw IllegalArgumentException("Invalid partyType: $partyType")
            }
            
            for (doc in contractsSnapshot.documents) {
                docsToDelete.add(doc.reference)
            }
            
            if (partyType.lowercase() == "seller" || partyType.lowercase() == "buyer") {
                val paymentsCollection = db.collection("firms").document(firmPath).collection("payments")
                collectionPaths.add(paymentsCollection.path)
                val paymentsSnapshot = when (partyType.lowercase()) {
                    "seller" -> paymentsCollection.whereEqualTo("sellerName", partyName).get().await()
                    "buyer" -> paymentsCollection.whereEqualTo("buyerName", partyName).get().await()
                    else -> throw IllegalArgumentException()
                }
                for (doc in paymentsSnapshot.documents) {
                    docsToDelete.add(doc.reference)
                }
            }
            
            val documentsFound = docsToDelete.size
            if (documentsFound == 0) {
                val res = LedgerDeleteResult(partyId, collectionPaths, 0, 0, null)
                Log.i("LedgerDelete", "Party ID: $partyId")
                Log.i("LedgerDelete", "Collection path: $collectionPaths")
                Log.i("LedgerDelete", "Documents found: 0")
                Log.i("LedgerDelete", "Documents deleted: 0")
                return res
            }
            
            var deletedCount = 0
            val chunks = docsToDelete.chunked(500)
            for (chunk in chunks) {
                val batch = db.batch()
                for (docRef in chunk) {
                    batch.delete(docRef)
                }
                batch.commit().await()
                deletedCount += chunk.size
            }
            
            val res = LedgerDeleteResult(partyId, collectionPaths, documentsFound, deletedCount, null)
            Log.i("LedgerDelete", "Party ID: $partyId")
            Log.i("LedgerDelete", "Collection path: $collectionPaths")
            Log.i("LedgerDelete", "Documents found: $documentsFound")
            Log.i("LedgerDelete", "Documents deleted: $deletedCount")
            return res
        } catch (e: Exception) {
            val errMsg = e.message ?: "Unknown Firestore error"
            Log.e("LedgerDelete", "Firestore error during ledger deletion", e)
            val res = LedgerDeleteResult(partyId, collectionPaths, docsToDelete.size, 0, errMsg)
            Log.i("LedgerDelete", "Party ID: $partyId")
            Log.i("LedgerDelete", "Collection path: $collectionPaths")
            Log.i("LedgerDelete", "Documents found: ${docsToDelete.size}")
            Log.i("LedgerDelete", "Documents deleted: 0")
            Log.e("LedgerDelete", "Firestore errors: $errMsg")
            return res
        }
    }
}

data class LedgerDeleteResult(
    val partyId: String,
    val collectionPaths: List<String>,
    val documentsFound: Int,
    val documentsDeleted: Int,
    val error: String? = null
)
