package com.example.vibemoney.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vibemoney.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

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

    // 单例 OkHttpClient 带超时配置
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // 单例 Retrofit（使用占位 baseUrl，实际请求通过 @Url 覆盖）
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val aiService: AiApiService by lazy {
        retrofit.create(AiApiService::class.java)
    }

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

    /**
     * 判断 URL 是否为 Gemini/Google 风格（需要 query param 认证）
     */
    private fun isGeminiStyle(url: String): Boolean {
        return url.contains("generativelanguage.googleapis.com", ignoreCase = true) ||
               url.contains("googleapis.com", ignoreCase = true)
    }

    /**
     * 根据 API 风格构建最终请求 URL 和认证信息
     */
    private fun buildRequestParams(baseUrl: String, apiKey: String): Pair<String, String?> {
        return if (isGeminiStyle(baseUrl)) {
            // Gemini 风格：key 作为 query parameter
            val separator = if (baseUrl.contains("?")) "&" else "?"
            val finalUrl = "$baseUrl${separator}key=$apiKey"
            finalUrl to null
        } else {
            // OpenAI 风格：Bearer token
            baseUrl to "Bearer $apiKey"
        }
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
                val (finalUrl, authHeader) = buildRequestParams(url, key)
                
                val response = aiService.getCompletion(
                    url = finalUrl,
                    auth = authHeader,
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
                _aiAnalysisResult.value = formatErrorMessage(e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * 格式化错误信息，提供更友好的提示
     */
    private fun formatErrorMessage(e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> "请求超时，请检查网络连接或稍后重试。"
            is IOException -> "网络错误: ${e.localizedMessage ?: "无法连接到服务器"}"
            is HttpException -> {
                val code = e.code()
                when (code) {
                    401 -> "认证失败 (401): API Key 无效或已过期。"
                    403 -> "访问被拒绝 (403): 请检查 API Key 权限。"
                    404 -> "接口不存在 (404): 请检查 API URL 是否正确。"
                    429 -> "请求过于频繁 (429): 请稍后重试。"
                    500, 502, 503 -> "服务器错误 ($code): 服务暂时不可用，请稍后重试。"
                    else -> "HTTP 错误 ($code): ${e.message()}"
                }
            }
            else -> "分析出错: ${e.localizedMessage ?: e.javaClass.simpleName}\n请检查 API Key 和 URL 是否匹配。"
        }
    }

    fun switchLedger(ledgerId: Int) { viewModelScope.launch { repository.switchLedger(ledgerId) } }

    fun startNewLedger(
        name: String,
        type: String,
        budget: Double,
        period: String,
        fixedExpenses: List<Pair<String, Double>>,
        customEndDate: Long? = null
    ) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val startDate = calendar.timeInMillis
            
            val endDate = when {
                // 临时账本：优先使用自定义结束日期，否则默认 7 天
                type == "Temporary" -> {
                    customEndDate ?: (startDate + 7L * 24 * 60 * 60 * 1000)
                }
                // 其他类型：使用周期计算
                else -> {
                    when (period) {
                        "Week" -> calendar.add(Calendar.DAY_OF_YEAR, 7)
                        "Month" -> calendar.add(Calendar.MONTH, 1)
                        "Quarter" -> calendar.add(Calendar.MONTH, 3)
                        "Year" -> calendar.add(Calendar.YEAR, 1)
                    }
                    calendar.timeInMillis
                }
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

    /**
     * 导出当前账本的交易记录为 CSV 文件
     * @return 成功返回 true，失败返回 false
     */
    suspend fun exportTransactionsToCsv(): Boolean {
        val ledger = activeLedger.value ?: return false
        val transactions = activeTransactions.value
        
        return withContext(Dispatchers.IO) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val csvContent = buildString {
                    // CSV 头部（带 BOM 以支持 Excel 中文显示）
                    append("\uFEFF")
                    appendLine("日期,类型,分类,金额,备注")
                    
                    transactions.forEach { tx ->
                        val date = dateFormat.format(Date(tx.date))
                        val type = if (tx.type == 0) "支出" else "收入"
                        val amount = if (tx.type == 0) "-${tx.amount}" else "+${tx.amount}"
                        // 处理 CSV 转义（备注中可能含逗号或引号）
                        val note = "\"${tx.note.replace("\"", "\"\"")}\""
                        appendLine("$date,$type,${tx.category},$amount,$note")
                    }
                }
                
                // 安全的文件名（移除非法字符）
                val safeFileName = ledger.name.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".csv"
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ 使用 MediaStore
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeFileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    
                    val resolver = getApplication<Application>().contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        ?: return@withContext false
                    
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
                    } ?: return@withContext false
                } else {
                    // Android 9 及以下直接写入 Downloads 目录
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, safeFileName)
                    FileOutputStream(file).use { fos ->
                        fos.write(csvContent.toByteArray(Charsets.UTF_8))
                    }
                }
                
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
