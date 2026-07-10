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
import com.example.data.FirebaseSeller
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Seller Master List",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
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
                    formMobile = ""
                    formPlace = ""
                    formMillName = ""
                    formAddress = ""
                    formGst = ""
                    showAddDialog = true
                },
                containerColor = Color(0xFF322659),
                contentColor = Color.White,
                shape = CircleShape,
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
                .background(Color(0xFFF9F9FB)) // Premium White/Off-White Background
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Seller Name...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("seller_search_input"),
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
                                .size(100.dp)
                                .padding(bottom = 16.dp),
                            tint = Color(0xFFCBC8D6)
                        )
                        Text(
                            text = "No Sellers Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF322659)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add sellers to start tracking transactions.",
                            fontSize = 14.sp,
                            color = Color.Gray
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF322659)),
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
    // Delete Ledger Dialog
    if (showDeleteDialog && selectedSellerForDelete != null) {
        val seller = selectedSellerForDelete!!
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
                                viewModel.deleteSellerLedger(
                                    sellerName = seller.sellerName,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
            .clickable { onCardClick() }
            .testTag("seller_card_${seller.sellerId}"),
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
                val initialLetter = seller.sellerName.firstOrNull()?.toString() ?: "S"
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
                        text = seller.sellerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF2D3748),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (seller.address.isNotBlank()) {
                        Text(
                            text = seller.address,
                            fontSize = 11.sp,
                            color = Color.Gray,
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
                        text = { Text("🗑 Delete Ledger") },
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
                    label = { Text("Seller Name *") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("form_seller_name"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF322659),
                        focusedLabelColor = Color(0xFF322659),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    )
                )

                // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = { Text("Address") },
                    maxLines = 4,
                    textStyle = LocalTextStyle.current.copy(color = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .testTag("form_seller_address"),
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
