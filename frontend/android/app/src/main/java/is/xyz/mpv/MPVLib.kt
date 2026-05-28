package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import android.view.Surface

object MPVLib {
    private const val TAG = "MPVLib"

    init {
        System.loadLibrary("mpv")
        System.loadLibrary("player")
    }

    external fun create(context: Context)
    external fun init()
    external fun destroy()
    external fun command(args: Array<String>)
    external fun attachSurface(surface: Surface)
    external fun detachSurface()
    external fun setOptionString(name: String, value: String)
    external fun getPropertyInt(name: String): Int
    external fun getPropertyDouble(name: String): Double
    external fun getPropertyBoolean(name: String): Boolean
    external fun getPropertyString(name: String): String?
    external fun setPropertyInt(name: String, value: Int)
    external fun setPropertyDouble(name: String, value: Double)
    external fun setPropertyBoolean(name: String, value: Boolean)
    external fun setPropertyString(name: String, value: String)
    external fun observeProperty(name: String, format: Int)
    external fun grabThumbnail(width: Int): ByteArray?

    @JvmStatic fun eventProperty(name: String) = Unit

    @JvmStatic fun eventProperty(name: String, value: Boolean) = Unit

    @JvmStatic fun eventProperty(name: String, value: Long) = Unit

    @JvmStatic fun eventProperty(name: String, value: Double) = Unit

    @JvmStatic fun eventProperty(name: String, value: String?) = Unit

    @JvmStatic fun event(eventId: Int) = Unit

    @JvmStatic fun logMessage(prefix: String, level: Int, text: String) {
        Log.d(TAG, "[$prefix/$level] $text")
    }
}
