package com.smartfinance.tracker.ui.debt

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Debt
import com.smartfinance.tracker.databinding.ItemDebtBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DebtAdapter(
    private val currentTabFilter: String,
    private val onItemClick: (Debt) -> Unit
) : ListAdapter<Debt, DebtAdapter.DebtViewHolder>(DebtDiffCallback()) {

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfDisplay = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale("id", "ID"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DebtViewHolder {
        val binding = ItemDebtBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DebtViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DebtViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DebtViewHolder(private val binding: ItemDebtBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(debt: Debt) {
            val context = binding.root.context
            val density = context.resources.displayMetrics.density

            // Ambil warna tema
            val colorBackground = ContextCompat.getColor(context, R.color.background_color)
            val colorSurface = ContextCompat.getColor(context, R.color.surface_white)
            val colorIncome = ContextCompat.getColor(context, R.color.income_green)
            val colorDebt = ContextCompat.getColor(context, R.color.debt_orange)
            val colorReceivable = ContextCompat.getColor(context, R.color.receivable_blue)
            val colorDivider = ContextCompat.getColor(context, R.color.divider_color)

            binding.tvContactName.text = debt.contactName
            binding.tvDate.text = "📅 ${sdfDisplay.format(Date(debt.timestamp))}"

            val paidAmount = debt.amount - debt.remainingAmount
            val progressPercent = if (debt.amount > 0) ((paidAmount / debt.amount) * 100).toInt() else 0

            // 🔥 FIX: Menggunakan string dinamis dari resources untuk mendukung dual bahasa
            binding.tvTotalAmount.text = context.getString(R.string.debt_total_loan, formatRupiah.format(debt.amount))
            binding.tvProgressPercent.text = context.getString(R.string.debt_progress_percent, progressPercent)

            // Logika Status Lunas / Belum
            if (debt.isPaid) {
                binding.cardDebtItem.setCardBackgroundColor(colorBackground)
                binding.tvBadgeLunas.visibility = View.VISIBLE
                binding.layoutRemaining.visibility = View.GONE
                
                binding.tvBadgeLunas.background = GradientDrawable().apply {
                    cornerRadius = 20f * density
                    setColor(colorIncome)
                }
            } else {
                binding.cardDebtItem.setCardBackgroundColor(colorSurface)
                binding.tvBadgeLunas.visibility = View.GONE
                binding.layoutRemaining.visibility = View.VISIBLE

                binding.tvRemainingAmount.text = formatRupiah.format(debt.remainingAmount)
                binding.tvRemainingAmount.setTextColor(if (currentTabFilter == "DEBT") colorDebt else colorReceivable)
            }

            // Styling Progress Bar
            binding.progressBar.progress = progressPercent
            binding.progressBar.progressTintList = ColorStateList.valueOf(if (currentTabFilter == "DEBT") colorDebt else colorReceivable)
            binding.progressBar.progressBackgroundTintList = ColorStateList.valueOf(colorDivider)

            // Aksi Klik memanggil Dialog Editor
            binding.root.setOnClickListener { onItemClick(debt) }
        }
    }

    class DebtDiffCallback : DiffUtil.ItemCallback<Debt>() {
        override fun areItemsTheSame(oldItem: Debt, newItem: Debt): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Debt, newItem: Debt): Boolean = oldItem == newItem
    }
}
