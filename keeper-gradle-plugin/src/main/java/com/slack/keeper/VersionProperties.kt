package com.slack.keeper

import java.util.Properties

internal class VersionProperties : Properties() {
    init {
        load(this.javaClass.getResourceAsStream("/keeper-gradle-plugin.properties"))
    }

    fun r8Version(): String = getProperty("keeper.r8_version")
}
