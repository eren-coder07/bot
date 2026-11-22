package com.payloadtunnel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.payloadtunnel.Graph
import com.payloadtunnel.core.Status
import com.payloadtunnel.core.TunnelState
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the main screens to PNG for visual review: ./gradlew testDebugUnitTest -Pscreenshots */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    private val out = System.getProperty("user.dir") + "/build/screenshots"

    private fun setup(connected: Boolean) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Graph.init(app)
        if (Graph.profiles.profiles.value.none { it.name == "My server" }) {
            Graph.profiles.save(Profile(name = "My server", sshHost = "de1.example.com", sshPort = 443))
        }
        Graph.profiles.select("freevip-iam6-ip10")
        TunnelStatus.update {
            if (connected) TunnelState(Status.CONNECTED, profileName = "IAM*6 IP 10", connectedAt = System.currentTimeMillis() - 2_936_000, socksPort = 1080, pingMs = 167)
            else TunnelState()
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("$out/$name.png") {
            AppTheme { AppBackground { Box(Modifier.fillMaxSize()) { content() } } }
        }
    }

    private fun fakeTraffic() = Traffic().apply {
        downSpeed = 5_285_000; upSpeed = 412_000; peakDown = 6_900_000
        down = 734_000_000; up = 52_000_000
        history = List(40) { i -> (2_000_000 + 3_000_000 * kotlin.math.sin(i / 4.0).let { it * it }).toLong() }
    }

    @Test fun home_connected() {
        setup(true)
        shot("1_home_connected") {
            Column { Box(Modifier.weight(1f)) { HomeScreen(fakeTraffic(), {}, {}, {}, {}, {}) } }
        }
    }

    @Test fun home_disconnected() {
        setup(false)
        shot("2_home_disconnected") { HomeScreen(Traffic(), {}, {}, {}, {}, {}) }
    }

    @Test fun speed() {
        setup(true)
        shot("3_speed") { SpeedScreen(fakeTraffic(), {}) }
    }

    @Test fun servers() {
        setup(true)
        shot("4_servers") { ProfilesScreen({}, {}) }
    }

    @Test fun settings() {
        setup(false)
        shot("5_settings") { SettingsScreen({}, {}) }
    }

    @Test fun editor() {
        setup(false)
        shot("6_editor") { ProfileEditor(Profile.defaultProfile(), {}, {}) }
    }
}
