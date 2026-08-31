"""Genera docs/TOC_SAR_Guida_utente_iOS.pdf — A4, loghi ANSMI + UCRS."""

from __future__ import annotations

from pathlib import Path

from fpdf import FPDF

ROOT = Path(__file__).resolve().parents[1]
DOCS = Path(__file__).resolve().parent
DRAWABLE = ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "drawable"
LOGO_ANSMI = DRAWABLE / "logo_ansmi.png"
LOGO_UCRS = DRAWABLE / "logo_ucrs.png"
OUT = DOCS / "TOC_SAR_Guida_utente_iOS.pdf"

NAVY = (11, 53, 107)
GREEN = (7, 155, 66)
DARK = (25, 25, 25)
MUTED = (70, 70, 70)
LINE = (180, 180, 180)
VERSION = "1.0.49"


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
        self.cell(150, 8, "TOC SAR  —  Guida utente iOS", align="C")
        self.set_xy(self.l_margin, 26)

    def footer(self) -> None:
        self.set_y(-14)
        self.set_draw_color(*LINE)
        self.line(12, self.get_y(), 198, self.get_y())
        self.set_y(-12)
        self.set_font("ArialIt", "", 8)
        self.set_text_color(*MUTED)
        self.cell(90, 8, "ANSMI  ·  Nucleo Volontari  ·  UCRS", align="L")
        self.cell(96, 8, f"iOS  ·  v. {VERSION}   pag. {self.page_no()}", align="R")

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
    pdf.cell(
        0,
        7,
        "NUCLEO VOLONTARI  ·  ASSOCIAZIONE NAZIONALE SANITÀ MILITARE ITALIANA",
        align="C",
        new_x="LMARGIN",
        new_y="NEXT",
    )
    pdf.set_font("ArialIt", "B", 11)
    pdf.cell(
        0,
        7,
        "REPARTO CINOFILI DA RICERCA E SOCCORSO  (UCRS)",
        align="C",
        new_x="LMARGIN",
        new_y="NEXT",
    )
    pdf.ln(10)
    pdf.set_font("ArialIt", "B", 26)
    pdf.set_text_color(*NAVY)
    pdf.cell(0, 12, "TOC SAR", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("ArialIt", "B", 16)
    pdf.cell(0, 9, "Guida utente operatore  —  iPhone", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.ln(4)
    pdf.set_font("ArialIt", "", 12)
    pdf.set_text_color(*MUTED)
    pdf.cell(0, 7, f"App iOS  ·  versione {VERSION}", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.cell(
        0,
        7,
        "Dal log-in al modulo GPS (mappa, waypoint, tracce, misura)",
        align="C",
        new_x="LMARGIN",
        new_y="NEXT",
    )
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
        "Questa guida è per l’iPhone. Le funzioni (log-in, GPS, WP, TRK, mappa, misura) "
        "sono le stesse della versione Android. Cambia l’installazione, i permessi di sistema "
        "e qualche dettaglio sulla mappa.",
        align="C",
    )
    pdf.ln(16)
    pdf.set_font("ArialIt", "I", 10)
    pdf.set_text_color(*MUTED)
    pdf.multi_cell(
        0,
        5.5,
        "Login = tracking verso il TOC. Per continuare in tasca e a schermo spento serve il "
        "permesso Posizione «Sempre». Il log-out è solo esplicito: tasto Log-out o force log-out da admin (LUPO).",
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
        "registrazione traccia, mappa e misura tra operatori e waypoint. "
        "Non sostituisce il TOC: è lo strumento in mano all’operatore."
    )

    pdf.h1("2.  Prima di usare l’app (iPhone)")
    pdf.bullet(
        "Installazione (IPA / Diawi)",
        "Apri il link Diawi sul telefono, tocca Installa. "
        "Se TOC SAR è già installata, DISINSTALLALA prima (tieni premuta l’icona → Rimuovi app). "
        "Altrimenti resta la versione vecchia e le correzioni non arrivano.",
    )
    pdf.bullet(
        "Sviluppatore non verificato",
        "Al primo avvio iOS può bloccare l’app. Vai in Impostazioni → Generali → VPN e gestione dispositivo "
        "(oppure Profilo e gestione dispositivi) → sviluppatore ANSMI / team TOC SAR → Autorizza. "
        "Poi riapri TOC SAR.",
    )
    pdf.bullet(
        "Permesso Posizione",
        "Alla prima richiesta scegli «Consenti durante l’uso». Poi, quando l’app lo chiede, "
        "scegli «Passa a Sempre» (o Impostazioni → Privacy e sicurezza → Localizzazione → TOC SAR → Sempre). "
        "«Durante l’uso» basta per la mappa a schermo acceso; «Sempre» serve per il tracking in tasca.",
    )
    pdf.bullet(
        "Posizione precisa",
        "Impostazioni → Privacy e sicurezza → Localizzazione → TOC SAR → Posizione precisa: ON. "
        "Se è spenta iOS dà una posizione approssimata (centinaia di metri) e la mappa / il TOC non sono utilizzabili.",
    )
    pdf.bullet(
        "Notifiche",
        "Concedile se richieste: servono per i messaggi dal TOC.",
    )
    pdf.note(
        "Non usare l’APK Android sull’iPhone. Serve il file toc_sar_iOS_1.0.49.ipa (o il link Diawi di quella versione). "
        "Ad ogni aggiornamento: disinstalla, poi installa di nuovo."
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
        "Apre le Impostazioni iOS dell’app (Localizzazione, notifiche).",
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
        "sparisci dalla mappa degli altri operatori. Per tornare visibili: di nuovo Log-in."
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
        "La calibrazione del sensore la fa il telefono (iOS).",
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
        "(o verso il WP se stavi navigando a un WP)."
    )

    pdf.h2("7.4  START TRK  /  STOP TRK")
    pdf.p(
        "Registra il percorso dell’operatore (bonifica, ricerca, spostamento). "
        "Un punto circa ogni 3 metri, filtrato per non fare «stelle» da GPS rumoroso."
    )
    pdf.bullet(
        "START TRK",
        "Il tasto diventa rosso STOP TRK. Compare «REC · N punti». "
        "Con permesso Posizione «Sempre» la registrazione continua in tasca e a schermo spento.",
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
        "Dopo Salva il waypoint viene messo in mappa. Non parte il calcolo da te verso quel punto: "
        "lo vedi, poi in mappa scegli tu cosa misurare (tap sul pin) oppure usi VAI / Naviga verso."
    )
    pdf.note(
        "I waypoint di missione (dal foglio missione / elenco missione) e quelli locali stanno in WP & TRK. "
        "INS WP crea solo un WP locale su questo telefono."
    )

    pdf.h2("7.6  WP & TRK")
    pdf.p("Elenco di tutto ciò che puoi mettere in mappa o condividere.")
    pdf.bullet(
        "WP MISSIONE",
        "Waypoint della missione (non si cancellano da qui). Flag = visibile in MAPPA. "
        "Tasto VAI = navigazione live verso quel WP (chiude l’elenco e usa la bussola, come Vai a BASE).",
    )
    pdf.bullet(
        "WP LOCALI",
        "Quelli creati con INS WP o salvati da un operatore in mappa. Flag, VAI, Invia (condividi file), Elimina.",
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
    pdf.bullet("Pallino colorato + codice (prime 3 lettere)", "Altro operatore online visibile (vedi cap. 6).")
    pdf.bullet("Casa verde + nome", "Waypoint in overlay (missione o locale flaggato, o destinazione BASE).")
    pdf.bullet("Linea verso un WP", "Se hai avviato VAI / Naviga verso, è la rotta dalla tua posizione al WP (banner in alto: distanza).")
    pdf.bullet("Linea gialla", "Misura tra i due pin scelti (finestra MISURA).")
    pdf.bullet("Linee colorate", "Tracce salvate caricate da WP & TRK.")
    pdf.bullet("POSIZIONE ATTUALE", "Riquadro in basso a destra: coordinate, accuratezza, orario fix.")
    pdf.bullet("NORD magnetico", "In alto a destra: prua in gradi.")
    pdf.bullet("Scala + quota", "In alto al centro: scala della mappa e metri s.l.m. se disponibili.")
    pdf.h2("Comandi sulla mappa")
    pdf.bullet(
        "Strati (icona due quadrati, in alto a sinistra)",
        "Strato: Stradale (OSM), Topografica (curve di livello), Satellite / ortofoto.",
    )
    pdf.bullet(
        "Icona sentiero (zigzag)",
        "Accende/spegne l’overlay dei sentieri (waymarked trails). Bordo giallo = acceso.",
    )
    pdf.bullet(
        "Tasto centra (cerchio con punto)",
        "Centra sulla tua posizione (follow). Dopo un pan a un dito il follow si toglie: ritocca il tasto per riprenderlo.",
    )
    pdf.bullet(
        "X verde / rossa",
        "Verde = nord dinamico (la mappa ruota con la prua, freccia fissa in alto). "
        "Rossa = nord in alto (mappa ferma, meglio per vedere WP e tracce).",
    )
    pdf.p("Pinch = zoom. Lo zoom non torna da solo all’inquadratura iniziale.")
    pdf.p(
        "Cambio strato o tasto sentieri: la mappa resta dove l’hai lasciata (pan e zoom). "
        "Per ricentrarti usa il tasto centra."
    )

    pdf.h2("7.8  Clear data")
    pdf.p(
        "Tasto rosso. Cancella coordinate BASE/PTG, rotta, overlay WP/TRK in mappa, flag WP & TRK, "
        "e ferma Vai a BASE / TRK in corso. Non cancella i file waypoint e tracce già salvati in locale, "
        "né la sessione di log-in."
    )

    pdf.h1("8.  Misura in mappa")
    pdf.p(
        "Non parte da sola dalla tua posizione. In mappa tocchi due punti e compare la finestra gialla "
        "MISURA (in alto a sinistra) con distanza (linea d’aria) e direzione (gradi + rumba, es. 168° SSE)."
    )
    pdf.p("Come fare:")
    pdf.bullet("1. Primo tap", "Tocca un pin (casa WP, pin operatore, o la tua freccia / pin GPS).")
    pdf.bullet("2. Secondo tap", "Tocca il secondo pin. Compare MISURA con Dist e Dir, e una linea gialla.")
    pdf.p("Combinazioni:")
    pdf.bullet("Waypoint → waypoint", "Due case verdi. Es. CP_02 e CP_03.")
    pdf.bullet("Operatore → operatore", "Due pin colorati (es. LOST e RAGGHY).")
    pdf.bullet("Operatore → waypoint", "Pin operatore e casa WP.")
    pdf.bullet(
        "Tu → un WP",
        "Tocca la tua freccia GPS (o il pin della tua posizione se hai fatto pan), poi il WP. "
        "È la misura da te al punto, non la navigazione continua.",
    )
    pdf.bullet(
        "Naviga verso {nome}",
        "Nella finestra MISURA, se uno dei due punti è un WP: chiude la mappa e avvia la bussola verso quel WP "
        "(come VAI in WP & TRK).",
    )
    pdf.bullet(
        "Salva WP",
        "Compare solo per le posizioni degli OPERATORI (snapshot locale, es. LUPO_WP_LOST). "
        "Se il punto è già un waypoint non c’è: è già salvato.",
    )
    pdf.bullet("Annulla", "Toglie la misura e la linea gialla.")
    pdf.p("Ri-tap sullo stesso pin: lo togli dalla coppia. Un terzo tap sostituisce il secondo punto.")
    pdf.note(
        "Il banner blu in alto («→ CP_03: 5,41 km») è la NAVIGAZIONE verso BASE/WP, non la misura. "
        "MISURA è solo la finestra gialla dopo due tap sui pin. "
        "VAI / Naviga verso = bussola continua dalla tua posizione. La misura confronta due pin scelti."
    )

    pdf.h1("9.  Cosa fare se…")
    pdf.bullet(
        "Non vedo la finestra MISURA",
        "Tocca il pin (il pallino/casa), non la scritta sulla mappa. Serve un secondo pin. "
        "La finestra è in alto a sinistra, bordo giallo. Se non compare, reinstallare la 1.0.49.",
    )
    pdf.bullet("Non vedo un operatore in mappa", "È online? Ha il GPS? Il flag visibile è acceso? Solo LUPO vede i nascosti.")
    pdf.bullet(
        "GPS impreciso (es. 100–200 m)",
        "Posizione precisa ON. All’aperto. Attendi qualche secondo. In casa o sotto tettoia il fix resta grosso.",
    )
    pdf.bullet("Non ho fix GPS", "Esci all’aperto, verifica Localizzazione TOC SAR. In casa il fix può mancare.")
    pdf.bullet(
        "Il TRK si ferma in tasca",
        "Localizzazione TOC SAR = Sempre. Non fare «Chiudi app» dallo switcher se ti serve il tracking.",
    )
    pdf.bullet("La bussola è storta", "Calibrazione a 8 e, se serve, correzione ±15°/±90°.")
    pdf.bullet("L’app non entra", "Evento attivo sul TOC? Codice/password? Già online su un altro telefono?")
    pdf.bullet("Ho inserito un WP e non voglio navigarci", "Normale: compare solo in mappa. Per misurare, tocca due pin. Per navigare: VAI.")
    pdf.bullet("L’app non si installa da Diawi", "UDID del telefono nel profilo Ad Hoc? Disinstallata la copia precedente? Autorizzato lo sviluppatore?")

    pdf.ln(8)
    pdf.set_font("ArialIt", "I", 10)
    pdf.set_text_color(*MUTED)
    pdf.multi_cell(
        0,
        5.4,
        "Fine della guida iOS per lo stato attuale dell’app (fino al GPS, versione 1.0.49). "
        "La guida Android resta il documento gemello per telefono Android.",
    )

    pdf.output(str(OUT))
    return OUT


if __name__ == "__main__":
    path = build()
    print(path)
