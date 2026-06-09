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
            setBackgroundColor(Color.parseColor("#F7FAFC"))
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
            text = "Laporan Keuangan"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A202C"))
            setPadding(0, 0, 0, (16 * density).toInt())
        })

        val cardSummary = MaterialCardView(context).apply {
            radius = 12 * density; cardElevation = 0f
            strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
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
            text = "Semua Riwayat Transaksi"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#718096"))
            setPadding(0, 0, 0, (8 * density).toInt())
        })

        val cardList = MaterialCardView(context).apply {
            radius = 12 * density; cardElevation = 0f
            strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.WHITE)
        }

        listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        }
        cardList.addView(listContainer)
        mainLayout.addView(cardList)

        nsv.addView(mainLayout)
        root.addView(nsv)

        observeCloudReportLive()
        return root
    }

    // 🔥 REAL-TIME WATCHER FOR REPORT: Mengikat data rekapitulasi langsung dari awan Firestore
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

                    // Klasifikasi aliran akuntansi kaku pendamping utang-piutang AI
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
                        put("note", note)
                    }
                    cloudTxList.add(itemMap)
                }

                tvReportIncome.text = "Pemasukan: ${formatRupiah.format(incomeSum)}"
                tvReportExpense.text = "Pengeluaran: ${formatRupiah.format(expenseSum)}"
                tvReportNet.text = "Selisih Bersih: ${formatRupiah.format(incomeSum - expenseSum)}"

                listContainer.removeAllViews()

                // Urutkan list berdasarkan riwayat mutasi waktu terbaru di awan
                cloudTxList.sortByDescending { (it["timestamp"] as? Long) ?: 0L }

                if (cloudTxList.isEmpty()) {
                    listContainer.addView(TextView(context).apply {
                        text = "Belum ada transaksi."
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setPadding(0, (20 * density).toInt(), 0, (20 * density).toInt())
                    })
                } else {
                    cloudTxList.forEachIndexed { index, item ->
                        val row = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding((8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt())
                            
                            setOnClickListener {
                                // Opsi aksi hapus/edit dokumen langsung menyasar target Firestore Document ID
                                val docId = item["id"] as String
                                showDeleteTransactionDialog(docId, item["note"] as String)
                            }
                        }

                        val note = item["note"] as String
                        val categoryName = item["categoryName"] as String
                        val timestamp = item["timestamp"] as Long
                        val amount = item["amount"] as Double
                        val currentType = item["type"] as String

                        val iconCircle = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#EDF2F7")) }
                            val txt = TextView(context).apply { text = if (currentType == "INCOME" || currentType == "DEBT") "📥" else "💸"; textSize = 16f; gravity = Gravity.CENTER }
                            addView(txt)
                        }
                        row.addView(iconCircle)

                        val center = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        center.addView(TextView(context).apply { text = note; setTextColor(Color.parseColor("#2D3748")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                        center.addView(TextView(context).apply { text = "$categoryName • ${sdf.format(Date(timestamp))}"; setTextColor(Color.parseColor("#A0AEC0")); textSize = 11f })
                        row.addView(center)

                        val isInc = currentType == "INCOME" || currentType == "DEBT"
                        row.addView(TextView(context).apply {
                            text = (if (isInc) "+" else "-") + formatRupiah.format(amount)
                            setTextColor(Color.parseColor(if (isInc) "#2B6CB0" else "#E53E3E"))
                            setTypeface(null, Typeface.BOLD)
                            textSize = 14f
                        })

                        listContainer.addView(row)

                        if (index < cloudTxList.size - 1) {
                            listContainer.addView(View(context).apply {
                                setBackgroundColor(Color.parseColor("#EDF2F7"))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { leftMargin = (50 * density).toInt() }
                            })
                        }
                    }
                }
            }
    }

    // 🔥 FULL CLOUD DELETE ACTION: Lenyapkan mutasi kas yang salah rekam langsung dari server awan Firestore
    private fun showDeleteTransactionDialog(docId: String, note: String) {
        val contextRef = requireContext()
        AlertDialog.Builder(contextRef).apply {
            setTitle("🗑️ Hapus Transaksi?")
            setMessage("Apakah Anda yakin ingin menghapus transaksi \"$note\" secara permanen dari server Cloud?")
            setPositiveButton("Ya, Hapus") { _, _ ->
                firestore.collection("transactions").document(docId).delete()
                Toast.makeText(contextRef, "Transaksi berhasil dihapus dari Cloud!", Toast.LENGTH_SHORT).show()
            }
            setNegativeButton("Batal", null)
            show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reportListenerRegistration?.remove()
    }
}
