package com.emprecio.soundpadplus

import com.emprecio.soundpadplus.config.Settings
import com.emprecio.soundpadplus.i18n.I18n
import com.emprecio.soundpadplus.config.AppPaths
import com.emprecio.soundpadplus.plugin.PluginManager
import com.emprecio.soundpadplus.plugin.watchPlugins
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
        val manager = PluginManager { jar, sha ->
            JOptionPane.showConfirmDialog(null, I18n.t("plugin.approve.text", jar.name, sha), I18n.t("plugin.approve.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION
        }
        manager.reload()
        val frame = JFrame("SoundpadPlus")
        lateinit var rebuild: () -> Unit
        fun showUi() {
            frame.contentPane = buildMainPanel(frame, manager) { rebuild() }
            frame.revalidate()
            frame.repaint()
        }
        // Language or plugin changes: redraw the window and fetch what newly enabled plugins need.
        rebuild = { showUi(); installToolsIfMissing(frame, manager.enabled) }
        frame.apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            showUi()
            minimumSize = Dimension(680, 440)
            setSize(860, 580)
            setLocationRelativeTo(null)
            isVisible = true
        }
        installToolsIfMissing(frame, manager.enabled)
        watchPlugins(AppPaths.plugins) { if (manager.reload()) rebuild() }
        if (Settings["autoUpdate", "true"] == "true") checkForUpdates(frame, silent = true)
    }
}
