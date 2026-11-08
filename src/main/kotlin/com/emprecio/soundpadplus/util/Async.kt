package com.emprecio.soundpadplus.util

import javax.swing.*

fun <T> async(work: () -> T, done: (Result<T>) -> Unit) = Thread {
    val r = runCatching(work)
    SwingUtilities.invokeLater { done(r) }
}.start()
