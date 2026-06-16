package com.smartfinance.tracker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.R

class RecurringTxListDialog : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Gunakan layout sederhana, misal fragment_recurring_tx_list (Buat XML nya nanti: ada list dan tombol Add)
        val view = inflater.inflate(R.layout.fragment_settings, container, false) 
        
        // TODO: Render daftar dari koleksi Firestore "recurring_transactions"
        Toast.makeText(context, "Modul Transaksi Berkala Terbuka!", Toast.LENGTH_SHORT).show()
        
        return view
    }
}

