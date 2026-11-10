package de.eincybersimon.onetune

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Invisible share target: turns a streaming-app share into a onetune link and re-shares it. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val track = MusicLinks.findIn(sharedText)
        if (track == null) {
            Toast.makeText(this, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
        } else {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(
                    Intent.EXTRA_TEXT,
                    MusicLinks.shareUrl(ServerPreference(this).linkHost, track)
                )
            val chooser = Intent.createChooser(send, null)
                .putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(this, ShareActivity::class.java))
                )
            startActivity(chooser)
        }
        finish()
    }
}
