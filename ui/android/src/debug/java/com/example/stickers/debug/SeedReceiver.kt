package com.example.stickers.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.stickers.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only. Fills the library for screenshots:
 * `adb shell am broadcast -n com.example.stickers/.debug.SeedReceiver --ez clear true`
 * Add `--ez seed false` to only clear. Add `--es import_dir <folder>` to import real images instead (FolderImport).
 */
class SeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = (context.applicationContext as App).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.getBooleanExtra("clear", false)) SeedData.clear(container)
                val dir = intent.getStringExtra("import_dir")
                if (dir != null) {
                    // Folder must be readable by the app, e.g. /sdcard/Android/data/<package>/files/import
                    Log.i(TAG, "imported ${FolderImport.run(container, java.io.File(dir))} stickers from $dir")
                } else if (intent.getBooleanExtra("seed", true)) {
                    Log.i(TAG, "seeded ${SeedData.seed(container)} stickers")
                }
            } catch (e: Exception) {
                Log.e(TAG, "seed failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SeedData"
    }
}
