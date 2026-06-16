package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Color
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
    
    private lateinit var topBorosContainer: LinearLayout
    
    // 🔥 Komponen AI Review
    private lateinit var tvAiRecommendation: TextView
    private lateinit var btnTriggerAi: Button
    private lateinit var pbAiLoading: ProgressBar

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val nsv = NestedScrollView(context).apply { isFillViewport = true }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        // --- VISUAL ASLI LU (TIDAK ADA YANG DIHAPUS) ---
        root.addView(TextView(context).apply { text = "Laporan Keuangan"; textSize = 22f; setTypeface(null, Typeface.BOLD) })
        
        // Card Summary
        val cardSummary = MaterialCardView(context).apply { radius = 14 * density; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 } }
        val sumL = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 30) }
        tvReportIncome = TextView(context).apply { text = "Pemasukan: Rp 0" }
        tvReportExpense = TextView(context).apply { text = "Pengeluaran: Rp 0" }
        tvReportNet = TextView(context).apply { text = "Sisa: Rp 0"; setTypeface(null, Typeface.BOLD) }
        sumL.addView(tvReportIncome); sumL.addView(tvReportExpense); sumL.addView(tvReportNet)
        cardSummary.addView(sumL)
        root.addView(cardSummary)

        // Chart Container
        chartContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 } }
        root.addView(chartContainer)

        // Top Boros
        topBorosContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 } }
        root.addView(topBorosContainer)

        // --- 🔥 INTEGRASI OTAK AI (TAMBAHAN) ---
        val cardAi = MaterialCardView(context).apply { radius = 16 * density; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 40 } }
        val aiLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 30) }
        aiLayout.addView(TextView(context).apply { text = "🤖 Rekomendasi AI"; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#0D9488")) })
        tvAiRecommendation = TextView(context).apply { text = "Klik tombol untuk analisis cerdas..."; textSize = 13f; setPadding(0, 10, 0, 20) }
        aiLayout.addView(tvAiRecommendation)
        pbAiLoading = ProgressBar(context).apply { visibility = View.GONE }
        aiLayout.addView(pbAiLoading)
        btnTriggerAi = Button(context).apply { text = "MINTA ANALISIS CERDAS"; setOnClickListener { triggerAiFinancialReview() } }
        aiLayout.addView(btnTriggerAi)
        cardAi.addView(aiLayout)
        root.addView(cardAi)

        nsv.addView(root)
        fetchReportData()
        return nsv
    }

    // --- FUNGSI VISUAL ASLI LU ---
    private fun fetchReportData() {
        // (Di sini lu panggil logika fetch data asli lu yang sudah berjalan baik)
        // Pastikan memanggil renderCharts() dan renderTopBoros()
    }

    // --- 🔥 FUNGSI OTAK AI (PENGGUNAAN GROQCLIENT) ---
    private fun triggerAiFinancialReview() {
        val context = context ?: return
        btnTriggerAi.visibility = View.GONE
        pbAiLoading.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val assistant = FinancialAssistant(context)
                val groqClient = GroqClient(context, assistant)
                
                // Prompt yang memanggil data context yang sudah disiapkan GroqClient
                val aiPromptRequest = "Berikan analisis tajam mengenai pengeluaran saya bulan ini berdasarkan data riwayat yang ada, dan beri saran penghematan."
                
                val response = withContext(Dispatchers.IO) {
                    groqClient.sendMessageToAI(aiPromptRequest)
                }
                tvAiRecommendation.text = response
            } catch (e: Exception) {
                tvAiRecommendation.text = "Error: ${e.message}"
            } finally {
                pbAiLoading.visibility = View.GONE
                btnTriggerAi.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reportListenerRegistration?.remove()
    }
}
