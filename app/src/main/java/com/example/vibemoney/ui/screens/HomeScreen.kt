package com.example.vibemoney.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vibemoney.data.Ledger
import com.example.vibemoney.ui.VibeViewModel
import com.example.vibemoney.ui.getString
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(viewModel: VibeViewModel) {
    val activeLedgers by viewModel.activeLedgers.collectAsState()
    val activeLedger by viewModel.activeLedger.collectAsState()
    val transactions by viewModel.activeTransactions.collectAsState()
    val currentSpent by viewModel.currentSpent.collectAsState()
    val aiResult by viewModel.aiAnalysisResult.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    
    val strings = getString()
    val context = LocalContext.current
    var isAddingNewLedger by remember { mutableStateOf(false) }

    if (activeLedgers.isEmpty() || isAddingNewLedger) {
        CreateLedgerScreen(
            viewModel = viewModel, 
            onFinished = { isAddingNewLedger = false },
            canCancel = activeLedgers.isNotEmpty()
        )
    } else {
        val initialPage = remember(activeLedgers, activeLedger) {
            val index = activeLedgers.indexOfFirst { it.id == activeLedger?.id }
            if (index != -1) index else 0
        }
        
        val pagerState = rememberPagerState(
            initialPage = initialPage,
            pageCount = { activeLedgers.size }
        )

        val listState = rememberLazyListState()
        val isFabExtended by remember {
            derivedStateOf { listState.firstVisibleItemIndex == 0 }
        }

        LaunchedEffect(pagerState.currentPage) {
            val selectedLedger = activeLedgers.getOrNull(pagerState.currentPage)
            if (selectedLedger != null && selectedLedger.id != activeLedger?.id) {
                viewModel.switchLedger(selectedLedger.id)
            }
        }

        val themeColor by animateColorAsState(
            targetValue = when (activeLedger?.type) {
                "Temporary" -> Color(0xFFFF9800)
                "Special" -> Color(0xFFE91E63)
                else -> MaterialTheme.colorScheme.primary
            }, label = "ThemeColor"
        )

        var showAiAssistant by remember { mutableStateOf(false) }

        if (showAiAssistant) {
            AiAssistantDialog(
                isApiConfigured = apiKey.isNotBlank(),
                aiResult = aiResult,
                isAnalyzing = isAnalyzing,
                onCopyPrompt = {
                    val prompt = viewModel.generatePrompt()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("VibeMoney Prompt", prompt)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, strings.promptCopied, Toast.LENGTH_SHORT).show()
                    showAiAssistant = false
                },
                onStartAnalysis = {
                    viewModel.analyzeWithAi()
                },
                onDismiss = { 
                    showAiAssistant = false
                    viewModel.clearAiResult()
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            activeLedger?.name ?: strings.welcome,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { isAddingNewLedger = true },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = themeColor.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = strings.newLedger, tint = themeColor)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        pageSpacing = 16.dp,
                        modifier = Modifier.height(180.dp)
                    ) { page ->
                        val ledger = activeLedgers.getOrNull(page)
                        if (ledger != null) {
                            LedgerCard(ledger = ledger, isSelected = pagerState.currentPage == page, themeColor = themeColor)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    val isExpired = activeLedger?.let { System.currentTimeMillis() > it.endDate } ?: false
                    val budget = activeLedger?.totalBudget ?: 1.0
                    val progress = (1f - (currentSpent / budget).toFloat()).coerceIn(0f, 1f)
                    
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                        CircularProgressRing(progress = progress, color = if (isExpired) Color.Gray else themeColor)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isExpired) {
                                Text(strings.finished, style = MaterialTheme.typography.headlineSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                            } else {
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = themeColor
                                )
                                Text(strings.remaining, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    val isExpired = activeLedger?.let { System.currentTimeMillis() > it.endDate } ?: false
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = themeColor.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(if (isExpired) strings.finished else strings.dailyLimit, style = MaterialTheme.typography.labelMedium)
                                Text("¥${String.format(Locale.getDefault(), "%.2f", viewModel.getDailySuggestion())}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            VerticalDivider(modifier = Modifier.height(40.dp), color = themeColor.copy(alpha = 0.3f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(strings.netSpent, style = MaterialTheme.typography.labelMedium)
                                Text("¥${String.format(Locale.getDefault(), "%.2f", currentSpent)}", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Text(
                        strings.recentTransactions,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(transactions) { transaction ->
                    TransactionItem(transaction)
                }
            }

            FloatingActionButton(
                onClick = { showAiAssistant = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp),
                containerColor = if (activeLedger?.let { System.currentTimeMillis() > it.endDate } == true) Color.Gray else themeColor,
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    AnimatedVisibility(visible = isFabExtended) {
                        Text(
                            text = strings.aiSummary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiAssistantDialog(
    isApiConfigured: Boolean,
    aiResult: String?,
    isAnalyzing: Boolean,
    onCopyPrompt: () -> Unit,
    onStartAnalysis: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = getString()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.aiAssistant, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (aiResult == null && !isAnalyzing) {
                    Text(strings.chooseAnalysis, style = MaterialTheme.typography.bodyMedium)
                    
                    Surface(
                        onClick = onStartAnalysis,
                        enabled = isApiConfigured,
                        shape = RoundedCornerShape(16.dp),
                        color = if (isApiConfigured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome, 
                                contentDescription = null,
                                tint = if (isApiConfigured) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    strings.apiAnalysis, 
                                    fontWeight = FontWeight.Bold,
                                    color = if (isApiConfigured) Color.Unspecified else Color.Gray
                                )
                                if (!isApiConfigured) {
                                    Text(strings.apiNotConfigured, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = onCopyPrompt,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(strings.copyPrompt, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .heightIn(min = 100.dp, max = 400.dp)
                        ) {
                            if (isAnalyzing && (aiResult == null || aiResult == "AI正在思考中...")) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(strings.thinking, style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    Text(
                                        text = aiResult ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    if (!isAnalyzing) {
                        TextButton(
                            onClick = { onDismiss() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(strings.back)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (aiResult != null || isAnalyzing) strings.close else strings.cancel) }
        }
    )
}

@Composable
fun LedgerCard(ledger: Ledger, isSelected: Boolean, themeColor: Color) {
    val strings = getString()
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) themeColor else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.padding(20.dp).fillMaxSize()) {
            Column {
                Icon(
                    imageVector = when(ledger.type) {
                        "Temporary" -> Icons.Default.AccessTime
                        "Special" -> Icons.Default.Star
                        else -> Icons.Default.AccountBalanceWallet
                    },
                    contentDescription = null,
                    tint = if (isSelected) Color.White else themeColor,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(ledger.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Color.Unspecified)
                val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                Text("${dateFormat.format(Date(ledger.startDate))} - ${dateFormat.format(Date(ledger.endDate))}", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = if (isSelected) Color.White.copy(alpha = 0.7f) else Color.Unspecified
                )
                Text("${strings.budget}: ¥${ledger.totalBudget}", style = MaterialTheme.typography.bodyMedium, color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color.Unspecified)
            }
        }
    }
}

@Composable
fun WelcomeGlassScreen(onStart: () -> Unit) {
    val strings = getString()
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
                .height(400.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .blur(20.dp)
        )
        
        Column(
            modifier = Modifier.padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(strings.welcome, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            Text(strings.aiDriven, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(strings.createFirst)
            }
        }
    }
}

@Composable
fun CircularProgressRing(progress: Float, color: Color) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        drawArc(
            color = color.copy(alpha = 0.1f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = animatedProgress * 360f,
            useCenter = false,
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun TransactionItem(transaction: com.example.vibemoney.data.Transaction) {
    ListItem(
        headlineContent = { Text(transaction.note) },
        supportingContent = { Text(transaction.category) },
        trailingContent = {
            Text(
                "${if(transaction.type == 0) "-" else "+"}¥${transaction.amount}",
                fontWeight = FontWeight.Bold,
                color = if (transaction.type == 0) Color.Unspecified else Color(0xFF388E3C)
            )
        }
    )
}
