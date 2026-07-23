package com.smartfinance.tracker.ui.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Debt
import com.smartfinance.tracker.data.repository.DebtRepository
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.HashMap
import java.util.Locale

data class DebtUiState(
    val currentMonthLabel: String = "",
    val totalActiveDebt: Double = 0.0,
    val totalActiveReceivable: Double = 0.0,
    val displayedDebts: List<Debt> = emptyList(),
    val currentTab: String = "DEBT"
)

class DebtViewModel : ViewModel() {
    private val repository = DebtRepository()
    
    // Kita panggil TransactionRepository yang sudah ada di Tahap 3
    private val txRepository = TransactionRepository()
    
    private val _uiState = MutableStateFlow(DebtUiState())
    val uiState: StateFlow<DebtUiState> = _uiState

    private var currentCalendar = Calendar.getInstance()
    private var activeTab = "DEBT"
    private val sdfMonthLabel = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

    init {
        repository.startListening()
        viewModelScope.launch {
            repository.debts.collect { allDebts ->
                processDebts(allDebts)
            }
        }
    }

    fun changeMonth(amount: Int) {
        currentCalendar.add(Calendar.MONTH, amount)
        processDebts(repository.debts.value)
    }

    fun changeTab(tab: String) {
        activeTab = tab
        processDebts(repository.debts.value)
    }

    fun getCurrentTimeInMillis(): Long = currentCalendar.timeInMillis

    private fun processDebts(allDebts: List<Debt>) {
        var totalDebt = 0.0
        var totalReceivable = 0.0

        val targetMonth = currentCalendar.get(Calendar.MONTH)
        val targetYear = currentCalendar.get(Calendar.YEAR)

        val monthlyFilteredDebts = mutableListOf<Debt>()

        for (debt in allDebts) {
            if (!debt.isPaid) {
                if (debt.type == "DEBT") totalDebt += debt.remainingAmount
                else totalReceivable += debt.remainingAmount
            }

            val txCal = Calendar.getInstance().apply { timeInMillis = debt.timestamp }
            if (txCal.get(Calendar.MONTH) == targetMonth && txCal.get(Calendar.YEAR) == targetYear) {
                monthlyFilteredDebts.add(debt)
            }
        }

        val activeTabFiltered = monthlyFilteredDebts
            .filter { it.type == activeTab }
            .sortedByDescending { it.timestamp }

        _uiState.value = DebtUiState(
            currentMonthLabel = sdfMonthLabel.format(currentCalendar.time).uppercase(Locale.ROOT),
            totalActiveDebt = totalDebt,
            totalActiveReceivable = totalReceivable,
            displayedDebts = activeTabFiltered,
            currentTab = activeTab
        )
    }

    // --- FUNGSI BARU UNTUK MVVM WRITE ---

    suspend fun saveNewDebtAndTransaction(debtId: String, debtMap: HashMap<String, Any>, txId: String, txMap: HashMap<String, Any>) {
        repository.saveDebt(debtId, debtMap)
        
        // Kita juga perlu menambahkan fungsi saveTransaction di TransactionRepository nanti,
        // Tapi sementara kita bisa pakai FirebaseManager langsung di repository jika belum ada
        val firestore = com.smartfinance.tracker.utils.FirebaseManager.getFirestore()
        firestore.collection("transactions").document(txId).set(txMap).kotlinx.coroutines.tasks.await()
    }

    suspend fun processDebtInstallment(debtId: String, newRemaining: Double, isPaid: Boolean, txId: String, txMap: HashMap<String, Any>) {
        repository.updateDebtFields(debtId, mapOf("remainingAmount" to newRemaining, "isPaid" to isPaid))
        
        val firestore = com.smartfinance.tracker.utils.FirebaseManager.getFirestore()
        firestore.collection("transactions").document(txId).set(txMap).kotlinx.coroutines.tasks.await()
    }

    suspend fun deleteDebtPermanently(debtId: String) {
        repository.deleteDebt(debtId)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
