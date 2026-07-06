import sys

def main():
    filepath = "/app/src/main/java/com/example/ui/RanisaApp.kt"
    with open(filepath, "r") as f:
        content = f.read()

    # 1. Update Buyer Ledger headers and row
    target_buyer_headers = """                        val headers = listOf(
                            "Date" to 90, "Bill No" to 80, "Party Name" to 150, "Place" to 90,
                            "Freight" to 80, "Rate" to 70, "Qtls" to 70, "BILL AMT" to 100,
                            "Received" to 100, "Discount" to 80, "Commission" to 90,
                            "Remark" to 150, "DD Date" to 100, "Balance AMT" to 100, "Action" to 180
                        )"""
    
    replacement_buyer_headers = """                        val headers = listOf(
                            "Date" to 90, "Bill No" to 80, "Party Name" to 150, "Place" to 90,
                            "Freight" to 80, "Rate" to 70, "Qtls" to 70, 
                            "BILL AMT" to 100, "Received" to 100, "Discount" to 80, "Commission" to 90,
                            "Remark Amt" to 100, "Remark" to 150, "Balance AMT" to 100, "Status" to 100, "Action" to 180
                        )"""

    target_buyer_row = """                                    RegisterField(bill.date, 90)
                                    RegisterField(bill.billNumber, 80)
                                    RegisterField(bill.sellerName, 150)
                                    RegisterField(bill.place, 90)
                                    RegisterField("₹${bill.lorryFreight}", 80)
                                    RegisterField("₹${bill.rate}", 70)
                                    RegisterField("${bill.quintals} Q", 70)
                                    RegisterField("₹${String.format("%.1f", bill.billAmount)}", 100)
                                    RegisterField("₹${String.format("%.1f", bill.totalReceived)}", 100)
                                    RegisterField("${bill.discountPercent}%", 80)
                                    RegisterField("${bill.commissionPercent}%", 90)
                                    RegisterField(bill.remarks, 150)
                                    RegisterField(bill.lastPaymentDate, 100)
                                    RegisterField("₹${String.format("%.1f", bill.balance)}", 100, highlight = true)"""

    replacement_buyer_row = """                                    RegisterField(bill.date, 90)
                                    RegisterField(bill.billNumber, 80)
                                    RegisterField(bill.sellerName, 150)
                                    RegisterField(bill.place, 90)
                                    RegisterField("₹${bill.lorryFreight}", 80)
                                    RegisterField("₹${bill.rate}", 70)
                                    RegisterField("${bill.quintals} Q", 70)
                                    RegisterField("₹${String.format("%.1f", bill.billAmount)}", 100)
                                    RegisterField("₹${String.format("%.1f", bill.totalReceived)}", 100)
                                    RegisterField("${bill.discountPercent}%", 80)
                                    RegisterField("${bill.commissionPercent}%", 90)
                                    RegisterField(if (bill.remark1.isNotBlank()) "₹${bill.remark1}" else "₹0.0", 100)
                                    RegisterField(bill.remark2.ifBlank { "-" }, 150)
                                    RegisterField("₹${String.format("%.1f", bill.balance)}", 100, highlight = true)
                                    
                                    val paymentStatus = when {
                                        bill.balance <= 0.01 -> "Fully Paid"
                                        bill.totalReceived <= 0.0 -> "Pending"
                                        else -> "Partial Paid"
                                    }
                                    RegisterField(paymentStatus, 100, highlight = true)"""

    if target_buyer_headers in content:
        content = content.replace(target_buyer_headers, replacement_buyer_headers, 1)
        print("Successfully replaced Buyer headers!")
    else:
        print("Warning: target_buyer_headers not found!")

    if target_buyer_row in content:
        content = content.replace(target_buyer_row, replacement_buyer_row, 1)
        print("Successfully replaced Buyer row!")
    else:
        print("Warning: target_buyer_row not found!")

    # 2. Update the two payment details rows (Seller Ledger & Buyer Ledger)
    target_payment_row = """                                                    // 5. Freight (80dp) -> Remark Amt (remarks1)
                                                    RegisterField(if (payment.remarks1.isNotBlank()) "₹${payment.remarks1}" else "₹0.0", 80)

                                                    // 6. Rate (70dp) -> blank
                                                    RegisterField("", 70)

                                                    // 7. Qtls (70dp) -> blank
                                                    RegisterField("", 70)

                                                    // 8. BILL AMT (100dp) -> blank
                                                    RegisterField("", 100)

                                                    // 9. Received (100dp) -> Amount
                                                    RegisterField("₹${String.format("%.1f", payment.amount)}", 100, highlight = true)

                                                    // 10. Discount (80dp) -> Discount Amount
                                                    RegisterField("₹${String.format("%.1f", payment.discountAmount)}", 80)

                                                    // 11. Commission (90dp) -> Commission Amount
                                                    RegisterField("₹${String.format("%.1f", payment.commissionAmount)}", 90)

                                                    // 12. Remark (150dp) -> Remark (remarks2)
                                                    RegisterField(payment.remarks2.ifBlank { "-" }, 150)

                                                    // 13. DD Date (100dp) -> Payment Date
                                                    RegisterField(payment.date, 100)

                                                    // 14. Balance AMT (100dp) -> Balance Amount after that payment
                                                    RegisterField("₹${String.format("%.1f", payment.pendingAmount)}", 100, highlight = true)"""

    replacement_payment_row = """                                                    // 5. Freight (80dp) -> blank
                                                    RegisterField("", 80)

                                                    // 6. Rate (70dp) -> blank
                                                    RegisterField("", 70)

                                                    // 7. Qtls (70dp) -> blank
                                                    RegisterField("", 70)

                                                    // 8. BILL AMT (100dp) -> blank
                                                    RegisterField("", 100)

                                                    // 9. Received (100dp) -> Amount
                                                    RegisterField("₹${String.format("%.1f", payment.amount)}", 100, highlight = true)

                                                    // 10. Discount (80dp) -> Discount Amount
                                                    RegisterField("₹${String.format("%.1f", payment.discountAmount)}", 80)

                                                    // 11. Commission (90dp) -> Commission Amount
                                                    RegisterField("₹${String.format("%.1f", payment.commissionAmount)}", 90)

                                                    // 12. Remark Amt (100dp) -> Remark Amt (remarks1)
                                                    RegisterField(if (payment.remarks1.isNotBlank()) "₹${payment.remarks1}" else "₹0.0", 100)

                                                    // 13. Remark (150dp) -> Remark (remarks2)
                                                    RegisterField(payment.remarks2.ifBlank { "-" }, 150)

                                                    // 14. Balance AMT (100dp) -> Balance Amount after that payment
                                                    RegisterField("₹${String.format("%.1f", payment.pendingAmount)}", 100, highlight = true)

                                                    // 15. Status (100dp) -> blank
                                                    RegisterField("", 100)"""

    # Replace both occurrences (Seller and Buyer)
    occurrences = content.count(target_payment_row)
    if occurrences > 0:
        content = content.replace(target_payment_row, replacement_payment_row)
        print(f"Successfully replaced {occurrences} payment rows!")
    else:
        print("Warning: target_payment_row not found!")

    with open(filepath, "w") as f:
        f.write(content)

if __name__ == "__main__":
    main()
