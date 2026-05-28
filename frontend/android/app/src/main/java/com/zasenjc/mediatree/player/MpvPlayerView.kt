package com.zasenjc.mediatree.player

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

class MpvPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    private var attachedController: MpvPlayerController? = null

    var controller: MpvPlayerController? = null
        set(value) {
            if (field === value) return
            detachCurrentSurface()
            field = value
            if (holder.surface?.isValid == true) {
                attachCurrentSurface(value)
            }
        }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        attachCurrentSurface(controller)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        detachCurrentSurface()
    }

    override fun onDetachedFromWindow() {
        detachCurrentSurface()
        super.onDetachedFromWindow()
    }

    private fun attachCurrentSurface(target: MpvPlayerController?) {
        val surface = holder.surface ?: return
        if (!surface.isValid || target == null || attachedController === target) return
        target.attachSurface(surface)
        attachedController = target
    }

    private fun detachCurrentSurface() {
        attachedController?.detachSurface()
        attachedController = null
    }
}
