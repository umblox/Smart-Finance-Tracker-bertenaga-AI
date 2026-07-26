package com.smartfinance.tracker.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.smartfinance.tracker.data.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {
    private val formatRp = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfDate = SimpleDateFormat("dd-MM-yyyy", Locale("id", "ID"))
    private val sdfDateTime = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID"))

    // 🔥 Fungsi membuat file PDF Cache sementara (untuk keperluan Preview)
    suspend fun generatePdfToTempFile(context: Context, transactions: List<Transaction>, title: String): File {
        return withContext(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842 // Ukuran standar A4
            
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
            val headerPaint = Paint().apply { textSize = 12f; isFakeBoldText = true; color = Color.BLACK }
            val textPaint = Paint().apply { textSize = 10f; color = Color.DKGRAY }
            val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

            var startY = 50f

            fun drawHeaders() {
                canvas.drawText(title, 50f, startY, titlePaint)
                startY += 20f
                canvas.drawText("Dicetak pada: ${sdfDateTime.format(Date())} | Total Data: ${transactions.size}", 50f, startY, textPaint)
                startY += 40f

                canvas.drawText("Tanggal", 50f, startY, headerPaint)
                canvas.drawText("Kategori", 140f, startY, headerPaint)
                canvas.drawText("Catatan", 280f, startY, headerPaint)
                canvas.drawText("Nominal", 460f, startY, headerPaint)
                startY += 10f
                canvas.drawLine(50f, startY, 545f, startY, linePaint)
                startY += 20f
            }

            drawHeaders()

            for (tx in transactions) {
                val date = sdfDate.format(Date(tx.timestamp))
                val cat = tx.categoryName
                var note = tx.note
                if (note.length > 30) note = note.substring(0, 27) + "..."
                
                val isInc = tx.type == "INCOME" || tx.type == "DEBT"
                val prefix = if (isInc) "+" else "-"
                val formattedAmt = "$prefix${formatRp.format(tx.amount)}"

                canvas.drawText(date, 50f, startY, textPaint)
                canvas.drawText(cat, 140f, startY, textPaint)
                canvas.drawText(note, 280f, startY, textPaint)
                
                textPaint.color = if (isInc) Color.parseColor("#006400") else Color.RED
                canvas.drawText(formattedAmt, 460f, startY, textPaint)
                textPaint.color = Color.DKGRAY
                
                startY += 25f 

                // Jika halaman penuh, ganti halaman
                if (startY > 780f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    startY = 50f
                    drawHeaders()
                }
            }
            pdfDocument.finishPage(page)
            
            // Tulis PDF ke folder Cache Android
            val tempFile = File(context.cacheDir, "preview_report.pdf")
            FileOutputStream(tempFile).use { os ->
                pdfDocument.writeTo(os)
            }
            pdfDocument.close()
            
            tempFile
        }
    }

    // 🔥 Fungsi untuk menduplikasi Cache ke Storage (Saat tombol Save diklik)
    suspend fun copyFileToUri(context: Context, sourceFile: File, targetUri: Uri) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(targetUri)?.use { os ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(os)
                }
            }
        }
    }
}
