package com.smartfinance.tracker.ui.dashboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.MainActivity
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.FragmentDashboardBinding
import com.smartfinance.tracker.ui.report.DetailCategoryReportFragment
import com.smartfinance.tracker.ui.report.ReportFragment
import com.smartfinance.tracker.ui.transaction.HistoryTransactionFragment
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import com.smartfinance.tracker.utils.FirebaseManager

class DashboardFragment : Fragment() {

    // Menggunakan ViewBinding untuk menghubungkan ke fragment_dashboard.xml
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseManager.getFirestore()
    private var transactionsListenerRegistration: ListenerRegistration? = null

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private var selectedTopFilter = "BULAN INI"
    
    private val sdfPremiumDateTime = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup aksi klik navigasi (Menggantikan fungsi createHeaderSectionRow lama)
        binding.btnDetailLaporan.setOnClickListener {
            (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment())
        }
        
        binding.btnLihatAnalisis.setOnClickListener {
            (activity as? MainActivity)?.navigateToSpecificFragment(DetailCategoryReportFragment())
        }
        
        binding.btnLihatSemua.setOnClickListener {
            (activity as? MainActivity)?.navigateToSpecificFragment(HistoryTransactionFragment(), R.id.menu_report)
        }

        binding.btnTabWeek.setOnClickListener { switchTopFilter("PERMINGGU") }
        binding.btnTabMonth.setOnClickListener { switchTopFilter("BULAN INI") }

        // Memicu default tab bulan aktif
        switchTopFilter("BULAN INI")
    }

    private fun switchTopFilter(filter: String) {
        val density = requireContext().resources.displayMetrics.density
        selectedTopFilter = filter
        
        if (filter == "PERMINGGU") {
            binding.btnTabWeek.apply {
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                background = android.graphics.drawable.GradientDrawable().apply { 
                    cornerRadius = 8 * density 
                    setColor(Color.parseColor("#1E293B")) 
                }
            }
            binding.btnTabMonth.apply {
                setTextColor(Color.parseColor("#64748B"))
                setTypeface(null, Typeface.NORMAL)
                background = null
            }
        } else {
            binding.btnTabMonth.apply {
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                background = android.graphics.drawable.GradientDrawable().apply { 
                    cornerRadius = 8 * density 
                    setColor(Color.parseColor("#1E293B")) 
                }
            }
            binding.btnTabWeek.apply {
                setTextColor(Color.parseColor("#64748B"))
                setTypeface(null, Typeface.NORMAL)
                background = null
            }
        }
        observeCloudTransactionsLive()
    }

    private fun observeCloudTransactionsLive() {
        if (!isAdded) return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        transactionsListenerRegistration?.remove()
        transactionsListenerRegistration = firestore.collection("transactions")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                val activeTimePrefs = prefs.getLong("active_report_time", System.currentTimeMillis())

                val calToday = Calendar.getInstance().apply { timeInMillis = activeTimePrefs }
                val calLastMonth = Calendar.getInstance().apply { timeInMillis = activeTimePrefs; add(Calendar.MONTH, -1) }

                var balanceTotal = 0.0
                var incomeThisMonth = 0.0
                var expenseThisMonth = 0.0
                var incomeLastMonth = 0.0
                var expenseLastMonth = 0.0

                val allTxList = ArrayList<HashMap<String, Any>>()

                // LOGIKA MENGHITUNG SALDO - DIBIARKAN UTUH
                for (doc in snapshots.documents) {
                    val data = doc.data ?: continue
                    val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                    val typeRaw = (data["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT)
                    val timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val categoryName = data["categoryName"] as? String ?: "Umum"
                    val note = data["note"] as? String ?: "Transaksi AI"

                    if (typeRaw == "INCOME" || typeRaw == "DEBT") {
                        balanceTotal += amount
                    } else if (typeRaw == "EXPENSE" || typeRaw == "RECEIVABLE") {
                        balanceTotal -= amount
                    }

                    val txCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val isThisMonth = txCal.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    val isLastMonth = txCal.get(Calendar.MONTH) == calLastMonth.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calLastMonth.get(Calendar.YEAR)

                    if (isThisMonth) {
                        if (typeRaw == "INCOME" || typeRaw == "DEBT") incomeThisMonth += amount
                        if (typeRaw == "EXPENSE" || typeRaw == "RECEIVABLE") expenseThisMonth += amount
                    }
                    if (isLastMonth) {
                        if (typeRaw == "INCOME" || typeRaw == "DEBT") incomeLastMonth += amount
                        if (typeRaw == "EXPENSE" || typeRaw == "RECEIVABLE") expenseLastMonth += amount
                    }

                    val itemMap = HashMap<String, Any>().apply {
                        put("amount", amount)
                        put("type", typeRaw)
                        put("timestamp", timestamp)
                        put("categoryName", categoryName)
                        put("note", note)
                    }
                    allTxList.add(itemMap)
                }
                
                // MENGUPDATE UI TEXT MENGGUNAKAN BINDING
                binding.tvTotalBalance.text = formatRupiah.format(balanceTotal)
                binding.tvExpenseSummary.text = formatRupiah.format(expenseThisMonth)
                binding.tvIncomeSummary.text = formatRupiah.format(incomeThisMonth)

                // Render Chart (Dibungkus LinearLayout vertikal agar susunannya tetap rapi)
                binding.chartContainer.removeAllViews()
                val chartVerticalLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                }
                
                val barView = com.smartfinance.tracker.ui.report.QuadVerticalBarChartView(
                    context, incomeLastMonth.toFloat(), incomeThisMonth.toFloat(), expenseLastMonth.toFloat(), expenseThisMonth.toFloat()
                )
                chartVerticalLayout.addView(barView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * density).toInt()))

                val summaryLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, (14 * density).toInt(), 0, 0)
                }

                val incDiffPercent = if (incomeLastMonth > 0) ((incomeThisMonth - incomeLastMonth) / incomeLastMonth * 100).toInt() else 0
                val expDiffPercent = if (expenseLastMonth > 0) ((expenseThisMonth - expenseLastMonth) / expenseLastMonth * 100).toInt() else 0

                val sdfMonthLabel = SimpleDateFormat("MMMM", Locale("id", "ID"))
                val labelBulanIni = sdfMonthLabel.format(calToday.time)

                summaryLayout.addView(TextView(context).apply {
                    text = "🔹 Ringkasan Kas Bulan $labelBulanIni Terpusat"
                    textSize = 11.5f; setTextColor(Color.parseColor("#64748B")); setPadding(0, 0, 0, (2 * density).toInt())
                })

                summaryLayout.addView(TextView(context).apply {
                    val incText = if (incDiffPercent >= 0) " naik $incDiffPercent%" else " turun ${Math.abs(incDiffPercent)}%"
                    val expText = if (expDiffPercent >= 0) " naik $expDiffPercent%" else " turun ${Math.abs(expDiffPercent)}%"
                    text = "📈 Performa: Pemasukan$incText • Pengeluaran$expText (vs Bulan Lalu)"
                    textSize = 12f; setTextColor(Color.parseColor("#0D9488")); setTypeface(null, Typeface.BOLD)
                })
                chartVerticalLayout.addView(summaryLayout)
                binding.chartContainer.addView(chartVerticalLayout)

                // 🔥 LOGIKA TOP EXPENSE DIKEMBALIKAN UTUH!
                binding.topExpenseContainer.removeAllViews()
                val nowTime = System.currentTimeMillis()
                val filteredExpenses = allTxList.filter { item -> 
                    val typeUpper = (item["type"] as? String) ?: "EXPENSE"
                    typeUpper == "EXPENSE" || typeUpper == "RECEIVABLE" 
                }.filter { item ->
                    val timestamp = (item["timestamp"] as? Long) ?: nowTime
                    if (selectedTopFilter == "PERMINGGU") {
                        (nowTime - timestamp) <= (7L * 24 * 60 * 60 * 1000)
                    } else {
                        val t = Calendar.getInstance().apply { timeInMillis = timestamp }
                        t.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && t.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    }
                }
                
                val totalFilteredExpenseAmount = filteredExpenses.sumOf { (it["amount"] as? Double) ?: 0.0 }
                val aggregatedExpenses = filteredExpenses.groupBy { (it["categoryName"] as? String) ?: "Umum" }
                    .mapValues { entry -> entry.value.sumOf { (it["amount"] as? Double) ?: 0.0 } }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(3)

                if (aggregatedExpenses.isEmpty()) {
                    for (i in 1..3) {
                        binding.topExpenseContainer.addView(createPlaceholderRow("Kategori Kosong ${i}", "Belum ada alokasi dana."))
                    }
                } else {
                    aggregatedExpenses.forEach { (categoryName, totalAmount) ->
                        val percentage = if (totalFilteredExpenseAmount > 0) {
                            ((totalAmount / totalFilteredExpenseAmount) * 100).toInt()
                        } else 0
                        val rowCard = MaterialCardView(context).apply {
                            radius = 12 * density; cardElevation = 1 * density; strokeWidth = 0
                            setCardBackgroundColor(Color.parseColor("#F8FAFC"))
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * density).toInt() }
                        }
                        val rowLayout = LinearLayout(context).apply { 
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                        }
                        val iconCircle = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.WHITE) }
                            addView(TextView(context).apply { text = "💰"; textSize = 14f; gravity = Gravity.CENTER })
                        }
                        rowLayout.addView(iconCircle)
                        val centerInfo = LinearLayout(context).apply { 
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        centerInfo.addView(TextView(context).apply { text = categoryName; setTextColor(Color.parseColor("#1E293B")); setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); textSize = 14f })
                        centerInfo.addView(TextView(context).apply { text = formatRupiah.format(totalAmount); setTextColor(Color.parseColor("#64748B")); textSize = 11.5f; setPadding(0, 2, 0, 0) })
                        rowLayout.addView(centerInfo)
                        rowLayout.addView(TextView(context).apply { 
                            text = "$percentage%"
                            setTextColor(Color.parseColor("#F43F5E"))
                            setTypeface(null, Typeface.BOLD)
                            textSize = 14f
                        })
                        rowCard.addView(rowLayout)
                        binding.topExpenseContainer.addView(rowCard)
                    }
                }

                // 🔥 LOGIKA TRANSAKSI TERAKHIR DIKEMBALIKAN UTUH!
                binding.recentTxContainer.removeAllViews()
                val recentTxList = allTxList.sortedByDescending { (it["timestamp"] as? Long) ?: 0L }.take(4)

                if (recentTxList.isEmpty()) {
                    for (i in 1..3) {
                        binding.recentTxContainer.addView(createPlaceholderRow("Mutasi Kosong ${i}", "Menunggu transaksi dicatat."))
                    }
                } else {
                    recentTxList.forEach { item ->
                        val note = (item["note"] as? String) ?: "Transaksi AI"
                        val timestamp = (item["timestamp"] as? Long) ?: nowTime
                        val amount = (item["amount"] as? Double) ?: 0.0
                        val currentTypeUpper = ((item["type"] as? String) ?: "EXPENSE").trim().uppercase(Locale.ROOT)
                        val mutasiCard = MaterialCardView(context).apply {
                            radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
                            setCardBackgroundColor(Color.WHITE)
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (10 * density).toInt() }
                        }
                        val rowLayout = LinearLayout(context).apply { 
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
                        }
                        val isInc = currentTypeUpper == "INCOME" || currentTypeUpper == "DEBT"
                        val iconCircle = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#F1F5F9")) }
                            addView(TextView(context).apply { 
                                text = if (isInc) "📥" else "💸"
                                textSize = 15f
                                gravity = Gravity.CENTER 
                            })
                        }
                        rowLayout.addView(iconCircle)
                        val centerInfo = LinearLayout(context).apply { 
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        centerInfo.addView(TextView(context).apply { text = note; setTextColor(Color.parseColor("#1E293B")); setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); textSize = 14.5f })
                        centerInfo.addView(TextView(context).apply { text = sdfPremiumDateTime.format(Date(timestamp)); setTextColor(Color.parseColor("#94A3B8")); textSize = 11.5f; setPadding(0, 2, 0, 0) })
                        rowLayout.addView(centerInfo)
                        val colorHex = if (isInc) "#0284C7" else "#F43F5E"
                        rowLayout.addView(TextView(context).apply { 
                            text = (if (isInc) "+" else "-") + formatRupiah.format(amount)
                            setTextColor(Color.parseColor(colorHex))
                            setTypeface(null, Typeface.BOLD)
                            textSize = 14.5f
                        })
                        mutasiCard.addView(rowLayout)
                        binding.recentTxContainer.addView(mutasiCard)
                    }
                }
            }
    }

    private fun createPlaceholderRow(mainTitle: String, subTitle: String): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density
        val layout = LinearLayout(context).apply { 
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
            alpha = 0.5f
        }
        val centerInfo = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        centerInfo.addView(TextView(context).apply { text = mainTitle; textSize = 14f; setTextColor(Color.parseColor("#94A3B8")); setTypeface(null, Typeface.ITALIC) })
        centerInfo.addView(TextView(context).apply { text = subTitle; textSize = 11f; setTextColor(Color.parseColor("#CBD5E0")) })
        layout.addView(centerInfo)
        layout.addView(TextView(context).apply { text = "Rp 0"; setTextColor(Color.parseColor("#CBD5E0")); textSize = 14f })
        return layout
    }

    override fun onDestroyView() {
        super.onDestroyView()
        transactionsListenerRegistration?.remove()
        _binding = null // Mencegah memory leak ViewBinding
    }
}
