package `is`.xyz.mpv

import android.view.Surface

object MPVLib {
    init {
        System.loadLibrary("mpv")
        System.loadLibrary("player")
    }

    external fun create()
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
}
