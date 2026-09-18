package com.shnapps.couple.feature.pairing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * A QR code of the invite link, drawn directly as modules rather than via a bitmap.
 *
 * Dark modules on a light plate, deliberately against the dark theme: plenty of scanners
 * still fail on inverted (light-on-dark) codes, and a QR that does not scan is worse than
 * one that looks slightly out of place.
 */
@Composable
internal fun QrCode(
    content: String,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val matrix = remember(content) {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
            ),
        )
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(PLATE)
            .padding(12.dp)
            .semantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cell = this.size.width / matrix.width
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (matrix.get(x, y)) {
                        drawRect(
                            color = MODULE,
                            topLeft = Offset(x * cell, y * cell),
                            // Slight overdraw closes hairline gaps between adjacent modules.
                            size = Size(cell + 0.5f, cell + 0.5f),
                        )
                    }
                }
            }
        }
    }
}

private const val QUIET_ZONE_MODULES = 1
private val PLATE = Color(0xFFF2E6DA)
private val MODULE = Color(0xFF0B0708)
