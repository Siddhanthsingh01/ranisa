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

        // 2. Header info
        paint.style = Paint.Style.FILL
        paint.textSize = 8f
        paint.isFakeBoldText = true
        
        // Subject to Jurisdiction
        canvas.drawText("Subject to Raichur Jurisdiction", (595f - paint.measureText("Subject to Raichur Jurisdiction")) / 2, 38f, paint)
        
        // Top invocation mantras
        val topLantra = "\"SRI GANESHAYA NAMAH\""
        canvas.drawText(topLantra, (595f - paint.measureText(topLantra)) / 2, 50f, paint)
        
        val leftMantra = "\"SRI KHANDERI MATAJI NAMAH\" \"HARE KRISHNA\""
        canvas.drawText(leftMantra, 30f, 62f, paint)
        
        paint.isFakeBoldText = false
        val cellText1 = "Cell : 92432 34814(L)"
        val cellText2 = "94484 55041(P)"
        canvas.drawText(cellText1, 565f - paint.measureText(cellText1), 50f, paint)
        canvas.drawText(cellText2, 565f - paint.measureText(cellText2), 62f, paint)

        // Firm Main Title
        paint.textSize = 24f
        paint.isFakeBoldText = true
        val isHareKrishna = payment.firm.uppercase().contains("HARE KRISHNA")
        val mainTitle = if (isHareKrishna) "HARE KRISHNA RICE BROKER" else "LALIT RICE BROKER"
        val titleWidth = paint.measureText(mainTitle)
        canvas.drawText(mainTitle, (595f - titleWidth) / 2, 88f, paint)

        // Subtitle
        paint.textSize = 10f
        paint.isFakeBoldText = true
        val subTitle = "CANVASSING AGENT"
        canvas.drawText(subTitle, (595f - paint.measureText(subTitle)) / 2, 102f, paint)

        // Address
        paint.textSize = 8.5f
        paint.isFakeBoldText = false
        val addressLine = "Shayamrao Complex, Beside Vaikuntam Complex Gunj Road, RAICHUR - 584102. (Karnataka)"
        canvas.drawText(addressLine, (595f - paint.measureText(addressLine)) / 2, 115f, paint)

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
        canvas.drawText("For ${if (isHareKrishna) "HARE KRISHNA" else "LALIT"} RICE BROKER", 360f, y, paint)
        
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

        // 2. Header info
        paint.style = Paint.Style.FILL
        paint.textSize = 8f
        paint.isFakeBoldText = true
        
        // Subject to Jurisdiction
        canvas.drawText("Subject to Raichur Jurisdiction", (595f - paint.measureText("Subject to Raichur Jurisdiction")) / 2, 38f, paint)
        
        // Top invocation mantras
        val topLantra = "\"SRI GANESHAYA NAMAH\""
        canvas.drawText(topLantra, (595f - paint.measureText(topLantra)) / 2, 50f, paint)
        
        val leftMantra = "\"SRI KHANDERI MATAJI NAMAH\" \"HARE KRISHNA\""
        canvas.drawText(leftMantra, 30f, 62f, paint)
        
        paint.isFakeBoldText = false
        val cellText1 = "Cell : 92432 34814(L)"
        val cellText2 = "94484 55041(P)"
        canvas.drawText(cellText1, 565f - paint.measureText(cellText1), 50f, paint)
        canvas.drawText(cellText2, 565f - paint.measureText(cellText2), 62f, paint)

        // Firm Main Title
        paint.textSize = 24f
        paint.isFakeBoldText = true
        val isHareKrishna = bill.firmName.uppercase().contains("HARE KRISHNA")
        val mainTitle = if (isHareKrishna) "HARE KRISHNA RICE BROKER" else "LALIT RICE BROKER"
        val titleWidth = paint.measureText(mainTitle)
        canvas.drawText(mainTitle, (595f - titleWidth) / 2, 88f, paint)

        // Subtitle
        paint.textSize = 10f
        paint.isFakeBoldText = true
        val subTitle = "CANVASSING AGENT"
        canvas.drawText(subTitle, (595f - paint.measureText(subTitle)) / 2, 102f, paint)

        // Address
        paint.textSize = 8.5f
        paint.isFakeBoldText = false
        val addressLine = "Shayamrao Complex, Beside Vaikuntam Complex Gunj Road, RAICHUR - 584102. (Karnataka)"
        canvas.drawText(addressLine, (595f - paint.measureText(addressLine)) / 2, 115f, paint)

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

        // Cell 3: For Lalit Rice Broker
        paint.isFakeBoldText = true
        val sigForLabel = "For : $mainTitle"
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
}
