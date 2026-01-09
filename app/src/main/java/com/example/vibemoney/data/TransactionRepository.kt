package com.example.vibemoney.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TransactionRepository(private val transactionDao: TransactionDao) {
    
    // --- Ledger Operations ---
    val activeLedger: Flow<Ledger?> = transactionDao.getActiveLedger()
    
    val allLedgers: Flow<List<Ledger>> = transactionDao.getAllLedgers()

    suspend fun switchLedger(ledgerId: Int) {
        transactionDao.deactivateAllLedgers()
        transactionDao.setActiveLedger(ledgerId)
    }

    suspend fun createNewLedger(ledger: Ledger, fixedExpenses: List<Pair<String, Double>>) {
        transactionDao.deactivateAllLedgers()
        val newLedgerId = transactionDao.insertLedger(ledger).toInt()
        
        fixedExpenses.forEach { (name, amount) ->
            transactionDao.insertTransaction(
                Transaction(
                    ledgerId = newLedgerId,
                    amount = amount,
                    category = "Fixed Expense",
                    note = name,
                    date = System.currentTimeMillis(),
                    type = 0 // Expense
                )
            )
        }
    }

    suspend fun archiveExpiredLedgers(currentTime: Long) {
        val ledgers = transactionDao.getAllLedgers().first()
        ledgers.forEach { ledger ->
            if (!ledger.isClosed && currentTime > ledger.endDate) {
                val updatedLedger = ledger.copy(isClosed = true, isActive = false)
                transactionDao.insertLedger(updatedLedger)
            }
        }
    }

    // --- Transaction Operations ---
    fun getTransactionsByLedger(ledgerId: Int): Flow<List<Transaction>> = 
        transactionDao.getTransactionsByLedger(ledgerId)

    // 修复：指向正确的 DAO 方法名 getNetBalanceByLedger
    fun getTotalExpenseByLedger(ledgerId: Int): Flow<Double?> = 
        transactionDao.getNetBalanceByLedger(ledgerId)

    suspend fun insertTransaction(transaction: Transaction) = 
        transactionDao.insertTransaction(transaction)
    
    suspend fun deleteTransaction(transaction: Transaction) = 
        transactionDao.deleteTransaction(transaction)
}
