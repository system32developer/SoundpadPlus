package com.emprecio.soundpadplus.ui

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Frame
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.border.EmptyBorder

class ProgressDialog(owner: Frame, title: String) : JDialog(owner, title, false) {
    private val label = JLabel(" ")
    private val bar = JProgressBar(0, 100).apply { isIndeterminate = true }

    init {
        contentPane = JPanel(BorderLayout(0, 8)).apply {
            border = EmptyBorder(16, 16, 16, 16)
            add(label, BorderLayout.NORTH)
            add(bar, BorderLayout.CENTER)
        }
        preferredSize = Dimension(380, 120)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        pack()
        setLocationRelativeTo(owner)
    }

    /** Call on the UI thread. [percent] -1 means unknown. */
    fun update(text: String, percent: Int) {
        label.text = text
        bar.isIndeterminate = percent < 0
        if (percent >= 0) bar.value = percent
    }
}
