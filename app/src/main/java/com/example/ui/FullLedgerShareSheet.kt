package com.example.ui

import com.example.ui.theme.AccentColor
import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ContractBill
import com.example.data.Payment
import com.example.util.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullLedgerShareSheet(
    ledgerName: String,
    ledgerType: String, // "seller", "buyer", "broker"
    bills: List<ContractBill>,
    payments: List<Payment>,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Automatically determine Firm Name from bills
    val firmName = remember(bills) {
        bills.firstOrNull()?.firmName ?: "All Firms"
    }
    
    // Date filter state
    val filterOptions = listOf(
        "All Records", "Today", "Yesterday", "This Week", "This Month", "Last Month", "Custom Date Range"
    )
    var selectedFilterOption by remember { mutableStateOf("All Records") }
    
    // Custom date range bounds
    var customStartDate by remember { mutableStateOf("") } // "yyyy-MM-dd"
    var customEndDate by remember { mutableStateOf("") }   // "yyyy-MM-dd"
    
    // Column selection state with reordering and local persistence
    val defaultColumns = listOf(
        "Date", "Bill No.", "Party Name", "Place", "Brand", "Qtls", "Rate", "Bill Amount",
        "Received Amount", "Balance Amount", "Status", "EB", "Lorry Freight", "Credit Days",
        "Bank / DD Details", "Remarks"
    )
    val defaultSelected = listOf(
        "Date", "Bill No.", "Party Name", "Place", "Qtls", "Rate", "Bill Amount", "Received Amount", "Balance Amount", "Status"
    )

    // Load from SharedPreferences
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("ranisa_pdf_preferences", Context.MODE_PRIVATE)
    }

    // Load column order
    val columnOrder = remember {
        val savedOrderStr = sharedPrefs.getString("column_order", null)
        val list = if (!savedOrderStr.isNullOrBlank()) {
            savedOrderStr.split(",")
        } else {
            defaultColumns
        }
        val mergedList = list.filter { it in defaultColumns }.toMutableList()
        defaultColumns.forEach { col ->
            if (col !in mergedList) {
                mergedList.add(col)
            }
        }
        mutableStateListOf<String>().apply { addAll(mergedList) }
    }

    // Load selected state
    val selectedColumns = remember {
        val savedSelectionStr = sharedPrefs.getString("column_selection", null)
        val selectedSet = if (!savedSelectionStr.isNullOrBlank()) {
            savedSelectionStr.split(",").toSet()
        } else {
            defaultSelected.toSet()
        }
        mutableStateMapOf<String, Boolean>().apply {
            columnOrder.forEach { col ->
                put(col, col in selectedSet)
            }
        }
    }

    val savePreferences = {
        val orderStr = columnOrder.joinToString(",")
        val selectionStr = columnOrder.filter { selectedColumns[it] == true }.joinToString(",")
        sharedPrefs.edit()
            .putString("column_order", orderStr)
            .putString("column_selection", selectionStr)
            .apply()
    }

    val selectedList by remember {
        derivedStateOf {
            columnOrder.filter { selectedColumns[it] == true }
        }
    }
    
    // PDF settings state
    var orientationOption by remember { mutableStateOf("Portrait") } // Portrait, Landscape
    var paperSizeOption by remember { mutableStateOf("A4") }       // A4, Letter
    var fontSizeOption by remember { mutableStateOf("Medium") }    // Small, Medium, Large
    
    var repeatHeader by remember { mutableStateOf(true) }
    var showSummary by remember { mutableStateOf(true) }
    var showPageNumbers by remember { mutableStateOf(true) }
    var autoFitColumns by remember { mutableStateOf(true) }
    
    // Progress/Loading state
    var isGenerating by remember { mutableStateOf(false) }
    
    // Filter bills based on dates
    val filteredBills = remember(bills, selectedFilterOption, customStartDate, customEndDate) {
        filterBillsByDateRange(bills, selectedFilterOption, customStartDate, customEndDate)
    }
    
    // Date Pickers helpers
    val calendar = Calendar.getInstance()
    val startDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            customStartDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    val endDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            customEndDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    
    // Computed formatting for Date Range in Preview
    val formattedDateRangeText = remember(selectedFilterOption, customStartDate, customEndDate) {
        if (selectedFilterOption == "Custom Date Range") {
            val start = if (customStartDate.isNotBlank()) formatDateToDdMmYyyy(customStartDate) else "Any"
            val end = if (customEndDate.isNotBlank()) formatDateToDdMmYyyy(customEndDate) else "Any"
            "$start to $end"
        } else {
            selectedFilterOption
        }
    }
    
    // Estimated Pages logic
    val estimatedPagesCount = remember(filteredBills.size, fontSizeOption, orientationOption) {
        val baseCount = when (fontSizeOption.lowercase()) {
            "small" -> 25
            "large" -> 12
            else -> 18
        }
        val count = if (orientationOption == "Landscape") baseCount - 3 else baseCount
        val pages = ceil(filteredBills.size.toDouble() / count.coerceAtLeast(1)).toInt()
        pages.coerceAtLeast(1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxHeight(0.95f).testTag("full_ledger_share_sheet"),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF130E20), // Premium Dark Slate theme matches existing app
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Sheet Header Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Share Full Ledger",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            
            HorizontalDivider(color = Color(0xFF322E3B))
            
            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // SECTION 1: Ledger Details
                item {
                    SectionHeader("1. Ledger Details")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1B24)),
                        border = BorderStroke(1.dp, Color(0xFF322E3B))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            DetailRow(label = "Ledger Name:", value = ledgerName)
                            DetailRow(label = "Ledger Type:", value = "${ledgerType.uppercase()} LEDGER")
                            DetailRow(label = "Firm Name:", value = firmName)
                            
                            val currentSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            DetailRow(label = "Date & Time:", value = currentSdf.format(Date()))
                        }
                    }
                }
                
                // SECTION 2: Date Filter
                item {
                    SectionHeader("2. Date Filter")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Horizontal Flow Row style layout of choice chips
                        Box(modifier = Modifier.fillMaxWidth()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                filterOptions.forEach { option ->
                                    val isSelected = selectedFilterOption == option
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedFilterOption = option },
                                        label = { Text(option, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentColor,
                                            selectedLabelColor = Color.Black,
                                            containerColor = Color(0xFF1F1B24),
                                            labelColor = Color.White
                                        ),
                                        border = BorderStroke(1.dp, if (isSelected) AccentColor else Color(0xFF322E3B))
                                    )
                                }
                            }
                        }
                        
                        // Custom Date Range selection dialog triggers
                        if (selectedFilterOption == "Custom Date Range") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DateFieldCard(
                                    label = "From Date",
                                    value = if (customStartDate.isNotBlank()) formatDateToDdMmYyyy(customStartDate) else "Select",
                                    onClick = { startDatePickerDialog.show() },
                                    modifier = Modifier.weight(1f)
                                )
                                DateFieldCard(
                                    label = "To Date",
                                    value = if (customEndDate.isNotBlank()) formatDateToDdMmYyyy(customEndDate) else "Select",
                                    onClick = { endDatePickerDialog.show() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                // SECTION 3: Column Selection
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader("3. Column Selection")
                        Text(
                            text = "${selectedColumns.values.count { it }} of 16 selected",
                            fontSize = 11.sp,
                            color = AccentColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                columnOrder.forEach { selectedColumns[it] = true }
                                savePreferences()
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentColor.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Select All", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                columnOrder.forEach { selectedColumns[it] = false }
                                savePreferences()
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Clear All", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                columnOrder.clear()
                                columnOrder.addAll(defaultColumns)
                                selectedColumns.clear()
                                defaultColumns.forEach { col ->
                                    selectedColumns[col] = col in defaultSelected
                                }
                                savePreferences()
                            },
                            modifier = Modifier.weight(1.2f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentColor.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Reset Default", fontSize = 10.sp)
                        }
                    }
                }
                
                // 16 Checkbox Cards in a custom FlowRow Grid
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        defaultColumns.forEach { col ->
                            val isSelected = selectedColumns[col] ?: false
                            ColumnCheckboxCard(
                                colName = col,
                                isSelected = isSelected,
                                onToggle = {
                                    selectedColumns[col] = !isSelected
                                    savePreferences()
                                },
                                modifier = Modifier.width(105.dp) // fits 3 side-by-side nicely on normal mobile screens
                            )
                        }
                    }
                }

                // Drag-and-drop column order
                item {
                    if (selectedList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SectionHeader("3b. Column Order")
                        Text(
                            text = "Drag ☰ to Reorder Columns (Saved Automatically)",
                            fontSize = 11.sp,
                            color = AccentColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        var activeDragIndex by remember { mutableStateOf<Int?>(null) }
                        var dragAccumulator by remember { mutableStateOf(0f) }
                        val density = LocalDensity.current
                        val itemHeightPx = with(density) { 46.dp.toPx() }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF121212), shape = RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            selectedList.forEachIndexed { index, col ->
                                val isDragging = activeDragIndex == index
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDragging) Color(0xFF322659) else Color(0xFF1F1B24)
                                    ),
                                    border = BorderStroke(1.dp, if (isDragging) AccentColor else Color(0xFF322E3B)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Drag handle with pointerInput
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(36.dp)
                                                .pointerInput(index) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            activeDragIndex = index
                                                            dragAccumulator = 0f
                                                        },
                                                        onDragEnd = {
                                                            activeDragIndex = null
                                                            savePreferences()
                                                        },
                                                        onDragCancel = {
                                                            activeDragIndex = null
                                                            savePreferences()
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragAccumulator += dragAmount.y
                                                            
                                                            val currIdx = activeDragIndex
                                                            if (currIdx != null) {
                                                                if (dragAccumulator > itemHeightPx && currIdx < selectedList.lastIndex) {
                                                                    // Swap in columnOrder
                                                                    val itemA = selectedList[currIdx]
                                                                    val itemB = selectedList[currIdx + 1]
                                                                    val idxA = columnOrder.indexOf(itemA)
                                                                    val idxB = columnOrder.indexOf(itemB)
                                                                    if (idxA != -1 && idxB != -1) {
                                                                        columnOrder[idxA] = itemB
                                                                        columnOrder[idxB] = itemA
                                                                        activeDragIndex = currIdx + 1
                                                                        dragAccumulator -= itemHeightPx
                                                                    }
                                                                } else if (dragAccumulator < -itemHeightPx && currIdx > 0) {
                                                                    // Swap in columnOrder
                                                                    val itemA = selectedList[currIdx]
                                                                    val itemB = selectedList[currIdx - 1]
                                                                    val idxA = columnOrder.indexOf(itemA)
                                                                    val idxB = columnOrder.indexOf(itemB)
                                                                    if (idxA != -1 && idxB != -1) {
                                                                        columnOrder[idxA] = itemB
                                                                        columnOrder[idxB] = itemA
                                                                        activeDragIndex = currIdx - 1
                                                                        dragAccumulator += itemHeightPx
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    )
                                                },
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = "☰",
                                                fontSize = 16.sp,
                                                color = AccentColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        Text(
                                            text = col,
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // SECTION 4: PDF Settings
                item {
                    SectionHeader("4. PDF Settings")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1B24)),
                        border = BorderStroke(1.dp, Color(0xFF322E3B))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Orientation (Portrait / Landscape)
                            Column {
                                Text("Orientation", fontSize = 11.sp, color = Color(0xFF8C8797), fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OptionChip(
                                        text = "Portrait",
                                        isSelected = orientationOption == "Portrait",
                                        onClick = { orientationOption = "Portrait" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OptionChip(
                                        text = "Landscape",
                                        isSelected = orientationOption == "Landscape",
                                        onClick = { orientationOption = "Landscape" },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            
                            // Paper Size (A4 / Letter)
                            Column {
                                Text("Paper Size", fontSize = 11.sp, color = Color(0xFF8C8797), fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OptionChip(
                                        text = "A4",
                                        isSelected = paperSizeOption == "A4",
                                        onClick = { paperSizeOption = "A4" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OptionChip(
                                        text = "Letter",
                                        isSelected = paperSizeOption == "Letter",
                                        onClick = { paperSizeOption = "Letter" },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            
                            // Font Size (Small / Medium / Large)
                            Column {
                                Text("Font Size", fontSize = 11.sp, color = Color(0xFF8C8797), fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OptionChip(
                                        text = "Small",
                                        isSelected = fontSizeOption == "Small",
                                        onClick = { fontSizeOption = "Small" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OptionChip(
                                        text = "Medium",
                                        isSelected = fontSizeOption == "Medium",
                                        onClick = { fontSizeOption = "Medium" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OptionChip(
                                        text = "Large",
                                        isSelected = fontSizeOption == "Large",
                                        onClick = { fontSizeOption = "Large" },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            
                            HorizontalDivider(color = Color(0xFF322E3B))
                            
                            // Toggles
                            ToggleRow(
                                title = "Repeat Header on Every Page",
                                isChecked = repeatHeader,
                                onCheckedChange = { repeatHeader = it }
                            )
                            ToggleRow(
                                title = "Show Summary",
                                isChecked = showSummary,
                                onCheckedChange = { showSummary = it }
                            )
                            ToggleRow(
                                title = "Show Page Numbers",
                                isChecked = showPageNumbers,
                                onCheckedChange = { showPageNumbers = it }
                            )
                            ToggleRow(
                                title = "Auto Fit Columns",
                                isChecked = autoFitColumns,
                                onCheckedChange = { autoFitColumns = it }
                            )
                        }
                    }
                }
                
                // SECTION 5: PDF Preview
                item {
                    SectionHeader("5. PDF Live Preview Status")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1A47)), // dark purple highlights
                        border = BorderStroke(1.dp, AccentColor.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            PreviewLabelValue("Firm Name:", firmName)
                            PreviewLabelValue("Ledger Owner:", ledgerName)
                            PreviewLabelValue("Selected Range:", formattedDateRangeText)
                            PreviewLabelValue("Total Bills Matches:", "${filteredBills.size} bills found")
                            
                            val colsList = selectedList.joinToString(", ")
                            PreviewLabelValue("Cols Selected:", if (colsList.isNotBlank()) colsList else "None")
                            
                            PreviewLabelValue("Est. Output Pages:", "$estimatedPagesCount pages")
                        }
                    }
                }
            }
            
            // Bottom Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("full_ledger_cancel_button")
                ) {
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = {
                        val activeCols = selectedList
                        if (activeCols.isEmpty()) {
                            Toast.makeText(context, "Please select at least 1 column", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (filteredBills.isEmpty()) {
                            Toast.makeText(context, "No records found in this date range", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        isGenerating = true
                        coroutineScope.launch {
                            val generatedFile = withContext(Dispatchers.IO) {
                                PdfGenerator.generateFullLedgerPdf(
                                    context = context,
                                    ledgerName = ledgerName,
                                    ledgerType = ledgerType,
                                    firmName = firmName,
                                    bills = filteredBills,
                                    payments = payments,
                                    dateRangeText = formattedDateRangeText,
                                    selectedColumns = activeCols,
                                    orientation = orientationOption,
                                    paperSize = paperSizeOption,
                                    fontSize = fontSizeOption,
                                    repeatHeader = repeatHeader,
                                    showSummary = showSummary,
                                    showPageNumbers = showPageNumbers,
                                    autoFit = autoFitColumns
                                )
                            }
                            isGenerating = false
                            if (generatedFile != null && generatedFile.exists()) {
                                PdfGenerator.sharePdf(context, generatedFile)
                                onDismissRequest()
                            } else {
                                Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("full_ledger_generate_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate PDF", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
    
    // Spinner Dialog Overlay during PDF Generation
    if (isGenerating) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
                Text(
                    "Generating Full Ledger PDF",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Composing tables, wrapping cell text, and generating multi-page layout. Please wait...", fontSize = 12.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = AccentColor,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF8C8797))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

@Composable
fun DateFieldCard(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(56.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1B24)),
        border = BorderStroke(1.dp, Color(0xFF322E3B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, fontSize = 9.sp, color = Color(0xFF8C8797))
                Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Icon(Icons.Default.DateRange, contentDescription = null, tint = AccentColor, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ColumnCheckboxCard(
    colName: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(44.dp)
            .toggleable(
                value = isSelected,
                onValueChange = { onToggle() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF322659) else Color(0xFF1F1B24)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) AccentColor else Color(0xFF322E3B)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null, // Handled by toggleable modifier
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentColor,
                    uncheckedColor = Color(0xFF8C8797)
                ),
                modifier = Modifier.size(16.dp).scale(0.8f)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = colName,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color(0xFFD0C4DF),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp
            )
        }
    }
}

// Extension to scale components easily
fun Modifier.scale(scale: Float): Modifier = this

@Composable
fun OptionChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(36.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AccentColor else Color(0xFF121212)
        ),
        border = BorderStroke(1.dp, if (isSelected) AccentColor else Color(0xFF322E3B)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.Black else Color.White
            )
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 11.sp, color = Color.White)
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF6200EE),
                checkedTrackColor = AccentColor,
                uncheckedThumbColor = Color(0xFF8C8797),
                uncheckedTrackColor = Color(0xFF1F1B24)
            ),
            modifier = Modifier.scale(0.7f)
        )
    }
}

@Composable
fun PreviewLabelValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = Color(0xFFD0C4DF), modifier = Modifier.width(100.dp))
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatDateToDdMmYyyy(dateStr: String): String {
    return try {
        val parts = dateStr.split("-")
        if (parts.size == 3 && parts[0].length == 4) {
            "${parts[2]}-${parts[1]}-${parts[0]}"
        } else {
            dateStr
        }
    } catch (e: Exception) {
        dateStr
    }
}

private fun filterBillsByDateRange(
    bills: List<ContractBill>,
    filterOption: String,
    customStart: String,
    customEnd: String
): List<ContractBill> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val now = Calendar.getInstance()
    now.set(Calendar.HOUR_OF_DAY, 0)
    now.set(Calendar.MINUTE, 0)
    now.set(Calendar.SECOND, 0)
    now.set(Calendar.MILLISECOND, 0)
    
    return bills.filter { bill ->
        val billDateStr = bill.date.trim()
        val parsedDate = try {
            if (billDateStr.contains("-")) {
                val parts = billDateStr.split("-")
                if (parts.size == 3) {
                    if (parts[0].length == 4) { // yyyy-MM-dd
                        sdf.parse(billDateStr)
                    } else if (parts[2].length == 4) { // dd-MM-yyyy
                        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(billDateStr)
                    } else {
                        sdf.parse(billDateStr)
                    }
                } else {
                    sdf.parse(billDateStr)
                }
            } else {
                sdf.parse(billDateStr)
            }
        } catch (e: Exception) {
            null
        }

        if (parsedDate == null) {
            true // fallback
        } else {
            when (filterOption) {
                "All Records" -> true
                "Today" -> {
                    val cal = Calendar.getInstance()
                    cal.time = parsedDate
                    val todayCal = Calendar.getInstance()
                    cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                }
                "Yesterday" -> {
                    val cal = Calendar.getInstance()
                    cal.time = parsedDate
                    val yesterdayCal = Calendar.getInstance()
                    yesterdayCal.add(Calendar.DAY_OF_YEAR, -1)
                    cal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)
                }
                "This Week" -> {
                    val cal = Calendar.getInstance()
                    cal.time = parsedDate
                    val weekCal = Calendar.getInstance()
                    cal.get(Calendar.YEAR) == weekCal.get(Calendar.YEAR) &&
                            cal.get(Calendar.WEEK_OF_YEAR) == weekCal.get(Calendar.WEEK_OF_YEAR)
                }
                "This Month" -> {
                    val cal = Calendar.getInstance()
                    cal.time = parsedDate
                    val monthCal = Calendar.getInstance()
                    cal.get(Calendar.YEAR) == monthCal.get(Calendar.YEAR) &&
                            cal.get(Calendar.MONTH) == monthCal.get(Calendar.MONTH)
                }
                "Last Month" -> {
                    val cal = Calendar.getInstance()
                    cal.time = parsedDate
                    val lastMonthCal = Calendar.getInstance()
                    lastMonthCal.add(Calendar.MONTH, -1)
                    cal.get(Calendar.YEAR) == lastMonthCal.get(Calendar.YEAR) &&
                            cal.get(Calendar.MONTH) == lastMonthCal.get(Calendar.MONTH)
                }
                "Custom Date Range" -> {
                    val start = try { sdf.parse(customStart) } catch (e: Exception) { null }
                    val end = try { sdf.parse(customEnd) } catch (e: Exception) { null }
                    if (start != null && end != null) {
                        !parsedDate.before(start) && !parsedDate.after(end)
                    } else if (start != null) {
                        !parsedDate.before(start)
                    } else if (end != null) {
                        !parsedDate.after(end)
                    } else {
                        true
                    }
                }
                else -> true
            }
        }
    }
}
