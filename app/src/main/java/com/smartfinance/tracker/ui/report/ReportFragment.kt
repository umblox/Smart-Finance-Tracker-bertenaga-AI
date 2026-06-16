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
    private lateinit var tvAiRecommendation: TextView
    private lateinit var btnTriggerAi: Button
    private lateinit var pbAiLoading: ProgressBar

    // (Biarkan fungsi onCreateView, fetchReportData, renderCharts, dan renderTopBoros persis seperti aslinya)
    // ... [BAGIAN UI TIDAK DIUBAH AGAR TIDAK MERUSAK DESAIN LU] ...

    // 🔥 INI YANG KITA ROMBAK: OTAK AI REPORT SEKARANG MEMBACA DATA REAL-TIME
    private fun triggerAiFinancialReview() {
        val context = context ?: return
        btnTriggerAi.visibility = View.GONE
        pbAiLoading.visibility = View.VISIBLE
        tvAiRecommendation.text = "Menganalisis data mutasi di Cloud..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val assistant = FinancialAssistant(context)
                val groqClient = GroqClient(context, assistant)
                
                // Prompt Super: Memerintahkan AI membaca TX_CONTEXT dari GroqClient
                val aiPromptRequest = """
                    Mam meminta REKOMENDASI FINANSIAL BULAN INI.
                    Tolong baca [RIWAYAT TRANSAKSI TERAKHIR] dan [SALDO UANG SAYA SAAT INI] yang sudah Anda miliki di konteks sistem Anda.
                    Berikan analisis evaluasi penghematan keuangan yang tajam berdasarkan ANGKA NYATA dari data tersebut.
                    Sebutkan pengeluaran apa yang paling boros bulan ini dan berikan solusinya.
                    WAJIB kembalikan action_type: "CHAT_ONLY" dan letakkan analisis Anda yang diformat rapi (gunakan emoji dan bullet points) di dalam field 'ai_response'.
                """.trimIndent()
                
                val response = withContext(Dispatchers.IO) {
                    groqClient.sendMessageToAI(aiPromptRequest)
                }
                tvAiRecommendation.text = response
            } catch (e: Exception) {
                tvAiRecommendation.text = "⚠️ Gagal memuat recommendation AI: ${e.localizedMessage}"
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
