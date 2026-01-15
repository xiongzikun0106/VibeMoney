package com.example.vibemoney.data

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface AiApiService {
    /**
     * 通用 Chat Completions 接口
     * @param url 完整请求地址（可含 query 参数）
     * @param auth 可选 Authorization header，OpenAI 风格使用 "Bearer xxx"
     */
    @POST
    suspend fun getCompletion(
        @Url url: String,
        @Header("Authorization") auth: String? = null,
        @Body request: AiRequest
    ): AiResponse
}

data class AiRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<AiMessage>
)

data class AiMessage(
    val role: String,
    val content: String
)

data class AiResponse(
    val choices: List<AiChoice>
)

data class AiChoice(
    val message: AiMessage
)
