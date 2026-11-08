package com.emprecio.soundpadplus.ui

import com.emprecio.soundpadplus.api.AudioSource
import com.emprecio.soundpadplus.config.Settings
import com.emprecio.soundpadplus.config.category
import com.emprecio.soundpadplus.i18n.I18n
import com.emprecio.soundpadplus.util.plain
import com.emprecio.soundpadplus.soundpad.Soundpad
import com.emprecio.soundpadplus.util.async
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

fun showPreferences(owner: Frame, sources: List<AudioSource>, onSaved: () -> Unit) {
    val combos = sources.associateWith { s ->
        JComboBox(arrayOf(s.category)).apply { isEditable = true; selectedItem = s.category }
    }
    async({ Soundpad.categories() }) { r ->
        r.onSuccess { names ->
            combos.values.forEach { c ->
                val cur = c.selectedItem
                names.filter { n -> (0 until c.itemCount).none { c.getItemAt(it) == n } }.forEach(c::addItem)
                c.selectedItem = cur
            }
        }
    }
    val deleteFile = JCheckBox(I18n.t("settings.deleteFile"), Settings["deleteFile", "true"] == "true")
    val languages = I18n.available
    val language = JComboBox(languages.values.toTypedArray()).apply { selectedItem = languages[I18n.language] }

    val cats = JPanel(GridBagLayout()).apply {
        border = EmptyBorder(12, 12, 12, 12)
        val c = GridBagConstraints().apply { anchor = GridBagConstraints.WEST; insets = Insets(4, 4, 4, 4) }
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2
        add(JLabel("<html><b>${I18n.t("settings.categories.title")}</b><br><span style='color:#8C8C8C'>${I18n.t("settings.categories.help")}</span></html>"), c)
        sources.forEachIndexed { i, s ->
            c.gridy = i + 1; c.gridwidth = 1; c.gridx = 0; c.fill = GridBagConstraints.NONE; c.weightx = 0.0
            add(JLabel(plain(s.name)), c)
            c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0
            add(combos.getValue(s), c)
        }
        c.gridx = 0; c.gridy = sources.size + 1; c.weighty = 1.0; add(JPanel().apply { isOpaque = false }, c)
    }
    val general = JPanel(GridLayout(0, 1, 0, 8)).apply {
        border = EmptyBorder(12, 12, 12, 12)
        add(deleteFile)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { add(JLabel(I18n.t("settings.language"))); add(language) })
        add(JLabel(I18n.t("settings.restart")).apply { foreground = MUTED })
    }
    val tabs = JTabbedPane().apply { addTab(I18n.t("settings.tab.categories"), cats); addTab(I18n.t("settings.tab.general"), general) }

    val dlg = JDialog(owner, I18n.t("settings.title"), true)
    val ok = button(I18n.t("btn.ok"), true).apply {
        addActionListener {
            sources.forEach { it.category = combos.getValue(it).selectedItem?.toString() ?: "" }
            Settings["deleteFile"] = deleteFile.isSelected.toString()
            languages.entries.firstOrNull { it.value == language.selectedItem }?.let { Settings["language"] = it.key }
            Settings.save(); onSaved(); dlg.dispose()
        }
    }
    val cancel = button(I18n.t("btn.cancel")).apply { addActionListener { dlg.dispose() } }
    dlg.contentPane = JPanel(BorderLayout(0, 8)).apply {
        border = EmptyBorder(8, 8, 8, 8)
        add(tabs, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { add(ok); add(cancel) }, BorderLayout.SOUTH)
    }
    dlg.rootPane.defaultButton = ok
    dlg.setSize(520, 300)
    dlg.setLocationRelativeTo(owner)
    dlg.isVisible = true
}
