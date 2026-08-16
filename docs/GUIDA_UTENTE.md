# Guida utente TOC SAR

App per operatori SAR / unità cinofile. Versione corrente: **1.0.42**.

Login = tracking verso il TOC (posizione, allarmi, push). Il GPS continua con schermo spento e telefono in tasca (notifica «Tracking operatore»).

---

## Avvio e login

1. Apri **TOC SAR**.
2. **Log-in** con codice operatore e password (es. `LUPO` / password assegnata).
3. Concedi **posizione** e **notifiche**.
4. In home compare il codice, il nome e l’orario di login. Resta visibile la notifica di tracking.

**Log-out** ferma il tracking. Non avviene da solo: solo tasto Log-out o force log-out da admin.

Su alcuni telefoni: Impostazioni → TOC SAR → **non rimuovere autorizzazioni se l’app non viene usata**, e batteria **senza restrizioni**.

---

## Home

| Tasto | Cosa fa |
| --- | --- |
| Reset notifica | Chiude la notifica **solo sul telefono**. La chiusura evento è solo dal TOC. |
| Impostazioni TOC SAR | Apre le impostazioni Android dell’app. |
| INVIA NOTIFICA A TOC | Allarme verso il TOC. |
| INVIA FOTO A TOC | Foto dal campo. |
| GPS | Modulo mappa, waypoint, tracce, misura. |
| Operatori on line | Solo **LUPO** (admin): elenco sessioni, visibilità pin, force log-out. |

---

## Visibilità in mappa (admin LUPO)

LUPO **vede tutti** gli operatori online con fix GPS.

Gli altri operatori vedono **solo** chi ha il flag «visibile in mappa». Vale anche per LUPO: se LUPO è nascosto, gli altri **non** lo vedono.

In **Operatori on line**:

- spunta **Visibile in mappa** → gli altri lo vedono
- togli la spunta → **nascosto: solo LUPO lo vede**
- **Forza log-out** → l’operatore sparisce dalla mappa finché non fa di nuovo Log-in

Al login il flag parte **spento** (nascosto). LUPO decide chi mostrare.

---

## GPS

Dalla home, **GPS**.

### BASE / PATTUGLIA / rotta

- **Imposta BASE da GPS** / **PATTUGLIA da GPS**: riempie le coordinate dal fix attuale.
- **Calcola rotta**: distanza e direzione tra PATTUGLIA e BASE.
- **Vai a BASE**: navigazione live verso le coordinate BASE (freccia e distanza). Non è la misura in mappa (vedi sotto).

### Waypoint

- **INS WP**: inserisci nome (prefisso `CODICE_WP_`) e coordinate (a mano o **Da GPS**).
- Dopo **Salva** il waypoint compare **in mappa**. Non parte nessun calcolo da te verso quel punto.
- Per misura o confronto: apri la mappa e **tocca** il pin del WP e un altro punto (operatore o altro WP).

**WP & TRK**: elenco waypoint di missione (dal TOC/DB) e locali. Il flag = visibili in MAPPA. Poi **MAPPA**.

I flag restano finché non fai **Clear data**.

### Traccia (TRK)

- **START TRK**: registra il percorso anche in tasca / schermo spento (stesso servizio di tracking).
- **STOP TRK**: se ci sono almeno 2 punti, puoi salvare la traccia locale (`CODICE_TRK_…`).
- In mappa la scia live è rossa; le tracce salvate si caricano da WP & TRK.

### MAPPA

- **⧉** strati: stradale, topografica, satellite.
- Icona sentiero: overlay waymarked trails on/off.
- **⊙ / ⊕**: centra sulla tua posizione (follow).
- **X** verde/rosso: nord dinamico (mappa ruota con la prua) oppure nord in alto.

Pin:

- **freccia rossa** = tu
- **pallino colorato + codice** = altro operatore online visibile
- **casa verde** = waypoint in overlay

---

## Misura in mappa

Non parte da sola dalla tua posizione. Scegli **due punti** toccandoli:

- operatore → operatore
- operatore → waypoint (o viceversa)
- waypoint → waypoint
- la tua freccia GPS → operatore o waypoint (se vuoi misurare **da te**)

Compare il riquadro **MISURA**: distanza, direzione (gradi + rumba) e linea gialla.

- **Salva WP** solo per le **posizioni degli operatori** (snapshot locale, es. `LUPO_WP_LOST`).
- Se il punto è già un waypoint (locale o missione) **non** c’è salvataggio: è già salvato.
- Ri-tap sullo stesso pin: lo togli dalla misura. **Annulla** azzera tutto.

---

## Condivisione / import file

Da **WP & TRK** puoi inviare un waypoint o una traccia.  
Puoi **importare** un file `.wpt` / `.trk` (anche da «Apri con» / Condividi verso TOC SAR). I nomi file originali restano.

---

## Se qualcosa non si vede in mappa

1. L’altro operatore è **online** e ha un fix GPS?
2. Il flag **visibile** è acceso? (solo LUPO vede i nascosti)
3. Stai guardando l’evento attivo giusto sul TOC?
4. Per i WP: sono flaggati in WP & TRK e poi aperti con **MAPPA**?
