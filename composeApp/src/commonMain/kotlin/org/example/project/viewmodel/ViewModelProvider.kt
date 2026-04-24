package org.example.project.viewmodel

import androidx.compose.runtime.Composable

@Composable
expect fun createTransactionViewModel(): TransactionViewModel

@Composable
expect fun createAiViewModel(): AiViewModel
