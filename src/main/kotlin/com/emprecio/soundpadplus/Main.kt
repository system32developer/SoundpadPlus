package com.emprecio.soundpadplus

import com.emprecio.soundpadplus.config.Settings
import com.emprecio.soundpadplus.i18n.I18n
import com.emprecio.soundpadplus.plugin.PluginLoader
import com.emprecio.soundpadplus.ui.BG
import com.emprecio.soundpadplus.ui.SURFACE
import com.emprecio.soundpadplus.ui.TEXT
import com.emprecio.soundpadplus.ui.buildMainPanel
import com.emprecio.soundpadplus.ui.checkForUpdates
import com.emprecio.soundpadplus.ui.installToolsIfMissing
import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import java.awt.*
import javax.swing.*

fun main() {
    FlatLaf.setGlobalExtraDefaults(mapOf("@accentColor" to "#3498DB", "@background" to "#262626"))
    FlatDarkLaf.setup()
    UIManager.put("Button.disabledText", Color(0x5E6673))
    UIManager.put("Table.background", SURFACE)
    UIManager.put("TableHeader.background", BG)
    UIManager.put("MenuBar.background", BG)
    UIManager.put("TabbedPane.selectedForeground", TEXT)
    SwingUtilities.invokeLater {
        val sources = PluginLoader.load { jar, sha ->
            JOptionPane.showConfirmDialog(null, I18n.t("plugin.approve.text", jar.name, sha), I18n.t("plugin.approve.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION
        }
        val frame = JFrame("SoundpadPlus")
        fun showUi() {
            frame.contentPane = buildMainPanel(frame, sources, ::showUi)
            frame.revalidate()
            frame.repaint()
        }
        frame.apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            showUi()
            minimumSize = Dimension(680, 440)
            setSize(860, 580)
            setLocationRelativeTo(null)
            isVisible = true
        }
        installToolsIfMissing(frame)
        if (Settings["autoUpdate", "true"] == "true") checkForUpdates(frame, silent = true)
    }
}
