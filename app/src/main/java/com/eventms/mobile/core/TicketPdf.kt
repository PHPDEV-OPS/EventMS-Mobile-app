package com.eventms.mobile.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.eventms.mobile.domain.DateTimes
import com.eventms.mobile.domain.Money
import com.eventms.mobile.domain.Ticket
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a ticket to a PDF in the device's Downloads folder.
 *
 * Drawn with PdfDocument rather than an HTML/print pipeline so it works with no
 * network and no print service — the attendee may be saving this on the way to
 * the venue.
 */
object TicketPdf {

    private const val PAGE_WIDTH = 595   // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f

    fun save(context: Context, ticket: Ticket): Result<Uri> = runCatching {
        val document = PdfDocument()
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        )

        draw(page.canvas, ticket)
        document.finishPage(page)

        val fileName = "EventMS-Ticket-${ticket.ticketNumber ?: ticket.bookingId.take(8)}.pdf"
        val uri = write(context, fileName) { stream -> document.writeTo(stream) }
        document.close()
        uri
    }

    private fun draw(canvas: android.graphics.Canvas, ticket: Ticket) {
        val title = Paint().apply {
            color = Color.BLACK
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val label = Paint().apply {
            color = Color.parseColor("#6B7280")
            textSize = 11f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val value = Paint().apply {
            color = Color.BLACK
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val rule = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 1f
        }

        var y = MARGIN + 30f

        canvas.drawText("EVENTMS", MARGIN, y, label)
        y += 34f
        canvas.drawText(ticket.eventTitle ?: "Event ticket", MARGIN, y, title)
        y += 26f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
        y += 34f

        fun row(labelText: String, valueText: String) {
            canvas.drawText(labelText.uppercase(), MARGIN, y, label)
            canvas.drawText(valueText, MARGIN, y + 19f, value)
            y += 48f
        }

        row("Ticket number", ticket.ticketNumber ?: ticket.bookingId.take(12).uppercase())
        row("Attendee", ticket.email)
        row("When", DateTimes.full(ticket.eventStartDate))
        row("Where", ticket.eventLocation ?: "See event page")
        row("Tickets", ticket.quantity.toString())
        row("Total paid", Money.formatExact(ticket.totalAmount))
        row("Status", if (ticket.isPaid) "PAID" else ticket.paymentStatus.name)

        // QR block
        val qrSize = 200
        QrCodes.generate(ticket.qrPayload, qrSize)?.let { bitmap ->
            val left = (PAGE_WIDTH - qrSize) / 2f
            canvas.drawBitmap(bitmap, null, Rect(left.toInt(), y.toInt(), (left + qrSize).toInt(), (y + qrSize).toInt()), null)
            y += qrSize + 26f
        }

        val centered = Paint(label).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(
            "Present this QR code at the gate for scanning.",
            PAGE_WIDTH / 2f, y, centered
        )
        y += 16f
        canvas.drawText(
            "This ticket admits ${ticket.quantity} " +
                if (ticket.quantity == 1) "person." else "people.",
            PAGE_WIDTH / 2f, y, centered
        )
    }

    /**
     * Writes into Downloads. MediaStore on API 29+, the public directory below
     * that (which is why the manifest keeps WRITE_EXTERNAL_STORAGE capped at 28).
     */
    private fun write(
        context: Context,
        fileName: String,
        body: (java.io.OutputStream) -> Unit
    ): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create a file in Downloads")

            resolver.openOutputStream(uri)?.use(body) ?: error("Could not open Downloads for writing")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }

        val downloads =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists()) downloads.mkdirs()

        val file = File(downloads, fileName)
        FileOutputStream(file).use(body)
        return Uri.fromFile(file)
    }
}
