package org.example.project.repository

import org.example.project.data.GoogleAppsScriptRepository
import org.example.project.data.GoogleSheetsApi
import org.example.project.model.AiSummaryRecord
import org.example.project.model.Transaction

class TransactionRepository {
    private val sheetsApi = GoogleSheetsApi()
    private val scriptRepo = GoogleAppsScriptRepository()
    
    suspend fun getAllTransactions(): List<Transaction> {
        return sheetsApi.getTransactions()
    }

    /**
     * Fetches the monthly category summary records from the AISummaryRecords sheet.
     * Used to give the AI richer context for financial analysis.
     */
    suspend fun getAiSummaryRecords(): List<AiSummaryRecord> {
        return sheetsApi.getAiSummaryRecords()
    }
    
    suspend fun addTransaction(transaction: Transaction): Boolean {
        return scriptRepo.addTransaction(transaction)
    }
    
    suspend fun testScriptConnection(): String {
        return scriptRepo.testConnection()
    }
}