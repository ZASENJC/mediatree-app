package com.zasenjc.mediatree.player

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

class MpvPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    var controller: MpvPlayerController? = null
        set(value) {
            if (field === value) return
            field?.detachSurface()
            field = value
            if (holder.surface?.isValid == true) {
                value?.attachSurface(holder.surface)
            }
        }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        controller?.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        controller?.detachSurface()
    }

    override fun onDetachedFromWindow() {
        controller?.detachSurface()
        super.onDetachedFromWindow()
    }
}
