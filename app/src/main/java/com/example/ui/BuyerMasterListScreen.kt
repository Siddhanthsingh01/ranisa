package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.example.ui.theme.*

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
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Buyer Master List",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
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
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onPrimary
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search field
            EnterpriseSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search Buyer Name...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onClear = { searchQuery = "" },
                testTag = "buyer_search_input"
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
                                .size(96.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "No Buyers Found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add buyers to start tracking transactions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
        EnterpriseDialog(
            title = "Delete all ledger records for this party?",
            description = "This will only delete ledger transactions.\nThe Master List will remain unchanged.",
            confirmText = "Delete",
            dismissText = "Cancel",
            onConfirm = {
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
            onDismiss = { showDeleteDialog = false },
            icon = Icons.Default.Delete,
            confirmButtonColor = MaterialTheme.colorScheme.error
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
    val isDark = isSystemInDarkTheme()
    val borderCol = if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    Card(
        onClick = onCardClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("buyer_card_${buyer.buyerId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderCol)
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
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initialLetter.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Mid Text Fields
                Column {
                    Text(
                        text = buyer.buyerName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = "$billCount Registered Billings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Total Qtls: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format("%.2f", totalQtls)} Qtls",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (buyer.mobile.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = buyer.mobile,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (buyer.address.isNotBlank()) {
                        Text(
                            text = buyer.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (buyer.gstNo.isNotBlank()) {
                        Text(
                            text = "GST: ${buyer.gstNo}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 4.dp)
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            shape = AppCorners.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.high)
        ) {
            Column(
                modifier = Modifier
                    .padding(AppSpacing.xl)
                    .fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = AppSpacing.md)
                )

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Buyer Name *") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacing.md)
                        .testTag("form_buyer_name"),
                    shape = AppCorners.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Mobiles Column
                Text(
                    text = "Mobile Numbers",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = AppSpacing.xs)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
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
                                textStyle = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("form_buyer_mobile_$index"),
                                shape = AppCorners.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (index == 0) {
                                IconButton(
                                    onClick = { mobiles.add("") },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Mobile",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { mobiles.removeAt(index) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Remove Mobile",
                                        tint = MaterialTheme.colorScheme.error
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
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacing.md)
                        .testTag("form_buyer_address"),
                    shape = AppCorners.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // GST No
                OutlinedTextField(
                    value = gst,
                    onValueChange = onGstChange,
                    label = { Text("GST Number (Optional)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacing.lg)
                        .testTag("form_buyer_gst"),
                    shape = AppCorners.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EnterpriseOutlinedButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    EnterprisePrimaryButton(
                        text = "Save Changes",
                        onClick = onSave,
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}
