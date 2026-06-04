package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.smartfinance.tracker.ui.category.CategoryManagerDialog

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)

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

        // 1. MENU AKSES KATEGORI HIERARKI
        val menuCategory = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            setBackgroundColor(Color.WHITE)
            setOnClickListener {
                CategoryManagerDialog().show(parentFragmentManager, "CategoryManagerDialog")
            }
        }
        menuCategory.addView(TextView(context).apply {
            text = "⚙️ Kelola Struktur Kategori & Sub-Kategori"
            textSize = 14f; setTextColor(Color.parseColor("#2D3748"))
        })
        mainLayout.addView(menuCategory)

        // Spacing pembatas antar baris menu
        mainLayout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, (12 * density).toInt()) })

        // 2. MENU INPUT API KEY GROQ (KEMBALI DISEDIAKAN)
        val menuApi = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            setBackgroundColor(Color.WHITE)
            setOnClickListener {
                val etInput = EditText(context).apply {
                    hint = "Masukkan token api groq..."
                    setHintTextColor(Color.LTGRAY)
                }
                AlertDialog.Builder(context)
                    .setTitle("Konfigurasi API Groq")
                    .setView(etInput)
                    .setPositiveButton("Simpan") { d, _ ->
                        val key = etInput.text.toString().trim()
                        if (key.isNotEmpty()) {
                            prefs.edit().putString("groq_key_override", key).apply()
                            Toast.makeText(context, "API Key Groq berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                        }
                        d.dismiss()
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
        menuApi.addView(TextView(context).apply {
            text = "🔑 Ubah Token API Key Groq Cloud"
            textSize = 14f; setTextColor(Color.parseColor("#2D3748"))
        })
        mainLayout.addView(menuApi)

        mainLayout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, (12 * density).toInt()) })

        // 3. MENU TENTANG APLIKASI (KEMBALI DISEDIAKAN)
        val menuAbout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            setBackgroundColor(Color.WHITE)
            setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Tentang Aplikasi")
                    .setMessage("Smart Finance Tracker bertenaga AI v5.0\n\nCore akuntansi digerakkan penuh oleh Groq Cloud Llama 3.1 AI Engine dengan manajemen penyimpanan kaku SQLite Room Database.")
                    .setPositiveButton("Oke", null)
                    .show()
            }
        }
        menuAbout.addView(TextView(context).apply {
            text = "ℹ️ Informasi Mengenai Sistem Aplikasi"
            textSize = 14f; setTextColor(Color.parseColor("#2D3748"))
        })
        mainLayout.addView(menuAbout)

        return mainLayout
    }
}
