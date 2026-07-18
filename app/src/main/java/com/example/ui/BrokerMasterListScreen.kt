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
import com.example.data.FirebaseBroker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerMasterListScreen(
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
    var selectedBrokerForEdit by remember { mutableStateOf<FirebaseBroker?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedBrokerForDelete by remember { mutableStateOf<FirebaseBroker?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    
    // Share sheet states
    var showShareSheet by remember { mutableStateOf(false) }
    var ledgerOwnerForShare by remember { mutableStateOf<String?>(null) }
    
    // Form States
    var formName by remember { mutableStateOf("") }
    val formMobiles = remember { mutableStateListOf<String>() }
    var formAddress by remember { mutableStateOf("") }
    
    // Brokers from database
    val brokersList by viewModel.rtdbFullBrokers.collectAsState()
    val bills by viewModel.allBills.collectAsState()
    val payments by viewModel.allPayments.collectAsState()
    
    // Filtered Brokers
    val filteredBrokers = remember(searchQuery, brokersList) {
        brokersList
            .distinctBy { it.brokerName.trim().lowercase() }
            .filter { broker ->
                broker.brokerName.contains(searchQuery, ignoreCase = true) ||
                broker.mobile.contains(searchQuery, ignoreCase = true) ||
                broker.address.contains(searchQuery, ignoreCase = true)
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
                        "Broker Master List",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.testTag("broker_master_menu_button")
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
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("add_broker_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add New Broker",
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
                placeholder = "Search Broker Name...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onClear = { searchQuery = "" },
                testTag = "broker_search_input"
            )

            // Content
            if (filteredBrokers.isEmpty()) {
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
                            contentDescription = "No brokers",
                            modifier = Modifier
                                .size(96.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "No Brokers Found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add brokers to start tracking transactions.",
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
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Broker")
                        }
                    }
                }
            } else {
                // Broker Master list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredBrokers, key = { it.brokerId }) { broker ->
                        val brokerBills = bills.filter { it.brokerName == broker.brokerName }
                        val totalQtls = brokerBills.sumOf { it.quintals }
                        BrokerCard(
                            broker = broker,
                            billCount = brokerBills.size,
                            totalQtls = totalQtls,
                            onEdit = {
                                selectedBrokerForEdit = broker
                                formName = broker.brokerName
                                formMobiles.clear()
                                val list = broker.mobile.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                if (list.isEmpty()) {
                                    formMobiles.add("")
                                } else {
                                    formMobiles.addAll(list)
                                }
                                formAddress = broker.address
                                showEditDialog = true
                            },
                            onShare = {
                                ledgerOwnerForShare = broker.brokerName
                                showShareSheet = true
                            },
                            onDeleteLedger = {
                                selectedBrokerForDelete = broker
                                showDeleteDialog = true
                            },
                            onCardClick = {
                                // Broker-specific navigation can go to ledger if desired or stay simple
                                // Let's keep the click behavior same but adaptive (no separate broker ledger required, but clicking can show info or go to home)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Broker Dialog
    if (showAddDialog) {
        BrokerMasterFormDialog(
            title = "Add Broker Master",
            name = formName,
            onNameChange = { formName = it },
            mobiles = formMobiles,
            address = formAddress,
            onAddressChange = { formAddress = it },
            onDismiss = { showAddDialog = false },
            onSave = {
                if (formName.isBlank()) {
                    Toast.makeText(context, "Please enter Broker Name", Toast.LENGTH_SHORT).show()
                } else {
                    val joinedMobile = formMobiles.filter { it.isNotBlank() }.joinToString(", ")
                    viewModel.addBroker(
                        brokerName = formName,
                        mobile = joinedMobile,
                        address = formAddress,
                        onSuccess = {
                            showAddDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Broker Added Successfully")
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

    // Edit Broker Dialog
    if (showEditDialog && selectedBrokerForEdit != null) {
        val broker = selectedBrokerForEdit!!
        BrokerMasterFormDialog(
            title = "Edit Broker Details",
            name = formName,
            onNameChange = { formName = it },
            mobiles = formMobiles,
            address = formAddress,
            onAddressChange = { formAddress = it },
            onDismiss = { showEditDialog = false },
            onSave = {
                if (formName.isBlank()) {
                    Toast.makeText(context, "Please enter Broker Name", Toast.LENGTH_SHORT).show()
                } else {
                    val joinedMobile = formMobiles.filter { it.isNotBlank() }.joinToString(", ")
                    viewModel.updateBroker(
                        brokerId = broker.brokerId,
                        brokerName = formName,
                        mobile = joinedMobile,
                        address = formAddress,
                        onSuccess = {
                            showEditDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Broker Updated Successfully")
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
    if (showDeleteDialog && selectedBrokerForDelete != null) {
        val broker = selectedBrokerForDelete!!
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
                        viewModel.deleteBrokerLedger(
                            brokerId = broker.brokerId,
                            brokerName = broker.brokerName,
                            onSuccess = { msg ->
                                showDeleteDialog = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg)
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
            bills.filter { it.brokerName == ownerName }
        }
        FullLedgerShareSheet(
            ledgerName = ownerName,
            ledgerType = "broker",
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
fun BrokerCard(
    broker: FirebaseBroker,
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
            .testTag("broker_card_${broker.brokerId}"),
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
                val initialLetter = broker.brokerName.firstOrNull()?.toString() ?: "B"
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
                        text = broker.brokerName,
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

                    if (broker.mobile.isNotBlank()) {
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
                                text = broker.mobile,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (broker.address.isNotBlank()) {
                        Text(
                            text = broker.address,
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
                    modifier = Modifier.testTag("broker_card_menu_${broker.brokerId}")
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
                        modifier = Modifier.testTag("broker_card_edit_${broker.brokerId}")
                    )
                    DropdownMenuItem(
                        text = { Text("📤 Share Ledger") },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        },
                        modifier = Modifier.testTag("broker_card_share_${broker.brokerId}")
                    )
                    DropdownMenuItem(
                        text = { Text("🗑 Delete Ledger") },
                        onClick = {
                            menuExpanded = false
                            onDeleteLedger()
                        },
                        modifier = Modifier.testTag("broker_card_delete_ledger_${broker.brokerId}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerMasterFormDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    mobiles: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
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
                    label = { Text("Broker Name *") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacing.md)
                        .testTag("form_broker_name"),
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
                                    .testTag("form_broker_mobile_$index"),
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
                        .padding(bottom = AppSpacing.lg)
                        .testTag("form_broker_address"),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerSelectionDialog(
    viewModel: RanisaViewModel,
    onDismiss: () -> Unit,
    onBrokerSelected: (FirebaseBroker) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedBrokerForEdit by remember { mutableStateOf<FirebaseBroker?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedBrokerForDelete by remember { mutableStateOf<FirebaseBroker?>(null) }
    
    // Share sheet states
    var showShareSheet by remember { mutableStateOf(false) }
    var ledgerOwnerForShare by remember { mutableStateOf<String?>(null) }
    
    // Form States
    var formName by remember { mutableStateOf("") }
    val formMobiles = remember { mutableStateListOf<String>() }
    var formAddress by remember { mutableStateOf("") }
    
    // Brokers from database
    val brokersList by viewModel.rtdbFullBrokers.collectAsState()
    val bills by viewModel.allBills.collectAsState()
    val payments by viewModel.allPayments.collectAsState()
    
    // Filtered Brokers
    val filteredBrokers = remember(searchQuery, brokersList) {
        brokersList
            .distinctBy { it.brokerName.trim().lowercase() }
            .filter { broker ->
                broker.brokerName.contains(searchQuery, ignoreCase = true) ||
                broker.mobile.contains(searchQuery, ignoreCase = true)
            }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
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
                                "Select Broker",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("broker_selection_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onPrimary
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
                            showAddDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("broker_selection_add_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add New Broker")
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Search Bar
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search Icon",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search Broker Name or Mobile...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("broker_selection_search_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (filteredBrokers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (searchQuery.isEmpty()) "No Brokers Found" else "No matching brokers found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("broker_selection_list"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredBrokers) { broker ->
                                val brokerBills = bills.filter { it.brokerName.equals(broker.brokerName, ignoreCase = true) }
                                val billCount = brokerBills.size
                                val totalQtls = brokerBills.sumOf { it.quintals }

                                BrokerCard(
                                    broker = broker,
                                    billCount = billCount,
                                    totalQtls = totalQtls,
                                    onEdit = {
                                        selectedBrokerForEdit = broker
                                        formName = broker.brokerName
                                        formMobiles.clear()
                                        if (broker.mobile.isNotBlank()) {
                                            formMobiles.addAll(broker.mobile.split(",").map { it.trim() })
                                        } else {
                                            formMobiles.add("")
                                        }
                                        formAddress = broker.address
                                        showEditDialog = true
                                    },
                                    onShare = {
                                        ledgerOwnerForShare = broker.brokerName
                                        showShareSheet = true
                                    },
                                    onDeleteLedger = {
                                        selectedBrokerForDelete = broker
                                        showDeleteDialog = true
                                    },
                                    onCardClick = {
                                        onBrokerSelected(broker)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }

                // Add Dialog
                if (showAddDialog) {
                    BrokerMasterFormDialog(
                        title = "Add Broker Master",
                        name = formName,
                        onNameChange = { formName = it },
                        mobiles = formMobiles,
                        address = formAddress,
                        onAddressChange = { formAddress = it },
                        onDismiss = { showAddDialog = false },
                        onSave = {
                            if (formName.isBlank()) {
                                Toast.makeText(context, "Please enter Broker Name", Toast.LENGTH_SHORT).show()
                            } else {
                                val joinedMobile = formMobiles.filter { it.isNotBlank() }.joinToString(",")
                                viewModel.addBroker(
                                    brokerName = formName.trim(),
                                    mobile = joinedMobile,
                                    address = formAddress.trim(),
                                    onSuccess = {
                                        Toast.makeText(context, "Broker Added Successfully", Toast.LENGTH_SHORT).show()
                                        
                                        // Auto-select the newly created broker
                                        val newlyCreated = viewModel.rtdbFullBrokers.value.find { 
                                            it.brokerName.trim().equals(formName.trim(), ignoreCase = true) 
                                        } ?: FirebaseBroker(
                                            brokerId = java.util.UUID.randomUUID().toString(),
                                            brokerName = formName.trim(),
                                            mobile = joinedMobile,
                                            address = formAddress.trim()
                                        )
                                        onBrokerSelected(newlyCreated)
                                        showAddDialog = false
                                        onDismiss()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    )
                }

                // Edit Dialog
                if (showEditDialog) {
                    val brokerToEdit = selectedBrokerForEdit
                    if (brokerToEdit != null) {
                        BrokerMasterFormDialog(
                            title = "Edit Broker Master",
                            name = formName,
                            onNameChange = { formName = it },
                            mobiles = formMobiles,
                            address = formAddress,
                            onAddressChange = { formAddress = it },
                            onDismiss = { showEditDialog = false },
                            onSave = {
                                if (formName.isBlank()) {
                                    Toast.makeText(context, "Please enter Broker Name", Toast.LENGTH_SHORT).show()
                                } else {
                                    val joinedMobile = formMobiles.filter { it.isNotBlank() }.joinToString(",")
                                    viewModel.updateBroker(
                                        brokerId = brokerToEdit.brokerId,
                                        brokerName = formName.trim(),
                                        mobile = joinedMobile,
                                        address = formAddress.trim(),
                                        onSuccess = {
                                            Toast.makeText(context, "Broker Updated Successfully", Toast.LENGTH_SHORT).show()
                                            showEditDialog = false
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            }
                        )
                    }
                         // Delete Dialog
                if (showDeleteDialog) {
                    val brokerToDelete = selectedBrokerForDelete
                    if (brokerToDelete != null) {
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
                                        viewModel.deleteBrokerLedger(
                                            brokerId = brokerToDelete.brokerId,
                                            brokerName = brokerToDelete.brokerName,
                                            onSuccess = { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                showDeleteDialog = false
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
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
                }         }

                // Share Ledger Bottom Sheet
                if (showShareSheet && ledgerOwnerForShare != null) {
                    val ownerName = ledgerOwnerForShare!!
                    val filteredBillsForOwner = remember(bills, ownerName) {
                        bills.filter { it.brokerName == ownerName }
                    }
                    FullLedgerShareSheet(
                        ledgerName = ownerName,
                        ledgerType = "broker",
                        bills = filteredBillsForOwner,
                        payments = payments,
                        onDismissRequest = {
                            showShareSheet = false
                            ledgerOwnerForShare = null
                        }
                    )
                }
            }
        }
    }
}

