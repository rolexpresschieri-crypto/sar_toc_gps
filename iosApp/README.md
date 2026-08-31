# iOS app shell

UI Compose condivisa (`MainViewController` in `composeApp`).

## Prima build

1. Copia `supabase-config.example.json` → `supabase-config.local.json` (URL + key del progetto TOC SAR).
2. Copia `iosApp/Configuration/Signing.xcconfig.example` → `Signing.xcconfig` e metti il Team ID Apple.
3. Apri `iosApp.xcodeproj` oppure:

```bash
./build-ios-ipa.sh ad-hoc
```

## Note

- GPS CoreLocation (when-in-use / always) e tracking TOC in background sono collegati.
- Mappa MapKit + OpenTopo / sentieri, tap pin per MISURA, VAI verso WP.
- Non committare `Signing.xcconfig`, `Config.xcconfig` né `supabase-config.json` (sono in `.gitignore`).
