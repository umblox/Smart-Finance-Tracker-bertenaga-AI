package com.smartfinance.tracker.ui.transaction

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smartfinance.tracker.MainActivity
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.databinding.FragmentHistoryTransactionBinding
import com.smartfinance.tracker.ui.report.ReportFragment
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class HistoryTransactionFragment : Fragment() {

    private var _binding: FragmentHistoryTransactionBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TransactionViewModel
    
    // Kembalikan variabel filter Anda yang sempat saya hapus!
    private var currentCalendar = Calendar.getInstance()
    private var searchQuery = ""
    private val sdfMonthLabel = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfDateOnly = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID"))
    private val sdfDayNum = SimpleDateFormat("dd", Locale("id", "ID"))
    private val sdfMonthYear = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[TransactionViewModel::class.java]
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())

        // Setup Bulan
        updateMonthLabel()
        binding.btnPrevMonth.setOnClickListener { changeMonth(-1) }
        binding.btnNextMonth.setOnClickListener { changeMonth(1) }

        // Filter Pencarian Real-time
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString().lowercase(Locale.ROOT)
                processAndRenderTransactions(viewModel.transactions.value)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnViewReport.setOnClickListener {
            (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.transactions.collect { allTx ->
                processAndRenderTransactions(allTx)
            }
        }
    }

    private fun changeMonth(amount: Int) {
        currentCalendar.add(Calendar.MONTH, amount)
        updateMonthLabel()
        processAndRenderTransactions(viewModel.transactions.value)
    }

    private fun updateMonthLabel() {
        binding.tvMonthLabel.text = sdfMonthLabel.format(currentCalendar.time).uppercase(Locale.ROOT)
    }

    private fun processAndRenderTransactions(allTransactions: List<Transaction>) {
        val targetMonth = currentCalendar.get(Calendar.MONTH)
        val targetYear = currentCalendar.get(Calendar.YEAR)

        // 1. Filter Berdasarkan Bulan yang Dipilih
        var filteredList = allTransactions.filter { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            cal.get(Calendar.MONTH) == targetMonth && cal.get(Calendar.YEAR) == targetYear
        }

        // 2. Filter Berdasarkan Kolom Pencarian
        if (searchQuery.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.note.lowercase(Locale.ROOT).contains(searchQuery) ||
                it.categoryName.lowercase(Locale.ROOT).contains(searchQuery) ||
                it.amount.toString().contains(searchQuery)
            }
        }

        // 3. Hitung Ringkasan (Hanya untuk data yang sudah difilter)
        var totalIncome = 0.0
        var totalExpense = 0.0

        for (tx in filteredList) {
            if (tx.type == "INCOME" || tx.type == "DEBT") {
                totalIncome += tx.amount
            } else {
                totalExpense += tx.amount
            }
        }
        val netBalance = totalIncome - totalExpense

        binding.tvTotalIncome.text = "+${formatRupiah.format(totalIncome)}"
        binding.tvTotalExpense.text = "-${formatRupiah.format(totalExpense)}"
        binding.tvTotalBalance.text = formatRupiah.format(netBalance)
        binding.tvTotalBalance.setTextColor(if (netBalance >= 0) Color.WHITE else Color.parseColor("#E53935"))

        // 4. KELOMPOKKAN BERDASARKAN TANGGAL (Grouping)
        val groupedMap = filteredList.sortedByDescending { it.timestamp }.groupBy { sdfDateOnly.format(Date(it.timestamp)) }
        val displayList = mutableListOf<Any>()

        for ((dateStr, txList) in groupedMap) {
            var dailyTotal = 0.0
            txList.forEach { tx ->
                if (tx.type == "INCOME" || tx.type == "DEBT") dailyTotal += tx.amount else dailyTotal -= tx.amount
            }
            displayList.add(DateHeader(txList.first().timestamp, dailyTotal))
            displayList.addAll(txList)
        }

        binding.rvTransactions.adapter = GroupedHistoryAdapter(displayList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ==============================================================================
    // ADAPTER MULTI-TIPE (Tetap Sama Mewahnya)
    // ==============================================================================
    
    data class DateHeader(val timestamp: Long, val dailyTotal: Double)

    inner class GroupedHistoryAdapter(private val items: List<Any>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_HEADER = 0
        private val TYPE_TRANSACTION = 1

        override fun getItemViewType(position: Int): Int {
            return if (items[position] is DateHeader) TYPE_HEADER else TYPE_TRANSACTION
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                val view = inflater.inflate(R.layout.item_history_header, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = inflater.inflate(R.layout.item_transaction, parent, false)
                TransactionViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (holder is HeaderViewHolder && item is DateHeader) {
                val date = Date(item.timestamp)
                holder.tvNumber.text = sdfDayNum.format(date)
                
                val cal = Calendar.getInstance()
                val todayStr = sdfDateOnly.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val yesterdayStr = sdfDateOnly.format(cal.time)
                val itemDateStr = sdfDateOnly.format(date)

                holder.tvDayName.text = when (itemDateStr) {
                    todayStr -> "Hari ini"
                    yesterdayStr -> "Kemarin"
                    else -> SimpleDateFormat("EEEE", Locale("id", "ID")).format(date)
                }
                
                holder.tvMonth.text = sdfMonthYear.format(date)
                
                val prefix = if (item.dailyTotal >= 0) "+" else "-"
                holder.tvDailyTotal.text = "$prefix${formatRupiah.format(Math.abs(item.dailyTotal))}"
                holder.tvDailyTotal.setTextColor(if (item.dailyTotal >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#E53935"))
                
            } else if (holder is TransactionViewHolder && item is Transaction) {
                val isInc = item.type == "INCOME" || item.type == "DEBT"
                holder.tvCategory.text = item.categoryName
                holder.tvNote.text = item.note.ifEmpty { "Tanpa catatan" }
                
                val prefix = if (isInc) "+" else "-"
                holder.tvAmount.text = "$prefix${formatRupiah.format(item.amount)}"
                holder.tvAmount.setTextColor(if (isInc) Color.parseColor("#4CAF50") else Color.parseColor("#E53935"))
                holder.tvIcon.text = if (isInc) "📥" else "💸"
                
                holder.itemView.setOnClickListener {
                    TransactionEditorDialog(
                        hashMapOf("id" to item.id, "amount" to item.amount, "note" to item.note, "type" to item.type, "timestamp" to item.timestamp, "categoryId" to item.categoryId, "debtId" to (item.debtId ?: ""))
                    ) { /* OnUpdate */ }.show(parentFragmentManager, "EditTx")
                }
            }
        }

        override fun getItemCount() = items.size

        inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNumber: TextView = view.findViewById(R.id.tvHeaderDateNumber)
            val tvDayName: TextView = view.findViewById(R.id.tvHeaderDayName)
            val tvMonth: TextView = view.findViewById(R.id.tvHeaderMonthYear)
            val tvDailyTotal: TextView = view.findViewById(R.id.tvHeaderDailyTotal)
        }

        inner class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvCategory: TextView = view.findViewById(R.id.tvItemCategory)
            val tvNote: TextView = view.findViewById(R.id.tvItemNote)
            val tvAmount: TextView = view.findViewById(R.id.tvItemAmount)
            val tvIcon: TextView = view.findViewById(R.id.tvItemIcon)
        }
    }
}
