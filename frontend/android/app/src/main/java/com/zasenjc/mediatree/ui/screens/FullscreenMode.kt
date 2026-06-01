package com.zasenjc.mediatree.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import com.zasenjc.mediatree.data.FullscreenModePreference

fun requestFullscreenOrientation(activity: Activity?, preference: FullscreenModePreference) {
    activity?.requestedOrientation = when (preference) {
        FullscreenModePreference.Portrait -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        FullscreenModePreference.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        FullscreenModePreference.Auto -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }
}
