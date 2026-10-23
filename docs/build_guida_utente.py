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
VERSION = "1.0.70"


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
        if self.will_page_break(16):
            self.add_page()
        self.start_section(text, level=0)
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
        if self.will_page_break(14):
            self.add_page()
        self.start_section(text, level=1)
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


def render_toc(pdf: GuidaPdf, outline: list) -> None:
    pdf.set_x(pdf.l_margin)
    pdf.set_font("ArialIt", "B", 14)
    pdf.set_text_color(*NAVY)
    pdf.cell(0, 8, "Sommario", new_x="LMARGIN", new_y="NEXT")
    pdf.set_draw_color(*GREEN)
    pdf.set_line_width(0.6)
    y = pdf.get_y()
    pdf.line(12, y, 80, y)
    pdf.ln(4)
    for section in outline:
        indent = 7 * section.level
        left = pdf.l_margin + indent
        usable = pdf.epw - indent
        page_s = str(section.page_number)
        title = section.name
        pdf.set_font("ArialIt", "B" if section.level == 0 else "", 11 if section.level == 0 else 10.5)
        pdf.set_text_color(*NAVY if section.level == 0 else DARK)
        title_w = pdf.get_string_width(title) + 1.6
        page_w = pdf.get_string_width(page_s) + 1.2
        link = pdf.add_link(page=section.page_number)
        pdf.set_x(left)
        if title_w + page_w + 4 > usable:
            pdf.multi_cell(usable, 5.6, f"{title}    {page_s}", link=link)
            continue
        pdf.cell(title_w, 6.2, title, link=link)
        dots_w = usable - title_w - page_w
        pdf.set_font("ArialIt", "", 10.5)
        pdf.set_text_color(*LINE)
        dot_w = pdf.get_string_width(".")
        count = max(0, int(dots_w / dot_w) - 1) if dot_w else 0
        pdf.cell(dots_w, 6.2, "." * count, align="R", link=link)
        pdf.set_font("ArialIt", "B", 10.5)
        pdf.set_text_color(*NAVY)
        pdf.cell(page_w, 6.2, page_s, align="R", new_x="LMARGIN", new_y="NEXT", link=link)
    pdf.set_text_color(*DARK)


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
    pdf.insert_toc_placeholder(render_toc, pages=1, reset_page_indices=False)

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
        "Copia sul telefono il file APK (es. toc_sar_KMP_1.0.71.apk) e installa. "
        "Se Android lo chiede, consenti «origini sconosciute» solo per TOC SAR.",
    )
    pdf.ios(
        "Installazione (IPA / Diawi)",
        "Apri il link Diawi, tocca Installa. Se TOC SAR è già installata, DISINSTALLALA prima "
        "(tieni premuta l’icona → Rimuovi app), altrimenti resta la versione vecchia. "
        "Non usare l’APK Android sull’iPhone: serve toc_sar_iOS_1.0.70.ipa (o il link Diawi di quella versione).",
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
    pdf.p(
        "Dalla home, tasto verde Log-in. Schermata «Log-in operatore»: codice TOC + codice + password."
    )
    pdf.bullet(
        "Codice TOC",
        "È la sala in cui si entra (es. NVANSMI), lo stesso per tutti gli operatori di quel TOC. "
        "Non è l’ente di appartenenza dell’anagrafica. Si scrive in maiuscolo. "
        "Se il telefono lo ricorda, in alto compare «TOC: …» e il link giallo «Cambia TOC».",
    )
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
        "poi si può rientrare. "
        "In anagrafica TOC gli operatori stanno in un unico elenco: ciascuno ha la propria associazione "
        "(ente di appartenenza) e il colore del cerchio. Quel colore è il pallino in mappa."
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
        "Apre la funzione GPS (capitolo 7): traccia, waypoint, rotta verso un collega, selezione e mappa.",
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

    pdf.h1("7.  Funzione GPS")
    pdf.p(
        "Dalla home, tasto giallo GPS. Qui sotto c’è quello che trovi subito dopo, "
        "poi un sotto-punto per ogni operazione da fare in campo."
    )
    pdf.bullet(
        "In alto",
        "Freccia indietro: torna alla home. Una traccia in corso non si ferma. "
        "Notte / Giorno: inverte i colori dello schermo GPS.",
    )
    pdf.bullet(
        "Bussola",
        "Barra gialla «Bussola imprecisa? Guida calibrazione». "
        "Movimento a 8, lento, 10–15 secondi. Se il Nord resta storto: ±15° o ±90°. «Azzera» toglie la correzione. "
        "Telefono in verticale. La rosa mostra il Nord magnetico.",
    )
    pdf.bullet(
        "BASE e PATTUGLIA",
        "BASE = dove vuoi arrivare. PATTUGLIA (PTG) = dove sei. "
        "«Imposta BASE da GPS» e «Imposta PTG da GPS» copiano il fix attuale (serve segnale all’aperto). "
        "Puoi anche scrivere lat, lon e quota a mano. Sotto compare l’accuratezza (acc).",
    )
    pdf.bullet(
        "Tasti",
        "START TRK, INS WP, Calcola rotta, Vai a BASE, WP & TRK, MAPPA. "
        "In basso: Distanza, Bearing e la rosa. In navigazione la rosa diventa una freccia "
        "e compare «Chiudi Vai a BASE».",
    )
    pdf.bullet(
        "Clear data",
        "Tasto rosso. Toglie dalla mappa i waypoint e le tracce selezionati, azzera BASE e PATTUGLIA, "
        "ferma la navigazione e la registrazione in corso, azzera TIME e DIST. "
        "Non cancella i file già salvati sul telefono e non fa il log-out.",
    )
    pdf.note(
        "TIME e DIST (striscia gialla) compaiono quando registri una traccia o quando il cronometro è in pausa. "
        "Restano anche se esci dal GPS. I dettagli sono nel punto 7.1."
    )

    pdf.h2("7.1  Memorizzare una traccia")
    pdf.p(
        "Registra il percorso che stai facendo (bonifica, ricerca, spostamento). "
        "Un punto circa ogni 3 metri, scartando i salti del GPS rumoroso."
    )
    pdf.bullet(
        "Cosa premere per partire",
        "START TRK. Il tasto diventa rosso STOP TRK e compare «REC · N punti». "
        "Parte anche la striscia gialla TIME (ore:minuti:secondi) e DIST (metri).",
    )
    pdf.bullet(
        "Mentre registra",
        "Puoi uscire dal GPS, tornare in home o aprire la mappa: la traccia continua. "
        "Android: continua in tasca se resta la notifica «Tracking operatore». "
        "iPhone: Localizzazione = Sempre; non chiudere l’app dallo switcher.",
    )
    pdf.bullet(
        "Come fare STOP della traccia",
        "STOP TRK. Ferma la registrazione e ferma anche TIME/DIST. "
        "Con almeno 2 punti si apre «Report traccia»: il nome diventa CODICE_TRK_ più quello che scrivi. "
        "Salva = file sul telefono + riepilogo al TOC. Salva + invia = uguale, e apre la condivisione del file .trk. "
        "Annulla = scarta tutto, non resta il file. Con meno di 2 punti non salva.",
    )
    pdf.bullet(
        "Fermare solo TIME e DIST",
        "Sulla striscia gialla, STOP mette in pausa il cronometro (il tasto diventa ST). "
        "Se START TRK è ancora rosso, la traccia continua. "
        "ST con pressione breve fa ripartire tempo e distanza. "
        "Pressione lunga su STOP o ST azzera TIME e DIST.",
    )
    pdf.p(
        "Per togliere la traccia dalla mappa senza cancellare il file già salvato: Clear data. "
        "Il Log-out ferma anche la registrazione ancora accesa."
    )

    pdf.h2("7.2  Inserire un waypoint")
    pdf.p("Segna un punto con un nome: bivio, ritrovamento, base di settore.")
    pdf.bullet(
        "Come si fa",
        "Tasto arancione INS WP. Scrivi il nome: diventa CODICE_WP_ più il testo "
        "(es. LUPO_WP_BIVIO). Lat e lon sono obbligatorie, la quota no. "
        "«Da GPS» copia il fix attuale; altrimenti scrivi le coordinate a mano. Poi Salva.",
    )
    pdf.bullet(
        "Dove salva",
        "Sul telefono, in WP & TRK → WP LOCALI. "
        "Dopo Salva il punto compare in mappa (casa verde). "
        "Non parte da solo la navigazione verso quel punto.",
    )
    pdf.note(
        "INS WP crea solo un waypoint di questo telefono. "
        "I waypoint di missione arrivano dal TOC e stanno in WP & TRK → WP MISSIONE: da qui non si cancellano."
    )

    pdf.h2("7.3  Devo raggiungere un collega (conosco le coordinate)")
    pdf.p(
        "BASE è la destinazione (il collega, o il punto che ti hanno dato). "
        "PATTUGLIA sei tu."
    )
    pdf.bullet(
        "Coordinate del collega",
        "Nella colonna BASE scrivi lat, lon e, se la conosci, la quota. "
        "Se invece sei tu sul punto da usare come meta, «Imposta BASE da GPS».",
    )
    pdf.bullet(
        "La tua posizione",
        "«Imposta PTG da GPS». In navigazione queste coordinate si aggiornano da sole.",
    )
    pdf.bullet(
        "Calcola rotta",
        "Una lettura sola: distanza in linea d’aria e bearing in gradi, da te verso BASE. "
        "Non ti segue mentre cammini.",
    )
    pdf.bullet(
        "Vai a BASE",
        "Navigazione continua: distanza aggiornata e freccia al posto della rosa. "
        "«Chiudi Vai a BASE» ferma solo la freccia. Le coordinate restano.",
    )
    pdf.p(
        "Se il collega è già un pin in mappa (operatore online o waypoint) e non devi copiare i numeri: "
        "su Android tocca la tua freccia e poi il pin. "
        "Su iPhone due tocchi fanno solo la misura; per la bussola usa VAI in WP & TRK "
        "oppure «Naviga verso» nella finestra MISURA (punto 7.6)."
    )

    pdf.h2("7.4  Voglio selezionare uno o più waypoint o una o più tracce")
    pdf.p(
        "Tasto blu WP & TRK. La spunta mette quel waypoint o quella traccia sulla mappa. "
        "Puoi spuntarne più di uno. Le spunte restano anche uscendo dal GPS, finché non fai Clear data."
    )
    pdf.bullet(
        "Cartelle di missione",
        "WP MISSIONE e TRK MISSIONE arrivano dal TOC, raggruppati per cartella. "
        "Tocca il nome per aprire la cartella. La spunta sulla cartella seleziona tutti i file; "
        "la spunta sul file ne seleziona uno solo. Una cartella vuota non compare.",
    )
    pdf.bullet(
        "Sul telefono",
        "WP LOCALI: creati con INS WP. Spunta, Invia, Elimina. Su iPhone c’è anche VAI. "
        "TRACCE LOCALI: salvate dopo STOP TRK. Spunta, Invia, REPORT, MAPPA (solo quella), Inverti, Elimina.",
    )
    pdf.bullet(
        "Tracce di missione",
        "A cartella aperta: Invia, REPORT, MAPPA. "
        "Non ci sono Inverti né Elimina: il file resta sul TOC.",
    )
    pdf.bullet(
        "Metterle in mappa",
        "In fondo all’elenco, MAPPA (n) apre la mappa con tutto ciò che è spuntato. "
        "Chiudi torna al GPS senza togliere le spunte. "
        "«Importa file…» porta dentro un .wpt o un .trk ricevuto (Condividi / Apri con).",
    )

    pdf.h2("7.5  Voglio visualizzare la mappa")
    pdf.p(
        "Tasto viola MAPPA, oppure MAPPA dentro WP & TRK. "
        "Schermo intero: la tua posizione, gli operatori visibili, i waypoint e le tracce spuntati, "
        "e la scia rossa se stai registrando."
    )
    pdf.bullet(
        "Aprire e chiudere",
        "La freccia indietro chiude solo la mappa e torna al GPS, non alla home. "
        "Traccia, waypoint selezionati e TIME/DIST restano attivi.",
    )
    pdf.bullet(
        "Muoverla",
        "Due dita = zoom. Un dito sposta la mappa e toglie l’inseguimento della tua posizione. "
        "Lo zoom non torna da solo. Per rimetterti al centro usa il tasto con il punto nel cerchio (punto 7.6).",
    )
    pdf.bullet("Freccia rossa + il tuo codice", "Sei tu. Se l’inseguimento è acceso, stai al centro.")
    pdf.bullet(
        "Pallino colorato + codice",
        "Altro operatore online e visibile (cap. 6). Il colore è quello scelto in anagrafica per quella persona, "
        "così si distinguono operatori di associazioni diverse. Su iPhone il codice può essere abbreviato.",
    )
    pdf.bullet("Casa verde + nome", "Waypoint selezionato o appena inserito.")
    pdf.bullet("Linea rossa", "Traccia che stai registrando adesso.")
    pdf.bullet("Linee colorate", "Tracce caricate da WP & TRK.")
    pdf.bullet("Contorno rosso", "Confini comunali accesi.")
    pdf.bullet("Linea gialla", "Misura tra due pin.")
    pdf.ios(
        "Banner blu verso un WP",
        "Se hai avviato VAI o «Naviga verso», è la rotta (distanza in alto). Non è la misura gialla.",
    )

    pdf.h2("7.6  In MAPPA: tasti e visualizzazioni")
    pdf.p("I tasti della mappa, e quale carta puoi accendere.")
    pdf.bullet(
        "Strati (due quadrati, in alto a sinistra)",
        "Tre visualizzazioni: Stradale (OSM), Topografica (curve di livello), Satellite / ortofoto. "
        "Il cambio non sposta la mappa e non ti ricentra.",
    )
    pdf.bullet(
        "Pulsante arancione",
        "Mostra o nasconde la barra in basso: coordinate, accuratezza, quota, batteria, TIME/DIST "
        "e il tasto per inserire un waypoint sul fix attuale.",
    )
    pdf.bullet(
        "Sentieri",
        "Icona del sentiero. Accende o spegne i sentieri sopra la carta. Bordo acceso = sentieri visibili.",
    )
    pdf.bullet(
        "C — confini comunali",
        "Elenco dei comuni. Spunta uno o più nomi, oppure «Tutti i confini» / «Nessuno». "
        "La C è rossa se almeno un confine è acceso. "
        "L’elenco lo carica il TOC in Anagrafica (file GeoJSON): dal telefono si accende e si spegne, non si aggiunge. "
        "Serve connessione. Un comune nuovo compare al prossimo ingresso in mappa, senza reinstallare l’app.",
    )
    pdf.bullet(
        "Centra (cerchio con punto, in basso a sinistra)",
        "Ti rimette al centro e riaccende l’inseguimento. Dopo uno spostamento a un dito va ritoccato.",
    )
    pdf.bullet(
        "X verde o rossa",
        "Verde: la mappa ruota con la prua, la freccia resta in alto. "
        "Rossa: il Nord resta in alto, meglio per leggere waypoint e tracce. "
        "Con waypoint o tracce in mappa si parte con il Nord in alto.",
    )
    pdf.bullet(
        "Prua, scala, quota",
        "In alto a destra: prua in gradi (Nord magnetico). In alto al centro: scala della mappa e metri s.l.m., se disponibili.",
    )
    pdf.bullet(
        "Misura",
        "Tocca due pin (tu, un operatore o un waypoint): distanza in linea d’aria, direzione in gradi, linea gialla. "
        "Stesso pin un’altra volta: lo togli. Un terzo tocco sostituisce il secondo punto. Annulla toglie la misura.",
    )
    pdf.bullet(
        "Android — dalla misura alla bussola",
        "Se uno dei due pin sei tu, parte anche la navigazione sullo schermo GPS. "
        "Chiudi la mappa: distanza e freccia grande. «Chiudi Vai a BASE» ferma solo quella freccia.",
    )
    pdf.ios(
        "dalla misura alla bussola",
        "Due tocchi = solo misura, anche se uno dei pin sei tu. "
        "Per la freccia: «Naviga verso» nella finestra MISURA (bordo giallo, in alto a sinistra) oppure VAI in WP & TRK.",
    )
    pdf.bullet(
        "Salva WP da un operatore",
        "Nella misura, Salva WP compare solo sulla posizione di un operatore (es. LUPO_WP_LOST), sul telefono. "
        "Se il pin è già un waypoint, il salvataggio non c’è.",
    )
    pdf.note(
        "In sala TOC gli stessi confini si accendono con il tasto Confini. "
        "Per aggiungere o togliere un comune: Anagrafica → Confini comunali. "
        "Non serve una nuova APK né una nuova IPA."
    )

    pdf.h1("8.  Cosa fare se…")
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
        "Normale: compare solo in mappa. La navigazione parte solo se la avvii tu (punti 7.3 e 7.6).",
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
