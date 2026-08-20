package com.alexmodzofc.tool.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PAUSE  = "com.alexmodzofc.tool.DOWNLOAD_PAUSE"
        const val ACTION_RESUME = "com.alexmodzofc.tool.DOWNLOAD_RESUME"
        const val EXTRA_ID = "download_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id == -1) return
        when (intent.action) {
            ACTION_PAUSE  -> AlexToolDownloadManager.pause(context, id)
            ACTION_RESUME -> AlexToolDownloadManager.resume(context, id)
        }
    }
}
