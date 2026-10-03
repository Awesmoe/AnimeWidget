package com.awesmoe.animewidget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

fun createMoeListIntent(animeId: Int): Intent {
    return Intent().apply {
        setClassName(
            "com.awesmoe.moelist",
            // applicationId was changed in the fork, but the code namespace is still com.axiel7.moelist
            "com.axiel7.moelist.ui.main.MainActivity"
        )
        action = "details"
        putExtra("media_id", animeId)
        putExtra("media_type", "anime")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        addCategory(animeId.toString())
    }
}

fun createMalWebIntent(animeId: Int): Intent {
    return Intent(Intent.ACTION_VIEW).apply {
        data = "https://myanimelist.net/anime/$animeId".toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

fun isMoeListInstalled(context: Context): Boolean {
    return context.packageManager.getLaunchIntentForPackage("com.awesmoe.moelist") != null
}
