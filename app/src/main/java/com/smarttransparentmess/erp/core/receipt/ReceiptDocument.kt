package com.smarttransparentmess.erp.core.receipt

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.finance.AdminPaymentReceipt
import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.domain.finance.PaymentMethod
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal object ReceiptDocument {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val BOTTOM = 790f

    fun write(context: Context, record: AdminPaymentReceipt, destination: Uri) {
        val stream = context.contentResolver.openOutputStream(destination, "wt")
            ?: error("The selected destination could not be opened.")
        stream.use { write(context, record, it) }
    }

    private fun write(context: Context, record: AdminPaymentReceipt, output: java.io.OutputStream) {
        val document = PdfDocument()
        val writer = ReceiptPageWriter(document)
        val valid = record.payment.isValid
        val status = context.getString(
            if (valid) R.string.payment_status_valid else R.string.payment_status_reversed,
        )
        val method = context.getString(
            when (record.payment.method) {
                PaymentMethod.CASH -> R.string.payment_method_cash
                PaymentMethod.BKASH -> R.string.payment_method_bkash
                PaymentMethod.NAGAD -> R.string.payment_method_nagad
                PaymentMethod.BANK -> R.string.payment_method_bank
                PaymentMethod.MOBILE_BANKING -> R.string.payment_method_mobile_banking
                PaymentMethod.OTHER -> R.string.payment_method_other
            },
        )
        val amount = context.getString(R.string.bdt_amount, Money.format(record.payment.amountMinor))
        writer.text(context.getString(R.string.app_name), 20f, bold = true)
        writer.text(context.getString(R.string.receipt_share_title), 16f, bold = true)
        writer.space(10f)
        writer.text("${context.getString(R.string.receipt_share_number)}: ${record.payment.receiptNumber}")
        writer.text("${context.getString(R.string.amount_label)}: $amount")
        writer.text("${context.getString(R.string.receipt_share_member)}: ${record.memberName}")
        writer.text("${context.getString(R.string.receipt_share_period)}: ${record.periodLabel}")
        writer.text("${context.getString(R.string.select_method)}: $method")
        writer.text("${context.getString(R.string.date_label)}: ${record.payment.paidAt}")
        writer.text("${context.getString(R.string.receipt_issued_at)}: ${record.payment.receiptIssuedAt}")
        writer.text("${context.getString(R.string.reference_optional)}: ${record.payment.transactionReference ?: "—"}")
        writer.text("${context.getString(R.string.note_optional)}: ${record.payment.note ?: "—"}")
        writer.text("${context.getString(R.string.receipt_share_status)}: $status", bold = true)
        if (!valid) {
            writer.space(8f)
            writer.text(context.getString(R.string.receipt_reversed_banner), 14f, bold = true, color = Color.rgb(160, 35, 35))
            writer.text("${context.getString(R.string.receipt_reversed_at)}: ${record.payment.reversedAt ?: "—"}")
            writer.text("${context.getString(R.string.receipt_reversal_reason)}: ${record.payment.reversalReason ?: "—"}")
        } else {
            writer.space(12f)
            writer.text(context.getString(R.string.receipt_received_ack), color = Color.DKGRAY)
        }
        writer.space(16f)
        writer.text(context.getString(R.string.receipt_verification), 11f, bold = true)
        writer.text(record.payment.receiptId, 8f, color = Color.DKGRAY)
        writer.text(context.getString(R.string.receipt_verification_help), 9f, color = Color.DKGRAY)
        writer.finish()
        try {
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    fun createShareFile(context: Context, record: AdminPaymentReceipt): File {
        val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(directory, "${record.payment.receiptNumber}-${UUID.randomUUID()}.pdf")
        FileOutputStream(file).use { stream -> write(context, record, stream) }
        return file
    }

    fun share(context: Context, record: AdminPaymentReceipt, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, record.payment.receiptNumber, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_receipt)))
    }

    private class ReceiptPageWriter(private val document: PdfDocument) {
        private var pageNumber = 1
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f

        init {
            startPage()
        }

        private fun startPage() {
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(info)
            canvas = page.canvas
            y = MARGIN + 20f
        }

        fun text(text: String, size: Float = 12f, bold: Boolean = false, color: Int = Color.BLACK) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                this.color = color
            }
            val paragraphs = text.split('\n')
            paragraphs.forEach { paragraph ->
                var line = ""
                paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (paint.measureText(candidate) > PAGE_WIDTH - 2 * MARGIN && line.isNotEmpty()) {
                        drawLine(line, paint, size)
                        line = word
                    } else {
                        line = candidate
                    }
                }
                drawLine(line.ifEmpty { " " }, paint, size)
            }
        }

        private fun drawLine(line: String, paint: Paint, size: Float) {
            if (y > BOTTOM) {
                document.finishPage(page)
                pageNumber += 1
                startPage()
            }
            canvas.drawText(line, MARGIN, y, paint)
            y += size + 7f
        }

        fun space(height: Float) {
            y += height
        }

        fun finish() {
            document.finishPage(page)
        }
    }
}
