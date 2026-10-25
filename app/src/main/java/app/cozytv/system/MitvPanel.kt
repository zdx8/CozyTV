package app.cozytv.system

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import android.util.Log

/**
 * MiTV panel backlight through the firmware's mitv middleware.
 *
 * Adapted from Aerial TV's MitvPanel (MIT, Copyright 2026 pengchujin),
 * verified there on MiTV-ASTP0. The panel level lives in the mitv
 * TvService; the System setting alone does not move the backlight.
 */
object MitvPanel {
    private const val TAG = "CozyTVMitvPanel"
    private const val SETTING = "backLightForNormal"
    private const val ITV_SERVICE = "mitv.internal.ITvService"
    private const val ITV_SETTING_MANAGER = "mitv.impl.tv.ITvSettingManager"
    private const val ACCESSORY_NAME = "TvSettingService"
    private const val TX_GET_ACCESSORY_SERVICE = 80
    private const val TX_GET_BACKLIGHT_VALUE = 43
    private const val TX_SET_BACKLIGHT_VALUE = 42
    private const val PROBE_STEP = 1
    private const val PROBE_TOLERANCE = 2

    private val UNTESTED_MODELS = setOf("MiTV-MFTP0", "MiTV_MFTP0")
    private var verified: Boolean? = null

    @Synchronized
    fun usable(context: Context): Boolean {
        verified?.let { return it }
        val ok = run {
            if (Build.MODEL in UNTESTED_MODELS) {
                Log.w(TAG, "model ${Build.MODEL} is not probed")
                return@run false
            }
            // Some ASTP0 firmware reports no backLightForNormal value while its
            // TvService remains usable, so the reversible transaction probe is
            // the authoritative compatibility check.
            val before = readSettled()
            if (before == null) {
                Log.w(TAG, "backlight read unavailable")
                return@run false
            }
            if (before !in 1..100) return@run false
            val step = if (before >= 50) -PROBE_STEP else PROBE_STEP
            setDelta(step)
            val moved = readSettled()
            setDelta(-step)
            val restored = readSettled()
            val matches = moved != null && restored != null &&
                kotlin.math.abs(moved - (before + step)) <= PROBE_TOLERANCE &&
                kotlin.math.abs(restored - before) <= PROBE_TOLERANCE
            if (!matches) {
                Log.w(TAG, "backlight transactions unverified ($before -> $moved -> $restored)")
            } else {
                Log.i(TAG, "backlight transactions verified at $before")
            }
            matches
        }
        verified = ok
        return ok
    }

    @Synchronized
    fun level(context: Context): Int? = readBacklight() ?: settingLevel(context)

    fun setBacklight(context: Context, from: Int, to: Int) {
        val value = to.coerceIn(1, 100)
        val current = from.coerceIn(1, 100)
        val delta = value - current
        if (delta != 0) setDelta(delta)
        // Keep the firmware setting in sync when this build permits the write.
        // A third-party launcher is often refused, which is non-fatal because
        // TvService has already moved the physical panel.
        runCatching {
            Settings.System.putInt(context.contentResolver, SETTING, value)
        }.onFailure { Log.d(TAG, "setting write refused: $it") }
    }

    private fun settingLevel(context: Context): Int? =
        runCatching {
            Settings.System.getInt(context.contentResolver, SETTING, 50)
        }.getOrNull()

    private fun readBacklight(): Int? {
        val service = settingService() ?: return null
        return runCatching {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ITV_SETTING_MANAGER)
                service.transact(TX_GET_BACKLIGHT_VALUE, data, reply, 0)
                reply.readException()
                reply.readInt()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }.onFailure { Log.w(TAG, "getBacklightStatus failed: $it") }.getOrNull()
    }

    private fun readSettled(): Int? {
        var last: Int? = null
        repeat(3) { attempt ->
            val value = readBacklight() ?: return last
            if (value == last) return value
            last = value
            if (attempt < 2) runCatching { Thread.sleep(30) }
        }
        return last
    }

    private fun setDelta(delta: Int) {
        val service = settingService() ?: return
        runCatching {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ITV_SETTING_MANAGER)
                data.writeInt(delta)
                service.transact(TX_SET_BACKLIGHT_VALUE, data, reply, 0)
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }.onFailure { Log.w(TAG, "setBacklightValue failed: $it") }
    }

    @SuppressLint("PrivateApi")
    private fun settingService(): IBinder? {
        val tvService = runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            getService.invoke(null, "TvService") as? IBinder
        }.onFailure { Log.w(TAG, "TvService lookup failed: $it") }.getOrNull()
        if (tvService == null) {
            Log.w(TAG, "TvService binder missing")
            return null
        }
        return runCatching {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ITV_SERVICE)
                data.writeString(ACCESSORY_NAME)
                tvService.transact(TX_GET_ACCESSORY_SERVICE, data, reply, 0)
                reply.readException()
                reply.readStrongBinder()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }.onFailure { Log.w(TAG, "accessory lookup failed: $it") }.getOrNull()
    }
}
