package com.nabla.chatovoice.domain.repository

import com.nabla.chatovoice.domain.model.ChatResponse

interface GatewayRepository {
    suspend fun chat(userText: String, screenContext: String?): Result<ChatResponse>
}
