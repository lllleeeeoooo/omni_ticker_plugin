package com.omniticker.ui

import com.intellij.ui.JBColor
import java.awt.Color

/** A-share convention: RED = up, GREEN = down. Brightened for dark themes. */
object QuoteColors {
    val up: Color = JBColor(Color(0xC62828), Color(0xE57373))
    val down: Color = JBColor(Color(0x2E7D32), Color(0x66BB6A))
    val flat: Color = JBColor(Color(0x909090), Color(0xBDBDBD))

    fun forChange(change: Double): Color = when {
        change > 0 -> up
        change < 0 -> down
        else -> flat
    }

    fun forPercent(percent: Double): Color = forChange(percent)
}