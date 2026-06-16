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
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GroqClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var reportListenerRegistration: ListenerRegistration? = null

    private lateinit var tvAiRecommendation: TextView
    private lateinit var btnTriggerAi: Button
    private lateinit var pbAiLoading: ProgressBar

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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()) 
        }

        mainLayout.addView(TextView(context).apply {
            text = "Laporan & Evaluasi AI"
            textSize = 22f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, 4, 0, (16 * density).toInt())
        })

        // 🔥 KARTU AI REVIEW
        val cardAi = MaterialCardView(context).apply {
            radius = 16 * density
            cardElevation = 2 * density
            strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (20 * density).toInt() }
        }
        
        val aiLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        aiLayout.addView(TextView(context).apply {
            text = "🤖 Rekomendasi Finansial"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0D9488"))
            setPadding(0, 0, 0, (12 * density).toInt())
        })

        tvAiRecommendation = TextView(context).apply {
            text = "Klik tombol di bawah untuk meminta AI membaca data cloud Anda dan memberikan rekomendasi penghematan cerdas."
            textSize = 14f
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        aiLayout.addView(tvAiRecommendation)

        pbAiLoading = ProgressBar(context).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER; bottomMargin = (16 * density).toInt() }
        }
        aiLayout.addView(pbAiLoading)

        btnTriggerAi = Button(context).apply {
            text = "MINTA EVALUASI AI"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { triggerAiFinancialReview() }
        }
        aiLayout.addView(btnTriggerAi)

        cardAi.addView(aiLayout)
        mainLayout.addView(cardAi)

        nsv.addView(mainLayout)
        root.addView(nsv)

        return root
    }

    private fun triggerAiFinancialReview() {
        val context = context ?: return
        btnTriggerAi.visibility = View.GONE
        pbAiLoading.visibility = View.VISIBLE
        tvAiRecommendation.text = "Menganalisis data mutasi di Cloud..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val assistant = FinancialAssistant(context)
                val groqClient = GroqClient(context, assistant)
                
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
}
