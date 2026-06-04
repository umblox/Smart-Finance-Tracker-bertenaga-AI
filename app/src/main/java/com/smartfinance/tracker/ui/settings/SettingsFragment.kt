package com.smartfinance.tracker.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.smartfinance.tracker.ui.category.CategoryManagerDialog // IMPORT FIX

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        mainLayout.addView(TextView(context).apply {
            text = "Pengaturan"
            textSize = 22f
            setTextColor(Color.parseColor("#1A202C"))
            setPadding(0, 0, 0, (24 * density).toInt())
        })

        // MENU AKSES KATEGORI
        val menuCategory = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            setBackgroundColor(Color.WHITE)
            setOnClickListener {
                // Panggil Dialog Manajemen Kategori yang Baru & Bersih
                CategoryManagerDialog().show(parentFragmentManager, "CategoryManagerDialog")
            }
        }

        menuCategory.addView(TextView(context).apply {
            text = "⚙️ Kelola Struktur Kategori & Sub-Kategori"
            textSize = 14f
            setTextColor(Color.parseColor("#2D3748"))
        })

        mainLayout.addView(menuCategory)
        return mainLayout
    }
}
