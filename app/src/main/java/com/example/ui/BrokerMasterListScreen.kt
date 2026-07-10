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
import com.example.data.FirebaseBroker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Broker Master List",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
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
                    showAddDialog = true
                },
                containerColor = Color(0xFF322659),
                contentColor = Color.White,
                shape = CircleShape,
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
                .background(Color(0xFFF9F9FB)) // Premium White/Off-White Background
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Broker Name...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("broker_search_input"),
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
                                .size(100.dp)
                                .padding(bottom = 16.dp),
                            tint = Color(0xFFCBC8D6)
                        )
                        Text(
                            text = "No Brokers Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF322659)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add brokers to start tracking transactions.",
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
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF322659)),
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
                                viewModel.deleteBrokerLedger(
                                    brokerName = broker.brokerName,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
            .clickable { onCardClick() }
            .testTag("broker_card_${broker.brokerId}"),
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
                val initialLetter = broker.brokerName.firstOrNull()?.toString() ?: "B"
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
                        text = broker.brokerName,
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

                    if (broker.mobile.isNotBlank()) {
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
                                text = broker.mobile,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    if (broker.address.isNotBlank()) {
                        Text(
                            text = broker.address,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
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
                    modifier = Modifier.testTag("broker_card_menu_${broker.brokerId}")
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
                    label = { Text("Broker Name *") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("form_broker_name"),
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
                                    .testTag("form_broker_mobile_$index"),
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
                        .padding(bottom = 20.dp)
                        .testTag("form_broker_address"),
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
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                "Select Broker",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color.White
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
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF322659)
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
                        containerColor = Color(0xFF322659),
                        contentColor = Color.White,
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
                        .background(Color(0xFFF7FAFC))
                ) {
                    // Search Bar
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search Icon",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search Broker Name or Mobile...", fontSize = 14.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("broker_selection_search_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent,
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black
                                ),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.Black)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Search",
                                        tint = Color.Gray
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
                                    tint = Color.Gray.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (searchQuery.isEmpty()) "No Brokers Found" else "No matching brokers found",
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
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
                }

                // Delete Dialog
                if (showDeleteDialog) {
                    val brokerToDelete = selectedBrokerForDelete
                    if (brokerToDelete != null) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("Delete all ledger records for this party?", fontWeight = FontWeight.Bold, color = Color(0xFF322659)) },
                            text = { Text("This will only delete ledger transactions.\nThe Master List will remain unchanged.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        com.example.util.BiometricHelper.runWithBiometric(
                                            context = context,
                                            title = "Ranisa Security",
                                            subtitle = "Verify your fingerprint to continue.",
                                            action = {
                                                viewModel.deleteBrokerLedger(
                                                    brokerName = brokerToDelete.brokerName,
                                                    onSuccess = {
                                                        Toast.makeText(context, "Ledger transactions deleted successfully", Toast.LENGTH_SHORT).show()
                                                        showDeleteDialog = false
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                ) {
                                    Text("Delete", color = Color.White)
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
        }
    }
}

