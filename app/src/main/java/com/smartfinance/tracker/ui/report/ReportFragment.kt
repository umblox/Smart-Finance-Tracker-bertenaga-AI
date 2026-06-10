package com.smartfinance.tracker.ui.report

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
import com.smartfinance.tracker.ui.transaction.TransactionEditorDialog
import com.smartfinance.tracker.R // Pastikan import resource terpasang aman
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ReportFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var reportListenerRegistration: ListenerRegistration? = null

    private lateinit var listContainer: LinearLayout
    private lateinit var tvReportIncome: TextView
    private lateinit var tvReportExpense: TextView
    private lateinit var tvReportNet: TextView
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val root = RelativeLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#F8FAFC")) // Menggunakan warna abu premium soft
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
            text = "Riwayat Buku Transaksi"
            textSize = 20f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, (8 * density).toInt(), 0, (16 * density).toInt())
        })

        // Ringkasan Saldo Atas Berbentuk Card Melengkung Halus Elegant
        val cardSummary = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 2 * density
            strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (20 * density).toInt() }
        }

        val summaryInside = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        tvReportIncome = TextView(context).apply { text = "Pemasukan: Rp 0"; setTextColor(Color.parseColor("#2B6CB0")); textSize = 15f; setPadding(0, 0, 0, (4 * density).toInt()) }
        tvReportExpense = TextView(context).apply { text = "Pengeluaran: Rp 0"; setTextColor(Color.parseColor("#E53E3E")); textSize = 15f; setPadding(0, 0, 0, (8 * density).toInt()) }
        tvReportNet = TextView(context).apply { text = "Selisih Bersih: Rp 0"; setTextColor(Color.parseColor("#2D3748")); textSize = 16f; setTypeface(null, Typeface.BOLD) }

        summaryInside.addView(tvReportIncome)
        summaryInside.addView(tvReportExpense)
        summaryInside.addView(View(context).apply { setBackgroundColor(Color.parseColor("#E2E8F0")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { bottomMargin = (8 * density).toInt() } })
        summaryInside.addView(tvReportNet)
        cardSummary.addView(summaryInside)
        mainLayout.addView(cardSummary)

        mainLayout.addView(TextView(context).apply {
            text = "Semua Riwayat Mutasi"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
            setPadding((4 * density).toInt(), 0, 0, (10 * density).toInt())
        })

        listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        mainLayout.addView(listContainer)

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

                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                var incomeSum = 0.0
                var expenseSum = 0.0

                val cloudTxList = ArrayList<HashMap<String, Any>>()

                for (doc in snapshots.documents) {
                    val data = doc.data ?: continue
                    val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                    val type = (data["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT)
                    val timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val categoryName = data["categoryName"] as? String ?: "Umum"
                    val note = data["note"] as? String ?: "Transaksi AI"
                    val categoryId = (data["categoryId"] as? Number)?.toLong() ?: 0L

                    // 🔥 FIX SINKRONISASI MATEMATIKA KAS: Satukan aturan filter agar utang piutang sinkron mutlak!
                    if (type == "INCOME" || type == "DEBT") {
                        incomeSum += amount
                    } else if (type == "EXPENSE" || type == "RECEIVABLE") {
                        expenseSum += amount
                    }

                    val itemMap = HashMap<String, Any>().apply {
                        put("id", doc.id)
                        put("amount", amount)
                        put("type", type)
                        put("timestamp", timestamp)
                        put("categoryName", categoryName)
                        put("categoryId", categoryId)
                        put("note", note)
                    }
                    cloudTxList.add(itemMap)
                }

                tvReportIncome.text = "Pemasukan: ${formatRupiah.format(incomeSum)}"
                tvReportExpense.text = "Pengeluaran: ${formatRupiah.format(expenseSum)}"
                tvReportNet.text = "Selisih Bersih: ${formatRupiah.format(incomeSum - expenseSum)}"

                listContainer.removeAllViews()

                cloudTxList.sortByDescending { (it["timestamp"] as? Long) ?: 0L }

                if (cloudTxList.isEmpty()) {
                    listContainer.addView(TextView(context).apply {
                        text = "Belum ada riwayat transaksi."
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#94A3B8"))
                        setPadding(0, (40 * density).toInt(), 0, (40 * density).toInt())
                    })
                } else {
                    cloudTxList.forEach { item ->
                        val currentType = item["type"] as String
                        // 🔥 FIX KONDISI VISUAL: Sinkronkan penentuan arah tanda plus-minus kas masuk/keluar
                        val isInc = currentType == "INCOME" || currentType == "DEBT"

                        // ✨ MEWAH: Setiap row baris riwayat dibungkus MaterialCardView melengkung asimetris premium
                        val rowCard = MaterialCardView(context).apply {
                            radius = 14 * density
                            cardElevation = 1.5f * density
                            strokeWidth = 0
                            setCardBackgroundColor(Color.WHITE)
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (10 * density).toInt() }
                            
                            setOnClickListener {
                                TransactionEditorDialog(item) {
                                    // Live snapshot cloud mengunci reaktivitas otomatis
                                }.show(parentFragmentManager, "TransactionEditorDialog")
                            }
                        }

                        val rowInside = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding((14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt())
                        }

                        val note = item["note"] as String
                        val categoryName = item["categoryName"] as String
                        val timestamp = item["timestamp"] as Long
                        val amount = item["amount"] as Double

                        val iconCircle = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#F1F5F9")) }
                            val txt = TextView(context).apply { text = if (isInc) "📥" else "💸"; textSize = 15f; gravity = Gravity.CENTER }
                            addView(txt)
                        }
                        rowInside.addView(iconCircle)

                        val center = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        center.addView(TextView(context).apply { text = note; setTextColor(Color.parseColor("#1E293B")); setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); textSize = 14.5f })
                        center.addView(TextView(context).apply { text = "$categoryName • ${sdf.format(Date(timestamp))}"; setTextColor(Color.parseColor("#94A3B8")); textSize = 11.5f; setPadding(0, (2 * density).toInt(), 0, 0) })
                        rowInside.addView(center)

                        rowInside.addView(TextView(context).apply {
                            text = (if (isInc) "+" else "-") + formatRupiah.format(amount)
                            setTextColor(Color.parseColor(if (isInc) "#0284C7" else "#EF4444")) // Warna modern sky-blue & crimson soft
                            setTypeface(null, Typeface.BOLD)
                            textSize = 14.5f
                        })

                        rowCard.addView(rowInside)
                        listContainer.addView(rowCard)
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reportListenerRegistration?.remove()
    }
}
