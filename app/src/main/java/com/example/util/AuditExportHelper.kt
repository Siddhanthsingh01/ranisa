package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.firestore.DocumentSnapshot
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AuditExportHelper {

    private const val PROVIDER_AUTHORITY = "com.aistudio.ranisa.rxbgla.provider"

    /**
     * Helper model representing a sanitized audit log row for rendering or export.
     */
    data class ExportLogItem(
        val logId: String,
        val dateTime: String,
        val action: String,
        val module: String,
        val user: String,
        val role: String,
        val details: String,
        val status: String,
        val firm: String,
        val billNo: String,
        val partyName: String
    )

    fun sanitizeLogs(docs: List<DocumentSnapshot>): List<ExportLogItem> {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return docs.map { doc ->
            val timestamp = doc.getTimestamp("timestamp")
            val formattedTime = if (timestamp != null) {
                sdf.format(timestamp.toDate())
            } else {
                val dateStr = doc.getString("date") ?: ""
                val timeStr = doc.getString("time") ?: doc.getString("timeStr") ?: ""
                if (dateStr.isNotBlank()) "$dateStr $timeStr" else "N/A"
            }

            ExportLogItem(
                logId = doc.getString("logId") ?: doc.id,
                dateTime = formattedTime,
                action = doc.getString("actionType") ?: doc.getString("action") ?: "UNKNOWN",
                module = doc.getString("module") ?: doc.getString("screen") ?: "System",
                user = doc.getString("userName") ?: doc.getString("user") ?: "System",
                role = doc.getString("userRole") ?: doc.getString("role") ?: "User",
                details = doc.getString("recordTitle") ?: doc.getString("description") ?: doc.getString("oldValue") ?: "N/A",
                status = doc.getString("status") ?: "Success",
                firm = doc.getString("firmName") ?: doc.getString("firm") ?: "Global",
                billNo = doc.getString("billNo") ?: "",
                partyName = doc.getString("partyName") ?: ""
            )
        }
    }

    /**
     * Exports logs to a standard, Excel-compatible CSV file and triggers a share action.
     */
    fun exportToExcelCsv(context: Context, docs: List<DocumentSnapshot>) {
        try {
            val logs = sanitizeLogs(docs)
            val csvBuilder = StringBuilder()
            
            // CSV Header with BOM for correct Excel Unicode parsing
            csvBuilder.append('\ufeff')
            csvBuilder.append("Log ID,Date & Time,Action,Module,User,Role,Firm,Bill No,Party,Status,Details\n")
            
            for (log in logs) {
                val escapedDetails = log.details.replace("\"", "\"\"")
                csvBuilder.append("\"${log.logId}\",")
                csvBuilder.append("\"${log.dateTime}\",")
                csvBuilder.append("\"${log.action}\",")
                csvBuilder.append("\"${log.module}\",")
                csvBuilder.append("\"${log.user}\",")
                csvBuilder.append("\"${log.role}\",")
                csvBuilder.append("\"${log.firm}\",")
                csvBuilder.append("\"${log.billNo}\",")
                csvBuilder.append("\"${log.partyName}\",")
                csvBuilder.append("\"${log.status}\",")
                csvBuilder.append("\"$escapedDetails\"\n")
            }

            val file = File(context.cacheDir, "Ranisa_Enterprise_Audit_Logs.csv")
            FileOutputStream(file).use { out ->
                out.write(csvBuilder.toString().toByteArray(Charsets.UTF_8))
            }

            val uri = FileProvider.getUriForFile(context, PROVIDER_AUTHORITY, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/comma-separated-values"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Ranisa Enterprise Audit Registry Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export Audit Log to CSV/Excel"))
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Generates a beautifully formatted PDF report of the audit log history and shares it.
     */
    fun exportToPdf(context: Context, docs: List<DocumentSnapshot>, sharedTitle: String = "Enterprise Global Audit Log Registry") {
        try {
            val logs = sanitizeLogs(docs)
            val pdfDocument = PdfDocument()
            
            // Standard A4 dimensions in pixels at 72 DPI: 595 x 842 (Portrait)
            val pageWidth = 842 // Use landscape for rich table layouts
            val pageHeight = 595
            
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            
            val paintText = Paint().apply {
                color = Color.BLACK
                textSize = 9f
                isAntiAlias = true
            }
            
            val paintHeader = Paint().apply {
                color = Color.rgb(15, 23, 42) // Slate 900
                textSize = 10f
                flags = Paint.SUBPIXEL_TEXT_FLAG
                isAntiAlias = true
            }

            val paintTitle = Paint().apply {
                color = Color.rgb(2, 132, 199) // Sky 600 primary
                textSize = 16f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val paintSubtitle = Paint().apply {
                color = Color.GRAY
                textSize = 9f
                isAntiAlias = true
            }

            val paintBorder = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 0.5f
                style = Paint.Style.STROKE
            }

            val paintRowBg = Paint().apply {
                color = Color.rgb(248, 250, 252) // Alternating row color (Slate 50)
                style = Paint.Style.FILL
            }

            val margin = 40f
            var currentY = 50f
            
            // Draw Header
            fun drawPageTemplate(c: Canvas, num: Int) {
                c.drawRect(margin, 20f, pageWidth - margin, 21f, paintBorder)
                c.drawText("RANISA ENTERPRISE AUDIT AND COMPLIANCE SYSTEM", margin, 35f, paintHeader.apply { textSize = 8f })
                c.drawText("Generated on: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}", pageWidth - margin - 200f, 35f, paintSubtitle)
                
                c.drawText(sharedTitle, margin, 75f, paintTitle)
                c.drawText("Official Append-Only System Registry Log Summary (Page $num)", margin, 92f, paintSubtitle.apply { textSize = 9.5f })
                
                // Table Headers
                val headY = 120f
                c.drawRect(margin, headY - 15f, pageWidth - margin, headY + 10f, Paint().apply { color = Color.rgb(226, 232, 240); style = Paint.Style.FILL }) // slate 200
                c.drawRect(margin, headY - 15f, pageWidth - margin, headY + 10f, paintBorder)

                paintHeader.apply { color = Color.rgb(15, 23, 42); textSize = 9f; isFakeBoldText = true }
                c.drawText("Date & Time", margin + 10f, headY, paintHeader)
                c.drawText("Action", margin + 140f, headY, paintHeader)
                c.drawText("Module", margin + 220f, headY, paintHeader)
                c.drawText("User Profile", margin + 310f, headY, paintHeader)
                c.drawText("Firm context", margin + 450f, headY, paintHeader)
                c.drawText("Status", margin + 550f, headY, paintHeader)
                c.drawText("Log Details / Operational Record", margin + 610f, headY, paintHeader)
            }

            drawPageTemplate(canvas, pageNumber)
            currentY = 145f
            
            val itemHeight = 22f
            val maxLinesPerPage = 18

            for ((index, log) in logs.withIndex()) {
                // Check if page full, wrap to new page
                if (currentY > pageHeight - margin - 30f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawPageTemplate(canvas, pageNumber)
                    currentY = 145f
                }

                // Alternating background
                if (index % 2 == 1) {
                    canvas.drawRect(margin, currentY - 12f, pageWidth - margin, currentY + 10f, paintRowBg)
                }

                paintText.color = Color.BLACK
                paintText.textSize = 8.5f
                
                canvas.drawText(log.dateTime, margin + 10f, currentY, paintText)
                
                // Color code actions
                val actionColor = when {
                    log.action.contains("DELETE", true) -> Color.rgb(220, 38, 38)
                    log.action.contains("UPDATE", true) -> Color.rgb(217, 119, 6)
                    log.action.contains("CREATE", true) -> Color.rgb(13, 148, 136)
                    else -> Color.rgb(71, 85, 105)
                }
                
                canvas.drawText(log.action, margin + 140f, currentY, paintText.apply { color = actionColor; isFakeBoldText = true })
                canvas.drawText(log.module, margin + 220f, currentY, paintText.apply { color = Color.BLACK; isFakeBoldText = false })
                canvas.drawText("${log.user} (${log.role})", margin + 310f, currentY, paintText)
                canvas.drawText(log.firm, margin + 450f, currentY, paintText)
                
                val statusColor = if (log.status.equals("Success", true)) Color.rgb(16, 185, 129) else Color.rgb(239, 68, 68)
                canvas.drawText(log.status.uppercase(), margin + 550f, currentY, paintText.apply { color = statusColor; isFakeBoldText = true })
                
                val detailCut = if (log.details.length > 38) log.details.take(35) + "..." else log.details
                canvas.drawText(detailCut, margin + 610f, currentY, paintText.apply { color = Color.BLACK; isFakeBoldText = false })

                // Draw cell boundary line
                canvas.drawLine(margin, currentY + 10f, pageWidth - margin, currentY + 10f, paintBorder)

                currentY += itemHeight
            }

            pdfDocument.finishPage(page)
            
            val file = File(context.cacheDir, "Ranisa_Enterprise_Audit_Report.pdf")
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(context, PROVIDER_AUTHORITY, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Ranisa Audit Compliance Report PDF")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Compliance PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "PDF Generation failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Triggers a native system print flow for the generated audit log PDF.
     */
    fun printAuditLog(context: Context, docs: List<DocumentSnapshot>) {
        try {
            // First generate the PDF
            val logs = sanitizeLogs(docs)
            val pdfDocument = PdfDocument()
            val pageWidth = 842
            val pageHeight = 595
            
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            
            val paintText = Paint().apply { color = Color.BLACK; textSize = 9f; isAntiAlias = true }
            val paintHeader = Paint().apply { color = Color.rgb(15, 23, 42); textSize = 10f; isAntiAlias = true }
            val paintTitle = Paint().apply { color = Color.rgb(2, 132, 199); textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
            val paintSubtitle = Paint().apply { color = Color.GRAY; textSize = 9f; isAntiAlias = true }
            val paintBorder = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f; style = Paint.Style.STROKE }
            val paintRowBg = Paint().apply { color = Color.rgb(248, 250, 252); style = Paint.Style.FILL }

            val margin = 40f
            var currentY = 145f
            
            fun drawPageTemplate(c: Canvas, num: Int) {
                c.drawRect(margin, 20f, pageWidth - margin, 21f, paintBorder)
                c.drawText("RANISA ENTERPRISE AUDIT AND COMPLIANCE SYSTEM", margin, 35f, paintHeader.apply { textSize = 8f })
                c.drawText("Generated on: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}", pageWidth - margin - 200f, 35f, paintSubtitle)
                c.drawText("Ranisa Global Compliance Registry Logs", margin, 75f, paintTitle)
                c.drawText("Print-Ready Authorized Log History Overview (Page $num)", margin, 92f, paintSubtitle.apply { textSize = 9.5f })
                
                val headY = 120f
                c.drawRect(margin, headY - 15f, pageWidth - margin, headY + 10f, Paint().apply { color = Color.rgb(226, 232, 240); style = Paint.Style.FILL })
                c.drawRect(margin, headY - 15f, pageWidth - margin, headY + 10f, paintBorder)

                paintHeader.apply { color = Color.rgb(15, 23, 42); textSize = 9f; isFakeBoldText = true }
                c.drawText("Date & Time", margin + 10f, headY, paintHeader)
                c.drawText("Action", margin + 140f, headY, paintHeader)
                c.drawText("Module", margin + 220f, headY, paintHeader)
                c.drawText("User Profile", margin + 310f, headY, paintHeader)
                c.drawText("Firm context", margin + 450f, headY, paintHeader)
                c.drawText("Status", margin + 550f, headY, paintHeader)
                c.drawText("Log Details / Operational Record", margin + 610f, headY, paintHeader)
            }

            drawPageTemplate(canvas, pageNumber)
            
            val itemHeight = 22f
            for ((index, log) in logs.withIndex()) {
                if (currentY > pageHeight - margin - 30f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawPageTemplate(canvas, pageNumber)
                    currentY = 145f
                }

                if (index % 2 == 1) {
                    canvas.drawRect(margin, currentY - 12f, pageWidth - margin, currentY + 10f, paintRowBg)
                }

                paintText.color = Color.BLACK
                paintText.textSize = 8.5f
                
                canvas.drawText(log.dateTime, margin + 10f, currentY, paintText)
                val actionColor = when {
                    log.action.contains("DELETE", true) -> Color.rgb(220, 38, 38)
                    log.action.contains("UPDATE", true) -> Color.rgb(217, 119, 6)
                    log.action.contains("CREATE", true) -> Color.rgb(13, 148, 136)
                    else -> Color.rgb(71, 85, 105)
                }
                canvas.drawText(log.action, margin + 140f, currentY, paintText.apply { color = actionColor; isFakeBoldText = true })
                canvas.drawText(log.module, margin + 220f, currentY, paintText.apply { color = Color.BLACK; isFakeBoldText = false })
                canvas.drawText("${log.user} (${log.role})", margin + 310f, currentY, paintText)
                canvas.drawText(log.firm, margin + 450f, currentY, paintText)
                val statusColor = if (log.status.equals("Success", true)) Color.rgb(16, 185, 129) else Color.rgb(239, 68, 68)
                canvas.drawText(log.status.uppercase(), margin + 550f, currentY, paintText.apply { color = statusColor; isFakeBoldText = true })
                
                val detailCut = if (log.details.length > 38) log.details.take(35) + "..." else log.details
                canvas.drawText(detailCut, margin + 610f, currentY, paintText.apply { color = Color.BLACK; isFakeBoldText = false })

                canvas.drawLine(margin, currentY + 10f, pageWidth - margin, currentY + 10f, paintBorder)
                currentY += itemHeight
            }

            pdfDocument.finishPage(page)
            
            val file = File(context.cacheDir, "Ranisa_Audit_Print_Doc.pdf")
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            // Trigger standard system Print Job
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "Ranisa_Audit_Compliance_Report_Job"
            val printAdapter = object : android.print.PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: android.os.Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = android.print.PrintDocumentInfo.Builder(jobName)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(pageNumber)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: android.os.ParcelFileDescriptor?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        val input = java.io.FileInputStream(file)
                        val output = java.io.FileOutputStream(destination?.fileDescriptor)
                        val buf = ByteArray(16384)
                        var bytesRead: Int
                        while (input.read(buf).also { bytesRead = it } >= 0) {
                            output.write(buf, 0, bytesRead)
                        }
                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                        input.close()
                        output.close()
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            Toast.makeText(context, "Printing failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
