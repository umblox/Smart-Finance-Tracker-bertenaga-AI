package com.smartfinance.tracker.ui.report

import androidpackage com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import         android.view.ViewGroup
import android.widget.*
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.R
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GroqClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ReportFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var reportListenerRegistration: ListenerRegistration? = null

    private lateinit var chartContainer: LinearLayout
    private lateinit var tvReportIncome: TextView
    private lateinit var tvReportExpense: TextView
    private lateinit var tvReportNet: TextView
    
    private lateinit var topBorosContainer: LinearLayout
    private lateinit var tvAiRecommendation: TextView
    private lateinit var btnTriggerAi: Button
    private lateinit var pbAiLoading: ProgressBar

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfPremiumDateTime = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val root = RelativeLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#F8FAFC"))
        }

        val nsv = NestedScrollView(context).apply {
            isFillViewport = true
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        mainLayout.addView(TextView(context).apply {
            text = "Analisis Laporan & Rekomendasi AI"
            textSize = 20f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, (8 * density).toInt(), 0, (16 * density).toInt())
        })

        val cardSummary = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 2 * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        val summaryInside = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        tvReportIncome = TextView(context).apply { text = "Pemasukan: Rp 0"; setTextColor(Color.parseColor("#10B981")); textSize = 14.5f; setPadding(0, 0, 0, (4 * density).toInt()); setTypeface(null, Typeface.BOLD) }
        tvReportExpense = TextView(context).apply { text = "Pengeluaran: Rp 0"; setTextColor(Color.parseColor("#F43F5E")); textSize = 14.5f; setPadding(0, 0, 0, (8 * density).toInt()); setTypeface(null, Typeface.BOLD) }
        tvReportNet = TextView(context).apply { text = "Selisih Bersih: Rp 0"; setTextColor(Color.parseColor("#1E293B")); textSize = 16f; setTypeface(null, Typeface.BOLD) }
        summaryInside.addView(tvReportIncome)
        summaryInside.addView(tvReportExpense)
        summaryInside.addView(View(context).apply { setBackgroundColor(Color.parseColor("#E2E8F0")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { bottomMargin = (8 * density).toInt() } })
        summaryInside.addView(tvReportNet)
        cardSummary.addView(summaryInside)
        mainLayout.addView(cardSummary)

        mainLayout.addView(TextView(context).apply { text = "Grafik Komparasi Arus Kas"; textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#64748B")); setPadding((4 * density).toInt(), 0, 0, (8 * density).toInt()) })
        val cardChart = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        chartContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        cardChart.addView(chartContainer)
        mainLayout.addView(cardChart)

        mainLayout.addView(TextView(context).apply { text = "Alokasi Kategori Terboros Bulan Ini"; textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#64748B")); setPadding((4 * density).toInt(), 0, 0, (8 * density).toInt()) })
        topBorosContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        mainLayout.addView(topBorosContainer)

        mainLayout.addView(TextView(context).apply { text = "Asisten Rekomendasi Finansial AI"; textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#64748B")); setPadding((4 * density).toInt(), 0, 0, (8 * density).toInt()) })
        val cardAi = MaterialCardView(context).apply {
            radius = 16 * density; cardElevation = 2 * density; strokeWidth = 0
            setCardBackgroundColor(Color.parseColor("#0F172A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (24 * density).toInt() }
        }
        val aiInside = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        tvAiRecommendation = TextView(context).apply {
            text = "Klik tombol di bawah ini untuk mengizinkan AI menganalisis riwayat transaksi Cloud Anda dan memberikan rekomendasi penghematan finansial terpusat."
            setTextColor(Color.parseColor("#94A3B8")); textSize = 13f; setLineSpacing(3f, 1.1f)
        }
        aiInside.addView(tvAiRecommendation)

        pbAiLoading = ProgressBar(context, null, android.R.style.Widget_Material_ProgressBar_Small).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                gravity = Gravity.CENTER; topMargin = (12 * density).toInt(); bottomMargin = (12 * density).toInt() 
            }
        }
        aiInside.addView(pbAiLoading)

        btnTriggerAi = Button(context).apply {
            text = "✨ ANALISIS KEUANGAN SAYA"
            textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(Color.parseColor("#0D9488"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (42 * density).toInt()).apply { topMargin = (14 * density).toInt() }
            setOnClickListener { triggerAiFinancialReview() }
        }
        aiInside.addView(btnTriggerAi)
        cardAi.addView(aiInside)
        mainLayout.addView(cardAi)

        nsv.addView(mainLayout)
        root.addView(nsv)

        observeCloudReportLive()
        return root
    }

    private fun observeCloudReportLive() {
        if (!isAdded) return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        reportListenerRegistration?.remove()
        reportListenerRegistration = firestore.collection("transactions")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                val calToday = Calendar.getInstance()
                val calLastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }

                var incomeThisMonth = 0.0
                var expenseThisMonth = 0.0
                var incomeLastMonth = 0.0
                var expenseLastMonth = 0.0

                val currentMonthExpenses = ArrayList<HashMap<String, Any>>()

                for (doc in snapshots.documents) {
                    val data = doc.data ?: continue
                    val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                    val typeRaw = (data["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT)
                    val timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val categoryName = data["categoryName"] as? String ?: "Umum"

                    val txCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val isThisMonth = txCal.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    val isLastMonth = txCal.get(Calendar.MONTH) == calLastMonth.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calLastMonth.get(Calendar.YEAR)

                    if (isThisMonth) {
                        if (typeRaw == "INCOME" || typeRaw == "DEBT") incomeThisMonth += amount
                        if (typeRaw == "EXPENSE" || typeRaw == "RECEIVABLE") {
                            expenseThisMonth += amount
                            val itemMap = HashMap<String, Any>().apply {
                                put("amount", amount)
                                put("categoryName", categoryName)
                            }
                            currentMonthExpenses.add(itemMap)
                        }
                    }
                    if (isLastMonth) {
                        if (typeRaw == "INCOME" || typeRaw == "DEBT") incomeLastMonth += amount
                        if (typeRaw == "EXPENSE" || typeRaw == "RECEIVABLE") expenseLastMonth += amount
                    }
                }

                tvReportIncome.text = "🟢 Total Pemasukan Bulan Ini: ${formatRupiah.format(incomeThisMonth)}"
                tvReportExpense.text = "🔴 Total Pengeluaran Bulan Ini: ${formatRupiah.format(expenseThisMonth)}"
                tvReportNet.text = "💰 Surplus Bersih Bulan Ini: ${formatRupiah.format(incomeThisMonth - expenseThisMonth)}"

                // ✅ FIX SINKRONISASI: Memanggil QuadVerticalBarChartView mandiri secara langsung dan bersih!
                chartContainer.removeAllViews()
                val barChartView = QuadVerticalBarChartView(context, incomeLastMonth.toFloat(), incomeThisMonth.toFloat(), expenseLastMonth.toFloat(), expenseThisMonth.toFloat())
                chartContainer.addView(barChartView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (140 * density).toInt()))

                topBorosContainer.removeAllViews()
                val groupedExpenses = currentMonthExpenses.groupBy { it["categoryName"] as String }
                    .mapValues { entry -> entry.value.sumOf { (it["amount"] as Double) } }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(3)

                if (groupedExpenses.isEmpty()) {
                    topBorosContainer.addView(TextView(context).apply {
                        text = "Belum ada pengeluaran terdeteksi bulan ini."; textSize = 13f
                        setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
                        setPadding(0, (20 * density).toInt(), 0, (20 * density).toInt())
                    })
                } else {
                    groupedExpenses.forEach { (categoryName, totalAmount) ->
                        val pct = if (expenseThisMonth > 0) ((totalAmount / expenseThisMonth) * 100).toInt() else 0

                        val rowCard = MaterialCardView(context).apply {
                            radius = 12 * density; strokeWidth = 0; setCardBackgroundColor(Color.WHITE)
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * density).toInt() }
                        }
                        val rowLayout = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                        }
                        rowLayout.addView(TextView(context).apply { text = "🔥"; textSize = 15f; setPadding(0, 0, (10 * density).toInt(), 0) })
                        
                        val txtLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                        txtLayout.addView(TextView(context).apply { text = categoryName; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD); textSize = 13.5f })
                        txtLayout.addView(TextView(context).apply { text = formatRupiah.format(totalAmount); setTextColor(Color.parseColor("#64748B")); textSize = 11.5f; setPadding(0, 2, 0, 0) })
                        rowLayout.addView(txtLayout)
                        
                        rowLayout.addView(TextView(context).apply { text = "$pct%"; setTextColor(Color.parseColor("#F43F5E")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                        rowCard.addView(rowLayout)
                        topBorosContainer.addView(rowCard)
                    }
                }
            }
    }

    private fun triggerAiFinancialReview() {
        val context = context ?: return
        btnTriggerAi.visibility = View.GONE
        pbAiLoading.visibility = View.VISIBLE
        tvAiRecommendation.text = "Menghubungi server Groq Cloud Premium..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val assistant = FinancialAssistant(context)
                val groqClient = GroqClient(context, assistant)
                val aiPromptRequest = "Tolong berikan analisis evaluasi penghematan keuangan singkat terperinci untuk akun saya, sebutkan rumpun kategori yang paling boros bulan ini dan berikan solusinya secara tajam"
                
                val response = withContext(Dispatchers.IO) {
                    groqClient.sendMessageToAI(aiPromptRequest)
                }
                tvAiRecommendation.text = response
            } catch (e: Exception) {
                tvAiRecommendation.text = "⚠️ Gagal memuat rekomendasi AI: ${e.localizedMessage ?: "Timeout"}"
            } finally {
                pbAiLoading.visibility = View.GONE
                btnTriggerAi.visibility = View.VISIBLE
                btnTriggerAi.text = "🔄 RE-ANALISIS KEUANGAN"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reportListenerRegistration?.remove()
    }
}
.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.R
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GroqClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ReportFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var reportListenerRegistration: ListenerRegistration? = null

    private lateinit var chartContainer: LinearLayout
    private lateinit var tvReportIncome: TextView
    private lateinit var tvReportExpense: TextView
    private lateinit var tvReportNet: TextView
    
    // Komponen Tambahan Baru untuk Statistik & AI
    private lateinit var topBorosContainer: LinearLayout
    private lateinit var tvAiRecommendation: TextView
    private lateinit var btnTriggerAi: Button
    private lateinit var pbAiLoading: ProgressBar

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfPremiumDateTime = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val root = RelativeLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#F8FAFC"))
        }

        val nsv = NestedScrollView(context).apply {
            isFillViewport = true
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        mainLayout.addView(TextView(context).apply {
            text = "Analisis Laporan & Rekomendasi AI"
            textSize = 20f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, (8 * density).toInt(), 0, (16 * density).toInt())
        })

        // 1. RINGKASAN SALDO CARD
        val cardSummary = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 2 * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        val summaryInside = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        tvReportIncome = TextView(context).apply { text = "Pemasukan: Rp 0"; setTextColor(Color.parseColor("#10B981")); textSize = 14.5f; setPadding(0, 0, 0, (4 * density).toInt()); setTypeface(null, Typeface.BOLD) }
        tvReportExpense = TextView(context).apply { text = "Pengeluaran: Rp 0"; setTextColor(Color.parseColor("#F43F5E")); textSize = 14.5f; setPadding(0, 0, 0, (8 * density).toInt()); setTypeface(null, Typeface.BOLD) }
        tvReportNet = TextView(context).apply { text = "Selisih Bersih: Rp 0"; setTextColor(Color.parseColor("#1E293B")); textSize = 16f; setTypeface(null, Typeface.BOLD) }
        summaryInside.addView(tvReportIncome)
        summaryInside.addView(tvReportExpense)
        summaryInside.addView(View(context).apply { setBackgroundColor(Color.parseColor("#E2E8F0")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { bottomMargin = (8 * density).toInt() } })
        summaryInside.addView(tvReportNet)
        cardSummary.addView(summaryInside)
        mainLayout.addView(cardSummary)

        // 2. GRAFIK KOMPARASI KAS BULANAN
        mainLayout.addView(TextView(context).apply { text = "Grafik Komparasi Arus Kas"; textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#64748B")); setPadding((4 * density).toInt(), 0, 0, (8 * density).toInt()) })
        val cardChart = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        chartContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        cardChart.addView(chartContainer)
        mainLayout.addView(cardChart)

        // 3. STATISTIK KATEGORI TERBOROS BULAN INI
        mainLayout.addView(TextView(context).apply { text = "Alokasi Kategori Terboros Bulan Ini"; textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#64748B")); setPadding((4 * density).toInt(), 0, 0, (8 * density).toInt()) })
        topBorosContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        mainLayout.addView(topBorosContainer)

        // 4. PUSAT REKOMENDASI AI PREMIUM CARD
        mainLayout.addView(TextView(context).apply { text = "Asisten Rekomendasi Finansial AI"; textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#64748B")); setPadding((4 * density).toInt(), 0, 0, (8 * density).toInt()) })
        val cardAi = MaterialCardView(context).apply {
            radius = 16 * density; cardElevation = 2 * density; strokeWidth = 0
            setCardBackgroundColor(Color.parseColor("#0F172A")) // Desain Midnight Slate Premium
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (24 * density).toInt() }
        }
        val aiInside = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        tvAiRecommendation = TextView(context).apply {
            text = "Klik tombol di bawah ini untuk mengizinkan AI menganalisis riwayat transaksi Cloud Anda dan memberikan rekomendasi penghematan finansial terpusat."
            setTextColor(Color.parseColor("#94A3B8")); textSize = 13f; setLineSpacing(3f, 1.1f)
        }
        aiInside.addView(tvAiRecommendation)

        pbAiLoading = ProgressBar(context, null, android.R.style.Widget_Material_ProgressBar_Small).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                gravity = Gravity.CENTER; topMargin = (12 * density).toInt(); bottomMargin = (12 * density).toInt() 
            }
        }
        aiInside.addView(pbAiLoading)

        btnTriggerAi = Button(context).apply {
            text = "✨ ANALISIS KEUANGAN SAYA"
            textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(Color.parseColor("#0D9488")) // Teal accent button
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (42 * density).toInt()).apply { topMargin = (14 * density).toInt() }
            setOnClickListener { triggerAiFinancialReview() }
        }
        aiInside.addView(btnTriggerAi)
        cardAi.addView(aiInside)
        mainLayout.addView(cardAi)

        nsv.addView(mainLayout)
        root.addView(nsv)

        observeCloudReportLive()
        return root
    }

    private fun observeCloudReportLive() {
        if (!isAdded) return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        reportListenerRegistration?.remove()
        reportListenerRegistration = firestore.collection("transactions")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                val calToday = Calendar.getInstance()
                val calLastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }

                var incomeThisMonth = 0.0
                var expenseThisMonth = 0.0
                var incomeLastMonth = 0.0
                var expenseLastMonth = 0.0

                val currentMonthExpenses = ArrayList<HashMap<String, Any>>()

                for (doc in snapshots.documents) {
                    val data = doc.data ?: continue
                    val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                    val typeRaw = (data["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT)
                    val timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val categoryName = data["categoryName"] as? String ?: "Umum"

                    val txCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val isThisMonth = txCal.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    val isLastMonth = txCal.get(Calendar.MONTH) == calLastMonth.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calLastMonth.get(Calendar.YEAR)

                    if (isThisMonth) {
                        if (typeRaw == "INCOME" || typeRaw == "DEBT") incomeThisMonth += amount
                        if (typeRaw == "EXPENSE" || typeRaw == "RECEIVABLE") {
                            expenseThisMonth += amount
                            val itemMap = HashMap<String, Any>().apply {
                                put("amount", amount)
                                put("categoryName", categoryName)
                            }
                            currentMonthExpenses.add(itemMap)
                        }
                    }
                    if (isLastMonth) {
                        if (typeRaw == "INCOME" || typeRaw == "DEBT") incomeLastMonth += amount
                        if (typeRaw == "EXPENSE" || typeRaw == "RECEIVABLE") expenseLastMonth += amount
                    }
                }

                // Update text view ringkasan dengan warna elegan
                tvReportIncome.text = "🟢 Total Pemasukan Bulan Ini: ${formatRupiah.format(incomeThisMonth)}"
                tvReportExpense.text = "🔴 Total Pengeluaran Bulan Ini: ${formatRupiah.format(expenseThisMonth)}"
                tvReportNet.text = "💰 Surplus Bersih Bulan Ini: ${formatRupiah.format(incomeThisMonth - expenseThisMonth)}"

                // Render Grafik Batang Komparasi Bulan Lalu vs Bulan Ini
                chartContainer.removeAllViews()
                val barChartView = DashboardFragment.QuadVerticalBarChartView(context, incomeLastMonth.toFloat(), incomeThisMonth.toFloat(), expenseLastMonth.toFloat(), expenseThisMonth.toFloat())
                chartContainer.addView(barChartView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (140 * density).toInt()))

                // Kategori Terboros Matematis
                topBorosContainer.removeAllViews()
                val groupedExpenses = currentMonthExpenses.groupBy { it["categoryName"] as String }
                    .mapValues { entry -> entry.value.sumOf { (it["amount"] as Double) } }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(3)

                if (groupedExpenses.isEmpty()) {
                    topBorosContainer.addView(TextView(context).apply {
                        text = "Belum ada pengeluaran terdeteksi bulan ini."; textSize = 13f
                        setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
                        setPadding(0, (20 * density).toInt(), 0, (20 * density).toInt())
                    })
                } else {
                    groupedExpenses.forEach { (categoryName, totalAmount) ->
                        val pct = if (expenseThisMonth > 0) ((totalAmount / expenseThisMonth) * 100).toInt() else 0

                        val rowCard = MaterialCardView(context).apply {
                            radius = 12 * density; strokeWidth = 0; setCardBackgroundColor(Color.WHITE)
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * density).toInt() }
                        }
                        val rowLayout = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                        }
                        rowLayout.addView(TextView(context).apply { text = "🔥"; textSize = 15f; setPadding(0, 0, (10 * density).toInt(), 0) })
                        
                        val txtLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                        txtLayout.addView(TextView(context).apply { text = categoryName; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD); textSize = 13.5f })
                        txtLayout.addView(TextView(context).apply { text = formatRupiah.format(totalAmount); setTextColor(Color.parseColor("#64748B")); textSize = 11.5f; setPadding(0, 2, 0, 0) })
                        rowLayout.addView(txtLayout)
                        
                        rowLayout.addView(TextView(context).apply { text = "$pct%"; setTextColor(Color.parseColor("#F43F5E")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                        rowCard.addView(rowLayout)
                        topBorosContainer.addView(rowCard)
                    }
                }
            }
    }

    private fun triggerAiFinancialReview() {
        val context = context ?: return
        btnTriggerAi.visibility = View.GONE
        pbAiLoading.visibility = View.VISIBLE
        tvAiRecommendation.text = "Menghubungi server Groq Cloud Premium..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val assistant = FinancialAssistant(context)
                val groqClient = GroqClient(context, assistant)
                
                // Kalimat perintah khusus menyuruh AI mengevaluasi database keuangan secara tajam & terperinci
                val aiPromptRequest = "Tolong berikan analisis evaluasi penghematan keuangan singkat terperinci untuk akun saya, sebutkan rumpun kategori yang paling boros bulan ini dan berikan solusinya secara tajam"
                
                val response = withContext(Dispatchers.IO) {
                    groqClient.sendMessageToAI(aiPromptRequest)
                }

                tvAiRecommendation.text = response
            } catch (e: Exception) {
                tvAiRecommendation.text = "⚠️ Gagal memuat rekomendasi AI: ${e.localizedMessage ?: "Timeout"}"
            } finally {
                pbAiLoading.visibility = View.GONE
                btnTriggerAi.visibility = View.VISIBLE
                btnTriggerAi.text = "🔄 RE-ANALISIS KEUANGAN"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reportListenerRegistration?.remove()
    }
}
