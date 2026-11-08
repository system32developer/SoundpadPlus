package com.emprecio.soundpadplus.ui

import java.awt.*
import javax.swing.*

val BG = Color(0x262626)
val SURFACE = Color(0x1E1E1E)
val PANEL = Color(0x2D2D2D)
val SELECTED = Color(0x3A3A3A)
val TEXT = Color(0xD8D8D8)
val MUTED = Color(0x8C8C8C)
val ACCENT = Color(0x3498DB)

fun button(text: String, primary: Boolean = false) = JButton(text).apply {
    isFocusPainted = false
    if (primary) { background = ACCENT; foreground = Color.WHITE }
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
}
