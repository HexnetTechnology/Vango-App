# VanGO Tablet / Telefon sürüm yönetimi (makro / mikro)

Tek KMP kaynak (`VanGO-v0.91-001`); Android tablet, telefon ve iOS aynı makro/mikro’yu paylaşır.

| Kavram | Örnek | Anlam |
|--------|--------|--------|
| **Makro** | `v0.91` | Ürün çizgisi |
| **Mikro** | `v0.91-0001` | Anlamlı değişiklik takip |
| **versionName** | `0.91-001` | Android / iOS görünen sürüm |
| **versionCode** | `91001` | Store / yükleme kodu |

## Dosyalar

- `VERSION.json`, `changelog.json`
- `composeApp/.../HexnetAppVersion.kt` (Sync üretir)
- `scripts/Sync-VersionFiles.ps1`, `scripts/Bump-Version.ps1`
- `build-debug-apk.ps1` Sync’i otomatik çağırır

```powershell
.\scripts\Bump-Version.ps1 -Bump micro -Notes "…"
.\build-debug-apk.ps1
```
