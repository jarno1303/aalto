package fi.aalto.radio.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aalto.radio.AaltoBlue
import fi.aalto.radio.AaltoNightBlack
import fi.aalto.radio.AaltoTheme
import fi.aalto.radio.R
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Full-screen wake-up view, shown over the lock screen. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AaltoTheme(darkTheme = true) {
                val ringing = AlarmRuntime.ringing
                LaunchedEffect(ringing) {
                    if (!ringing) finish()
                }
                AlarmScreen(
                    stationName = AlarmRuntime.stationName,
                    usingFallback = AlarmRuntime.usingFallback,
                    onSnooze = { send(AlarmService.ACTION_SNOOZE) },
                    onDismiss = { send(AlarmService.ACTION_DISMISS) },
                    onContinue = { send(AlarmService.ACTION_CONTINUE) }
                )
            }
        }
    }

    private fun send(action: String) {
        AlarmService.send(this, action)
        finish()
    }
}

@Composable
private fun AlarmScreen(
    stationName: String,
    usingFallback: Boolean,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AaltoNightBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.alarm_title),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = formatClock(now.hour, now.minute),
            color = Color.White,
            fontSize = 72.sp,
            fontWeight = FontWeight.Light
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (usingFallback) {
                stringResource(R.string.alarm_fallback_note)
            } else {
                stationName
            },
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(56.dp))

        Button(
            onClick = onSnooze,
            colors = ButtonDefaults.buttonColors(containerColor = AaltoBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text(
                text = stringResource(R.string.alarm_snooze_minutes, AlarmService.SNOOZE_MINUTES),
                fontSize = 20.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(text = stringResource(R.string.alarm_continue), fontSize = 17.sp, color = Color.White)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(text = stringResource(R.string.alarm_dismiss), fontSize = 17.sp, color = Color.White)
        }
    }
}
