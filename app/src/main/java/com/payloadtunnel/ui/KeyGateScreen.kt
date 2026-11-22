package com.payloadtunnel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payloadtunnel.Graph
import com.payloadtunnel.data.Session
import kotlinx.coroutines.launch

/** Full-screen sign-in: enter the access key from your provider. Nothing else is needed. */
@Composable
fun KeyGateScreen() {
    val state by Graph.session.state.collectAsState()
    val announcement by Graph.session.announcement.collectAsState()
    var key by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        if (key.isBlank() || state == Session.State.CHECKING) return
        keyboard?.hide()
        error = null
        scope.launch { error = Graph.signIn(key) }
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(Lime.copy(alpha = 0.14f))
                .border(1.5.dp, Lime.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Bolt, null, tint = Lime, modifier = Modifier.size(38.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Payload Tunnel", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Enter your access key to start", color = TextSecondary, fontSize = 13.sp)

        Spacer(Modifier.height(30.dp))

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Lime.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.VpnKey, null, tint = Lime, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (key.isEmpty()) Text("e.g. AB12-CD34-EF56", color = TextSecondary, fontSize = 15.sp)
                BasicTextField(
                    key, { key = it }, singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    cursorBrush = SolidColor(Lime),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when (state) {
            Session.State.CHECKING -> Row(
                Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Lime, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text("Checking your key…", color = TextSecondary, fontSize = 13.sp)
            }
            Session.State.INVALID -> GateNote(error ?: "That key isn't valid. Double-check it and try again.")
            Session.State.BANNED -> GateNote(error ?: "This key has been blocked. Contact your provider.")
            Session.State.OFFLINE -> GateNote(error ?: "Can't reach the server. Check your internet and try again.")
            else -> {}
        }

        if (announcement.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Campaign, null, tint = Lime, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(announcement, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        val canGo = key.isNotBlank() && state != Session.State.CHECKING
        Surface(
            onClick = { submit() },
            enabled = canGo,
            shape = RoundedCornerShape(16.dp),
            color = if (canGo) Lime else Lime.copy(alpha = 0.25f),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "Continue",
                    color = if (canGo) OnLime else OnLime.copy(alpha = 0.6f),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("No key yet? Get one from your provider.", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GateNote(text: String) {
    GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, null, tint = Failed, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}
