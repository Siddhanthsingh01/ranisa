package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RanisaViewModel(application: Application, private val repository: AppRepository) : AndroidViewModel(application) {

    // Reactive states from repository
    val activeUser = repository.activeUser
    val activeFirm = repository.activeFirm
    val users = repository.users.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val firms = repository.firms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sellers = repository.sellers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val buyers = repository.buyers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val brokers = repository.brokers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val logs = repository.logs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // State for Search & Filters
    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery = _globalSearchQuery.asStateFlow()

    private val _sellerLedgerSearch = MutableStateFlow("")
    val sellerLedgerSearch = _sellerLedgerSearch.asStateFlow()

    private val _buyerLedgerSearch = MutableStateFlow("")
    val buyerLedgerSearch = _buyerLedgerSearch.asStateFlow()

    private val _brokerLedgerSearch = MutableStateFlow("")
    val brokerLedgerSearch = _brokerLedgerSearch.asStateFlow()

    // Real-time Firebase masterData StateFlows for autocompletes
    val rtdbSellers = MutableStateFlow<List<String>>(emptyList())
    val rtdbBuyers = MutableStateFlow<List<String>>(emptyList())
    val rtdbFullSellers = MutableStateFlow<List<FirebaseSeller>>(emptyList())
    val rtdbFullBuyers = MutableStateFlow<List<FirebaseBuyer>>(emptyList())
    val rtdbFullBrokers = MutableStateFlow<List<FirebaseBroker>>(emptyList())
    val rtdbTransports = MutableStateFlow<List<String>>(emptyList())
    val rtdbBrands = MutableStateFlow<List<String>>(emptyList())
    val rtdbMobiles = MutableStateFlow<List<String>>(emptyList())
    val rtdbGsts = MutableStateFlow<List<String>>(emptyList())
    val rtdbBrokers = MutableStateFlow<List<String>>(emptyList())

    init {
        FirebaseService.startSync(application, repository)
        startListeningToMasterData()
    }

    private fun startListeningToMasterData() {
        try {
            val dbUrl = "https://ranisa-78679-default-rtdb.asia-southeast1.firebasedatabase.app"
            val db = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
            db.getReference("masterData").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val sellersList = mutableListOf<String>()
                    val fullSellersList = mutableListOf<FirebaseSeller>()
                    val buyersList = mutableListOf<String>()
                    val fullBuyersList = mutableListOf<FirebaseBuyer>()
                    val transportsList = mutableListOf<String>()
                    val brandsList = mutableListOf<String>()
                    val mobilesList = mutableListOf<String>()
                    val gstsList = mutableListOf<String>()

                    for (child in snapshot.child("sellers").children) {
                        val sellerName = child.child("sellerName").getValue(String::class.java)
                        if (sellerName != null) {
                            sellersList.add(sellerName)
                            fullSellersList.add(
                                FirebaseSeller(
                                    sellerId = child.child("sellerId").getValue(String::class.java) ?: child.key ?: "",
                                    sellerName = sellerName,
                                    mobile = child.child("mobile").getValue(String::class.java) ?: "",
                                    place = child.child("place").getValue(String::class.java) ?: "",
                                    gstNo = child.child("gstNo").getValue(String::class.java) ?: "",
                                    millName = child.child("millName").getValue(String::class.java) ?: "",
                                    address = child.child("address").getValue(String::class.java) ?: ""
                                )
                            )
                        } else {
                            child.key?.let { name ->
                                sellersList.add(name)
                                fullSellersList.add(
                                    FirebaseSeller(
                                        sellerId = name,
                                        sellerName = name
                                    )
                                )
                            }
                        }
                    }
                    for (child in snapshot.child("seller").children) {
                        child.key?.let { name ->
                            if (!sellersList.contains(name)) {
                                sellersList.add(name)
                                fullSellersList.add(FirebaseSeller(sellerId = name, sellerName = name))
                            }
                        }
                    }

                    for (child in snapshot.child("buyers").children) {
                        val buyerName = child.child("buyerName").getValue(String::class.java)
                        if (buyerName != null) {
                            buyersList.add(buyerName)
                            fullBuyersList.add(
                                FirebaseBuyer(
                                    buyerId = child.child("buyerId").getValue(String::class.java) ?: child.key ?: "",
                                    buyerName = buyerName,
                                    mobile = child.child("mobile").getValue(String::class.java) ?: "",
                                    place = child.child("place").getValue(String::class.java) ?: "",
                                    gstNo = child.child("gstNo").getValue(String::class.java) ?: "",
                                    firmName = child.child("firmName").getValue(String::class.java) ?: "",
                                    address = child.child("address").getValue(String::class.java) ?: ""
                                )
                            )
                        } else {
                            child.key?.let { name ->
                                buyersList.add(name)
                                fullBuyersList.add(
                                    FirebaseBuyer(
                                        buyerId = name,
                                        buyerName = name
                                    )
                                )
                            }
                        }
                    }
                    for (child in snapshot.child("buyer").children) {
                        child.key?.let { name ->
                            if (!buyersList.contains(name)) {
                                buyersList.add(name)
                                fullBuyersList.add(FirebaseBuyer(buyerId = name, buyerName = name))
                            }
                        }
                    }

                    for (child in snapshot.child("transports").children) {
                        child.key?.let { transportsList.add(it) }
                    }
                    for (child in snapshot.child("transport").children) {
                        child.key?.let { if (!transportsList.contains(it)) transportsList.add(it) }
                    }

                    for (child in snapshot.child("brands").children) {
                        child.key?.let { brandsList.add(it) }
                    }
                    for (child in snapshot.child("brand").children) {
                        child.key?.let { if (!brandsList.contains(it)) brandsList.add(it) }
                    }

                    for (child in snapshot.child("mobileNumbers").children) {
                        child.key?.let { mobilesList.add(it) }
                    }
                    for (child in snapshot.child("mobile_numbers").children) {
                        child.key?.let { if (!mobilesList.contains(it)) mobilesList.add(it) }
                    }

                    for (child in snapshot.child("gstNumbers").children) {
                        child.key?.let { gstsList.add(it) }
                    }
                    for (child in snapshot.child("gst_numbers").children) {
                        child.key?.let { if (!gstsList.contains(it)) gstsList.add(it) }
                    }

                    val brokersList = mutableListOf<String>()
                    val fullBrokersList = mutableListOf<FirebaseBroker>()
                    for (child in snapshot.child("brokers").children) {
                        val name = child.child("brokerName").getValue(String::class.java) ?: child.key
                        if (name != null) {
                            brokersList.add(name)
                            val totalBillings = child.child("totalBillings").getValue(Int::class.java) ?: 0
                            val totalQtlsVal = child.child("totalQtls").getValue(Double::class.java) ?: 0.0
                            val createdBy = child.child("createdBy").getValue(String::class.java) ?: ""
                            val updatedBy = child.child("updatedBy").getValue(String::class.java) ?: ""
                            val createdTimeVal = child.child("createdTime").getValue(Long::class.java) ?: 0L
                            val updatedTimeVal = child.child("updatedTime").getValue(Long::class.java) ?: 0L
                            
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            val createdDateTime = if (createdTimeVal > 0) sdf.format(java.util.Date(createdTimeVal)) else ""
                            val updatedDateTime = if (updatedTimeVal > 0) sdf.format(java.util.Date(updatedTimeVal)) else ""

                            fullBrokersList.add(
                                FirebaseBroker(
                                    brokerId = child.child("brokerId").getValue(String::class.java) ?: child.key ?: "",
                                    brokerName = name,
                                    mobile = child.child("mobile").getValue(String::class.java) ?: "",
                                    address = child.child("address").getValue(String::class.java) ?: "",
                                    totalBillings = totalBillings,
                                    totalQtls = totalQtlsVal,
                                    createdDateTime = createdDateTime,
                                    updatedDateTime = updatedDateTime,
                                    createdBy = createdBy,
                                    updatedBy = updatedBy
                                )
                            )
                        }
                    }

                    rtdbSellers.value = sellersList.distinct().sorted()
                    rtdbBuyers.value = buyersList.distinct().sorted()
                    rtdbFullSellers.value = fullSellersList.distinctBy { it.sellerName.trim().lowercase() }
                    rtdbFullBuyers.value = fullBuyersList.distinctBy { it.buyerName.trim().lowercase() }
                    rtdbFullBrokers.value = fullBrokersList.distinctBy { it.brokerName.trim().lowercase() }
                    rtdbTransports.value = transportsList.sorted()
                    rtdbBrands.value = brandsList.sorted()
                    rtdbMobiles.value = mobilesList.sorted()
                    rtdbGsts.value = gstsList.sorted()
                    rtdbBrokers.value = brokersList.distinct().sorted()
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        } catch (e: Exception) {
            android.util.Log.e("RanisaViewModel", "Error starting masterData listener", e)
        }
    }

    // Base flows
    val allBills = repository.contractBills.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allPayments = repository.payments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined/derived flows for Ledgers and outstanding calculations
    val filteredBills = combine(allBills, _globalSearchQuery) { bills, query ->
        if (query.isBlank()) bills else {
            bills.filter {
                it.billNumber.contains(query, ignoreCase = true) ||
                it.buyerName.contains(query, ignoreCase = true) ||
                it.sellerName.contains(query, ignoreCase = true) ||
                it.date.contains(query, ignoreCase = true) ||
                it.remarks.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredPayments = combine(allPayments, _globalSearchQuery) { payments, query ->
        if (query.isBlank()) payments else {
            payments.filter {
                it.buyerName.contains(query, ignoreCase = true) ||
                it.date.contains(query, ignoreCase = true) ||
                it.paymentMode.contains(query, ignoreCase = true) ||
                it.remarks.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Actions
    fun updateGlobalSearch(query: String) {
        _globalSearchQuery.value = query
    }

    fun updateSellerLedgerSearch(query: String) {
        _sellerLedgerSearch.value = query
    }

    fun updateBuyerLedgerSearch(query: String) {
        _buyerLedgerSearch.value = query
    }

    fun updateBrokerLedgerSearch(query: String) {
        _brokerLedgerSearch.value = query
    }

    fun selectUser(user: User) {
        repository.selectUser(user)
    }

    fun selectFirm(firm: Firm) {
        repository.selectFirm(firm)
    }

    // Bills CRUD
    fun autoSyncSellerAndBuyer(bill: ContractBill) {
        viewModelScope.launch {
            val user = activeUser.value?.username ?: "Admin"
            
            // 1. SELLER AUTO SYNC
            if (bill.sellerName.isNotBlank()) {
                val existingSeller = rtdbFullSellers.value.find { 
                    it.sellerName.equals(bill.sellerName, ignoreCase = true) && 
                    it.mobile == bill.mobileNo 
                }
                if (existingSeller == null) {
                    FirebaseService.saveSellerToFirebase(
                        getApplication(),
                        sellerName = bill.sellerName,
                        mobile = bill.mobileNo,
                        place = bill.place,
                        gstNo = bill.gstNo,
                        millName = bill.sellerName,
                        address = bill.sellerAddress,
                        user = user
                    )
                } else {
                    FirebaseService.updateSellerInFirebase(
                        getApplication(),
                        sellerId = existingSeller.sellerId,
                        sellerName = bill.sellerName,
                        mobile = bill.mobileNo,
                        place = bill.place,
                        gstNo = bill.gstNo,
                        millName = existingSeller.millName.ifBlank { bill.sellerName },
                        address = bill.sellerAddress.ifBlank { existingSeller.address },
                        user = user
                    )
                }
            }

            // 2. BUYER AUTO SYNC
            if (bill.buyerName.isNotBlank()) {
                val existingBuyer = rtdbFullBuyers.value.find { 
                    it.buyerName.equals(bill.buyerName, ignoreCase = true) && 
                    it.mobile == bill.mobileNo 
                }
                if (existingBuyer == null) {
                    FirebaseService.saveBuyerToFirebase(
                        getApplication(),
                        buyerName = bill.buyerName,
                        mobile = bill.mobileNo,
                        place = bill.place,
                        gstNo = bill.gstNo,
                        firmName = bill.buyerName,
                        address = bill.buyerAddress,
                        user = user
                    )
                } else {
                    FirebaseService.updateBuyerInFirebase(
                        getApplication(),
                        buyerId = existingBuyer.buyerId,
                        buyerName = bill.buyerName,
                        mobile = bill.mobileNo,
                        place = bill.place,
                        gstNo = bill.gstNo,
                        firmName = existingBuyer.firmName.ifBlank { bill.buyerName },
                        address = bill.buyerAddress.ifBlank { existingBuyer.address },
                        user = user
                    )
                }
            }
        }
    }

    fun saveBill(bill: ContractBill) {
        viewModelScope.launch {
            repository.insertContractBill(bill)
            FirebaseService.saveBillToFirebase(
                getApplication(),
                bill,
                activeUser.value?.username ?: "Admin",
                activeUser.value?.role ?: "Admin"
            )
            autoSyncSellerAndBuyer(bill)
        }
    }

    fun updateBill(bill: ContractBill) {
        viewModelScope.launch {
            val oldBill = allBills.value.find { it.id == bill.id }
            repository.updateContractBill(bill)
            FirebaseService.updateBillInFirebase(
                getApplication(),
                oldBill,
                bill,
                activeUser.value?.username ?: "Admin",
                activeUser.value?.role ?: "Admin"
            )
            autoSyncSellerAndBuyer(bill)
        }
    }

    fun deleteBill(bill: ContractBill) {
        viewModelScope.launch {
            val associatedPayments = repository.deleteContractBill(bill)
            FirebaseService.deleteBillFromFirebase(
                getApplication(),
                bill,
                activeUser.value?.username ?: "Admin",
                activeUser.value?.role ?: "Admin"
            )
            associatedPayments.forEach { payment ->
                FirebaseService.deletePaymentFromFirebase(
                    getApplication(),
                    payment
                )
            }
        }
    }

    fun addSeller(
        sellerName: String,
        mobile: String,
        place: String,
        gstNo: String,
        millName: String,
        address: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val user = activeUser.value?.username ?: "Admin"
            try {
                repository.addSeller(Seller(name = sellerName, phone = mobile, place = place, address = address))
            } catch (e: Exception) {
                Log.e("RanisaViewModel", "Error saving seller locally", e)
            }

            val result = FirebaseService.saveSellerToFirebase(
                getApplication(),
                sellerName = sellerName,
                mobile = mobile,
                place = place,
                gstNo = gstNo,
                millName = millName,
                address = address,
                user = user
            )
            if (result.first) {
                // Instantly update our Live RTDB cache so the UI dropdown has it immediately!
                val newSeller = FirebaseSeller(
                    sellerId = java.util.UUID.randomUUID().toString(),
                    sellerName = sellerName,
                    mobile = mobile,
                    place = place,
                    gstNo = gstNo,
                    millName = millName,
                    address = address
                )
                val updatedFullList = rtdbFullSellers.value.toMutableList()
                if (updatedFullList.none { it.sellerName.trim().equals(sellerName.trim(), ignoreCase = true) }) {
                    updatedFullList.add(newSeller)
                    rtdbFullSellers.value = updatedFullList.distinctBy { it.sellerName.trim().lowercase() }
                }
                val updatedNames = rtdbSellers.value.toMutableList()
                if (!updatedNames.contains(sellerName)) {
                    updatedNames.add(sellerName)
                    rtdbSellers.value = updatedNames.sorted()
                }

                onSuccess()
            } else {
                onError(result.second ?: "Error saving seller")
            }
        }
    }

    fun addBroker(
        brokerName: String,
        mobile: String,
        address: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val user = activeUser.value?.username ?: "Admin"
            try {
                repository.addBroker(Broker(name = brokerName, phone = mobile, address = address))
            } catch (e: Exception) {
                Log.e("RanisaViewModel", "Error saving broker locally", e)
            }

            val result = FirebaseService.saveBrokerToFirebase(
                getApplication(),
                brokerName = brokerName,
                mobile = mobile,
                address = address,
                user = user
            )
            if (result.first) {
                val newBroker = FirebaseBroker(
                    brokerId = java.util.UUID.randomUUID().toString(),
                    brokerName = brokerName,
                    mobile = mobile,
                    address = address,
                    createdBy = user,
                    createdDateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                )
                val updatedFullList = rtdbFullBrokers.value.toMutableList()
                if (updatedFullList.none { it.brokerName.trim().equals(brokerName.trim(), ignoreCase = true) }) {
                    updatedFullList.add(newBroker)
                    rtdbFullBrokers.value = updatedFullList.distinctBy { it.brokerName.trim().lowercase() }
                }
                val updatedNames = rtdbBrokers.value.toMutableList()
                if (!updatedNames.contains(brokerName)) {
                    updatedNames.add(brokerName)
                    rtdbBrokers.value = updatedNames.sorted()
                }
                onSuccess()
            } else {
                onError(result.second ?: "Error saving broker")
            }
        }
    }

    fun updateBroker(
        brokerId: String,
        brokerName: String,
        mobile: String,
        address: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val user = activeUser.value?.username ?: "Admin"
            // Update local Room database if match found by name
            val localBrokers = brokers.value
            localBrokers.find { it.name.equals(brokerName, ignoreCase = true) }?.let { local ->
                repository.updateBroker(local.copy(phone = mobile, address = address))
            }
            // Update Firebase
            val result = FirebaseService.updateBrokerInFirebase(
                getApplication(),
                brokerId = brokerId,
                brokerName = brokerName,
                mobile = mobile,
                address = address,
                user = user
            )
            if (result.first) {
                onSuccess()
            } else {
                onError(result.second ?: "Error updating broker")
            }
        }
    }

    fun deleteBroker(
        brokerId: String,
        brokerName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val user = activeUser.value?.username ?: "Admin"
            // Delete from local Room database if match found by name
            val localBrokers = brokers.value
            localBrokers.find { it.name.equals(brokerName, ignoreCase = true) }?.let { local ->
                repository.deleteBroker(local)
            }
            // Delete from Firebase
            val result = FirebaseService.deleteBrokerFromFirebase(
                getApplication(),
                brokerId = brokerId,
                brokerName = brokerName,
                user = user
            )
            if (result.first) {
                onSuccess()
            } else {
                onError(result.second ?: "Error deleting broker")
            }
        }
    }

    fun updateSeller(
        sellerId: String,
        sellerName: String,
        mobile: String,
        place: String,
        gstNo: String,
        millName: String,
        address: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val user = activeUser.value?.username ?: "Admin"
            // Update local Room database if match found by name
            val localSellers = sellers.value
            localSellers.find { it.name.equals(sellerName, ignoreCase = true) }?.let { local ->
                repository.updateSeller(local.copy(phone = mobile, place = place, address = address))
            }
            // Update Firebase
            val result = FirebaseService.updateSellerInFirebase(
                getApplication(),
                sellerId = sellerId,
                sellerName = sellerName,
                mobile = mobile,
                place = place,
                gstNo = gstNo,
                millName = millName,
                address = address,
                user = user
            )
            if (result.first) {
                onSuccess()
            } else {
                onError(result.second ?: "Error updating seller")
            }
        }
    }

    fun deleteSeller(
        sellerId: String,
        sellerName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val user = activeUser.value?.username ?: "Admin"
            // Delete from local Room database if match found by name
            val localSellers = sellers.value
            localSellers.find { it.name.equals(sellerName, ignoreCase = true) }?.let { local ->
                repository.deleteSeller(local)
            }
            // Delete from Firebase
            val result = FirebaseService.deleteSellerFromFirebase(
                getApplication(),
                sellerId = sellerId,
                sellerName = sellerName,
                user = user
            )
            if (result.first) {
                onSuccess()
            } else {
                onError(result.second ?: "Error deleting seller")
            }
        }
    }

    fun addBuyer(
        buyerName: String,
        mobile: String,
        place: String,
        gstNo: String,
        firmName: String,
        address: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val user = activeUser.value?.username ?: "Admin"
            try {
                repository.addBuyer(Buyer(name = buyerName, phone = mobile, place = place, address = address))
            } catch (e: Exception) {
                Log.e("RanisaViewModel", "Error saving buyer locally", e)
            }

            val result = FirebaseService.saveBuyerToFirebase(
                getApplication(),
                buyerName = buyerName,
                mobile = mobile,
                place = place,
                gstNo = gstNo,
                firmName = firmName,
                address = address,
                user = user
            )
            if (result.first) {
                // Instantly update our Live RTDB cache so the UI dropdown has it immediately!
                val newBuyer = FirebaseBuyer(
                    buyerId = java.util.UUID.randomUUID().toString(),
                    buyerName = buyerName,
                    mobile = mobile,
                    place = place,
                    gstNo = gstNo,
                    firmName = firmName,
                    address = address
                )
                val updatedFullList = rtdbFullBuyers.value.toMutableList()
                if (updatedFullList.none { it.buyerName.trim().equals(buyerName.trim(), ignoreCase = true) }) {
                    updatedFullList.add(newBuyer)
                    rtdbFullBuyers.value = updatedFullList.distinctBy { it.buyerName.trim().lowercase() }
                }
                val updatedNames = rtdbBuyers.value.toMutableList()
                if (!updatedNames.contains(buyerName)) {
                    updatedNames.add(buyerName)
                    rtdbBuyers.value = updatedNames.sorted()
                }

                onSuccess()
            } else {
                onError(result.second ?: "Error saving buyer")
            }
        }
    }

    fun updateBuyer(
        buyerId: String,
        buyerName: String,
        mobile: String,
        place: String,
        gstNo: String,
        firmName: String,
        address: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val user = activeUser.value?.username ?: "Admin"
            // Update local Room database if match found by name
            val localBuyers = buyers.value
            localBuyers.find { it.name.equals(buyerName, ignoreCase = true) }?.let { local ->
                repository.updateBuyer(local.copy(phone = mobile, place = place, address = address))
            }
            // Update Firebase
            val result = FirebaseService.updateBuyerInFirebase(
                getApplication(),
                buyerId = buyerId,
                buyerName = buyerName,
                mobile = mobile,
                place = place,
                gstNo = gstNo,
                firmName = firmName,
                address = address,
                user = user
            )
            if (result.first) {
                onSuccess()
            } else {
                onError(result.second ?: "Error updating buyer")
            }
        }
    }

    fun deleteBuyer(
        buyerId: String,
        buyerName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val user = activeUser.value?.username ?: "Admin"
            // Delete from local Room database if match found by name
            val localBuyers = buyers.value
            localBuyers.find { it.name.equals(buyerName, ignoreCase = true) }?.let { local ->
                repository.deleteBuyer(local)
            }
            // Delete from Firebase
            val result = FirebaseService.deleteBuyerFromFirebase(
                getApplication(),
                buyerId = buyerId,
                buyerName = buyerName,
                user = user
            )
            if (result.first) {
                onSuccess()
            } else {
                onError(result.second ?: "Error deleting buyer")
            }
        }
    }

    val isSavingPayment = MutableStateFlow(false)

    // Payments CRUD
    fun savePayment(
        buyerName: String,
        date: String,
        amount: Double,
        paymentMode: String,
        bankName: String,
        remarks: String,
        billNo: String = "",
        firm: String = "",
        sellerName: String = "",
        referenceNumber: String = "",
        remainingBalance: Double = 0.0,
        discountPercent: Double = 0.0,
        discountAmount: Double = 0.0,
        commissionPercent: Double = 0.0,
        commissionAmount: Double = 0.0,
        remarks1: String = "",
        remarks2: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (isSavingPayment.value) return
        viewModelScope.launch {
            try {
                isSavingPayment.value = true
                val user = activeUser.value?.username ?: "Admin"
                val role = activeUser.value?.role ?: "Admin"
                
                // Generate a unique paymentId upfront to keep SQLite and Firebase synchronized
                val uniquePaymentId = java.util.UUID.randomUUID().toString()
                val currentTimestamp = System.currentTimeMillis()

                var calculatedBillAmt = 0.0
                if (billNo.isNotBlank() && firm.isNotBlank()) {
                    try {
                        val billsList = repository.contractBills.first()
                        val matched = billsList.find { 
                            it.billNumber.trim().equals(billNo.trim(), ignoreCase = true) && 
                            it.firmName.trim().replace(" ", "").equals(firm.trim().replace(" ", ""), ignoreCase = true) 
                        }
                        if (matched != null) {
                            calculatedBillAmt = matched.billAmount
                        }
                    } catch (e: Exception) {
                        Log.e("RanisaViewModel", "Error fetching bill in savePayment", e)
                    }
                }

                val newPayment = Payment(
                    paymentId = uniquePaymentId,
                    billNo = billNo,
                    firm = firm,
                    sellerName = sellerName,
                    buyerId = buyerName,
                    buyerName = buyerName,
                    paymentAmount = amount,
                    paymentMode = paymentMode,
                    referenceNumber = bankName.ifBlank { referenceNumber },
                    remarks = remarks,
                    receivedBy = user,
                    createdBy = user,
                    createdByName = user,
                    createdAt = date.ifBlank { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) },
                    timestamp = currentTimestamp,
                    paymentDate = date,
                    discountPercent = discountPercent,
                    discountAmount = discountAmount,
                    commissionPercent = commissionPercent,
                    commissionAmount = commissionAmount,
                    remarks1 = remarks1,
                    remarks2 = remarks2,
                    alreadyPaidAmount = 0.0,
                    pendingAmount = remainingBalance,
                    updatedAt = currentTimestamp,
                    updatedBy = user,
                    billAmount = calculatedBillAmt
                )

                repository.insertPayment(newPayment)
                val fbSuccess = FirebaseService.savePaymentToFirebase(
                    getApplication(),
                    newPayment,
                    user,
                    role
                )
                if (fbSuccess) {
                    if (billNo.isNotBlank()) {
                        FirebaseService.logActionToFirebase(
                            context = getApplication(),
                            action = "ADD_PAYMENT",
                            billNo = billNo,
                            firm = firm,
                            user = user,
                            role = role,
                            details = "Added payment of ₹${amount}. Remaining Balance: ₹${remainingBalance}"
                        )
                        // Update corresponding bill in Firebase
                        updateBillPaymentStatus(billNo, firm, newPayment, isDeletion = false)
                    }
                    onSuccess()
                } else {
                    onError("Firebase save failed")
                }
            } catch (e: Exception) {
                Log.e("RanisaViewModel", "Error saving payment", e)
                onError(e.localizedMessage ?: "Unknown error")
            } finally {
                isSavingPayment.value = false
            }
        }
    }

    suspend fun updateBillPaymentStatus(billNo: String, firm: String, newPayment: Payment? = null, isDeletion: Boolean = false) {
        try {
            if (billNo.isBlank()) return
            val billsList = repository.contractBills.first()
            val bill = billsList.find { 
                it.billNumber.trim().equals(billNo.trim(), ignoreCase = true)
            } ?: return

            val localPayments = repository.payments.first().toMutableList()
            if (newPayment != null) {
                if (isDeletion) {
                    localPayments.removeAll { it.paymentId == newPayment.paymentId }
                } else {
                    localPayments.removeAll { it.paymentId == newPayment.paymentId }
                    localPayments.add(newPayment)
                }
            }
            val matchingPayments = localPayments.filter { p ->
                p.billNo.trim().equals(billNo.trim(), ignoreCase = true)
            }

            val totalReceived = matchingPayments.sumOf { it.paymentAmount }
            val totalDiscount = matchingPayments.sumOf { it.discountAmount }
            val totalCommission = matchingPayments.sumOf { it.commissionAmount }
            val totalRemarkAmt = matchingPayments.sumOf { it.remarks1.toDoubleOrNull() ?: 0.0 }
            val latestRemark = matchingPayments.filter { it.remarks2.isNotBlank() }.maxByOrNull { it.timestamp }?.remarks2 ?: ""

            val remainingBalance = bill.billAmount - totalReceived - totalDiscount - totalCommission - totalRemarkAmt
            val paymentStatus = when {
                remainingBalance <= 0.01 -> "Fully Paid"
                totalReceived <= 0.0 -> "Pending"
                else -> "Partial Paid"
            }

            val updatedBill = bill.copy(
                totalReceived = totalReceived,
                remainingBalance = remainingBalance,
                balance = remainingBalance,
                paymentStatus = paymentStatus,
                lastPaymentDate = matchingPayments.maxByOrNull { it.timestamp }?.date ?: bill.lastPaymentDate,
                discountPercent = totalDiscount,
                commissionPercent = totalCommission,
                remark1 = if (totalRemarkAmt > 0.0) String.format(java.util.Locale.US, "%.2f", totalRemarkAmt) else "",
                remark2 = latestRemark
            )

            repository.updateContractBill(updatedBill)

            val user = activeUser.value?.username ?: "Admin"
            val role = activeUser.value?.role ?: "Admin"
            FirebaseService.updateBillInFirebase(
                getApplication(),
                oldBill = bill,
                newBill = updatedBill,
                user = user,
                role = role
            )
            Log.d("RanisaViewModel", "Updated Bill: ${bill.billNumber} to $paymentStatus")
        } catch (e: Exception) {
            Log.e("RanisaViewModel", "Error in updateBillPaymentStatus", e)
        }
    }

    val isUpdatingPayment = MutableStateFlow(false)

    fun updatePaymentTransaction(
        bill: ContractBill,
        paymentAmount: Double,
        paymentDate: String,
        paymentMode: String,
        referenceNumber: String,
        notes: String,
        existingPayment: Payment? = null,
        discountPercent: Double = 0.0,
        discountAmount: Double = 0.0,
        commissionPercent: Double = 0.0,
        commissionAmount: Double = 0.0,
        remarks1: String = "",
        remarks2: String = "",
        alreadyPaidAmount: Double = 0.0,
        pendingAmount: Double = 0.0,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (isUpdatingPayment.value) return
        viewModelScope.launch {
            try {
                isUpdatingPayment.value = true
                val user = activeUser.value?.username ?: "Admin"
                val role = activeUser.value?.role ?: "Admin"
                
                val paymentIdToUse = existingPayment?.paymentId ?: java.util.UUID.randomUUID().toString()
                val currentTimestamp = existingPayment?.timestamp ?: System.currentTimeMillis()
                
                val newPayment = Payment(
                    paymentId = paymentIdToUse,
                    billNo = bill.billNumber,
                    firm = bill.firmName,
                    sellerName = bill.sellerName,
                    buyerId = bill.buyerName,
                    buyerName = bill.buyerName,
                    paymentAmount = paymentAmount,
                    paymentMode = paymentMode,
                    referenceNumber = referenceNumber,
                    remarks = notes,
                    receivedBy = existingPayment?.receivedBy ?: user,
                    createdBy = existingPayment?.createdBy ?: user,
                    createdByName = existingPayment?.createdByName ?: user,
                    createdAt = paymentDate,
                    timestamp = currentTimestamp,
                    paymentDate = paymentDate,
                    discountPercent = discountPercent,
                    discountAmount = discountAmount,
                    commissionPercent = commissionPercent,
                    commissionAmount = commissionAmount,
                    remarks1 = remarks1,
                    remarks2 = remarks2,
                    alreadyPaidAmount = alreadyPaidAmount,
                    pendingAmount = pendingAmount,
                    updatedAt = System.currentTimeMillis(),
                    updatedBy = user,
                    billAmount = bill.billAmount
                )

                // Simulate the local list with newPayment to compute exact aggregates
                val localPayments = repository.payments.first().toMutableList()
                localPayments.removeAll { it.paymentId == paymentIdToUse }
                localPayments.add(newPayment)

                val matchingPayments = localPayments.filter { p ->
                    p.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true)
                }

                val totalReceived = matchingPayments.sumOf { it.paymentAmount }
                val totalDiscount = matchingPayments.sumOf { it.discountAmount }
                val totalCommission = matchingPayments.sumOf { it.commissionAmount }
                val totalRemarkAmt = matchingPayments.sumOf { it.remarks1.toDoubleOrNull() ?: 0.0 }
                val latestRemark = matchingPayments.filter { it.remarks2.isNotBlank() }.maxByOrNull { it.timestamp }?.remarks2 ?: ""

                val remainingBalance = bill.billAmount - totalReceived - totalDiscount - totalCommission - totalRemarkAmt
                val paymentStatus = when {
                    remainingBalance <= 0.01 -> "Fully Paid"
                    totalReceived <= 0.0 -> "Pending"
                    else -> "Partial Paid"
                }

                val updatedBill = bill.copy(
                    totalReceived = totalReceived,
                    remainingBalance = remainingBalance,
                    balance = remainingBalance,
                    paymentStatus = paymentStatus,
                    lastPaymentDate = paymentDate,
                    discountPercent = totalDiscount,
                    commissionPercent = totalCommission,
                    remark1 = if (totalRemarkAmt > 0.0) String.format(java.util.Locale.US, "%.2f", totalRemarkAmt) else "",
                    remark2 = latestRemark
                )
                
                // 2. Save to SQLite database locally
                repository.insertPayment(newPayment)
                repository.updateContractBill(updatedBill)
                
                // 3. Save to Firebase atomically using transaction multi-path write
                val fbSuccess = FirebaseService.savePaymentAtomicTransaction(
                    getApplication(),
                    updatedBill,
                    newPayment,
                    user,
                    role
                )
                
                if (fbSuccess) {
                    FirebaseService.logActionToFirebase(
                        context = getApplication(),
                        action = if (existingPayment != null) "UPDATE_EXISTING_PAYMENT" else "EDIT_PAYMENT",
                        billNo = bill.billNumber,
                        firm = bill.firmName,
                        user = user,
                        role = role,
                        details = if (existingPayment != null) "Payment updated from ₹${existingPayment.paymentAmount} to ₹${paymentAmount}. Remaining: ₹${remainingBalance}" else "Direct table payment edited: ₹${paymentAmount}. Remaining: ₹${remainingBalance}"
                    )
                    onSuccess()
                } else {
                    onError("Firebase transaction update failed. Please check network connection.")
                }
            } catch (e: Exception) {
                Log.e("RanisaViewModel", "Error in updatePaymentTransaction", e)
                onError(e.localizedMessage ?: "Unknown error occurred")
            } finally {
                isUpdatingPayment.value = false
            }
        }
    }

    fun updatePayment(payment: Payment) {
        viewModelScope.launch {
            repository.updatePayment(payment)
            updateBillPaymentStatus(payment.billNo, payment.firm, payment, isDeletion = false)
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            repository.deletePayment(payment)
            FirebaseService.deletePaymentFromFirebase(
                getApplication(),
                payment
            )
            updateBillPaymentStatus(payment.billNo, payment.firm, payment, isDeletion = true)
        }
    }

    // Logger Helpers for PDF & Print Actions
    fun logPdfExport(billNumber: String, firmName: String) {
        viewModelScope.launch {
            repository.logAction("Contract Form", "PDF Export", "", "Bill: $billNumber, Firm: $firmName", billNo = billNumber)
        }
    }

    fun logPrint(billNumber: String, firmName: String) {
        viewModelScope.launch {
            repository.logAction("Contract Form", "Print", "", "Bill: $billNumber, Firm: $firmName", billNo = billNumber)
        }
    }

    fun logPdfPreview(billNumber: String, firmName: String) {
        viewModelScope.launch {
            repository.logAction("Contract Form", "PDF Preview", "", "Bill: $billNumber, Firm: $firmName", billNo = billNumber)
        }
    }

    fun logPdfDownload(billNumber: String, firmName: String) {
        viewModelScope.launch {
            repository.logAction("Contract Form", "PDF Download", "", "Bill: $billNumber, Firm: $firmName", billNo = billNumber)
        }
    }

    fun logPdfShare(billNumber: String, firmName: String) {
        viewModelScope.launch {
            repository.logAction("Contract Form", "PDF Share", "", "Bill: $billNumber, Firm: $firmName", billNo = billNumber)
        }
    }

    fun logSettingsChange(settingName: String, oldValue: String, newValue: String) {
        viewModelScope.launch {
            repository.logAction("Settings Screen", "Settings Change", oldValue, newValue)
        }
    }

    fun logUserCreation(newUsername: String, role: String) {
        viewModelScope.launch {
            repository.logAction("User Management", "User Creation", "", "Created User: $newUsername, Role: $role")
        }
    }

    fun logUserUpdate(username: String, details: String) {
        viewModelScope.launch {
            repository.logAction("User Management", "User Update", "", "Updated User: $username, Details: $details")
        }
    }

    fun logPasswordChange(username: String) {
        viewModelScope.launch {
            repository.logAction("Settings Screen", "Password Change", "", "Password changed for user: $username")
        }
    }

    fun logImport(fileName: String) {
        viewModelScope.launch {
            repository.logAction("Data Sync", "Import", "", "Imported data from: $fileName")
        }
    }

    fun logExport() {
        viewModelScope.launch {
            repository.logAction("Data Sync", "Export", "", "Exported database to Firebase Cloud")
        }
    }

    /**
     * Authenticates a user locally by setting their state in the repository and Room DB.
     */
    fun loginUserLocally(username: String, role: String) {
        viewModelScope.launch {
            repository.loginUserLocally(username, role)
        }
    }

    /**
     * Clears the active session and logs out the user.
     */
    fun logoutUser() {
        repository.logoutUser()
    }

    // Calculate outstanding metrics per Buyer or Seller
    fun getBuyerLedgerData(buyerName: String): Flow<List<ContractBill>> {
        return repository.getBillsByBuyer(buyerName)
    }

    fun getSellerLedgerData(sellerName: String): Flow<List<ContractBill>> {
        return repository.getBillsBySeller(sellerName)
    }

    fun getBuyerPayments(buyerName: String): Flow<List<Payment>> {
        return repository.getPaymentsByBuyer(buyerName)
    }

    fun getBuyerOutstanding(buyerName: String): Flow<Double> {
        val billsFlow = repository.getBillsByBuyer(buyerName)
        val paymentsFlow = repository.getPaymentsByBuyer(buyerName)
        return combine(billsFlow, paymentsFlow) { bills, payments ->
            val totalBillAmount = bills.sumOf { it.billAmount }
            val totalDD = bills.sumOf { it.ddAmount }
            val totalCashCutting = bills.sumOf { it.cashCutting }
            val totalPaid = payments.sumOf { it.amount }
            totalBillAmount - totalDD - totalCashCutting - totalPaid
        }
    }

    fun getSellerOutstanding(sellerName: String): Flow<Double> {
        val billsFlow = repository.getBillsBySeller(sellerName)
        return billsFlow.map { bills ->
            // For a seller, they are owed Bill Amount minus cash cuttings/DD
            bills.sumOf { it.billAmount - it.ddAmount - it.cashCutting }
        }
    }

    fun deleteSellerLedger(sellerName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val sellerBills = allBills.value.filter { it.sellerName == sellerName }
                sellerBills.forEach { bill ->
                    val associatedPayments = repository.deleteContractBill(bill)
                    FirebaseService.deleteBillFromFirebase(
                        getApplication(),
                        bill,
                        activeUser.value?.username ?: "Admin",
                        activeUser.value?.role ?: "Admin"
                    )
                    associatedPayments.forEach { payment ->
                        FirebaseService.deletePaymentFromFirebase(
                            getApplication(),
                            payment
                        )
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Error deleting seller ledger")
            }
        }
    }

    fun deleteBuyerLedger(buyerName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val buyerBills = allBills.value.filter { it.buyerName == buyerName }
                buyerBills.forEach { bill ->
                    val associatedPayments = repository.deleteContractBill(bill)
                    FirebaseService.deleteBillFromFirebase(
                        getApplication(),
                        bill,
                        activeUser.value?.username ?: "Admin",
                        activeUser.value?.role ?: "Admin"
                    )
                    associatedPayments.forEach { payment ->
                        FirebaseService.deletePaymentFromFirebase(
                            getApplication(),
                            payment
                        )
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Error deleting buyer ledger")
            }
        }
    }

    fun deleteBrokerLedger(brokerName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val brokerBills = allBills.value.filter { it.brokerName == brokerName }
                brokerBills.forEach { bill ->
                    val associatedPayments = repository.deleteContractBill(bill)
                    FirebaseService.deleteBillFromFirebase(
                        getApplication(),
                        bill,
                        activeUser.value?.username ?: "Admin",
                        activeUser.value?.role ?: "Admin"
                    )
                    associatedPayments.forEach { payment ->
                        FirebaseService.deletePaymentFromFirebase(
                            getApplication(),
                            payment
                        )
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Error deleting broker ledger")
            }
        }
    }

    fun resetAllLocalData(context: android.content.Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // Clear Room SQLite database
                val database = AppDatabase.getDatabase(context)
                database.clearAllTables()
                
                // Clear remember me preferences
                val sharedPrefs = context.getSharedPreferences("ranisa_prefs", android.content.Context.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putBoolean("remember_me", false)
                    .remove("saved_username")
                    .remove("saved_role")
                    .apply()
                
                // Logout user locally
                logoutUser()
                
                onSuccess()
            } catch (e: Exception) {
                Log.e("RanisaViewModel", "Error resetting local data: ${e.message}")
            }
        }
    }

    // Custom Factory
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RanisaViewModel::class.java)) {
                val database = AppDatabase.getDatabase(application)
                val repository = AppRepository(database.appDao())
                @Suppress("UNCHECKED_CAST")
                return RanisaViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
