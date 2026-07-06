package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Firebase Service handling direct interaction with Firebase Realtime Database
 * for authentication and audit log persistence.
 */
object FirebaseService {
    private const val TAG = "FirebaseService"

    private var paymentsListener: com.google.firebase.database.ValueEventListener? = null
    private var lalitBillsListener: com.google.firebase.database.ValueEventListener? = null
    private var hareKrishnaBillsListener: com.google.firebase.database.ValueEventListener? = null
    private var auditLogsListener: com.google.firebase.database.ValueEventListener? = null

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
     * Authenticate a user against the Realtime Database "users" node.
     */
    suspend fun authenticateUser(
        context: Context,
        usernameInput: String,
        passwordInput: String
    ): Result<FirestoreUser> {
        // 1. Initialize Firebase before any login call
        // 2. Verify Firebase.initializeApp() completes successfully
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
                Log.e(TAG, "Stack Trace", ex)
                ex.printStackTrace()
            }
        }

        if (!isInitialized) {
            return Result.failure(Exception("Firebase initialization failed."))
        }

        val enteredUsername = usernameInput.trim()
        val enteredPassword = passwordInput.trim()
        Log.d(TAG, "Entered Username: $enteredUsername")

        return try {
            // 3. Use the correct Realtime Database URL from firebase_options/google-services configuration.
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            Log.d(TAG, "Database URL: $dbUrl")

            val db = FirebaseDatabase.getInstance(dbUrl)
            
            // 4. Read ONLY the "/users" node.
            Log.d(TAG, "Reading /users")
            val snapshot = db.getReference("users").get().await()
            val children = snapshot.children.toList()
            val loadedCount = children.size
            
            // Log loaded count
            Log.d(TAG, "Users Loaded Count: $loadedCount")

            var matchedUser: FirestoreUser? = null
            var matchedChild: com.google.firebase.database.DataSnapshot? = null

            for (child in children) {
                val u = child.child("username").getValue(String::class.java) ?: ""
                val p = child.child("password").getValue(String::class.java) ?: ""
                
                val activeVal = child.child("active").value
                val isActive = when (activeVal) {
                    is Boolean -> activeVal
                    is String -> activeVal.toBoolean()
                    is Number -> activeVal.toInt() != 0
                    else -> true
                }

                // Log checking user
                Log.d(TAG, "Current User Checking: $u")

                // 10. Compare enteredUsername.trim() == username (ignoring case for safety, matching exact trim value)
                if (u.trim().equals(enteredUsername, ignoreCase = true)) {
                    Log.d(TAG, "Username Matched: $u")
                    if (p == enteredPassword) {
                        Log.d(TAG, "Password Matched")
                        if (!isActive) {
                            Log.d(TAG, "Login Failed: User $u is inactive")
                            return Result.failure(Exception("Your account is disabled."))
                        }

                        // Save the matched child snapshot for separate loading of firmAccess
                        matchedChild = child
                        break
                    }
                }
            }

            if (matchedChild != null) {
                val u = matchedChild.child("username").getValue(String::class.java) ?: ""
                val p = matchedChild.child("password").getValue(String::class.java) ?: ""
                val r = matchedChild.child("role").getValue(String::class.java) ?: "Viewer"
                
                val activeVal = matchedChild.child("active").value
                val isActive = when (activeVal) {
                    is Boolean -> activeVal
                    is String -> activeVal.toBoolean()
                    is Number -> activeVal.toInt() != 0
                    else -> true
                }

                // Only after successful login, load firmAccess separately.
                Log.d(TAG, "Loading firmAccess separately")
                var fa = ""
                try {
                    val firmAccessSnapshot = matchedChild.child("firmAccess")
                    val value = firmAccessSnapshot.value
                    if (value != null) {
                        if (value is List<*>) {
                            // Read it as List<String>
                            val t = object : com.google.firebase.database.GenericTypeIndicator<List<String>>() {}
                            val list = firmAccessSnapshot.getValue(t)
                            if (list != null) {
                                fa = list.filterNotNull().joinToString(", ")
                            } else {
                                fa = value.filterNotNull().map { it.toString() }.joinToString(", ")
                            }
                        } else if (value is String) {
                            fa = value
                        } else {
                            fa = value.toString()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing firmAccess separately, fallback to empty/string", e)
                    try {
                        fa = matchedChild.child("firmAccess").getValue(String::class.java) ?: ""
                    } catch (ex: Exception) {
                        Log.e(TAG, "Fallback parsing also failed", ex)
                    }
                }

                matchedUser = FirestoreUser(
                    username = u,
                    password = p,
                    fullName = u,
                    role = r,
                    firmAccess = fa,
                    isActive = isActive,
                    lastLogin = Date(),
                    createdAt = Date()
                )
            }

            if (matchedUser != null) {
                Log.d(TAG, "Login Success")
                Result.success(matchedUser)
            } else {
                Log.d(TAG, "Login Failed: Invalid credentials for $enteredUsername")
                Result.failure(Exception("Invalid Username or Password"))
            }
        } catch (e: Exception) {
            Log.d(TAG, "Firebase Exception: ${e.message}")
            Log.e(TAG, "Stack Trace", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Store login audit records in the Realtime Database "audit_logs" node.
     */
    suspend fun saveLoginAuditLog(context: Context, username: String) {
        if (!isFirebaseInitialized(context)) return

        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
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

            // Save to /audit_logs with a unique push key
            db.getReference("audit_logs").push().setValue(auditData).await()
            Log.d(TAG, "Audit log saved successfully to RTDB for user: $username")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log to Realtime Database", e)
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
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val logsRef = db.getReference("logs")

            // Find the next LOG_xxxxx sequence number
            val snapshot = logsRef.get().await()
            var maxSeq = 0
            for (child in snapshot.children) {
                val key = child.key ?: ""
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
                "time" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "device" to "Android",
                "details" to details
            )

            logsRef.child(nextKey).setValue(logData).await()
            Log.d(TAG, "Log written to Firebase successfully: $nextKey")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to Firebase", e)
        }
    }

    suspend fun logAuditLogToFirebase(log: AuditLog) {
        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val auditLogRef = db.getReference("audit_logs")
            val key = auditLogRef.push().key ?: java.util.UUID.randomUUID().toString()

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
            auditLogRef.child(key).setValue(logData).await()
            Log.d(TAG, "Audit log written to Firebase successfully: $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log to Firebase", e)
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
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val firmPath = bill.firmName.replace(" ", "")
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
                "createdBy" to user,
                "createdRole" to role,
                "createdTime" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "lastUpdatedBy" to user,
                "lastUpdatedTime" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "status" to "Active"
            )

            db.getReference("firms/$firmPath/bills/$billKey").setValue(billData).await()

            // Automatically check/insert masterData autocomplete fields
            val masterRef = db.getReference("masterData")
            if (bill.sellerName.isNotBlank()) masterRef.child("sellers").child(bill.sellerName).setValue(true)
            if (bill.buyerName.isNotBlank()) masterRef.child("buyers").child(bill.buyerName).setValue(true)
            if (bill.transport.isNotBlank()) {
                masterRef.child("transports").child(bill.transport).setValue(true)
                masterRef.child("transport").child(bill.transport).setValue(true)
            }
            if (bill.brand.isNotBlank()) {
                masterRef.child("brands").child(bill.brand).setValue(true)
            }
            if (bill.mobileNo.isNotBlank()) {
                masterRef.child("mobileNumbers").child(bill.mobileNo).setValue(true)
                masterRef.child("mobile_numbers").child(bill.mobileNo).setValue(true)
            }
            if (bill.gstNo.isNotBlank()) {
                masterRef.child("gstNumbers").child(bill.gstNo).setValue(true)
                masterRef.child("gst_numbers").child(bill.gstNo).setValue(true)
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
            Log.e(TAG, "Error saving bill to Firebase", e)
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
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val firmPath = newBill.firmName.replace(" ", "")
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
                "createdBy" to (oldBill?.sellerName ?: user), // reuse some placeholder if not saved
                "createdRole" to role,
                "createdTime" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "lastUpdatedBy" to user,
                "lastUpdatedTime" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "status" to "Active"
            )

            db.getReference("firms/$firmPath/bills/$billKey").setValue(billData).await()

            // Autocomplete update for masterData
            val masterRef = db.getReference("masterData")
            if (newBill.sellerName.isNotBlank()) masterRef.child("sellers").child(newBill.sellerName).setValue(true)
            if (newBill.buyerName.isNotBlank()) masterRef.child("buyers").child(newBill.buyerName).setValue(true)
            if (newBill.transport.isNotBlank()) {
                masterRef.child("transports").child(newBill.transport).setValue(true)
                masterRef.child("transport").child(newBill.transport).setValue(true)
            }
            if (newBill.brand.isNotBlank()) {
                masterRef.child("brands").child(newBill.brand).setValue(true)
            }
            if (newBill.mobileNo.isNotBlank()) {
                masterRef.child("mobileNumbers").child(newBill.mobileNo).setValue(true)
                masterRef.child("mobile_numbers").child(newBill.mobileNo).setValue(true)
            }
            if (newBill.gstNo.isNotBlank()) {
                masterRef.child("gstNumbers").child(newBill.gstNo).setValue(true)
                masterRef.child("gst_numbers").child(newBill.gstNo).setValue(true)
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
            Log.e(TAG, "Error updating bill in Firebase", e)
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
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val firmPath = bill.firmName.replace(" ", "")
            val billKey = getBillNodeKey(bill.billNumber)

            db.getReference("firms/$firmPath/bills/$billKey").removeValue().await()

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
            Log.e(TAG, "Error deleting bill in Firebase", e)
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
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val paymentsRef = db.getReference("payments")

            val paymentId = payment.paymentId.ifBlank {
                paymentsRef.push().key ?: java.util.UUID.randomUUID().toString()
            }

            val timestamp = if (payment.timestamp > 0L) payment.timestamp else System.currentTimeMillis()
            val createdAt = payment.createdAt.ifBlank {
                payment.paymentDate.ifBlank {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
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
                // Standardized payment fields
                "billAmount" to payment.billAmount,
                "receivedAmount" to payment.paymentAmount,
                "discount" to payment.discountAmount,
                "commission" to payment.commissionAmount,
                "remarkAmt" to (payment.remarks1.toDoubleOrNull() ?: 0.0),
                "remark" to payment.remarks2,
                "balanceAmount" to payment.pendingAmount
            )

            paymentsRef.child(paymentId).setValue(paymentData).await()
            Log.d("FirebaseService", "Payment Saved: $paymentId")

            // Also ensure buyer is in masterData
            if (payment.buyerName.isNotBlank()) {
                db.getReference("masterData/buyers").child(payment.buyerName).setValue(true)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving payment to Firebase", e)
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
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            
            val updates = hashMapOf<String, Any?>()
            
            val firmPath = bill.firmName.replace(" ", "")
            val billKey = getBillNodeKey(bill.billNumber)
            val billPath = "firms/$firmPath/bills/$billKey"
            val paymentPath = "payments/${newPayment.paymentId}"
            
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
                "createdTime" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "lastUpdatedBy" to user,
                "lastUpdatedTime" to com.google.firebase.database.ServerValue.TIMESTAMP,
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
                // New required Firebase fields
                "billAmount" to bill.billAmount,
                "receivedAmount" to newPayment.paymentAmount,
                "discount" to newPayment.discountAmount,
                "commission" to newPayment.commissionAmount,
                "remarkAmt" to (newPayment.remarks1.toDoubleOrNull() ?: 0.0),
                "remark" to newPayment.remarks2,
                "balanceAmount" to newPayment.pendingAmount
            )
            
            updates[billPath] = billData
            updates[paymentPath] = paymentData
            updates["masterData/buyers/${newPayment.buyerName}"] = true
            
            val auditLogId = db.getReference("audit_logs").push().key ?: java.util.UUID.randomUUID().toString()
            val auditData = hashMapOf(
                "userName" to user,
                "date" to java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
                "time" to java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                "screen" to "Party Ledger Dialog",
                "action" to "EDIT_PAYMENT_TRANSACTION",
                "oldValue" to "Received: ${bill.totalReceived - newPayment.paymentAmount}, Pending: ${bill.remainingBalance + newPayment.paymentAmount}",
                "newValue" to "Received: ${bill.totalReceived}, Pending: ${bill.remainingBalance}",
                "device" to "Android SDK " + android.os.Build.VERSION.SDK_INT
            )
            updates["audit_logs/$auditLogId"] = auditData
            
            db.getReference().updateChildren(updates).await()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Transaction multi-path update failed", e)
            return false
        }
    }

    suspend fun deletePaymentFromFirebase(
        context: Context,
        payment: Payment
    ): Boolean {
        if (!isFirebaseInitialized(context)) return false
        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val paymentsRef = db.getReference("payments")
            val paymentKey = payment.paymentId.ifBlank { String.format("PAYMENT_%06d", payment.id) }
            
            paymentsRef.child(paymentKey).removeValue().await()
            Log.d("FirebaseService", "Payment Deleted: $paymentKey")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting payment in Firebase", e)
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
        user: String
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val sellersRef = db.getReference("masterData/sellers")

            // Duplicate Check: Check if a seller with the same name already exists (case-insensitive)
            val snapshot = sellersRef.get().await()
            for (child in snapshot.children) {
                val existingName = child.child("sellerName").getValue(String::class.java)
                if (existingName != null && existingName.equals(sellerName, ignoreCase = true)) {
                    return Pair(false, "Seller already exists.")
                }
            }

            val newRef = sellersRef.push()
            val sellerId = newRef.key ?: ""

            val sellerData = hashMapOf(
                "sellerId" to sellerId,
                "sellerName" to sellerName,
                "mobile" to mobile,
                "place" to place,
                "gstNo" to gstNo,
                "millName" to millName,
                "address" to address,
                "createdBy" to user,
                "createdTime" to com.google.firebase.database.ServerValue.TIMESTAMP
            )

            newRef.setValue(sellerData).await()

            // 6. LOG SYSTEM
            val logDetails = if (millName.isNotBlank()) millName else sellerName
            logActionToFirebase(
                context = context,
                action = "CREATE_SELLER",
                billNo = "",
                firm = "",
                user = user,
                role = "User",
                details = "Added Seller Master : $logDetails"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving seller to Firebase", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun saveBrokerToFirebase(
        context: Context,
        brokerName: String,
        mobile: String,
        address: String,
        user: String
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val brokersRef = db.getReference("masterData/brokers")

            val snapshot = brokersRef.get().await()
            for (child in snapshot.children) {
                val existingName = child.child("brokerName").getValue(String::class.java)
                if (existingName != null && existingName.equals(brokerName, ignoreCase = true)) {
                    return Pair(false, "Broker already exists.")
                }
            }

            val newRef = brokersRef.push()
            val brokerId = newRef.key ?: ""

            val brokerData = hashMapOf(
                "brokerId" to brokerId,
                "brokerName" to brokerName,
                "mobile" to mobile,
                "address" to address,
                "totalBillings" to 0,
                "totalQtls" to 0.0,
                "createdBy" to user,
                "createdTime" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "updatedBy" to user,
                "updatedTime" to com.google.firebase.database.ServerValue.TIMESTAMP
            )

            newRef.setValue(brokerData).await()

            logActionToFirebase(
                context = context,
                action = "CREATE_BROKER",
                billNo = "",
                firm = "",
                user = user,
                role = "User",
                details = "Added Broker Master : $brokerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving broker to Firebase", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun updateBrokerInFirebase(
        context: Context,
        brokerId: String,
        brokerName: String,
        mobile: String,
        address: String,
        user: String
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val brokerRef = db.getReference("masterData/brokers").child(brokerId)

            val updates = hashMapOf<String, Any>(
                "brokerName" to brokerName,
                "mobile" to mobile,
                "address" to address,
                "updatedBy" to user,
                "updatedTime" to com.google.firebase.database.ServerValue.TIMESTAMP
            )

            brokerRef.updateChildren(updates).await()

            logActionToFirebase(
                context = context,
                action = "UPDATE_BROKER",
                billNo = "",
                firm = "",
                user = user,
                role = "User",
                details = "Updated Broker Master : $brokerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating broker in Firebase", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun deleteBrokerFromFirebase(
        context: Context,
        brokerId: String,
        brokerName: String,
        user: String
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val brokerRef = db.getReference("masterData/brokers").child(brokerId)

            brokerRef.removeValue().await()

            logActionToFirebase(
                context = context,
                action = "DELETE_BROKER",
                billNo = "",
                firm = "",
                user = user,
                role = "User",
                details = "Deleted Broker Master : $brokerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting broker from Firebase", e)
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
        user: String
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val sellerRef = db.getReference("masterData/sellers").child(sellerId)

            val sellerData = hashMapOf(
                "sellerId" to sellerId,
                "sellerName" to sellerName,
                "mobile" to mobile,
                "place" to place,
                "gstNo" to gstNo,
                "millName" to millName,
                "address" to address,
                "updatedBy" to user,
                "updatedTime" to com.google.firebase.database.ServerValue.TIMESTAMP
            )

            sellerRef.setValue(sellerData).await()

            logActionToFirebase(
                context = context,
                action = "UPDATE_SELLER",
                billNo = "",
                firm = "",
                user = user,
                role = "User",
                details = "Updated Seller Master : $sellerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating seller in Firebase", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun deleteSellerFromFirebase(
        context: Context,
        sellerId: String,
        sellerName: String,
        user: String
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val sellerRef = db.getReference("masterData/sellers").child(sellerId)

            sellerRef.removeValue().await()

            logActionToFirebase(
                context = context,
                action = "DELETE_SELLER",
                billNo = "",
                firm = "",
                user = user,
                role = "User",
                details = "Deleted Seller Master : $sellerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting seller in Firebase", e)
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
        user: String
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val buyerRef = db.getReference("masterData/buyers").child(buyerId)

            val buyerData = hashMapOf(
                "buyerId" to buyerId,
                "buyerName" to buyerName,
                "mobile" to mobile,
                "place" to place,
                "gstNo" to gstNo,
                "firmName" to firmName,
                "address" to address,
                "updatedBy" to user,
                "updatedTime" to com.google.firebase.database.ServerValue.TIMESTAMP
            )

            buyerRef.setValue(buyerData).await()

            logActionToFirebase(
                context = context,
                action = "UPDATE_BUYER",
                billNo = "",
                firm = "",
                user = user,
                role = "User",
                details = "Updated Buyer Master : $buyerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating buyer in Firebase", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    suspend fun deleteBuyerFromFirebase(
        context: Context,
        buyerId: String,
        buyerName: String,
        user: String
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val buyerRef = db.getReference("masterData/buyers").child(buyerId)

            buyerRef.removeValue().await()

            logActionToFirebase(
                context = context,
                action = "DELETE_BUYER",
                billNo = "",
                firm = "",
                user = user,
                role = "User",
                details = "Deleted Buyer Master : $buyerName"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting buyer in Firebase", e)
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
        user: String
    ): Pair<Boolean, String?> {
        if (!isFirebaseInitialized(context)) return Pair(false, "Firebase not initialized")
        try {
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val buyersRef = db.getReference("masterData/buyers")

            // Duplicate Check: Check if a buyer with the same name already exists (case-insensitive)
            val snapshot = buyersRef.get().await()
            for (child in snapshot.children) {
                val existingName = child.child("buyerName").getValue(String::class.java)
                if (existingName != null && existingName.equals(buyerName, ignoreCase = true)) {
                    return Pair(false, "Buyer already exists.")
                }
            }

            val newRef = buyersRef.push()
            val buyerId = newRef.key ?: ""

            val buyerData = hashMapOf(
                "buyerId" to buyerId,
                "buyerName" to buyerName,
                "mobile" to mobile,
                "place" to place,
                "gstNo" to gstNo,
                "firmName" to firmName,
                "address" to address,
                "createdBy" to user,
                "createdTime" to com.google.firebase.database.ServerValue.TIMESTAMP
            )

            newRef.setValue(buyerData).await()

            // 6. LOG SYSTEM
            val logDetails = if (firmName.isNotBlank()) firmName else buyerName
            logActionToFirebase(
                context = context,
                action = "CREATE_BUYER",
                billNo = "",
                firm = "",
                user = user,
                role = "User",
                details = "Added Buyer Master : $logDetails"
            )

            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving buyer to Firebase", e)
            return Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    fun startSync(context: Context, appRepository: AppRepository) {
        try {
            if (!isFirebaseInitialized(context)) {
                FirebaseApp.initializeApp(context)
            }
            val options = FirebaseApp.getInstance().options
            var dbUrl = options.databaseUrl
            if (dbUrl.isNullOrEmpty()) {
                dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            }
            val db = FirebaseDatabase.getInstance(dbUrl)

            // Dispose previous listeners to ensure only one listener is active
            lalitBillsListener?.let {
                db.getReference("firms/LalitRiceBroker/bills").removeEventListener(it)
            }
            hareKrishnaBillsListener?.let {
                db.getReference("firms/HareKrishnaRiceBroker/bills").removeEventListener(it)
            }
            paymentsListener?.let {
                db.getReference("payments").removeEventListener(it)
            }
            auditLogsListener?.let {
                db.getReference("audit_logs").removeEventListener(it)
            }

            // Listen to LalitRiceBroker bills
            lalitBillsListener = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val list = mutableListOf<ContractBill>()
                            for (child in snapshot.children) {
                                val b = parseBillSnapshot(child, "Lalit Rice Broker")
                                if (b != null) list.add(b)
                            }
                            appRepository.syncBillsFromFirebase("Lalit Rice Broker", list)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing LalitRiceBroker bills", e)
                        }
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            }
            db.getReference("firms/LalitRiceBroker/bills").addValueEventListener(lalitBillsListener!!)

            // Listen to HareKrishnaRiceBroker bills
            hareKrishnaBillsListener = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val list = mutableListOf<ContractBill>()
                            for (child in snapshot.children) {
                                val b = parseBillSnapshot(child, "Hare Krishna Rice Broker")
                                if (b != null) list.add(b)
                            }
                            appRepository.syncBillsFromFirebase("Hare Krishna Rice Broker", list)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing HareKrishnaRiceBroker bills", e)
                        }
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            }
            db.getReference("firms/HareKrishnaRiceBroker/bills").addValueEventListener(hareKrishnaBillsListener!!)

            // Listen to payments
            paymentsListener = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val list = mutableListOf<Payment>()
                            val seenPaymentIds = mutableSetOf<String>()
                            for (child in snapshot.children) {
                                val p = parsePaymentSnapshot(child)
                                if (p != null) {
                                    if (p.paymentId.isBlank() || p.buyerName.isBlank()) {
                                        Log.d("FirebaseService", "Duplicate Ignored: Empty paymentId or buyerName")
                                        continue
                                    }
                                    if (seenPaymentIds.contains(p.paymentId)) {
                                        Log.d("FirebaseService", "Duplicate Ignored: ${p.paymentId}")
                                        continue
                                    }
                                    seenPaymentIds.add(p.paymentId)
                                    list.add(p)
                                }
                            }
                            // Sort payments by timestamp descending (latest first)
                            list.sortByDescending { it.timestamp }
                            appRepository.syncPaymentsFromFirebase(list)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing payments", e)
                        }
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            }
            db.getReference("payments").addValueEventListener(paymentsListener!!)

            // Listen to audit logs
            auditLogsListener = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val list = mutableListOf<AuditLog>()
                            for (child in snapshot.children) {
                                val userName = child.child("userName").getValue(String::class.java) ?: ""
                                val userRole = child.child("userRole").getValue(String::class.java) ?: ""
                                val date = child.child("date").getValue(String::class.java) ?: ""
                                val time = child.child("time").getValue(String::class.java) ?: ""
                                val screen = child.child("screen").getValue(String::class.java) ?: ""
                                val action = child.child("action").getValue(String::class.java) ?: ""
                                val firmName = child.child("firmName").getValue(String::class.java) ?: ""
                                val oldValue = child.child("oldValue").getValue(String::class.java) ?: ""
                                val newValue = child.child("newValue").getValue(String::class.java) ?: ""
                                val device = child.child("device").getValue(String::class.java) ?: ""
                                val ipSessionId = child.child("ipSessionId").getValue(String::class.java) ?: ""
                                val billNo = child.child("billNo").getValue(String::class.java) ?: ""
                                val partyName = child.child("partyName").getValue(String::class.java) ?: ""

                                val log = AuditLog(
                                    userName = userName,
                                    userRole = userRole,
                                    date = date,
                                    time = time,
                                    screen = screen,
                                    action = action,
                                    firmName = firmName,
                                    oldValue = oldValue,
                                    newValue = newValue,
                                    device = device,
                                    ipSessionId = ipSessionId,
                                    billNo = billNo,
                                    partyName = partyName
                                )
                                list.add(log)
                            }
                            appRepository.syncLogsFromFirebase(list)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing audit logs", e)
                        }
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            }
            db.getReference("audit_logs").addValueEventListener(auditLogsListener!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting sync", e)
        }
    }

    private fun parseBillSnapshot(child: com.google.firebase.database.DataSnapshot, defaultFirmName: String): ContractBill? {
        try {
            val billNoVal = child.child("billNo").value
            val billNumber = when (billNoVal) {
                is Number -> billNoVal.toLong().toString()
                is String -> billNoVal
                else -> child.child("billNumber").getValue(String::class.java) ?: child.key?.replace("BILL_", "") ?: ""
            }
            if (billNumber.isBlank()) return null

            val date = child.child("date").getValue(String::class.java) ?: ""
            val sellerName = child.child("sellerName").getValue(String::class.java) ?: ""
            val buyerName = child.child("buyerName").getValue(String::class.java) ?: ""
            val gstNo = child.child("gstNo").getValue(String::class.java) ?: ""
            val particulars = child.child("particulars").getValue(String::class.java) ?: "Rice Brokerage Contract booking"
            
            val bagsVal = child.child("bagsKg").value ?: child.child("bags").value
            val bags = when (bagsVal) {
                is Number -> bagsVal.toInt()
                is String -> bagsVal.toIntOrNull() ?: 0
                else -> 0
            }

            val qtlsVal = child.child("qtls").value ?: child.child("quintals").value
            val quintals = when (qtlsVal) {
                is Number -> qtlsVal.toDouble()
                is String -> qtlsVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val rateVal = child.child("rate").value
            val rate = when (rateVal) {
                is Number -> rateVal.toDouble()
                is String -> rateVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val packing = child.child("packing").getValue(String::class.java) ?: "Standard 50kg Bags"
            val transport = child.child("transport").getValue(String::class.java) ?: ""
            val delivery = child.child("delivery").getValue(String::class.java) ?: "Immediate Mandi Delivery"
            val lorryNo = child.child("lorryNo").getValue(String::class.java) ?: ""
            val payment = child.child("payment").getValue(String::class.java) ?: "Within 15 Credit Days"
            val mobileNo = child.child("mobileNo").getValue(String::class.java) ?: ""
            val brand = child.child("brand").getValue(String::class.java) ?: ""
            
            val lorryFreightVal = child.child("lorryFreight").value
            val lorryFreight = when (lorryFreightVal) {
                is Number -> lorryFreightVal.toDouble()
                is String -> lorryFreightVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val creditDaysVal = child.child("creditDays").value
            val creditDays = when (creditDaysVal) {
                is Number -> creditDaysVal.toInt()
                is String -> creditDaysVal.toIntOrNull() ?: 0
                else -> 15
            }

            val amountInWords = child.child("amountInWords").getValue(String::class.java) ?: ""
            val sellerSignature = child.child("sellerSignature").getValue(String::class.java) ?: "Verified"
            
            val billAmountVal = child.child("billAmount").value
            val billAmount = when (billAmountVal) {
                is Number -> billAmountVal.toDouble()
                is String -> billAmountVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val outstandingBalanceVal = child.child("outstandingBalance").value ?: child.child("balance").value
            val balance = when (outstandingBalanceVal) {
                is Number -> outstandingBalanceVal.toDouble()
                is String -> outstandingBalanceVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val place = child.child("place").getValue(String::class.java) ?: "Raichur"
            val bankName = child.child("bankName").getValue(String::class.java) ?: ""
            val remarks = child.child("remarks").getValue(String::class.java) ?: ""
            
            val ddAmountVal = child.child("ddAmount").value
            val ddAmount = when (ddAmountVal) {
                is Number -> ddAmountVal.toDouble()
                is String -> ddAmountVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val cashCuttingVal = child.child("cashCutting").value
            val cashCutting = when (cashCuttingVal) {
                is Number -> cashCuttingVal.toDouble()
                is String -> cashCuttingVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val firmName = child.child("firmName").getValue(String::class.java) ?: defaultFirmName

            val totalReceivedVal = child.child("totalReceived").value
            val totalReceived = when (totalReceivedVal) {
                is Number -> totalReceivedVal.toDouble()
                is String -> totalReceivedVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val remainingBalanceVal = child.child("remainingBalance").value
            val remainingBalance = when (remainingBalanceVal) {
                is Number -> remainingBalanceVal.toDouble()
                is String -> remainingBalanceVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val paymentStatus = child.child("paymentStatus").getValue(String::class.java) ?: "Pending"
            val lastPaymentDate = child.child("lastPaymentDate").getValue(String::class.java) ?: ""

            val sellerAddress = child.child("sellerAddress").getValue(String::class.java) ?: ""
            val buyerAddress = child.child("buyerAddress").getValue(String::class.java) ?: ""

            val itemsSnap = child.child("items")
            val parsedItemsList = mutableListOf<ContractItem>()
            if (itemsSnap.exists()) {
                for (itemChild in itemsSnap.children) {
                    val particularsVal = itemChild.child("particulars").getValue(String::class.java) ?: ""
                    val bagsVal = itemChild.child("bags").value
                    val bagsNum = when (bagsVal) {
                        is Number -> bagsVal.toInt()
                        is String -> bagsVal.toIntOrNull() ?: 0
                        else -> 0
                    }
                    val qtlsVal = itemChild.child("qtls").value
                    val qtlsNum = when (qtlsVal) {
                        is Number -> qtlsVal.toDouble()
                        is String -> qtlsVal.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val rateVal = itemChild.child("rate").value
                    val rateNum = when (rateVal) {
                        is Number -> rateVal.toDouble()
                        is String -> rateVal.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val packingVal = itemChild.child("packing").getValue(String::class.java) ?: ""
                    parsedItemsList.add(ContractItem(particularsVal, bagsNum, packingVal, qtlsNum, rateNum))
                }
            }
            val itemsJson = if (parsedItemsList.isNotEmpty()) {
                serializeItems(parsedItemsList)
            } else {
                child.child("itemsJson").getValue(String::class.java) ?: ""
            }

            val discountPercentVal = child.child("discountPercent").value
            val discountPercent = when (discountPercentVal) {
                is Number -> discountPercentVal.toDouble()
                is String -> discountPercentVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val commissionPercentVal = child.child("commissionPercent").value
            val commissionPercent = when (commissionPercentVal) {
                is Number -> commissionPercentVal.toDouble()
                is String -> commissionPercentVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val remark1 = child.child("remark1").getValue(String::class.java) ?: ""
            val remark2 = child.child("remark2").getValue(String::class.java) ?: ""
            val brokerName = child.child("brokerName").getValue(String::class.java) ?: ""
            val brokerId = child.child("brokerId").getValue(String::class.java) ?: ""

            // Parse key as fallback ID to maintain uniqueness
            val key = child.key ?: ""
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
                brokerId = brokerId
            )
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error parsing bill snapshot", e)
            return null
        }
    }

    private fun parsePaymentSnapshot(child: com.google.firebase.database.DataSnapshot): Payment? {
        try {
            val paymentId = child.child("paymentId").getValue(String::class.java) ?: child.key ?: ""
            val buyerName = child.child("buyerName").getValue(String::class.java) ?: ""

            // Requirement 4: If buyerName or paymentId is empty, ignore that record.
            if (paymentId.isBlank() || buyerName.isBlank()) {
                Log.d("FirebaseService", "Duplicate Ignored: Empty paymentId or buyerName")
                return null
            }

            val billNo = child.child("billNo").getValue(String::class.java) ?: ""
            val firm = child.child("firm").getValue(String::class.java) ?: child.child("firmName").getValue(String::class.java) ?: ""
            val sellerName = child.child("sellerName").getValue(String::class.java) ?: ""
            val buyerId = child.child("buyerId").getValue(String::class.java) ?: buyerName
            
            val amtVal = child.child("amount").value ?: child.child("paymentAmount").value ?: child.child("receivedAmount").value
            val paymentAmount = when (amtVal) {
                is Number -> amtVal.toDouble()
                is String -> amtVal.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val createdAt = child.child("createdAt").getValue(String::class.java) ?: child.child("paymentDate").getValue(String::class.java) ?: child.child("date").getValue(String::class.java) ?: ""
            val paymentMode = child.child("paymentMode").getValue(String::class.java) ?: "Cash"
            val referenceNumber = child.child("bankName").getValue(String::class.java) ?: child.child("referenceNumber").getValue(String::class.java) ?: ""
            val remarks = child.child("remarks").getValue(String::class.java) ?: ""
            val createdBy = child.child("createdBy").getValue(String::class.java) ?: child.child("receivedBy").getValue(String::class.java) ?: ""
            val createdByName = child.child("createdByName").getValue(String::class.java) ?: createdBy

            val tsVal = child.child("timestamp").value ?: child.child("receivedTime").value
            val timestamp = when (tsVal) {
                is Number -> tsVal.toLong()
                is String -> tsVal.toLongOrNull() ?: 0L
                else -> 0L
            }

            val key = child.key ?: ""
            val idVal = key.filter { it.isDigit() }.toIntOrNull() ?: 0

            val discountPercent = child.child("discountPercent").getValue(Double::class.java) ?: 0.0
            val discountAmount = child.child("discountAmount").getValue(Double::class.java) 
                ?: child.child("discount").getValue(Double::class.java) 
                ?: 0.0
            val commissionPercent = child.child("commissionPercent").getValue(Double::class.java) ?: 0.0
            val commissionAmount = child.child("commissionAmount").getValue(Double::class.java) 
                ?: child.child("commission").getValue(Double::class.java) 
                ?: 0.0
            
            var remarks1 = child.child("remarks1").getValue(String::class.java) ?: ""
            if (remarks1.isEmpty()) {
                val remarkAmtVal = child.child("remarkAmt").value
                remarks1 = remarkAmtVal?.toString() ?: ""
            }
            
            var remarks2 = child.child("remarks2").getValue(String::class.java) ?: ""
            if (remarks2.isEmpty()) {
                remarks2 = child.child("remark").getValue(String::class.java) ?: ""
            }

            val alreadyPaidAmount = child.child("alreadyPaidAmount").getValue(Double::class.java) ?: 0.0
            val pendingAmount = child.child("pendingAmount").getValue(Double::class.java) 
                ?: child.child("balanceAmount").getValue(Double::class.java) 
                ?: 0.0
            val updatedAt = child.child("updatedAt").getValue(Long::class.java) ?: 0L
            val updatedBy = child.child("updatedBy").getValue(String::class.java) ?: ""
            val billAmount = child.child("billAmount").getValue(Double::class.java) ?: 0.0

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
