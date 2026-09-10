package dev.prateekthakur.devprobe.data.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 36f
private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
private const val BANNER_HEIGHT = 72f
private const val FOOTER_RESERVED = 34f

private object ReportColors {
    const val PRIMARY = 0xFF146C2E.toInt()
    const val TEXT = 0xFF1A1B22.toInt()
    const val MUTED = 0xFF5C5E64.toInt()
    const val DIVIDER = 0xFFD6D7DC.toInt()
    const val CARD_TINT = 0xFFF1F3EF.toInt()
    const val CRITICAL = 0xFFC5231F.toInt()
    const val HIGH = 0xFFE0554F.toInt()
    const val MEDIUM = 0xFFC97A1E.toInt()
    const val LOW = 0xFF3D7FE0.toInt()
    const val INFO = 0xFF7A8194.toInt()
}

private fun severityReportColor(label: String): Int = when (label) {
    "CRITICAL" -> ReportColors.CRITICAL
    "HIGH" -> ReportColors.HIGH
    "MEDIUM" -> ReportColors.MEDIUM
    "LOW" -> ReportColors.LOW
    else -> ReportColors.INFO
}

/**
 * Paginates a list of [ReportBlock]s onto an on-device [PdfDocument] — a small hand-rolled
 * layout engine (banner header, section rules, key/value rows, severity-colored finding
 * cards, code blocks) rather than a template library, since Android ships everything needed
 * (`PdfDocument` + `Canvas` + `StaticLayout`) and this keeps the feature dependency-free.
 */
class PdfReportRenderer(private val reportTitle: String, private val reportSubtitle: String) {

    private val document = PdfDocument()
    private var pageNumber = 0
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas
    private var y = 0f

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 20f; isFakeBoldText = true }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11.5f; alpha = 235 }
    private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 9f; alpha = 200; textAlign = Paint.Align.RIGHT }
    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.PRIMARY; textSize = 14f; isFakeBoldText = true }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.MUTED; textSize = 10f }
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.TEXT; textSize = 10.5f }
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.TEXT; textSize = 10.5f }
    private val bulletPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.TEXT; textSize = 10f }
    private val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.TEXT; textSize = 8.5f; typeface = Typeface.MONOSPACE }
    private val findingTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.TEXT; textSize = 11.5f; isFakeBoldText = true }
    private val dividerPaint = Paint().apply { color = ReportColors.DIVIDER; strokeWidth = 1f }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.MUTED; textSize = 8f }

    fun renderAndSave(blocks: List<ReportBlock>, destination: File) {
        startPage()
        blocks.forEach { renderBlock(it) }
        finishPage()
        FileOutputStream(destination).use { document.writeTo(it) }
        document.close()
    }

    private fun startPage() {
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        page = document.startPage(info)
        canvas = page.canvas
        y = if (pageNumber == 1) {
            drawHeaderBanner()
            BANNER_HEIGHT + 22f
        } else {
            MARGIN
        }
    }

    private fun drawHeaderBanner() {
        val bannerPaint = Paint().apply { color = ReportColors.PRIMARY }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), BANNER_HEIGHT, bannerPaint)
        canvas.drawText(reportTitle, MARGIN, 30f, titlePaint)
        canvas.drawText(reportSubtitle, MARGIN, 50f, subtitlePaint)
        val generatedAt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        canvas.drawText("DevProbe", PAGE_WIDTH - MARGIN, 30f, metaPaint)
        canvas.drawText("Generated $generatedAt", PAGE_WIDTH - MARGIN, 44f, metaPaint)
    }

    private fun finishPage() {
        val footerY = PAGE_HEIGHT - 18f
        canvas.drawLine(MARGIN, footerY - 10f, PAGE_WIDTH - MARGIN, footerY - 10f, dividerPaint)
        canvas.drawText("Generated locally by DevProbe — no data leaves this device", MARGIN, footerY, footerPaint)
        val pagePaint = Paint(footerPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Page $pageNumber", PAGE_WIDTH - MARGIN, footerY, pagePaint)
        document.finishPage(page)
    }

    private fun ensureSpace(height: Float) {
        if (y + height > PAGE_HEIGHT - MARGIN - FOOTER_RESERVED) {
            finishPage()
            startPage()
        }
    }

    private fun renderBlock(block: ReportBlock) {
        when (block) {
            is ReportBlock.Section -> {
                ensureSpace(32f)
                canvas.drawText(block.text, MARGIN, y + 12f, sectionPaint)
                y += 16f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
                y += 14f
            }
            is ReportBlock.KeyValue -> renderKeyValue(block)
            is ReportBlock.Paragraph -> {
                val layout = buildLayout(block.text, bodyPaint, CONTENT_WIDTH.toInt())
                ensureSpace(layout.height + 8f)
                drawLayout(layout, MARGIN, y)
                y += layout.height + 8f
            }
            is ReportBlock.Bullets -> renderBullets(block)
            is ReportBlock.Finding -> renderFinding(block)
            is ReportBlock.Code -> renderCode(block)
            ReportBlock.Divider -> {
                ensureSpace(12f)
                canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, dividerPaint)
                y += 14f
            }
            is ReportBlock.Spacer -> {
                ensureSpace(block.height)
                y += block.height
            }
        }
    }

    private fun renderKeyValue(block: ReportBlock.KeyValue) {
        val valueColumnX = MARGIN + CONTENT_WIDTH * 0.34f
        val valueWidth = (CONTENT_WIDTH * 0.66f).toInt()
        block.rows.forEach { (label, value) ->
            val layout = buildLayout(value, valuePaint, valueWidth)
            val rowHeight = maxOf(15f, layout.height.toFloat())
            ensureSpace(rowHeight + 6f)
            canvas.drawText(label, MARGIN, y + 10.5f, labelPaint)
            drawLayout(layout, valueColumnX, y)
            y += rowHeight + 6f
        }
        y += 4f
    }

    private fun renderBullets(block: ReportBlock.Bullets) {
        block.items.forEach { item ->
            val layout = buildLayout(item, bulletPaint, (CONTENT_WIDTH - 14f).toInt())
            ensureSpace(layout.height + 5f)
            canvas.drawText("•", MARGIN, y + 10f, bulletPaint)
            drawLayout(layout, MARGIN + 14f, y)
            y += layout.height + 5f
        }
        y += 4f
    }

    private fun renderFinding(block: ReportBlock.Finding) {
        val innerWidth = (CONTENT_WIDTH - 24f).toInt()
        val descLayout = buildLayout(block.description, bodyPaint, innerWidth)
        val evidenceLayout = block.evidence?.let { buildLayout("Evidence: $it", labelPaint, innerWidth) }
        val recLayout = block.recommendation?.let { buildLayout("Recommendation: $it", labelPaint, innerWidth) }

        val cardHeight = 24f + descLayout.height +
            (evidenceLayout?.let { it.height + 6f } ?: 0f) +
            (recLayout?.let { it.height + 6f } ?: 0f) + 12f
        ensureSpace(cardHeight)

        val top = y
        val accent = severityReportColor(block.severityLabel)
        canvas.drawRect(MARGIN, top, MARGIN + 4f, top + cardHeight, Paint().apply { color = accent })
        canvas.drawRect(MARGIN + 4f, top, PAGE_WIDTH - MARGIN, top + cardHeight, Paint().apply { color = ReportColors.CARD_TINT })

        var innerY = top + 17f
        canvas.drawText(block.severityLabel, MARGIN + 14f, innerY, Paint(findingTitlePaint).apply { color = accent; textSize = 8.5f })
        canvas.drawText(block.title, MARGIN + 14f + 52f, innerY, findingTitlePaint)
        innerY += 13f
        drawLayout(descLayout, MARGIN + 14f, innerY)
        innerY += descLayout.height + 6f
        evidenceLayout?.let {
            drawLayout(it, MARGIN + 14f, innerY)
            innerY += it.height + 6f
        }
        recLayout?.let {
            drawLayout(it, MARGIN + 14f, innerY)
        }
        y = top + cardHeight + 10f
    }

    private fun renderCode(block: ReportBlock.Code) {
        block.lines.forEach { line ->
            ensureSpace(11f)
            canvas.drawText(line.take(130), MARGIN + 10f, y + 8f, codePaint)
            y += 11f
        }
        y += 6f
    }

    private fun drawLayout(layout: StaticLayout, x: Float, top: Float) {
        canvas.save()
        canvas.translate(x, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(60))
            .setLineSpacing(1f, 1.08f)
            .build()
}
