package com.example.vibemoney.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    // --- Ledger Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedger(ledger: Ledger): Long

    @Query("SELECT * FROM ledgers ORDER BY startDate DESC")
    fun getAllLedgers(): Flow<List<Ledger>>

    @Query("SELECT * FROM ledgers WHERE isActive = 1 LIMIT 1")
    fun getActiveLedger(): Flow<Ledger?>

    @Query("UPDATE ledgers SET isActive = 0")
    suspend fun deactivateAllLedgers()

    @Query("UPDATE ledgers SET isActive = 1 WHERE id = :ledgerId")
    suspend fun setActiveLedger(ledgerId: Int)

    // --- Transaction Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE ledgerId = :ledgerId ORDER BY date DESC")
    fun getTransactionsByLedger(ledgerId: Int): Flow<List<Transaction>>

    // 修改：同时考虑支出(0)和收入(1)，计算净支出或根据收入增加预算逻辑
    @Query("SELECT SUM(CASE WHEN type = 0 THEN amount ELSE -amount END) FROM transactions WHERE ledgerId = :ledgerId")
    fun getNetBalanceByLedger(ledgerId: Int): Flow<Double?>
}
