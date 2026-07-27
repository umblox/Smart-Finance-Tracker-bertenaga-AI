package com.smartfinance.tracker.ui.transaction

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
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
        
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext()).apply {
            // Memberi jarak atas agar blok pertama tidak menempel ke header
            binding.rvTransactions.setPadding(0, (16f * resources.displayMetrics.density).toInt(), 0, 0)
        }

        updateMonthLabel()
        binding.btnPrevMonth.setOnClickListener { changeMonth(-1) }
        binding.btnNextMonth.setOnClickListener { changeMonth(1) }

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

        var filteredList = allTransactions.filter { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            cal.get(Calendar.MONTH) == targetMonth && cal.get(Calendar.YEAR) == targetYear
        }

        if (searchQuery.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.note.lowercase(Locale.ROOT).contains(searchQuery) ||
                it.categoryName.lowercase(Locale.ROOT).contains(searchQuery) ||
                it.amount.toString().contains(searchQuery)
            }
        }

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
        binding.tvTotalBalance.setTextColor(ContextCompat.getColor(requireContext(), if (netBalance >= 0) R.color.text_primary else R.color.expense_red))

        // 🔥 LOGIKA BARU: Data dibungkus ke dalam model "DailyBlock"
        val groupedMap = filteredList.sortedByDescending { it.timestamp }.groupBy { sdfDateOnly.format(Date(it.timestamp)) }
        val displayBlocks = mutableListOf<DailyBlock>()

        for ((_, txList) in groupedMap) {
            var dailyTotal = 0.0
            txList.forEach { tx ->
                if (tx.type == "INCOME" || tx.type == "DEBT") dailyTotal += tx.amount else dailyTotal -= tx.amount
            }
            displayBlocks.add(DailyBlock(txList.first().timestamp, dailyTotal, txList))
        }

        binding.rvTransactions.adapter = BlockHistoryAdapter(displayBlocks)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ==============================================================================
    // ADAPTER BARU: SATU CARDVIEW = SATU HARI
    // ==============================================================================
    
    data class DailyBlock(val timestamp: Long, val dailyTotal: Double, val transactions: List<Transaction>)

    inner class BlockHistoryAdapter(private val blocks: List<DailyBlock>) : RecyclerView.Adapter<BlockHistoryAdapter.BlockViewHolder>() {

        inner class BlockViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNumber: TextView = view.findViewById(R.id.tvHeaderDateNumber)
            val tvDayName: TextView = view.findViewById(R.id.tvHeaderDayName)
            val tvMonth: TextView = view.findViewById(R.id.tvHeaderMonthYear)
            val tvDailyTotal: TextView = view.findViewById(R.id.tvHeaderDailyTotal)
            val container: LinearLayout = view.findViewById(R.id.containerTransactions)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_block, parent, false)
            return BlockViewHolder(view)
        }

        override fun onBindViewHolder(holder: BlockViewHolder, position: Int) {
            val block = blocks[position]
            val date = Date(block.timestamp)
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
            
            val prefix = if (block.dailyTotal >= 0) "+" else "-"
            holder.tvDailyTotal.text = "$prefix${formatRupiah.format(Math.abs(block.dailyTotal))}"
            holder.tvDailyTotal.setTextColor(ContextCompat.getColor(requireContext(), if (block.dailyTotal >= 0) R.color.income_green else R.color.expense_red))
            
            // 🔥 LOOPING TRANSAKSI KE DALAM CONTAINER CARDVIEW
            holder.container.removeAllViews()
            val inflater = LayoutInflater.from(holder.itemView.context)
            
            for ((index, tx) in block.transactions.withIndex()) {
                val txView = inflater.inflate(R.layout.item_transaction, holder.container, false)
                
                val tvCategory: TextView = txView.findViewById(R.id.tvItemCategory)
                val tvNote: TextView = txView.findViewById(R.id.tvItemNote)
                val tvAmount: TextView = txView.findViewById(R.id.tvItemAmount)
                val tvIcon: TextView = txView.findViewById(R.id.tvItemIcon)
                
                val isInc = tx.type == "INCOME" || tx.type == "DEBT"
                tvCategory.text = tx.categoryName
                tvNote.text = tx.note.ifEmpty { "Tanpa catatan" }
                
                val amtPrefix = if (isInc) "+" else "-"
                tvAmount.text = "$amtPrefix${formatRupiah.format(tx.amount)}"
                tvAmount.setTextColor(ContextCompat.getColor(requireContext(), if (isInc) R.color.income_green else R.color.expense_red))
                tvIcon.text = if (isInc) "📥" else "💸"
                
                txView.setOnClickListener {
                    TransactionEditorDialog(
                        hashMapOf("id" to tx.id, "amount" to tx.amount, "note" to tx.note, "type" to tx.type, "timestamp" to tx.timestamp, "categoryId" to tx.categoryId, "debtId" to (tx.debtId ?: ""))
                    ) { /* OnUpdate */ }.show(parentFragmentManager, "EditTx")
                }
                
                holder.container.addView(txView)
                
                // Tambahkan Garis Pembatas (Divider) jika bukan item terakhir
                if (index < block.transactions.size - 1) {
                    val divider = View(holder.itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                            // Menjorok ke dalam agar sejajar dengan teks
                            val marginStart = (70f * resources.displayMetrics.density).toInt()
                            setMargins(marginStart, 0, 0, 0)
                        }
                        setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider_color))
                    }
                    holder.container.addView(divider)
                }
            }
        }

        override fun getItemCount() = blocks.size
    }
}
