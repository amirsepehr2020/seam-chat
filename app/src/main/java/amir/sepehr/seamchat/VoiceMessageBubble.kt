package amir.sepehr.seamchat

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VoiceMessageBubble(url:String) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth().padding(vertical=2.dp)) {
        IconButton(onClick={
            if (playing) { player?.pause(); playing=false }
            else {
                player?.release()
                player=MediaPlayer().apply {
                    setDataSource(url)
                    setOnPreparedListener { it.start(); playing=true }
                    setOnCompletionListener { playing=false; release(); player=null }
                    prepareAsync()
                }
            }
        }) { Icon(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow, if(playing) "Pause" else "Play") }
        Column(Modifier.weight(1f)) {
            Text("Voice message", fontSize=13.sp)
            Text(if(playing) "Playing…" else "Tap to play", fontSize=10.sp, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
