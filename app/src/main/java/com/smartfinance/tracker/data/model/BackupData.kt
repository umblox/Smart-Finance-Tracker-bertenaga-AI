package com.smartfinance.tracker.data.model

data class BackupData(
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val recurringTransactions: List<RecurringTransaction> = emptyList(),
    val aiNotifications: List<AiNotification> = emptyList()
)

