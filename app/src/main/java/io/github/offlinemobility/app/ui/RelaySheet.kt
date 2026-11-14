package io.github.offlinemobility.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import io.github.offlinemobility.app.TxState
import java.util.Locale
import kotlin.math.floor

/**
 * QR code of the last sent message for a person without the app: their phone opens the link where
 * it has a connection and the rescue server's page hands the message in.
 */
@Composable
internal fun RelaySheet(tx: TxState, onClose: () -> Unit) {
    val c = LocalAppColors.current
    val url = tx.relayUrl
    val frame = tx.lastFrame
    if (url == null || frame == null) return
    val message = frame.message
    SheetScrim(onClose) {
        Sheet(width = 1100.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QrCode(url, 460.dp)
                Spacer(Modifier.width(32.dp))
                Column(Modifier.weight(1f)) {
                    Text("휴대폰으로 전달", fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
                    Text("이 앱이 없는 사람도 구조 요청을 대신 전해 줄 수 있어요".keepWords(), fontSize = Type.body, lineHeight = Type.lines(Type.body), color = c.body)
                    Spacer(Modifier.height(12.dp))
                    GuideLine("1", "휴대폰 카메라로 왼쪽 그림을 찍어요", c.ink)
                    GuideLine("2", "인터넷이 되는 곳에서 그 주소를 열어요", c.ink)
                    GuideLine("3", "‘전달하기’를 누르면 구조 서버로 전해져요", c.ink)
                    Spacer(Modifier.height(12.dp))
                    Divider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        titleWithPeople(message, frame.people),
                        fontSize = Type.title,
                        fontWeight = FontWeight.Bold,
                        color = c.ink,
                    )
                    // Shown as numbers so the person can also read the position out over the phone.
                    Text(
                        frame.location?.let { String.format(Locale.ROOT, "위치 %.5f, %.5f", it.latitude, it.longitude) } ?: "위치는 담기지 않았어요",
                        fontSize = Type.body,
                        color = c.body,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            AppButton("닫기", onClose, Kind.SECONDARY, Modifier.fillMaxWidth())
        }
    }
}

/** Always dark on white with the standard four-module margin, in both day and night themes. */
@Composable
private fun QrCode(text: String, size: Dp) {
    val matrix = remember(text) { runCatching { Encoder.encode(text, ErrorCorrectionLevel.M).matrix }.getOrNull() }
    Canvas(Modifier.size(size).background(Color.White)) {
        val m = matrix ?: return@Canvas
        val modules = m.width + 2 * QUIET_MODULES
        // Whole pixels per module keep the edges sharp for phone cameras.
        val cell = floor(this.size.width / modules)
        val origin = (this.size.width - cell * modules) / 2 + cell * QUIET_MODULES
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.get(x, y).toInt() == 1) {
                    drawRect(Color.Black, Offset(origin + x * cell, origin + y * cell), androidx.compose.ui.geometry.Size(cell, cell))
                }
            }
        }
    }
}

private const val QUIET_MODULES = 4
