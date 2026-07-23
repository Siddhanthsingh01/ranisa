package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.ContractBill
import com.example.data.ContractItem
import com.example.data.Payment
import com.example.data.getItemsForBill
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

object PdfGenerator {

    data class BrokerageFirmProfile(
        val id: String,
        val name: String,
        val title: String,
        val subtitle: String = "CANVASSING AGENT",
        val jurisdiction: String = "Subject to Raichur Jurisdiction",
        val topMantra: String = "\"SRI GANESHAYA NAMAH\"",
        val leftMantra: String,
        val cellText1: String,
        val cellText2: String = "",
        val address: String = "Shayamrao Complex, Beside Vaikuntam Complex Gunj Road, RAICHUR - 584102. (Karnataka)",
        val gstNo: String = "",
        val sigLabel: String
    )

    fun getFirmProfile(context: Context, rawFirmName: String?): BrokerageFirmProfile {
        val prefs = context.getSharedPreferences("ranisa_prefs", Context.MODE_PRIVATE)
        val activeFirmName = prefs.getString("current_firm_name", "Lalit Rice Broker") ?: "Lalit Rice Broker"

        val target = rawFirmName?.trim()?.ifBlank { activeFirmName } ?: activeFirmName

        val isKrishna = target.contains("Krishna", ignoreCase = true) || target.contains("f002", ignoreCase = true)
        val isLalit = target.contains("Lalit", ignoreCase = true) || target.contains("f001", ignoreCase = true)

        return when {
            isKrishna -> {
                BrokerageFirmProfile(
                    id = "F002",
                    name = "Hare Krishna Rice Broker",
                    title = "HARE KRISHNA RICE BROKER",
                    subtitle = "CANVASSING AGENT",
                    jurisdiction = "Subject to Raichur Jurisdiction",
                    topMantra = "\"SRI GANESHAYA NAMAH\"",
                    leftMantra = "\"HARE KRISHNA\"",
                    cellText1 = "Cell : 98866 12345(H)",
                    cellText2 = "94484 55041(P)",
                    address = "Shayamrao Complex, Beside Vaikuntam Complex Gunj Road, RAICHUR - 584102. (Karnataka)",
                    gstNo = prefs.getString("gst_F002", "") ?: "",
                    sigLabel = "For : HARE KRISHNA RICE BROKER"
                )
            }
            isLalit -> {
                BrokerageFirmProfile(
                    id = "F001",
                    name = "Lalit Rice Broker",
                    title = "LALIT RICE BROKER",
                    subtitle = "CANVASSING AGENT",
                    jurisdiction = "Subject to Raichur Jurisdiction",
                    topMantra = "\"SRI GANESHAYA NAMAH\"",
                    leftMantra = "\"SRI KHANDERI MATAJI NAMAH\"",
                    cellText1 = "Cell : 92432 34814(L)",
                    cellText2 = "94484 55041(P)",
                    address = "Shayamrao Complex, Beside Vaikuntam Complex Gunj Road, RAICHUR - 584102. (Karnataka)",
                    gstNo = prefs.getString("gst_F001", "") ?: "",
                    sigLabel = "For : LALIT RICE BROKER"
                )
            }
            else -> {
                val cleanTitle = target.uppercase()
                val formattedTitle = if (cleanTitle.contains("BROKER")) cleanTitle else "$cleanTitle RICE BROKER"
                BrokerageFirmProfile(
                    id = target,
                    name = target,
                    title = formattedTitle,
                    subtitle = "CANVASSING AGENT",
                    jurisdiction = "Subject to Raichur Jurisdiction",
                    topMantra = "\"SRI GANESHAYA NAMAH\"",
                    leftMantra = "\"SRI GANESHAYA NAMAH\"",
                    cellText1 = "Cell : 94484 55041",
                    cellText2 = "",
                    address = "Shayamrao Complex, Beside Vaikuntam Complex Gunj Road, RAICHUR - 584102. (Karnataka)",
                    gstNo = "",
                    sigLabel = "For : $formattedTitle"
                )
            }
        }
    }

    fun generatePaymentPdf(context: Context, payment: Payment, bill: ContractBill): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()

        // 1. Draw outer double borders (like old paper registers)
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(20f, 20f, 575f, 822f, paint)

        paint.strokeWidth = 0.5f
        canvas.drawRect(24f, 24f, 571f, 818f, paint)

        val profile = getFirmProfile(context, payment.firm.ifBlank { bill.firmName })

        // 2. Header info
        paint.style = Paint.Style.FILL
        paint.textSize = 8f
        paint.isFakeBoldText = true
        
        // Subject to Jurisdiction
        canvas.drawText(profile.jurisdiction, (595f - paint.measureText(profile.jurisdiction)) / 2, 38f, paint)
        
        // Top invocation mantras
        canvas.drawText(profile.topMantra, (595f - paint.measureText(profile.topMantra)) / 2, 50f, paint)
        
        canvas.drawText(profile.leftMantra, 30f, 62f, paint)
        
        paint.isFakeBoldText = false
        canvas.drawText(profile.cellText1, 565f - paint.measureText(profile.cellText1), 50f, paint)
        if (profile.cellText2.isNotBlank()) {
            canvas.drawText(profile.cellText2, 565f - paint.measureText(profile.cellText2), 62f, paint)
        }

        // Firm Main Title
        paint.textSize = 24f
        paint.isFakeBoldText = true
        val mainTitle = profile.title
        val titleWidth = paint.measureText(mainTitle)
        canvas.drawText(mainTitle, (595f - titleWidth) / 2, 88f, paint)

        // Subtitle
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText(profile.subtitle, (595f - paint.measureText(profile.subtitle)) / 2, 102f, paint)

        // Address
        paint.textSize = 8.5f
        paint.isFakeBoldText = false
        canvas.drawText(profile.address, (595f - paint.measureText(profile.address)) / 2, 115f, paint)

        // Payment Receipt Oval Banner
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(220f, 125f, 375f, 142f, 8f, 8f, paint)
        
        paint.style = Paint.Style.FILL
        paint.textSize = 9f
        paint.isFakeBoldText = true
        canvas.drawText("PAYMENT RECEIPT", (595f - paint.measureText("PAYMENT RECEIPT")) / 2, 137f, paint)

        // 3. Receipt Fields
        paint.style = Paint.Style.FILL
        paint.textSize = 11f
        paint.isFakeBoldText = false
        
        var y = 180f
        val lineSpacing = 28f
        
        paint.isFakeBoldText = true
        canvas.drawText("Receipt Date: ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText(formatDateToDdMmYyyy(payment.date), 140f, y, paint)
        
        paint.isFakeBoldText = true
        canvas.drawText("Receipt ID: ", 380f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText(payment.paymentId.take(12).uppercase(), 460f, y, paint)
        
        y += lineSpacing
        paint.isFakeBoldText = true
        canvas.drawText("Bill Number: ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText(payment.billNo, 140f, y, paint)
        
        paint.isFakeBoldText = true
        canvas.drawText("Bill Date: ", 380f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText(formatDateToDdMmYyyy(bill.date), 460f, y, paint)

        y += lineSpacing
        paint.isFakeBoldText = true
        canvas.drawText("Party (Buyer): ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText(payment.buyerName, 140f, y, paint)

        y += lineSpacing
        paint.isFakeBoldText = true
        canvas.drawText("Party (Seller): ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText(payment.sellerName, 140f, y, paint)
        
        y += lineSpacing
        paint.isFakeBoldText = true
        canvas.drawText("Firm Name: ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText(payment.firm, 140f, y, paint)

        // Draw a divider line
        y += 15f
        paint.strokeWidth = 1f
        paint.color = Color.BLACK
        canvas.drawLine(40f, y, 555f, y, paint)
        
        // Receipt Details Section
        y += 25f
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("PAYMENT TRANSACTION DETAILS", 40f, y, paint)
        
        paint.textSize = 10.5f
        y += 25f
        paint.isFakeBoldText = true
        canvas.drawText("Amount Paid: ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText("₹${String.format("%.2f", payment.amount)}", 160f, y, paint)
        
        paint.isFakeBoldText = true
        val words = convertNumberToWords(payment.amount)
        canvas.drawText("Amount in Words: ", 40f, y + 18f, paint)
        paint.isFakeBoldText = false
        canvas.drawText("$words Rupees Only", 160f, y + 18f, paint)
        
        y += 40f
        paint.isFakeBoldText = true
        canvas.drawText("Payment Mode: ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText(payment.paymentMode.ifBlank { "Direct Entry" }, 160f, y, paint)
        
        y += 20f
        paint.isFakeBoldText = true
        canvas.drawText("Discount: ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText("₹${String.format("%.2f", payment.discountAmount)}", 160f, y, paint)
        
        y += 20f
        paint.isFakeBoldText = true
        canvas.drawText("Commission: ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText("₹${String.format("%.2f", payment.commissionAmount)}", 160f, y, paint)
        
        y += 20f
        paint.isFakeBoldText = true
        canvas.drawText("Remark Amt: ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText(if (payment.remarks1.isNotBlank()) "₹${payment.remarks1}" else "₹0.00", 160f, y, paint)
        
        y += 20f
        paint.isFakeBoldText = true
        canvas.drawText("Remark: ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText(payment.remarks2.ifBlank { "-" }, 160f, y, paint)
        
        y += 20f
        paint.isFakeBoldText = true
        canvas.drawText("Transaction Note: ", 40f, y, paint)
        paint.isFakeBoldText = false
        canvas.drawText(payment.remarks.ifBlank { "-" }, 160f, y, paint)

        // Bill Summary Box
        y += 40f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(40f, y, 555f, y + 120f, paint)
        
        paint.style = Paint.Style.FILL
        paint.isFakeBoldText = true
        canvas.drawText("BILL AND BALANCE SUMMARY", 50f, y + 20f, paint)
        canvas.drawLine(40f, y + 28f, 555f, y + 28f, paint)
        
        paint.isFakeBoldText = false
        canvas.drawText("Bill Amount:", 55f, y + 45f, paint)
        canvas.drawText("₹${String.format("%.2f", bill.billAmount)}", 400f, y + 45f, paint)
        
        canvas.drawText("Total Paid Amount (cumulative):", 55f, y + 65f, paint)
        canvas.drawText("₹${String.format("%.2f", bill.totalReceived)}", 400f, y + 65f, paint)
        
        canvas.drawText("Balance Amount:", 55f, y + 85f, paint)
        paint.isFakeBoldText = true
        canvas.drawText("₹${String.format("%.2f", bill.balance)}", 400f, y + 85f, paint)
        
        // Footer Signatures
        y += 100f
        paint.isFakeBoldText = true
        paint.textSize = 10f
        canvas.drawText("Receiver's Signature", 40f, y, paint)
        canvas.drawText(profile.sigLabel, 360f, y, paint)
        
        paint.isFakeBoldText = false
        canvas.drawText("(Authorized Signatory)", 390f, y + 25f, paint)

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "Payment_Receipt_${payment.paymentId.take(8)}.pdf")
        try {
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
        return file
    }

    fun generateContractPdf(context: Context, bill: ContractBill): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()

        // 1. Draw outer double borders (like old paper registers)
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(20f, 20f, 575f, 822f, paint)

        paint.strokeWidth = 0.5f
        canvas.drawRect(24f, 24f, 571f, 818f, paint)

        val profile = getFirmProfile(context, bill.firmName)

        // 2. Header info
        paint.style = Paint.Style.FILL
        paint.textSize = 8f
        paint.isFakeBoldText = true
        
        // Subject to Jurisdiction
        canvas.drawText(profile.jurisdiction, (595f - paint.measureText(profile.jurisdiction)) / 2, 38f, paint)
        
        // Top invocation mantras
        canvas.drawText(profile.topMantra, (595f - paint.measureText(profile.topMantra)) / 2, 50f, paint)
        
        canvas.drawText(profile.leftMantra, 30f, 62f, paint)
        
        paint.isFakeBoldText = false
        canvas.drawText(profile.cellText1, 565f - paint.measureText(profile.cellText1), 50f, paint)
        if (profile.cellText2.isNotBlank()) {
            canvas.drawText(profile.cellText2, 565f - paint.measureText(profile.cellText2), 62f, paint)
        }

        // Firm Main Title
        paint.textSize = 24f
        paint.isFakeBoldText = true
        val mainTitle = profile.title
        val titleWidth = paint.measureText(mainTitle)
        canvas.drawText(mainTitle, (595f - titleWidth) / 2, 88f, paint)

        // Subtitle
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText(profile.subtitle, (595f - paint.measureText(profile.subtitle)) / 2, 102f, paint)

        // Address
        paint.textSize = 8.5f
        paint.isFakeBoldText = false
        canvas.drawText(profile.address, (595f - paint.measureText(profile.address)) / 2, 115f, paint)

        // Contract Form Oval Banner
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(240f, 125f, 355f, 142f, 8f, 8f, paint)
        
        paint.style = Paint.Style.FILL
        paint.textSize = 9f
        paint.isFakeBoldText = true
        canvas.drawText("CONTRACT FORM", (595f - paint.measureText("CONTRACT FORM")) / 2, 137f, paint)

        // 3. Document Fields Area (Bill No., Date, Sellers, Buyers, GST No.)
        paint.style = Paint.Style.FILL
        paint.textSize = 10f
        
        // Row 1: Bill No. and Date
        paint.isFakeBoldText = true
        canvas.drawText("No. ", 35f, 165f, paint)
        
        // Render generated bill number in bold red to look authentic like the stamp!
        val oldColor = paint.color
        paint.color = Color.rgb(211, 47, 47) // red
        paint.textSize = 12f
        canvas.drawText(bill.billNumber, 60f, 165f, paint)
        
        paint.color = oldColor
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText("Date: ", 380f, 165f, paint)
        
        paint.isFakeBoldText = false
        canvas.drawText(formatDateToDdMmYyyy(bill.date), 415f, 165f, paint)
        // Underline for Date field
        paint.strokeWidth = 0.5f
        canvas.drawLine(410f, 168f, 555f, 168f, paint)

        // Row 2: Sellers M/s
        paint.isFakeBoldText = true
        canvas.drawText("Sellers M/s.:", 35f, 182f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(bill.sellerName, 105f, 182f, paint)
        paint.strokeWidth = 0.5f
        canvas.drawLine(100f, 185f, 565f, 185f, paint)

        // Seller Address immediately below Seller Name
        paint.isFakeBoldText = true
        canvas.drawText("Address:", 35f, 198f, paint)
        paint.isFakeBoldText = false
        val cleanSellerAddress = bill.sellerAddress.replace("\n", ", ")
        canvas.drawText(cleanSellerAddress, 105f, 198f, paint)
        canvas.drawLine(100f, 201f, 565f, 201f, paint)

        // Row 3: Buyers M/s
        paint.isFakeBoldText = true
        canvas.drawText("Buyers M/s.:", 35f, 218f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(bill.buyerName, 105f, 218f, paint)
        canvas.drawLine(100f, 221f, 565f, 221f, paint)

        // Buyer Address immediately below Buyer Name
        paint.isFakeBoldText = true
        canvas.drawText("Address:", 35f, 234f, paint)
        paint.isFakeBoldText = false
        val cleanBuyerAddress = bill.buyerAddress.replace("\n", ", ")
        canvas.drawText(cleanBuyerAddress, 105f, 234f, paint)
        canvas.drawLine(100f, 237f, 565f, 237f, paint)

        // Row 4: GST No
        paint.isFakeBoldText = true
        canvas.drawText("GST No. ", 35f, 252f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(bill.gstNo, 85f, 252f, paint)
        canvas.drawLine(80f, 255f, 565f, 255f, paint)

        // 4. Main Particulars Table
        val tableTop = 265f
        val tableHeaderBottom = 285f
        val tableBottom = 415f

        // Draw Table Outline Box
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawRect(30f, tableTop, 565f, tableBottom, paint)

        // Header separator line
        canvas.drawLine(30f, tableHeaderBottom, 565f, tableHeaderBottom, paint)

        // Column dividers (incorporating a separate Packing column, Amount removed)
        canvas.drawLine(260f, tableTop, 260f, tableBottom, paint) // Item to Bags
        canvas.drawLine(325f, tableTop, 325f, tableBottom, paint) // Bags to Packaging
        canvas.drawLine(415f, tableTop, 415f, tableBottom, paint) // Packaging to Qtls
        canvas.drawLine(490f, tableTop, 490f, tableBottom, paint) // Qtls to Rate

        // Header labels
        paint.style = Paint.Style.FILL
        paint.isFakeBoldText = true
        paint.textSize = 9.5f
        canvas.drawText("Item", (30f + 260f) / 2f - paint.measureText("Item") / 2f, tableTop + 13f, paint)
        canvas.drawText("Bags", (260f + 325f) / 2f - paint.measureText("Bags") / 2f, tableTop + 13f, paint)
        canvas.drawText("Packaging", (325f + 415f) / 2f - paint.measureText("Packaging") / 2f, tableTop + 13f, paint)
        canvas.drawText("Qtls", (415f + 490f) / 2f - paint.measureText("Qtls") / 2f, tableTop + 13f, paint)
        canvas.drawText("Rate", (490f + 565f) / 2f - paint.measureText("Rate") / 2f, tableTop + 13f, paint)

        // Row values
        paint.isFakeBoldText = false
        val billItems = getItemsForBill(bill)
        var currentY = tableHeaderBottom + 16f
        billItems.forEach { item ->
            canvas.drawText(item.particulars, 38f, currentY, paint)
            
            val bagsText = if (item.bags > 0) item.bags.toString() else ""
            canvas.drawText(bagsText, (260f + 325f) / 2f - paint.measureText(bagsText) / 2f, currentY, paint)
            
            val packingText = item.packing.ifBlank { bill.packing }
            canvas.drawText(packingText, (325f + 415f) / 2f - paint.measureText(packingText) / 2f, currentY, paint)
            
            val qtlsText = if (item.qtls > 0.0) String.format(Locale.getDefault(), "%.2f", item.qtls) else ""
            canvas.drawText(qtlsText, (415f + 490f) / 2f - paint.measureText(qtlsText) / 2f, currentY, paint)
            
            val rateText = if (item.rate > 0.0) String.format(Locale.getDefault(), "%.2f", item.rate) else ""
            canvas.drawText(rateText, (490f + 565f) / 2f - paint.measureText(rateText) / 2f, currentY, paint)
            
            currentY += 15f
        }

        // 5. Grid of 4x2 key-value entries (Below table)
        val gridTop = tableBottom
        val gridBottom = gridTop + 100f // 25f per row * 4
        
        // Draw grid boundaries
        paint.style = Paint.Style.STROKE
        canvas.drawRect(30f, gridTop, 565f, gridBottom, paint)
        
        // Horizontal row dividers
        canvas.drawLine(30f, gridTop + 25f, 565f, gridTop + 25f, paint)
        canvas.drawLine(30f, gridTop + 50f, 565f, gridTop + 50f, paint)
        canvas.drawLine(30f, gridTop + 75f, 565f, gridTop + 75f, paint)
        
        // Vertical divider at center
        val centerGridX = 295f
        canvas.drawLine(centerGridX, gridTop, centerGridX, gridBottom, paint)

        // Render cell contents
        paint.style = Paint.Style.FILL
        paint.textSize = 9f
        
        // Row 1: Total Qtls & Transport
        paint.isFakeBoldText = true
        canvas.drawText("Total Qtls : ", 35f, gridTop + 16f, paint)
        paint.isFakeBoldText = false
        val totalQtlsValue = billItems.sumOf { it.qtls }
        val totalQtlsText = String.format(Locale.getDefault(), "%.2f", totalQtlsValue)
        canvas.drawText(totalQtlsText, 95f, gridTop + 16f, paint)

        paint.isFakeBoldText = true
        canvas.drawText("Transport : ", centerGridX + 8f, gridTop + 16f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(bill.transport, centerGridX + 68f, gridTop + 16f, paint)

        // Row 2: Delivery & Lorry No.
        paint.isFakeBoldText = true
        canvas.drawText("Delivery : ", 35f, gridTop + 41f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(bill.delivery, 82f, gridTop + 41f, paint)

        paint.isFakeBoldText = true
        canvas.drawText("Lorry No. : ", centerGridX + 8f, gridTop + 41f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(bill.lorryNo, centerGridX + 68f, gridTop + 41f, paint)

        // Row 3: Payment & Mobile No.
        paint.isFakeBoldText = true
        canvas.drawText("Payment : ", 35f, gridTop + 66f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(bill.payment, 82f, gridTop + 66f, paint)

        paint.isFakeBoldText = true
        canvas.drawText("Mobile No. : ", centerGridX + 8f, gridTop + 66f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(bill.mobileNo, centerGridX + 72f, gridTop + 66f, paint)

        // Row 4: Brand & Lorry Freight
        paint.isFakeBoldText = true
        canvas.drawText("Brand : ", 35f, gridTop + 91f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(bill.brand, 72f, gridTop + 91f, paint)

        paint.isFakeBoldText = true
        canvas.drawText("Lorry Freight : ", centerGridX + 8f, gridTop + 91f, paint)
        paint.isFakeBoldText = false
        val freightText = if (bill.lorryFreight > 0) String.format(Locale.getDefault(), "₹ %.2f", bill.lorryFreight) else ""
        canvas.drawText(freightText, centerGridX + 85f, gridTop + 91f, paint)

        // 6. Amount in words line
        val wordsY = gridBottom + 25f
        paint.isFakeBoldText = true
        canvas.drawText("In Words : ", 35f, wordsY, paint)
        paint.isFakeBoldText = false
        val freightInWords = convertNumberToWords(bill.lorryFreight.toDouble())
        canvas.drawText(freightInWords, 90f, wordsY, paint)
        paint.strokeWidth = 0.5f
        canvas.drawLine(85f, wordsY + 3f, 565f, wordsY + 3f, paint)

        // 7. Signature Grid at bottom
        val sigTop = wordsY + 25f
        val sigBottom = sigTop + 60f
        
        // Outline box
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(30f, sigTop, 565f, sigBottom, paint)
        
        // Vertical dividers
        canvas.drawLine(185f, sigTop, 185f, sigBottom, paint)
        canvas.drawLine(320f, sigTop, 320f, sigBottom, paint)

        // Cell Labels
        paint.style = Paint.Style.FILL
        paint.textSize = 9.5f
        paint.isFakeBoldText = true
        
        // Cell 1: Seller's Signature
        canvas.drawText("Seller's Signature", 35f, sigTop + 15f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(bill.sellerSignature, 35f, sigTop + 40f, paint)

        // Cell 2: Credit Days
        paint.isFakeBoldText = true
        canvas.drawText("Credit Days", 200f, sigTop + 15f, paint)
        paint.isFakeBoldText = false
        val creditText = if (bill.creditDays > 0) bill.creditDays.toString() else "N/A"
        canvas.drawText(creditText, 200f, sigTop + 40f, paint)

        // Cell 3: Authorized Signatory
        paint.isFakeBoldText = true
        val sigForLabel = profile.sigLabel
        canvas.drawText(sigForLabel, 335f, sigTop + 15f, paint)
        
        // Authorized brokerage signature placeholder
        paint.textSize = 8f
        paint.isFakeBoldText = false
        canvas.drawText("Authorized Signatory", 400f, sigBottom - 8f, paint)

        // 8. Dynamic Legal Disclaimer Notice (like actual bills) - REMOVED

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "Ranisa_Contract_${bill.billNumber}.pdf")
        val fos = FileOutputStream(file)
        pdfDocument.writeTo(fos)
        pdfDocument.close()
        fos.close()
        return file
    }

    fun sharePdf(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "com.aistudio.ranisa.rxbgla.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Contract PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun printPdf(context: Context, file: File) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val builder = PrintDocumentInfo.Builder(file.name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                    callback?.onLayoutFinished(builder.build(), true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        val input = FileInputStream(file)
                        val output = FileOutputStream(destination?.fileDescriptor)
                        val buf = ByteArray(1024)
                        var bytesRead: Int
                        while (input.read(buf).also { bytesRead = it } > 0) {
                            output.write(buf, 0, bytesRead)
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        input.close()
                        output.close()
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }
            printManager.print("Ranisa_Contract_Printing", printAdapter, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Error printing PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatDateToDdMmYyyy(dateStr: String): String {
        return try {
            val parts = dateStr.split("-")
            if (parts.size == 3 && parts[0].length == 4) {
                "${parts[2]}-${parts[1]}-${parts[0]}"
            } else {
                dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun convertNumberToWords(number: Double): String {
        val num = number.toLong()
        if (num <= 0L) return "Zero Rupees Only"
        
        val units = arrayOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
        val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")
        
        fun convertLessThanThousand(n: Int): String {
            var str = ""
            if (n >= 100) {
                str += units[n / 100] + " Hundred "
                str += convertLessThanThousand(n % 100)
            } else if (n >= 20) {
                str += tens[n / 10] + " " + units[n % 10]
            } else if (n > 0) {
                str += units[n]
            }
            return str.trim()
        }
        
        var temp = num
        var result = ""
        
        val crore = temp / 10000000
        temp %= 10000000
        
        val lakh = temp / 100000
        temp %= 100000
        
        val thousand = temp / 1000
        temp %= 1000
        
        val hundred = temp
        
        if (crore > 0) {
            result += convertLessThanThousand(crore.toInt()) + " Crore "
        }
        if (lakh > 0) {
            result += convertLessThanThousand(lakh.toInt()) + " Lakh "
        }
        if (thousand > 0) {
            result += convertLessThanThousand(thousand.toInt()) + " Thousand "
        }
        if (hundred > 0) {
            result += convertLessThanThousand(hundred.toInt())
        }
        
        return "${result.trim()} Rupees Only"
    }

    fun generateFullLedgerPdf(
        context: Context,
        ledgerName: String,
        ledgerType: String,
        firmName: String,
        bills: List<ContractBill>,
        payments: List<Payment>,
        dateRangeText: String,
        selectedColumns: List<String>,
        orientation: String, // "Portrait" or "Landscape"
        paperSize: String, // "A4" or "Letter"
        fontSize: String, // "Small", "Medium", "Large"
        repeatHeader: Boolean,
        showSummary: Boolean,
        showPageNumbers: Boolean,
        autoFit: Boolean
    ): File? {
        try {
            // 1. Determine orientation and dimensions
            // Automatically switch to Landscape if there are more than 7 columns selected
            var isPortrait = orientation.equals("Portrait", ignoreCase = true)
            if (selectedColumns.size > 7) {
                isPortrait = false
            }
            val isA4 = paperSize.equals("A4", ignoreCase = true)
            
            val pageWidth = if (isA4) {
                if (isPortrait) 595 else 842
            } else {
                if (isPortrait) 612 else 792
            }
            
            val pageHeight = if (isA4) {
                if (isPortrait) 842 else 595
            } else {
                if (isPortrait) 792 else 612
            }
            
            // Determine starting font scales
            val fontScale = when (fontSize.lowercase()) {
                "small" -> 0.8f
                "large" -> 1.2f
                else -> 1.0f // medium
            }
            
            val titleSize = 16f * fontScale
            val labelSize = 10f * fontScale
            val textDetailSize = 9f * fontScale
            
            val leftMargin = 30f
            val rightMargin = 30f
            val topMargin = 40f
            val bottomMargin = 40f
            val availableWidth = pageWidth - leftMargin - rightMargin
            
            val pdfDocument = PdfDocument()
            val paint = Paint()
            
            // Col weights based on standard columns
            val colWeights = mapOf(
                "Date" to 1.0f,
                "Bill No." to 0.8f,
                "Party Name" to 1.8f,
                "Place" to 1.0f,
                "Brand" to 1.0f,
                "Qtls" to 0.8f,
                "Rate" to 0.8f,
                "Bill Amount" to 1.1f,
                "Received Amount" to 1.1f,
                "Balance Amount" to 1.1f,
                "Status" to 0.9f,
                "EB" to 0.8f,
                "Lorry Freight" to 0.9f,
                "Credit Days" to 0.7f,
                "Bank / DD Details" to 1.5f,
                "Remarks" to 1.5f
            )

            val colMinWidths = mapOf(
                "Date" to 50f,
                "Bill No." to 40f,
                "Party Name" to 90f,
                "Place" to 50f,
                "Brand" to 50f,
                "Qtls" to 40f,
                "Rate" to 40f,
                "Bill Amount" to 55f,
                "Received Amount" to 55f,
                "Balance Amount" to 55f,
                "Status" to 45f,
                "EB" to 40f,
                "Lorry Freight" to 45f,
                "Credit Days" to 35f,
                "Bank / DD Details" to 75f,
                "Remarks" to 75f
            )

            // Dynamic font sizing based on column space
            var currentScale = fontScale
            var tempTableRowSize = 8f * currentScale
            var tempTableHeaderSize = 9f * currentScale

            fun getMinWidthForCol(col: String, rSize: Float): Float {
                val baseMin = colMinWidths[col] ?: 50f
                return baseMin * (rSize / 8f)
            }

            var totalMinWidth = selectedColumns.sumOf { getMinWidthForCol(it, tempTableRowSize).toDouble() }.toFloat()

            // If columns exceed the page width, reduce the font size down to minimum of 7pt (7f)
            while (totalMinWidth > availableWidth && tempTableRowSize > 7f) {
                tempTableRowSize -= 0.2f
                tempTableHeaderSize = tempTableRowSize + 1f
                totalMinWidth = selectedColumns.sumOf { getMinWidthForCol(it, tempTableRowSize).toDouble() }.toFloat()
            }

            if (tempTableRowSize < 7f) {
                tempTableRowSize = 7f
                tempTableHeaderSize = 8f
            }

            val tableRowSize = tempTableRowSize
            val tableHeaderSize = tempTableHeaderSize
            val rowHeightFactor = 1.3f
            val baseRowHeight = 16f * (tableRowSize / 8f) * rowHeightFactor
            
            // Calculate widths based on remaining/available width
            val minWidths = selectedColumns.map { getMinWidthForCol(it, tableRowSize) }
            val sumMinWidths = minWidths.sum()

            val colWidths = if (sumMinWidths >= availableWidth) {
                // Scale down minimum widths to fit exactly
                selectedColumns.mapIndexed { idx, _ ->
                    (availableWidth * minWidths[idx]) / sumMinWidths
                }
            } else {
                // Distribute extra width proportionally based on weights
                val extraWidth = availableWidth - sumMinWidths
                val selectedWeights = selectedColumns.map { colWeights[it] ?: 1.0f }
                val sumWeights = selectedWeights.sum()
                selectedColumns.mapIndexed { idx, col ->
                    val minW = minWidths[idx]
                    val weight = selectedWeights[idx]
                    minW + (extraWidth * weight) / sumWeights
                }
            }
            
            // Prepare pages and draw
            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            
            // Helper function to draw background frame/borders (professional style)
            fun drawBorders(c: Canvas) {
                paint.color = Color.parseColor("#4A3B60") // Purple accent
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                c.drawRect(leftMargin - 10f, topMargin - 10f, pageWidth - rightMargin + 10f, pageHeight - bottomMargin + 10f, paint)
            }
            
            // Helper to draw Footer
            fun drawFooter(c: Canvas, pNum: Int) {
                paint.style = Paint.Style.FILL
                paint.color = Color.GRAY
                paint.textSize = 8f
                paint.isFakeBoldText = false
                
                val footerText = "Generated by Ranisa"
                c.drawText(footerText, leftMargin, pageHeight - bottomMargin + 25f, paint)
                
                if (showPageNumbers) {
                    val pageText = "Page $pNum"
                    c.drawText(pageText, pageWidth - rightMargin - paint.measureText(pageText), pageHeight - bottomMargin + 25f, paint)
                }
            }
            
            // Helper to wrap text with safe padding
            fun wrapText(text: String, width: Float, rowPaint: Paint): List<String> {
                val words = text.split(" ")
                val lines = mutableListOf<String>()
                var currentLine = ""
                val padding = 10f
                val availableCellWidth = width - padding
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    val testWidth = rowPaint.measureText(testLine)
                    if (testWidth <= availableCellWidth) {
                        currentLine = testLine
                    } else {
                        if (currentLine.isNotEmpty()) {
                            lines.add(currentLine)
                        }
                        currentLine = word
                        while (rowPaint.measureText(currentLine) > availableCellWidth && currentLine.length > 1) {
                            var i = currentLine.length
                            while (i > 0 && rowPaint.measureText(currentLine.substring(0, i)) > availableCellWidth) {
                                i--
                            }
                            if (i == 0) i = 1
                            lines.add(currentLine.substring(0, i))
                            currentLine = currentLine.substring(i)
                        }
                    }
                }
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                return lines
            }
            
            var y = topMargin
            
            val profile = getFirmProfile(context, firmName)
            
            // Draw Main Header
            fun drawHeaderBlock(c: Canvas) {
                paint.color = Color.parseColor("#2E1A47") // Deep dark purple title
                paint.style = Paint.Style.FILL
                paint.textSize = titleSize
                paint.isFakeBoldText = true
                c.drawText(profile.title, leftMargin, y + 15f, paint)
                
                paint.color = Color.DKGRAY
                paint.textSize = labelSize
                paint.isFakeBoldText = false
                c.drawText(profile.subtitle, leftMargin, y + 28f, paint)
                
                // Top-right info
                paint.color = Color.BLACK
                paint.textSize = textDetailSize
                paint.isFakeBoldText = true
                val infoX = pageWidth - rightMargin - 220f
                c.drawText("Ledger Name: $ledgerName", infoX, y + 12f, paint)
                paint.isFakeBoldText = false
                c.drawText("Type: ${ledgerType.uppercase()} LEDGER", infoX, y + 24f, paint)
                c.drawText("Date Range: $dateRangeText", infoX, y + 36f, paint)
                
                val sdfDateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                c.drawText("Generated: ${sdfDateTime.format(java.util.Date())}", infoX, y + 48f, paint)
                
                y += 60f
                paint.strokeWidth = 1f
                paint.color = Color.parseColor("#4A3B60")
                c.drawLine(leftMargin, y, pageWidth - rightMargin, y, paint)
                y += 10f
            }
            
            // Draw Mini Header (for subsequent pages)
            fun drawMiniHeader(c: Canvas) {
                paint.color = Color.parseColor("#2E1A47")
                paint.style = Paint.Style.FILL
                paint.textSize = labelSize
                paint.isFakeBoldText = true
                c.drawText("${profile.title} - $ledgerName (${ledgerType.uppercase()})", leftMargin, y + 12f, paint)
                
                paint.color = Color.DKGRAY
                paint.textSize = textDetailSize - 1f
                paint.isFakeBoldText = false
                val infoText = "Date Range: $dateRangeText"
                c.drawText(infoText, pageWidth - rightMargin - paint.measureText(infoText), y + 12f, paint)
                
                y += 20f
                paint.strokeWidth = 0.5f
                paint.color = Color.parseColor("#4A3B60")
                c.drawLine(leftMargin, y, pageWidth - rightMargin, y, paint)
                y += 8f
            }
            
            // Draw Summary Box
            fun drawSummaryBox(c: Canvas) {
                val boxHeight = 45f
                paint.color = Color.parseColor("#F4EFFF") // very light purple
                paint.style = Paint.Style.FILL
                c.drawRoundRect(leftMargin, y, pageWidth - rightMargin, y + boxHeight, 6f, 6f, paint)
                
                paint.color = Color.parseColor("#4A3B60")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                c.drawRoundRect(leftMargin, y, pageWidth - rightMargin, y + boxHeight, 6f, 6f, paint)
                
                // Compute summary values
                val totalBillsCount = bills.size
                val totalQtlsSum = bills.sumOf { it.quintals }
                val totalBillAmtSum = bills.sumOf { it.billAmount }
                
                val receivedList = bills.map { b ->
                    payments.filter { p -> p.billNo.trim().equals(b.billNumber.trim(), ignoreCase = true) }.sumOf { it.paymentAmount }
                }
                val totalReceivedSum = receivedList.sum()
                val totalBalanceSum = bills.sumOf { it.billAmount } - totalReceivedSum - bills.map { b ->
                    payments.filter { p -> p.billNo.trim().equals(b.billNumber.trim(), ignoreCase = true) }.sumOf { it.discountAmount + it.commissionAmount + (it.remarks1.toDoubleOrNull() ?: 0.0) }
                }.sum()
                
                // Draw columns (5 columns)
                val labels = listOf("Total Bills", "Total Qtls", "Total Bill Amt", "Total Received", "Total Balance")
                val values = listOf(
                    "$totalBillsCount",
                    "${String.format("%.2f", totalQtlsSum)} Q",
                    "₹${String.format("%.1f", totalBillAmtSum)}",
                    "₹${String.format("%.1f", totalReceivedSum)}",
                    "₹${String.format("%.1f", totalBalanceSum)}"
                )
                
                val colWidthSum = availableWidth / 5f
                for (i in 0 until 5) {
                    val cx = leftMargin + i * colWidthSum
                    paint.style = Paint.Style.FILL
                    paint.isFakeBoldText = true
                    paint.color = Color.parseColor("#4A3B60")
                    paint.textSize = tableHeaderSize - 1.5f
                    val lbl = labels[i]
                    c.drawText(lbl, cx + (colWidthSum - paint.measureText(lbl)) / 2f, y + 16f, paint)
                    
                    paint.textSize = tableHeaderSize + 0.5f
                    paint.color = Color.BLACK
                    val valStr = values[i]
                    c.drawText(valStr, cx + (colWidthSum - paint.measureText(valStr)) / 2f, y + 33f, paint)
                    
                    // Draw separator lines inside summary box
                    if (i > 0) {
                        paint.color = Color.parseColor("#D0C4DF")
                        paint.strokeWidth = 0.5f
                        c.drawLine(cx, y + 5f, cx, y + boxHeight - 5f, paint)
                    }
                }
                
                y += boxHeight + 15f
            }
            
            // Helper to draw thick table outer borders on each completed page
            fun drawTableOuterBorder(c: Canvas, startY: Float, endY: Float) {
                paint.color = Color.parseColor("#322659") // Dark purple outer border matching the header background
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                c.drawRect(leftMargin, startY, pageWidth - rightMargin, endY, paint)
            }
            
            // Draw Table Header Row
            fun drawTableHeader(c: Canvas) {
                // Background
                paint.color = Color.parseColor("#322659") // Dark purple header background
                paint.style = Paint.Style.FILL
                c.drawRect(leftMargin, y, pageWidth - rightMargin, y + baseRowHeight, paint)
                
                paint.color = Color.WHITE
                paint.textSize = tableHeaderSize
                paint.isFakeBoldText = true
                val headerTextY = y + (baseRowHeight + tableHeaderSize) / 2f - 1.5f
                
                var currentX = leftMargin
                for (i in selectedColumns.indices) {
                    val colName = selectedColumns[i]
                    val w = colWidths[i]
                    val txtWidth = paint.measureText(colName)
                    c.drawText(colName, currentX + (w - txtWidth) / 2f, headerTextY, paint)
                    currentX += w
                }
                
                // Draw bottom border and vertical divider lines for header
                paint.color = Color.parseColor("#D0C4DF")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                currentX = leftMargin
                for (i in selectedColumns.indices) {
                    val w = colWidths[i]
                    if (i > 0) {
                        c.drawLine(currentX, y, currentX, y + baseRowHeight, paint)
                    }
                    currentX += w
                }
                c.drawLine(leftMargin, y + baseRowHeight, pageWidth - rightMargin, y + baseRowHeight, paint)
                
                y += baseRowHeight
            }
            
            // Draw page borders and footer on the initial page
            drawBorders(canvas)
            drawHeaderBlock(canvas)
            
            if (showSummary) {
                drawSummaryBox(canvas)
            }
            
            var tablePageStartY = y
            drawTableHeader(canvas)
            
            // Draw Rows
            for (index in bills.indices) {
                val bill = bills[index]
                
                // Gather values for each column
                val matchingPayments = payments.filter { p -> p.billNo.trim().equals(bill.billNumber.trim(), ignoreCase = true) }
                val liveReceived = matchingPayments.sumOf { it.paymentAmount }
                val liveDiscount = matchingPayments.sumOf { it.discountAmount }
                val liveCommission = matchingPayments.sumOf { it.commissionAmount }
                val liveRemarkAmt = matchingPayments.sumOf { it.remarks1.toDoubleOrNull() ?: 0.0 }
                val liveBalance = bill.billAmount - liveReceived - liveDiscount - liveCommission - liveRemarkAmt
                
                val paymentStatus = when {
                    liveBalance <= 0.01 -> "Fully Paid"
                    liveReceived <= 0.0 -> "Pending"
                    else -> "Partial Paid"
                }
                
                val partyNameValue = when (ledgerType.lowercase()) {
                    "seller" -> bill.buyerName
                    "buyer" -> bill.sellerName
                    else -> "${bill.sellerName} / ${bill.buyerName}"
                }
                
                val remarksValue = if (bill.remarks.isNotBlank()) bill.remarks else if (bill.remark2.isNotBlank()) bill.remark2 else "-"
                
                val bankDetailsValue = if (matchingPayments.isNotEmpty()) {
                    matchingPayments.joinToString(", ") { p ->
                        val mode = p.paymentMode.ifBlank { "Paid" }
                        val ref = p.referenceNumber
                        if (ref.isNotBlank()) "$mode($ref)" else mode
                    }
                } else {
                    "-"
                }
                
                val colValues = selectedColumns.map { col ->
                    when (col) {
                        "Date" -> formatDateToDdMmYyyy(bill.date)
                        "Bill No." -> bill.billNumber
                        "Party Name" -> partyNameValue
                        "Place" -> bill.place.ifBlank { "-" }
                        "Brand" -> bill.brand.ifBlank { "-" }
                        "Qtls" -> String.format("%.2f", bill.quintals)
                        "Rate" -> "₹${String.format("%.1f", bill.rate)}"
                        "Bill Amount" -> "₹${String.format("%.1f", bill.billAmount)}"
                        "Received Amount" -> "₹${String.format("%.1f", liveReceived)}"
                        "Balance Amount" -> "₹${String.format("%.1f", liveBalance)}"
                        "Status" -> paymentStatus
                        "EB" -> bill.eb.ifBlank { "-" }
                        "Lorry Freight" -> "₹${String.format("%.1f", bill.lorryFreight)}"
                        "Credit Days" -> if (bill.creditDays > 0) bill.creditDays.toString() else "N/A"
                        "Bank / DD Details" -> bankDetailsValue
                        "Remarks" -> remarksValue
                        else -> "-"
                    }
                }
                
                // Wrap cells
                paint.textSize = tableRowSize
                paint.isFakeBoldText = false
                val cellLines = colValues.mapIndexed { idx, value ->
                    wrapText(value, colWidths[idx], paint)
                }
                val maxLinesInRow = cellLines.maxOf { it.size }
                val neededRowHeight = maxLinesInRow * (tableRowSize + 3f) + 8f
                
                // Check page break
                if (y + neededRowHeight > pageHeight - bottomMargin) {
                    // Draw outer border for completed table on current page
                    drawTableOuterBorder(canvas, tablePageStartY, y)
                    
                    drawFooter(canvas, pageNum)
                    pdfDocument.finishPage(page)
                    
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = topMargin
                    
                    drawBorders(canvas)
                    if (repeatHeader) {
                        drawMiniHeader(canvas)
                    }
                    tablePageStartY = y
                    drawTableHeader(canvas)
                }
                
                // Draw Row Background (Alternate row colors)
                if (index % 2 == 1) {
                    paint.color = Color.parseColor("#F4EFFF") // light purple
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(leftMargin, y, pageWidth - rightMargin, y + neededRowHeight, paint)
                }
                
                // Draw Cells
                paint.style = Paint.Style.FILL
                var currentX = leftMargin
                for (cIdx in selectedColumns.indices) {
                    val lines = cellLines[cIdx]
                    val colW = colWidths[cIdx]
                    
                    // Column colors styling
                    val colName = selectedColumns[cIdx]
                    if (colName == "Balance Amount" && liveBalance > 0.0) {
                        paint.color = Color.parseColor("#D32F2F") // red for outstanding
                    } else if (colName == "Status") {
                        paint.color = when (paymentStatus) {
                            "Fully Paid" -> Color.parseColor("#388E3C") // green
                            "Pending" -> Color.parseColor("#D32F2F") // red
                            else -> Color.parseColor("#F57C00") // orange
                        }
                        paint.isFakeBoldText = true
                    } else {
                        paint.color = Color.BLACK
                        paint.isFakeBoldText = false
                    }
                    
                    paint.textSize = tableRowSize
                    
                    // Vertically center text lines in row
                    val totalTextHeight = lines.size * (tableRowSize + 3f) - 3f
                    val cellYOffset = (neededRowHeight - totalTextHeight) / 2f
                    val cellYStart = y + cellYOffset + tableRowSize - 1f
                    
                    for (lineNum in lines.indices) {
                        val lineTxt = lines[lineNum]
                        val txtW = paint.measureText(lineTxt)
                        // center horizontally in cell if it's numeric/status
                        val drawX = if (colName in listOf("Date", "Bill No.", "Qtls", "Rate", "Bill Amount", "Received Amount", "Balance Amount", "Status", "Credit Days")) {
                            currentX + (colW - txtW) / 2f
                        } else {
                            currentX + 5f // Left aligned with padding
                        }
                        canvas.drawText(lineTxt, drawX, cellYStart + lineNum * (tableRowSize + 3f), paint)
                    }
                    
                    currentX += colW
                }
                
                // Draw borders for this row (Grid layout)
                paint.color = Color.parseColor("#D0C4DF")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                
                // Vertical dividers
                currentX = leftMargin
                for (cIdx in selectedColumns.indices) {
                    val colW = colWidths[cIdx]
                    if (cIdx > 0) {
                        canvas.drawLine(currentX, y, currentX, y + neededRowHeight, paint)
                    }
                    currentX += colW
                }
                
                // Horizontal bottom row divider line
                canvas.drawLine(leftMargin, y + neededRowHeight, pageWidth - rightMargin, y + neededRowHeight, paint)
                
                y += neededRowHeight
            }
            
            // Draw outer border for final table
            drawTableOuterBorder(canvas, tablePageStartY, y)
            
            // Draw Footer on last page
            drawFooter(canvas, pageNum)
            pdfDocument.finishPage(page)
            
            val filename = "Full_Ledger_${ledgerName.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, filename)
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()
            return file
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

