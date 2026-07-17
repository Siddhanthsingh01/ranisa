package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AppRepository(private val appDao: AppDao) {

    private val _activeUser = MutableStateFlow<User?>(null)
    val activeUser: StateFlow<User?> = _activeUser.asStateFlow()

    private val _activeFirm = MutableStateFlow<Firm?>(null)
    val activeFirm: StateFlow<Firm?> = _activeFirm.asStateFlow()

    val users: Flow<List<User>> = appDao.getAllUsersFlow()
    val firms: Flow<List<Firm>> = appDao.getAllFirmsFlow()
    val sellers: Flow<List<Seller>> = appDao.getAllSellersFlow()
    val buyers: Flow<List<Buyer>> = appDao.getAllBuyersFlow()
    val brokers: Flow<List<Broker>> = appDao.getAllBrokersFlow()
    val contractBills: Flow<List<ContractBill>> = appDao.getAllContractBills()
    val payments: Flow<List<Payment>> = appDao.getAllPayments()
    val logs: Flow<List<AuditLog>> = kotlinx.coroutines.flow.flowOf(emptyList())

    init {
        CoroutineScope(Dispatchers.IO).launch {
            // Prepopulate Users if empty
            val existingUsers = appDao.getAllUsers()
            if (existingUsers.isEmpty()) {
                val defaultUsers = listOf(
                    User(username = "Sidhant (Admin)", role = "Admin"),
                    User(username = "Lalit (Broker)", role = "Broker"),
                    User(username = "Krishna (Accountant)", role = "Accountant"),
                    User(username = "Guest (Viewer)", role = "Viewer")
                )
                defaultUsers.forEach { appDao.insertUser(it) }
            }

            // Prepopulate Firms if empty
            val existingFirms = appDao.getAllFirms()
            if (existingFirms.isEmpty()) {
                val defaultFirms = listOf(
                    Firm(id = "F001", name = "Lalit Rice Broker"),
                    Firm(id = "F002", name = "Hare Krishna Rice Broker")
                )
                defaultFirms.forEach { appDao.insertFirm(it) }
            }

            // Set default firm (user remains null for login flow)
            val finalFirms = appDao.getAllFirms()
            if (finalFirms.isNotEmpty()) {
                _activeFirm.value = finalFirms.first()
            }
        }
    }

    fun selectUser(user: User) {
        val oldUser = _activeUser.value?.username ?: "None"
        _activeUser.value = user
        CoroutineScope(Dispatchers.IO).launch {
            logAction("Auth", "Login", oldUser, user.username)
        }
    }

    /**
     * Clears the active user state for logout.
     */
    fun logoutUser() {
        val oldUser = _activeUser.value?.username ?: "None"
        _activeUser.value = null
        CoroutineScope(Dispatchers.IO).launch {
            logAction("Auth", "Logout", oldUser, "")
        }
    }

    fun selectFirm(firm: Firm) {
        val oldFirm = _activeFirm.value?.name ?: "None"
        _activeFirm.value = firm
        CoroutineScope(Dispatchers.IO).launch {
            logAction("Home", "Firm Switch", oldFirm, firm.name)
        }
    }

    // Helper to get active username or system
    private fun getActiveUsername(): String {
        return _activeUser.value?.username ?: "Guest (Viewer)"
    }

    private fun getActiveUserRole(): String {
        return _activeUser.value?.role ?: "Viewer"
    }

    // Logging helper
    suspend fun logAction(
        screen: String,
        action: String,
        oldValue: String = "",
        newValue: String = "",
        billNo: String = "",
        partyName: String = ""
    ) {
        val activeFrm = _activeFirm.value?.name ?: "Lalit Rice Broker"
        try {
            FirebaseService.logEnterpriseAudit(
                context = com.google.firebase.firestore.FirebaseFirestore.getInstance().app.applicationContext,
                actionType = action,
                module = when {
                    screen.contains("Bill", ignoreCase = true) || screen.contains("Contract", ignoreCase = true) -> "Bills"
                    screen.contains("Payment", ignoreCase = true) -> "Payments"
                    screen.contains("Seller", ignoreCase = true) -> "Sellers"
                    screen.contains("Buyer", ignoreCase = true) -> "Buyers"
                    screen.contains("Broker", ignoreCase = true) -> "Brokers"
                    else -> "System"
                },
                collectionName = "logs",
                documentId = billNo,
                recordTitle = partyName.ifBlank { "Action in $screen" },
                userOverride = getActiveUsername(),
                roleOverride = getActiveUserRole(),
                descriptionOverride = "$action on $screen. Old: $oldValue, New: $newValue",
                billNoOverride = billNo,
                partyNameOverride = partyName,
                firmNameOverride = activeFrm,
                oldValueOverride = oldValue,
                newValueOverride = newValue
            )
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Firebase logging failed", e)
        }
    }

    // Contract Bills logic with autocomplete insertion
    suspend fun insertContractBill(bill: ContractBill) {
        // Automatically check/insert seller in Master
        val sellersList = appDao.getAllSellers()
        if (sellersList.none { it.name.equals(bill.sellerName, ignoreCase = true) }) {
            appDao.insertSeller(Seller(name = bill.sellerName, place = bill.place))
        }

        // Automatically check/insert buyer in Master
        val buyersList = appDao.getAllBuyers()
        if (buyersList.none { it.name.equals(bill.buyerName, ignoreCase = true) }) {
            appDao.insertBuyer(Buyer(name = bill.buyerName, place = bill.place))
        }

        appDao.insertBill(bill)
        
        val createDetails = "Bill No: ${bill.billNumber}\n" +
                "Party: ${bill.sellerName} / ${bill.buyerName}\n" +
                "Amount: ₹${bill.billAmount}"
        logAction(
            screen = "Contract Form",
            action = "CREATE BILL",
            oldValue = "",
            newValue = createDetails,
            billNo = bill.billNumber,
            partyName = bill.sellerName
        )
    }

    suspend fun updateContractBill(bill: ContractBill) {
        val oldBill = appDao.getBillById(bill.id)
        appDao.updateBill(bill)
        
        val changes = StringBuilder()
        if (oldBill != null) {
            if (oldBill.rate != bill.rate) changes.append("Rate:\nOld: ₹${oldBill.rate}\nNew: ₹${bill.rate}\n\n")
            if (oldBill.quintals != bill.quintals) changes.append("Quantity:\nOld: ${oldBill.quintals} Qtls\nNew: ${bill.quintals} Qtls\n\n")
            if (oldBill.balance != bill.balance) changes.append("Balance:\nOld: ₹${oldBill.balance}\nNew: ₹${bill.balance}\n\n")
            if (oldBill.billAmount != bill.billAmount) changes.append("Bill Amount:\nOld: ₹${oldBill.billAmount}\nNew: ₹${bill.billAmount}\n\n")
            if (oldBill.sellerName != bill.sellerName) changes.append("Seller:\nOld: ${oldBill.sellerName}\nNew: ${bill.sellerName}\n\n")
            if (oldBill.buyerName != bill.buyerName) changes.append("Buyer:\nOld: ${oldBill.buyerName}\nNew: ${bill.buyerName}\n\n")
            if (oldBill.place != bill.place) changes.append("Place:\nOld: ${oldBill.place}\nNew: ${bill.place}\n\n")
            if (oldBill.lorryNo != bill.lorryNo) changes.append("Lorry No:\nOld: ${oldBill.lorryNo}\nNew: ${bill.lorryNo}\n\n")
            if (oldBill.paymentStatus != bill.paymentStatus) changes.append("Payment Status:\nOld: ${oldBill.paymentStatus}\nNew: ${bill.paymentStatus}\n\n")
        }
        
        val diffText = changes.toString().trim()
        val finalDiffText = if (diffText.isEmpty()) "No major financial or party fields changed." else diffText

        logAction(
            screen = "Seller Ledger",
            action = "UPDATE BILL",
            oldValue = "Bill No: ${bill.billNumber}\n" + (oldBill?.toString() ?: ""),
            newValue = "Bill No: ${bill.billNumber}\n$finalDiffText",
            billNo = bill.billNumber,
            partyName = bill.sellerName
        )
    }

    suspend fun deleteContractBill(bill: ContractBill): List<Payment> {
        val associatedPayments = appDao.getPaymentsByBill(bill.billNumber, bill.firmName)
        appDao.deletePaymentsByBill(bill.billNumber, bill.firmName)
        appDao.deleteBill(bill)
        
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentDate = sdfDate.format(Date())
        val currentTime = sdfTime.format(Date())
        
        val deleteDetails = "Bill No: ${bill.billNumber}\n" +
                "Party: ${bill.sellerName} / ${bill.buyerName}\n" +
                "Amount: ₹${bill.billAmount}\n" +
                "Reason: Deleted by User\n" +
                "Deleted At: $currentDate $currentTime"
        logAction(
            screen = "Contract Form",
            action = "DELETE BILL",
            oldValue = deleteDetails,
            newValue = "",
            billNo = bill.billNumber,
            partyName = bill.sellerName
        )
        return associatedPayments
    }

    // Payments logic
    suspend fun insertPayment(payment: Payment) {
        // Automatically check/insert buyer in Master
        val buyersList = appDao.getAllBuyers()
        if (buyersList.none { it.name.equals(payment.buyerName, ignoreCase = true) }) {
            appDao.insertBuyer(Buyer(name = payment.buyerName))
        }

        appDao.insertPayment(payment)
        
        val bill = appDao.getBillByNumberAndFirm(payment.billNo, payment.firm)
        val prevBalance = bill?.remainingBalance ?: 0.0
        val newBalance = prevBalance - payment.paymentAmount
        
        logAction(
            screen = "Payment List",
            action = "PAYMENT ADDED",
            oldValue = "Payment of ₹${payment.paymentAmount} to Bill No: ${payment.billNo}",
            newValue = "Bill No: ${payment.billNo}\nPayment Amount: ₹${payment.paymentAmount}\nPrevious Balance: ₹$prevBalance\nNew Balance: ₹$newBalance",
            billNo = payment.billNo,
            partyName = payment.buyerName
        )
    }

    suspend fun updatePayment(payment: Payment) {
        appDao.updatePayment(payment)
        
        val bill = appDao.getBillByNumberAndFirm(payment.billNo, payment.firm)
        val prevBalance = bill?.remainingBalance ?: 0.0
        val newBalance = prevBalance - payment.paymentAmount
        
        logAction(
            screen = "Payment List",
            action = "PAYMENT UPDATED",
            oldValue = "Payment of ₹${payment.paymentAmount} to Bill No: ${payment.billNo}",
            newValue = "Bill No: ${payment.billNo}\nPayment Amount: ₹${payment.paymentAmount}\nPrevious Balance: ₹$prevBalance\nNew Balance: ₹$newBalance",
            billNo = payment.billNo,
            partyName = payment.buyerName
        )
    }

    suspend fun deletePayment(payment: Payment) {
        appDao.deletePayment(payment)
        
        val bill = appDao.getBillByNumberAndFirm(payment.billNo, payment.firm)
        val prevBalance = bill?.remainingBalance ?: 0.0
        val newBalance = prevBalance + payment.paymentAmount
        
        logAction(
            screen = "Payment List",
            action = "PAYMENT DELETED",
            oldValue = "Bill No: ${payment.billNo}\nPayment Amount: ₹${payment.paymentAmount}\nPrevious Balance: ₹$prevBalance\nNew Balance: ₹$newBalance",
            newValue = "",
            billNo = payment.billNo,
            partyName = payment.buyerName
        )
    }

    // Master list helpers
    suspend fun addSeller(seller: Seller) {
        appDao.insertSeller(seller)
        logAction("Seller Master", "Create", "", seller.name)
    }

    suspend fun updateSeller(seller: Seller) {
        appDao.updateSeller(seller)
        logAction("Seller Master", "Update", "", seller.name)
    }

    suspend fun deleteSeller(seller: Seller) {
        appDao.deleteSeller(seller)
        logAction("Seller Master", "Delete", seller.name, "")
    }

    suspend fun addBuyer(buyer: Buyer) {
        appDao.insertBuyer(buyer)
        logAction("Buyer Master", "Create", "", buyer.name)
    }

    suspend fun updateBuyer(buyer: Buyer) {
        appDao.updateBuyer(buyer)
        logAction("Buyer Master", "Update", "", buyer.name)
    }

    suspend fun deleteBuyer(buyer: Buyer) {
        appDao.deleteBuyer(buyer)
        logAction("Buyer Master", "Delete", buyer.name, "")
    }

    suspend fun addBroker(broker: Broker) {
        appDao.insertBroker(broker)
        logAction("Broker Master", "Create", "", broker.name)
    }

    suspend fun updateBroker(broker: Broker) {
        appDao.updateBroker(broker)
        logAction("Broker Master", "Update", "", broker.name)
    }

    suspend fun deleteBroker(broker: Broker) {
        appDao.deleteBroker(broker)
        logAction("Broker Master", "Delete", broker.name, "")
    }

    // Search queries
    fun searchSellers(query: String) = appDao.searchSellers("%$query%")
    fun searchBuyers(query: String) = appDao.searchBuyers("%$query%")

    fun getBillsBySeller(sellerName: String) = appDao.getBillsBySeller(sellerName)
    fun getBillsByBuyer(buyerName: String) = appDao.getBillsByBuyer(buyerName)
    fun getPaymentsByBuyer(buyerName: String) = appDao.getPaymentsByBuyer(buyerName)

    /**
     * Authenticates a user locally by setting their state, and inserts them 
     * into Room database if they don't already exist.
     */
    suspend fun loginUserLocally(username: String, role: String) {
        val existingUsers = appDao.getAllUsers()
        var localUser = existingUsers.find { it.username.equals(username, ignoreCase = true) }
        if (localUser == null) {
            localUser = User(username = username, role = role)
            appDao.insertUser(localUser)
            logAction("Auth", "User Creation", "", "Created User: $username, Role: $role")
        }
        selectUser(localUser)
    }

    suspend fun syncBillsFromFirebase(firmName: String, billsList: List<ContractBill>) {
        val sanitizedId = FirebaseService.getSanitizedFirmId(firmName)
        val targetName = if (sanitizedId == "F001") "Lalit Rice Broker" else "Hare Krishna Rice Broker"
        
        // Fully clear local Room cache for this firm's data by both sanitized ID and name representation
        appDao.clearBillsByFirm(sanitizedId)
        appDao.clearBillsByFirm(targetName)
        
        // Strictly prevent duplicates by firm ID and bill number
        val uniqueBills = billsList.distinctBy { 
            val sanFirm = FirebaseService.getSanitizedFirmId(it.firmName)
            "${sanFirm}_${it.billNumber}".lowercase()
        }
        
        android.util.Log.d("AppRepository", "[SYNC LOG] Inserting ${uniqueBills.size} unique bills after deduplication. Raw list size was: ${billsList.size}")
        
        for (bill in uniqueBills) {
            val normalizedBill = bill.copy(
                firmName = sanitizedId
            )
            appDao.insertBill(normalizedBill)
            // Also insert seller and buyer if not exists
            val sellersList = appDao.getAllSellers()
            if (sellersList.none { it.name.equals(normalizedBill.sellerName, ignoreCase = true) }) {
                appDao.insertSeller(Seller(name = normalizedBill.sellerName, place = normalizedBill.place))
            }
            val buyersList = appDao.getAllBuyers()
            if (buyersList.none { it.name.equals(normalizedBill.buyerName, ignoreCase = true) }) {
                appDao.insertBuyer(Buyer(name = normalizedBill.buyerName, place = normalizedBill.place))
            }
        }
    }

    suspend fun syncPaymentsFromFirebase(paymentsList: List<Payment>) {
        appDao.clearAllPayments()
        val seenPaymentIds = mutableSetOf<String>()
        for (p in paymentsList) {
            if (p.paymentId.isBlank() || p.buyerName.isBlank()) {
                android.util.Log.d("AppRepository", "Duplicate Ignored: Empty paymentId or buyerName")
                continue
            }
            if (seenPaymentIds.contains(p.paymentId)) {
                android.util.Log.d("AppRepository", "Duplicate Ignored: ${p.paymentId}")
                continue
            }
            seenPaymentIds.add(p.paymentId)
            appDao.insertPayment(p)
            val buyersList = appDao.getAllBuyers()
            if (buyersList.none { it.name.equals(p.buyerName, ignoreCase = true) }) {
                appDao.insertBuyer(Buyer(name = p.buyerName))
            }
        }
    }

    suspend fun syncLogsFromFirebase(logsList: List<AuditLog>) {
        appDao.clearAllLogs()
        for (log in logsList) {
            appDao.insertLog(log)
        }
    }

    suspend fun syncSellersFromFirebase(firmName: String, sellersList: List<Seller>) {
        appDao.clearSellersByFirm(firmName)
        for (seller in sellersList) {
            appDao.insertSeller(seller)
        }
    }

    suspend fun syncBuyersFromFirebase(firmName: String, buyersList: List<Buyer>) {
        appDao.clearBuyersByFirm(firmName)
        for (buyer in buyersList) {
            appDao.insertBuyer(buyer)
        }
    }

    suspend fun syncBrokersFromFirebase(firmName: String, brokersList: List<Broker>) {
        appDao.clearBrokersByFirm(firmName)
        for (broker in brokersList) {
            appDao.insertBroker(broker)
        }
    }

    suspend fun insertFirmDirect(firm: Firm) {
        val existing = appDao.getAllFirms()
        val foundById = existing.find { it.id == firm.id }
        if (foundById != null) {
            if (foundById.name != firm.name) {
                appDao.insertFirm(firm)
            }
        } else {
            val foundByName = existing.find { it.name.equals(firm.name, ignoreCase = true) }
            if (foundByName == null) {
                appDao.insertFirm(firm)
            }
        }
    }
}
