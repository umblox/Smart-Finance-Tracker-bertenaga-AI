package com.smartfinance.tracker.ui.category

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.R
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale

class CategoryManagerDialog : DialogFragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var categoryListenerRegistration: ListenerRegistration? = null
    
    private lateinit var containerList: LinearLayout
    private lateinit var btnTabExpense: MaterialButton
    private lateinit var btnTabIncome: MaterialButton
    private lateinit var btnTabDebt: MaterialButton
    
    private var currentTypeFilter = "EXPENSE"
    private var allCategoriesCloud = listOf<Map<String, Any>>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC")) // Menyesuaikan warna abu soft premium aplikasi
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // 1. TOP BAR CONTROL DENGAN TOMBOL BATAL/TUTUP PREMIUM
        val topBarRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16f * density).toInt(), (14f * density).toInt(), (16f * density).toInt(), (14f * density).toInt())
            setBackgroundColor(Color.WHITE)
        }
        val btnClose = TextView(context).apply {
            text = "✕"; textSize = 18f; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, (20f * density).toInt(), 0)
            setOnClickListener { dismiss() }
        }
        val tvHeaderTitle = TextView(context).apply {
            text = "Manajemen Kategori Cloud"; textSize = 15.5f; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD)
        }
        topBarRow.addView(btnClose)
        topBarRow.addView(tvHeaderTitle)
        mainLayout.addView(topBarRow)

        // 2. KONTROL FILTER TABHorizontal ELEGANT (Menyelaraskan tema UI Buku Utang)
        val tabOuterBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((4f * density).toInt(), (4f * density).toInt(), (4f * density).toInt(), (4f * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (42f * density).toInt()).apply {
                setMargins((16f * density).toInt(), (14f * density).toInt(), (16f * density).toInt(), (10f * density).toInt())
            }
            background = GradientDrawable().apply { cornerRadius = 12f * density; setColor(Color.parseColor("#E2E8F0")) }
            weightSum = 3f
        }

        btnTabExpense = MaterialButton(context).apply { text = "Pengeluaran"; textSize = 11.5f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }
        btnTabIncome = MaterialButton(context).apply { text = "Pemasukan"; textSize = 11.5f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }
        btnTabDebt = MaterialButton(context).apply { text = "Hutang/Piutang"; textSize = 11.5f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }

        tabOuterBox.addView(btnTabExpense)
        tabOuterBox.addView(btnTabIncome)
        tabOuterBox.addView(btnTabDebt)
        mainLayout.addView(tabOuterBox)

        // 3. ACCENT BUTTON TAMBAH KATEGORI BARU TEAL PREMIUM
        val btnAdd = MaterialButton(context).apply {
            text = "＋ BUAT KATEGORI BARU"
            textSize = 12.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            cornerRadius = (14f * density).toInt()
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0D9488")) // Teal premium accent
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (46f * density).toInt()).apply {
                setMargins((16f * density).toInt(), 0, (16f * density).toInt(), (14f * density).toInt())
            }
            setOnClickListener { 
                CategoryEditorDialog(null, currentTypeFilter) { renderHierarchyCloud() }.show(parentFragmentManager, "CategoryEditorDialog")
            }
        }
        mainLayout.addView(btnAdd)

        // 4. SCROLL CONTAINER DATA LIST
        val scrollView = ScrollView(context).apply { isFillViewport = true }
        containerList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16f * density).toInt(), 0, (16f * density).toInt(), (20f * density).toInt())
        }
        scrollView.addView(containerList)
        mainLayout.addView(scrollView)

        btnTabExpense.setOnClickListener { switchFilterTab("EXPENSE", btnTabExpense, btnTabIncome, btnTabDebt) }
        btnTabIncome.setOnClickListener { switchFilterTab("INCOME", btnTabIncome, btnTabExpense, btnTabDebt) }
        btnTabDebt.setOnClickListener { switchFilterTab("DEBT", btnTabDebt, btnTabExpense, btnTabIncome) }

        switchFilterTab("EXPENSE", btnTabExpense, btnTabIncome, btnTabDebt)
        observeCategoriesCloudLive()

        return AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            .setView(mainLayout)
            .create()
    }

    private fun switchFilterTab(targetFilter: String, active: MaterialButton, in1: MaterialButton, in2: MaterialButton) {
        currentTypeFilter = targetFilter
        active.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B")) // Slate dark active
        active.setTextColor(Color.WHITE); active.setTypeface(null, Typeface.BOLD)
        
        in1.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
        in1.setTextColor(Color.parseColor("#64748B")); in1.setTypeface(null, Typeface.NORMAL)
        in2.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
        in2.setTextColor(Color.parseColor("#64748B")); in2.setTypeface(null, Typeface.NORMAL)
        
        renderHierarchyCloud()
    }

    private fun observeCategoriesCloudLive() {
        if (!isAdded) return
        categoryListenerRegistration?.remove()
        categoryListenerRegistration = firestore.collection("categories")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                val list = ArrayList<Map<String, Any>>()
                for (doc in snapshots.documents) {
                    val data = doc.data ?: continue
                    val mutableData = HashMap(data)
                    mutableData["docId"] = doc.id
                    mutableData["id"] = doc.getLong("id") ?: 0L
                    list.add(mutableData)
                }
                allCategoriesCloud = list
                renderHierarchyCloud()
            }
    }

    private fun renderHierarchyCloud() {
        containerList.removeAllViews()
        val context = context ?: return
        val density = context.resources.displayMetrics.density

        val filtered = allCategoriesCloud.filter { (it["type"] as? String) == currentTypeFilter }
        val parentCategories = filtered.filter { it["parentCategoryId"] == null }
        val subCategories = filtered.filter { it["parentCategoryId"] != null }

        if (parentCategories.isEmpty()) {
            containerList.addView(TextView(context).apply {
                text = "Belum ada rumpun kategori terdaftar."
                textSize = 13.5f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
                setPadding(0, (40f * density).toInt(), 0, 0); setTypeface(null, Typeface.ITALIC)
            })
            return
        }

        parentCategories.forEach { parent ->
            // Pembungkus Card Luxury untuk rumpun kategori induk beserta anaknya
            val blockCard = MaterialCardView(context).apply {
                radius = 14f * density; cardElevation = 1f * density; strokeWidth = 0
                setCardBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12f * density).toInt() }
            }

            val cardContentContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

            // BARIS KATEGORI INDUK
            val parentRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((14f * density).toInt(), (14f * density).toInt(), (14f * density).toInt(), (14f * density).toInt())
                setOnClickListener { 
                    val passMap = HashMap(parent)
                    CategoryEditorDialog(passMap, currentTypeFilter) { renderHierarchyCloud() }.show(parentFragmentManager, "CategoryEditorDialog")
                }
            }

            val iconView = TextView(context).apply { text = "📁"; textSize = 16f; setPadding(0, 0, (12f * density).toInt(), 0) }
            val titleView = TextView(context).apply {
                text = parent["name"] as? String ?: ""; setTextColor(Color.parseColor("#1E293B")); textSize = 14.5f; setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            parentRow.addView(iconView)
            parentRow.addView(titleView)

            val isLocked = parent["isLocked"] as? Boolean ?: false
            if (isLocked) {
                parentRow.addView(TextView(context).apply { text = "🔒"; textSize = 13f; setTextColor(Color.parseColor("#94A3B8")); setPadding((6f * density).toInt(), 0, 0, 0) })
            }
            cardContentContainer.addView(parentRow)

            val parentId = parent["id"] as Long
            val kids = subCategories.filter { (it["parentCategoryId"] as? Number)?.toLong() == parentId }
            
            if (kids.isNotEmpty()) {
                cardContentContainer.addView(View(context).apply { setBackgroundColor(Color.parseColor("#F1F5F9")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1f * density).toInt()) })
            }

            // BARIS KATEGORI ANAK (SUB-KATEGORI)
            kids.forEach { child ->
                val childRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding((14f * density).toInt(), (10f * density).toInt(), (14f * density).toInt(), (10f * density).toInt())
                    setBackgroundColor(Color.parseColor("#FAFAFA"))
                    setOnClickListener { 
                        val passMap = HashMap(child)
                        CategoryEditorDialog(passMap, currentTypeFilter) { renderHierarchyCloud() }.show(parentFragmentManager, "CategoryEditorDialog")
                    }
                }

                val treeLine = View(context).apply {
                    setBackgroundColor(Color.parseColor("#CBD5E0"))
                    layoutParams = LinearLayout.LayoutParams((1.5f * density).toInt(), (16f * density).toInt()).apply { rightMargin = (12f * density).toInt(); leftMargin = (6f * density).toInt() }
                }
                childRow.addView(treeLine)

                val childIcon = TextView(context).apply { text = "💰"; textSize = 13f; setPadding(0, 0, (10f * density).toInt(), 0) }
                val childTitle = TextView(context).apply {
                    text = child["name"] as? String ?: ""; setTextColor(Color.parseColor("#475569")); textSize = 13.5f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                childRow.addView(childIcon)
                childRow.addView(childTitle)

                val isChildLocked = child["isLocked"] as? Boolean ?: false
                if (isChildLocked) {
                    childRow.addView(TextView(context).apply { text = "🔒"; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); setPadding((6f * density).toInt(), 0, 0, 0) })
                }
                cardContentContainer.addView(childRow)
            }

            blockCard.addView(cardContentContainer)
            containerList.addView(blockCard)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        categoryListenerRegistration?.remove()
    }
}
