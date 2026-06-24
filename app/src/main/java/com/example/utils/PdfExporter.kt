package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.Worker
import com.example.data.OvertimeLog
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object PdfExporter {
    fun exportReportToPdfAndShare(
        context: Context,
        weekLabel: String,
        fromDate: String,
        toDate: String,
        workers: List<Worker>,
        logs: List<OvertimeLog>
    ) {
        val pdfDocument = PdfDocument()
        
        // Define page size (A4 is approx 595 x 842 points)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        
        val paint = Paint()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subHeaderPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            isAntiAlias = true
        }
        val tableHeaderPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        
        var y = 40f
        
        // Header
        canvas.drawText("Overtime Tracker Report", 40f, y, headerPaint)
        y += 22f
        
        val dateRange = if (fromDate == toDate) fromDate else "$fromDate to $toDate"
        canvas.drawText("Date Range: $dateRange", 40f, y, subHeaderPaint)
        if (weekLabel.isNotEmpty()) {
            y += 14f
            canvas.drawText("Week: $weekLabel", 40f, y, subHeaderPaint)
        }
        y += 25f
        
        // Draw horizontal line
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 20f
        
        // Table Headers
        canvas.drawText("Worker Name", 40f, y, tableHeaderPaint)
        canvas.drawText("Section", 260f, y, tableHeaderPaint)
        canvas.drawText("Shift", 460f, y, tableHeaderPaint)
        y += 10f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 20f
        
        var shownWorkersCount = 0
        
        // Filter and group logs inside the date range
        val logsByWorker = logs.filter { it.date in fromDate..toDate && it.isChecked }.groupBy { it.workerId }
        
        workers.forEach { worker ->
            val workerLogs = (logsByWorker[worker.id] ?: emptyList()).sortedBy { it.date }
            
            if (workerLogs.isNotEmpty()) {
                // Auto pagination handling
                if (y > 780f) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 40f
                    
                    // Redraw Table Headers on new page
                    canvas.drawText("Worker Name", 40f, y, tableHeaderPaint)
                    canvas.drawText("Section", 260f, y, tableHeaderPaint)
                    canvas.drawText("Shift", 460f, y, tableHeaderPaint)
                    y += 10f
                    canvas.drawLine(40f, y, 555f, y, linePaint)
                    y += 20f
                }
                
                // Name
                canvas.drawText(worker.name, 40f, y, textPaint.apply { isFakeBoldText = true; textSize = 11f; color = Color.BLACK })
                // Section
                canvas.drawText(worker.section, 260f, y, textPaint.apply { isFakeBoldText = false; textSize = 11f; color = Color.BLACK })
                // Shift
                canvas.drawText(worker.shift, 460f, y, textPaint.apply { isFakeBoldText = false; textSize = 11f; color = Color.BLACK })
                
                shownWorkersCount++
                y += 20f
                
                // Draw individual logs under their name
                workerLogs.forEach { log ->
                    if (y > 780f) {
                        pdfDocument.finishPage(page)
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        y = 40f
                    }
                    val logDetail = "   • Overtime Date: ${log.date}" + if (log.note.isNotEmpty()) " | Note: ${log.note}" else ""
                    canvas.drawText(logDetail, 50f, y, textPaint.apply { isFakeBoldText = true; color = Color.BLACK; textSize = 10f })
                    y += 14f
                }
                
                textPaint.color = Color.BLACK // restore color
                y += 6f
                canvas.drawLine(40f, y, 555f, y, linePaint)
                y += 15f
            }
        }
        
        // Summary block
        if (y > 740f) {
            pdfDocument.finishPage(page)
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
        }
        
        y += 10f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 20f
        canvas.drawText("Total Active Workers with OT: $shownWorkersCount", 40f, y, tableHeaderPaint)
        
        pdfDocument.finishPage(page)
        
        try {
            val cacheFile = File(context.cacheDir, "OT_Report_${fromDate}_to_${toDate}.pdf")
            val outputStream = FileOutputStream(cacheFile)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()
            
            val contentUri = FileProvider.getUriForFile(
                context,
                "com.aistudio.ottracker.globesteels.fileprovider",
                cacheFile
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "OT Tracker Report ($fromDate to $toDate)")
                putExtra(Intent.EXTRA_TEXT, "Overtime Report from $fromDate to $toDate")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Export PDF Report"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareReportToWhatsApp(
        context: Context,
        weekLabel: String,
        fromDate: String,
        toDate: String,
        workers: List<Worker>,
        logs: List<OvertimeLog>
    ) {
        val dateRange = if (fromDate == toDate) fromDate else "$fromDate to $toDate"
        val builder = StringBuilder()
        builder.append("*🏭 OVERTIME REPORT*\n")
        builder.append("🗓 Period: $dateRange\n")
        if (weekLabel.isNotEmpty()) {
            builder.append("📋 Week: $weekLabel\n")
        }
        builder.append("\n")

        val logsByWorker = logs.filter { it.date in fromDate..toDate && it.isChecked }.groupBy { it.workerId }
        var workerCount = 0

        workers.forEach { worker ->
            val workerLogs = (logsByWorker[worker.id] ?: emptyList()).sortedBy { it.date }

            if (workerLogs.isNotEmpty()) {
                val isQualitySection = isQualitySection(worker.section)
                val emoji = if (isQualitySection) "🪖" else "👷"
                
                builder.append("$emoji *${worker.name}* | ${worker.section}\n")
                workerLogs.forEach { log ->
                    val noteStr = if (log.note.isNotEmpty()) " | Note: ${log.note}" else ""
                    builder.append("  • Date: ${log.date}$noteStr\n")
                }
                builder.append("\n")

                workerCount++
            }
        }

        builder.append("*Grand Total: $workerCount Workers with OT*")

        val text = builder.toString()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Share OT Report via"))
    }

    private fun isQualitySection(section: String): Boolean {
        val s = section.uppercase(Locale.US)
        return s.contains("QUALITY") || s.contains("QC") || s.contains("Q/C") || 
               s.contains("INSPECTION") || s.contains("LAB") || s.contains("INCOMING")
    }
}
