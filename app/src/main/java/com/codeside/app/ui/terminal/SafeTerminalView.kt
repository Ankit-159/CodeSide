package com.codeside.app.ui.terminal

import android.content.Context
import android.util.AttributeSet
import com.codeside.app.util.AppLog
import com.termux.view.TerminalView

class SafeTerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TerminalView(context, attrs) {
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        try {
            super.onSizeChanged(w, h, oldw, oldh)
        } catch (t: Throwable) {
            AppLog.logThrowable("TerminalView", "onSizeChanged crash ignored", t)
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        try {
            super.onDraw(canvas)
        } catch (t: Throwable) {
            AppLog.logThrowable("TerminalView", "onDraw crash ignored", t)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } catch (t: Throwable) {
            AppLog.logThrowable("TerminalView", "onMeasure crash ignored", t)
            super.setMeasuredDimension(
                android.view.View.MeasureSpec.getSize(widthMeasureSpec),
                android.view.View.MeasureSpec.getSize(heightMeasureSpec),
            )
        }
    }
}
