package com.github.imflog.schema.registry

import org.slf4j.Logger

object LoggingUtils {

    var quietLogging: Boolean = false

    /**
     * Utility method that checks if the quiet logging is activated before logging.
     * This is needed because we cannot set a log level per task.
     * See https://github.com/gradle/gradle/issues/1010
     */
    fun Logger.infoIfNotQuiet(message: String) {
        if (!quietLogging) this.info(message)
    }
}