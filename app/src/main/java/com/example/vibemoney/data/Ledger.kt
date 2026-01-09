package com.example.vibemoney.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledgers")
data class Ledger(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String, // "Daily", "Temporary", "Special"
    val iconRes: Int, 
    val totalBudget: Double,
    val startDate: Long,
    val endDate: Long,
    val isActive: Boolean = false,
    val isClosed: Boolean = false // 新增：标记账本是否已结算/结束
)
