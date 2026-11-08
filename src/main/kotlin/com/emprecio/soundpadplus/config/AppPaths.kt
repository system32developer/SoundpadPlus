package com.emprecio.soundpadplus.config

import java.io.File

object AppPaths {
    val home = File(System.getProperty("user.home"), ".soundpadplus")
    val plugins = File(home, "plugins")
    val languages = File(home, "lang")
}
