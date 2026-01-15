package com.example.vibemoney.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.vibemoney.ui.VibeViewModel
import com.example.vibemoney.ui.getString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(viewModel: VibeViewModel, onBack: () -> Unit) {
    val strings = getString()
    
    var note by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var type by remember { mutableIntStateOf(0) } // 0 为支出
    
    // 分类映射，UI 显示翻译后的文字，但数据库保存 key
    val categoryList = listOf("Food", "Transport", "Shopping", "Bills", "General")
    val categoryDisplay = mapOf(
        "Food" to strings.catFood,
        "Transport" to strings.catTransport,
        "Shopping" to strings.catShopping,
        "Bills" to strings.catBills,
        "General" to strings.catGeneral
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(strings.addTransaction) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(strings.note) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(strings.amount) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Text(strings.category)
            ScrollableTabRow(
                selectedTabIndex = categoryList.indexOf(category),
                edgePadding = 0.dp,
                divider = {}
            ) {
                categoryList.forEach { catKey ->
                    Tab(
                        selected = category == catKey,
                        onClick = { category = catKey },
                        text = { Text(categoryDisplay[catKey] ?: catKey) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(
                    selected = type == 0,
                    onClick = { type = 0 },
                    label = { Text(strings.expense) }
                )
                FilterChip(
                    selected = type == 1,
                    onClick = { type = 1 },
                    label = { Text(strings.income) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (note.isNotEmpty() && amt > 0) {
                        viewModel.addTransaction(note, amt, category, type)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.save)
            }
        }
    }
}
