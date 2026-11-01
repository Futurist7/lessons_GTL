package com.linguascreen.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.linguascreen.service.OverlayService

/**
 * Invisible activity that shows Android's screen-capture consent dialog and hands the result to
 * [OverlayService]. Android requires this dialog; it cannot and must not be bypassed.
 */
class CaptureConsentActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            OverlayService.deliverProjection(this, result.resultCode, result.data!!)
        } else {
            OverlayService.deliverProjectionDenied(this)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            launcher.launch(mgr.createScreenCaptureIntent())
        }
    }
}
