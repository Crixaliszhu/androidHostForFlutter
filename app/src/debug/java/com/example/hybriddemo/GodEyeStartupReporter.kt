package com.example.hybriddemo

import cn.hikyson.godeye.core.GodEyeHelper
import cn.hikyson.godeye.core.exceptions.UninstallException
import cn.hikyson.godeye.core.internal.modules.startup.StartupInfo

object GodEyeStartupReporter {
    private var reported = false

    fun reportColdStart(costMillis: Long) {
        if (reported) return
        reported = true
        try {
            GodEyeHelper.onAppStartEnd(StartupInfo(StartupInfo.StartUpType.COLD, costMillis))
        } catch (_: UninstallException) {
            // GodEye debug module may be disabled or not installed yet; startup reporting is best effort.
        }
    }
}
