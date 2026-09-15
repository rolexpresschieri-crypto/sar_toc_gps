"""Genera docs/TOC_SAR_Guida_utente.pdf — A4, loghi ANSMI + UCRS.

Una sola guida Android + iPhone: il testo comune vale per entrambi;
le eccezioni iPhone sono nei punti «iPhone —» e nei riquadri."""

from __future__ import annotations

from pathlib import Path

from fpdf import FPDF

ROOT = Path(__file__).resolve().parents[1]
DOCS = Path(__file__).resolve().parent
DRAWABLE = ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "drawable"
LOGO_ANSMI = DRAWABLE / "logo_ansmi.png"
LOGO_UCRS = DRAWABLE / "logo_ucrs.png"
OUT = DOCS / "TOC_SAR_Guida_utente.pdf"

NAVY = (11, 53, 107)
GREEN = (7, 155, 66)
DARK = (25, 25, 25)
MUTED = (70, 70, 70)
LINE = (180, 180, 180)
VERSION = "1.0.68"


def _first_existing(*paths: Path) -> Path:
    for p in paths:
        if p.is_file():
            return p
    raise FileNotFoundError("Font Arial non trovato (Windows Fonts o macOS Supplemental).")


FONT_REG = _first_existing(
    Path(r"C:\Windows\Fonts\arial.ttf"),
    Path("/System/Library/Fonts/Supplemental/Arial.ttf"),
)
FONT_BOLD = _first_existing(
    Path(r"C:\Windows\Fonts\arialbd.ttf"),
    Path("/System/Library/Fonts/Supplemental/Arial Bold.ttf"),
)
FONT_ITA = _first_existing(
    Path(r"C:\Windows\Fonts\ariali.ttf"),
    Path("/System/Library/Fonts/Supplemental/Arial Italic.ttf"),
    FONT_REG,
)


class GuidaPdf(FPDF):
    def header(self) -> None:
        if self.page_no() == 1:
            return
        self.set_fill_color(*NAVY)
        self.rect(0, 0, 210, 22, "F")
        if LOGO_ANSMI.exists():
            self.image(str(LOGO_ANSMI), x=8, y=3, h=16)
        if LOGO_UCRS.exists():
            self.image(str(LOGO_UCRS), x=186, y=3, h=16)
        self.set_text_color(255, 255, 255)
        self.set_font("ArialIt", "B", 11)
        self.set_xy(30, 7)
        self.cell(150, 8, "TOC SAR  —  Guida utente operatore", align="C")
        self.set_xy(self.l_margin, 26)

    def footer(self) -> None:
        self.set_y(-14)
        self.set_draw_color(*LINE)
        self.line(12, self.get_y(), 198, self.get_y())
        self.set_y(-12)
        self.set_font("ArialIt", "", 8)
        self.set_text_color(*MUTED)
        self.cell(90, 8, "ANSMI  ·  Nucleo Volontari  ·  UCRS", align="L")
        self.cell(96, 8, f"Android + iPhone  ·  v. {VERSION}   pag. {self.page_no()}", align="R")

    def h1(self, text: str) -> None:
        self.ln(4)
        self.set_font("ArialIt", "B", 14)
        self.set_text_color(*NAVY)
        self.cell(0, 8, text, new_x="LMARGIN", new_y="NEXT")
        self.set_draw_color(*GREEN)
        self.set_line_width(0.6)
        y = self.get_y()
        self.line(12, y, 80, y)
        self.ln(3)
        self.set_text_color(*DARK)

    def h2(self, text: str) -> None:
        self.ln(2)
        self.set_font("ArialIt", "B", 12)
        self.set_text_color(*GREEN)
        self.cell(0, 7, text, new_x="LMARGIN", new_y="NEXT")
        self.set_text_color(*DARK)
        self.ln(1)

    def p(self, text: str) -> None:
        self.set_font("ArialIt", "", 10.5)
        self.set_text_color(*DARK)
        self.multi_cell(0, 5.4, text)
        self.ln(1.5)

    def bullet(self, title: str, body: str) -> None:
        left = self.l_margin
        width = self.epw
        self.set_x(left)
        self.set_font("ArialIt", "B", 10.5)
        self.set_text_color(*NAVY)
        self.multi_cell(width, 5.4, f"•  {title}")
        self.set_x(left + 7)
        self.set_font("ArialIt", "", 10.5)
        self.set_text_color(*DARK)
        self.multi_cell(width - 7, 5.4, body)
        self.set_x(left)
        self.ln(1.2)

    def ios(self, title: str, body: str) -> None:
        self.bullet(f"iPhone — {title}", body)

    def note(self, text: str) -> None:
        self.set_fill_color(245, 248, 252)
        self.set_draw_color(*NAVY)
        self.set_font("ArialIt", "I", 10)
        self.set_text_color(*NAVY)
        self.multi_cell(0, 5.4, text, border=1, fill=True)
        self.ln(2.5)
        self.set_text_color(*DARK)


def build() -> Path:
    pdf = GuidaPdf(format="A4", unit="mm")
    pdf.set_auto_page_break(auto=True, margin=18)
    pdf.set_margins(12, 16, 12)
    pdf.add_font("ArialIt", "", str(FONT_REG))
    pdf.add_font("ArialIt", "B", str(FONT_BOLD))
    pdf.add_font("ArialIt", "I", str(FONT_ITA))

    pdf.add_page()
    pdf.set_fill_color(*NAVY)
    pdf.rect(0, 0, 210, 48, "F")
    if LOGO_ANSMI.exists():
        pdf.image(str(LOGO_ANSMI), x=22, y=8, h=32)
    if LOGO_UCRS.exists():
        pdf.image(str(LOGO_UCRS), x=148, y=8, h=32)
    pdf.set_y(58)
    pdf.set_font("ArialIt", "B", 11)
    pdf.set_text_color(*GREEN)
    pdf.cell(0, 7, "NUCLEO VOLONTARI  ·  ASSOCIAZIONE NAZIONALE SANITÀ MILITARE ITALIANA", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("ArialIt", "B", 11)
    pdf.cell(0, 7, "REPARTO CINOFILI DA RICERCA E SOCCORSO  (UCRS)", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.ln(10)
    pdf.set_font("ArialIt", "B", 26)
    pdf.set_text_color(*NAVY)
    pdf.cell(0, 12, "TOC SAR", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("ArialIt", "B", 16)
    pdf.cell(0, 9, "Guida utente operatore", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.ln(4)
    pdf.set_font("ArialIt", "", 12)
    pdf.set_text_color(*MUTED)
    pdf.cell(0, 7, f"Android e iPhone  ·  versione {VERSION}", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.cell(0, 7, "Dal log-in al modulo GPS (mappa, waypoint, tracce, misura, confini)", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.ln(14)
    pdf.set_draw_color(*GREEN)
    pdf.set_line_width(1.2)
    pdf.line(50, pdf.get_y(), 160, pdf.get_y())
    pdf.ln(10)
    pdf.set_font("ArialIt", "", 11)
    pdf.set_text_color(*DARK)
    pdf.multi_cell(
        0,
        6,
        "Questa guida vale per Android e iPhone: tasti e funzioni sono gli stessi. "
        "Le eccezioni iPhone (installazione, permessi, qualche dettaglio in mappa) "
        "sono nei punti «iPhone —» e nei riquadri. Il Mac serve solo a compilare l’IPA, non in campo.",
        align="C",
    )
    pdf.ln(16)
    pdf.set_font("ArialIt", "I", 10)
    pdf.set_text_color(*MUTED)
    pdf.multi_cell(
        0,
        5.5,
        "Login = tracking verso il TOC. Il log-out è solo esplicito: tasto Log-out o force log-out da admin (LUPO).",
        align="C",
    )

    pdf.add_page()

    pdf.h1("1.  A cosa serve TOC SAR")
    pdf.p(
        "TOC SAR è l’app dell’operatore collegata al Tactical Operations Center. "
        "Con il log-in l’operatore risulta online sull’evento attivo: il TOC vede la sessione, "
        "riceve la posizione GPS, le notifiche di allarme e le foto dal campo."
    )
    pdf.p(
        "Il modulo GPS serve al lavoro sul terreno: rotta verso un punto BASE, waypoint, "
        "registrazione traccia, mappa, misura e confini comunali. "
        "Non sostituisce il TOC: è lo strumento in mano all’operatore."
    )

    pdf.h1("2.  Prima di usare l’app")
    pdf.bullet(
        "Installazione Android",
        "Copia sul telefono il file APK (es. toc_sar_KMP_1.0.68.apk) e installa. "
        "Se Android lo chiede, consenti «origini sconosciute» solo per TOC SAR.",
    )
    pdf.ios(
        "Installazione (IPA / Diawi)",
        "Apri il link Diawi, tocca Installa. Se TOC SAR è già installata, DISINSTALLALA prima "
        "(tieni premuta l’icona → Rimuovi app), altrimenti resta la versione vecchia. "
        "Non usare l’APK Android sull’iPhone: serve toc_sar_iOS_1.0.68.ipa (o il link Diawi di quella versione).",
    )
    pdf.ios(
        "Sviluppatore non verificato",
        "Al primo avvio iOS può bloccare l’app. Impostazioni → Generali → VPN e gestione dispositivo "
        "(o Profilo e gestione dispositivi) → sviluppatore ANSMI / team TOC SAR → Autorizza. Poi riapri TOC SAR.",
    )
    pdf.bullet(
        "Permesso Posizione (Android)",
        "«Consenti solo mentre l’app è in uso» è corretto e sufficiente. "
        "Non serve «Consenti sempre». In tasca / schermo spento il GPS resta attivo perché dopo il log-in "
        "c’è la notifica fissa «Tracking operatore»: per Android l’app è ancora in uso. "
        "Se togli la notifica o fai «Forza chiusura», il GPS si ferma.",
    )
    pdf.ios(
        "Permesso Posizione",
        "Alla prima richiesta scegli «Consenti durante l’uso». Poi «Passa a Sempre» "
        "(o Impostazioni → Privacy e sicurezza → Localizzazione → TOC SAR → Sempre). "
        "«Durante l’uso» basta per la mappa a schermo acceso; «Sempre» serve per il tracking in tasca.",
    )
    pdf.ios(
        "Posizione precisa",
        "Impostazioni → Privacy e sicurezza → Localizzazione → TOC SAR → Posizione precisa: ON. "
        "Se è spenta iOS dà una posizione a centinaia di metri: mappa e TOC non sono utilizzabili.",
    )
    pdf.bullet(
        "Notifiche",
        "Concedile: servono per i messaggi dal TOC e, su Android, per il tracking in tasca.",
    )
    pdf.bullet(
        "Batteria e «app inutilizzata» (Android)",
        "Impostazioni → TOC SAR → disattiva «Rimuovi le autorizzazioni se l’app non viene usata». "
        "Batteria: nessuna ottimizzazione / nessuna restrizione. Altrimenti il telefono taglia GPS e notifiche da solo.",
    )
    pdf.note(
        "Il tracking non richiede di tenere l’app aperta in mano. "
        "Android: dopo il log-in resta la notifica fissa «Tracking operatore». "
        "iPhone: Localizzazione = Sempre; non fare «Chiudi app» dallo switcher se ti serve il GPS in tasca."
    )

    pdf.h1("3.  Log-in")
    pdf.p("Dalla home, tasto verde Log-in. Schermata «Log-in operatore».")
    pdf.bullet(
        "Codice operatore",
        "Codice assegnato dal TOC (anagrafica). Si scrive in maiuscolo (es. LUPO, RAGGHY, LOST).",
    )
    pdf.bullet(
        "Password app",
        "Password dell’operatore, non quella del database. È quella comunicata in briefing / anagrafica TOC.",
    )
    pdf.bullet("Entra", "Avvia la sessione sull’evento attivo. Parte il tracking GPS verso il TOC.")
    pdf.bullet("Indietro", "Torna alla home senza entrare.")
    pdf.p(
        "Se il codice non esiste, la password è sbagliata, l’operatore è disabilitato, "
        "non c’è evento attivo, oppure lo stesso codice è già online su un altro telefono, "
        "compare un messaggio di errore: non si entra."
    )
    pdf.note(
        "Un operatore = un telefono. Se risulta già online, prima va fatto log-out (o force log-out da LUPO), "
        "poi si può rientrare."
    )

    pdf.h1("4.  Home (dopo il log-in)")
    pdf.p(
        "In alto restano i loghi e il titolo Tracking / Operatori SAR. "
        "Il riquadro verde mostra: CODICE · nome + orario di login. "
        "Sotto può comparire lo stato GPS inviato al TOC (accuratezza dell’ultimo fix)."
    )
    pdf.bullet(
        "Reset notifica",
        "Chiude la notifica push solo su QUESTO telefono e registra «notifica chiusa». "
        "Non chiude l’evento: la chiusura evento è solo dal TOC.",
    )
    pdf.bullet(
        "Impostazioni TOC SAR",
        "Apre le impostazioni di sistema dell’app (permessi, batteria/localizzazione, notifiche).",
    )
    pdf.bullet(
        "Log-in",
        "Disattivo se sei già dentro (tasto grigio). Serve solo da scollegati.",
    )
    pdf.bullet(
        "Log-out",
        "Tasto arancione. Chiude la sessione, ferma il tracking e toglie il pin dalla mappa degli altri. "
        "Non è automatico: se chiudi l’app senza Log-out, per il TOC resti online.",
    )
    pdf.bullet(
        "INVIA NOTIFICA A TOC",
        "Allarme operatore → TOC (rosso). Usalo per richiedere attenzione / emergenza, non per chiacchiere.",
    )
    pdf.bullet(
        "INVIA FOTO A TOC",
        "Scatta o sceglie una foto e la manda al TOC come documentazione dal campo.",
    )
    pdf.bullet(
        "GPS",
        "Apre il modulo descritto dal capitolo 7 in poi: rotta, WP, TRK, mappa, misura.",
    )
    pdf.bullet(
        "Operatori on line",
        "Visibile solo a LUPO (admin). Elenco sessioni, flag visibilità in mappa, force log-out. Vedi capitolo 6.",
    )

    pdf.h1("5.  Log-out")
    pdf.p(
        "Dalla home, Log-out. Conferma se richiesta. Effetti: is_online = no, tracking fermo, "
        "sparisci dalla mappa degli altri operatori. Su Android sparisce anche la notifica tracking. "
        "Per tornare visibili: di nuovo Log-in."
    )
    pdf.p(
        "LUPO può forzare il log-out di un altro operatore da Operatori on line "
        "(es. telefono spento, app bloccata, cambio dispositivo)."
    )

    pdf.h1("6.  Operatori on line  (solo LUPO)")
    pdf.p(
        "Serve a LUPO per vedere chi è in sessione e decidere chi compare in mappa agli altri. "
        "Si aggiorna da solo ogni pochi secondi."
    )
    pdf.h2("6.1  Cosa vedi per ogni operatore")
    pdf.p("Codice, nome, se ha già un fix GPS, orario di login, e il flag visibilità.")
    pdf.h2("6.2  Flag «visibile in mappa»")
    pdf.p(
        "LUPO vede sempre tutti gli online con posizione. "
        "Gli altri operatori vedono solo chi ha il flag acceso. "
        "Il flag vale anche per LUPO: se LUPO è nascosto, gli altri non vedono LUPO. "
        "LUPO deve vedere tutti; non è detto che tutti debbano vedere LUPO."
    )
    pdf.bullet("Visibile in mappa (spunta accesa)", "Gli altri online vedono il pin di quell’operatore.")
    pdf.bullet(
        "Nascosto",
        "Solo LUPO lo vede in mappa. Esempio: LOST nascosto → RAGGHY non lo vede; LUPO sì.",
    )
    pdf.p("Al log-in il flag parte spento (nascosto). LUPO lo accende quando serve.")
    pdf.h2("6.3  Forza log-out")
    pdf.p(
        "Disconnette quell’operatore. Sparisce dalla mappa finché non fa di nuovo Log-in sul telefono. "
        "Se LUPO forza il log-out di se stesso, torna alla home scollegato."
    )

    pdf.h1("7.  Modulo GPS")
    pdf.p(
        "Dalla home, tasto giallo GPS. In alto: freccia indietro (torna alla home; il TRK in corso NON si ferma) "
        "e Notte / Giorno (inverte i colori dello schermo GPS, utile di notte)."
    )

    pdf.h2("7.1  Calibrazione bussola")
    pdf.p("Barra gialla in cima: «Bussola imprecisa? Guida calibrazione».")
    pdf.bullet(
        "Movimento a 8",
        "Muovi il telefono in aria a forma di 8, lento e ampio, 10–15 secondi. "
        "La calibrazione del sensore la fa il telefono.",
    )
    pdf.bullet(
        "Correzione orientamento",
        "Se il Nord resta storto (rosa ruotata), usa ±15° o ±90°. «Azzera» toglie la correzione.",
    )
    pdf.p("Tieni il telefono in verticale come in navigazione. La rosa (goniometro) mostra il Nord magnetico.")

    pdf.h2("7.2  BASE e PATTUGLIA")
    pdf.p(
        "Due colonne di coordinate. BASE = punto verso cui vuoi andare (casa, TOC, punto di ritrovo). "
        "PATTUGLIA (PTG) = la tua posizione (o quella da cui calcoli la rotta)."
    )
    pdf.bullet(
        "Imposta BASE da GPS",
        "Riempie lat / lon / quota BASE con il fix attuale. Serve un segnale outdoor.",
    )
    pdf.bullet(
        "Imposta PTG da GPS",
        "Stesso per PATTUGLIA. In navigazione live queste coordinate si aggiornano da sole.",
    )
    pdf.p("Puoi anche scrivere o correggere a mano lat, lon e quota. Sotto il tasto GPS compare l’accuratezza (acc).")

    pdf.h2("7.3  Calcola rotta  e  Vai a BASE")
    pdf.bullet(
        "Calcola rotta",
        "Serve BASE e PATTUGLIA compilate. Calcola distanza (linea d’aria) e bearing (direzione in gradi) "
        "da PTG verso BASE. Non avvia la navigazione continua.",
    )
    pdf.bullet(
        "Vai a BASE",
        "Avvia la navigazione live verso BASE: aggiorna la tua posizione, distanza e freccia rispetto alla prua. "
        "La rosa viene sostituita dalla freccia di direzione. «Chiudi Vai a BASE» ferma solo la navigazione, non cancella le coordinate.",
    )
    pdf.p(
        "In basso: Distanza e Bearing. In modalità Vai a BASE la distanza è verso BASE "
        "(o verso il WP / operatore se hai avviato la navigazione dalla mappa, cap. 8)."
    )
    pdf.p(
        "Scorciatoia Android in mappa: tocca la tua freccia GPS e poi la destinazione (WP o operatore). "
        "Si riempie BASE e parte Vai a BASE, senza copiare le coordinate a mano."
    )
    pdf.ios(
        "Navigazione verso un WP",
        "Due tap in mappa fanno solo la MISURA. Per la bussola usa VAI in WP & TRK, "
        "oppure «Naviga verso» nella finestra MISURA se uno dei due pin è un WP.",
    )

    pdf.h2("7.4  START TRK  /  STOP TRK")
    pdf.p(
        "Registra il percorso dell’operatore (bonifica, ricerca, spostamento). "
        "Un punto circa ogni 3 metri, filtrato per non fare «stelle» da GPS rumoroso."
    )
    pdf.bullet(
        "START TRK",
        "Il tasto diventa rosso STOP TRK. Compare «REC · N punti». "
        "Su Android la registrazione continua in tasca (notifica tracking). "
        "Su iPhone serve Permesso Posizione «Sempre».",
    )
    pdf.bullet(
        "STOP TRK",
        "Se ci sono almeno 2 punti, chiede il nome della traccia (prefisso CODICE_TRK_). "
        "La salva in locale sul telefono. Poi la puoi vedere in mappa da WP & TRK.",
    )
    pdf.p("Con TRK attivo puoi uscire da GPS e tornare in home: la traccia continua. Si ferma con STOP TRK o Log-out.")

    pdf.h2("7.5  INS WP  (inserisci waypoint)")
    pdf.p(
        "Crea un waypoint locale. Nome: prefisso automatico CODICEOPERATORE_WP_ + il nome che scrivi "
        "(es. LUPO_WP_BIVIO). Lat e lon obbligatorie; quota facoltativa. Tasto «Da GPS» copia il fix attuale."
    )
    pdf.p(
        "Dopo Salva il waypoint viene messo in mappa. Non parte da solo il calcolo da te verso quel punto: "
        "lo vedi, poi in mappa scegli tu cosa misurare o navigare (cap. 8)."
    )
    pdf.note(
        "I waypoint di missione (dal TOC / elenco missione) e quelli locali stanno in WP & TRK. "
        "INS WP crea solo un WP locale su questo telefono."
    )

    pdf.h2("7.6  WP & TRK")
    pdf.p("Elenco di tutto ciò che puoi mettere in mappa o condividere.")
    pdf.bullet(
        "WP MISSIONE",
        "Waypoint arrivati dalla missione (non si cancellano da qui). Flag = visibile in MAPPA.",
    )
    pdf.ios(
        "VAI",
        "Su iPhone, accanto al WP: avvia la navigazione live verso quel punto (come Vai a BASE) e chiude l’elenco.",
    )
    pdf.bullet(
        "WP LOCALI",
        "Quelli creati con INS WP o salvati da un operatore in mappa. Flag, Invia (condividi file), Elimina. "
        "Su iPhone c’è anche VAI.",
    )
    pdf.bullet(
        "TRACCE LOCALI",
        "Tracce salvate dopo STOP TRK. Flag, Invia, MAPPA (solo quella traccia), Elimina.",
    )
    pdf.bullet(
        "Importa file…",
        "Importa un .wpt / .trk (anche da «Apri con» / Condividi verso TOC SAR). Resta il nome file originale.",
    )
    pdf.bullet(
        "MAPPA (n. selezionati)",
        "Apre la mappa con i WP e le TRK che hai flaggato. I flag restano finché non fai Clear data.",
    )
    pdf.p("Chiudi torna al modulo GPS senza togliere i flag.")

    pdf.h2("7.7  MAPPA")
    pdf.p(
        "Schermo a tutto campo. La freccia indietro chiude solo la mappa e torna al GPS "
        "(non alla home). Cosa vedi:"
    )
    pdf.bullet("Freccia rossa + il tuo codice", "Sei tu. In follow è al centro; se fai pan resta sul punto geografico.")
    pdf.bullet("Pallino colorato + codice", "Altro operatore online visibile (vedi cap. 6). Su iPhone il codice può essere abbreviato alle prime 3 lettere.")
    pdf.bullet("Casa verde + nome", "Waypoint in overlay (missione o locale flaggato, o appena inserito).")
    pdf.bullet("Linea rossa", "Scia TRK in registrazione.")
    pdf.bullet("Linee colorate", "Tracce salvate caricate da WP & TRK.")
    pdf.bullet(
        "Contorno rosso vivo",
        "Confini comunali accesi (uno o più insieme). Li carica il TOC da Anagrafica; in mappa li accendi tu.",
    )
    pdf.bullet("Linea gialla", "Misura tra i due pin scelti (riquadro MISURA).")
    pdf.ios(
        "Linea / banner verso un WP",
        "Se hai avviato VAI / Naviga verso, è la rotta dalla tua posizione al WP (banner in alto: distanza). "
        "Non confonderla con la misura gialla.",
    )
    pdf.bullet("POSIZIONE ATTUALE", "Riquadro in basso a destra: coordinate, accuratezza, orario fix (su Android anche velocità).")
    pdf.bullet("NORD magnetico", "In alto a destra: prua in gradi.")
    pdf.bullet("Scala + quota", "In alto al centro: scala della mappa e metri s.l.m. se disponibili.")
    pdf.h2("Comandi sulla mappa")
    pdf.bullet("Strati (icona due quadrati, in alto a sinistra)", "Strato: Stradale (OSM), Topografica (curve di livello), Satellite / ortofoto.")
    pdf.bullet(
        "Icona sentiero (zigzag)",
        "Accende/spegne l'overlay dei sentieri (waymarked trails). Bordo giallo = acceso.",
    )
    pdf.bullet(
        "Tasto C (confini comunali)",
        "Apre l’elenco dei comuni. Spunta uno o più nomi, oppure «Tutti i confini» / «Nessuno». "
        "Il tasto C diventa rosso se almeno un confine è acceso. Se l’elenco è vuoto, il TOC deve caricarli in Anagrafica.",
    )
    pdf.bullet("Tasto centra (cerchio con punto)", "Centra sulla tua posizione (follow). Dopo un pan a un dito il follow si toglie: ritocca il tasto per riprenderlo.")
    pdf.bullet(
        "X verde / rossa",
        "Verde = nord dinamico (la mappa ruota con la prua, freccia fissa in alto). "
        "Rossa = nord in alto (mappa ferma, meglio per vedere WP e tracce).",
    )
    pdf.p("Pinch = zoom. Lo zoom non torna da solo all’inquadratura iniziale.")
    pdf.p(
        "Cambio strato o tasto sentieri: la mappa resta dove l’hai lasciata (pan e zoom). "
        "Non torna da sola sulla tua posizione. Per ricentrarti usa il tasto centra."
    )

    pdf.h2("7.8  Clear data")
    pdf.p(
        "Tasto rosso. Cancella coordinate BASE/PTG, rotta, overlay WP/TRK in mappa, flag WP & TRK, "
        "e ferma Vai a BASE / TRK in corso. Non cancella i file waypoint e tracce già salvati in locale, "
        "né la sessione di log-in."
    )

    pdf.h2("7.9  Confini comunali  (app e TOC)")
    pdf.p(
        "I confini dei comuni si vedono sulla mappa della sala TOC e su quella del telefono "
        "(Android e iPhone), con riga rosso vivo. Puoi accenderne più di uno insieme. "
        "Non si disegnano sul telefono: arrivano da Storage, caricati solo dal TOC."
    )
    pdf.bullet(
        "In sala TOC",
        "Sulla mappa, tasto Confini: spunte sui comuni, Tutti / Nessuno. "
        "Per aggiungere o togliere un comune: Anagrafica → Confini comunali → nome + file GeoJSON "
        "(oppure Elimina). Non serve una nuova APK né una nuova IPA: TOC e telefoni scaricano l’elenco aggiornato.",
    )
    pdf.bullet(
        "Sul telefono",
        "In MAPPA, tasto C. Stesso elenco della sala. Serve connessione (come per WP di missione). "
        "Un comune nuovo comparirà al prossimo ingresso in mappa, senza reinstallare l’app.",
    )
    pdf.note(
        "Solo l’admin TOC carica i GeoJSON in Anagrafica. L’operatore in campo accende/spegne "
        "quelli già presenti, non ne inserisce di nuovi dal telefono."
    )

    pdf.h1("8.  Misura in mappa  e  navigazione da te")
    pdf.p(
        "In mappa tocchi due pin: distanza (linea d’aria) e direzione (gradi + rumba, es. 168° SSE), "
        "con linea gialla. Combinazioni: operatore↔operatore, operatore↔WP, WP↔WP, tu↔WP."
    )
    pdf.bullet(
        "Android — riquadro MISURA",
        "In basso a sinistra. Se nella coppia NON c’è la tua posizione: solo misura. "
        "Se uno dei due punti SEI TU, parte anche la navigazione sullo schermo GPS "
        "(tocca la tua freccia, poi il WP; chiudi la mappa: distanza e freccia grande).",
    )
    pdf.ios(
        "riquadro MISURA",
        "In alto a sinistra, bordo giallo. Due tap = solo misura, anche se uno dei pin sei tu. "
        "Per la bussola: tasto «Naviga verso {nome}» nella finestra MISURA (se c’è un WP), oppure VAI in WP & TRK. "
        "Il banner blu in alto («→ CP_03: 5,41 km») è la navigazione, non la misura.",
    )
    pdf.bullet(
        "Salva WP",
        "Compare solo per le posizioni degli OPERATORI (snapshot locale, es. LUPO_WP_LOST). "
        "Se il punto è già un waypoint — locale o di missione — il salvataggio non c’è: è già salvato.",
    )
    pdf.bullet("Annulla", "Toglie la misura e la linea gialla.")
    pdf.p("Ri-tap sullo stesso pin: lo togli dalla coppia. Un terzo tap sostituisce il secondo punto.")
    pdf.bullet(
        "Chiudi Vai a BASE",
        "Ferma la navigazione (distanza e freccia). Non cancella i WP in mappa.",
    )

    pdf.h1("9.  Cosa fare se…")
    pdf.bullet("Non vedo un operatore in mappa", "È online? Ha il GPS? Il flag visibile è acceso? Solo LUPO vede i nascosti.")
    pdf.bullet(
        "Non vedo i confini comunali",
        "In mappa apri il tasto C e spunta il comune (o Tutti). "
        "Se l’elenco è vuoto: in TOC Anagrafica → Confini comunali carica il GeoJSON.",
    )
    pdf.ios(
        "Non vedo la finestra MISURA",
        "Tocca il pin (pallino/casa), non la scritta. Serve un secondo pin. Finestra in alto a sinistra, bordo giallo.",
    )
    pdf.ios(
        "GPS impreciso (es. 100–200 m)",
        "Posizione precisa ON. All’aperto. Attendi qualche secondo. In casa o sotto tettoia il fix resta grosso.",
    )
    pdf.bullet("Non ho fix GPS", "Esci all’aperto, attendi, verifica il permesso posizione. In casa il fix può mancare o essere impreciso.")
    pdf.bullet(
        "Il TRK fa tratti dritti / si ferma in tasca",
        "Android: notifica tracking e batteria (cap. 2). iPhone: Localizzazione = Sempre; non chiudere l’app dallo switcher.",
    )
    pdf.bullet("La bussola è storta", "Calibrazione a 8 e, se serve, correzione ±15°/±90°.")
    pdf.bullet("L’app non entra", "Evento attivo sul TOC? Codice/password? Già online su un altro telefono?")
    pdf.bullet(
        "Devo andare da me a un WP (es. CP_01)",
        "Android: mappa, tocca la tua freccia GPS poi il WP, chiudi la mappa. "
        "iPhone: VAI in WP & TRK, oppure MISURA → Naviga verso.",
    )
    pdf.bullet(
        "Ho inserito un WP e non voglio navigarci",
        "Normale: compare solo in mappa. La navigazione parte solo se la avvii tu (cap. 8).",
    )
    pdf.ios(
        "L’app non si installa da Diawi",
        "UDID del telefono nel profilo Ad Hoc? Disinstallata la copia precedente? Autorizzato lo sviluppatore?",
    )

    pdf.ln(8)
    pdf.set_font("ArialIt", "I", 10)
    pdf.set_text_color(*MUTED)
    pdf.multi_cell(
        0,
        5.4,
        "Fine della guida unica Android + iPhone (fino al GPS, versione "
        + VERSION
        + "). Funzioni successive verranno aggiunte in questo stesso PDF.",
    )

    pdf.output(str(OUT))
    return OUT


if __name__ == "__main__":
    path = build()
    print(path)
