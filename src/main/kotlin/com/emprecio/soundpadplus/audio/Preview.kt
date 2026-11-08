package com.emprecio.soundpadplus.audio

import com.emprecio.soundpadplus.api.Downloads
import javax.swing.*

object Preview {
    private var player: javazoom.jl.player.Player? = null
    private var token = 0

    fun stop() { token++; player?.close(); player = null }

    fun play(url: String, done: (Throwable?) -> Unit) {
        stop()
        val mine = token
        Thread {
            val err = runCatching {
                Downloads.open(url).inputStream.buffered().use { s ->
                    val p = javazoom.jl.player.Player(s)
                    if (mine == token) { player = p; p.play() }
                }
            }.exceptionOrNull()
            SwingUtilities.invokeLater { if (mine == token) done(err) }
        }.start()
    }
}
