package com.example.vibemoney.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vibemoney.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class VibeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TransactionRepository
    private val sharedPrefs = application.getSharedPreferences("vibe_prefs", Context.MODE_PRIVATE)

    val activeLedgers: StateFlow<List<Ledger>>
    val archivedLedgers: StateFlow<List<Ledger>>
    val activeLedger: StateFlow<Ledger?>
    val activeTransactions: StateFlow<List<Transaction>>
    val currentSpent: StateFlow<Double>

    private val _apiKey = MutableStateFlow(sharedPrefs.getString("api_key", "") ?: "")
    val apiKey = _apiKey.asStateFlow()

    private val _apiUrl = MutableStateFlow(sharedPrefs.getString("api_url", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions") ?: "")
    val apiUrl = _apiUrl.asStateFlow()

    private val _apiModel = MutableStateFlow(sharedPrefs.getString("api_model", "gemini-2.0-flash") ?: "gemini-2.0-flash")
    val apiModel = _apiModel.asStateFlow()

    private val _language = MutableStateFlow(
        AppLanguage.valueOf(sharedPrefs.getString("app_language", AppLanguage.CHINESE.name) ?: AppLanguage.CHINESE.name)
    )
    val language = _language.asStateFlow()

    private val _aiAnalysisResult = MutableStateFlow<String?>(null)
    val aiAnalysisResult = _aiAnalysisResult.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing = _isAnalyzing.asStateFlow()

    init {
        val dao = AppDatabase.getDatabase(application).transactionDao()
        repository = TransactionRepository(dao)
        val allLedgersFlow = repository.allLedgers
        activeLedgers = allLedgersFlow.map { list -> list.filter { !it.isClosed } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        archivedLedgers = allLedgersFlow.map { list -> list.filter { it.isClosed } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        activeLedger = repository.activeLedger.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        activeTransactions = activeLedger.flatMapLatest { ledger -> ledger?.let { repository.getTransactionsByLedger(it.id) } ?: flowOf(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        currentSpent = activeLedger.flatMapLatest { ledger -> ledger?.let { repository.getTotalExpenseByLedger(it.id) } ?: flowOf(0.0) }.map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
        checkAndArchiveExpiredLedgers()
    }

    private fun checkAndArchiveExpiredLedgers() {
        viewModelScope.launch { repository.archiveExpiredLedgers(System.currentTimeMillis()) }
    }

    fun setApiSettings(key: String, url: String, model: String) {
        _apiKey.value = key
        _apiUrl.value = url
        _apiModel.value = model
        sharedPrefs.edit()
            .putString("api_key", key)
            .putString("api_url", url)
            .putString("api_model", model)
            .apply()
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        sharedPrefs.edit().putString("app_language", lang.name).apply()
    }

    fun analyzeWithAi() {
        val key = apiKey.value
        val url = apiUrl.value
        val model = apiModel.value
        if (key.isBlank() || url.isBlank()) return

        _isAnalyzing.value = true
        _aiAnalysisResult.value = "AI正在思考中..."
        
        viewModelScope.launch {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://api.openai.com/") 
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
                val service = retrofit.create(AiApiService::class.java)
                val response = service.getCompletion(
                    url = url,
                    auth = "Bearer $key",
                    request = AiRequest(
                        model = model, 
                        messages = listOf(
                            AiMessage(role = "system", content = "你是一个财务理财助手。"),
                            AiMessage(role = "user", content = generatePrompt())
                        )
                    )
                )
                _aiAnalysisResult.value = response.choices.firstOrNull()?.message?.content ?: "未获得AI回复"
            } catch (e: Exception) {
                _aiAnalysisResult.value = "分析出错: ${e.localizedMessage}\n请检查 API Key 和 URL 是否匹配。"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun switchLedger(ledgerId: Int) { viewModelScope.launch { repository.switchLedger(ledgerId) } }

    fun startNewLedger(
        name: String,
        type: String,
        budget: Double,
        period: String,
        fixedExpenses: List<Pair<String, Double>>,
        customEndDate: Long? = null // 新增：支持自定义结束日期
    ) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val startDate = calendar.timeInMillis
            
            val endDate = if (customEndDate != null) {
                customEndDate
            } else {
                when (period) {
                    "Week" -> calendar.add(Calendar.DAY_OF_YEAR, 7)
                    "Month" -> calendar.add(Calendar.MONTH, 1)
                    "Quarter" -> calendar.add(Calendar.MONTH, 3)
                    "Year" -> calendar.add(Calendar.YEAR, 1)
                }
                calendar.timeInMillis
            }

            val newLedger = Ledger(
                name = name,
                type = type,
                iconRes = 0,
                totalBudget = budget,
                startDate = startDate,
                endDate = endDate,
                isActive = true,
                isClosed = false
            )
            repository.createNewLedger(newLedger, fixedExpenses)
        }
    }

    fun addTransaction(note: String, amount: Double, category: String, type: Int) {
        val currentLedger = activeLedger.value ?: return
        if (currentLedger.isClosed || System.currentTimeMillis() > currentLedger.endDate) return
        viewModelScope.launch { repository.insertTransaction(Transaction(ledgerId = currentLedger.id, note = note, amount = amount, category = category, type = type, date = System.currentTimeMillis())) }
    }

    fun getDailySuggestion(): Double {
        val ledger = activeLedger.value ?: return 0.0
        val remainingBudget = ledger.totalBudget - currentSpent.value
        val now = System.currentTimeMillis()
        if (now > ledger.endDate) {
            val totalDays = ((ledger.endDate - ledger.startDate) / (1000 * 60 * 60 * 24)).coerceAtLeast(1)
            return currentSpent.value / totalDays
        }
        val remainingDays = ((ledger.endDate - now) / (1000 * 60 * 60 * 24)).coerceAtLeast(1)
        return remainingBudget / remainingDays
    }

    fun generatePrompt(): String {
        val ledger = activeLedger.value ?: return ""
        val transactionsText = activeTransactions.value.joinToString("\n") { "- ${if(it.type == 0) "支出" else "收入"}: ¥${it.amount}, 分类: ${it.category}, 备注: ${it.note}" }
        return "你是一位专业的财务分析师。请分析我本周期的账单：\n账本名称：${ledger.name}\n周期类型：${ledger.type}\n总预算：¥${ledger.totalBudget}\n净支出：¥${currentSpent.value}\n详细流水如下：\n$transactionsText\n请给出简洁的分析结果，直接以纯文本格式回答，不要使用Markdown。"
    }

    fun clearAiResult() { _aiAnalysisResult.value = null }
}
