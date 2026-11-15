package com.hextech.vango

import com.hextech.vango.profile.LoginPreferences

/**
 * BLE / MQTT / UI [DevConfig] ile IO modül flash profilini hizalar.
 */
fun applyIoProfileToUi(devConfig: DevConfig, profile: IoProfileSnapshot): DevConfig =
    mergeDevConfigWithIoProfile(devConfig, profile)

fun applyVanGoBleProfileToUi(data: VanGoData) {
    val fields = IoProfileBleFields(
        motorEn = data.motorEn,
        rgbEn = data.rgbEn,
        tankN = data.tankN,
        relayMask = data.relayMask,
        dimMask = data.dimMask,
        sensorMask = data.sensorMask,
        tankMask = data.tankMask,
        ioLink = data.ioLink,
        io = data.io,
    )
    val snap = fields.toSnapshot() ?: return
    val seq = data.panelSeq?.takeIf { it > 0 } ?: snap.panelSeq
    val changed = data.panelChanged == true || snap.panelChanged
    val enriched = if (seq > 0 || snap.channels != null) {
        snap.copy(panelSeq = seq, panelChanged = changed)
    } else {
        snap
    }
    if (!PanelSettingsPlatformSync.shouldPreservePlatformPanelLayout()) {
        val ui = uiState.value
        uiState.value = ui.copy(devConfig = applyIoProfileToUi(ui.devConfig, enriched))
    } else {
        /* Maske + isim/ikon IO SoT (BLE baglantida ikonlari da cek). */
        applyIoEnableMasksFromDevice(enriched)
    }
    applyIoChannelLabelsFromDevice(enriched, data.outNB, data.dimNB)
}

fun applyRemoteIoProfileToUi(profile: IoProfileSnapshot?) {
    if (profile == null) return
    val ui = uiState.value
    uiState.value = ui.copy(devConfig = applyIoProfileToUi(ui.devConfig, profile))
    syncVanGoRuntimeFromDevConfig(uiState.value.devConfig)
    applyIoChannelLabelsFromDevice(profile)
}

/**
 * IO flash isim/ikon SoT — panel_seq daha yeniyse veya panel_changed ise tablete uygula.
 * @param outNB IO tip tamponu (ikon yedegi)
 * @param dimNB dimmer tip tamponu
 */
fun applyIoChannelLabelsFromDevice(
    profile: IoProfileSnapshot,
    outNB: ByteArray = byteArrayOf(),
    dimNB: ByteArray = byteArrayOf(),
) {
    val channels = profile.channels ?: return
    if (channels.relays.isEmpty() && channels.dimmers.isEmpty() &&
        channels.sensors.isEmpty() && channels.water.isEmpty()
    ) {
        return
    }
    /* Ayar ekranında kaydedilmemiş düzenlemeyi ezme */
    if (uiState.value.settingsPage == SettingsPage.Panel && panelSettingsSession.value.hasChanges) {
        return
    }
    val lastSeq = PanelSettingsPlatformSync.readCachedProfile()?.panelSeq ?: 0
    val seq = profile.panelSeq
    if (!profile.panelChanged && seq > 0 && seq <= lastSeq) {
        return
    }
    val profileKey = devicePanelProfile.value.profileKey
    val base = PanelSettingsPlatformSync.readCachedPanelConfig()
        ?: defaultPanelPlatformConfig(profileKey)
    var merged = mergeIoChannelLabels(base.channels, channels)
    merged = enrichIconsFromTypeBuffers(merged, outNB, dimNB)
    /* Sadece gercek maske ile zorla. mask=0 + channels[] varken applyRelayEnableMask
     * tum slotlari enabled=false yapip ana sayfada butonlari pasif birakiyordu. */
    if (profile.relayMask != 0) {
        merged = merged.copy(relays = applyRelayEnableMask(merged.relays, profile.relayMask, slotCount = 12))
    }
    if (profile.dimMask != 0) {
        merged = merged.copy(dimmers = applyBitEnableMask(merged.dimmers, profile.dimMask, slotCount = 8))
    }
    PanelSettingsPlatformSync.applyPlatformPanelConfig(
        base.copy(profile = profileKey, channels = merged),
        "io-sot-seq=$seq",
    )
    PanelSettingsPlatformSync.persistProfile(profile.copy(panelSeq = seq.coerceAtLeast(lastSeq)))
    /* Cihaz SoT ilerledi — bekleyen eski tablet push'unu iptal et */
    if (seq > lastSeq && LoginPreferences.isPanelConfigPendingBlePush()) {
        LoginPreferences.setPanelConfigPendingBlePush(false)
    }
    val iconHits = merged.relays.count { it.icon.isNotBlank() && it.icon != "circle" }
    println("Panel labels applied from IO SoT seq=$seq relays=${merged.relays.size} icons=$iconHits")
}

/**
 * Platform layout korunurken bile Display/IO aktif-pasif maskesini uygula.
 */
fun applyIoEnableMasksFromDevice(profile: IoProfileSnapshot) {
    if (uiState.value.settingsPage == SettingsPage.Panel && panelSettingsSession.value.hasChanges) {
        return
    }
    if (profile.relayMask == 0 && profile.dimMask == 0 && profile.sensorMask == 0 && profile.tankMask == 0) {
        return
    }
    val profileKey = devicePanelProfile.value.profileKey
    val base = PanelSettingsPlatformSync.readCachedPanelConfig()
        ?: defaultPanelPlatformConfig(profileKey)
    val relays = applyRelayEnableMask(base.channels.relays, profile.relayMask, slotCount = 12)
    val dimmers = applyBitEnableMask(base.channels.dimmers, profile.dimMask, slotCount = 8)
    val sensors = applyBitEnableMask(base.channels.sensors, profile.sensorMask, slotCount = 4)
    val water = applyBitEnableMask(base.channels.water, profile.tankMask, slotCount = 3)
    val next = base.copy(
        profile = profileKey,
        channels = base.channels.copy(
            relays = relays,
            dimmers = dimmers,
            sensors = sensors,
            water = water,
        ),
    )
    PanelSettingsPlatformSync.applyPlatformPanelConfig(next, "io-mask")
    /* DevConfig buffer da maskeye uysun (eski 16 slot aktif kalmasin) */
    val ui = uiState.value
    uiState.value = ui.copy(
        devConfig = ui.devConfig.copy(
            OutputsNameBuffer = mergeTypesWithEnableMask(ui.devConfig.OutputsNameBuffer, profile.relayMask, 16),
            DimsNameBuffer = mergeTypesWithEnableMask(ui.devConfig.DimsNameBuffer, profile.dimMask, 8),
            SensorsNameBuffer = mergeTypesWithEnableMask(ui.devConfig.SensorsNameBuffer, profile.sensorMask, 5),
            relayEnableMask = profile.relayMask,
            dimEnableMask = profile.dimMask,
            sensorEnableMask = profile.sensorMask,
            tankEnableMask = profile.tankMask,
            numOfOutputs = countBits16(profile.relayMask),
            numOfDims = countBits8(profile.dimMask),
            numOfSensors = countBits8(profile.sensorMask),
            numOfTanks = profile.tanks.takeIf { it > 0 } ?: countBits8(profile.tankMask),
        ),
    )
    println("Panel enable masks from IO SoT rMask=${profile.relayMask}")
}

private fun applyRelayEnableMask(
    current: List<PanelPlatformChannel>,
    mask: Int,
    slotCount: Int,
): List<PanelPlatformChannel> = applyBitEnableMask(current, mask, slotCount)

private fun applyBitEnableMask(
    current: List<PanelPlatformChannel>,
    mask: Int,
    slotCount: Int,
): List<PanelPlatformChannel> {
    val byId = current.associateBy { it.id }.toMutableMap()
    for (id in 0 until slotCount) {
        val enabled = (mask and (1 shl id)) != 0
        val prev = byId[id]
        byId[id] = (prev ?: PanelPlatformChannel(id = id, enabled = enabled, sortOrder = id)).copy(
            enabled = enabled,
        )
    }
    return byId.values.sortedBy { it.sortOrder.takeIf { o -> o != 0 } ?: it.id }
}

private fun mergeTypesWithEnableMask(buf: ByteArray, mask: Int, size: Int): ByteArray {
    val out = ByteArray(size) { 0 }
    val n = minOf(buf.size, size)
    for (i in 0 until n) {
        out[i] = if ((mask and (1 shl i)) != 0) {
            val v = buf[i].toInt() and 0xFF
            if (v == 0) 1 else v.toByte()
        } else {
            0
        }
    }
    for (i in n until size) {
        out[i] = if ((mask and (1 shl i)) != 0) 1 else 0
    }
    return out
}

private fun countBits16(v: Int): Int {
    var n = 0
    var x = v and 0xFFFF
    while (x != 0) {
        n += x and 1
        x = x shr 1
    }
    return n
}

private fun countBits8(v: Int): Int = countBits16(v and 0xFF)

private fun mergeIoChannelLabels(
    current: PanelPlatformChannels,
    fromIo: PanelPlatformChannels,
): PanelPlatformChannels {
    fun mergeList(
        cur: List<PanelPlatformChannel>,
        io: List<PanelPlatformChannel>,
    ): List<PanelPlatformChannel> {
        if (io.isEmpty()) return cur
        val byId = cur.associateBy { it.id }.toMutableMap()
        io.forEach { row ->
            val prev = byId[row.id]
            val ioIcon = row.icon.trim()
            byId[row.id] = (prev ?: row).copy(
                name = row.name.ifBlank { prev?.name.orEmpty() },
                /* IO ikon geldiyse tableti guncelle; bossa onceki kalsin */
                icon = when {
                    ioIcon.isNotEmpty() && ioIcon != "circle" -> ioIcon
                    else -> prev?.icon?.takeIf { it.isNotBlank() } ?: ioIcon
                },
                enabled = row.enabled,
                sensorType = row.sensorType.ifBlank { prev?.sensorType.orEmpty() },
            )
        }
        return byId.values.sortedBy { it.sortOrder.takeIf { o -> o != 0 } ?: it.id }
    }
    return current.copy(
        relays = mergeList(current.relays, fromIo.relays),
        dimmers = mergeList(current.dimmers, fromIo.dimmers),
        sensors = mergeList(current.sensors, fromIo.sensors),
        water = mergeList(current.water, fromIo.water),
        rgb = mergeList(current.rgb, fromIo.rgb),
        motors = mergeList(current.motors, fromIo.motors),
    )
}

/** Label ikonu yoksa outNB/dimNB tipinden katalog ikonu uret. */
private fun enrichIconsFromTypeBuffers(
    channels: PanelPlatformChannels,
    outNB: ByteArray,
    dimNB: ByteArray,
): PanelPlatformChannels {
    fun needsIcon(icon: String): Boolean {
        val k = icon.trim()
        return k.isEmpty() || k == "circle"
    }
    val relays = channels.relays.map { row ->
        if (!needsIcon(row.icon)) return@map row
        val t = outNB.getOrElse(row.id) { 0 }.toInt() and 0xFF
        if (t > 0) row.copy(icon = PanelConfigMapper.presetToIconKey(t)) else row
    }
    val dimmers = channels.dimmers.map { row ->
        if (!needsIcon(row.icon)) return@map row
        val t = dimNB.getOrElse(row.id) { 0 }.toInt() and 0xFF
        if (t > 0) row.copy(icon = PanelConfigMapper.presetToIconKey(t)) else row
    }
    return channels.copy(relays = relays, dimmers = dimmers)
}

fun syncVanGoRuntimeFromDevConfig(devConfig: DevConfig) {
    val current = vanGoData.value
    val profileCaps = devicePanelProfile.value.caps
    val outSize = profileCaps.relays.coerceIn(1, FIRMWARE_RELAY_BUFFER_MAX)
    val dimSize = profileCaps.dimmers.coerceIn(1, FIRMWARE_DIMMER_MAX)
    val outNB = padBuffer(devConfig.OutputsNameBuffer.copyOf(), outSize)
    val dimNB = padBuffer(devConfig.DimsNameBuffer.copyOf(), dimSize)
    val senNB = padBuffer(devConfig.SensorsNameBuffer.copyOf(), FIRMWARE_SENSOR_BUFFER_MAX)
    vanGoData.value = VanGoData(
        slvConn = current.slvConn,
        wifiMac = current.wifiMac,
        numOfO = profileCaps.relays.coerceAtLeast(countEnabledSlots(outNB)),
        numOfD = profileCaps.dimmers.coerceAtLeast(countEnabledSlots(dimNB)),
        numOfS = devConfig.numOfSensors.coerceAtLeast(countEnabledSlots(senNB)),
        RGBE = if (devConfig.RGBEnables.equals("yes", ignoreCase = true)) "yes" else "no",
        Th = current.Th,
        volt = current.volt,
        outNB = outNB,
        dimNB = dimNB,
        senNB = senNB,
        outDB = current.outDB,
        dDB = current.dDB,
        sDB = current.sDB,
        RGBDB = current.RGBDB,
        mac = current.mac,
        motorEn = current.motorEn,
        rgbEn = current.rgbEn,
        tankN = current.tankN,
        relayMask = current.relayMask,
        dimMask = current.dimMask,
        sensorMask = current.sensorMask,
        tankMask = current.tankMask,
        ioLink = current.ioLink,
        io = current.io,
        sensorTemp = current.sensorTemp,
        sensorHumidity = current.sensorHumidity,
        panelSeq = current.panelSeq,
        panelChanged = current.panelChanged,
    )
}

private fun padBuffer(source: ByteArray, size: Int): ByteArray {
    if (source.size >= size) return source.copyOf(size)
    return source + ByteArray(size - source.size)
}
