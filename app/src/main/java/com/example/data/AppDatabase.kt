package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val role: String // Admin, Broker, Accountant, Viewer
)

@Entity(tableName = "firms")
data class Firm(
    @PrimaryKey val id: String,
    val name: String
)

@Entity(tableName = "sellers")
data class Seller(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val place: String = "",
    val address: String = "",
    val firmName: String = ""
)

@Entity(tableName = "buyers")
data class Buyer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val place: String = "",
    val address: String = "",
    val firmName: String = ""
)

data class FirebaseSeller(
    val sellerId: String = "",
    val sellerName: String = "",
    val mobile: String = "",
    val place: String = "",
    val gstNo: String = "",
    val millName: String = "",
    val address: String = ""
)

data class FirebaseBuyer(
    val buyerId: String = "",
    val buyerName: String = "",
    val mobile: String = "",
    val place: String = "",
    val gstNo: String = "",
    val firmName: String = "",
    val address: String = ""
)

data class FirebaseBroker(
    val brokerId: String = "",
    val brokerName: String = "",
    val mobile: String = "",
    val address: String = "",
    val totalBillings: Int = 0,
    val totalQtls: Double = 0.0,
    val createdDateTime: String = "",
    val updatedDateTime: String = "",
    val createdBy: String = "",
    val updatedBy: String = ""
)

@Entity(tableName = "brokers")
data class Broker(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val firmName: String = ""
)

@Entity(tableName = "contract_bills")
data class ContractBill(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firmName: String, // "Lalit Rice Broker" or "Hare Krishna Rice Broker"
    val date: String,
    val billNumber: String,
    val sellerName: String,
    val buyerName: String,
    val place: String,
    val lorryFreight: Double = 0.0,
    val rate: Double = 0.0,
    val quintals: Double = 0.0,
    val billAmount: Double = 0.0,
    val ddAmount: Double = 0.0,
    val cashCutting: Double = 0.0,
    val balance: Double = 0.0,
    val bankName: String = "",
    val remarks: String = "",
    val gstNo: String = "",
    val particulars: String = "",
    val bags: Int = 0,
    val packing: String = "",
    val transport: String = "",
    val delivery: String = "",
    val lorryNo: String = "",
    val payment: String = "",
    val mobileNo: String = "",
    val brand: String = "",
    val amountInWords: String = "",
    val sellerSignature: String = "",
    val creditDays: Int = 0,
    val totalReceived: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val paymentStatus: String = "Pending",
    val lastPaymentDate: String = "",
    val sellerAddress: String = "",
    val buyerAddress: String = "",
    val itemsJson: String = "",
    val discountPercent: Double = 0.0,
    val commissionPercent: Double = 0.0,
    val remark1: String = "",
    val remark2: String = "",
    val brokerName: String = "",
    val brokerId: String = "",
    val eb: String = ""
)

data class ContractItem(
    val particulars: String = "",
    val bags: Int = 0,
    val packing: String = "",
    val qtls: Double = 0.0,
    val rate: Double = 0.0
)

fun serializeItems(items: List<ContractItem>): String {
    val array = org.json.JSONArray()
    for (item in items) {
        val obj = org.json.JSONObject()
        obj.put("particulars", item.particulars)
        obj.put("bags", item.bags)
        obj.put("packing", item.packing)
        obj.put("qtls", item.qtls)
        obj.put("rate", item.rate)
        array.put(obj)
    }
    return array.toString()
}

fun deserializeItems(json: String?): List<ContractItem> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val list = mutableListOf<ContractItem>()
        val array = org.json.JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                ContractItem(
                    particulars = obj.optString("particulars", ""),
                    bags = obj.optInt("bags", 0),
                    packing = obj.optString("packing", ""),
                    qtls = obj.optDouble("qtls", 0.0),
                    rate = obj.optDouble("rate", 0.0)
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

fun getItemsForBill(bill: ContractBill): List<ContractItem> {
    val items = deserializeItems(bill.itemsJson)
    if (items.isEmpty() && (bill.particulars.isNotBlank() || bill.bags > 0 || bill.quintals > 0.0 || bill.rate > 0.0)) {
        return listOf(
            ContractItem(
                particulars = bill.particulars,
                bags = bill.bags,
                packing = bill.packing,
                qtls = bill.quintals,
                rate = bill.rate
            )
        )
    }
    return items
}

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey val paymentId: String = "",
    val id: Int = 0,
    val billNo: String = "",
    val firm: String = "",
    val sellerName: String = "",
    val buyerId: String = "",
    val buyerName: String = "",
    val paymentAmount: Double = 0.0,
    val paymentDate: String = "",
    val paymentMode: String = "",
    val referenceNumber: String = "",
    val remarks: String = "",
    val receivedBy: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: String = "",
    val timestamp: Long = 0L,
    val discountPercent: Double = 0.0,
    val discountAmount: Double = 0.0,
    val commissionPercent: Double = 0.0,
    val commissionAmount: Double = 0.0,
    val remarks1: String = "",
    val remarks2: String = "",
    val alreadyPaidAmount: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val updatedAt: Long = 0L,
    val updatedBy: String = "",
    val billAmount: Double = 0.0
) {
    val date: String get() = paymentDate.ifBlank { createdAt }
    val amount: Double get() = paymentAmount
    val bankName: String get() = referenceNumber
}

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userName: String,
    val userRole: String = "",
    val date: String,
    val time: String,
    val screen: String,
    val action: String, // Create, Update, Delete, Login, Logout, Print, PDF Export, etc.
    val firmName: String = "",
    val oldValue: String = "",
    val newValue: String = "",
    val device: String = "",
    val ipSessionId: String = "",
    val billNo: String = "",
    val partyName: String = ""
)

@Dao
interface AppDao {
    // Users
    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<User>>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    // Firms
    @Query("SELECT * FROM firms")
    fun getAllFirmsFlow(): Flow<List<Firm>>

    @Query("SELECT * FROM firms")
    suspend fun getAllFirms(): List<Firm>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFirm(firm: Firm)

    // Sellers
    @Query("SELECT * FROM sellers")
    fun getAllSellersFlow(): Flow<List<Seller>>

    @Query("SELECT * FROM sellers")
    suspend fun getAllSellers(): List<Seller>

    @Query("SELECT * FROM sellers WHERE name LIKE :query")
    fun searchSellers(query: String): Flow<List<Seller>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeller(seller: Seller)

    @Update
    suspend fun updateSeller(seller: Seller)

    @Delete
    suspend fun deleteSeller(seller: Seller)

    // Buyers
    @Query("SELECT * FROM buyers")
    fun getAllBuyersFlow(): Flow<List<Buyer>>

    @Query("SELECT * FROM buyers")
    suspend fun getAllBuyers(): List<Buyer>

    @Query("SELECT * FROM buyers WHERE name LIKE :query")
    fun searchBuyers(query: String): Flow<List<Buyer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuyer(buyer: Buyer)

    @Update
    suspend fun updateBuyer(buyer: Buyer)

    @Delete
    suspend fun deleteBuyer(buyer: Buyer)

    // Brokers
    @Query("SELECT * FROM brokers")
    fun getAllBrokersFlow(): Flow<List<Broker>>

    @Query("SELECT * FROM brokers")
    suspend fun getAllBrokers(): List<Broker>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBroker(broker: Broker)

    @Update
    suspend fun updateBroker(broker: Broker)

    @Delete
    suspend fun deleteBroker(broker: Broker)

    // Contract Bills
    @Query("SELECT * FROM contract_bills ORDER BY date DESC, id DESC")
    fun getAllContractBills(): Flow<List<ContractBill>>

    @Query("SELECT * FROM contract_bills WHERE sellerName = :sellerName ORDER BY date DESC")
    fun getBillsBySeller(sellerName: String): Flow<List<ContractBill>>

    @Query("SELECT * FROM contract_bills WHERE buyerName = :buyerName ORDER BY date DESC")
    fun getBillsByBuyer(buyerName: String): Flow<List<ContractBill>>

    @Query("SELECT * FROM contract_bills WHERE id = :id")
    suspend fun getBillById(id: Int): ContractBill?

    @Query("SELECT * FROM contract_bills WHERE billNumber = :billNumber AND firmName = :firmName LIMIT 1")
    suspend fun getBillByNumberAndFirm(billNumber: String, firmName: String): ContractBill?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: ContractBill): Long

    @Update
    suspend fun updateBill(bill: ContractBill)

    @Delete
    suspend fun deleteBill(bill: ContractBill)

    @Query("DELETE FROM contract_bills WHERE firmName = :firmName")
    suspend fun clearBillsByFirm(firmName: String)

    @Query("DELETE FROM contract_bills")
    suspend fun clearAllBills()

    // Payments
    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE billNo = :billNo AND firm = :firmName")
    suspend fun getPaymentsByBill(billNo: String, firmName: String): List<Payment>

    @Query("DELETE FROM payments WHERE billNo = :billNo AND firm = :firmName")
    suspend fun deletePaymentsByBill(billNo: String, firmName: String)

    @Query("SELECT * FROM payments WHERE buyerName = :buyerName ORDER BY timestamp DESC")
    fun getPaymentsByBuyer(buyerName: String): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Update
    suspend fun updatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("DELETE FROM payments")
    suspend fun clearAllPayments()

    // Logs
    @Query("SELECT * FROM audit_logs ORDER BY date DESC, time DESC, id DESC")
    fun getAllLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog)

    @Query("DELETE FROM sellers WHERE firmName = :firmName")
    suspend fun clearSellersByFirm(firmName: String)

    @Query("DELETE FROM buyers WHERE firmName = :firmName")
    suspend fun clearBuyersByFirm(firmName: String)

    @Query("DELETE FROM brokers WHERE firmName = :firmName")
    suspend fun clearBrokersByFirm(firmName: String)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAllLogs()
}

@Database(
    entities = [User::class, Firm::class, Seller::class, Buyer::class, ContractBill::class, Payment::class, AuditLog::class, Broker::class],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ranisa_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
