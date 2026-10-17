package io.github.prismtv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.prismtv.BuildConfig
import io.github.prismtv.R
import io.github.prismtv.player.ExternalPlayers
import io.github.prismtv.ui.MainViewModel
import io.github.prismtv.ui.components.Panel

@Composable
fun AboutScreen(vm: MainViewModel, bottomPadding: PaddingValues) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("About", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = bottomPadding.calculateBottomPadding() + 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Image(painterResource(R.drawable.app_logo), null, Modifier.size(96.dp).clip(RoundedCornerShape(26.dp)))
            Spacer(Modifier.height(12.dp))
            Text("Prism TV", style = MaterialTheme.typography.headlineMedium)
            Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Prism TV is a free and open-source player for publicly available TV channels from around the world.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "The channel list comes from the iptv-org community project. Prism TV does not host, store or " +
                            "control any of the streams; they belong to their broadcasters. If a channel should not be listed, " +
                            "please raise it with iptv-org directly.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Released under the MIT License.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = { ExternalPlayers.openUrl(ctx, BuildConfig.REPO_URL) }, modifier = Modifier.fillMaxWidth()) { Text("Source code") }
            FilledTonalButton(onClick = { ExternalPlayers.openUrl(ctx, BuildConfig.REPO_URL + "/issues") }, modifier = Modifier.fillMaxWidth()) { Text("Report a problem") }
            FilledTonalButton(onClick = { ExternalPlayers.openUrl(ctx, "https://github.com/iptv-org/iptv") }, modifier = Modifier.fillMaxWidth()) { Text("iptv-org/iptv") }
            Spacer(Modifier.height(16.dp))
            Text(
                "Built with Jetpack Compose, Media3 ExoPlayer, OkHttp and Coil.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}
