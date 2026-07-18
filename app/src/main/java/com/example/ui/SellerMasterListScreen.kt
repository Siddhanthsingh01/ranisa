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
import com.example.data.FirebaseSeller
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerMasterListScreen(
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
    var selectedSellerForEdit by remember { mutableStateOf<FirebaseSeller?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedSellerForDelete by remember { mutableStateOf<FirebaseSeller?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    
    // Share sheet states
    var showShareSheet by remember { mutableStateOf(false) }
    var ledgerOwnerForShare by remember { mutableStateOf<String?>(null) }
    
    // Form States
    var formName by remember { mutableStateOf("") }
    var formMobile by remember { mutableStateOf("") }
    var formPlace by remember { mutableStateOf("") }
    var formMillName by remember { mutableStateOf("") }
    var formAddress by remember { mutableStateOf("") }
    var formGst by remember { mutableStateOf("") }
    
    // Sellers, bills, and payments from database
    val sellersList by viewModel.rtdbFullSellers.collectAsState()
    val bills by viewModel.allBills.collectAsState()
    val payments by viewModel.allPayments.collectAsState()
    
    // Filtered Sellers
    val filteredSellers = remember(searchQuery, sellersList) {
        sellersList
            .distinctBy { it.sellerName.trim().lowercase() }
            .filter { seller ->
                seller.sellerName.contains(searchQuery, ignoreCase = true) ||
                seller.address.contains(searchQuery, ignoreCase = true)
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
                        "Seller Master List",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.testTag("seller_master_menu_button")
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
                    formMobile = ""
                    formPlace = ""
                    formMillName = ""
                    formAddress = ""
                    formGst = ""
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("add_seller_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add New Seller",
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
                placeholder = "Search Seller Name...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onClear = { searchQuery = "" },
                testTag = "seller_search_input"
            )

            // Content
            if (filteredSellers.isEmpty()) {
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
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "No sellers",
                            modifier = Modifier
                                .size(96.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "No Sellers Found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add sellers to start tracking transactions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                formName = ""
                                formMobile = ""
                                formPlace = ""
                                formMillName = ""
                                formAddress = ""
                                formGst = ""
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Seller")
                        }
                    }
                }
            } else {
                // Seller Master list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredSellers, key = { it.sellerId }) { seller ->
                        SellerCard(
                            seller = seller,
                            onEdit = {
                                selectedSellerForEdit = seller
                                formName = seller.sellerName
                                formAddress = seller.address
                                showEditDialog = true
                            },
                            onShare = {
                                ledgerOwnerForShare = seller.sellerName
                                showShareSheet = true
                            },
                            onDeleteLedger = {
                                selectedSellerForDelete = seller
                                showDeleteDialog = true
                            },
                            onCardClick = {
                                navController.navigate("seller_ledger?preselectedSeller=${seller.sellerName}")
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Seller Dialog
    if (showAddDialog) {
        SellerFormDialog(
            title = "Add Seller Master",
            name = formName,
            onNameChange = { formName = it },
            address = formAddress,
            onAddressChange = { formAddress = it },
            onDismiss = { showAddDialog = false },
            onSave = {
                if (formName.isBlank()) {
                    Toast.makeText(context, "Please enter Seller Name", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addSeller(
                        sellerName = formName,
                        mobile = "",
                        place = "",
                        gstNo = "",
                        millName = "",
                        address = formAddress,
                        onSuccess = {
                            showAddDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Seller Added Successfully")
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

    // Edit Seller Dialog
    if (showEditDialog && selectedSellerForEdit != null) {
        val seller = selectedSellerForEdit!!
        SellerFormDialog(
            title = "Edit Seller Details",
            name = formName,
            onNameChange = { formName = it },
            address = formAddress,
            onAddressChange = { formAddress = it },
            onDismiss = { showEditDialog = false },
            onSave = {
                if (formName.isBlank()) {
                    Toast.makeText(context, "Please enter Seller Name", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.updateSeller(
                        sellerId = seller.sellerId,
                        sellerName = formName,
                        mobile = "",
                        place = "",
                        gstNo = "",
                        millName = "",
                        address = formAddress,
                        onSuccess = {
                            showEditDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Seller Updated Successfully")
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
    if (showDeleteDialog && selectedSellerForDelete != null) {
        val seller = selectedSellerForDelete!!
        EnterpriseDialog(
            title = "Delete Seller Master Record?",
            description = "Are you sure you want to delete the master record for '${seller.sellerName}'? This action cannot be undone.",
            confirmText = "Delete",
            dismissText = "Cancel",
            onConfirm = {
                com.example.util.BiometricHelper.runWithBiometric(
                    context = context,
                    title = "Ranisa Security",
                    subtitle = "Verify your fingerprint to continue.",
                    action = {
                        viewModel.deleteSeller(
                            sellerId = seller.sellerId,
                            sellerName = seller.sellerName,
                            onSuccess = {
                                showDeleteDialog = false
                                scope.launch {
                                    snackbarHostState.showSnackbar("Deleted Successfully")
                                }
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
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
            bills.filter { it.sellerName == ownerName }
        }
        FullLedgerShareSheet(
            ledgerName = ownerName,
            ledgerType = "seller",
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
fun SellerCard(
    seller: FirebaseSeller,
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
            .testTag("seller_card_${seller.sellerId}"),
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
                val initialLetter = seller.sellerName.firstOrNull()?.toString() ?: "S"
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
                        text = seller.sellerName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (seller.address.isNotBlank()) {
                        Text(
                            text = seller.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
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
                    modifier = Modifier.testTag("seller_card_menu_${seller.sellerId}")
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
                        modifier = Modifier.testTag("seller_card_edit_${seller.sellerId}")
                    )
                    DropdownMenuItem(
                        text = { Text("📤 Share Ledger") },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        },
                        modifier = Modifier.testTag("seller_card_share_${seller.sellerId}")
                    )
                    DropdownMenuItem(
                        text = { Text("🗑 Delete Seller") },
                        onClick = {
                            menuExpanded = false
                            onDeleteLedger()
                        },
                        modifier = Modifier.testTag("seller_card_delete_ledger_${seller.sellerId}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerFormDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
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
                    label = { Text("Seller Name *") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacing.md)
                        .testTag("form_seller_name"),
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

                // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = { Text("Address") },
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacing.lg)
                        .testTag("form_seller_address"),
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
