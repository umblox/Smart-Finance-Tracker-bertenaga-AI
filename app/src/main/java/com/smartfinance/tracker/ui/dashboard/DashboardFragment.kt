package com.smartfinance.tracker.ui.dashboard

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.databinding.FragmentDashboardBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Inisialisasi form manual dan muat data real database
        setupManualInputForm()
        loadRealDashboardData()
    }

    private fun setupManualInputForm() {
        val context = requireContext()
        val db = AppDatabase.getDatabase(context)
        
        // PERBAIKAN MUTLAK: Gunakan linearLayout induk langsung dari binding root tanpa memanggil android.R.id
        val layout = binding.root as? ViewGroup
        
        layout?.let { viewGroup ->
            // Pastikan form hanya dibuat satu kali agar tidak duplikat saat fragment dibuat ulang
            if (viewGroup.findViewWithTag<View>("manual_tx_form") == null) {
                
                val formContainer = LinearLayout(context).apply {
                    tag = "manual_tx_form"
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 16, 16, 16)
                }

                val tvTitle = TextView(context).apply {
                    text = "➕ INPUT TRANSAKSI MANUAL"
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#2D3748"))
                }
                formContainer.addView(tvTitle)

                val etAmount = EditText(context).apply { 
                    hint = "Nominal Uang (Rp)"
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER 
                }
                formContainer.addView(etAmount)

                val etNote = EditText(context).apply { hint = "Keterangan (ex: Beli Bakso)" }
                formContainer.addView(etNote)

                val spinnerCategory = Spinner(context)
                formContainer.addView(spinnerCategory)

                lifecycleScope.launch {
                    val categories = db.categoryDao().getAllCategories().first()
                    val catNames = categories.map { "${it.id} - ${it.name} (${it.type})" }
                    val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, catNames.ifEmpty { listOf("Belum ada kategori. Buat di Pengaturan") })
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerCategory.adapter = adapter
                }

                val btnSave = Button(context).apply {
                    text = "SIMPAN TRANSAKSI"
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
                    setOnClickListener {
                        val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                        val note = etNote.text.toString().trim()
                        val selectedCat = spinnerCategory.selectedItem?.toString() ?: ""

                        if (amount > 0.0 && selectedCat.isNotEmpty() && !selectedCat.contains("Belum ada")) {
                            lifecycleScope.launch {
                                val parts = selectedCat.split("-")
                                val catId = parts[0].trim().toLongOrNull() ?: 1L
                                val isIncome = selectedCat.contains("INCOME")
                                val catName = parts[1].split("(")[0].trim()

                                val newTx = TransactionEntity(
                                    amount = amount,
                                    type = if (isIncome) "INCOME" else "EXPENSE",
                                    categoryId = catId,
                                    categoryName = catName,
                                    note = if (note.isEmpty()) "Input Manual" else note,
                                    timestamp = System.currentTimeMillis()
                                )
                                db.transactionDao().insertTransaction(newTx)
                                Toast.makeText(context, "Transaksi Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                                
                                etAmount.setText("")
                                etNote.setText("")
                                loadRealDashboardData()
                            }
                        } else {
                            Toast.makeText(context, "Lengkapi nominal dan kategori!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                formContainer.addView(btnSave)
                
                // Sisipkan form secara aman ke dalam susunan vertikal utama halaman dashboard
                val targetLinearLayout = viewGroup.getChildAt(0) as? LinearLayout
                targetLinearLayout?.addView(formContainer, 2)
            }
        }
    }

    private fun loadRealDashboardData() {
        val db = AppDatabase.getDatabase(requireContext())
        
        lifecycleScope.launch {
            val allTransactions = db.transactionDao().getAllTransactions().first()
            
            var totalIncome = 0.0
            var totalExpense = 0.0
            var harian = 0.0
            var mingguan = 0.0
            var bulanan = 0.0

            val now = System.currentTimeMillis()
            val calTx = Calendar.getInstance()
            val calNow = Calendar.getInstance().apply { timeInMillis = now }

            for (tx in allTransactions) {
                if (tx.type == "INCOME") totalIncome += tx.amount else totalExpense += tx.amount

                calTx.timeInMillis = tx.timestamp
                val diffDays = (now - tx.timestamp) / (1000 * 60 * 60 * 24)

                if (diffDays <= 0) harian += if (tx.type == "EXPENSE") tx.amount else 0.0
                if (diffDays <= 7) mingguan += if (tx.type == "EXPENSE") tx.amount else 0.0
                if (calTx.get(Calendar.MONTH) == calNow.get(Calendar.MONTH)) {
                    bulanan += if (tx.type == "EXPENSE") tx.amount else 0.0
                }
            }

            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            binding.tvTotalBalance.text = formatRupiah.format(totalIncome - totalExpense)
            binding.tvIncomeSummary.text = formatRupiah.format(totalIncome)
            binding.tvExpenseSummary.text = formatRupiah.format(totalExpense)

            // Pasang interaksi klik rincian penuh pada total angka ringkasan
            val clickListener = View.OnClickListener {
                showFullDetailsDialog(allTransactions, formatRupiah)
            }
            binding.tvIncomeSummary.setOnClickListener(clickListener)
            binding.tvExpenseSummary.setOnClickListener(clickListener)

            val reportBuilder = StringBuilder()
            reportBuilder.append("📋 PENGELUARAN BERKALA\n")
            reportBuilder.append("▪️ Hari Ini: ${formatRupiah.format(harian)}\n")
            reportBuilder.append("▪️ 7 Hari Terakhir: ${formatRupiah.format(mingguan)}\n")
            reportBuilder.append("▪️ Bulan Ini: ${formatRupiah.format(bulanan)}\n\n")
            
            reportBuilder.append("🕒 HISTORY TRANSAKSI TERBARU (KLIK UNTUK DETAIL)\n")
            if (allTransactions.isEmpty()) {
                reportBuilder.append("Belum ada transaksi.")
            } else {
                allTransactions.take(5).forEach { tx ->
                    val sign = if (tx.type == "INCOME") "🟢 +" else "🔴 -"
                    reportBuilder.append("$sign ${tx.categoryName}: ${formatRupiah.format(tx.amount)} (${tx.note})\n")
                }
            }

            binding.tvDashboardReport.text = reportBuilder.toString()
            binding.tvDashboardReport.setOnClickListener { showFullDetailsDialog(allTransactions, formatRupiah) }

            setupReportChart(totalIncome.toFloat(), totalExpense.toFloat())
        }
    }

    private fun showFullDetailsDialog(list: List<TransactionEntity>, format: NumberFormat) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("📜 RINCIAN PENUH HISTORY TRANSAKSI")
        
        val details = StringBuilder()
        list.forEachIndexed { index, tx ->
            val tipe = if (tx.type == "INCOME") "[PEMASUKAN]" else "[PENGELUARAN]"
            details.append("${index + 1}. $tipe Kategori: ${tx.categoryName}\n Nominal: ${format.format(tx.amount)}\n Catatan: ${tx.note}\n\n")
        }
        
        builder.setMessage(details.ifEmpty { "Tidak ada data riwayat transaksi mendalam." })
        builder.setPositiveButton("Tutup", null)
        builder.show()
    }

    private fun setupReportChart(income: Float, expense: Float) {
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(1f, if(income == 0f) 1f else income))
        entries.add(BarEntry(2f, if(expense == 0f) 1f else expense))

        val dataSet = BarDataSet(entries, "Pemasukan vs Pengeluaran")
        dataSet.colors = arrayListOf(Color.parseColor("#2F855A"), Color.parseColor("#C53030"))
        binding.reportBarChart.data = BarData(dataSet)
        binding.reportBarChart.description.isEnabled = false
        binding.reportBarChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
