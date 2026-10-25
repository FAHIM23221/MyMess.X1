package com.smarttransparentmess.erp.ui.profile

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smarttransparentmess.erp.R

@Composable
fun AppInformationScreen(
    versionName: String,
    onBack: () -> Unit,
    onAdminEntry: () -> Unit,
) {
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(112.dp)
                .clickable {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastTapAt > TAP_WINDOW_MS) tapCount = 0
                    lastTapAt = now
                    tapCount += 1
                    if (tapCount >= REQUIRED_TAPS) {
                        tapCount = 0
                        onAdminEntry()
                    }
                },
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(32.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("ST", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("SMART TRANSPARENT MESS ERP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.app_information_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.version_format, versionName), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(30.dp))
        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.close)) }
    }
}

private const val REQUIRED_TAPS = 7
private const val TAP_WINDOW_MS = 5_000L
