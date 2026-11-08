package com.emprecio.soundpadplus.ui

import java.awt.*
import javax.swing.*

class Badge(private val side: Int) : JComponent() {
    var letter = ""
        set(v) { field = v; repaint() }
    init { Dimension(side, side).let { preferredSize = it; minimumSize = it; maximumSize = it } }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.color = Color(0x2E86C1); g2.fillRoundRect(0, 0, side, side, 4, 4)
        g2.color = Color(0x16324A); g2.font = font.deriveFont(Font.BOLD, side * 0.7f)
        val fm = g2.fontMetrics
        g2.drawString(letter, (side - fm.stringWidth(letter)) / 2, (side - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }
}

fun letterOf(s: String) = s.firstOrNull()?.uppercase() ?: "?"
