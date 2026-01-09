package com.example.vibemoney.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vibemoney.ui.AppLanguage
import com.example.vibemoney.ui.VibeViewModel
import com.example.vibemoney.ui.getString

@Composable
fun SettingsScreen(viewModel: VibeViewModel) {
    val apiKey by viewModel.apiKey.collectAsState()
    val apiUrl by viewModel.apiUrl.collectAsState()
    val apiModel by viewModel.apiModel.collectAsState()
    val activeLedger by viewModel.activeLedger.collectAsState()
    val currentLanguage by viewModel.language.collectAsState()
    
    val strings = getString()

    var tempKey by remember { mutableStateOf(apiKey) }
    var tempUrl by remember { mutableStateOf(apiUrl) }
    var tempModel by remember { mutableStateOf(apiModel) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(strings.settings, style = MaterialTheme.typography.headlineMedium)

        // 1. Language Selection
        Text(strings.language, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = currentLanguage == AppLanguage.CHINESE,
                onClick = { viewModel.setLanguage(AppLanguage.CHINESE) },
                label = { Text("中文") }
            )
            FilterChip(
                selected = currentLanguage == AppLanguage.ENGLISH,
                onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                label = { Text("English") }
            )
        }

        HorizontalDivider()

        // 2. Active Ledger Info
        activeLedger?.let { ledger ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.activeLedger, style = MaterialTheme.typography.titleMedium)
                    Text("${strings.name}: ${ledger.name}", style = MaterialTheme.typography.bodyMedium)
                    Text("${strings.budget}: ¥${ledger.totalBudget}", style = MaterialTheme.typography.bodyMedium)
                    Text("${strings.type}: ${when(ledger.type) {
                        "Daily" -> strings.daily
                        "Temporary" -> strings.temporary
                        "Special" -> strings.special
                        else -> ledger.type
                    }}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        HorizontalDivider()

        // 3. AI Config
        Text(strings.llmConfig, style = MaterialTheme.typography.titleMedium)
        Text(strings.llmDesc, style = MaterialTheme.typography.bodySmall)
        
        OutlinedTextField(
            value = tempUrl,
            onValueChange = { tempUrl = it },
            label = { Text(strings.apiUrl) },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = tempKey,
            onValueChange = { tempKey = it },
            label = { Text(strings.apiKey) },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = tempModel,
            onValueChange = { tempModel = it },
            label = { Text(strings.apiModel) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Button(
            onClick = { viewModel.setApiSettings(tempKey, tempUrl, tempModel) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strings.saveSettings)
        }

        Text(
            strings.persistNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4. About Section (Updated)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(strings.about, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(strings.version, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(strings.aiPowered, style = MaterialTheme.typography.labelMedium)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
