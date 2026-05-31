package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import android.view.Surface
import java.util.concurrent.ConcurrentHashMap

object MPVLib {
    private const val TAG = "MPVLib"
    private val observedDoubles = ConcurrentHashMap<String, Double>()
    private val observedBooleans = ConcurrentHashMap<String, Boolean>()
    private val observedStrings = ConcurrentHashMap<String, String>()

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

    fun observedDouble(name: String): Double? = observedDoubles[name]

    fun observedBoolean(name: String): Boolean? = observedBooleans[name]

    fun observedString(name: String): String? = observedStrings[name]

    @JvmStatic fun eventProperty(name: String) = Unit

    @JvmStatic fun eventProperty(name: String, value: Boolean) {
        observedBooleans[name] = value
    }

    @JvmStatic fun eventProperty(name: String, value: Long) {
        observedDoubles[name] = value.toDouble()
    }

    @JvmStatic fun eventProperty(name: String, value: Double) {
        observedDoubles[name] = value
    }

    @JvmStatic fun eventProperty(name: String, value: String?) {
        if (value == null) {
            observedStrings.remove(name)
        } else {
            observedStrings[name] = value
        }
    }

    @JvmStatic fun event(eventId: Int) = Unit

    @JvmStatic fun logMessage(prefix: String, level: Int, text: String) {
        Log.d(TAG, "[$prefix/$level] $text")
    }
}
