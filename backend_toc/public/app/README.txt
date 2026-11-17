Aggiornamento obbligatorio Android (TOC SAR)
============================================

1. Carica l'APK in un URL pubblico stabile (Drive "chiunque con il link",
   Storage, o copia in questa cartella /app/ e rideploya Vercel).

2. Modifica android-latest.json:
   - versionName  es. "1.0.76"
   - versionCode  es. 10076  (deve essere > della build installata)
   - apkUrl       URL diretto al .apk
   - message      testo del dialog (opzionale)

3. Deploy TOC (vercel --prod) se il JSON è su toc-sar.vercel.app.

L'app 1.0.75+ a ogni avvio legge:
  https://toc-sar.vercel.app/app/android-latest.json
Se versionCode remoto > versionCode installato, blocca finché non si apre apkUrl.

Chi ha ancora ≤1.0.73 deve installare UNA VOLTA a mano un'APK ≥1.0.75
(che contiene il controllo); da allora gli aggiornamenti sono obbligatori via dialog.

Release corrente di esempio: toc_sar_KMP_1.0.76.apk in questa cartella
(apkUrl = https://toc-sar.vercel.app/app/toc_sar_KMP_1.0.76.apk).
