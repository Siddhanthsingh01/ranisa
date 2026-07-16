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
     * Store login audit records in the Firestore "audit_logs" collection.
     */
    suspend fun saveLoginAuditLog(context: Context, username: String) {
        if (!isFirebaseInitialized(context)) return

        try {
            val db = FirebaseFirestore.getInstance()
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val now = Date()

            val auditData = hashMapOf(
                "userName" to username,
                "action" to "Login",
                "date" to sdfDate.format(now),
                "time" to sdfTime.format(now),
                "device" to Build.MODEL
            )

            db.collection("audit_logs").add(auditData).await()
            Log.d(TAG, "Audit log saved successfully to Firestore for user: $username")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log to Firestore", e)
        }
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

    suspend fun logActionToFirebase(
        context: Context,
        action: String,
        billNo: String,
        firm: String,
        user: String,
        role: String,
        details: String
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = if (firm.isNotBlank()) getSanitizedFirmId(firm) else "F001"
            val logsColl = db.collection("firms").document(firmPath).collection("logs")

            // Find the next LOG_xxxxx sequence number
            val snapshot = logsColl.get().await()
            var maxSeq = 0
            for (doc in snapshot.documents) {
                val key = doc.id
                if (key.startsWith("LOG_")) {
                    val numStr = key.substring(4)
                    val num = numStr.toIntOrNull()
                    if (num != null && num > maxSeq) {
                        maxSeq = num
                    }
                }
            }
            val nextSeq = maxSeq + 1
            val nextKey = String.format("LOG_%06d", nextSeq)

            val logData = hashMapOf(
                "action" to action,
                "billNo" to (billNo.toIntOrNull() ?: billNo),
                "firm" to firm,
                "user" to user,
                "role" to role,
                "time" to FieldValue.serverTimestamp(),
                "device" to "Android",
                "details" to details,
                // Add AuditLog compatibility fields
                "userName" to user,
                "userRole" to role,
                "date" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                "timeStr" to SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                "screen" to "System Log",
                "firmName" to firm,
                "oldValue" to details,
                "newValue" to ""
            )

            logsColl.document(nextKey).set(logData).await()
            Log.d(TAG, "Log written to Firestore successfully: $nextKey")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to Firestore", e)
        }
    }

    suspend fun logAuditLogToFirebase(log: AuditLog) {
        try {
            val db = FirebaseFirestore.getInstance()
            val firmPath = if (log.firmName.isNotBlank()) getSanitizedFirmId(log.firmName) else "F001"
            val logsColl = db.collection("firms").document(firmPath).collection("logs")

            val logData = hashMapOf(
                "userName" to log.userName,
                "userRole" to log.userRole,
                "date" to log.date,
                "time" to log.time,
                "screen" to log.screen,
                "action" to log.action,
                "firmName" to log.firmName,
                "oldValue" to log.oldValue,
                "newValue" to log.newValue,
                "device" to log.device,
                "ipSessionId" to log.ipSessionId,
                "billNo" to log.billNo,
                "partyName" to log.partyName
            )
            logsColl.add(logData).await()
            Log.d(TAG, "Audit log written to Firestore successfully under firm: $firmPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log to Firestore", e)
        }
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

            db.collection("firms").document(firmPath)
                .collection("contracts").document(billKey)
                .set(billData).await()

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

            db.collection("firms").document(firmPath)
                .collection("contracts").document(billKey)
                .set(billData).await()

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

            db.collection("firms").document(firmPath)
                .collection("contracts").document(billKey)
                .delete().await()

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

            paymentsColl.document(paymentId).set(paymentData).await()
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

            db.collection("firms").document(firmPath)
                .collection("payments").document(paymentKey)
                .delete().await()

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

            newDocRef.set(sellerData).await()

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

            newDocRef.set(brokerData).await()

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

            brokerDoc.update(updates).await()

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

            brokerDoc.delete().await()

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

            sellerDoc.set(sellerData, SetOptions.merge()).await()

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

            sellerDoc.delete().await()

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

            buyerDoc.set(buyerData, SetOptions.merge()).await()

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

            buyerDoc.delete().await()

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

            newDocRef.set(buyerData).await()

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

            // 6. Listen to Audit Logs
            auditLogsListener = db.collection("firms").document(firmPath).collection("logs")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error syncing logs for ${firm.name}", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                val list = mutableListOf<AuditLog>()
                                for (doc in snapshot.documents) {
                                    val userName = doc.getString("userName") ?: doc.getString("user") ?: ""
                                    val userRole = doc.getString("userRole") ?: doc.getString("role") ?: ""
                                    val date = doc.getString("date") ?: ""
                                    val time = doc.getString("time") ?: doc.getString("timeStr") ?: ""
                                    val screen = doc.getString("screen") ?: "System Log"
                                    val action = doc.getString("action") ?: ""
                                    val fName = doc.getString("firmName") ?: doc.getString("firm") ?: ""
                                    val oldValue = doc.getString("oldValue") ?: doc.getString("details") ?: ""
                                    val newValue = doc.getString("newValue") ?: ""
                                    val device = doc.getString("device") ?: ""
                                    val ipSessionId = doc.getString("ipSessionId") ?: ""
                                    
                                    val billNoVal = doc.get("billNo")
                                    val billNo = when (billNoVal) {
                                        is Number -> billNoVal.toLong().toString()
                                        is String -> billNoVal
                                        else -> ""
                                    }
                                    val partyName = doc.getString("partyName") ?: ""

                                    val logItem = AuditLog(
                                        userName = userName,
                                        userRole = userRole,
                                        date = date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) },
                                        time = time.ifBlank { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) },
                                        screen = screen,
                                        action = action,
                                        firmName = fName,
                                        oldValue = oldValue,
                                        newValue = newValue,
                                        device = device,
                                        ipSessionId = ipSessionId,
                                        billNo = billNo,
                                        partyName = partyName
                                    )
                                    list.add(logItem)
                                }
                                appRepository.syncLogsFromFirebase(list)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing logs", e)
                            }
                        }
                    }
                }

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
}
