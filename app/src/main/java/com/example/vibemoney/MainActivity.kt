package com.example.vibemoney

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.vibemoney.ui.*
import com.example.vibemoney.ui.screens.AddTransactionScreen
import com.example.vibemoney.ui.screens.HomeScreen
import com.example.vibemoney.ui.screens.SettingsScreen
import com.example.vibemoney.ui.theme.VibeMoneyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: VibeViewModel = viewModel()
            val language by viewModel.language.collectAsState()
            
            val appStrings = if (language == AppLanguage.CHINESE) zhStrings else enStrings

            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                VibeMoneyTheme {
                    VibeApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun VibeApp(viewModel: VibeViewModel) {
    val navController = rememberNavController()
    val strings = getString()
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Settings")
    val labels = listOf("Home", strings.settings)
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Settings)

    val activeLedger by viewModel.activeLedger.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(labels[index]) },
                        selected = selectedItem == index,
                        onClick = {
                            selectedItem = index
                            navController.navigate(item)
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            // 只有当有活跃账本且未结束时，才显示添加按钮
            if (activeLedger != null && !activeLedger!!.isClosed) {
                FloatingActionButton(
                    onClick = { navController.navigate("Add") }
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.addTransaction)
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "Home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("Home") { HomeScreen(viewModel) }
            composable("Settings") { SettingsScreen(viewModel) }
            composable("Add") { AddTransactionScreen(viewModel) { navController.popBackStack() } }
        }
    }
}
