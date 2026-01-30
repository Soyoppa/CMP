package org.example.project.repository

import org.example.project.data.GoogleAppsScriptRepository
import org.example.project.data.GoogleSheetsApi
import org.example.project.model.Transaction

class TransactionRepository {
    private val sheetsApi = GoogleSheetsApi()
    private val scriptRepo = GoogleAppsScriptRepository()
    
    suspend fun getAllTransactions(): List<Transaction> {
        return sheetsApi.getTransactions()
    }
    
    suspend fun addTransaction(transaction: Transaction): Boolean {
        // Use Google Apps Script for writing (more reliable)
        return scriptRepo.addTransaction(transaction)
    }
    
    suspend fun testScriptConnection(): String {
        return scriptRepo.testConnection()
    }
}