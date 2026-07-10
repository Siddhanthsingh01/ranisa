package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.data.FirebaseBuyer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyerMasterListScreen(
    navController: NavController,
    viewModel: RanisaViewModel,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // State variables
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedBuyerForEdit by remember { mutableStateOf<FirebaseBuyer?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedBuyerForDelete by remember { mutableStateOf<FirebaseBuyer?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    
    // Share sheet states
    var showShareSheet by remember { mutableStateOf(false) }
    var ledgerOwnerForShare by remember { mutableStateOf<String?>(null) }
    
    // Form States
    var formName by remember { mutableStateOf("") }
    val formMobiles = remember { mutableStateListOf<String>() }
    var formAddress by remember { mutableStateOf("") }
    var formGst by remember { mutableStateOf("") }
    
    // Buyers from database
    val buyersList by viewModel.rtdbFullBuyers.collectAsState()
    val bills by viewModel.allBills.collectAsState()
    val payments by viewModel.allPayments.collectAsState()
    
    // Filtered Buyers
    val filteredBuyers = remember(searchQuery, buyersList) {
        buyersList
            .distinctBy { it.buyerName.trim().lowercase() }
            .filter { buyer ->
                buyer.buyerName.contains(searchQuery, ignoreCase = true) ||
                buyer.mobile.contains(searchQuery, ignoreCase = true) ||
                buyer.address.contains(searchQuery, ignoreCase = true)
            }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Buyer Master List",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.testTag("buyer_master_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Drawer Menu",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Refresh List") },
                            onClick = {
                                showMoreMenu = false
                                Toast.makeText(context, "Refreshing data...", Toast.LENGTH_SHORT).show()
                            },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF322659) // Deep Purple Theme
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    formName = ""
                    formMobiles.clear()
                    formMobiles.add("")
                    formAddress = ""
                    formGst = ""
                    showAddDialog = true
                },
                containerColor = Color(0xFF322659),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("add_buyer_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add New Buyer",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF9F9FB)) // Premium White/Off-White Background
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Buyer Name...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("buyer_search_input"),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search icon",
                        tint = Color.Gray
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color.Gray
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF322659),
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            // Content
            if (filteredBuyers.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "No buyers",
                            modifier = Modifier
                                .size(100.dp)
                                .padding(bottom = 16.dp),
                            tint = Color(0xFFCBC8D6)
                        )
                        Text(
                            text = "No Buyers Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF322659)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add buyers to start tracking transactions.",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                formName = ""
                                formMobiles.clear()
                                formMobiles.add("")
                                formAddress = ""
                                formGst = ""
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF322659)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Buyer")
                        }
                    }
                }
            } else {
                // Buyer Master list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredBuyers, key = { it.buyerId }) { buyer ->
                        val buyerBills = bills.filter { it.buyerName == buyer.buyerName }
                        val totalQtls = buyerBills.sumOf { it.quintals }
                        BuyerCard(
                            buyer = buyer,
                            billCount = buyerBills.size,
                            totalQtls = totalQtls,
                            onEdit = {
                                selectedBuyerForEdit = buyer
                                formName = buyer.buyerName
                                formMobiles.clear()
                                val list = buyer.mobile.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                if (list.isEmpty()) {
                                    formMobiles.add("")
                                } else {
                                    formMobiles.addAll(list)
                                }
                                formAddress = buyer.address
                                formGst = buyer.gstNo
                                showEditDialog = true
                            },
                            onShare = {
                                ledgerOwnerForShare = buyer.buyerName
                                showShareSheet = true
                            },
                            onDeleteLedger = {
                                selectedBuyerForDelete = buyer
                                showDeleteDialog = true
                            },
                            onCardClick = {
                                navController.navigate("buyer_ledger?preselectedBuyer=${buyer.buyerName}")
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Buyer Dialog
    if (showAddDialog) {
        BuyerFormDialog(
            title = "Add Buyer Master",
            name = formName,
            onNameChange = { formName = it },
            mobiles = formMobiles,
            address = formAddress,
            onAddressChange = { formAddress = it },
            gst = formGst,
            onGstChange = { formGst = it },
            onDismiss = { showAddDialog = false },
            onSave = {
                if (formName.isBlank()) {
                    Toast.makeText(context, "Please enter Buyer Name", Toast.LENGTH_SHORT).show()
                } else {
                    val joinedMobile = formMobiles.filter { it.isNotBlank() }.joinToString(", ")
                    viewModel.addBuyer(
                        buyerName = formName,
                        mobile = joinedMobile,
                        place = "",
                        gstNo = formGst,
                        firmName = "",
                        address = formAddress,
                        onSuccess = {
                            showAddDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Buyer Added Successfully")
                            }
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        )
    }

    // Edit Buyer Dialog
    if (showEditDialog && selectedBuyerForEdit != null) {
        val buyer = selectedBuyerForEdit!!
        BuyerFormDialog(
            title = "Edit Buyer Details",
            name = formName,
            onNameChange = { formName = it },
            mobiles = formMobiles,
            address = formAddress,
            onAddressChange = { formAddress = it },
            gst = formGst,
            onGstChange = { formGst = it },
            onDismiss = { showEditDialog = false },
            onSave = {
                if (formName.isBlank()) {
                    Toast.makeText(context, "Please enter Buyer Name", Toast.LENGTH_SHORT).show()
                } else {
                    val joinedMobile = formMobiles.filter { it.isNotBlank() }.joinToString(", ")
                    viewModel.updateBuyer(
                        buyerId = buyer.buyerId,
                        buyerName = formName,
                        mobile = joinedMobile,
                        place = "",
                        gstNo = formGst,
                        firmName = "",
                        address = formAddress,
                        onSuccess = {
                            showEditDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Buyer Updated Successfully")
                            }
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        )
    }

    // Delete Confirmation Dialog
    // Delete Ledger Dialog
    if (showDeleteDialog && selectedBuyerForDelete != null) {
        val buyer = selectedBuyerForDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete all ledger records for this party?") },
            text = { Text("This will only delete ledger transactions.\nThe Master List will remain unchanged.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        com.example.util.BiometricHelper.runWithBiometric(
                            context = context,
                            title = "Ranisa Security",
                            subtitle = "Verify your fingerprint to continue.",
                            action = {
                                viewModel.deleteBuyerLedger(
                                    buyerName = buyer.buyerName,
                                    onSuccess = {
                                        showDeleteDialog = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Ledger transactions deleted successfully")
                                        }
                                    },
                                    onError = { error ->
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Share Ledger Bottom Sheet
    if (showShareSheet && ledgerOwnerForShare != null) {
        val ownerName = ledgerOwnerForShare!!
        val filteredBillsForOwner = remember(bills, ownerName) {
            bills.filter { it.buyerName == ownerName }
        }
        FullLedgerShareSheet(
            ledgerName = ownerName,
            ledgerType = "buyer",
            bills = filteredBillsForOwner,
            payments = payments,
            onDismissRequest = {
                showShareSheet = false
                ledgerOwnerForShare = null
            }
        )
    }
}

@Composable
fun BuyerCard(
    buyer: FirebaseBuyer,
    billCount: Int,
    totalQtls: Double,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDeleteLedger: () -> Unit,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
            .clickable { onCardClick() }
            .testTag("buyer_card_${buyer.buyerId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Profile Circle & Text
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large initial circle
                val initialLetter = buyer.buyerName.firstOrNull()?.toString() ?: "B"
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF322659).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initialLetter.uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF322659),
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Mid Text Fields
                Column {
                    Text(
                        text = buyer.buyerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF2D3748),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = "$billCount Registered Billings",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Total Qtls: ",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${String.format("%.2f", totalQtls)} Qtls",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF322659)
                        )
                    }

                    if (buyer.mobile.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = buyer.mobile,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    if (buyer.address.isNotBlank()) {
                        Text(
                            text = buyer.address,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (buyer.gstNo.isNotBlank()) {
                        Text(
                            text = "GST: ${buyer.gstNo}",
                            fontSize = 11.sp,
                            color = Color(0xFF38C194),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Right Actions: Three-dot Menu Action
            var menuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.padding(start = 8.dp)) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("buyer_card_menu_${buyer.buyerId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.Gray
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("✏️ Edit") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                        modifier = Modifier.testTag("buyer_card_edit_${buyer.buyerId}")
                    )
                    DropdownMenuItem(
                        text = { Text("📤 Share Ledger") },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        },
                        modifier = Modifier.testTag("buyer_card_share_${buyer.buyerId}")
                    )
                    DropdownMenuItem(
                        text = { Text("🗑 Delete Ledger") },
                        onClick = {
                            menuExpanded = false
                            onDeleteLedger()
                        },
                        modifier = Modifier.testTag("buyer_card_delete_ledger_${buyer.buyerId}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyerFormDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    mobiles: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    address: String,
    onAddressChange: (String) -> Unit,
    gst: String,
    onGstChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .shadow(8.dp, shape = RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF322659),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Buyer Name *") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("form_buyer_name"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF322659),
                        focusedLabelColor = Color(0xFF322659),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    )
                )

                // Mobiles Column
                Text(
                    text = "Mobile Numbers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF322659),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mobiles.forEachIndexed { index, mobileValue ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = mobileValue,
                                onValueChange = { mobiles[index] = it },
                                label = { Text("Mobile Number ${index + 1}") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                textStyle = LocalTextStyle.current.copy(color = Color.Black),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("form_buyer_mobile_$index"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF322659),
                                    focusedLabelColor = Color(0xFF322659),
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (index == 0) {
                                IconButton(
                                    onClick = { mobiles.add("") },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE6FFFA))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Mobile",
                                        tint = Color(0xFF319795)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { mobiles.removeAt(index) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFECEC))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Remove Mobile",
                                        tint = Color.Red
                                    )
                                }
                            }
                        }
                    }
                }

                 // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = { Text("Address") },
                    maxLines = 2,
                    textStyle = LocalTextStyle.current.copy(color = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("form_buyer_address"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF322659),
                        focusedLabelColor = Color(0xFF322659),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    )
                )

                // GST No
                OutlinedTextField(
                    value = gst,
                    onValueChange = onGstChange,
                    label = { Text("GST Number (Optional)") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .testTag("form_buyer_gst"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF322659),
                        focusedLabelColor = Color(0xFF322659),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    )
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF322659)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}
