package com.emprecio.soundpadplus.ui

import com.emprecio.soundpadplus.audio.Preview
import com.emprecio.soundpadplus.config.Settings
import com.emprecio.soundpadplus.api.AudioSource
import com.emprecio.soundpadplus.api.Entry
import com.emprecio.soundpadplus.config.category
import com.emprecio.soundpadplus.i18n.I18n
import com.emprecio.soundpadplus.plugin.PluginManager
import com.emprecio.soundpadplus.update.BuildInfo
import com.emprecio.soundpadplus.util.plain
import com.emprecio.soundpadplus.soundpad.Soundpad
import com.emprecio.soundpadplus.util.async
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

enum class Mode { ADD, PLAY, PLAY_DELETE }

fun buildMainPanel(frame: JFrame, manager: PluginManager, onRebuild: () -> Unit): JPanel {
    val sources = manager.enabled
    var current = sources.firstOrNull { it.id == manager.selectedId } ?: sources[0]
    var shown = emptyList<Entry>()
    var downloading = false
    var searchId = 0
    val counts = mutableMapOf<AudioSource, Int>()

    val query = JTextField().apply {
        margin = Insets(4, 8, 4, 8)
        putClientProperty("JTextField.searchHintText", current.searchHint)
    }
    val previewBtn = button("▶").apply { toolTipText = I18n.t("toolbar.preview"); isEnabled = false }
    val stopBtn = button("■").apply { toolTipText = I18n.t("toolbar.stop") }
    val searchBtn = button(I18n.t("toolbar.search"))
    val toolbar = JPanel(BorderLayout(8, 0)).apply {
        background = BG; border = EmptyBorder(6, 8, 6, 8)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false; add(previewBtn); add(stopBtn) }, BorderLayout.WEST)
        add(query, BorderLayout.CENTER)
        add(searchBtn, BorderLayout.EAST)
    }

    val sourceModel = DefaultListModel<AudioSource>().apply { addAll(sources) }
    val sourceList = JList(sourceModel).apply {
        background = PANEL; selectedIndex = sources.indexOf(current); fixedCellHeight = 36
        cellRenderer = ListCellRenderer { l, s, _, sel, _ ->
            JPanel(BorderLayout(8, 0)).apply {
                background = if (sel) SELECTED else PANEL; border = EmptyBorder(4, 8, 4, 8)
                add(Badge(26).apply { letter = letterOf(plain(s.name)) }, BorderLayout.WEST)
                add(JLabel(plain(s.name)).apply { foreground = TEXT; if (sel) font = font.deriveFont(Font.BOLD) }, BorderLayout.CENTER)
                add(JLabel(counts[s]?.toString() ?: "").apply { foreground = TEXT }, BorderLayout.EAST)
            }
        }
    }
    val left = JPanel(BorderLayout()).apply {
        background = PANEL; preferredSize = Dimension(190, 10)
        add(JPanel(BorderLayout()).apply {
            background = PANEL; border = EmptyBorder(6, 12, 6, 12)
            add(JLabel(I18n.t("sources.header")).apply { font = font.deriveFont(Font.BOLD) }, BorderLayout.WEST)
            add(JLabel("#").apply { font = font.deriveFont(Font.BOLD) }, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        add(sourceList, BorderLayout.CENTER)
    }

    val tableModel = object : javax.swing.table.DefaultTableModel(arrayOf(I18n.t("table.index"), I18n.t("table.title"), I18n.t("table.info"), I18n.t("table.duration")), 0) {
        override fun isCellEditable(r: Int, c: Int) = false
    }
    val table = JTable(tableModel).apply {
        background = SURFACE; rowHeight = 22; showHorizontalLines = false; showVerticalLines = false
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION); selectionBackground = SELECTED; selectionForeground = TEXT
        columnModel.getColumn(0).preferredWidth = 40; columnModel.getColumn(0).maxWidth = 60
        columnModel.getColumn(1).preferredWidth = 360
        columnModel.getColumn(2).preferredWidth = 160
        columnModel.getColumn(3).preferredWidth = 60; columnModel.getColumn(3).maxWidth = 80
        tableHeader.reorderingAllowed = false
    }
    val scroll = JScrollPane(table).apply { border = BorderFactory.createEmptyBorder(); viewport.background = SURFACE }

    val badge = Badge(48)
    val infoName = JLabel().apply { font = font.deriveFont(Font.BOLD, 13f) }
    val infoCat = JLabel().apply { foreground = MUTED }
    val infoResults = JLabel().apply { foreground = MUTED }
    val infoSelected = JLabel().apply { foreground = MUTED }
    val wrench = button("⚙").apply { toolTipText = I18n.t("settings.title"); isBorderPainted = false; isContentAreaFilled = false; font = font.deriveFont(18f) }
    val info = JPanel(BorderLayout(12, 0)).apply {
        background = PANEL; border = EmptyBorder(8, 8, 8, 8)
        add(badge, BorderLayout.WEST)
        add(JPanel(GridLayout(2, 1)).apply {
            isOpaque = false
            add(JPanel(FlowLayout(FlowLayout.LEFT, 16, 0)).apply { isOpaque = false; add(infoName); add(infoCat) })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 16, 0)).apply { isOpaque = false; add(infoResults); add(infoSelected) })
        }, BorderLayout.CENTER)
        add(wrench, BorderLayout.EAST)
    }
    val status = JLabel(I18n.t("status.idle")).apply { foreground = MUTED }
    val bar = JProgressBar(0, 100).apply { foreground = ACCENT; background = SURFACE; isBorderPainted = false; preferredSize = Dimension(10, 4) }
    val addBtn = button(I18n.t("btn.download")).apply { isEnabled = false }
    val playBtn = button(I18n.t("btn.downloadPlay")).apply { isEnabled = false }
    val delBtn = button(I18n.t("btn.playDelete"), true).apply { isEnabled = false }
    val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false; add(addBtn); add(playBtn); add(delBtn) }
    val bottom = JPanel(BorderLayout(0, 6)).apply {
        background = BG; border = EmptyBorder(6, 8, 8, 8)
        add(bar, BorderLayout.NORTH)
        add(status, BorderLayout.CENTER)
        add(actions, BorderLayout.EAST)
    }
    val south = JPanel(BorderLayout()).apply { add(info, BorderLayout.NORTH); add(bottom, BorderLayout.CENTER) }

    fun selected(): Entry? = table.selectedRow.takeIf { it >= 0 }?.let { shown.getOrNull(it) }

    fun refresh() {
        val sel = selected()
        val can = !downloading && sel != null
        addBtn.isEnabled = can; playBtn.isEnabled = can; delBtn.isEnabled = can
        previewBtn.isEnabled = sel?.previewUrl != null
        badge.letter = letterOf(current.category)
        infoName.text = plain(current.name)
        infoCat.text = I18n.t("info.category", current.category)
        infoResults.text = I18n.t("info.results", shown.size)
        infoSelected.text = I18n.t("info.selected", if (sel != null) 1 else 0)
        query.putClientProperty("JTextField.searchHintText", current.searchHint)
        sourceList.repaint()
    }

    fun search() {
        val q = query.text.trim()
        val id = ++searchId
        if (q.length < 2) {
            shown = emptyList(); tableModel.rowCount = 0; counts.remove(current)
            status.text = I18n.t("status.idle"); refresh(); return
        }
        status.text = I18n.t("status.searching")
        val src = current
        async({ src.search(q) }) { r ->
            if (id != searchId) return@async
            shown = r.getOrDefault(emptyList())
            counts[src] = shown.size
            tableModel.rowCount = 0
            shown.forEachIndexed { i, e -> tableModel.addRow(arrayOf<Any>(i + 1, e.title, e.subtitle, e.duration)) }
            status.text = r.fold({ if (it.isEmpty()) I18n.t("status.noResults", q) else I18n.t("status.results", it.size) }, { I18n.t("status.searchError", it.message ?: "") })
            refresh()
        }
    }

    fun run(mode: Mode) {
        val e = selected() ?: return
        val src = current
        downloading = true; refresh()
        bar.isIndeterminate = false; bar.value = 0
        status.text = I18n.t("status.downloading", e.title)
        async({
            val file = e.download { p -> SwingUtilities.invokeLater { if (p < 0) bar.isIndeterminate = true else { bar.isIndeterminate = false; bar.value = p } } }
            SwingUtilities.invokeLater { bar.isIndeterminate = true; status.text = I18n.t("status.adding") }
            Soundpad.add(file, src.category, mode != Mode.ADD, mode == Mode.PLAY_DELETE)
            if (mode == Mode.PLAY_DELETE && Settings["deleteFile", "true"] == "true") file.delete()
        }) { r ->
            downloading = false
            bar.isIndeterminate = false; bar.value = if (r.isSuccess) 100 else 0
            status.text = r.fold({
                if (mode == Mode.PLAY_DELETE) I18n.t("status.playedDeleted", e.title) else I18n.t("status.added", src.category, e.title)
            }, { I18n.t("status.error", it.message ?: "") })
            refresh()
        }
    }

    fun preview() {
        Preview.play(selected()?.previewUrl ?: return) { err ->
            if (err != null) status.text = I18n.t("status.previewError", err.message ?: "")
        }
    }

    fun settings() = showPreferences(frame, manager) { rebuild -> if (rebuild) onRebuild() else refresh() }

    val debounce = Timer(400) { search() }.apply { isRepeats = false }
    query.document.addDocumentListener(object : javax.swing.event.DocumentListener {
        override fun insertUpdate(e: javax.swing.event.DocumentEvent) = debounce.restart()
        override fun removeUpdate(e: javax.swing.event.DocumentEvent) = debounce.restart()
        override fun changedUpdate(e: javax.swing.event.DocumentEvent) = debounce.restart()
    })
    query.addActionListener { debounce.stop(); search() }
    searchBtn.addActionListener { debounce.stop(); search() }
    addBtn.addActionListener { run(Mode.ADD) }
    playBtn.addActionListener { run(Mode.PLAY) }
    delBtn.addActionListener { run(Mode.PLAY_DELETE) }
    previewBtn.addActionListener { preview() }
    stopBtn.addActionListener { Preview.stop() }
    wrench.addActionListener { settings() }
    sourceList.addListSelectionListener {
        if (it.valueIsAdjusting || sourceList.selectedValue == null || sourceList.selectedValue === current) return@addListSelectionListener
        Preview.stop(); current = sourceList.selectedValue; manager.selectedId = current.id; search()
    }
    table.selectionModel.addListSelectionListener { Preview.stop(); refresh() }
    table.addMouseListener(object : java.awt.event.MouseAdapter() {
        override fun mouseClicked(e: java.awt.event.MouseEvent) { if (e.clickCount == 2) preview() }
    })
    table.addKeyListener(object : java.awt.event.KeyAdapter() {
        override fun keyPressed(e: java.awt.event.KeyEvent) { if (e.keyCode == java.awt.event.KeyEvent.VK_ENTER) run(Mode.ADD) }
    })

    fun item(text: String, key: String? = null, action: () -> Unit) = JMenuItem(text).apply {
        key?.let { accelerator = KeyStroke.getKeyStroke(it) }
        addActionListener { action() }
    }
    frame.jMenuBar = JMenuBar().apply {
        add(JMenu(I18n.t("menu.file")).apply { add(item(I18n.t("menu.file.settings"), "ctrl COMMA") { settings() }); addSeparator(); add(item(I18n.t("menu.file.exit")) { frame.dispose(); System.exit(0) }) })
        add(JMenu(I18n.t("menu.play")).apply {
            add(item(I18n.t("menu.play.download")) { run(Mode.ADD) })
            add(item(I18n.t("menu.play.downloadPlay")) { run(Mode.PLAY) })
            add(item(I18n.t("menu.play.playDelete")) { run(Mode.PLAY_DELETE) })
            addSeparator()
            add(item(I18n.t("menu.play.preview")) { preview() }); add(item(I18n.t("menu.play.stopPreview"), "ESCAPE") { Preview.stop() })
        })
        add(JMenu(I18n.t("menu.help")).apply {
            add(item(I18n.t("menu.help.update")) { checkForUpdates(frame, silent = false) })
            add(item(I18n.t("menu.help.about")) { JOptionPane.showMessageDialog(frame, I18n.t("about.text", BuildInfo.version), I18n.t("about.title"), JOptionPane.INFORMATION_MESSAGE) })
        })
    }

    refresh()
    return JPanel(BorderLayout()).apply {
        background = BG
        add(toolbar, BorderLayout.NORTH)
        add(left, BorderLayout.WEST)
        add(scroll, BorderLayout.CENTER)
        add(south, BorderLayout.SOUTH)
    }
}
