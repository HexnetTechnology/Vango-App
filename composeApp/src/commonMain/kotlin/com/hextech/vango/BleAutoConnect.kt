package com.hextech.vango

import com.hextech.vango.profile.LoginPreferences

/**
 * Tek merkezi BLE otomatik bağlanma.
 * Ortamda canlı (rssi dolu) tek IO varsa ona bağlanır; birden fazlaysa kayıtlı MAC'e kilitlenir.
 */
object BleAutoConnect {
    @Volatile
    private var lastAttemptAtMs: Long = 0L

    @Volatile
    private var lastAttemptMac: String? = null

    fun resetThrottle() {
        lastAttemptAtMs = 0L
        lastAttemptMac = null
    }

    fun tryAutoConnect(reason: String = "") {
        if (remoteModeEnabled.value) {
            println("BleAutoConnect skip ($reason): remote mode")
            return
        }
        if (!bleAutoReconnectEnabled.value) {
            println("BleAutoConnect skip ($reason): user disabled")
            return
        }
        if (isBleGattLinkReady()) return
        val pairPageSkip = isOnBleConnectionFlowPage() &&
            reason != "connection-page" &&
            reason != "mode-ble" &&
            reason != "refresh"
        if (pairPageSkip) {
            println("BleAutoConnect skip ($reason): on pair page")
            return
        }

        val state = vanGoGlobal.value.connectionState
        /* ChainEnded yalnizca GATT gercekten hazirsa "bagli" say — aksi halde auto-connect engellenmesin */
        if (state == BleGattConnectionChain.ConnectingGattServer ||
            state == BleGattConnectionChain.DiscoveringServices ||
            (state == BleGattConnectionChain.ChainEnded && isBleGattLinkReady())
        ) {
            return
        }

        val ioDevices = preferBleAdapterDevices(
            vangoDeviceList.filter { isSupportedBleDeviceName(it.name) },
        )
        // Yalnızca gerçek tarama sonuçları (önbellek/seed rssi=null — bağlanma tetiklemez)
        val live = ioDevices.filter { it.rssi != null }
        val lastMac = LoginPreferences.getCachedLastBleAdapterMac()
            ?.let { normalizeMacSafe(it) }

        val targetMac = when {
            // Canlı IO varsa her zaman ona (eski Display MAC cache'ini ez)
            live.size == 1 -> normalizeMacSafe(live.first().mac)
            live.size > 1 && lastMac != null && live.any { macsEqual(it.mac, lastMac) } -> lastMac
            live.size > 1 -> normalizeMacSafe(live.maxByOrNull { it.rssi ?: -999 }!!.mac)
            // Canlı yok: kayıtlı MAC ile dene (startup / keepalive / kopma sonrası)
            lastMac != null &&
                (reason == "discover-done" || reason == "discover-finished-ui" ||
                    reason == "retry-failed" || reason == "startup" ||
                    reason == "keepalive" || reason == "disconnect-retry") -> lastMac
            else -> null
        } ?: run {
            println(
                "BleAutoConnect skip ($reason): waiting live IO " +
                    "(live=${live.size} last=$lastMac state=$state)",
            )
            return
        }

        val now = getCurrentTimeInSeconds() * 1000L
        if (macsEqual(targetMac, lastAttemptMac ?: "") && now - lastAttemptAtMs < 5_000L) {
            return
        }
        lastAttemptAtMs = now
        lastAttemptMac = targetMac
        bleAutoReconnectEnabled.value = true
        println("🔗 BleAutoConnect ($reason) → $targetMac (live=${live.map { it.mac }})")
        getVanGO().connectByMac(targetMac)
    }

    private fun normalizeMacSafe(mac: String): String? {
        val n = com.hextech.vango.profile.normalizeMacForDisplay(mac)
        return n.takeIf { it.length == 17 && com.hextech.vango.profile.isValidMacFormat(it) }
    }
}
