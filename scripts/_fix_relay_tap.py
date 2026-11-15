from pathlib import Path

p = Path(
    r"c:\Users\Smartup\Desktop\ARGE\Hexnet\HexnetWorkspace\VanGO\VanGO-v0.90-001"
    r"\composeApp\src\commonMain\kotlin\com\hextech\vango\DashboardHomeScreen.kt"
)
text = p.read_text(encoding="utf-8")

old_ordered = """    val orderedIds = remember(channels, data.outNB.size, data.numOfO) {
        val fromPlatform = channels?.relays
            ?.filter { it.enabled }
            ?.sortedBy { it.id }
            ?.map { it.id }
            .orEmpty()
"""
new_ordered = """    val orderedIds = remember(channels, data.outNB.size, data.numOfO) {
        val fromPlatform = channels?.relays
            ?.filter { it.enabled }
            ?.sortedWith(compareBy<PanelPlatformChannel> { it.sortOrder }.thenBy { it.id })
            ?.map { it.id }
            .orEmpty()
"""
if old_ordered not in text:
    raise SystemExit("orderedIds block not found")
text = text.replace(old_ordered, new_ordered, 1)

old_tile = """private fun DashboardRelayTile(index: Int, data: VanGoData) {
    val scope = rememberCoroutineScope()
    val channels by panelPlatformChannels.collectAsState()
    val channel = channels?.relays?.firstOrNull { it.id == index }
    val typeId = when {
        channel != null -> PanelConfigMapper.iconToPreset(channel.icon)
        else -> data.outNB.getOrElse(index) { 0 }.toInt() and 0xFF
    }
    val on = data.outDB.getOrElse(index) { 0 }.toInt() != 0
    val visible = channel?.enabled ?: (typeId != 0 || index < data.numOfO)
"""

new_tile = """private fun isRgbRelayIcon(icon: String?): Boolean {
    val key = icon?.trim()?.lowercase().orEmpty()
    return key == "rgb_aydinlatma" || key == "palette" || key.startsWith("rgb")
}

private fun DashboardRelayTile(index: Int, data: VanGoData) {
    val scope = rememberCoroutineScope()
    val channels by panelPlatformChannels.collectAsState()
    val channel = channels?.relays?.firstOrNull { it.id == index }
    val rgbTile = isRgbRelayIcon(channel?.icon)
    val typeId = when {
        channel != null -> PanelConfigMapper.iconToPreset(channel.icon)
        else -> data.outNB.getOrElse(index) { 0 }.toInt() and 0xFF
    }
    val on = if (rgbTile) {
        data.getSafeRGBData(3).toInt() != 0
    } else {
        data.outDB.getOrElse(index) { 0 }.toInt() != 0
    }
    val visible = channel?.enabled ?: (typeId != 0 || index < data.numOfO)
"""

if old_tile not in text:
    raise SystemExit("tile header not found")
text = text.replace(old_tile, new_tile, 1)

old_click = """            .clickable(enabled = visible) {
                if (vanGoGlobal.value.lockOperation(Operations.WriteDataDevice)) {
                    scope.launch {
                        try {
                            getAppContext().vanGO.writeDigitalPort(index, !on)
                        } finally {
                            vanGoGlobal.value.unlockOperation()
                        }
                    }
                }
            }
"""

new_click = """            .clickable(enabled = visible) {
                fun runToggle() {
                    scope.launch {
                        try {
                            if (rgbTile) {
                                val (r, g, b) = data.rgb888ForUi()
                                println("Dashboard relay tap RGB idx=$index -> ${if (!on) "ON" else "OFF"}")
                                getAppContext().vanGO.writeRGBPort(r, g, b, if (on) 0 else 1)
                            } else {
                                println("Dashboard relay tap DIGITAL idx=$index -> ${if (!on) "ON" else "OFF"}")
                                getAppContext().vanGO.writeDigitalPort(index, !on)
                            }
                        } finally {
                            vanGoGlobal.value.unlockOperation()
                        }
                    }
                }
                if (vanGoGlobal.value.lockOperation(Operations.WriteDataDevice)) {
                    runToggle()
                } else {
                    // Stale lock (weather/config) — force unlock once and retry
                    println("Dashboard relay lock busy idx=$index — forcing unlock")
                    vanGoGlobal.value.unlockOperation()
                    if (vanGoGlobal.value.lockOperation(Operations.WriteDataDevice)) {
                        runToggle()
                    } else {
                        println("Dashboard relay lock still busy idx=$index")
                    }
                }
            }
"""

if old_click not in text:
    raise SystemExit("click block not found")
text = text.replace(old_click, new_click, 1)

p.write_text(text, encoding="utf-8")
print("Dashboard OK")
