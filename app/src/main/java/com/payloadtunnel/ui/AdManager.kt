package com.payloadtunnel.ui

import android.app.Activity
import com.payloadtunnel.core.AppLog
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

/** Loads and shows a Start.io rewarded video; [onReward] fires only when the video completes. */
class AdManager(private val activity: Activity) {

    fun showRewarded(onLoading: (Boolean) -> Unit, onReward: () -> Unit, onError: (String) -> Unit) {
        runCatching {
            com.startapp.sdk.adsbase.StartAppSDK.setTestAdsEnabled(com.payloadtunnel.Graph.session.testAds)
        }
        val ad = StartAppAd(activity)
        var rewarded = false
        ad.setVideoListener(VideoListener { rewarded = true })
        onLoading(true)
        ad.loadAd(
            StartAppAd.AdMode.REWARDED_VIDEO,
            object : AdEventListener {
                override fun onReceiveAd(a: Ad) {
                    onLoading(false)
                    val shown = ad.showAd(object : com.startapp.sdk.adsbase.adlisteners.AdDisplayListener {
                        override fun adHidden(a2: Ad?) {
                            if (rewarded) activity.runOnUiThread { onReward() }
                            else activity.runOnUiThread { onError("Ad closed early — no time added.") }
                        }
                        override fun adDisplayed(a2: Ad?) {}
                        override fun adClicked(a2: Ad?) {}
                        override fun adNotDisplayed(a2: Ad?) {
                            activity.runOnUiThread { onError("The ad could not be shown. Try again.") }
                        }
                    })
                    if (!shown) {
                        onLoading(false)
                        onError("The ad could not be shown. Try again.")
                    }
                }

                override fun onFailedToReceiveAd(a: Ad?) {
                    AppLog.dbg("ad load failed: ${a?.errorMessage}")
                    onLoading(false)
                    onError("No ad available right now. Try again in a moment.")
                }
            },
        )
    }
}
