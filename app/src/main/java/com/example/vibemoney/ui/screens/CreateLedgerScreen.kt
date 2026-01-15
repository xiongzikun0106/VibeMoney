package com.example.vibemoney.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.vibemoney.ui.VibeViewModel
import com.example.vibemoney.ui.getString
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateLedgerScreen(
    viewModel: VibeViewModel,
    onFinished: () -> Unit = {},
    canCancel: Boolean = false
) {
    val strings = getString()
    var name by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var selectedPeriod by remember { mutableStateOf("Month") }
    var selectedType by remember { mutableStateOf("Daily") }
    
    // 自定义结束日期状态
    var showDatePicker by remember { mutableStateOf(false) }
    val calendar = remember { Calendar.getInstance() }
    var customEndDate by remember { mutableStateOf<Long?>(null) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() + 86400000 * 7 // 默认一周后
    )

    val periods = listOf("Week", "Month", "Quarter", "Year")
    val periodDisplay = mapOf(
        "Week" to strings.week,
        "Month" to strings.month,
        "Quarter" to strings.quarter,
        "Year" to strings.year
    )
    
    val types = listOf("Daily", "Temporary", "Special")
    val typeDisplay = mapOf(
        "Daily" to strings.daily,
        "Temporary" to strings.temporary,
        "Special" to strings.special
    )
    
    val fixedExpenses = remember { mutableStateListOf<Pair<String, String>>() }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customEndDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text(strings.save) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.newLedger) },
                navigationIcon = {
                    if (canCancel) {
                        TextButton(onClick = onFinished) {
                            Text(strings.cancel)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                strings.setupBudget,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.ledgerName) },
                modifier = Modifier.fillMaxWidth()
            )

            Text(strings.ledgerType, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(typeDisplay[type] ?: type) }
                    )
                }
            }

            // 周期或自定义时间选择
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text(strings.totalBudget) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(modifier = Modifier.width(16.dp))
                
                if (selectedType == "Temporary") {
                    // 临时账本显示自定义日期选择
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth, 
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val df = SimpleDateFormat("MM/dd", Locale.getDefault())
                        Text(
                            text = customEndDate?.let { df.format(Date(it)) } ?: strings.selectEndDate,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    // 其他账本显示周期下拉
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        FilterChip(
                            selected = true,
                            onClick = { expanded = true },
                            label = { Text(periodDisplay[selectedPeriod] ?: selectedPeriod) }
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            periods.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(periodDisplay[p] ?: p) },
                                    onClick = {
                                        selectedPeriod = p
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.fixedExpenses, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { fixedExpenses.add("" to "") }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(fixedExpenses.size) { index ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = fixedExpenses[index].first,
                            onValueChange = { fixedExpenses[index] = it to fixedExpenses[index].second },
                            label = { Text(strings.name) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = fixedExpenses[index].second,
                            onValueChange = { fixedExpenses[index] = fixedExpenses[index].first to it },
                            label = { Text("¥") },
                            modifier = Modifier.weight(0.6f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        IconButton(onClick = { fixedExpenses.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }

            // 验证：临时账本必须选择结束日期
            val isTemporaryWithoutDate = selectedType == "Temporary" && customEndDate == null
            val budgetValue = budget.toDoubleOrNull() ?: 0.0
            val canCreate = name.isNotEmpty() && budgetValue > 0 && !isTemporaryWithoutDate

            // 临时账本未选日期时显示提示
            if (isTemporaryWithoutDate && name.isNotEmpty() && budgetValue > 0) {
                Text(
                    text = strings.endDateRequired,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    if (canCreate) {
                        val fixedList = fixedExpenses.mapNotNull { 
                            val amt = it.second.toDoubleOrNull()
                            if (it.first.isNotEmpty() && amt != null) it.first to amt else null
                        }
                        viewModel.startNewLedger(
                            name = name,
                            type = selectedType,
                            budget = budgetValue,
                            period = selectedPeriod,
                            fixedExpenses = fixedList,
                            customEndDate = if (selectedType == "Temporary") customEndDate else null
                        )
                        onFinished()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canCreate,
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(strings.createLedger, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
