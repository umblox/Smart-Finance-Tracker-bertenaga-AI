package com.smartfinance.tracker.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartfinance.tracker.data.local.DatabaseProvider
import com.smartfinance.tracker.data.model.Debt
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.worker.AiWorkerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class RecurringTxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.db
        val now = System.currentTimeMillis()

        try {
            val dueSchedules = db.recurringTxDao().getDueTransactions(now)

            for (doc in dueSchedules) {
                val amount = doc.amount
                val type = doc.type.uppercase(Locale.ROOT)
                val catId = doc.categoryId
                val catName = doc.categoryName
                val note = doc.note
                val interval = doc.interval
                val hasEndDate = doc.hasEndDate
                val endDate = doc.endDate ?: Long.MAX_VALUE
                val contactName = doc.contactName

                if (amount <= 0.0) continue

                val txId = "tx_${System.currentTimeMillis()}_${(1000..9999).random()}"
                
                if (type == "DEBT" || type == "RECEIVABLE") {
                    val debtId = "debt_${System.currentTimeMillis()}_${(1000..9999).random()}"
                    val cName = contactName.ifEmpty { "SISTEM BERKALA" }.uppercase(Locale.ROOT)
                    val flowType = if (type == "RECEIVABLE") "EXPENSE" else "INCOME"
                    val realCatId = if (type == "RECEIVABLE") 104L else 101L
                    val realCatName = if (type == "RECEIVABLE") "Piutang" else "Hutang"
                    val stNote = if (type == "RECEIVABLE") "MEMBERIKAN PINJAMAN KEPADA $cName" else "MENERIMA PINJAMAN DARI $cName"

                    val debt = Debt(
                        id = debtId, contactName = cName, contactPhoneNumber = "", amount = amount, 
                        remainingAmount = amount, type = type, note = note, timestamp = now, isPaid = false
                    )
                    db.debtDao().insert(debt)

                    val tx = Transaction(
                        id = txId, amount = amount, type = flowType, categoryId = realCatId, 
                        categoryName = realCatName, note = "$stNote ($note)", timestamp = now, debtId = debtId
                    )
                    db.transactionDao().insert(tx)
                } else {
                    val tx = Transaction(
                        id = txId, amount = amount, type = type, categoryId = catId, 
                        categoryName = catName, note = note, timestamp = now
                    )
                    db.transactionDao().insert(tx)
                }

                AiWorkerManager.triggerRecurringAlert(applicationContext, note, amount, true)

                val cal = Calendar.getInstance()
                cal.timeInMillis = if (doc.nextExecutionTime > 0) doc.nextExecutionTime else now
                
                while (cal.timeInMillis <= now) {
                    when (interval) {
                        "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                        "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                        "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                        "YEARLY" -> cal.add(Calendar.YEAR, 1)
                        else -> cal.add(Calendar.MONTH, 1)
                    }
                }
                val nextTime = cal.timeInMillis

                if (hasEndDate && nextTime > endDate) {
                    db.recurringTxDao().update(doc.copy(isActive = false))
                } else {
                    db.recurringTxDao().update(doc.copy(nextExecutionTime = nextTime))
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry() 
        }
    }
}
