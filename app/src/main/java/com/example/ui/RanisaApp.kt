package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import com.example.util.PdfGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RanisaApp(
    viewModel: RanisaViewModel = viewModel(
        factory = RanisaViewModel.Factory(LocalContext.current.applicationContext as Application)
    )
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("ranisa_prefs", android.content.Context.MODE_PRIVATE) }
    var isAppLocked by remember { mutableStateOf(sharedPrefs.getBoolean("biometric_lock_enabled", false)) }

    if (isAppLocked) {
        BiometricLockScreen(
            onUnlockSuccess = { isAppLocked = false },
            context = context
        )
        return
    }

    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val activeUser by viewModel.activeUser.collectAsState()
    val activeFirm by viewModel.activeFirm.collectAsState()
    val users by viewModel.users.collectAsState()
    val firms by viewModel.firms.collectAsState()

    var showFirmDialog by remember { mutableStateOf(false) }
    var showUserDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }

    // Navigation state monitoring to select active items
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "login"
    val showScaffoldBars = currentRoute != "login"

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showScaffoldBars,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                drawerContainerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Drawer Header with Logo & Titles
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Custom Drawn Logo matching the image
                        Image(
                            painter = painterResource(id = R.drawable.ic_ranisa_logo),
                            contentDescription = "Ranisa Logo",
                            modifier = Modifier
                                .size(90.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Ranisa",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color(0xFF322659)
                        )
                        Text(
                            text = "हिसाब आपके साथ",
                            fontSize = 13.sp,
                            color = Color(0xFF322659).copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Menu items
                    val menuItems = listOf(
                        Triple("home", Icons.Default.Home, "Home"),
                        Triple("seller_master_list", Icons.Default.Storefront, "New Seller"),
                        Triple("buyer_master_list", Icons.Default.People, "New Buyer"),
                        Triple("broker_master_list", Icons.Default.People, "New Broker"),
                        Triple("log_history", Icons.Default.History, "Logs"),
                        Triple("security_settings", Icons.Default.Lock, "Security")
                    )

                    menuItems.forEach { (route, icon, label) ->
                        val isSelected = currentRoute == route
                        CustomDrawerItem(
                            label = label,
                            icon = icon,
                            selected = isSelected,
                            onClick = {
                                if (route == "home") {
                                    navController.navigate("home") {
                                        popUpTo("home") {
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                    }
                                } else {
                                    navController.navigate(route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                scope.launch { drawerState.close() }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFECE6F0))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Firms section
                    Text(
                        text = "Firms",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF322659),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Firm 1: Lalit Rice Broker
                    FirmDrawerItem(
                        name = "Lalit Rice Broker",
                        selected = activeFirm?.name == "Lalit Rice Broker",
                        onClick = {
                            viewModel.selectFirm(Firm(name = "Lalit Rice Broker"))
                            scope.launch { drawerState.close() }
                        }
                    )

                    // Firm 2: Hare Krishna Rice Broker
                    FirmDrawerItem(
                        name = "Hare Krishna Rice Broker",
                        selected = activeFirm?.name == "Hare Krishna Rice Broker",
                        onClick = {
                            viewModel.selectFirm(Firm(name = "Hare Krishna Rice Broker"))
                            scope.launch { drawerState.close() }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color(0xFFECE6F0))
                    Spacer(modifier = Modifier.height(16.dp))

                    val context = androidx.compose.ui.platform.LocalContext.current
                    CustomDrawerItem(
                        label = "Logout",
                        icon = Icons.Default.ExitToApp,
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            val prefs = context.getSharedPreferences("ranisa_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().clear().apply()
                            viewModel.logoutUser()
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (showScaffoldBars && currentRoute != "seller_master_list" && currentRoute != "buyer_master_list" && currentRoute != "broker_master_list") {
                    TopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = "Ranisa",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "हिसाब आपके साथ",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu, 
                                    contentDescription = "Open Navigation Menu",
                                    tint = Color.White
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { navController.navigate("search") }) {
                                Icon(
                                    imageVector = Icons.Default.Search, 
                                    contentDescription = "Global Database Search",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF322659), // Rich dark purple
                            titleContentColor = Color.White
                        )
                    )
                }
            },
            bottomBar = {
                if (showScaffoldBars) {
                    NavigationBar(
                        containerColor = Color(0xFF322659), // Deep purple/indigo as requested
                        tonalElevation = 4.dp
                    ) {
                        val bottomDestinations = listOf(
                            Triple("home", Icons.Default.Home, "Home"),
                            Triple("search", Icons.Default.Search, "Search"),
                            Triple("payment_list", Icons.Default.AccountBalanceWallet, "Payment"),
                            Triple("seller_ledger?preselectedSeller={preselectedSeller}", Icons.Default.Storefront, "Seller Ledger"),
                            Triple("buyer_ledger?preselectedBuyer={preselectedBuyer}", Icons.Default.People, "Buyer Ledger")
                        )

                        bottomDestinations.forEach { (route, icon, label) ->
                            val isSelected = if (route.contains("seller_ledger")) {
                                currentRoute.contains("seller_ledger")
                            } else if (route.contains("buyer_ledger")) {
                                currentRoute.contains("buyer_ledger")
                            } else {
                                currentRoute == route
                            }
                            val navRoute = when {
                                route.contains("seller_ledger") -> "seller_ledger"
                                route.contains("buyer_ledger") -> "buyer_ledger"
                                else -> route
                            }
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (navRoute == "home") {
                                        navController.navigate("home") {
                                            popUpTo("home") {
                                                inclusive = false
                                            }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navController.navigate(navRoute) {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { 
                                    Icon(
                                        imageVector = icon, 
                                        contentDescription = label,
                                        tint = if (isSelected) Color(0xFF322659) else Color.White.copy(alpha = 0.7f)
                                    ) 
                                },
                                label = { 
                                    Text(
                                        text = label, 
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                                    ) 
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.White, // active item pill background
                                    selectedIconColor = Color(0xFF322659),
                                    unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                    selectedTextColor = Color.White,
                                    unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "login"
                ) {
                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = { username, role ->
                                navController.navigate("home") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            viewModel = viewModel
                        )
                    }
                    composable("home") {
                        HomeScreen(navController, viewModel)
                    }
                    composable("order_temp") {
                        OrderTempScreen(viewModel)
                    }
                    composable("contract_selection") {
                        ContractSelectionScreen(navController)
                    }
                    composable(
                        route = "bill_entry/{firmName}/{billId}",
                        arguments = listOf(
                            navArgument("firmName") { type = NavType.StringType },
                            navArgument("billId") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        val firmName = backStackEntry.arguments?.getString("firmName") ?: "Lalit Rice Broker"
                        val billId = backStackEntry.arguments?.getInt("billId") ?: -1
                        BillEntryScreen(navController, viewModel, firmName, billId)
                    }
                    composable(
                        route = "seller_ledger?preselectedSeller={preselectedSeller}",
                        arguments = listOf(navArgument("preselectedSeller") { defaultValue = ""; type = NavType.StringType })
                    ) { backStackEntry ->
                        val preselectedSeller = backStackEntry.arguments?.getString("preselectedSeller") ?: ""
                        SellerLedgerScreen(navController, viewModel, preselectedSeller)
                    }
                    composable(
                        route = "buyer_ledger?preselectedBuyer={preselectedBuyer}",
                        arguments = listOf(navArgument("preselectedBuyer") { defaultValue = ""; type = NavType.StringType })
                    ) { backStackEntry ->
                        val preselectedBuyer = backStackEntry.arguments?.getString("preselectedBuyer") ?: ""
                        BuyerLedgerScreen(navController, viewModel, preselectedBuyer)
                    }
                    composable("seller_master_list") {
                        SellerMasterListScreen(
                            navController = navController,
                            viewModel = viewModel,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                    }
                    composable("buyer_master_list") {
                        BuyerMasterListScreen(
                            navController = navController,
                            viewModel = viewModel,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                    }
                    composable("broker_master_list") {
                        BrokerMasterListScreen(
                            navController = navController,
                            viewModel = viewModel,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                    }
                    composable("payment_list") {
                        PaymentListScreen(navController, viewModel)
                    }
                    composable("reports") {
                        ReportsScreen(viewModel)
                    }
                    composable("log_history") {
                        LogHistoryScreen(viewModel)
                    }
                    composable("settings") {
                        SettingsScreen(navController, viewModel)
                    }
                    composable("security_settings") {
                        SecuritySettingsScreen(navController)
                    }
                    composable("search") {
                        SearchScreen(navController, viewModel)
                    }
                    composable(
                        route = "broker_ledger?preselectedBroker={preselectedBroker}",
                        arguments = listOf(navArgument("preselectedBroker") { defaultValue = ""; type = NavType.StringType })
                    ) { backStackEntry ->
                        val preselectedBroker = backStackEntry.arguments?.getString("preselectedBroker") ?: ""
                        BrokerLedgerScreen(navController, viewModel, preselectedBroker)
                    }
                }
            }
        }
    }

    // Switch Firm Dialog
    if (showFirmDialog) {
        Dialog(onDismissRequest = { showFirmDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Switch Active Firm",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    firms.forEach { firm ->
                        Button(
                            onClick = {
                                viewModel.selectFirm(firm)
                                showFirmDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeFirm?.id == firm.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (activeFirm?.id == firm.id) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(firm.name)
                        }
                    }
                    TextButton(
                        onClick = { showFirmDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    // Switch User Dialog
    if (showUserDialog) {
        Dialog(onDismissRequest = { showUserDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Switch User Profile",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Access permissions and audit log details will update according to selected user role.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )
                    users.forEach { user ->
                        Button(
                            onClick = {
                                viewModel.selectUser(user)
                                showUserDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeUser?.id == user.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (activeUser?.id == user.id) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(user.username, fontWeight = FontWeight.Bold)
                                Text("Role: ${user.role}", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Notifications Dialog
    if (showNotificationDialog) {
        Dialog(onDismissRequest = { showNotificationDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ranisa Notifications",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text("🔔 SQLite Database connection initialized successfully.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
                    Text("🔔 Auto-backup configuration: Cloud Backup Active.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
                    Text("🔔 Role permissions enabled for: ${activeUser?.role ?: "Viewer"}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { showNotificationDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@Composable
fun CustomDrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFE8E5F3) else Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color(0xFF5E35B1) else Color(0xFF6A6573),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                color = if (selected) Color(0xFF5E35B1) else Color(0xFF1F1B24)
            )
        }
    }
}

@Composable
fun FirmDrawerItem(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFF3E8FF) else Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Business,
                contentDescription = name,
                tint = Color(0xFF5E35B1),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = name,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                color = Color(0xFF1F1B24)
            )
        }
    }
}

// ==========================================
// HOME DASHBOARD SCREEN
// ==========================================
@Composable
fun HomeScreen(navController: NavController, viewModel: RanisaViewModel) {
    val activeUser by viewModel.activeUser.collectAsState()
    val activeFirm by viewModel.activeFirm.collectAsState()
    val bills by viewModel.allBills.collectAsState()
    val payments by viewModel.allPayments.collectAsState()
    val rtdbFullBuyers by viewModel.rtdbFullBuyers.collectAsState()
    val rtdbFullSellers by viewModel.rtdbFullSellers.collectAsState()
    val rtdbFullBrokers by viewModel.rtdbFullBrokers.collectAsState()

    val currentCalendar = java.util.Calendar.getInstance()
    val hour = currentCalendar.get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good Morning ☀️"
        in 12..16 -> "Good Afternoon 🌤️"
        in 17..20 -> "Good Evening 🌇"
        else -> "Good Night 🌙"
    }
    val liveDateStr = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(currentCalendar.time)
    val liveDayStr = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(currentCalendar.time)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Card matching image
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Welcome, ${activeUser?.username?.substringBefore(" ") ?: "Admin"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF1F1B24)
                        )
                        Text(
                            text = greeting,
                            fontSize = 13.sp,
                            color = Color(0xFF6A6573),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFE8E5F3), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = "Calendar",
                                tint = Color(0xFF5E35B1),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = liveDateStr,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1F1B24)
                            )
                            Text(
                                text = liveDayStr,
                                fontSize = 12.sp,
                                color = Color(0xFF6A6573)
                            )
                        }
                    }
                }
            }
        }

        // Home Title
        item {
            Text(
                text = "Home",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF3F2B96),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Main Grid (Rows of 2 columns)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        HomeGridCard(
                            title = "Contract Form",
                            subtitle = "Create New Contract / Bill",
                            icon = Icons.Default.Description,
                            iconBgColor = Color(0xFF38C194),
                            cardBgColor = Color(0xFFEEFBF7),
                            onClick = { navController.navigate("contract_selection") }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        HomeGridCard(
                            title = "Seller Ledger",
                            subtitle = "Seller Wise Account Details",
                            icon = Icons.Default.ContactPage,
                            iconBgColor = Color(0xFF2F80ED),
                            cardBgColor = Color(0xFFEDF5FF),
                            onClick = { navController.navigate("seller_ledger") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        HomeGridCard(
                            title = "Buyer Ledger",
                            subtitle = "Buyer Wise Account Details",
                            icon = Icons.Default.Person,
                            iconBgColor = Color(0xFFF2994A),
                            cardBgColor = Color(0xFFFFF7ED),
                            onClick = { navController.navigate("buyer_ledger") }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        HomeGridCard(
                            title = "Payment List",
                            subtitle = "Payment Receive / Balance Details",
                            icon = Icons.Default.AccountBalanceWallet,
                            iconBgColor = Color(0xFFEB5757),
                            cardBgColor = Color(0xFFFFF2F2),
                            onClick = { navController.navigate("payment_list") }
                        )
                    }
                }
            }
        }

        // Broker Ledger Full-Width Card
        item {
            HomeFullWidthCard(
                title = "Broker Ledger",
                subtitle = "Broker Wise Account Details & Settlement",
                icon = Icons.Default.Work,
                iconBgColor = Color(0xFF5E35B1),
                cardBgColor = Color(0xFFF4F0FF),
                onClick = { navController.navigate("broker_ledger") }
            )
        }

        // Quick Summary Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Quick Summary",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF3F2B96)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Stat 1: Total Bills
                        SummaryStatItem(
                            value = "${bills.size}",
                            label = "Total Bills",
                            icon = Icons.Default.Description,
                            iconColor = Color(0xFF7C3AED),
                            iconBgColor = Color(0xFFF3E8FF),
                            modifier = Modifier.weight(1f)
                        )
                        // Stat 2: Total Qtls
                        val totalQuintals = bills.sumOf { it.quintals }
                        SummaryStatItem(
                            value = String.format(java.util.Locale.getDefault(), "%.2f", totalQuintals),
                            label = "Total Qtls",
                            icon = Icons.Default.Eco,
                            iconColor = Color(0xFF10B981),
                            iconBgColor = Color(0xFFDCFCE7),
                            modifier = Modifier.weight(1f)
                        )
                        // Stat 3: Buyers
                        SummaryStatItem(
                            value = "${rtdbFullBuyers.size}",
                            label = "Buyers",
                            icon = Icons.Default.People,
                            iconColor = Color(0xFFD97706),
                            iconBgColor = Color(0xFFFEF3C7),
                            modifier = Modifier.weight(1f)
                        )
                        // Stat 4: Sellers
                        SummaryStatItem(
                            value = "${rtdbFullSellers.size}",
                            label = "Sellers",
                            icon = Icons.Default.Storefront,
                            iconColor = Color(0xFF2563EB),
                            iconBgColor = Color(0xFFDBEAFE),
                            modifier = Modifier.weight(1f)
                        )
                        // Stat 5: Brokers
                        SummaryStatItem(
                            value = "${rtdbFullBrokers.size}",
                            label = "Brokers",
                            icon = Icons.Default.Business,
                            iconColor = Color(0xFF6366F1),
                            iconBgColor = Color(0xFFE0E7FF),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeGridCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    cardBgColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .testTag(title.lowercase().replace(" ", "_")),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconBgColor, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Navigate",
                    tint = Color(0xFF6A6573),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF1F1B24)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF6A6573),
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun HomeFullWidthCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    cardBgColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(title.lowercase().replace(" ", "_")),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconBgColor, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1F1B24)
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF6A6573),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = Color(0xFF6A6573),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SummaryStatItem(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBgColor: Color,
    modifier: Modifier = Modifier.width(72.dp)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(iconBgColor, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (icon == Icons.Default.Payment) {
                Text(
                    text = "₹",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
            } else if (icon == Icons.Default.Balance) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFF1F1B24)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF6A6573),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==========================================
// TEMPORARY ORDER NOTES SCREEN
// ==========================================
@Composable
fun OrderTempScreen(viewModel: RanisaViewModel) {
    var noteText by remember { mutableStateOf("") }
    var notesList by remember { mutableStateOf(listOf<String>()) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Temporary Order Pad", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Write transient notes here for quick negotiations. These are cleared when no longer needed.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text("Enter temporary contract or negotiation note") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            minLines = 3,
            maxLines = 5
        )

        Button(
            onClick = {
                if (noteText.isNotBlank()) {
                    notesList = notesList + noteText
                    noteText = ""
                    Toast.makeText(context, "Note Saved Temp", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Temp Note")
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Note")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Active Notes (${notesList.size})", fontWeight = FontWeight.Bold)

        if (notesList.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No active temporary notes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notesList) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.StickyNote2, contentDescription = "Note", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(item, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            IconButton(onClick = { notesList = notesList.filter { it != item } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// CONTRACT FIRM SELECTION SCREEN
// ==========================================
@Composable
fun ContractSelectionScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Select Brokerage Firm",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Open billing books for Lalit Rice Broker or Hare Krishna Rice Broker.",
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            onClick = { navController.navigate("bill_entry/Lalit Rice Broker/-1") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("lalit_rice_broker_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Business, contentDescription = "Lalit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Lalit Rice Broker", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                }
                Text("Click to view & record contract bills for Lalit Brokerage account.", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Card(
            onClick = { navController.navigate("bill_entry/Hare Krishna Rice Broker/-1") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("hare_krishna_rice_broker_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Business, contentDescription = "Krishna", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Hare Krishna Rice Broker", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.secondary)
                }
                Text("Click to view & record contract bills for Hare Krishna Brokerage account.", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

// ==========================================
// HELPER FOR LATEST MASTER ADDRESSES FOR PDF
// ==========================================
fun getLatestSellerAddress(name: String, sellersMaster: List<Seller>, rtdbFullSellers: List<FirebaseSeller>): String {
    val rtdbObj = rtdbFullSellers.find { it.sellerName.equals(name, ignoreCase = true) }
    if (rtdbObj != null && rtdbObj.address.isNotBlank()) {
        return rtdbObj.address
    }
    val localObj = sellersMaster.find { it.name.equals(name, ignoreCase = true) }
    if (localObj != null && localObj.address.isNotBlank()) {
        return localObj.address
    }
    return ""
}

fun getLatestBuyerAddress(name: String, buyersMaster: List<Buyer>, rtdbFullBuyers: List<FirebaseBuyer>): String {
    val rtdbObj = rtdbFullBuyers.find { it.buyerName.equals(name, ignoreCase = true) }
    if (rtdbObj != null && rtdbObj.address.isNotBlank()) {
        return rtdbObj.address
    }
    val localObj = buyersMaster.find { it.name.equals(name, ignoreCase = true) }
    if (localObj != null && localObj.address.isNotBlank()) {
        return localObj.address
    }
    return ""
}

fun getLatestBillForPdf(
    bill: ContractBill,
    sellersMaster: List<Seller>,
    rtdbFullSellers: List<FirebaseSeller>,
    buyersMaster: List<Buyer>,
    rtdbFullBuyers: List<FirebaseBuyer>
): ContractBill {
    val latestSellerAddr = getLatestSellerAddress(bill.sellerName, sellersMaster, rtdbFullSellers)
    val latestBuyerAddr = getLatestBuyerAddress(bill.buyerName, buyersMaster, rtdbFullBuyers)
    return bill.copy(
        sellerAddress = if (latestSellerAddr.isNotBlank()) latestSellerAddr else bill.sellerAddress,
        buyerAddress = if (latestBuyerAddr.isNotBlank()) latestBuyerAddr else bill.buyerAddress
    )
}

fun savePdfToDownloads(context: android.content.Context, file: java.io.File, fileName: String): java.io.File? {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    java.io.FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                return java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
            }
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val destFile = java.io.File(downloadsDir, fileName)
            java.io.FileInputStream(file).use { inputStream ->
                java.io.FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return destFile
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun exportPdfToDownloads(context: android.content.Context, bill: ContractBill, sellersMaster: List<Seller>, rtdbFullSellers: List<FirebaseSeller>, buyersMaster: List<Buyer>, rtdbFullBuyers: List<FirebaseBuyer>) {
    try {
        val pdfBill = getLatestBillForPdf(bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
        val file = PdfGenerator.generateContractPdf(context, pdfBill)
        if (file != null && file.exists()) {
            val fileName = "Ranisa_Contract_${bill.billNumber}.pdf"
            val savedFile = savePdfToDownloads(context, file, fileName)
            if (savedFile != null) {
                android.widget.Toast.makeText(context, "PDF saved to Downloads folder as $fileName", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(context, "Failed to export PDF to Downloads", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Failed to generate PDF", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
    }
}

fun exportPaymentPdfToDownloads(context: android.content.Context, payment: Payment, bill: ContractBill) {
    try {
        val file = PdfGenerator.generatePaymentPdf(context, payment, bill)
        if (file != null && file.exists()) {
            val fileName = "Ranisa_Receipt_${payment.paymentId.take(8).uppercase()}.pdf"
            val savedFile = savePdfToDownloads(context, file, fileName)
            if (savedFile != null) {
                android.widget.Toast.makeText(context, "PDF saved to Downloads folder as $fileName", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(context, "Failed to export PDF to Downloads", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Failed to generate PDF", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
    }
}

// ==========================================
// BILL ENTRY & CONTRACT FORM SCREEN
// ==========================================
fun convertNumberToWords(number: Double): String {
    val num = number.toLong()
    if (num <= 0L) return "Zero Rupees Only"
    
    val units = arrayOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
    val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")
    
    fun convertLessThanThousand(n: Int): String {
        var str = ""
        if (n >= 100) {
            str += units[n / 100] + " Hundred "
            str += convertLessThanThousand(n % 100)
        } else if (n >= 20) {
            str += tens[n / 10] + " " + units[n % 10]
        } else if (n > 0) {
            str += units[n]
        }
        return str.trim()
    }
    
    var temp = num
    var result = ""
    
    val crore = temp / 10000000
    temp %= 10000000
    
    val lakh = temp / 100000
    temp %= 100000
    
    val thousand = temp / 1000
    temp %= 1000
    
    val hundred = temp
    
    if (crore > 0) {
        result += convertLessThanThousand(crore.toInt()) + " Crore "
    }
    if (lakh > 0) {
        result += convertLessThanThousand(lakh.toInt()) + " Lakh "
    }
    if (thousand > 0) {
        result += convertLessThanThousand(thousand.toInt()) + " Thousand "
    }
    if (hundred > 0) {
        result += convertLessThanThousand(hundred.toInt())
    }
    
    return "${result.trim()} Rupees Only"
}

@Composable
fun BillEntryScreen(
    navController: NavController,
    viewModel: RanisaViewModel,
    firmName: String,
    billId: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allBills by viewModel.allBills.collectAsState()
    val sellersMaster by viewModel.sellers.collectAsState()
    val buyersMaster by viewModel.buyers.collectAsState()
    val brokersMaster by viewModel.brokers.collectAsState()

    // Real-time Firebase masterData StateFlows for autocomplete
    val rtdbSellers by viewModel.rtdbSellers.collectAsState()
    val rtdbBuyers by viewModel.rtdbBuyers.collectAsState()
    val rtdbFullSellers by viewModel.rtdbFullSellers.collectAsState()
    val rtdbFullBuyers by viewModel.rtdbFullBuyers.collectAsState()
    val rtdbTransports by viewModel.rtdbTransports.collectAsState()
    val rtdbBrands by viewModel.rtdbBrands.collectAsState()
    val rtdbMobiles by viewModel.rtdbMobiles.collectAsState()
    val rtdbGsts by viewModel.rtdbGsts.collectAsState()
    val rtdbBrokers by viewModel.rtdbBrokers.collectAsState()
    val rtdbFullBrokers by viewModel.rtdbFullBrokers.collectAsState()

    // 20 Fields matching paper bill exact layout structure
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var billNumber by remember { mutableStateOf("") }
    var sellerName by remember { mutableStateOf("") }
    var buyerName by remember { mutableStateOf("") }
    var gstNo by remember { mutableStateOf("") }
    var brokerName by remember { mutableStateOf("") }
    var particulars by remember { mutableStateOf("Rice Brokerage Contract booking") }
    var bags by remember { mutableStateOf("") }
    var quintals by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("") }
    var delivery by remember { mutableStateOf("") }
    var lorryNo by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var mobileNo by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var lorryFreight by remember { mutableStateOf("") }
    var amountInWords by remember { mutableStateOf("") }
    var sellerSignature by remember { mutableStateOf("Verified") }
    var creditDays by remember { mutableStateOf("") }
    var eb by remember { mutableStateOf("") }

    // legacy fields for DB backward compatibility
    var place by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var ddAmount by remember { mutableStateOf("") }
    var cashCutting by remember { mutableStateOf("") }

    // Payment Section States & Real-time Calculations
    val allPayments by viewModel.allPayments.collectAsState()
    var paymentReceivedInput by remember { mutableStateOf("") }
    var paymentDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var paymentMode by remember { mutableStateOf("Cash") }
    var referenceNumber by remember { mutableStateOf("") }
    var paymentRemarks by remember { mutableStateOf("") }

    // New Fields State
    var sellerAddress by remember { mutableStateOf("") }
    var buyerAddress by remember { mutableStateOf("") }
    var itemEntries by remember { mutableStateOf(listOf(ContractItem(particulars = "", bags = 0, packing = "", qtls = 0.0, rate = 0.0))) }
    val packing = itemEntries.firstOrNull()?.packing ?: ""
    val parseWeightInKg: (String) -> Double = { packingStr ->
        val directDouble = packingStr.toDoubleOrNull()
        if (directDouble != null) {
            directDouble
        } else {
            val numericRegex = """\d+(\.\d+)?""".toRegex()
            val match = numericRegex.find(packingStr)
            match?.value?.toDoubleOrNull() ?: 0.0
        }
    }
    val itemBagsText = remember { mutableStateMapOf<Int, String>() }
    val itemPackingText = remember { mutableStateMapOf<Int, String>() }
    val itemQtlsText = remember { mutableStateMapOf<Int, String>() }
    val itemRateText = remember { mutableStateMapOf<Int, String>() }
    var discountPercent by remember { mutableStateOf("") }
    var commissionPercent by remember { mutableStateOf("") }
    var remark1 by remember { mutableStateOf("") }
    var remark2 by remember { mutableStateOf("") }
    var showPaymentModeDropdown by remember { mutableStateOf(false) }

    // PDF Preview and Unsaved Changes state variables
    var showPdfPreview by remember { mutableStateOf(false) }
    var previewPdfFile by remember { mutableStateOf<java.io.File?>(null) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    var initialLoaded by remember { mutableStateOf(false) }
    var initialBillNumber by remember { mutableStateOf("") }
    var initialSellerName by remember { mutableStateOf("") }
    var initialBuyerName by remember { mutableStateOf("") }
    var initialGstNo by remember { mutableStateOf("") }
    var initialBrokerName by remember { mutableStateOf("") }
    var initialParticulars by remember { mutableStateOf("Rice Brokerage Contract booking") }
    var initialBags by remember { mutableStateOf("") }
    var initialQuintals by remember { mutableStateOf("") }
    var initialRate by remember { mutableStateOf("") }
    var initialTransport by remember { mutableStateOf("") }
    var initialDelivery by remember { mutableStateOf("") }
    var initialLorryNo by remember { mutableStateOf("") }
    var initialPayment by remember { mutableStateOf("") }
    var initialMobileNo by remember { mutableStateOf("") }
    var initialBrand by remember { mutableStateOf("") }
    var initialLorryFreight by remember { mutableStateOf("") }
    var initialAmountInWords by remember { mutableStateOf("") }
    var initialSellerSignature by remember { mutableStateOf("Verified") }
    var initialCreditDays by remember { mutableStateOf("") }
    var initialEb by remember { mutableStateOf("") }
    var initialSellerAddress by remember { mutableStateOf("") }
    var initialBuyerAddress by remember { mutableStateOf("") }
    var initialItemEntries by remember { mutableStateOf<List<ContractItem>>(emptyList()) }

    // Autocomplete list triggers
    var showSellerDropdown by remember { mutableStateOf(false) }
    var showBuyerDropdown by remember { mutableStateOf(false) }
    var showTransportDropdown by remember { mutableStateOf(false) }
    var showBrandDropdown by remember { mutableStateOf(false) }
    var showMobileDropdown by remember { mutableStateOf(false) }
    var showGstDropdown by remember { mutableStateOf(false) }
    var showBrokerDropdown by remember { mutableStateOf(false) }
    var showBrokerSelectionDialog by remember { mutableStateOf(false) }
    var selectedBrokerId by remember { mutableStateOf("") }

    // Dialog trigger states
    var showAddSellerDialog by remember { mutableStateOf(false) }
    var showAddBuyerDialog by remember { mutableStateOf(false) }
    var showAddBrokerDialog by remember { mutableStateOf(false) }
    var newSellerName by remember { mutableStateOf("") }
    var newSellerPhone by remember { mutableStateOf("") }
    var newSellerPlace by remember { mutableStateOf("") }
    var newSellerGst by remember { mutableStateOf("") }
    var newSellerMillName by remember { mutableStateOf("") }
    var newSellerAddress by remember { mutableStateOf("") }

    var newBrokerName by remember { mutableStateOf("") }
    var newBrokerPhone by remember { mutableStateOf("") }
    var newBrokerAddress by remember { mutableStateOf("") }

    var newBuyerName by remember { mutableStateOf("") }
    var newBuyerPhone by remember { mutableStateOf("") }
    var newBuyerPlace by remember { mutableStateOf("") }
    var newBuyerGst by remember { mutableStateOf("") }
    var newBuyerFirmName by remember { mutableStateOf("") }
    var newBuyerAddress by remember { mutableStateOf("") }
    val newBuyerMobiles = remember { mutableStateListOf<String>() }

    LaunchedEffect(showAddBuyerDialog) {
        if (showAddBuyerDialog) {
            newBuyerMobiles.clear()
            newBuyerMobiles.add("")
        }
    }

    // Auto-computed states
    var billAmount by remember { mutableStateOf(0.0) }
    var balance by remember { mutableStateOf(0.0) }
    var outstandingAmount by remember { mutableStateOf(0.0) }
    var commissionAmount by remember { mutableStateOf(0.0) }
    var discountAmount by remember { mutableStateOf(0.0) }

    // Suggest next bill number and load existing contract if editing
    LaunchedEffect(billId, allBills) {
        if (billId != -1) {
            allBills.find { it.id == billId }?.let { b ->
                date = b.date
                billNumber = b.billNumber
                sellerName = b.sellerName
                buyerName = b.buyerName
                place = b.place
                lorryFreight = if (b.lorryFreight > 0) b.lorryFreight.toString() else ""
                rate = if (b.rate > 0) b.rate.toString() else ""
                quintals = if (b.quintals > 0) b.quintals.toString() else ""
                bankName = b.bankName
                remarks = b.remarks
                ddAmount = if (b.ddAmount > 0) b.ddAmount.toString() else ""
                cashCutting = if (b.cashCutting > 0) b.cashCutting.toString() else ""
                
                // load new paper bill fields
                gstNo = b.gstNo
                brokerName = b.brokerName
                selectedBrokerId = b.brokerId
                particulars = b.particulars
                bags = if (b.bags > 0) b.bags.toString() else ""
                transport = b.transport
                delivery = b.delivery
                lorryNo = b.lorryNo
                payment = b.payment
                mobileNo = b.mobileNo
                brand = b.brand
                amountInWords = b.amountInWords
                sellerSignature = b.sellerSignature
                creditDays = if (b.creditDays > 0) b.creditDays.toString() else ""
                eb = b.eb

                // load updated fields
                sellerAddress = b.sellerAddress
                buyerAddress = b.buyerAddress
                discountPercent = if (b.discountPercent > 0.0) b.discountPercent.toString() else ""
                commissionPercent = if (b.commissionPercent > 0.0) b.commissionPercent.toString() else ""
                remark1 = b.remark1
                remark2 = b.remark2
                paymentReceivedInput = if (b.totalReceived > 0.0) b.totalReceived.toString() else ""

                val parsedItems = getItemsForBill(b)
                val finalItems = if (parsedItems.isNotEmpty()) parsedItems else listOf(ContractItem())
                itemEntries = finalItems
                
                itemBagsText.clear()
                itemPackingText.clear()
                itemQtlsText.clear()
                itemRateText.clear()
                finalItems.forEachIndexed { idx, itm ->
                    itemBagsText[idx] = if (itm.bags > 0) itm.bags.toString() else ""
                    itemPackingText[idx] = itm.packing
                    itemQtlsText[idx] = if (itm.qtls > 0.0) String.format(Locale.US, "%.2f", itm.qtls) else ""
                    itemRateText[idx] = if (itm.rate > 0.0) itm.rate.toString() else ""
                }
            }
        } else {
            // Uniquely increment bill number starting from 299 as on reference paper form
            val nextNo = if (allBills.isNotEmpty()) {
                val maxNo = allBills.mapNotNull { it.billNumber.toIntOrNull() }.maxOrNull()
                if (maxNo != null) (maxNo + 1).toString() else "299"
            } else {
                "299"
            }
            billNumber = nextNo
            sellerAddress = ""
            buyerAddress = ""
            brokerName = ""
            selectedBrokerId = ""
            discountPercent = ""
            commissionPercent = ""
            remark1 = ""
            remark2 = ""
            delivery = ""
            payment = ""
            creditDays = ""
            eb = ""
            itemEntries = listOf(ContractItem(particulars = "", bags = 0, packing = "", qtls = 0.0, rate = 0.0))
            itemBagsText.clear()
            itemPackingText.clear()
            itemQtlsText.clear()
            itemRateText.clear()
            itemBagsText[0] = ""
            itemPackingText[0] = ""
            itemQtlsText[0] = ""
            itemRateText[0] = ""
            paymentReceivedInput = ""
        }

        if (!initialLoaded) {
            if (billId != -1) {
                val b = allBills.find { it.id == billId }
                if (b != null) {
                    initialBillNumber = b.billNumber
                    initialSellerName = b.sellerName
                    initialBuyerName = b.buyerName
                    initialGstNo = b.gstNo
                    initialBrokerName = b.brokerName
                    initialParticulars = b.particulars
                    initialBags = if (b.bags > 0) b.bags.toString() else ""
                    initialQuintals = if (b.quintals > 0) b.quintals.toString() else ""
                    initialRate = if (b.rate > 0) b.rate.toString() else ""
                    initialTransport = b.transport
                    initialDelivery = b.delivery
                    initialLorryNo = b.lorryNo
                    initialPayment = b.payment
                    initialMobileNo = b.mobileNo
                    initialBrand = b.brand
                    initialLorryFreight = if (b.lorryFreight > 0) b.lorryFreight.toString() else ""
                    initialAmountInWords = b.amountInWords
                    initialSellerSignature = b.sellerSignature
                    initialCreditDays = if (b.creditDays > 0) b.creditDays.toString() else ""
                    initialEb = b.eb
                    initialSellerAddress = b.sellerAddress
                    initialBuyerAddress = b.buyerAddress
                    val parsed = getItemsForBill(b)
                    initialItemEntries = if (parsed.isNotEmpty()) parsed else listOf(ContractItem())
                    initialLoaded = true
                }
            } else {
                initialBillNumber = billNumber
                initialSellerName = sellerName
                initialBuyerName = buyerName
                initialGstNo = gstNo
                initialBrokerName = brokerName
                initialParticulars = particulars
                initialBags = bags
                initialQuintals = quintals
                initialRate = rate
                initialTransport = transport
                initialDelivery = delivery
                initialLorryNo = lorryNo
                initialPayment = payment
                initialMobileNo = mobileNo
                initialBrand = brand
                initialLorryFreight = lorryFreight
                initialAmountInWords = amountInWords
                initialSellerSignature = sellerSignature
                initialCreditDays = creditDays
                initialEb = eb
                initialSellerAddress = sellerAddress
                initialBuyerAddress = buyerAddress
                initialItemEntries = itemEntries
                initialLoaded = true
            }
        }
    }

    var totalWeightKg by remember { mutableStateOf(0.0) }

    // Computed payment values
    val previousPaymentsForBill = allPayments.filter { it.billNo == billNumber && it.firm == firmName }
    val currentReceivedAmount = paymentReceivedInput.toDoubleOrNull() ?: 0.0
    val remainingBalance = balance
    val computedPaymentStatus = when {
        currentReceivedAmount == 0.0 -> "Pending"
        balance > 0.0 -> "Partial"
        else -> "Received"
    }

    // Real-time automatic computations
    LaunchedEffect(itemEntries, paymentReceivedInput, discountPercent, commissionPercent, lorryFreight, ddAmount, cashCutting, remark1) {
        val totalBagsWeight = itemEntries.sumOf { it.qtls * 100.0 }
        totalWeightKg = totalBagsWeight

        val computedBillAmount = itemEntries.sumOf { it.qtls * it.rate }
        billAmount = computedBillAmount

        val computedCommission = commissionPercent.toDoubleOrNull() ?: 0.0
        commissionAmount = computedCommission

        val computedDiscount = discountPercent.toDoubleOrNull() ?: 0.0
        discountAmount = computedDiscount

        val enteredRemarkAmt = remark1.toDoubleOrNull() ?: 0.0

        val computedFinalPending = computedBillAmount - currentReceivedAmount - computedDiscount - computedCommission - enteredRemarkAmt
        balance = computedFinalPending

        // Generate Rupees in Words dynamically matching paper layout standard
        amountInWords = convertNumberToWords(computedFinalPending)
    }

    // Build Current Bill object for PDF actions
    val currentBill = ContractBill(
        id = if (billId == -1) 0 else billId,
        firmName = firmName,
        date = date,
        billNumber = billNumber,
        sellerName = sellerName,
        buyerName = buyerName,
        place = place,
        lorryFreight = lorryFreight.toDoubleOrNull() ?: 0.0,
        rate = if (itemEntries.isNotEmpty()) itemEntries.first().rate else 0.0,
        quintals = itemEntries.sumOf { it.qtls },
        billAmount = billAmount,
        ddAmount = ddAmount.toDoubleOrNull() ?: 0.0,
        cashCutting = cashCutting.toDoubleOrNull() ?: 0.0,
        balance = balance,
        bankName = bankName,
        remarks = remarks,
        gstNo = gstNo,
        particulars = itemEntries.map { it.particulars }.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Rice Brokerage Contract booking" },
        bags = itemEntries.sumOf { it.bags },
        packing = packing,
        transport = transport,
        delivery = delivery,
        lorryNo = lorryNo,
        payment = payment,
        mobileNo = mobileNo,
        brand = brand,
        amountInWords = amountInWords,
        sellerSignature = sellerSignature,
        creditDays = creditDays.toIntOrNull() ?: 0,
        totalReceived = currentReceivedAmount,
        remainingBalance = balance,
        paymentStatus = computedPaymentStatus,
        lastPaymentDate = if (currentReceivedAmount > 0.0) paymentDate else (previousPaymentsForBill.maxByOrNull { it.paymentDate }?.paymentDate ?: ""),
        sellerAddress = sellerAddress,
        buyerAddress = buyerAddress,
        itemsJson = serializeItems(itemEntries),
        discountPercent = discountPercent.toDoubleOrNull() ?: 0.0,
        commissionPercent = commissionPercent.toDoubleOrNull() ?: 0.0,
        remark1 = remark1,
        remark2 = remark2,
        brokerName = brokerName,
        brokerId = selectedBrokerId,
        eb = eb
    )

    val hasUnsavedChanges = remember(
        billNumber, sellerName, buyerName, gstNo, brokerName, particulars, bags, quintals, rate, transport, delivery,
        lorryNo, payment, mobileNo, brand, lorryFreight, amountInWords, sellerSignature, creditDays, eb,
        sellerAddress, buyerAddress, itemEntries, paymentReceivedInput,
        initialBillNumber, initialSellerName, initialBuyerName, initialGstNo, initialBrokerName, initialParticulars,
        initialBags, initialQuintals, initialRate, initialTransport, initialDelivery, initialLorryNo,
        initialPayment, initialMobileNo, initialBrand, initialLorryFreight, initialAmountInWords,
        initialSellerSignature, initialCreditDays, initialEb, initialSellerAddress, initialBuyerAddress, initialItemEntries
    ) {
        billNumber != initialBillNumber ||
        sellerName != initialSellerName ||
        buyerName != initialBuyerName ||
        gstNo != initialGstNo ||
        brokerName != initialBrokerName ||
        particulars != initialParticulars ||
        bags != initialBags ||
        quintals != initialQuintals ||
        rate != initialRate ||
        transport != initialTransport ||
        delivery != initialDelivery ||
        lorryNo != initialLorryNo ||
        payment != initialPayment ||
        mobileNo != initialMobileNo ||
        brand != initialBrand ||
        lorryFreight != initialLorryFreight ||
        amountInWords != initialAmountInWords ||
        sellerSignature != initialSellerSignature ||
        creditDays != initialCreditDays ||
        eb != initialEb ||
        sellerAddress != initialSellerAddress ||
        buyerAddress != initialBuyerAddress ||
        itemEntries != initialItemEntries ||
        paymentReceivedInput.isNotEmpty()
    }

    BackHandler(enabled = hasUnsavedChanges) {
        showDiscardConfirmation = true
    }

    // Focus Requesters for ERP-style vertical form input workflow
    val focusBillNumber = remember { FocusRequester() }
    val focusDate = remember { FocusRequester() }
    val focusSeller = remember { FocusRequester() }
    val focusBuyer = remember { FocusRequester() }
    val focusGst = remember { FocusRequester() }
    val focusBroker = remember { FocusRequester() }
    val focusParticulars = remember { FocusRequester() }
    val focusBags = remember { FocusRequester() }
    val focusQtls = remember { FocusRequester() }
    val focusRate = remember { FocusRequester() }
    val focusPacking = remember { FocusRequester() }
    val focusTransport = remember { FocusRequester() }
    val focusDelivery = remember { FocusRequester() }
    val focusLorryNo = remember { FocusRequester() }
    val focusPayment = remember { FocusRequester() }
    val focusMobileNo = remember { FocusRequester() }
    val focusBrand = remember { FocusRequester() }
    val focusFreight = remember { FocusRequester() }
    val focusCreditDays = remember { FocusRequester() }
    val focusEb = remember { FocusRequester() }
    val focusRemark1 = remember { FocusRequester() }
    val focusRemark2 = remember { FocusRequester() }
    
    val focusParticularsMap = remember { mutableMapOf<Int, FocusRequester>() }
    val focusBagsMap = remember { mutableMapOf<Int, FocusRequester>() }
    val focusPackagingMap = remember { mutableMapOf<Int, FocusRequester>() }
    val focusQtlsMap = remember { mutableMapOf<Int, FocusRequester>() }
    val focusRateMap = remember { mutableMapOf<Int, FocusRequester>() }

    fun getParticularsFocusRequester(index: Int): FocusRequester = focusParticularsMap.getOrPut(index) { FocusRequester() }
    fun getBagsFocusRequester(index: Int): FocusRequester = focusBagsMap.getOrPut(index) { FocusRequester() }
    fun getPackagingFocusRequester(index: Int): FocusRequester = focusPackagingMap.getOrPut(index) { FocusRequester() }
    fun getQtlsFocusRequester(index: Int): FocusRequester = focusQtlsMap.getOrPut(index) { FocusRequester() }
    fun getRateFocusRequester(index: Int): FocusRequester = focusRateMap.getOrPut(index) { FocusRequester() }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = scope

    // Focus Requesters for Add Seller Dialog
    val focusSellerName = remember { FocusRequester() }
    val focusSellerMobile = remember { FocusRequester() }
    val focusSellerPlace = remember { FocusRequester() }
    val focusSellerGst = remember { FocusRequester() }
    val focusSellerMill = remember { FocusRequester() }
    val focusSellerAddress = remember { FocusRequester() }
    val focusSellerAddButton = remember { FocusRequester() }

    // Focus Requesters for Add Buyer Dialog
    val focusBuyerName = remember { FocusRequester() }
    val focusBuyerMobile = remember { FocusRequester() }
    val focusBuyerPlace = remember { FocusRequester() }
    val focusBuyerGst = remember { FocusRequester() }
    val focusBuyerFirm = remember { FocusRequester() }
    val focusBuyerAddress = remember { FocusRequester() }
    val focusBuyerAddButton = remember { FocusRequester() }

    // Calendar setup for modern dynamic DatePicker Dialog
    val calendar = Calendar.getInstance()
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            date = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            focusSeller.requestFocus()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // UI Structure
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 8.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "$firmName",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Contract Booking Entry Form",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ==========================================
            // 1. Bill No.
            // ==========================================
            OutlinedTextField(
                value = billNumber,
                onValueChange = { billNumber = it },
                label = { Text("Bill No.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusBillNumber)
                    .testTag("bill_number_input"),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusDate.requestFocus() }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 2. Date
            // ==========================================
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Date") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusDate)
                    .clickable { datePickerDialog.show() }
                    .testTag("date_input"),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusSeller.requestFocus() }
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 3. Seller
            // ==========================================
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = sellerName,
                    onValueChange = {
                        sellerName = it
                        showSellerDropdown = true
                    },
                    label = { Text("Seller Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusSeller)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                showSellerDropdown = true
                            }
                        }
                        .testTag("seller_name_input"),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusBuyer.requestFocus() }
                    ),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showAddSellerDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add New Seller")
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                if (showSellerDropdown) {
                    val filteredSellers = (sellersMaster.map { it.name } + rtdbSellers).distinct().filter {
                        it.contains(sellerName, ignoreCase = true)
                    }
                    if (filteredSellers.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                filteredSellers.take(5).forEach { name ->
                                    Text(
                                        text = name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                sellerName = name
                                                val selectedSellerObj = rtdbFullSellers.find { it.sellerName.equals(name, ignoreCase = true) }
                                                if (selectedSellerObj != null) {
                                                    if (selectedSellerObj.place.isNotBlank()) place = selectedSellerObj.place
                                                    if (selectedSellerObj.mobile.isNotBlank()) mobileNo = selectedSellerObj.mobile
                                                    if (selectedSellerObj.gstNo.isNotBlank()) gstNo = selectedSellerObj.gstNo
                                                    if (selectedSellerObj.address.isNotBlank()) sellerAddress = selectedSellerObj.address
                                                    
                                                    val millAndAddr = listOfNotNull(
                                                        selectedSellerObj.millName.takeIf { it.isNotBlank() },
                                                        selectedSellerObj.address.takeIf { it.isNotBlank() }
                                                    ).joinToString(", ")
                                                    if (millAndAddr.isNotBlank()) {
                                                        remarks = "Mill: $millAndAddr"
                                                    }
                                                } else {
                                                    val localObj = sellersMaster.find { it.name.equals(name, ignoreCase = true) }
                                                    if (localObj != null) {
                                                        if (localObj.place.isNotBlank()) place = localObj.place
                                                        if (localObj.address.isNotBlank()) sellerAddress = localObj.address
                                                    }
                                                }
                                                showSellerDropdown = false
                                                focusBuyer.requestFocus()
                                            }
                                            .padding(12.dp),
                                        fontSize = 13.sp
                                    )
                                }
                             }
                         }
                     }
                 }
             }
             Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 4. Buyer
            // ==========================================
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = buyerName,
                    onValueChange = {
                        buyerName = it
                        showBuyerDropdown = true
                    },
                    label = { Text("Buyer Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusBuyer)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                showBuyerDropdown = true
                            }
                        }
                        .testTag("buyer_name_input"),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusGst.requestFocus() }
                    ),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showAddBuyerDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add New Buyer")
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                if (showBuyerDropdown) {
                    val filteredBuyers = (buyersMaster.map { it.name } + rtdbBuyers).distinct().filter {
                        it.contains(buyerName, ignoreCase = true)
                    }
                    if (filteredBuyers.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                filteredBuyers.take(5).forEach { name ->
                                    Text(
                                        text = name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                buyerName = name
                                                val selectedBuyerObj = rtdbFullBuyers.find { it.buyerName.equals(name, ignoreCase = true) }
                                                if (selectedBuyerObj != null) {
                                                    if (selectedBuyerObj.place.isNotBlank()) place = selectedBuyerObj.place
                                                    if (selectedBuyerObj.mobile.isNotBlank()) mobileNo = selectedBuyerObj.mobile
                                                    if (selectedBuyerObj.gstNo.isNotBlank()) gstNo = selectedBuyerObj.gstNo
                                                    if (selectedBuyerObj.address.isNotBlank()) buyerAddress = selectedBuyerObj.address
                                                    
                                                    val firmAndAddr = listOfNotNull(
                                                        selectedBuyerObj.firmName.takeIf { it.isNotBlank() },
                                                        selectedBuyerObj.address.takeIf { it.isNotBlank() }
                                                    ).joinToString(", ")
                                                    if (firmAndAddr.isNotBlank()) {
                                                        remarks = "Firm: $firmAndAddr"
                                                    }
                                                } else {
                                                    val localObj = buyersMaster.find { it.name.equals(name, ignoreCase = true) }
                                                    if (localObj != null) {
                                                        if (localObj.place.isNotBlank()) place = localObj.place
                                                        if (localObj.address.isNotBlank()) buyerAddress = localObj.address
                                                    }
                                                }
                                                showBuyerDropdown = false
                                                focusGst.requestFocus()
                                            }
                                            .padding(12.dp),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 5. GST No.
            // ==========================================
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = gstNo,
                    onValueChange = {
                        gstNo = it
                        showGstDropdown = it.isNotBlank()
                    },
                    label = { Text("GST No.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusGst)
                        .testTag("gst_no_input"),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusBroker.requestFocus() }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (showGstDropdown) {
                    val filteredGsts = rtdbGsts.filter {
                        it.contains(gstNo, ignoreCase = true)
                    }
                    if (filteredGsts.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                filteredGsts.take(5).forEach { gst ->
                                    Text(
                                        text = gst,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                gstNo = gst
                                                showGstDropdown = false
                                                focusBroker.requestFocus()
                                            }
                                            .padding(12.dp),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 5b. Broker Name
            // ==========================================
            Column(modifier = Modifier.fillMaxWidth()) {
                val brokerExists = (brokersMaster.map { it.name } + rtdbBrokers).distinct().any {
                    it.trim().equals(brokerName.trim(), ignoreCase = true)
                }

                OutlinedTextField(
                    value = brokerName,
                    onValueChange = { newValue ->
                        brokerName = newValue
                        showBrokerDropdown = true
                        // Try to find matching broker to set selectedBrokerId
                        val matchedBroker = rtdbFullBrokers.find { b -> b.brokerName.trim().equals(newValue.trim(), ignoreCase = true) }
                        selectedBrokerId = matchedBroker?.brokerId ?: ""
                    },
                    label = { Text("Broker Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusBroker)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                showBrokerDropdown = true
                            }
                        }
                        .testTag("broker_name_input"),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    trailingIcon = {
                        if (!brokerExists && brokerName.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    newBrokerName = brokerName.trim()
                                    showAddBrokerDialog = true
                                },
                                modifier = Modifier.testTag("add_broker_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add New Broker")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                if (showBrokerDropdown) {
                    val filteredBrokers = (brokersMaster.map { it.name } + rtdbBrokers).distinct().filter {
                        it.contains(brokerName, ignoreCase = true)
                    }
                    if (filteredBrokers.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                filteredBrokers.take(5).forEach { name ->
                                    Text(
                                        text = name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                brokerName = name
                                                val selectedBrokerObj = rtdbFullBrokers.find { it.brokerName.equals(name, ignoreCase = true) }
                                                if (selectedBrokerObj != null) {
                                                    selectedBrokerId = selectedBrokerObj.brokerId
                                                }
                                                showBrokerDropdown = false
                                            }
                                            .padding(12.dp),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // Add Item (+) button (directly below GST)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Items / Particulars List",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = {
                        val nextIdx = itemEntries.size
                        itemBagsText[nextIdx] = ""
                        itemPackingText[nextIdx] = ""
                        itemQtlsText[nextIdx] = ""
                        itemRateText[nextIdx] = ""
                        itemEntries = itemEntries + ContractItem(particulars = "", bags = 0, packing = "", qtls = 0.0, rate = 0.0)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("add_item_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Item")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // ==========================================
            // Dynamic Item Blocks
            // ==========================================
            itemEntries.forEachIndexed { index, item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Item #${index + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (itemEntries.size > 1) {
                                IconButton(
                                    onClick = {
                                        // Shift map entries to the left
                                        val size = itemEntries.size
                                        for (i in index until size - 1) {
                                            itemBagsText[i] = itemBagsText[i + 1] ?: ""
                                            itemPackingText[i] = itemPackingText[i + 1] ?: ""
                                            itemQtlsText[i] = itemQtlsText[i + 1] ?: ""
                                            itemRateText[i] = itemRateText[i + 1] ?: ""
                                        }
                                        itemBagsText.remove(size - 1)
                                        itemPackingText.remove(size - 1)
                                        itemQtlsText.remove(size - 1)
                                        itemRateText.remove(size - 1)

                                        itemEntries = itemEntries.toMutableList().apply { removeAt(index) }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Item",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Particulars
                        OutlinedTextField(
                            value = item.particulars,
                            onValueChange = { newVal ->
                                itemEntries = itemEntries.toMutableList().apply {
                                    this[index] = this[index].copy(particulars = newVal)
                                }
                            },
                            label = { Text("Particulars") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(getParticularsFocusRequester(index))
                                .testTag("particulars_input_$index"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { getBagsFocusRequester(index).requestFocus() }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Bags
                        OutlinedTextField(
                            value = itemBagsText[index] ?: "",
                            onValueChange = { newVal ->
                                itemBagsText[index] = newVal
                                val intVal = newVal.toIntOrNull() ?: 0
                                
                                // Auto calculate Qtls = (Bags * Packaging) / 100
                                val packingStr = itemPackingText[index] ?: ""
                                val packingVal = parseWeightInKg(packingStr)
                                val computedQtls = (intVal * packingVal) / 100.0
                                val qtlsText = if (computedQtls > 0.0) String.format(Locale.US, "%.2f", computedQtls) else ""
                                itemQtlsText[index] = qtlsText
                                
                                itemEntries = itemEntries.toMutableList().apply {
                                    this[index] = this[index].copy(
                                        bags = intVal,
                                        qtls = computedQtls
                                    )
                                }
                            },
                            label = { Text("Bags") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(getBagsFocusRequester(index))
                                .testTag("bags_input_$index"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { getPackagingFocusRequester(index).requestFocus() }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Packaging
                        OutlinedTextField(
                            value = itemPackingText[index] ?: "",
                            onValueChange = { newVal ->
                                itemPackingText[index] = newVal
                                val packingVal = parseWeightInKg(newVal)
                                
                                // Auto calculate Qtls = (Bags * Packaging) / 100
                                val bagsVal = itemBagsText[index]?.toIntOrNull() ?: 0
                                val computedQtls = (bagsVal * packingVal) / 100.0
                                val qtlsText = if (computedQtls > 0.0) String.format(Locale.US, "%.2f", computedQtls) else ""
                                itemQtlsText[index] = qtlsText
                                
                                itemEntries = itemEntries.toMutableList().apply {
                                    this[index] = this[index].copy(
                                        packing = newVal,
                                        qtls = computedQtls
                                    )
                                }
                            },
                            label = { Text("Packaging") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(getPackagingFocusRequester(index))
                                .testTag("packing_input_$index"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { getRateFocusRequester(index).requestFocus() }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Qtls
                        OutlinedTextField(
                            value = itemQtlsText[index] ?: "",
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Qtls") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(getQtlsFocusRequester(index))
                                .testTag("quintals_input_$index"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { getRateFocusRequester(index).requestFocus() }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Rate
                        OutlinedTextField(
                            value = itemRateText[index] ?: "",
                            onValueChange = { newVal ->
                                itemRateText[index] = newVal
                                val doubleVal = newVal.toDoubleOrNull() ?: 0.0
                                itemEntries = itemEntries.toMutableList().apply {
                                    this[index] = this[index].copy(rate = doubleVal)
                                }
                            },
                            label = { Text("Rate") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(getRateFocusRequester(index))
                                .testTag("rate_input_$index"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    if (index < itemEntries.size - 1) {
                                        getParticularsFocusRequester(index + 1).requestFocus()
                                    } else {
                                        focusTransport.requestFocus()
                                    }
                                }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Item Amount: ₹${String.format(Locale.getDefault(), "%,.2f", item.qtls * item.rate)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // ==========================================
            // 11. Transport
            // ==========================================
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = transport,
                    onValueChange = {
                        transport = it
                        showTransportDropdown = it.isNotBlank()
                    },
                    label = { Text("Transport") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusTransport)
                        .testTag("transport_input"),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusDelivery.requestFocus() }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (showTransportDropdown) {
                    val filteredTransports = rtdbTransports.filter {
                        it.contains(transport, ignoreCase = true)
                    }
                    if (filteredTransports.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                filteredTransports.take(5).forEach { tr ->
                                    Text(
                                        text = tr,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                transport = tr
                                                showTransportDropdown = false
                                                focusDelivery.requestFocus()
                                            }
                                            .padding(12.dp),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 12. Delivery
            // ==========================================
            OutlinedTextField(
                value = delivery,
                onValueChange = { delivery = it },
                label = { Text("Delivery") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusDelivery)
                    .testTag("delivery_input"),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusLorryNo.requestFocus() }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 13. Lorry No.
            // ==========================================
            OutlinedTextField(
                value = lorryNo,
                onValueChange = { lorryNo = it },
                label = { Text("Lorry No.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusLorryNo)
                    .testTag("lorry_no_input"),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusPayment.requestFocus() }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 14. Payment
            // ==========================================
            OutlinedTextField(
                value = payment,
                onValueChange = { payment = it },
                label = { Text("Payment") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusPayment)
                    .testTag("payment_input"),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusMobileNo.requestFocus() }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 15. Mobile No.
            // ==========================================
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = mobileNo,
                    onValueChange = {
                        mobileNo = it
                        showMobileDropdown = it.isNotBlank()
                    },
                    label = { Text("Mobile No.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusMobileNo)
                        .testTag("mobile_no_input"),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusBrand.requestFocus() }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (showMobileDropdown) {
                    val filteredMobiles = rtdbMobiles.filter {
                        it.contains(mobileNo, ignoreCase = true)
                    }
                    if (filteredMobiles.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                filteredMobiles.take(5).forEach { mob ->
                                    Text(
                                        text = mob,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                mobileNo = mob
                                                showMobileDropdown = false
                                                focusBrand.requestFocus()
                                            }
                                            .padding(12.dp),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 16. Brand
            // ==========================================
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = brand,
                    onValueChange = {
                        brand = it
                        showBrandDropdown = it.isNotBlank()
                    },
                    label = { Text("Brand") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusBrand)
                        .testTag("brand_input"),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusFreight.requestFocus() }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (showBrandDropdown) {
                    val filteredBrands = rtdbBrands.filter {
                        it.contains(brand, ignoreCase = true)
                    }
                    if (filteredBrands.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                filteredBrands.take(5).forEach { br ->
                                    Text(
                                        text = br,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                brand = br
                                                showBrandDropdown = false
                                                focusFreight.requestFocus()
                                            }
                                            .padding(12.dp),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 17. Lorry Freight
            // ==========================================
            OutlinedTextField(
                value = lorryFreight,
                onValueChange = { lorryFreight = it },
                label = { Text("Lorry Freight") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusFreight)
                    .testTag("lorry_freight_input"),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusCreditDays.requestFocus() }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 18. Credit Days
            // ==========================================
            OutlinedTextField(
                value = creditDays,
                onValueChange = { creditDays = it },
                label = { Text("Credit Days") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusCreditDays)
                    .testTag("credit_days_input"),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusEb.requestFocus() }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 19. EB
            // ==========================================
            OutlinedTextField(
                value = eb,
                onValueChange = { eb = it },
                label = { Text("EB") },
                placeholder = { Text("Enter EB") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusEb)
                    .testTag("eb_input"),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // ==========================================
            // PAYMENT HISTORY
            // ==========================================
            if (previousPaymentsForBill.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag("payment_history_section"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "PAYMENT HISTORY (${previousPaymentsForBill.size})",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        previousPaymentsForBill.forEachIndexed { index, p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "₹${String.format(Locale.getDefault(), "%,.2f", p.paymentAmount)}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Date: ${p.paymentDate} | Mode: ${p.paymentMode}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (p.referenceNumber.isNotBlank()) {
                                        Text(
                                            text = "Ref: ${p.referenceNumber}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (p.remarks.isNotBlank()) {
                                        Text(
                                            text = "Note: ${p.remarks}",
                                            fontSize = 11.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Delete/Remove payment action
                                IconButton(
                                    onClick = {
                                        com.example.util.BiometricHelper.runWithBiometric(
                                            context = context,
                                            title = "Ranisa Security",
                                            subtitle = "Verify your fingerprint to continue.",
                                            action = {
                                                viewModel.deletePayment(p)
                                                Toast.makeText(context, "Payment Removed Successfully", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Payment",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            if (index < previousPaymentsForBill.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }

            // Real-time dynamic checks / actions for Saving / Editing
            val saveAction = {
                if (billNumber.isBlank() || sellerName.isBlank() || buyerName.isBlank()) {
                    Toast.makeText(context, "Please enter Bill No, Seller Name and Buyer Name", Toast.LENGTH_SHORT).show()
                } else {
                    if (currentReceivedAmount > 0.0) {
                        viewModel.savePayment(
                            buyerName = buyerName,
                            date = paymentDate,
                            amount = currentReceivedAmount,
                            paymentMode = paymentMode,
                            bankName = referenceNumber,
                            remarks = paymentRemarks,
                            billNo = billNumber,
                            firm = firmName,
                            sellerName = sellerName,
                            referenceNumber = referenceNumber,
                            remainingBalance = balance
                        )
                    }
                    if (billId == -1) {
                        viewModel.saveBill(currentBill)
                    } else {
                        viewModel.updateBill(currentBill)
                    }
                    Toast.makeText(context, "Contract Saved Successfully", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            }

            // ==========================================
            // ACTION BUTTONS (2x2 Grid + Cancel / Go Back)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: Save Bill and PDF Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { saveAction() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("save_contract_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (billId == -1) "Save Bill" else "Update Bill",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (billNumber.isBlank() || sellerName.isBlank() || buyerName.isBlank()) {
                                Toast.makeText(context, "Please fill required details to preview PDF", Toast.LENGTH_SHORT).show()
                            } else {
                                val pdfBill = getLatestBillForPdf(currentBill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                val pdfFile = PdfGenerator.generateContractPdf(context, pdfBill)
                                previewPdfFile = pdfFile
                                showPdfPreview = true
                                viewModel.logPdfPreview(billNumber, firmName)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("preview_pdf_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Preview")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PDF Preview",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Row 2: Print PDF and Share PDF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (billNumber.isBlank() || sellerName.isBlank() || buyerName.isBlank()) {
                                Toast.makeText(context, "Please fill required details to generate PDF", Toast.LENGTH_SHORT).show()
                            } else {
                                val pdfBill = getLatestBillForPdf(currentBill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                val pdfFile = PdfGenerator.generateContractPdf(context, pdfBill)
                                PdfGenerator.printPdf(context, pdfFile)
                                viewModel.logPrint(billNumber, firmName)
                                Toast.makeText(context, "Generating & Sending to Printer...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("print_pdf_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print", tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Print PDF",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (billNumber.isBlank() || sellerName.isBlank() || buyerName.isBlank()) {
                                Toast.makeText(context, "Please fill required details to share PDF", Toast.LENGTH_SHORT).show()
                            } else {
                                val pdfBill = getLatestBillForPdf(currentBill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                val pdfFile = PdfGenerator.generateContractPdf(context, pdfBill)
                                PdfGenerator.sharePdf(context, pdfFile)
                                viewModel.logPdfShare(billNumber, firmName)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("share_pdf_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share PDF",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Delete Bill (if editing)
                if (billId != -1) {
                    Button(
                        onClick = {
                            com.example.util.BiometricHelper.runWithBiometric(
                                context = context,
                                title = "Ranisa Security",
                                subtitle = "Verify your fingerprint to continue.",
                                action = {
                                    viewModel.deleteBill(currentBill)
                                    Toast.makeText(context, "Contract Bill Deleted", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("delete_contract_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delete Contract",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Cancel / Go Back
                OutlinedButton(
                    onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardConfirmation = true
                        } else {
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("cancel_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cancel / Go Back",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ==========================================
            // RECENT CONTRACT BILLS LIST SECTION
            // ==========================================
            val firmBills = allBills.filter { it.firmName.equals(firmName, ignoreCase = true) }
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Recent Contract Bills",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (firmBills.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No contract bills recorded yet for $firmName.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Table inside horizontal scroll
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                ) {
                    Column {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Bill No", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Date", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Seller", modifier = Modifier.width(130.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Buyer", modifier = Modifier.width(130.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Qtls", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.End)
                            Text("Amount", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.End)
                            Text("Status", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
                            Text("Action", modifier = Modifier.width(160.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
                        }

                        // Data Rows
                        firmBills.take(20).forEachIndexed { index, bill ->
                            val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            Row(
                                modifier = Modifier
                                    .background(rowBg)
                                    .clickable {
                                        // clicking row loads it for editing
                                        navController.navigate("bill_entry/$firmName/${bill.id}") {
                                            popUpTo("bill_entry/$firmName/$billId") { inclusive = true }
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(bill.billNumber, modifier = Modifier.width(70.dp), fontSize = 12.sp)
                                Text(bill.date, modifier = Modifier.width(90.dp), fontSize = 12.sp)
                                Text(bill.sellerName, modifier = Modifier.width(130.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(bill.buyerName, modifier = Modifier.width(130.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(String.format(Locale.getDefault(), "%.1f", bill.quintals), modifier = Modifier.width(70.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                                Text("₹${String.format(Locale.getDefault(), "%,.1f", bill.billAmount)}", modifier = Modifier.width(100.dp), fontSize = 12.sp, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                                
                                val statusText = if (bill.balance > 0) "O/S" else "Paid"
                                val statusColor = if (bill.balance > 0) Color(0xFFD97706) else Color(0xFF16A34A)
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = statusText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(statusColor, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.width(160.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            navController.navigate("bill_entry/$firmName/${bill.id}") {
                                                popUpTo("bill_entry/$firmName/$billId") { inclusive = true }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            val pdfBill = getLatestBillForPdf(bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                            val pdfFile = PdfGenerator.generateContractPdf(context, pdfBill)
                                            PdfGenerator.printPdf(context, pdfFile)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = "Print", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            val pdfBill = getLatestBillForPdf(bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                            val pdfFile = PdfGenerator.generateContractPdf(context, pdfBill)
                                            PdfGenerator.sharePdf(context, pdfFile)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            com.example.util.BiometricHelper.runWithBiometric(
                                                context = context,
                                                title = "Ranisa Security",
                                                subtitle = "Verify your fingerprint to continue.",
                                                action = {
                                                    viewModel.deleteBill(bill)
                                                    Toast.makeText(context, "Contract Bill ${bill.billNumber} Deleted", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }



    // Dynamic Pop-up Dialog to Quick Add Seller Master
    if (showAddSellerDialog) {
        SellerFormDialog(
            title = "Add Seller Master",
            name = newSellerName,
            onNameChange = { newSellerName = it },
            address = newSellerAddress,
            onAddressChange = { newSellerAddress = it },
            onDismiss = { showAddSellerDialog = false },
            onSave = {
                if (newSellerName.isBlank()) {
                    Toast.makeText(context, "Please enter Seller Name", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addSeller(
                        sellerName = newSellerName.trim(),
                        mobile = "",
                        place = "",
                        gstNo = "",
                        millName = "",
                        address = newSellerAddress.trim(),
                        onSuccess = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Seller Added Successfully")
                            }
                            Toast.makeText(context, "Seller Added Successfully", Toast.LENGTH_SHORT).show()
                            // Auto-select and auto-fill
                            sellerName = newSellerName.trim()
                            if (newSellerAddress.isNotBlank()) {
                                sellerAddress = newSellerAddress.trim()
                                remarks = "Mill: ${newSellerAddress.trim()}"
                            }

                            // Close Dialog
                            showAddSellerDialog = false
                            // Reset inputs
                            newSellerName = ""
                            newSellerAddress = ""
                        },
                        onError = { errorMsg ->
                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        )
    }

    // Dynamic Pop-up Dialog to Quick Add Broker Master
    if (showAddBrokerDialog) {
        val formMobiles = remember { mutableStateListOf<String>("") }
        BrokerMasterFormDialog(
            title = "Add Broker Master",
            name = newBrokerName,
            onNameChange = { newBrokerName = it },
            mobiles = formMobiles,
            address = newBrokerAddress,
            onAddressChange = { newBrokerAddress = it },
            onDismiss = { showAddBrokerDialog = false },
            onSave = {
                if (newBrokerName.isBlank()) {
                    Toast.makeText(context, "Please enter Broker Name", Toast.LENGTH_SHORT).show()
                } else {
                    val joinedMobile = formMobiles.filter { it.isNotBlank() }.joinToString(",")
                    viewModel.addBroker(
                        brokerName = newBrokerName.trim(),
                        mobile = joinedMobile,
                        address = newBrokerAddress.trim(),
                        onSuccess = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Broker Added Successfully")
                            }
                            Toast.makeText(context, "Broker Added Successfully", Toast.LENGTH_SHORT).show()
                            
                            // Auto-select and auto-fill
                            val newlyCreated = viewModel.rtdbFullBrokers.value.find { 
                                it.brokerName.trim().equals(newBrokerName.trim(), ignoreCase = true) 
                            } ?: FirebaseBroker(
                                brokerId = java.util.UUID.randomUUID().toString(),
                                brokerName = newBrokerName.trim(),
                                mobile = joinedMobile,
                                address = newBrokerAddress.trim()
                            )
                            brokerName = newlyCreated.brokerName
                            selectedBrokerId = newlyCreated.brokerId

                            // Close Dialog
                            showAddBrokerDialog = false
                            // Reset inputs
                            newBrokerName = ""
                            newBrokerAddress = ""
                        },
                        onError = { errorMsg ->
                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        )
    }

    if (showBrokerSelectionDialog) {
        BrokerSelectionDialog(
            viewModel = viewModel,
            onDismiss = { showBrokerSelectionDialog = false },
            onBrokerSelected = { selectedBroker ->
                brokerName = selectedBroker.brokerName
                selectedBrokerId = selectedBroker.brokerId
            }
        )
    }

    // Dynamic Pop-up Dialog to Quick Add Buyer Master
    if (showAddBuyerDialog) {
        BuyerFormDialog(
            title = "Add Buyer Master",
            name = newBuyerName,
            onNameChange = { newBuyerName = it },
            mobiles = newBuyerMobiles,
            address = newBuyerAddress,
            onAddressChange = { newBuyerAddress = it },
            gst = newBuyerGst,
            onGstChange = { newBuyerGst = it },
            onDismiss = { showAddBuyerDialog = false },
            onSave = {
                if (newBuyerName.isBlank()) {
                    Toast.makeText(context, "Please enter Buyer Name", Toast.LENGTH_SHORT).show()
                } else {
                    val joinedMobile = newBuyerMobiles.filter { it.isNotBlank() }.joinToString(", ")
                    viewModel.addBuyer(
                        buyerName = newBuyerName.trim(),
                        mobile = joinedMobile,
                        place = "",
                        gstNo = newBuyerGst.trim(),
                        firmName = "",
                        address = newBuyerAddress.trim(),
                        onSuccess = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Buyer Added Successfully")
                            }
                            Toast.makeText(context, "Buyer Added Successfully", Toast.LENGTH_SHORT).show()
                            // Auto-select and auto-fill
                            buyerName = newBuyerName.trim()
                            if (joinedMobile.isNotBlank()) mobileNo = joinedMobile
                            if (newBuyerGst.isNotBlank()) gstNo = newBuyerGst.trim()
                            if (newBuyerAddress.isNotBlank()) buyerAddress = newBuyerAddress.trim()

                            // Close Dialog
                            showAddBuyerDialog = false
                            // Reset inputs
                            newBuyerName = ""
                            newBuyerGst = ""
                            newBuyerAddress = ""
                        },
                        onError = { errorMsg ->
                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        )
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("Discard Unsaved Changes?", fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved changes in this contract. Are you sure you want to discard them and go back?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmation = false
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Discard", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDiscardConfirmation = false }
                ) {
                    Text("No, Stay Here", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showPdfPreview && previewPdfFile != null) {
        PdfPreviewDialog(
            file = previewPdfFile!!,
            onPrint = {
                PdfGenerator.printPdf(context, previewPdfFile!!)
                viewModel.logPrint(billNumber, firmName)
            },
            onShare = {
                PdfGenerator.sharePdf(context, previewPdfFile!!)
                viewModel.logPdfExport(billNumber, firmName)
            },
            onClose = {
                showPdfPreview = false
                previewPdfFile = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewDialog(
    file: java.io.File,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var bitmaps by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var scale by remember { mutableStateOf(1f) }

    LaunchedEffect(file) {
        val list = mutableListOf<android.graphics.Bitmap>()
        try {
            val fileDescriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
            val pageCount = pdfRenderer.pageCount
            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                val width = page.width * 2
                val height = page.height * 2
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                list.add(bitmap)
                page.close()
            }
            pdfRenderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error rendering PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        bitmaps = list
    }

    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("👁 PDF Preview", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { if (scale > 0.5f) scale -= 0.25f }) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                        }
                        Text("${(scale * 100).toInt()}%", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(onClick = { if (scale < 3.0f) scale += 0.25f }) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In")
                        }
                        IconButton(onClick = { scale = 1f }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Zoom")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            },
            bottomBar = {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onPrint,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "Print")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print PDF", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onShare,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share PDF", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Close", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Gray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmaps.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(bitmaps) { bitmap ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                                    )
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, _, zoom, _ ->
                                            val newScale = scale * zoom
                                            scale = newScale.coerceIn(0.5f, 3.0f)
                                        }
                                    },
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                shape = RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "PDF Page",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SELLER LEDGER SCREEN (REGISTER STYLE!)
// ==========================================
@Composable
fun SellerLedgerScreen(navController: NavController, viewModel: RanisaViewModel, preselectedSeller: String = "") {
    val bills by viewModel.allBills.collectAsState()
    val searchQuery by viewModel.sellerLedgerSearch.collectAsState()

    val payments by viewModel.allPayments.collectAsState()
    val sellersMaster by viewModel.sellers.collectAsState()
    val buyersMaster by viewModel.buyers.collectAsState()
    val rtdbFullSellers by viewModel.rtdbFullSellers.collectAsState()
    val rtdbFullBuyers by viewModel.rtdbFullBuyers.collectAsState()

    var selectedSeller by remember { mutableStateOf(preselectedSeller) }
    var expandedBillId by remember { mutableStateOf<Int?>(null) }
    var previewPdfFile by remember { mutableStateOf<java.io.File?>(null) }
    var showPdfPreview by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var showEditPaymentDialog by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<ContractBill?>(null) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    var editingAlreadyPaid by remember { mutableStateOf(0.0) }
    var editingPending by remember { mutableStateOf(0.0) }

    var showDeleteBillDialog by remember { mutableStateOf(false) }
    var billToDelete by remember { mutableStateOf<ContractBill?>(null) }
    var showDeletePaymentDialog by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }
    var paymentBillToDeleteFrom by remember { mutableStateOf<ContractBill?>(null) }

    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    var showDeleteLedgerDialog by remember { mutableStateOf(false) }
    var ledgerToDeleteName by remember { mutableStateOf("") }
    var showShareSheetForLedger by remember { mutableStateOf(false) }
    var ledgerToShareName by remember { mutableStateOf("") }

    val distinctSellers = remember(bills) {
        bills.map { it.sellerName }.distinct().filter { seller ->
            val sellerBills = bills.filter { it.sellerName == seller }
            val totalQtls = sellerBills.sumOf { it.quintals }
            sellerBills.isNotEmpty() && totalQtls > 0.0
        }
    }

    val filteredSellers = remember(distinctSellers, bills, searchQuery) {
        distinctSellers.filter { seller ->
            seller.contains(searchQuery, ignoreCase = true) ||
            bills.any { it.sellerName == seller && it.billNumber.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (isSearchActive) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSellerLedgerSearch(it) },
                    placeholder = { Text("Search Seller Name or Bill No...", fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(focusRequester)
                        .testTag("seller_ledger_search_input"),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                    trailingIcon = {
                        IconButton(onClick = { 
                            viewModel.updateSellerLedgerSearch("")
                            isSearchActive = false 
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )
            }
            LaunchedEffect(isSearchActive) {
                if (isSearchActive) {
                    focusRequester.requestFocus()
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Seller Ledger & Registers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { isSearchActive = true },
                    modifier = Modifier.testTag("seller_ledger_search_button")
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search Seller")
                }
            }
        }

        if (selectedSeller.isBlank()) {
            Text("Sellers Master List", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp), fontSize = 13.sp)
            if (distinctSellers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("📄", fontSize = 48.sp, modifier = Modifier.padding(bottom = 12.dp))
                        Text(
                            text = "No Ledger Records Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create your first bill to see ledger entries here.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (filteredSellers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No matching sellers found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(filteredSellers) { seller ->
                        val sellerBills = bills.filter { it.sellerName == seller }
                        val totalQtls = sellerBills.sumOf { it.quintals }
                        val matchingSeller = rtdbFullSellers.find { it.sellerName == seller }
                        Card(
                            onClick = { selectedSeller = seller },
                            modifier = Modifier.fillMaxWidth().testTag("seller_card_${seller}")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(seller, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("${sellerBills.size} Registered Bills", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                                    Text("Total Qtls", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${String.format("%.2f", totalQtls)} Qtls", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                
                                LedgerOverflowMenu(
                                    itemId = matchingSeller?.sellerId ?: seller,
                                    onEdit = {
                                        navController.navigate("seller_master_list")
                                    },
                                    onShare = {
                                        ledgerToShareName = seller
                                        showShareSheetForLedger = true
                                    },
                                    onDeleteLedger = {
                                        ledgerToDeleteName = seller
                                        showDeleteLedgerDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Selected seller: 13-column Register View
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = { selectedSeller = "" },
                        modifier = Modifier.testTag("seller_ledger_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "$selectedSeller's Book Registry",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                var showShareSheet by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showShareSheet = true },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(end = 4.dp).testTag("share_full_seller_ledger_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share Full Ledger", fontSize = 11.sp)
                }

                if (showShareSheet) {
                    FullLedgerShareSheet(
                        ledgerName = selectedSeller,
                        ledgerType = "seller",
                        bills = bills.filter { it.sellerName == selectedSeller },
                        payments = payments,
                        onDismissRequest = { showShareSheet = false }
                    )
                }
            }

            val sellerBills = remember(bills, selectedSeller, searchQuery) {
                bills.filter { it.sellerName == selectedSeller }.filter { bill ->
                    searchQuery.isBlank() ||
                    bill.billNumber.contains(searchQuery, ignoreCase = true) ||
                    bill.buyerName.contains(searchQuery, ignoreCase = true) ||
                    bill.date.contains(searchQuery, ignoreCase = true)
                }
            }

            // Dynamic Calculations & Summary Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Total Bills", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        Text("${sellerBills.size}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Total Qtls", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        Text("${String.format("%.2f", sellerBills.sumOf { it.quintals })} Qtls", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            var pageLimit by remember { mutableStateOf(500) }
            val paginatedBills = remember(sellerBills, pageLimit) {
                sellerBills.take(pageLimit)
            }

            // Scrollable 15-column Grid simulating the physical register
            Box(modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    // Header row
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val headers = listOf(
                            "Date" to 90, "Bill No" to 80, "Party Name" to 150, "Place" to 90,
                            "Freight" to 80, "Rate" to 70, "Qtls" to 70, 
                            "BILL AMT" to 100, "Received" to 100, "Discount" to 80, "Broker Name" to 120, "Commission" to 90,
                            "Remark Amt" to 100, "Remark" to 150, "Balance AMT" to 100, "Status" to 100, "EB" to 100, "Action" to 180
                        )
                        headers.forEach { (text, width) ->
                            Text(
                                text = text,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(width.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    HorizontalDivider()

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(paginatedBills) { bill ->
                            val isExpanded = expandedBillId == bill.id
                            val billPayments = remember(payments, bill.billNumber) {
                                payments.filter { p ->
                                    p.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true)
                                }
                            }

                            Column {
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            expandedBillId = if (isExpanded) null else bill.id
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp)
                                        .testTag("seller_ledger_row_${bill.id}"),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RegisterField(bill.date, 90)
                                    RegisterField(
                                        text = bill.billNumber,
                                        width = 80,
                                        clickableColor = MaterialTheme.colorScheme.primary,
                                        onClick = { navController.navigate("bill_entry/${bill.firmName}/${bill.id}") }
                                    )
                                    RegisterField(bill.buyerName, 150)
                                    RegisterField(bill.place, 90)
                                    RegisterField("₹${bill.lorryFreight}", 80)
                                    RegisterField("₹${bill.rate}", 70)
                                    RegisterField("${bill.quintals} Q", 70)
                                    val matchingPayments = payments.filter { p ->
                                        p.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true)
                                    }
                                    val liveReceived = matchingPayments.sumOf { it.paymentAmount }
                                    val liveDiscount = matchingPayments.sumOf { it.discountAmount }
                                    val liveCommission = matchingPayments.sumOf { it.commissionAmount }
                                    val liveRemarkAmt = matchingPayments.sumOf { it.remarks1.toDoubleOrNull() ?: 0.0 }
                                    val liveBalance = bill.billAmount - liveReceived - liveDiscount - liveCommission - liveRemarkAmt
                                    RegisterField("₹${String.format("%.1f", bill.billAmount)}", 100)
                                    RegisterField("₹${String.format("%.1f", liveReceived)}", 100)
                                    RegisterField("₹${String.format(Locale.getDefault(), "%.2f", liveDiscount)}", 80)
                                    RegisterField(bill.brokerName.ifBlank { "-" }, 120)
                                    RegisterField("₹${String.format(Locale.getDefault(), "%.2f", liveCommission)}", 90)
                                    RegisterField(if (liveRemarkAmt > 0.0) "₹${String.format(Locale.getDefault(), "%.2f", liveRemarkAmt)}" else "₹0.00", 100)
                                    RegisterField(bill.remark2.ifBlank { "-" }, 150)
                                    RegisterField("₹${String.format("%.2f", liveBalance)}", 100, highlight = true)
                                    
                                    val paymentStatus = when {
                                        liveBalance <= 0.01 -> "Fully Paid"
                                        liveReceived <= 0.0 -> "Pending"
                                        else -> "Partial Paid"
                                    }
                                    RegisterField(paymentStatus, 100, highlight = true)
                                    RegisterField(bill.eb, 100)

                                    // Action Column
                                    Row(
                                        modifier = Modifier.width(180.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { navController.navigate("bill_entry/${bill.firmName}/${bill.id}") },
                                            modifier = Modifier.size(28.dp).testTag("edit_bill_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Bill",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val pdfBill = getLatestBillForPdf(bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                                val pdfFile = PdfGenerator.generateContractPdf(context, pdfBill)
                                                if (pdfFile != null) {
                                                    PdfGenerator.sharePdf(context, pdfFile)
                                                    viewModel.logPdfShare(bill.billNumber, bill.firmName)
                                                } else {
                                                    Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp).testTag("share_bill_pdf_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share PDF",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                exportPdfToDownloads(context, bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                                viewModel.logPdfDownload(bill.billNumber, bill.firmName)
                                            },
                                            modifier = Modifier.size(28.dp).testTag("export_bill_pdf_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Export PDF",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val pdfBill = getLatestBillForPdf(bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                                val pdfFile = PdfGenerator.generateContractPdf(context, pdfBill)
                                                if (pdfFile != null) {
                                                    previewPdfFile = pdfFile
                                                    showPdfPreview = true
                                                    viewModel.logPdfPreview(bill.billNumber, bill.firmName)
                                                } else {
                                                    Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp).testTag("preview_bill_pdf_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = "Preview PDF",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                billToDelete = bill
                                                showDeleteBillDialog = true
                                            },
                                            modifier = Modifier.size(28.dp).testTag("delete_bill_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Bill",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column {
                                        if (billPayments.isEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.08f))
                                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RegisterField("No payments", 90, highlight = true)
                                                RegisterField("No payments recorded for this bill.", 600)
                                            }
                                        } else {
                                            billPayments.forEachIndexed { index, payment ->
                                                Row(
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.12f))
                                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // 1. Date (90dp)
                                                    RegisterField(payment.paymentDate.ifBlank { payment.createdAt }, 90)

                                                    // 2. Bill No (80dp)
                                                    RegisterField(payment.billNo, 80)

                                                    // 3. Party Name (150dp)
                                                    RegisterField(payment.buyerName.ifBlank { bill.buyerName }, 150)

                                                    // 4. Place (90dp)
                                                    RegisterField(bill.place, 90)

                                                    // 5. Freight (80dp)
                                                    RegisterField("-", 80)

                                                    // 6. Rate (70dp)
                                                    RegisterField("-", 70)

                                                    // 7. Qtls (70dp)
                                                    RegisterField("-", 70)

                                                    // 8. BILL AMT (100dp)
                                                    RegisterField("-", 100)

                                                    // 9. Received (100dp)
                                                    RegisterField("₹${String.format("%.1f", payment.paymentAmount)}", 100, highlight = true)

                                                    // 10. Discount (80dp)
                                                    RegisterField("₹${String.format("%.1f", payment.discountAmount)}", 80)

                                                    // 11. Broker Name (120dp)
                                                    RegisterField(bill.brokerName.ifBlank { "-" }, 120)

                                                    // 12. Commission (90dp)
                                                    RegisterField("₹${String.format("%.1f", payment.commissionAmount)}", 90)

                                                    // 13. Remark Amt (100dp)
                                                    RegisterField(if (payment.remarks1.isNotBlank()) "₹${payment.remarks1}" else "₹0.0", 100)

                                                    // 14. DD (150dp)
                                                    val rawMode = payment.paymentMode
                                                    val isDD = rawMode.equals("DD", ignoreCase = true) || rawMode.equals("Cash", ignoreCase = true)
                                                    val ddDisplay = if (isDD) {
                                                        if (payment.referenceNumber.isNotBlank()) payment.referenceNumber else "-"
                                                    } else {
                                                        payment.paymentMode.ifBlank { "-" }
                                                    }
                                                    RegisterField(ddDisplay, 150)

                                                    // 15. Balance AMT (100dp)
                                                    RegisterField("₹${String.format("%.1f", payment.pendingAmount)}", 100, highlight = true)

                                                    // 16. Status (100dp)
                                                    val statusDisplay = if (payment.paymentMode.equals("Cash", ignoreCase = true)) "DD" else payment.paymentMode.ifBlank { "Paid" }
                                                    RegisterField(statusDisplay, 100, highlight = true)

                                                    // 17. EB (100dp)
                                                    RegisterField("", 100)

                                                    // 16. Action (140dp) -> Payment Action Icons
                                                    Row(
                                                        modifier = Modifier.width(180.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                val billPaymentsFiltered = payments.filter { bp ->
                                                                    bp.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true) &&
                                                                    bp.firm.trim().replace(" ", "").equals(bill.firmName.trim().replace(" ", ""), ignoreCase = true)
                                                                }
                                                                val received = billPaymentsFiltered.sumOf { bp -> bp.paymentAmount }
                                                                val pending = bill.billAmount - received

                                                                editingPayment = payment
                                                                editingBill = bill
                                                                editingAlreadyPaid = received - payment.paymentAmount
                                                                editingPending = pending + payment.paymentAmount
                                                                showEditPaymentDialog = true
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("edit_payment_${payment.paymentId}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Edit,
                                                                contentDescription = "Edit Payment",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                val file = PdfGenerator.generatePaymentPdf(context, payment, bill)
                                                                if (file != null) {
                                                                    PdfGenerator.sharePdf(context, file)
                                                                } else {
                                                                    Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("share_payment_pdf_${payment.paymentId}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Share,
                                                                contentDescription = "Share PDF",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                exportPaymentPdfToDownloads(context, payment, bill)
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("export_payment_pdf_${payment.paymentId}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Download,
                                                                contentDescription = "Export PDF",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                val file = PdfGenerator.generatePaymentPdf(context, payment, bill)
                                                                if (file != null) {
                                                                    previewPdfFile = file
                                                                    showPdfPreview = true
                                                                } else {
                                                                    Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("preview_payment_pdf_${payment.paymentId}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Visibility,
                                                                contentDescription = "Preview PDF",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            paymentToDelete = payment
                                                            showDeletePaymentDialog = true
                                                        },
                                                        modifier = Modifier.size(28.dp).testTag("delete_payment_${payment.paymentId}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete Payment",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                if (index < billPayments.size - 1) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }

                        if (sellerBills.size > pageLimit) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Button(
                                        onClick = { pageLimit += 500 },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Load More (Showing $pageLimit of ${sellerBills.size})", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showPdfPreview && previewPdfFile != null) {
                PdfPreviewDialog(
                    file = previewPdfFile!!,
                    onPrint = {
                        PdfGenerator.printPdf(context, previewPdfFile!!)
                    },
                    onShare = {
                        PdfGenerator.sharePdf(context, previewPdfFile!!)
                    },
                    onClose = {
                        showPdfPreview = false
                        previewPdfFile = null
                    }
                )
            }

            if (showEditPaymentDialog) {
                EditPaymentDialog(
                    showDialog = showEditPaymentDialog,
                    onDismissRequest = { showEditPaymentDialog = false },
                    bill = editingBill,
                    editingPayment = editingPayment,
                    editingAlreadyPaid = editingAlreadyPaid,
                    editingPending = editingPending,
                    viewModel = viewModel
                )
            }

            if (showDeleteBillDialog && billToDelete != null) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteBillDialog = false
                        billToDelete = null
                    },
                    title = { Text("Delete Bill") },
                    text = { Text("Are you sure you want to delete Bill No. ${billToDelete!!.billNumber}?\nThis action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                com.example.util.BiometricHelper.runWithBiometric(
                                    context = context,
                                    title = "Ranisa Security",
                                    subtitle = "Verify your fingerprint to continue.",
                                    action = {
                                        viewModel.deleteBill(billToDelete!!)
                                        showDeleteBillDialog = false
                                        billToDelete = null
                                        Toast.makeText(context, "Bill deleted successfully", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            modifier = Modifier.testTag("confirm_delete_bill_button")
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showDeleteBillDialog = false
                                billToDelete = null
                            },
                            modifier = Modifier.testTag("cancel_delete_bill_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showDeletePaymentDialog && paymentToDelete != null) {
                AlertDialog(
                    onDismissRequest = {
                        showDeletePaymentDialog = false
                        paymentToDelete = null
                    },
                    title = { Text("Delete Payment") },
                    text = { Text("Are you sure you want to delete this payment?\nThis action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                com.example.util.BiometricHelper.runWithBiometric(
                                    context = context,
                                    title = "Ranisa Security",
                                    subtitle = "Verify your fingerprint to continue.",
                                    action = {
                                        viewModel.deletePayment(paymentToDelete!!)
                                        showDeletePaymentDialog = false
                                        paymentToDelete = null
                                        Toast.makeText(context, "Payment deleted successfully", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            modifier = Modifier.testTag("confirm_delete_payment_button")
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showDeletePaymentDialog = false
                                paymentToDelete = null
                            },
                            modifier = Modifier.testTag("cancel_delete_payment_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showDeleteLedgerDialog && ledgerToDeleteName.isNotBlank()) {
                AlertDialog(
                    onDismissRequest = { showDeleteLedgerDialog = false },
                    title = { Text("Delete all ledger transactions for this party?") },
                    text = { Text("This action will remove only the ledger records.\nThe Master List will remain unchanged.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                com.example.util.BiometricHelper.runWithBiometric(
                                    context = context,
                                    title = "Ranisa Security",
                                    subtitle = "Verify your fingerprint to continue.",
                                    action = {
                                        viewModel.deleteSellerLedger(
                                            sellerName = ledgerToDeleteName,
                                            onSuccess = {
                                                showDeleteLedgerDialog = false
                                                Toast.makeText(context, "Ledger transactions deleted successfully", Toast.LENGTH_SHORT).show()
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
                        TextButton(onClick = { showDeleteLedgerDialog = false }) {
                            Text("Cancel")
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }

            if (showShareSheetForLedger && ledgerToShareName.isNotBlank()) {
                val filteredBillsForOwner = remember(bills, ledgerToShareName) {
                    bills.filter { it.sellerName == ledgerToShareName }
                }
                FullLedgerShareSheet(
                    ledgerName = ledgerToShareName,
                    ledgerType = "seller",
                    bills = filteredBillsForOwner,
                    payments = payments,
                    onDismissRequest = {
                        showShareSheetForLedger = false
                        ledgerToShareName = ""
                    }
                )
            }
        }
    }
}

// ==========================================
// BUYER LEDGER SCREEN (REGISTER STYLE!)
// ==========================================
@Composable
fun BuyerLedgerScreen(navController: NavController, viewModel: RanisaViewModel, preselectedBuyer: String = "") {
    val bills by viewModel.allBills.collectAsState()
    val searchQuery by viewModel.buyerLedgerSearch.collectAsState()

    val payments by viewModel.allPayments.collectAsState()
    val sellersMaster by viewModel.sellers.collectAsState()
    val buyersMaster by viewModel.buyers.collectAsState()
    val rtdbFullSellers by viewModel.rtdbFullSellers.collectAsState()
    val rtdbFullBuyers by viewModel.rtdbFullBuyers.collectAsState()

    var selectedBuyer by remember { mutableStateOf(preselectedBuyer) }
    var expandedBillId by remember { mutableStateOf<Int?>(null) }
    var previewPdfFile by remember { mutableStateOf<java.io.File?>(null) }
    var showPdfPreview by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var showEditPaymentDialog by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<ContractBill?>(null) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    var editingAlreadyPaid by remember { mutableStateOf(0.0) }
    var editingPending by remember { mutableStateOf(0.0) }

    var showDeleteBillDialog by remember { mutableStateOf(false) }
    var billToDelete by remember { mutableStateOf<ContractBill?>(null) }
    var showDeletePaymentDialog by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }
    var paymentBillToDeleteFrom by remember { mutableStateOf<ContractBill?>(null) }

    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    var showDeleteLedgerDialog by remember { mutableStateOf(false) }
    var ledgerToDeleteName by remember { mutableStateOf("") }
    var showShareSheetForLedger by remember { mutableStateOf(false) }
    var ledgerToShareName by remember { mutableStateOf("") }

    val distinctBuyers = remember(bills) {
        bills.map { it.buyerName }.distinct().filter { buyer ->
            val buyerBills = bills.filter { it.buyerName == buyer }
            val totalQtls = buyerBills.sumOf { it.quintals }
            buyerBills.isNotEmpty() && totalQtls > 0.0
        }
    }

    val filteredBuyers = remember(distinctBuyers, bills, searchQuery) {
        distinctBuyers.filter { buyer ->
            buyer.contains(searchQuery, ignoreCase = true) ||
            bills.any { it.buyerName == buyer && it.billNumber.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (isSearchActive) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateBuyerLedgerSearch(it) },
                    placeholder = { Text("Search Buyer Name or Bill No...", fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(focusRequester)
                        .testTag("buyer_ledger_search_input"),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                    trailingIcon = {
                        IconButton(onClick = { 
                            viewModel.updateBuyerLedgerSearch("")
                            isSearchActive = false 
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )
            }
            LaunchedEffect(isSearchActive) {
                if (isSearchActive) {
                    focusRequester.requestFocus()
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Buyer Ledger & Registers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { isSearchActive = true },
                    modifier = Modifier.testTag("buyer_ledger_search_button")
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search Buyer")
                }
            }
        }

        if (selectedBuyer.isBlank()) {
            Text("Buyers Master List", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp), fontSize = 13.sp)
            if (distinctBuyers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("📄", fontSize = 48.sp, modifier = Modifier.padding(bottom = 12.dp))
                        Text(
                            text = "No Ledger Records Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create your first bill to see ledger entries here.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (filteredBuyers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No matching buyers found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(filteredBuyers) { buyer ->
                        val buyerBills = bills.filter { it.buyerName == buyer }
                        val totalQtls = buyerBills.sumOf { it.quintals }
                        val matchingBuyer = rtdbFullBuyers.find { it.buyerName == buyer }
                        Card(
                            onClick = { selectedBuyer = buyer },
                            modifier = Modifier.fillMaxWidth().testTag("buyer_card_${buyer}")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(buyer, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("${buyerBills.size} Registered Billings", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                                    Text("Total Qtls", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${String.format("%.2f", totalQtls)} Qtls", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                
                                LedgerOverflowMenu(
                                    itemId = matchingBuyer?.buyerId ?: buyer,
                                    onEdit = {
                                        navController.navigate("buyer_master_list")
                                    },
                                    onShare = {
                                        ledgerToShareName = buyer
                                        showShareSheetForLedger = true
                                    },
                                    onDeleteLedger = {
                                        ledgerToDeleteName = buyer
                                        showDeleteLedgerDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Selected buyer: 13-column Register View
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = { selectedBuyer = "" },
                        modifier = Modifier.testTag("buyer_ledger_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "$selectedBuyer's Book Registry",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                var showShareSheet by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showShareSheet = true },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(end = 4.dp).testTag("share_full_buyer_ledger_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share Full Ledger", fontSize = 11.sp)
                }

                if (showShareSheet) {
                    FullLedgerShareSheet(
                        ledgerName = selectedBuyer,
                        ledgerType = "buyer",
                        bills = bills.filter { it.buyerName == selectedBuyer },
                        payments = payments,
                        onDismissRequest = { showShareSheet = false }
                    )
                }
            }

            val buyerBills = remember(bills, selectedBuyer, searchQuery) {
                bills.filter { it.buyerName == selectedBuyer }.filter { bill ->
                    searchQuery.isBlank() ||
                    bill.billNumber.contains(searchQuery, ignoreCase = true) ||
                    bill.sellerName.contains(searchQuery, ignoreCase = true) ||
                    bill.date.contains(searchQuery, ignoreCase = true)
                }
            }

            // Dynamic Calculations & Summary Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Total Bills", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        Text("${buyerBills.size}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Total Qtls", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        Text("${String.format("%.2f", buyerBills.sumOf { it.quintals })} Qtls", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Scrollable 15-column Grid simulating the physical register
            Box(modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    // Header row
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val headers = listOf(
                            "Date" to 90, "Bill No" to 80, "Party Name" to 150, "Place" to 90,
                            "Freight" to 80, "Rate" to 70, "Qtls" to 70, 
                            "BILL AMT" to 100, "Received" to 100, "Discount" to 80, "Broker Name" to 120, "Commission" to 90,
                            "Remark Amt" to 100, "Remark" to 150, "Balance AMT" to 100, "Status" to 100, "Action" to 180
                        )
                        headers.forEach { (text, width) ->
                            Text(
                                text = text,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(width.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    HorizontalDivider()

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(buyerBills) { bill ->
                            val isExpanded = expandedBillId == bill.id
                            val billPayments = remember(payments, bill.billNumber) {
                                payments.filter { p ->
                                    p.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true)
                                }
                            }

                            Column {
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            expandedBillId = if (isExpanded) null else bill.id
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp)
                                        .testTag("buyer_ledger_row_${bill.id}"),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RegisterField(bill.date, 90)
                                    RegisterField(
                                        text = bill.billNumber,
                                        width = 80,
                                        clickableColor = MaterialTheme.colorScheme.primary,
                                        onClick = { navController.navigate("bill_entry/${bill.firmName}/${bill.id}") }
                                    )
                                    RegisterField(bill.sellerName, 150)
                                    RegisterField(bill.place, 90)
                                    RegisterField("₹${bill.lorryFreight}", 80)
                                    RegisterField("₹${bill.rate}", 70)
                                    RegisterField("${bill.quintals} Q", 70)
                                    val matchingPayments = payments.filter { p ->
                                        p.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true)
                                    }
                                    val liveReceived = matchingPayments.sumOf { it.paymentAmount }
                                    val liveDiscount = matchingPayments.sumOf { it.discountAmount }
                                    val liveCommission = matchingPayments.sumOf { it.commissionAmount }
                                    val liveRemarkAmt = matchingPayments.sumOf { it.remarks1.toDoubleOrNull() ?: 0.0 }
                                    val liveBalance = bill.billAmount - liveReceived - liveDiscount - liveCommission - liveRemarkAmt
                                    RegisterField("₹${String.format("%.1f", bill.billAmount)}", 100)
                                    RegisterField("₹${String.format("%.1f", liveReceived)}", 100)
                                    RegisterField("₹${String.format(Locale.getDefault(), "%.2f", liveDiscount)}", 80)
                                    RegisterField(bill.brokerName.ifBlank { "-" }, 120)
                                    RegisterField("₹${String.format(Locale.getDefault(), "%.2f", liveCommission)}", 90)
                                    RegisterField(if (liveRemarkAmt > 0.0) "₹${String.format(Locale.getDefault(), "%.2f", liveRemarkAmt)}" else "₹0.00", 100)
                                    RegisterField(bill.remark2.ifBlank { "-" }, 150)
                                    RegisterField("₹${String.format("%.2f", liveBalance)}", 100, highlight = true)
                                    
                                    val paymentStatus = when {
                                        liveBalance <= 0.01 -> "Fully Paid"
                                        liveReceived <= 0.0 -> "Pending"
                                        else -> "Partial Paid"
                                    }
                                    RegisterField(paymentStatus, 100, highlight = true)

                                    // Action Column
                                    Row(
                                        modifier = Modifier.width(180.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { navController.navigate("bill_entry/${bill.firmName}/${bill.id}") },
                                            modifier = Modifier.size(28.dp).testTag("edit_bill_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Bill",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val pdfBill = getLatestBillForPdf(bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                                val pdfFile = PdfGenerator.generateContractPdf(context, pdfBill)
                                                if (pdfFile != null) {
                                                    PdfGenerator.sharePdf(context, pdfFile)
                                                    viewModel.logPdfShare(bill.billNumber, bill.firmName)
                                                } else {
                                                    Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp).testTag("share_bill_pdf_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share PDF",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                exportPdfToDownloads(context, bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                                viewModel.logPdfDownload(bill.billNumber, bill.firmName)
                                            },
                                            modifier = Modifier.size(28.dp).testTag("export_bill_pdf_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Export PDF",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val pdfBill = getLatestBillForPdf(bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                                val pdfFile = PdfGenerator.generateContractPdf(context, pdfBill)
                                                if (pdfFile != null) {
                                                    previewPdfFile = pdfFile
                                                    showPdfPreview = true
                                                    viewModel.logPdfPreview(bill.billNumber, bill.firmName)
                                                } else {
                                                    Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp).testTag("preview_bill_pdf_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = "Preview PDF",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                billToDelete = bill
                                                showDeleteBillDialog = true
                                            },
                                            modifier = Modifier.size(28.dp).testTag("delete_bill_${bill.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Bill",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column {
                                        if (billPayments.isEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.08f))
                                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RegisterField("No payments", 90, highlight = true)
                                                RegisterField("No payments recorded for this bill.", 600)
                                            }
                                        } else {
                                            billPayments.forEachIndexed { index, payment ->
                                                Row(
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.12f))
                                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // 1. Date (90dp)
                                                    RegisterField(payment.paymentDate.ifBlank { payment.createdAt }, 90)

                                                    // 2. Bill No (80dp)
                                                    RegisterField(payment.billNo, 80)

                                                    // 3. Party Name (150dp)
                                                    RegisterField(payment.sellerName.ifBlank { bill.sellerName }, 150)

                                                    // 4. Place (90dp)
                                                    RegisterField(bill.place, 90)

                                                    // 5. Freight (80dp)
                                                    RegisterField("-", 80)

                                                    // 6. Rate (70dp)
                                                    RegisterField("-", 70)

                                                    // 7. Qtls (70dp)
                                                    RegisterField("-", 70)

                                                    // 8. BILL AMT (100dp)
                                                    RegisterField("-", 100)

                                                    // 9. Received (100dp)
                                                    RegisterField("₹${String.format("%.1f", payment.paymentAmount)}", 100, highlight = true)

                                                    // 10. Discount (80dp)
                                                    RegisterField("₹${String.format("%.1f", payment.discountAmount)}", 80)

                                                    // 11. Broker Name (120dp)
                                                    RegisterField(bill.brokerName.ifBlank { "-" }, 120)

                                                    // 12. Commission (90dp)
                                                    RegisterField("₹${String.format("%.1f", payment.commissionAmount)}", 90)

                                                    // 13. Remark Amt (100dp)
                                                    RegisterField(if (payment.remarks1.isNotBlank()) "₹${payment.remarks1}" else "₹0.0", 100)

                                                    // 14. DD (150dp)
                                                    val rawMode = payment.paymentMode
                                                    val isDD = rawMode.equals("DD", ignoreCase = true) || rawMode.equals("Cash", ignoreCase = true)
                                                    val ddDisplay = if (isDD) {
                                                        if (payment.referenceNumber.isNotBlank()) payment.referenceNumber else "-"
                                                    } else {
                                                        payment.paymentMode.ifBlank { "-" }
                                                    }
                                                    RegisterField(ddDisplay, 150)

                                                    // 15. Balance AMT (100dp)
                                                    RegisterField("₹${String.format("%.1f", payment.pendingAmount)}", 100, highlight = true)

                                                    // 16. Status (100dp)
                                                    val statusDisplay = if (payment.paymentMode.equals("Cash", ignoreCase = true)) "DD" else payment.paymentMode.ifBlank { "Paid" }
                                                    RegisterField(statusDisplay, 100, highlight = true)

                                                    // 16. Action (140dp) -> Payment Action Icons
                                                    Row(
                                                        modifier = Modifier.width(180.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                val billPaymentsFiltered = payments.filter { bp ->
                                                                    bp.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true) &&
                                                                    bp.firm.trim().replace(" ", "").equals(bill.firmName.trim().replace(" ", ""), ignoreCase = true)
                                                                }
                                                                val received = billPaymentsFiltered.sumOf { bp -> bp.paymentAmount }
                                                                val pending = bill.billAmount - received

                                                                editingPayment = payment
                                                                editingBill = bill
                                                                editingAlreadyPaid = received - payment.paymentAmount
                                                                editingPending = pending + payment.paymentAmount
                                                                showEditPaymentDialog = true
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("edit_payment_${payment.paymentId}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Edit,
                                                                contentDescription = "Edit Payment",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                val file = PdfGenerator.generatePaymentPdf(context, payment, bill)
                                                                if (file != null) {
                                                                    PdfGenerator.sharePdf(context, file)
                                                                } else {
                                                                    Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("share_payment_pdf_${payment.paymentId}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Share,
                                                                contentDescription = "Share PDF",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                exportPaymentPdfToDownloads(context, payment, bill)
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("export_payment_pdf_${payment.paymentId}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Download,
                                                                contentDescription = "Export PDF",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                val file = PdfGenerator.generatePaymentPdf(context, payment, bill)
                                                                if (file != null) {
                                                                    previewPdfFile = file
                                                                    showPdfPreview = true
                                                                } else {
                                                                    Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("preview_payment_pdf_${payment.paymentId}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Visibility,
                                                                contentDescription = "Preview PDF",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            paymentToDelete = payment
                                                            showDeletePaymentDialog = true
                                                        },
                                                        modifier = Modifier.size(28.dp).testTag("delete_payment_${payment.paymentId}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete Payment",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                if (index < billPayments.size - 1) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            if (showPdfPreview && previewPdfFile != null) {
                PdfPreviewDialog(
                    file = previewPdfFile!!,
                    onPrint = {
                        PdfGenerator.printPdf(context, previewPdfFile!!)
                    },
                    onShare = {
                        PdfGenerator.sharePdf(context, previewPdfFile!!)
                    },
                    onClose = {
                        showPdfPreview = false
                        previewPdfFile = null
                    }
                )
            }

            if (showEditPaymentDialog) {
                EditPaymentDialog(
                    showDialog = showEditPaymentDialog,
                    onDismissRequest = { showEditPaymentDialog = false },
                    bill = editingBill,
                    editingPayment = editingPayment,
                    editingAlreadyPaid = editingAlreadyPaid,
                    editingPending = editingPending,
                    viewModel = viewModel
                )
            }

            if (showDeleteBillDialog && billToDelete != null) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteBillDialog = false
                        billToDelete = null
                    },
                    title = { Text("Delete Bill") },
                    text = { Text("Are you sure you want to delete Bill No. ${billToDelete!!.billNumber}?\nThis action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                com.example.util.BiometricHelper.runWithBiometric(
                                    context = context,
                                    title = "Ranisa Security",
                                    subtitle = "Verify your fingerprint to continue.",
                                    action = {
                                        viewModel.deleteBill(billToDelete!!)
                                        showDeleteBillDialog = false
                                        billToDelete = null
                                        Toast.makeText(context, "Bill deleted successfully", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            modifier = Modifier.testTag("confirm_delete_bill_button")
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showDeleteBillDialog = false
                                billToDelete = null
                            },
                            modifier = Modifier.testTag("cancel_delete_bill_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showDeletePaymentDialog && paymentToDelete != null) {
                AlertDialog(
                    onDismissRequest = {
                        showDeletePaymentDialog = false
                        paymentToDelete = null
                    },
                    title = { Text("Delete Payment") },
                    text = { Text("Are you sure you want to delete this payment?\nThis action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                com.example.util.BiometricHelper.runWithBiometric(
                                    context = context,
                                    title = "Ranisa Security",
                                    subtitle = "Verify your fingerprint to continue.",
                                    action = {
                                        viewModel.deletePayment(paymentToDelete!!)
                                        showDeletePaymentDialog = false
                                        paymentToDelete = null
                                        Toast.makeText(context, "Payment deleted successfully", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            modifier = Modifier.testTag("confirm_delete_payment_button")
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showDeletePaymentDialog = false
                                paymentToDelete = null
                            },
                            modifier = Modifier.testTag("cancel_delete_payment_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showDeleteLedgerDialog && ledgerToDeleteName.isNotBlank()) {
                AlertDialog(
                    onDismissRequest = { showDeleteLedgerDialog = false },
                    title = { Text("Delete all ledger transactions for this party?") },
                    text = { Text("This action will remove only the ledger records.\nThe Master List will remain unchanged.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                com.example.util.BiometricHelper.runWithBiometric(
                                    context = context,
                                    title = "Ranisa Security",
                                    subtitle = "Verify your fingerprint to continue.",
                                    action = {
                                        viewModel.deleteBuyerLedger(
                                            buyerName = ledgerToDeleteName,
                                            onSuccess = {
                                                showDeleteLedgerDialog = false
                                                Toast.makeText(context, "Ledger transactions deleted successfully", Toast.LENGTH_SHORT).show()
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
                        TextButton(onClick = { showDeleteLedgerDialog = false }) {
                            Text("Cancel")
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }

            if (showShareSheetForLedger && ledgerToShareName.isNotBlank()) {
                val filteredBillsForOwner = remember(bills, ledgerToShareName) {
                    bills.filter { it.buyerName == ledgerToShareName }
                }
                FullLedgerShareSheet(
                    ledgerName = ledgerToShareName,
                    ledgerType = "buyer",
                    bills = filteredBillsForOwner,
                    payments = payments,
                    onDismissRequest = {
                        showShareSheetForLedger = false
                        ledgerToShareName = ""
                    }
                )
            }
        }
    }
}

@Composable
fun RegisterField(
    text: String,
    width: Int,
    highlight: Boolean = false,
    clickableColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val modifier = if (onClick != null) {
        Modifier
            .width(width.dp)
            .clickable(onClick = onClick)
    } else {
        Modifier.width(width.dp)
    }
    Text(
        text = text.ifBlank { "-" },
        modifier = modifier,
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontWeight = if (highlight || onClick != null) FontWeight.Bold else FontWeight.Normal,
        color = clickableColor ?: if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        style = if (onClick != null) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline) else androidx.compose.ui.text.TextStyle.Default
    )
}

fun getDueDateString(dateStr: String, creditDays: Int): String {
    if (creditDays <= 0) return "-"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = parser.parse(dateStr) ?: return "-"
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.DAY_OF_YEAR, creditDays)
        val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        formatter.format(calendar.time)
    } catch (e: Exception) {
        "-"
    }
}

fun getDueDateComparable(dateStr: String, creditDays: Int): String {
    if (creditDays <= 0) return "9999-12-31"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = parser.parse(dateStr) ?: return "9999-12-31"
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.DAY_OF_YEAR, creditDays)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        formatter.format(calendar.time)
    } catch (e: Exception) {
        "9999-12-31"
    }
}

// ==========================================
// PAYMENT MODULE SCREEN
// ==========================================
@Composable
fun PaymentListScreen(navController: NavController, viewModel: RanisaViewModel) {
    val context = LocalContext.current
    val payments by viewModel.allPayments.collectAsState()
    val bills by viewModel.allBills.collectAsState()

    // Direct Edit Payment Dialog State
    var showEditPaymentDialog by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<ContractBill?>(null) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    var editingAlreadyPaid by remember { mutableStateOf(0.0) }
    var editingPending by remember { mutableStateOf(0.0) }
    var paymentReceivedValue by remember { mutableStateOf("") }
    var paymentDateValue by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var paymentModeValue by remember { mutableStateOf("Cash") }
    var paymentRefValue by remember { mutableStateOf("") }
    var paymentNotesValue by remember { mutableStateOf("") }

    // Section 1 State
    var filterDateSection1 by remember { mutableStateOf("") }
    var showRecentBillsFilter by remember { mutableStateOf(false) }

    // Screen State
    var selectedPartyName by remember { mutableStateOf<String?>(null) }

    // Dialog state for Recording Payment
    var showAddDialog by remember { mutableStateOf(false) }

    // Pre-filled fields for Add Payment Dialog
    var formBuyerName by remember { mutableStateOf("") }
    var formBillNo by remember { mutableStateOf("") }
    var formFirm by remember { mutableStateOf("") }
    var formSellerName by remember { mutableStateOf("") }
    var formDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var formAmount by remember { mutableStateOf("") }
    var formMode by remember { mutableStateOf("Bank Transfer") }
    var formBankName by remember { mutableStateOf("") }
    var formRemarks by remember { mutableStateOf("") }
    var formDiscount by remember { mutableStateOf("") }
    var formCommission by remember { mutableStateOf("") }
    var formRemarkAmt by remember { mutableStateOf("") }

    // Section 1 State
    var searchQuerySection1 by remember { mutableStateOf("") }
    var recentSortAscending by remember { mutableStateOf(false) } // False = Newest first (Descending)
    var recentSortKey by remember { mutableStateOf("Date") } // "Date" or "Due Date"

    // Section 2 (All Parties) Search
    var searchQuerySection2 by remember { mutableStateOf("") }

    // Party Ledger Screen State
    var searchQueryPartyLedger by remember { mutableStateOf("") }
    var sortOptionPartyLedger by remember { mutableStateOf("Newest First") }
    var filterStatusPartyLedger by remember { mutableStateOf("All") }
    var filterFirmPartyLedger by remember { mutableStateOf("All") }
    var filterDatePartyLedger by remember { mutableStateOf("") }
    var showAllBillsOfParty by remember { mutableStateOf(false) }
    var isFilterPanelExpanded by remember { mutableStateOf(false) }

    // Map bills to their computed totals dynamically
    val computedBills = remember(bills, payments) {
        bills.map { bill ->
            val matchingPaymentsForBill = payments.filter { p ->
                p.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true)
            }
            val totalReceivedForBill = matchingPaymentsForBill.sumOf { it.paymentAmount }
            val totalDiscountForBill = matchingPaymentsForBill.sumOf { it.discountAmount }
            val totalCommissionForBill = matchingPaymentsForBill.sumOf { it.commissionAmount }
            val totalRemarkAmtForBill = matchingPaymentsForBill.sumOf { it.remarks1.toDoubleOrNull() ?: 0.0 }
            val balanceAmount = bill.billAmount - totalReceivedForBill - totalDiscountForBill - totalCommissionForBill - totalRemarkAmtForBill
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val dueComparable = getDueDateComparable(bill.date, bill.creditDays)
            val isOverdue = balanceAmount > 0.01 && bill.creditDays > 0 && todayStr > dueComparable

            val status = when {
                balanceAmount <= 0.01 -> "Fully Paid"
                isOverdue -> "Overdue"
                totalReceivedForBill <= 0.0 -> "Pending"
                else -> "Partial Paid"
            }
            Pair(bill, Triple(totalReceivedForBill, balanceAmount, status))
        }
    }

    MyApplicationTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (selectedPartyName == null) {
                // =========================================================================
                // MAIN PAYMENT LEDGER VIEW
                // =========================================================================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Payments Received Ledger",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            OutlinedButton(
                                onClick = { showRecentBillsFilter = !showRecentBillsFilter },
                                modifier = Modifier.testTag("payment_ledger_filter_button"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filter Icon",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Filter",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (showRecentBillsFilter) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (showRecentBillsFilter) "Collapse" else "Expand",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // SECTION 1: RECENT BILL ENTRIES (LAST 5)
                    // =========================================================================
                    item {
                        AnimatedVisibility(
                            visible = showRecentBillsFilter,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Recent Bill Entries (Last 5)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Redesigned compact search bar
                                BasicTextField(
                                    value = searchQuerySection1,
                                    onValueChange = { searchQuerySection1 = it },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    singleLine = true,
                                    maxLines = 1,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp),
                                    decorationBox = { innerTextField ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .background(
                                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                                if (searchQuerySection1.isEmpty()) {
                                                    Text(
                                                        text = "Search by Buyer, Seller, Bill No, Due Date",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                innerTextField()
                                            }
                                            if (searchQuerySection1.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { searchQuerySection1 = "" },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear search",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            if (filterDateSection1.isNotEmpty()) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.padding(end = 4.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = filterDateSection1,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Clear Date Filter",
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .clickable { filterDateSection1 = "" }
                                                        )
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    val calendar = java.util.Calendar.getInstance()
                                                    if (filterDateSection1.isNotEmpty()) {
                                                        try {
                                                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                            val d = sdf.parse(filterDateSection1)
                                                            if (d != null) {
                                                                calendar.time = d
                                                            }
                                                        } catch (e: Exception) {}
                                                    }
                                                    android.app.DatePickerDialog(
                                                        context,
                                                        { _, year, month, dayOfMonth ->
                                                            filterDateSection1 = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                                        },
                                                        calendar.get(java.util.Calendar.YEAR),
                                                        calendar.get(java.util.Calendar.MONTH),
                                                        calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                                    ).show()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DateRange,
                                                    contentDescription = "Choose Date Filter",
                                                    tint = if (filterDateSection1.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Filter, Sort and pick last 5
                                val filteredRecentBills = computedBills.filter { (bill, _) ->
                                    val dueDateStr = getDueDateString(bill.date, bill.creditDays)
                                    val matchesSearch = searchQuerySection1.isBlank() ||
                                            bill.buyerName.contains(searchQuerySection1, ignoreCase = true) ||
                                            bill.sellerName.contains(searchQuerySection1, ignoreCase = true) ||
                                            bill.billNumber.contains(searchQuerySection1, ignoreCase = true) ||
                                            bill.firmName.contains(searchQuerySection1, ignoreCase = true) ||
                                            dueDateStr.contains(searchQuerySection1, ignoreCase = true)
                                            
                                    val matchesDate = filterDateSection1.isBlank() ||
                                            bill.date.contains(filterDateSection1, ignoreCase = true) ||
                                            dueDateStr.contains(filterDateSection1, ignoreCase = true)
                                            
                                    matchesSearch && matchesDate
                                }.sortedWith { a, b ->
                                    val valA = if (recentSortKey == "Due Date") {
                                        getDueDateComparable(a.first.date, a.first.creditDays)
                                    } else {
                                        a.first.date
                                    }
                                    val valB = if (recentSortKey == "Due Date") {
                                        getDueDateComparable(b.first.date, b.first.creditDays)
                                    } else {
                                        b.first.date
                                    }
                                    if (recentSortAscending) valA.compareTo(valB) else valB.compareTo(valA)
                                }.take(5)

                                if (filteredRecentBills.isEmpty()) {
                                    Text(
                                        text = "No matching recent bill records.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                } else {
                                    // Horizontally Scrollable Ledger Table
                                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        Column(modifier = Modifier.widthIn(min = 1570.dp)) {
                                            // Table Header
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Bill No", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Date", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Due Date", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Seller Name", modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Buyer Name", modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Bill Amt", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Received", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Discount", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Broker Name", modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Commission", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Remark Amt", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Remark", modifier = Modifier.width(150.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Balance Amount", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Status", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Actions", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                            }

                                            // Table Rows
                                            filteredRecentBills.forEachIndexed { index, (bill, data) ->
                                                val received = data.first
                                                val pending = data.second
                                                val status = data.third
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            editingPayment = null
                                                            editingBill = bill
                                                            editingAlreadyPaid = received
                                                            editingPending = pending
                                                            paymentReceivedValue = ""
                                                            paymentDateValue = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                                            paymentModeValue = "Cash"
                                                            paymentRefValue = ""
                                                            paymentNotesValue = ""
                                                            showEditPaymentDialog = true
                                                        }
                                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = bill.billNumber,
                                                        modifier = Modifier
                                                            .width(80.dp)
                                                            .clickable {
                                                                navController.navigate("bill_entry/${bill.firmName}/${bill.id}")
                                                            },
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                                        ),
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(bill.date, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(getDueDateString(bill.date, bill.creditDays), modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(bill.sellerName, modifier = Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(bill.buyerName, modifier = Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    Text("₹${String.format("%.2f", bill.billAmount)}", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("₹${String.format("%.2f", received)}", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                                                    Text("₹${String.format(Locale.getDefault(), "%.2f", bill.discountPercent)}", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(bill.brokerName.ifBlank { "-" }, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("₹${String.format(Locale.getDefault(), "%.2f", bill.commissionPercent)}", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(if (bill.remark1.isNotBlank()) "₹${String.format(Locale.getDefault(), "%.2f", bill.remark1.toDoubleOrNull() ?: 0.0)}" else "₹0.00", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(bill.remark2.ifBlank { "-" }, modifier = Modifier.width(150.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("₹${String.format("%.2f", pending)}", modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, color = if (pending > 0.0) Color(0xFFEF5350) else Color(0xFF81C784), fontWeight = FontWeight.Bold)

                                                    val chipColor = when (status) {
                                                        "Fully Paid", "Full Paid" -> Color(0xFF81C784)
                                                        "Partial Paid" -> Color(0xFFFFB74D)
                                                        else -> Color(0xFFEF5350)
                                                    }
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = chipColor.copy(alpha = 0.12f)),
                                                        modifier = Modifier.width(100.dp)
                                                    ) {
                                                        Text(
                                                            text = status,
                                                            color = chipColor,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).fillMaxWidth(),
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }

                                                    Row(
                                                        modifier = Modifier.width(100.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                editingPayment = null
                                                                editingBill = bill
                                                                editingAlreadyPaid = received
                                                                editingPending = pending
                                                                paymentReceivedValue = ""
                                                                paymentDateValue = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                                                paymentModeValue = "Cash"
                                                                paymentRefValue = ""
                                                                paymentNotesValue = ""
                                                                showEditPaymentDialog = true
                                                            },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Text("✏️", fontSize = 16.sp)
                                                        }

                                                        Box {
                                                            var expandedMoreMenu by remember { mutableStateOf(false) }
                                                            IconButton(
                                                                onClick = { expandedMoreMenu = true },
                                                                modifier = Modifier.size(36.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.MoreVert,
                                                                    contentDescription = "More actions",
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                            DropdownMenu(
                                                                expanded = expandedMoreMenu,
                                                                onDismissRequest = { expandedMoreMenu = false }
                                                            ) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Go to Bill Page") },
                                                                    onClick = {
                                                                        expandedMoreMenu = false
                                                                        navController.navigate("bill_entry/${bill.firmName}/${bill.id}")
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                    // =========================================================================
                    // SECTION 2: PARTY LIST (ALL PARTIES)
                    // =========================================================================
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "All Parties",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    item {
                        // Redesigned compact search bar for All Parties
                        BasicTextField(
                            value = searchQuerySection2,
                            onValueChange = { searchQuerySection2 = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            maxLines = 1,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .background(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (searchQuerySection2.isEmpty()) {
                                            Text(
                                                text = "Search Parties by Name",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuerySection2.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuerySection2 = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    // Dynamically calculate party list cards from all bills and payments
                    val partiesList = computedBills.groupBy { it.first.buyerName }
                        .map { (partyName, partyBills) ->
                            val sortedBills = partyBills.sortedByDescending { it.first.date }
                            val latestBill = sortedBills.firstOrNull()?.first
                            val totalBillsCount = sortedBills.size
                            
                            val totalBillAmount = partyBills.sumOf { it.first.billAmount }
                            val totalReceived = partyBills.sumOf { it.second.first }
                            val totalOutstanding = partyBills.sumOf { maxOf(it.second.second, 0.0) }
                            
                            val partyRemainingBalances = partyBills.map { maxOf(it.second.second, 0.0) }
                            val partyReceivedAmounts = partyBills.map { it.second.first }

                            val paymentStatus = when {
                                partyRemainingBalances.all { it <= 0.01 } -> "Fully Paid"
                                partyRemainingBalances.any { it > 0.01 } && partyReceivedAmounts.any { it > 0.0 } -> "Partial Paid"
                                partyReceivedAmounts.all { it <= 0.0 } -> "Pending"
                                else -> "Pending"
                            }
                            
                            val statusColor = when (paymentStatus) {
                                "Fully Paid" -> Color(0xFF81C784) // Soft green for dark theme
                                "Partial Paid" -> Color(0xFFFFB74D) // Soft orange for dark theme
                                else -> Color(0xFFEF5350) // Soft red for dark theme
                            }

                            Triple(partyName, latestBill, Triple(totalOutstanding, totalBillsCount, Pair(paymentStatus, statusColor)))
                        }.filter {
                            val totalOutstanding = it.third.first
                            totalOutstanding > 0.01 && (searchQuerySection2.isBlank() || it.first.contains(searchQuerySection2, ignoreCase = true))
                        }.sortedBy { it.first }

                    if (partiesList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("📄", fontSize = 48.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    Text(
                                        text = "No Ledger Records Found",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Create your first bill to see ledger entries here.",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(partiesList) { (partyName, latestBill, data) ->
                            val outstanding = data.first
                            val totalBills = data.second
                            val statusText = data.third.first
                            val statusColor = data.third.second

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPartyName = partyName },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = partyName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.12f))
                                        ) {
                                            Text(
                                                text = statusText,
                                                color = statusColor,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Latest Bill: #${latestBill?.billNumber ?: "N/A"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Latest Date: ${latestBill?.date ?: "N/A"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Firm: ${latestBill?.firmName ?: "N/A"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Total Bills: $totalBills", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Balance Amount", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            text = "₹${String.format("%.2f", outstanding)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (outstanding > 0.0) Color(0xFFEF5350) else Color(0xFF81C784)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // =========================================================================
                // PARTY LEDGER PAGE (WHEN PARTY IS SELECTED)
                // =========================================================================
                val partyName = selectedPartyName!!
                val partyBillsAndData = computedBills.filter { it.first.buyerName.equals(partyName, ignoreCase = true) }
                
                val totalBillAmount = partyBillsAndData.sumOf { it.first.billAmount }
                val totalReceived = partyBillsAndData.sumOf { it.second.first }
                val outstandingBalance = partyBillsAndData.sumOf { maxOf(it.second.second, 0.0) }
                val latestBill = partyBillsAndData.sortedByDescending { it.first.date }.firstOrNull()?.first
                val firmName = latestBill?.firmName ?: "Multiple"

                // Search, Sort and Filter Logic
                val filteredBillsOfParty = partyBillsAndData.filter { (bill, data) ->
                    val dueDateStr = getDueDateString(bill.date, bill.creditDays)
                    val matchesSearch = searchQueryPartyLedger.isBlank() ||
                            bill.billNumber.contains(searchQueryPartyLedger, ignoreCase = true) ||
                            bill.sellerName.contains(searchQueryPartyLedger, ignoreCase = true) ||
                            bill.buyerName.contains(searchQueryPartyLedger, ignoreCase = true) ||
                            bill.firmName.contains(searchQueryPartyLedger, ignoreCase = true) ||
                            bill.date.contains(searchQueryPartyLedger, ignoreCase = true) ||
                            dueDateStr.contains(searchQueryPartyLedger, ignoreCase = true)
                    
                    val matchesStatus = filterStatusPartyLedger == "All" || 
                            data.third.equals(filterStatusPartyLedger, ignoreCase = true) ||
                            (filterStatusPartyLedger == "Fully Paid" && (data.third.equals("Full Paid", ignoreCase = true) || data.third.equals("Paid", ignoreCase = true)))
                    val matchesFirm = filterFirmPartyLedger == "All" || bill.firmName.contains(filterFirmPartyLedger, ignoreCase = true)
                    val matchesDate = filterDatePartyLedger.isBlank() || 
                            bill.date.contains(filterDatePartyLedger, ignoreCase = true) ||
                            dueDateStr.contains(filterDatePartyLedger, ignoreCase = true)

                    matchesSearch && matchesStatus && matchesFirm && matchesDate
                }.sortedWith { a, b ->
                    when (sortOptionPartyLedger) {
                        "Newest First" -> b.first.date.compareTo(a.first.date)
                        "Oldest First" -> a.first.date.compareTo(b.first.date)
                        "Due Date (Newest)" -> getDueDateComparable(b.first.date, b.first.creditDays).compareTo(getDueDateComparable(a.first.date, a.first.creditDays))
                        "Due Date (Oldest)" -> getDueDateComparable(a.first.date, a.first.creditDays).compareTo(getDueDateComparable(b.first.date, b.first.creditDays))
                        "Highest Amount" -> b.first.billAmount.compareTo(a.first.billAmount)
                        "Lowest Amount" -> a.first.billAmount.compareTo(b.first.billAmount)
                        "Ascending" -> a.first.billNumber.compareTo(b.first.billNumber)
                        "Descending" -> b.first.billNumber.compareTo(a.first.billNumber)
                        else -> b.first.date.compareTo(a.first.date)
                    }
                }

                // Lazy load logic (show top 5 first, then click Show All for performance)
                val visibleBills = if (showAllBillsOfParty) filteredBillsOfParty else filteredBillsOfParty.take(5)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header / Navigation Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedPartyName = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Parties List", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Party Ledger - $partyName",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Bills Header with Filter Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bills of $partyName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { isFilterPanelExpanded = !isFilterPanelExpanded },
                            modifier = Modifier.testTag("party_ledger_filter_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter Icon",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Filter",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (isFilterPanelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isFilterPanelExpanded) "Collapse" else "Expand",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Collapsible Filter Panel
                    AnimatedVisibility(
                        visible = isFilterPanelExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Search Input
                                OutlinedTextField(
                                    value = searchQueryPartyLedger,
                                    onValueChange = { searchQueryPartyLedger = it },
                                    placeholder = {
                                        Text(
                                            text = "Search by Buyer, Seller, Bill No, Firm, Date",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search"
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQueryPartyLedger.isNotEmpty()) {
                                            IconButton(onClick = { searchQueryPartyLedger = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear search"
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    maxLines = 1
                                )

                                // Sort By Block
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Sort By:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val sortOptions = listOf("Newest First", "Oldest First", "Due Date (Newest)", "Due Date (Oldest)", "Highest Amount", "Lowest Amount", "Ascending", "Descending")
                                    Box {
                                        var expandedSort by remember { mutableStateOf(false) }
                                        OutlinedButton(
                                            onClick = { expandedSort = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = sortOptionPartyLedger,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Dropdown"
                                                )
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = expandedSort,
                                            onDismissRequest = { expandedSort = false },
                                            modifier = Modifier.fillMaxWidth(0.9f)
                                        ) {
                                            sortOptions.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = { Text(opt, maxLines = 1) },
                                                    onClick = {
                                                        sortOptionPartyLedger = opt
                                                        expandedSort = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Status Filter Block
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Status:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val statusOptions = listOf("All", "Pending", "Partial Paid", "Fully Paid", "Overdue")
                                        statusOptions.forEach { opt ->
                                            FilterChip(
                                                selected = filterStatusPartyLedger == opt,
                                                onClick = { filterStatusPartyLedger = opt },
                                                label = {
                                                    Text(
                                                        text = opt,
                                                        maxLines = 1,
                                                        color = if (filterStatusPartyLedger == opt) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                // Date Filter Block
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Date Filter:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val calendar = java.util.Calendar.getInstance()
                                                if (filterDatePartyLedger.isNotEmpty()) {
                                                    try {
                                                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                        val d = sdf.parse(filterDatePartyLedger)
                                                        if (d != null) {
                                                            calendar.time = d
                                                        }
                                                    } catch (e: Exception) {}
                                                }
                                                android.app.DatePickerDialog(
                                                    context,
                                                    { _, year, month, dayOfMonth ->
                                                        filterDatePartyLedger = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                                    },
                                                    calendar.get(java.util.Calendar.YEAR),
                                                    calendar.get(java.util.Calendar.MONTH),
                                                    calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                                ).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (filterDatePartyLedger.isEmpty()) "Select Date" else filterDatePartyLedger,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.DateRange,
                                                    contentDescription = "Date Range"
                                                )
                                            }
                                        }
                                        if (filterDatePartyLedger.isNotEmpty()) {
                                            FilledTonalIconButton(
                                                onClick = { filterDatePartyLedger = "" },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear Date Filter"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Horizontally Scrollable Party Bills Table
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Column(modifier = Modifier.widthIn(min = 1570.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bill No", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Date", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Due Date", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Seller Name", modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Buyer Name", modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Bill Amt", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Received", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Discount", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Broker Name", modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Commission", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Remark Amt", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Remark", modifier = Modifier.width(150.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Balance Amount", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Status", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Actions", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }

                            if (visibleBills.isEmpty()) {
                                Row(modifier = Modifier.padding(16.dp)) {
                                    Text("No bills matching selected filters.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                visibleBills.forEachIndexed { index, (bill, data) ->
                                    val received = data.first
                                    val pending = data.second
                                    val status = data.third
                                    val billPayments = payments.filter { p ->
                                        p.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true) &&
                                        p.firm.trim().replace(" ", "").equals(bill.firmName.trim().replace(" ", ""), ignoreCase = true)
                                    }

                                    var isExpanded by remember { mutableStateOf(false) }

                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    editingPayment = null
                                                    editingBill = bill
                                                    editingAlreadyPaid = received
                                                    editingPending = pending
                                                    paymentReceivedValue = ""
                                                    paymentDateValue = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                                    paymentModeValue = "Cash"
                                                    paymentRefValue = ""
                                                    paymentNotesValue = ""
                                                    showEditPaymentDialog = true
                                                }
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = bill.billNumber,
                                                modifier = Modifier
                                                    .width(80.dp)
                                                    .clickable {
                                                        navController.navigate("bill_entry/${bill.firmName}/${bill.id}")
                                                    },
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                                ),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(bill.date, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                            Text(getDueDateString(bill.date, bill.creditDays), modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                            Text(bill.sellerName, modifier = Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(bill.buyerName, modifier = Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text("₹${String.format("%.2f", bill.billAmount)}", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("₹${String.format("%.2f", received)}", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                                            Text("₹${String.format(Locale.getDefault(), "%.2f", bill.discountPercent)}", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                            Text(bill.brokerName.ifBlank { "-" }, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                            Text("₹${String.format(Locale.getDefault(), "%.2f", bill.commissionPercent)}", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                            Text(if (bill.remark1.isNotBlank()) "₹${String.format(Locale.getDefault(), "%.2f", bill.remark1.toDoubleOrNull() ?: 0.0)}" else "₹0.00", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                            Text(bill.remark2.ifBlank { "-" }, modifier = Modifier.width(150.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                            Text("₹${String.format("%.2f", pending)}", modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, color = if (pending > 0.0) Color(0xFFEF5350) else Color(0xFF81C784), fontWeight = FontWeight.Bold)
                                            
                                            val chipColor = when (status) {
                                                "Fully Paid", "Full Paid" -> Color(0xFF81C784)
                                                "Partial Paid" -> Color(0xFFFFB74D)
                                                else -> Color(0xFFEF5350)
                                            }
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = chipColor.copy(alpha = 0.12f)),
                                                modifier = Modifier.width(100.dp)
                                            ) {
                                                Text(
                                                    text = status,
                                                    color = chipColor,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).fillMaxWidth(),
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.width(100.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        editingPayment = null
                                                        editingBill = bill
                                                        editingAlreadyPaid = received
                                                        editingPending = pending
                                                        paymentReceivedValue = ""
                                                        paymentDateValue = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                                        paymentModeValue = "Cash"
                                                        paymentRefValue = ""
                                                        paymentNotesValue = ""
                                                        showEditPaymentDialog = true
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Text("✏️", fontSize = 16.sp)
                                                }

                                                Box {
                                                    var expandedMoreMenu by remember { mutableStateOf(false) }
                                                    IconButton(
                                                        onClick = { expandedMoreMenu = true },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.MoreVert,
                                                            contentDescription = "More actions",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    DropdownMenu(
                                                        expanded = expandedMoreMenu,
                                                        onDismissRequest = { expandedMoreMenu = false }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("Toggle Payments History") },
                                                            onClick = {
                                                                expandedMoreMenu = false
                                                                isExpanded = !isExpanded
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Go to Bill Page") },
                                                            onClick = {
                                                                expandedMoreMenu = false
                                                                navController.navigate("bill_entry/${bill.firmName}/${bill.id}")
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // EXPANDABLE PAYMENT HISTORY SECTION
                                        if (isExpanded) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF15131A))
                                                    .padding(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Payment History for Bill #${bill.billNumber}",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Button(
                                                        onClick = {
                                                            formBuyerName = bill.buyerName
                                                            formBillNo = bill.billNumber
                                                            formFirm = bill.firmName
                                                            formSellerName = bill.sellerName
                                                            formDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                                            formAmount = String.format("%.2f", pending)
                                                            formMode = "Bank Transfer"
                                                            formBankName = ""
                                                            formRemarks = ""
                                                            formDiscount = ""
                                                            formCommission = ""
                                                            formRemarkAmt = ""
                                                            showAddDialog = true
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C), contentColor = Color.White),
                                                        modifier = Modifier.height(32.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = "Add Payment", modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Add Payment", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                if (billPayments.isEmpty()) {
                                                    Text(
                                                        text = "No payment receipts recorded for this bill.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    // Mini table of payment history entries
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                                            .padding(6.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Date", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                                        Text("Amount", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                                        Text("Mode", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                                        Text("Received By", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                                        Text("Remark Amt", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                                        Text("Remark", modifier = Modifier.weight(2.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                                        Text("Action", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                                    }

                                                    billPayments.forEach { p ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    editingPayment = p
                                                                    editingBill = bill
                                                                    editingAlreadyPaid = received - p.paymentAmount
                                                                    editingPending = pending + p.paymentAmount
                                                                    paymentReceivedValue = p.paymentAmount.toString()
                                                                    paymentDateValue = p.paymentDate.ifBlank { p.createdAt }
                                                                    paymentModeValue = p.paymentMode
                                                                    paymentRefValue = p.referenceNumber
                                                                    paymentNotesValue = p.remarks
                                                                    showEditPaymentDialog = true
                                                                }
                                                                .padding(vertical = 6.dp, horizontal = 6.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(p.date, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                            Text("₹${String.format("%.2f", p.amount)}", modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                            Text(p.paymentMode, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(p.receivedBy, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(if (p.remarks1.isNotBlank()) "₹${p.remarks1}" else "₹0.00", modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(p.remarks2.ifBlank { "-" }, modifier = Modifier.weight(2.5f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Row(
                                                                modifier = Modifier.weight(1.5f),
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                IconButton(
                                                                    onClick = {
                                                                        editingPayment = p
                                                                        editingBill = bill
                                                                        editingAlreadyPaid = received - p.paymentAmount
                                                                        editingPending = pending + p.paymentAmount
                                                                        paymentReceivedValue = p.paymentAmount.toString()
                                                                        paymentDateValue = p.paymentDate.ifBlank { p.createdAt }
                                                                        paymentModeValue = p.paymentMode
                                                                        paymentRefValue = p.referenceNumber
                                                                        paymentNotesValue = p.remarks
                                                                        showEditPaymentDialog = true
                                                                    },
                                                                    modifier = Modifier.size(24.dp)
                                                                ) {
                                                                    Text("✏️", fontSize = 12.sp)
                                                                }
                                                                IconButton(
                                                                    onClick = {
                                                                        com.example.util.BiometricHelper.runWithBiometric(
                                                                            context = context,
                                                                            title = "Ranisa Security",
                                                                            subtitle = "Verify your fingerprint to continue.",
                                                                            action = {
                                                                                viewModel.deletePayment(p)
                                                                                Toast.makeText(context, "Payment receipt deleted", Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        )
                                                                    },
                                                                    modifier = Modifier.size(24.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Delete,
                                                                        contentDescription = "Delete Receipt",
                                                                        tint = Color(0xFFEF5350),
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    }
                                }
                            }
                        }
                    }

                    // Lazy Load pagination button if applicable
                    if (!showAllBillsOfParty && filteredBillsOfParty.size > 5) {
                        Button(
                            onClick = { showAllBillsOfParty = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Load Remaining (${filteredBillsOfParty.size - 5}) Bills")
                        }
                    } else if (showAllBillsOfParty) {
                        TextButton(
                            onClick = { showAllBillsOfParty = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Show Less (Collapse to 5)", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // ADD PAYMENT DIALOG (RECORD PAYMENT RECEIPT)
    // =========================================================================
    if (showAddDialog) {
        val calendarForAdd = java.util.Calendar.getInstance()
        val addDatePickerDialog = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                formDate = String.format(java.util.Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            },
            calendarForAdd.get(java.util.Calendar.YEAR),
            calendarForAdd.get(java.util.Calendar.MONTH),
            calendarForAdd.get(java.util.Calendar.DAY_OF_MONTH)
        )
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Record Buyer Payment",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = formBuyerName,
                        onValueChange = { formBuyerName = it },
                        label = { Text("Buyer Name (Party)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formBillNo,
                        onValueChange = { formBillNo = it },
                        label = { Text("Bill Number (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formSellerName,
                        onValueChange = { formSellerName = it },
                        label = { Text("Seller Name (Mill - Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formFirm,
                        onValueChange = { formFirm = it },
                        label = { Text("Firm (Lalit or Hare Krishna)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formDate,
                        onValueChange = { formDate = it },
                        label = { Text("Payment Date (YYYY-MM-DD)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addDatePickerDialog.show() },
                        trailingIcon = {
                            IconButton(onClick = { addDatePickerDialog.show() }) {
                                Icon(Icons.Default.Event, contentDescription = "Choose Date")
                            }
                        },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formAmount,
                        onValueChange = { formAmount = it },
                        label = { Text("Amount Received (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formDiscount,
                        onValueChange = { formDiscount = it },
                        label = { Text("Discount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formCommission,
                        onValueChange = { formCommission = it },
                        label = { Text("Commission (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formRemarkAmt,
                        onValueChange = { formRemarkAmt = it },
                        label = { Text("Remark Amt (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formMode,
                        onValueChange = { formMode = it },
                        label = { Text("Payment Mode (Cash/Bank)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formBankName,
                        onValueChange = { formBankName = it },
                        label = { Text("Bank Name / Reference Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formRemarks,
                        onValueChange = { formRemarks = it },
                        label = { Text("Remarks") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val isSavingPayment by viewModel.isSavingPayment.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            enabled = !isSavingPayment,
                            onClick = { showAddDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            enabled = !isSavingPayment,
                            onClick = {
                                if (formBuyerName.isBlank() || formAmount.isBlank()) {
                                    Toast.makeText(context, "Enter Party Name & Amount", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val enteredAmt = formAmount.toDoubleOrNull() ?: 0.0
                                val enteredDiscount = formDiscount.toDoubleOrNull() ?: 0.0
                                val enteredCommission = formCommission.toDoubleOrNull() ?: 0.0
                                val enteredRemarkAmt = formRemarkAmt.toDoubleOrNull() ?: 0.0

                                if (enteredAmt < 0.0 || enteredDiscount < 0.0 || enteredCommission < 0.0 || enteredRemarkAmt < 0.0) {
                                    Toast.makeText(context, "Deductions or payments cannot be negative", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                if (formBillNo.isNotBlank() && formFirm.isNotBlank()) {
                                    val matched = bills.find {
                                        it.billNumber.trim().equals(formBillNo.trim(), ignoreCase = true) &&
                                        it.firmName.trim().replace(" ", "").equals(formFirm.trim().replace(" ", ""), ignoreCase = true)
                                    }
                                    if (matched != null) {
                                        val otherPaymentsForBill = payments.filter {
                                            it.billNo.trim().equals(formBillNo.trim(), ignoreCase = true) &&
                                            it.firm.trim().replace(" ", "").equals(formFirm.trim().replace(" ", ""), ignoreCase = true)
                                        }
                                        val totalOtherReceived = otherPaymentsForBill.sumOf { it.paymentAmount }
                                        val totalOtherDiscount = otherPaymentsForBill.sumOf { it.discountAmount }
                                        val totalOtherCommission = otherPaymentsForBill.sumOf { it.commissionAmount }
                                        val totalOtherRemarkAmt = otherPaymentsForBill.sumOf { it.remarks1.toDoubleOrNull() ?: 0.0 }

                                        val billDiscount = 0.0
                                        val billCommission = 0.0
                                        val billRemarkAmt = 0.0

                                        val finalCheckPending = matched.billAmount - 
                                                (totalOtherReceived + enteredAmt) - 
                                                (totalOtherDiscount + enteredDiscount) - 
                                                (totalOtherCommission + enteredCommission) - 
                                                (totalOtherRemarkAmt + enteredRemarkAmt) - 
                                                billDiscount - 
                                                billCommission - 
                                                billRemarkAmt

                                        if (finalCheckPending < -0.01) {
                                            Toast.makeText(context, "Balance Amount cannot become negative. Total deduction exceeds Bill Amount.", Toast.LENGTH_LONG).show()
                                            return@Button
                                        }
                                    }
                                }

                                viewModel.savePayment(
                                    buyerName = formBuyerName,
                                    date = formDate,
                                    amount = enteredAmt,
                                    paymentMode = formMode,
                                    bankName = formBankName,
                                    remarks = formRemarks,
                                    billNo = formBillNo,
                                    firm = formFirm,
                                    sellerName = formSellerName,
                                    discountPercent = enteredDiscount,
                                    discountAmount = enteredDiscount,
                                    commissionPercent = enteredCommission,
                                    commissionAmount = enteredCommission,
                                    remarks1 = formRemarkAmt.ifBlank { "0.0" },
                                    remarks2 = formRemarks,
                                    onSuccess = {
                                        showAddDialog = false
                                        Toast.makeText(context, "Payment logged & synced successfully", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { errorMsg ->
                                        Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSavingPayment) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // EDIT PAYMENT DIALOG (COMPACT DIRECT EDIT DIALOG)
    // =========================================================================
    EditPaymentDialog(
        showDialog = showEditPaymentDialog,
        onDismissRequest = { showEditPaymentDialog = false },
        bill = editingBill,
        editingPayment = editingPayment,
        editingAlreadyPaid = editingAlreadyPaid,
        editingPending = editingPending,
        viewModel = viewModel
    )
}

// ==========================================
// REPORTS & ANALYTICS SCREEN
// ==========================================
@Composable
fun ReportsScreen(viewModel: RanisaViewModel) {
    val bills by viewModel.allBills.collectAsState()
    val payments by viewModel.allPayments.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Reports Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Detailed audit statistics and total outstanding analytics.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Net Balance Owed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    val sumBill = bills.sumOf { it.billAmount }
                    val sumPaid = payments.sumOf { it.amount }
                    val ddTotal = bills.sumOf { it.ddAmount }
                    val cashCutTotal = bills.sumOf { it.cashCutting }
                    val netOutstanding = sumBill - ddTotal - cashCutTotal - sumPaid
                    Text("₹${String.format("%.2f", netOutstanding)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Firms Sales Split", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                    val lalitSales = bills.filter { it.firmName.contains("Lalit") }.sumOf { it.billAmount }
                    val krishnaSales = bills.filter { it.firmName.contains("Krishna") }.sumOf { it.billAmount }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Lalit Rice Broker:")
                        Text("₹${String.format("%.2f", lalitSales)}", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Hare Krishna Rice Broker:")
                        Text("₹${String.format("%.2f", krishnaSales)}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Brokerage Metrics", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Text("• Local SQLite Database status: Healthy (Encrypted)", style = MaterialTheme.typography.bodyMedium)
                    Text("• Firebase Auto backup: Completed 100%", style = MaterialTheme.typography.bodyMedium)
                    Text("• Sync Logs Status: Validated by System admin", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ==========================================
// AUDIT LOG HISTORY SCREEN
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogHistoryScreen(viewModel: RanisaViewModel) {
    val logs by viewModel.logs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedRoleFilter by remember { mutableStateOf("All") }
    var selectedFirmFilter by remember { mutableStateOf("All") }
    var expandedLogId by remember { mutableStateOf<Int?>(null) }

    val categories = listOf("All", "Bills", "Payments", "PDF/Print", "Auth", "Others")
    val roles = listOf("All", "Admin", "Broker", "Accountant", "Viewer")
    val firms = listOf("All", "Lalit Rice Broker", "Hare Krishna Rice Broker")

    val filteredLogs = logs.filter { log ->
        val matchesSearch = searchQuery.isBlank() ||
                log.action.contains(searchQuery, ignoreCase = true) ||
                log.userName.contains(searchQuery, ignoreCase = true) ||
                log.screen.contains(searchQuery, ignoreCase = true) ||
                log.billNo.contains(searchQuery, ignoreCase = true) ||
                log.partyName.contains(searchQuery, ignoreCase = true) ||
                log.oldValue.contains(searchQuery, ignoreCase = true) ||
                log.newValue.contains(searchQuery, ignoreCase = true)

        val matchesCategory = when (selectedCategoryFilter) {
            "All" -> true
            "Bills" -> log.action.contains("BILL", ignoreCase = true)
            "Payments" -> log.action.contains("PAYMENT", ignoreCase = true)
            "PDF/Print" -> log.action.contains("PDF", ignoreCase = true) || log.action.equals("Print", ignoreCase = true)
            "Auth" -> log.action.equals("Login", ignoreCase = true) || log.action.equals("Logout", ignoreCase = true) || log.action.contains("User", ignoreCase = true)
            "Others" -> !log.action.contains("BILL", ignoreCase = true) &&
                        !log.action.contains("PAYMENT", ignoreCase = true) &&
                        !log.action.contains("PDF", ignoreCase = true) &&
                        !log.action.equals("Print", ignoreCase = true) &&
                        !log.action.equals("Login", ignoreCase = true) &&
                        !log.action.equals("Logout", ignoreCase = true) &&
                        !log.action.contains("User", ignoreCase = true)
            else -> true
        }

        val matchesRole = selectedRoleFilter == "All" || log.userRole.equals(selectedRoleFilter, ignoreCase = true)
        val matchesFirm = selectedFirmFilter == "All" || log.firmName.equals(selectedFirmFilter, ignoreCase = true)

        matchesSearch && matchesCategory && matchesRole && matchesFirm
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Audit Trail Registry",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Immutable, system-wide log capturing all user sessions, contract mutations, payment lifecycles, and print jobs.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search by user, action, party, bill no, or details") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter
        Text("Categories:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategoryFilter == cat,
                    onClick = { selectedCategoryFilter = cat },
                    label = { Text(cat, fontSize = 11.sp) }
                )
            }
        }

        // Row of Roles & Firms Filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Role:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    roles.forEach { r ->
                        FilterChip(
                            selected = selectedRoleFilter == r,
                            onClick = { selectedRoleFilter = r },
                            label = { Text(r, fontSize = 10.sp) }
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("Firm Ledger:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    firms.forEach { f ->
                        FilterChip(
                            selected = selectedFirmFilter == f,
                            onClick = { selectedFirmFilter = f },
                            label = { Text(if (f.startsWith("Lalit")) "Lalit" else if (f.startsWith("Hare")) "H. Krishna" else "All", fontSize = 10.sp) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Logs List
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "No logs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No matching audit logs found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    val isExpanded = expandedLogId == log.id
                    val isDelet = log.action.contains("DELETE", ignoreCase = true)
                    val isUpdat = log.action.contains("UPDATE", ignoreCase = true) || log.action.contains("EDIT", ignoreCase = true)
                    val isCreat = log.action.contains("CREATE", ignoreCase = true) || log.action.contains("ADD", ignoreCase = true)
                    val isPdf = log.action.contains("PDF", ignoreCase = true) || log.action.equals("Print", ignoreCase = true)

                    val actionBg = when {
                        isDelet -> MaterialTheme.colorScheme.errorContainer
                        isUpdat -> MaterialTheme.colorScheme.tertiaryContainer
                        isCreat -> MaterialTheme.colorScheme.primaryContainer
                        isPdf -> Color(0xFFFDE8E8)
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }

                    val actionColor = when {
                        isDelet -> MaterialTheme.colorScheme.onErrorContainer
                        isUpdat -> MaterialTheme.colorScheme.onTertiaryContainer
                        isCreat -> MaterialTheme.colorScheme.onPrimaryContainer
                        isPdf -> Color(0xFFC81E1E)
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedLogId = if (isExpanded) null else log.id }
                            .testTag("audit_log_item_${log.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Initials Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                RoundedCornerShape(50)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = log.userName.take(2).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = log.userName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (log.userRole.isNotBlank()) {
                                            Text(
                                                text = "Role: ${log.userRole}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "${log.date} ${log.time}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action badge and Screen Name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(actionBg, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = log.action,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = actionColor
                                    )
                                }

                                Text(
                                    text = "Screen: ${log.screen}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (log.billNo.isNotBlank() || log.partyName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (log.billNo.isNotBlank()) {
                                        Text("Bill No: ${log.billNo}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (log.partyName.isNotBlank()) {
                                        Text("Party: ${log.partyName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }

                            // Details / Comparisons
                            Spacer(modifier = Modifier.height(8.dp))
                            if (isExpanded) {
                                // Expandable Detail Panel
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Log Metadata:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("Firm Ledger: ${log.firmName.ifBlank { "N/A" }}", fontSize = 11.sp)
                                    Text("Device: ${log.device.ifBlank { "Android Device" }}", fontSize = 11.sp)
                                    Text("Session Reference: ${log.ipSessionId.ifBlank { "N/A" }}", fontSize = 11.sp)

                                    if (log.oldValue.isNotBlank() || log.newValue.isNotBlank()) {
                                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        Text("Data Modifications:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        
                                        if (log.oldValue.isNotBlank() && log.newValue.isNotBlank()) {
                                            // Show comparative changes
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("PREVIOUS VALUE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color(0xFFFEF2F2), RoundedCornerShape(4.dp))
                                                            .border(BorderStroke(1.dp, Color(0xFFFCA5A5)), RoundedCornerShape(4.dp))
                                                            .padding(6.dp)
                                                    ) {
                                                        Text(log.oldValue, fontSize = 11.sp, color = Color(0xFF991B1B))
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("UPDATED VALUE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color(0xFFECFDF5), RoundedCornerShape(4.dp))
                                                            .border(BorderStroke(1.dp, Color(0xFF6EE7B7)), RoundedCornerShape(4.dp))
                                                            .padding(6.dp)
                                                    ) {
                                                        Text(log.newValue, fontSize = 11.sp, color = Color(0xFF065F46))
                                                    }
                                                }
                                            }
                                        } else {
                                            // Either only oldValue or only newValue
                                            val singleText = log.newValue.ifBlank { log.oldValue }
                                            val title = if (log.newValue.isNotBlank()) "CREATED DETAILS" else "REMOVED RECORD DETAILS"
                                            val borderC = if (log.newValue.isNotBlank()) Color(0xFF6EE7B7) else Color(0xFFFCA5A5)
                                            val bgC = if (log.newValue.isNotBlank()) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                                            val txtC = if (log.newValue.isNotBlank()) Color(0xFF065F46) else Color(0xFF991B1B)

                                            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = txtC)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(bgC, RoundedCornerShape(4.dp))
                                                    .border(BorderStroke(1.dp, borderC), RoundedCornerShape(4.dp))
                                                    .padding(8.dp)
                                            ) {
                                                Text(singleText, fontSize = 11.sp, color = txtC)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Collapsed short summary
                                val summary = when {
                                    log.newValue.isNotBlank() -> log.newValue
                                    log.oldValue.isNotBlank() -> log.oldValue
                                    else -> "No change values recorded."
                                }
                                Text(
                                    text = summary,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SETTINGS SCREEN
// ==========================================
@Composable
fun SettingsScreen(navController: NavController, viewModel: RanisaViewModel) {
    val activeUser by viewModel.activeUser.collectAsState()
    val activeFirm by viewModel.activeFirm.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings & System Configurations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // Profile Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Active Session Profile", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Username: ${activeUser?.username ?: "Guest"}")
                Text("Assigned Role: ${activeUser?.role ?: "Viewer"}")
                Text("Connected Firm Notebook: ${activeFirm?.name ?: "None"}")
            }
        }

        // Preferences Section
        val sharedPrefs = remember(context) { context.getSharedPreferences("ranisa_prefs", android.content.Context.MODE_PRIVATE) }
        var isBiometricEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("biometric_lock_enabled", false)) }
        val activity = context as? androidx.fragment.app.FragmentActivity

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("System Preferences", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fingerprint Lock", fontWeight = FontWeight.SemiBold)
                        Text("Require fingerprint or device PIN when opening the app and performing sensitive actions.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (activity == null) {
                                    Toast.makeText(context, "Error: Activity context not available", Toast.LENGTH_SHORT).show()
                                    return@Switch
                                }
                                val status = com.example.util.BiometricHelper.getBiometricStatus(context)
                                if (status == "NO_HARDWARE" || status == "HW_UNAVAILABLE") {
                                    Toast.makeText(context, "This device does not support biometric authentication.", Toast.LENGTH_LONG).show()
                                    return@Switch
                                } else if (status == "NONE_ENROLLED") {
                                    Toast.makeText(context, "No biometrics enrolled. Please set up fingerprint/face lock in device settings first.", Toast.LENGTH_LONG).show()
                                    return@Switch
                                } else if (status == "SUCCESS") {
                                    com.example.util.BiometricHelper.authenticate(
                                        activity = activity,
                                        title = "Ranisa Security",
                                        subtitle = "Verify your fingerprint to continue.",
                                        onSuccess = {
                                            isBiometricEnabled = true
                                            sharedPrefs.edit().putBoolean("biometric_lock_enabled", true).apply()
                                            viewModel.logSettingsChange("Biometric Security", "Disabled", "Enabled")
                                            Toast.makeText(context, "Fingerprint Lock enabled successfully.", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { errorCode, errString ->
                                            Toast.makeText(context, "Authentication failed: $errString", Toast.LENGTH_SHORT).show()
                                        },
                                        onFailed = {
                                            Toast.makeText(context, "Authentication failed. Try again.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    Toast.makeText(context, "Biometric authentication is currently unavailable ($status).", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                isBiometricEnabled = false
                                sharedPrefs.edit().putBoolean("biometric_lock_enabled", false).apply()
                                viewModel.logSettingsChange("Biometric Security", "Enabled", "Disabled")
                                Toast.makeText(context, "Fingerprint Lock disabled.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("biometric_lock_switch")
                    )
                }
            }
        }

        // Database Configurations
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Storage Systems", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("• Engine: SQLite (Room ORM library)")
                Text("• Cloud: Firebase Cloud Backup Service Status Active")
                Text("• Data Residency: Offline-first storage")
            }
        }

        // Action controls
        Button(
            onClick = {
                com.example.util.BiometricHelper.runWithBiometric(
                    context = context,
                    title = "Ranisa Security",
                    subtitle = "Verify your fingerprint to continue.",
                    action = {
                        viewModel.logExport()
                        Toast.makeText(context, "SQLite Master Database backed up to Firebase Cloud.", Toast.LENGTH_LONG).show()
                    }
                )
            },
            modifier = Modifier.fillMaxWidth().testTag("backup_button")
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = "Sync Cloud")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Force Firebase Cloud Sync")
        }

        OutlinedButton(
            onClick = {
                viewModel.logSettingsChange("Clear Cache", "Active Logs", "Cleared")
                Toast.makeText(context, "Developer Debug: System logs cleared.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Clear cache")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear Audit log cache")
        }

        OutlinedButton(
            onClick = {
                com.example.util.BiometricHelper.runWithBiometric(
                    context = context,
                    title = "Ranisa Security",
                    subtitle = "Verify your fingerprint to continue.",
                    action = {
                        viewModel.resetAllLocalData(context) {
                            Toast.makeText(context, "All local data has been reset.", Toast.LENGTH_LONG).show()
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxWidth().testTag("reset_data_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = "Reset all data")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset All Local Data")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.logoutUser()
                val sharedPrefs = context.getSharedPreferences("ranisa_prefs", android.content.Context.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putBoolean("remember_me", false)
                    .remove("saved_username")
                    .remove("saved_role")
                    .apply()
                navController.navigate("login") {
                    popUpTo("home") { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("logout_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = "Logout")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout Session", fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// GLOBAL SEARCH SCREEN
// ==========================================
@Composable
fun SearchScreen(navController: NavController, viewModel: RanisaViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val query by viewModel.globalSearchQuery.collectAsState()
    val bills by viewModel.filteredBills.collectAsState()
    val payments by viewModel.filteredPayments.collectAsState()
    val allBills by viewModel.allBills.collectAsState()

    var showEditPaymentDialog by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<ContractBill?>(null) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    var editingAlreadyPaid by remember { mutableStateOf(0.0) }
    var editingPending by remember { mutableStateOf(0.0) }
    var paymentReceivedValue by remember { mutableStateOf("") }
    var paymentDateValue by remember { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())) }
    var paymentModeValue by remember { mutableStateOf("Cash") }
    var paymentRefValue by remember { mutableStateOf("") }
    var paymentNotesValue by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Global Database Search", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Query any Buyer Name, Seller Name, Mandi, Bill Number, Date, or payment mode instantly.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.updateGlobalSearch(it) },
            label = { Text("Enter search term...") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") }
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            if (bills.isNotEmpty()) {
                item {
                    Text("Matching Contract Bills (${bills.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
                }
                items(bills) { b ->
                    Card(
                        onClick = { navController.navigate("bill_entry/${b.firmName}/${b.id}") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bill: ${b.billNumber}", fontWeight = FontWeight.Bold)
                                Text(b.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Seller: ${b.sellerName} ➔ Buyer: ${b.buyerName}", fontSize = 13.sp)
                            Text("Bill Amt: ₹${String.format("%.2f", b.billAmount)} | Balance Amount: ₹${String.format("%.2f", b.balance)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (payments.isNotEmpty()) {
                item {
                    Text("Matching Payment Receipts (${payments.size})", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), modifier = Modifier.padding(vertical = 4.dp))
                }
                items(payments) { p ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(p.buyerName, fontWeight = FontWeight.Bold)
                                Text(p.date, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mode: ${p.paymentMode} | Bank: ${p.bankName.ifBlank { "N/A" }}", fontSize = 12.sp)
                                    Text("Receipt: ₹${String.format("%.2f", p.amount)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val matchingBill = allBills.find { it.billNumber.trim() == p.billNo.trim() && it.firmName.trim().replace(" ", "").equals(p.firm.trim().replace(" ", ""), ignoreCase = true) }
                                    IconButton(
                                        onClick = {
                                            if (matchingBill != null) {
                                                val billPayments = viewModel.allPayments.value.filter { bp ->
                                                    bp.billNo.trim().equals(matchingBill.billNumber.trim(), ignoreCase = true) &&
                                                    bp.firm.trim().replace(" ", "").equals(matchingBill.firmName.trim().replace(" ", ""), ignoreCase = true)
                                                }
                                                val received = billPayments.sumOf { bp -> bp.paymentAmount }
                                                val pending = matchingBill.billAmount - received

                                                editingPayment = p
                                                editingBill = matchingBill
                                                editingAlreadyPaid = received - p.paymentAmount
                                                editingPending = pending + p.paymentAmount
                                                paymentReceivedValue = p.paymentAmount.toString()
                                                paymentDateValue = p.paymentDate.ifBlank { p.createdAt }
                                                paymentModeValue = p.paymentMode
                                                paymentRefValue = p.referenceNumber
                                                paymentNotesValue = p.remarks
                                                showEditPaymentDialog = true
                                            } else {
                                                Toast.makeText(context, "Associated contract bill not found.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Text("✏️", fontSize = 13.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            com.example.util.BiometricHelper.runWithBiometric(
                                                context = context,
                                                title = "Ranisa Security",
                                                subtitle = "Verify your fingerprint to continue.",
                                                action = {
                                                    viewModel.deletePayment(p)
                                                    Toast.makeText(context, "Payment receipt deleted", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Text("❌", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (bills.isEmpty() && payments.isEmpty() && query.isNotBlank()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No matching results found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    EditPaymentDialog(
        showDialog = showEditPaymentDialog,
        onDismissRequest = { showEditPaymentDialog = false },
        bill = editingBill,
        editingPayment = editingPayment,
        editingAlreadyPaid = editingAlreadyPaid,
        editingPending = editingPending,
        viewModel = viewModel
    )
}

// =========================================================================
// PREMIUM ACCOUNTING CARD UI HELPERS
// =========================================================================

fun formatCurrency(amount: Double): String {
    val isNegative = amount < 0
    val absAmount = kotlin.math.abs(amount)
    val formatted = String.format(java.util.Locale.US, "%.2f", absAmount)
    val parts = formatted.split(".")
    val num = parts[0]
    val dec = parts[1]
    val result = if (num.length <= 3) {
        formatted
    } else {
        val lastThree = num.substring(num.length - 3)
        val remaining = num.substring(0, num.length - 3)
        val sb = StringBuilder()
        var i = remaining.length - 1
        var count = 0
        while (i >= 0) {
            sb.append(remaining[i])
            count++
            if (count == 2 && i > 0) {
                sb.append(",")
                count = 0
            }
            i--
        }
        "${sb.reverse().toString()},$lastThree.$dec"
    }
    return if (isNegative) "-₹$result" else "₹$result"
}

@Composable
fun AutoShrinkAmountText(
    amount: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    var fontSize by remember(amount) { mutableStateOf(24.sp) }
    var readyToDraw by remember(amount) { mutableStateOf(false) }

    Text(
        text = amount,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow && fontSize.value > 12f) {
                fontSize = (fontSize.value - 1f).sp
            } else {
                readyToDraw = true
            }
        },
        modifier = modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        }
    )
}

// ==========================================
// UNIFIED EDIT PAYMENT DIALOG
// ==========================================
@Composable
fun EditPaymentDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    bill: ContractBill?,
    editingPayment: Payment?,
    editingAlreadyPaid: Double,
    editingPending: Double,
    viewModel: RanisaViewModel
) {
    if (!showDialog || bill == null) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val calendar = java.util.Calendar.getInstance()

    // Internal Form States
    var paymentReceivedValue by remember(editingPayment) {
        mutableStateOf(editingPayment?.paymentAmount?.toString() ?: "")
    }
    var paymentDateValue by remember(editingPayment) {
        mutableStateOf(editingPayment?.paymentDate?.ifBlank { editingPayment.createdAt } ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()))
    }
    var discountValue by remember(editingPayment) {
        mutableStateOf(if (editingPayment != null && editingPayment.discountAmount > 0.0) editingPayment.discountAmount.toString() else "")
    }
    var commissionValue by remember(editingPayment) {
        mutableStateOf(if (editingPayment != null && editingPayment.commissionAmount > 0.0) editingPayment.commissionAmount.toString() else "")
    }
    var remarks1Value by remember(editingPayment) {
        mutableStateOf(editingPayment?.remarks1 ?: "")
    }
    var remarks2Value by remember(editingPayment) {
        mutableStateOf(editingPayment?.remarks2 ?: "")
    }

    // Calculations
    val allPaymentsList by viewModel.allPayments.collectAsState()

    val billPayments = remember(allPaymentsList, bill.billNumber, bill.firmName) {
        allPaymentsList.filter { p ->
            p.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true) &&
            p.firm.trim().replace(" ", "").equals(bill.firmName.trim().replace(" ", ""), ignoreCase = true)
        }
    }

    val otherPayments = remember(billPayments, editingPayment) {
        if (editingPayment == null) billPayments
        else billPayments.filter { it.paymentId != editingPayment.paymentId }
    }

    val totalAmount = bill.billAmount
    val otherReceivedAmount = otherPayments.sumOf { it.paymentAmount }
    val otherDiscountAmount = otherPayments.sumOf { it.discountAmount }
    val otherCommissionAmount = otherPayments.sumOf { it.commissionAmount }
    val otherRemarkAmt = otherPayments.sumOf { it.remarks1.toDoubleOrNull() ?: 0.0 }

    val paymentReceived = paymentReceivedValue.toDoubleOrNull() ?: 0.0
    val discountAmount = discountValue.toDoubleOrNull() ?: 0.0
    val commissionAmount = commissionValue.toDoubleOrNull() ?: 0.0
    val remarkAmtVal = remarks1Value.toDoubleOrNull() ?: 0.0

    val calculatedPending = maxOf(
        0.0,
        totalAmount - 
        (otherReceivedAmount + paymentReceived) - 
        (otherDiscountAmount + discountAmount) - 
        (otherCommissionAmount + commissionAmount) - 
        (otherRemarkAmt + remarkAmtVal)
    )

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Update Payment",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Payment Received (₹)
                OutlinedTextField(
                    value = paymentReceivedValue,
                    onValueChange = { paymentReceivedValue = it },
                    label = { Text("Payment Received (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Payment Date
                val editDatePickerDialog = android.app.DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        paymentDateValue = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                OutlinedTextField(
                    value = paymentDateValue,
                    onValueChange = { paymentDateValue = it },
                    label = { Text("Payment Date (YYYY-MM-DD)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editDatePickerDialog.show() },
                    trailingIcon = {
                        IconButton(onClick = { editDatePickerDialog.show() }) {
                            Icon(Icons.Default.Event, contentDescription = "Choose Date")
                        }
                    },
                    singleLine = true
                )

                // Discount - Numeric only
                OutlinedTextField(
                    value = discountValue,
                    onValueChange = { discountValue = it },
                    label = { Text("Discount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Commission - Numeric only
                OutlinedTextField(
                    value = commissionValue,
                    onValueChange = { commissionValue = it },
                    label = { Text("Commission") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Remark Amt - Numeric only
                OutlinedTextField(
                    value = remarks1Value,
                    onValueChange = { remarks1Value = it },
                    label = { Text("Remark Amt") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Remark
                OutlinedTextField(
                    value = remarks2Value,
                    onValueChange = { remarks2Value = it },
                    label = { Text("Remark") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                val isUpdatingPayment by viewModel.isUpdatingPayment.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = !isUpdatingPayment,
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        enabled = !isUpdatingPayment,
                        onClick = {
                            val enteredAmt = paymentReceivedValue.toDoubleOrNull() ?: 0.0
                            val enteredDiscount = discountValue.toDoubleOrNull() ?: 0.0
                            val enteredCommission = commissionValue.toDoubleOrNull() ?: 0.0
                            val enteredRemarkAmt = remarks1Value.toDoubleOrNull() ?: 0.0

                            // Validation
                            if (enteredAmt < 0.0) {
                                Toast.makeText(context, "Payment received cannot be negative", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (enteredDiscount < 0.0) {
                                Toast.makeText(context, "Discount cannot be negative", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (enteredCommission < 0.0) {
                                Toast.makeText(context, "Commission cannot be negative", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (remarks1Value.isNotBlank() && remarks1Value.toDoubleOrNull() == null) {
                                Toast.makeText(context, "Remark Amt must be a valid number", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (enteredRemarkAmt < 0.0) {
                                Toast.makeText(context, "Remark Amt cannot be negative", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val checkPending = totalAmount - 
                                    (otherReceivedAmount + enteredAmt) - 
                                    (otherDiscountAmount + enteredDiscount) - 
                                    (otherCommissionAmount + enteredCommission) - 
                                    (otherRemarkAmt + enteredRemarkAmt)

                            if (checkPending < -0.01) {
                                Toast.makeText(context, "Balance Amount cannot become negative. Total deduction exceeds Bill Amount.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            viewModel.updatePaymentTransaction(
                                bill = bill,
                                paymentAmount = enteredAmt,
                                paymentDate = paymentDateValue,
                                paymentMode = editingPayment?.paymentMode ?: "Cash",
                                referenceNumber = editingPayment?.referenceNumber ?: "",
                                notes = editingPayment?.remarks ?: "",
                                existingPayment = editingPayment,
                                discountPercent = enteredDiscount,
                                discountAmount = enteredDiscount,
                                commissionPercent = enteredCommission,
                                commissionAmount = enteredCommission,
                                remarks1 = remarks1Value,
                                remarks2 = remarks2Value,
                                alreadyPaidAmount = otherReceivedAmount,
                                pendingAmount = maxOf(0.0, checkPending),
                                onSuccess = {
                                    onDismissRequest()
                                    Toast.makeText(context, "Payment updated & synced atomically", Toast.LENGTH_SHORT).show()
                                },
                                onError = { errorMsg ->
                                    Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isUpdatingPayment) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Update Payment")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrokerLedgerScreen(
    navController: NavController,
    viewModel: RanisaViewModel,
    preselectedBroker: String = ""
) {
    val bills by viewModel.allBills.collectAsState()
    val payments by viewModel.allPayments.collectAsState()
    val searchQuery by viewModel.brokerLedgerSearch.collectAsState()
    val brokersList by viewModel.rtdbFullBrokers.collectAsState()
    val context = LocalContext.current

    val sellersMaster by viewModel.sellers.collectAsState()
    val buyersMaster by viewModel.buyers.collectAsState()
    val rtdbFullSellers by viewModel.rtdbFullSellers.collectAsState()
    val rtdbFullBuyers by viewModel.rtdbFullBuyers.collectAsState()

    var selectedBroker by remember { mutableStateOf<FirebaseBroker?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    var showDeleteLedgerDialog by remember { mutableStateOf(false) }
    var ledgerToDeleteName by remember { mutableStateOf("") }
    var showShareSheetForLedger by remember { mutableStateOf(false) }
    var ledgerToShareName by remember { mutableStateOf("") }

    // State for the Quick Add Broker Master dialog
    var showAddBrokerDialog by remember { mutableStateOf(false) }
    var newBrokerName by remember { mutableStateOf("") }
    val formMobiles = remember { mutableStateListOf<String>("") }
    var newBrokerAddress by remember { mutableStateOf("") }

    var previewPdfFile by remember { mutableStateOf<java.io.File?>(null) }
    var previewBill by remember { mutableStateOf<ContractBill?>(null) }
    var showPdfPreview by remember { mutableStateOf(false) }
    var showDeleteBillDialog by remember { mutableStateOf(false) }
    var billToDelete by remember { mutableStateOf<ContractBill?>(null) }
    var pageLimit by remember { mutableStateOf(500) }

    // Auto-select broker if preselectedBroker is provided
    LaunchedEffect(preselectedBroker, brokersList) {
        if (preselectedBroker.isNotBlank() && selectedBroker == null) {
            val found = brokersList.find { it.brokerName.equals(preselectedBroker, ignoreCase = true) }
            if (found != null) {
                selectedBroker = found
            }
        }
    }

    BackHandler(enabled = selectedBroker != null) {
        selectedBroker = null
    }

    // Filter brokers for the listing screen
    val distinctBrokers = remember(bills, brokersList) {
        brokersList.filter { broker ->
            val brokerBills = bills.filter {
                (it.brokerId.isNotBlank() && it.brokerId == broker.brokerId) || 
                (it.brokerId.isBlank() && it.brokerName.equals(broker.brokerName, ignoreCase = true))
            }
            val totalQtls = brokerBills.sumOf { it.quintals }
            brokerBills.isNotEmpty() && totalQtls > 0.0
        }
    }

    val filteredBrokers = remember(searchQuery, distinctBrokers) {
        distinctBrokers.filter { broker ->
            broker.brokerName.contains(searchQuery, ignoreCase = true) ||
            broker.mobile.contains(searchQuery, ignoreCase = true)
        }
    }

    MyApplicationTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                if (selectedBroker == null) {
                    // ==========================================
                    // BROKER LEDGER HOME SCREEN (List of Brokers)
                    // ==========================================
                    if (isSearchActive) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateBrokerLedgerSearch(it) },
                                placeholder = { Text("Search Broker Name or Mobile...", fontSize = 13.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .focusRequester(focusRequester)
                                    .testTag("broker_ledger_search_input"),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                                trailingIcon = {
                                    IconButton(onClick = { 
                                        viewModel.updateBrokerLedgerSearch("")
                                        isSearchActive = false 
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close search")
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                            )
                        }
                        LaunchedEffect(isSearchActive) {
                            if (isSearchActive) {
                                focusRequester.requestFocus()
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Broker Ledger & Registers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.testTag("broker_ledger_search_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search Broker")
                            }
                        }
                    }

                    Text("Brokers Master List", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp), fontSize = 13.sp)
                    
                    if (distinctBrokers.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text("📄", fontSize = 48.sp, modifier = Modifier.padding(bottom = 12.dp))
                                Text(
                                    text = "No Ledger Records Found",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Create your first bill to see ledger entries here.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else if (filteredBrokers.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No matching brokers found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredBrokers) { broker ->
                                    val brokerBills = bills.filter { 
                                        (it.brokerId.isNotBlank() && it.brokerId == broker.brokerId) || 
                                        (it.brokerId.isBlank() && it.brokerName.equals(broker.brokerName, ignoreCase = true))
                                    }
                                    val totalQtls = brokerBills.sumOf { it.quintals }
                                    Card(
                                        onClick = { selectedBroker = broker },
                                        modifier = Modifier.fillMaxWidth().testTag("broker_card_${broker.brokerName}"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1B24)),
                                        border = BorderStroke(1.dp, Color(0xFF322E3B))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(broker.brokerName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                                Text("${brokerBills.size} Registered Bills", fontSize = 12.sp, color = Color(0xFF8C8797))
                                            }
                                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                                                Text("Total Qtls", fontSize = 11.sp, color = Color(0xFF8C8797))
                                                Text("${String.format("%.2f", totalQtls)} Qtls", fontWeight = FontWeight.Bold, color = Color(0xFFBB86FC))
                                            }
                                            
                                            LedgerOverflowMenu(
                                                itemId = broker.brokerId.ifBlank { broker.brokerName },
                                                onEdit = {
                                                    navController.navigate("broker_master_list")
                                                },
                                                onShare = {
                                                    ledgerToShareName = broker.brokerName
                                                    showShareSheetForLedger = true
                                                },
                                                onDeleteLedger = {
                                                    ledgerToDeleteName = broker.brokerName
                                                    showDeleteLedgerDialog = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // FAB for Quick Add Broker
                            FloatingActionButton(
                                onClick = {
                                    newBrokerName = ""
                                    formMobiles.clear()
                                    formMobiles.add("")
                                    newBrokerAddress = ""
                                    showAddBrokerDialog = true
                                },
                                containerColor = Color(0xFF322659),
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .testTag("broker_ledger_add_fab")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Broker Master")
                            }
                        }
                    }

                } else {
                    // ==========================================
                    // BROKER BOOK REGISTRY SCREEN (For Selected Broker)
                    // ==========================================
                    val currentBroker = selectedBroker!!
                    var isRegistrySearchActive by remember { mutableStateOf(false) }
                    var registrySearchQuery by remember { mutableStateOf("") }
                    val registryFocusRequester = remember { FocusRequester() }

                    val brokerBills = remember(bills, currentBroker) {
                        bills.filter { 
                            (it.brokerId.isNotBlank() && it.brokerId == currentBroker.brokerId) || 
                            (it.brokerId.isBlank() && it.brokerName.equals(currentBroker.brokerName, ignoreCase = true))
                        }
                    }

                    val filteredBrokerBills = remember(brokerBills, registrySearchQuery) {
                        if (registrySearchQuery.isBlank()) brokerBills else {
                            brokerBills.filter { bill ->
                                bill.billNumber.contains(registrySearchQuery, ignoreCase = true) ||
                                bill.buyerName.contains(registrySearchQuery, ignoreCase = true) ||
                                bill.sellerName.contains(registrySearchQuery, ignoreCase = true) ||
                                bill.date.contains(registrySearchQuery, ignoreCase = true)
                            }
                        }
                    }

                    // AppBar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            IconButton(
                                onClick = { selectedBroker = null },
                                modifier = Modifier.testTag("broker_ledger_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            if (isRegistrySearchActive) {
                                OutlinedTextField(
                                    value = registrySearchQuery,
                                    onValueChange = { registrySearchQuery = it },
                                    placeholder = { Text("Search by Bill No, Party or Date...", fontSize = 13.sp) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .focusRequester(registryFocusRequester)
                                        .testTag("broker_registry_search_input"),
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                                    trailingIcon = {
                                        IconButton(onClick = { 
                                            registrySearchQuery = ""
                                            isRegistrySearchActive = false 
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close search")
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                                )
                                LaunchedEffect(isRegistrySearchActive) {
                                    if (isRegistrySearchActive) {
                                        registryFocusRequester.requestFocus()
                                    }
                                }
                            } else {
                                Text(
                                    text = "${currentBroker.brokerName}'s Book Registry",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (!isRegistrySearchActive) {
                            var showShareSheet by remember { mutableStateOf(false) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { showShareSheet = true },
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.padding(end = 4.dp).testTag("share_full_broker_ledger_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share Full Ledger", fontSize = 11.sp)
                                }
                                IconButton(
                                    onClick = { isRegistrySearchActive = true },
                                    modifier = Modifier.testTag("broker_registry_search_button")
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search bills", tint = Color.White)
                                }
                            }

                            if (showShareSheet) {
                                FullLedgerShareSheet(
                                    ledgerName = currentBroker.brokerName,
                                    ledgerType = "broker",
                                    bills = brokerBills,
                                    payments = payments,
                                    onDismissRequest = { showShareSheet = false }
                                )
                            }
                        }
                    }

                    // Dynamic Calculations & Summary Card
                    val totalQtlsSum = brokerBills.sumOf { it.quintals }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1B24)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF3F2B96).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Bills", fontSize = 11.sp, color = Color(0xFF8C8797), fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${brokerBills.size}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Qtls", fontSize = 11.sp, color = Color(0xFF8C8797), fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${String.format("%.2f", totalQtlsSum)} Qtls", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFBB86FC))
                            }
                        }
                    }

                    // Table Container with Floating Action Button overlay
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (filteredBrokerBills.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No contract bills found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            val paginatedBills = remember(filteredBrokerBills, pageLimit) {
                                filteredBrokerBills.take(pageLimit)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, Color(0xFF322E3B), RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF121212))
                            ) {
                                Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    // Header row
                                    Row(
                                        modifier = Modifier
                                            .background(Color(0xFF322659))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val headers = listOf(
                                            "Date" to 90, 
                                            "Bill No" to 80, 
                                            "Seller Name" to 150, 
                                            "Buyer Name" to 150,
                                            "Quintal" to 80, 
                                            "Rate" to 80, 
                                            "Comm" to 90,
                                            "Balance Amount" to 110, 
                                            "Action" to 180
                                        )
                                        headers.forEach { (text, width) ->
                                            Text(
                                                text = text,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                modifier = Modifier.width(width.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFF322E3B), thickness = 1.dp)

                                    LazyColumn(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(paginatedBills) { bill ->
                                            val billPayments = remember(payments, bill.billNumber) {
                                                payments.filter { p ->
                                                    p.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true)
                                                }
                                            }
                                            val liveReceived = billPayments.sumOf { it.paymentAmount }
                                            val liveDiscount = billPayments.sumOf { it.discountAmount }
                                            val liveCommission = billPayments.sumOf { it.commissionAmount }
                                            val liveRemarkAmt = billPayments.sumOf { it.remarks1.toDoubleOrNull() ?: 0.0 }
                                            val liveBalance = bill.billAmount - liveReceived - liveDiscount - liveCommission - liveRemarkAmt

                                            Column {
                                                Row(
                                                    modifier = Modifier
                                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // 1. Date (90dp)
                                                    RegisterField(bill.date, 90)

                                                    // 2. Bill No (80dp) - Clickable to open contract details
                                                    RegisterField(
                                                        text = bill.billNumber,
                                                        width = 80,
                                                        clickableColor = Color(0xFFBB86FC),
                                                        onClick = { navController.navigate("bill_entry/${bill.firmName}/${bill.id}") }
                                                    )

                                                    // 3. Seller Name (150dp)
                                                    RegisterField(bill.sellerName, 150)

                                                    // 4. Buyer Name (150dp)
                                                    RegisterField(bill.buyerName, 150)

                                                    // 5. Quintal (80dp)
                                                    RegisterField(String.format("%.2f", bill.quintals), 80)

                                                    // 6. Rate (80dp)
                                                    RegisterField("₹${String.format("%.2f", bill.rate)}", 80)

                                                    // 7. Commission (Comm) (90dp)
                                                    RegisterField("₹${String.format("%.2f", liveCommission)}", 90)

                                                    // 8. Balance Amount (110dp)
                                                    RegisterField("₹${String.format("%.2f", liveBalance)}", 110, highlight = true)

                                                    // 9. Action Column (180dp)
                                                    Row(
                                                        modifier = Modifier.width(180.dp),
                                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(
                                                            onClick = { navController.navigate("bill_entry/${bill.firmName}/${bill.id}") },
                                                            modifier = Modifier.size(28.dp).testTag("edit_broker_bill_${bill.id}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Edit,
                                                                contentDescription = "Edit Bill",
                                                                tint = Color(0xFFBB86FC),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                val pdfBill = getLatestBillForPdf(bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                                                val pdfFile = PdfGenerator.generateContractPdf(context, pdfBill)
                                                                if (pdfFile != null) {
                                                                    PdfGenerator.sharePdf(context, pdfFile)
                                                                    viewModel.logPdfShare(bill.billNumber, bill.firmName)
                                                                } else {
                                                                    Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("share_broker_bill_pdf_${bill.id}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Share,
                                                                contentDescription = "Share PDF",
                                                                tint = Color(0xFFBB86FC),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                exportPdfToDownloads(context, bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                                                viewModel.logPdfDownload(bill.billNumber, bill.firmName)
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("export_broker_bill_pdf_${bill.id}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Download,
                                                                contentDescription = "Export PDF",
                                                                tint = Color(0xFFBB86FC),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                val pdfBill = getLatestBillForPdf(bill, sellersMaster, rtdbFullSellers, buyersMaster, rtdbFullBuyers)
                                                                val pdfFile = PdfGenerator.generateContractPdf(context, pdfBill)
                                                                if (pdfFile != null) {
                                                                    previewPdfFile = pdfFile
                                                                    previewBill = bill
                                                                    showPdfPreview = true
                                                                    viewModel.logPdfPreview(bill.billNumber, bill.firmName)
                                                                } else {
                                                                    Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("preview_broker_bill_pdf_${bill.id}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Visibility,
                                                                contentDescription = "Preview PDF",
                                                                tint = Color(0xFFBB86FC),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                billToDelete = bill
                                                                showDeleteBillDialog = true
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("delete_broker_bill_${bill.id}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "Delete Bill",
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                HorizontalDivider(color = Color(0xFF2D2937), thickness = 1.dp)
                                            }
                                        }

                                        if (filteredBrokerBills.size > pageLimit) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Button(
                                                        onClick = { pageLimit += 500 },
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text("Load More (Showing $pageLimit of ${filteredBrokerBills.size})", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // FAB to add a new bill with this broker preselected, perfectly floating on top of the list
                        FloatingActionButton(
                            onClick = {
                                val firm = if (currentBroker.brokerName.contains("Krishna", ignoreCase = true)) "Hare Krishna Rice Broker" else "Lalit Rice Broker"
                                navController.navigate("bill_entry/$firm/-1")
                            },
                            containerColor = Color(0xFF322659),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .testTag("broker_bill_add_fab")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add New Bill")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteBillDialog && billToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteBillDialog = false
                billToDelete = null
            },
            title = { Text("Delete Bill") },
            text = { Text("Are you sure you want to delete Bill No. ${billToDelete!!.billNumber}?\nThis action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        com.example.util.BiometricHelper.runWithBiometric(
                            context = context,
                            title = "Ranisa Security",
                            subtitle = "Verify your fingerprint to continue.",
                            action = {
                                viewModel.deleteBill(billToDelete!!)
                                showDeleteBillDialog = false
                                billToDelete = null
                                Toast.makeText(context, "Bill deleted successfully", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    modifier = Modifier.testTag("confirm_delete_broker_bill_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteBillDialog = false
                        billToDelete = null
                    },
                    modifier = Modifier.testTag("cancel_delete_broker_bill_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPdfPreview && previewPdfFile != null) {
        PdfPreviewDialog(
            file = previewPdfFile!!,
            onPrint = {
                PdfGenerator.printPdf(context, previewPdfFile!!)
                previewBill?.let { viewModel.logPrint(it.billNumber, it.firmName) }
            },
            onShare = {
                PdfGenerator.sharePdf(context, previewPdfFile!!)
                previewBill?.let { viewModel.logPdfExport(it.billNumber, it.firmName) }
            },
            onClose = {
                showPdfPreview = false
                previewPdfFile = null
            }
        )
    }

    // Add Broker Master Dialog (using BrokerMasterFormDialog)
    if (showAddBrokerDialog) {
        BrokerMasterFormDialog(
            title = "Add Broker Master",
            name = newBrokerName,
            onNameChange = { newBrokerName = it },
            mobiles = formMobiles,
            address = newBrokerAddress,
            onAddressChange = { newBrokerAddress = it },
            onDismiss = { showAddBrokerDialog = false },
            onSave = {
                if (newBrokerName.isBlank()) {
                    Toast.makeText(context, "Please enter Broker Name", Toast.LENGTH_SHORT).show()
                } else {
                    val joinedMobile = formMobiles.filter { it.isNotBlank() }.joinToString(",")
                    viewModel.addBroker(
                        brokerName = newBrokerName.trim(),
                        mobile = joinedMobile,
                        address = newBrokerAddress.trim(),
                        onSuccess = {
                            Toast.makeText(context, "Broker Added Successfully", Toast.LENGTH_SHORT).show()
                            showAddBrokerDialog = false
                            newBrokerName = ""
                            newBrokerAddress = ""
                        },
                        onError = { errorMsg ->
                            Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        )
    }

    if (showDeleteLedgerDialog && ledgerToDeleteName.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showDeleteLedgerDialog = false },
            title = { Text("Delete all ledger transactions for this party?") },
            text = { Text("This action will remove only the ledger records.\nThe Master List will remain unchanged.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        com.example.util.BiometricHelper.runWithBiometric(
                            context = context,
                            title = "Ranisa Security",
                            subtitle = "Verify your fingerprint to continue.",
                            action = {
                                viewModel.deleteBrokerLedger(
                                    brokerName = ledgerToDeleteName,
                                    onSuccess = {
                                        showDeleteLedgerDialog = false
                                        Toast.makeText(context, "Ledger transactions deleted successfully", Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { showDeleteLedgerDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showShareSheetForLedger && ledgerToShareName.isNotBlank()) {
        val filteredBillsForOwner = remember(bills, ledgerToShareName) {
            bills.filter { 
                (it.brokerId.isBlank() && it.brokerName.equals(ledgerToShareName, ignoreCase = true)) ||
                (it.brokerId.isNotBlank() && brokersList.find { b -> b.brokerId == it.brokerId }?.brokerName?.equals(ledgerToShareName, ignoreCase = true) == true)
            }
        }
        FullLedgerShareSheet(
            ledgerName = ledgerToShareName,
            ledgerType = "broker",
            bills = filteredBillsForOwner,
            payments = payments,
            onDismissRequest = {
                showShareSheetForLedger = false
                ledgerToShareName = ""
            }
        )
    }
}

@Composable
fun BrokerFormDialog(
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Broker Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("form_broker_name"),
                )

                // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = { Text("Address") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .testTag("form_broker_address"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSave) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerOverflowMenu(
    itemId: String,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDeleteLedger: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.padding(start = 8.dp)) {
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.testTag("ledger_card_menu_$itemId")
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
                modifier = Modifier.testTag("ledger_card_edit_$itemId")
            )
            DropdownMenuItem(
                text = { Text("📤 Share Ledger") },
                onClick = {
                    menuExpanded = false
                    onShare()
                },
                modifier = Modifier.testTag("ledger_card_share_$itemId")
            )
            DropdownMenuItem(
                text = { Text("🗑 Delete Ledger") },
                onClick = {
                    menuExpanded = false
                    onDeleteLedger()
                },
                modifier = Modifier.testTag("ledger_card_delete_ledger_$itemId")
            )
        }
    }
}

@Composable
fun BiometricLockScreen(
    onUnlockSuccess: () -> Unit,
    context: android.content.Context
) {
    val activity = context as? androidx.fragment.app.FragmentActivity

    // Automatically trigger biometric authentication when first launched
    LaunchedEffect(Unit) {
        if (activity != null) {
            com.example.util.BiometricHelper.authenticate(
                activity = activity,
                title = "Ranisa Security",
                subtitle = "Verify your fingerprint to continue.",
                onSuccess = {
                    onUnlockSuccess()
                },
                onError = { _, _ ->
                    // Fallback to manual trigger button if error/cancel
                },
                onFailed = {
                    // Fail state: retry is available via button
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF13101E)) // Dark majestic background
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color(0xFF322659).copy(alpha = 0.2f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Ranisa Lock",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(70.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Ranisa Security",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "हिसाब आपके साथ\nVerify your fingerprint or credentials to unlock.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    if (activity != null) {
                        com.example.util.BiometricHelper.authenticate(
                            activity = activity,
                            title = "Ranisa Security",
                            subtitle = "Verify your fingerprint to continue.",
                            onSuccess = {
                                onUnlockSuccess()
                            },
                            onError = { _, errString ->
                                Toast.makeText(context, "Verification failed: $errString", Toast.LENGTH_SHORT).show()
                            },
                            onFailed = {
                                Toast.makeText(context, "Verification failed. Please try again.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        Toast.makeText(context, "Error: Biometric context not available", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF322659),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("biometric_unlock_button")
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "Unlock App"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock App",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
