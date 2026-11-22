package com.payloadtunnel.ui

import android.app.Activity
import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.payloadtunnel.core.AppLog
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

class AdManager(private val activity: Activity) {

    private var interstitialAd: StartAppAd? = null

    init {
        preloadInterstitial()
    }

    fun showRewarded(onLoading: (Boolean) -> Unit, onReward: () -> Unit, onError: (String) -> Unit) {
        val ad = StartAppAd(activity)
        var rewarded = false
        ad.setVideoListener(VideoListener { rewarded = true })
        onLoading(true)
        ad.loadAd(
            StartAppAd.AdMode.REWARDED_VIDEO,
            object : AdEventListener {
                override fun onReceiveAd(a: Ad) {
                    onLoading(false)
                    val shown = ad.showAd(object : AdDisplayListener {
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

    fun showInterstitial() {
        val ad = interstitialAd ?: return
        ad.showAd(object : AdDisplayListener {
            override fun adHidden(a: Ad?) { preloadInterstitial() }
            override fun adDisplayed(a: Ad?) {}
            override fun adClicked(a: Ad?) {}
            override fun adNotDisplayed(a: Ad?) { preloadInterstitial() }
        })
        interstitialAd = null
    }

    private fun preloadInterstitial() {
        val ad = StartAppAd(activity)
        ad.loadAd(object : AdEventListener {
            override fun onReceiveAd(a: Ad) { interstitialAd = ad }
            override fun onFailedToReceiveAd(a: Ad?) {
                AppLog.dbg("interstitial preload failed: ${a?.errorMessage}")
            }
        })
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            val container = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            val banner = Banner(ctx, object : BannerListener {
                override fun onReceiveAd(a: android.view.View?) {}
                override fun onFailedToReceiveAd(a: android.view.View?) {
                    AppLog.dbg("banner load failed")
                }
                override fun onImpression(a: android.view.View?) {}
                override fun onClick(a: android.view.View?) {}
            })
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER }
            container.addView(banner, params)
            container
        },
        modifier = modifier.fillMaxWidth().height(56.dp),
    )
}
