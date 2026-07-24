package com.smartfinance.tracker.ui.transaction

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.databinding.FragmentHistoryTransactionBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class HistoryTransactionFragment : Fragment() {

    private var _binding: FragmentHistoryTransactionBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TransactionViewModel
    
    private var currentCalendar = Calendar.getInstance()
    private var activeTab = "EXPENSE"
    private var searchQuery = ""

    private val sdfMonthLabel = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
    private val sdfDate = SimpleDateFormat("dd MMM • HH:mm", Locale("id", "ID"))
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[TransactionViewModel::class.java]

        binding.btnPrevMonth.setOnClickListener { changeMonth(-1) }
        binding.btnNextMonth.setOnClickListener { changeMonth(1) }

        binding.btnTabExpense.setOnClickListener { setTab("EXPENSE") }
        binding.btnTabIncome.setOnClickListener { setTab("INCOME") }

        // Filter Pencarian Real-time
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString().lowercase(Locale.ROOT)
                renderList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Default Setup
        updateMonthLabel()
        setTab("EXPENSE")

        // Observe Data dari ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.transactions.collect {
                renderList()
            }
        }
    }

    private fun changeMonth(amount: Int) {
        currentCalendar.add(Calendar.MONTH, amount)
        updateMonthLabel()
        renderList()
    }

    private fun updateMonthLabel() {
        binding.tvMonthLabel.text = sdfMonthLabel.format(currentCalendar.time).uppercase(Locale.ROOT)
    }

    private fun setTab(tab: String) {
        activeTab = tab
        val density = requireContext().resources.displayMetrics.density

        if (tab == "EXPENSE") {
            binding.btnTabExpense.apply { setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 10 * density; setColor(Color.parseColor("#1E293B")) } }
            binding.btnTabIncome.apply { setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.NORMAL); background = null }
        } else {
            binding.btnTabIncome.apply { setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 10 * density; setColor(Color.parseColor("#1E293B")) } }
            binding.btnTabExpense.apply { setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.NORMAL); background = null }
        }
        renderList()
    }

    private fun renderList() {
        if (_binding == null) return
        val allTx = viewModel.transactions.value
        val density = requireContext().resources.displayMetrics.density

        binding.listContainer.removeAllViews()

        val targetMonth = currentCalendar.get(Calendar.MONTH)
        val targetYear = currentCalendar.get(Calendar.YEAR)

        // 1. Filter by Tab & Month
        var filteredList = allTx.filter { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            tx.type == activeTab && cal.get(Calendar.MONTH) == targetMonth && cal.get(Calendar.YEAR) == targetYear
        }

        // 2. Filter by Search Query
        if (searchQuery.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.note.lowercase(Locale.ROOT).contains(searchQuery) ||
                it.categoryName.lowercase(Locale.ROOT).contains(searchQuery) ||
                it.amount.toString().contains(searchQuery)
            }
        }

        // 3. Urutkan terbaru di atas
        filteredList = filteredList.sortedByDescending { it.timestamp }

        if (filteredList.isEmpty()) {
            val emptyMsg = if (searchQuery.isNotEmpty()) "Pencarian '$searchQuery' tidak ditemukan." else "Belum ada riwayat transaksi di bulan ini."
            binding.listContainer.addView(TextView(requireContext()).apply {
                text = "\n$emptyMsg"
                textSize = 13.5f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
                setTypeface(null, Typeface.ITALIC)
            })
            return
        }

        var totalAmount = 0.0

        filteredList.forEach { tx ->
            totalAmount += tx.amount

            val itemCard = MaterialCardView(requireContext()).apply {
                radius = 12f * density; cardElevation = 1f * density; strokeWidth = 0
                setCardBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (10f * density).toInt() }
                
                setOnClickListener {
                    val passMap = HashMap<String, Any>().apply {
                        put("id", tx.id)
                        put("amount", tx.amount)
                        put("type", tx.type)
                        put("timestamp", tx.timestamp)
                        put("categoryId", tx.categoryId)
                        put("categoryName", tx.categoryName)
                        put("note", tx.note)
                        put("debtId", tx.debtId)
                    }
                    TransactionEditorDialog(passMap) {
                        // ViewModel auto-updates list
                    }.show(parentFragmentManager, "TransactionEditorDialog")
                }
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((16f * density).toInt(), (14f * density).toInt(), (16f * density).toInt(), (14f * density).toInt())
            }

            // Kiri: Kategori & Catatan
            val leftLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            leftLayout.addView(TextView(requireContext()).apply { text = tx.categoryName; textSize = 12f; setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.BOLD) })
            leftLayout.addView(TextView(requireContext()).apply { text = tx.note; textSize = 15f; setTextColor(Color.parseColor("#1E293B")); setPadding(0, (2f*density).toInt(), 0, 0) })
            leftLayout.addView(TextView(requireContext()).apply { text = sdfDate.format(Date(tx.timestamp)); textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); setPadding(0, (4f*density).toInt(), 0, 0) })
            row.addView(leftLayout)

            // Kanan: Nominal
            val amountColor = if (tx.type == "EXPENSE") Color.parseColor("#EF4444") else Color.parseColor("#10B981")
            val amountPrefix = if (tx.type == "EXPENSE") "-" else "+"
            row.addView(TextView(requireContext()).apply {
                text = "$amountPrefix ${formatRupiah.format(tx.amount)}"
                textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(amountColor)
            })

            itemCard.addView(row)
            binding.listContainer.addView(itemCard)
        }

        // Tampilkan Summary Total di atas List
        val summaryText = TextView(requireContext()).apply {
            text = "Total ${if (activeTab == "EXPENSE") "Pengeluaran" else "Pemasukan"}: ${formatRupiah.format(totalAmount)}"
            textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#1E293B"))
            setPadding((4f*density).toInt(), (8f*density).toInt(), 0, (8f*density).toInt())
        }
        binding.listContainer.addView(summaryText, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
