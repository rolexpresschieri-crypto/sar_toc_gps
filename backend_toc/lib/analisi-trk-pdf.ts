import { enteLogoUrl } from "@/lib/ente-logo";
import type { SeparationStats, TrkPoint, TrkStats } from "@/lib/trk-analysis";
import {
  caneConduttoreSeparation,
  formatDistance,
  formatDuration,
  formatSpeed,
  movementTimeWindow,
  speedSeriesKmh,
} from "@/lib/trk-analysis";
import type { jsPDF } from "jspdf";

const COLOR_AREA: [number, number, number] = [46, 125, 50];
const COLOR_COND: [number, number, number] = [30, 136, 229];
const COLOR_CANE: [number, number, number] = [230, 74, 25];
const COLOR_SEP: [number, number, number] = [80, 40, 120];

function pdfText(s: string): string {
  return s
    .replace(/[—–]/g, "-")
    .replace(/[·•]/g, "-")
    .replace(/[“”]/g, '"')
    .replace(/[‘’]/g, "'")
    .replace(/\u0000/g, "");
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

function imageSize(dataUrl: string): Promise<{ w: number; h: number }> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve({ w: img.naturalWidth, h: img.naturalHeight });
    img.onerror = () => reject(new Error("Logo non leggibile."));
    img.src = dataUrl;
  });
}

async function loadEnteLogo(orgCode: string): Promise<string | null> {
  const primary = enteLogoUrl(orgCode);
  const code = orgCode.trim().toUpperCase();
  const urls = [primary, `/org/${code}.png`, `/org/${code.toLowerCase()}.png`].filter(
    (url): url is string => Boolean(url),
  );
  for (const url of urls) {
    try {
      const res = await fetch(url);
      if (!res.ok) continue;
      return await blobToDataUrl(await res.blob());
    } catch {
      continue;
    }
  }
  return null;
}

function stampLogoOnAllPages(
  doc: jsPDF,
  logoData: string,
  logoW: number,
  logoH: number,
  pageW: number,
) {
  const pages = doc.getNumberOfPages();
  for (let i = 1; i <= pages; i++) {
    doc.setPage(i);
    try {
      doc.addImage(logoData, "PNG", pageW - 12 - logoW, 6, logoW, logoH);
    } catch {
      // ignore
    }
  }
}

function statsRows(stats: TrkStats | null): string[][] {
  if (!stats) {
    return [["(non selezionato)", ""]];
  }
  const timeNote = stats.hasTime ? "si" : "no (solo percorso)";
  return [
    ["File", pdfText(stats.name)],
    ["Distanza tracciato", formatDistance(stats.distanceM)],
    [
      "Durata (in moto)",
      stats.hasTime ? formatDuration(stats.durationMs) : "n/d (senza orario)",
    ],
    [
      "V media",
      stats.hasTime ? formatSpeed(stats.avgSpeedKmh) : "n/d (senza orario)",
    ],
    [
      "V max",
      stats.hasTime ? formatSpeed(stats.maxSpeedKmh) : "n/d (senza orario)",
    ],
    ["Orario punti", timeNote],
  ];
}

function separationRows(sep: SeparationStats): string[][] {
  if (sep.samples.length === 0) {
    return [["Dati", "seleziona cane e conduttore"]];
  }
  return [
    ["Sync temporale", sep.hasTime ? "si (con orario)" : "stimata (senza orario)"],
    ["Campioni", String(sep.samples.length)],
    ["Dist. min cane-cond", sep.minM != null ? formatDistance(sep.minM) : "n/d"],
    ["Dist. media cane-cond", sep.avgM != null ? formatDistance(sep.avgM) : "n/d"],
    ["Dist. max cane-cond", sep.maxM != null ? formatDistance(sep.maxM) : "n/d"],
  ];
}

function lastTableY(doc: jsPDF, fallback: number): number {
  return (doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? fallback;
}

function formatAxis(v: number, unit: string): string {
  if (unit === "m" && v >= 1000) return `${(v / 1000).toFixed(1)} km`;
  if (unit === "m") return `${Math.round(v)}`;
  if (unit === "km/h") return v.toFixed(1);
  return v.toFixed(0);
}

function drawFrame(
  doc: jsPDF,
  x: number,
  y: number,
  w: number,
  h: number,
  title: string,
) {
  doc.setDrawColor(40, 60, 100);
  doc.setLineWidth(0.3);
  doc.rect(x, y, w, h);
  doc.setFontSize(9);
  doc.setTextColor(40, 60, 100);
  doc.text(pdfText(title), x, y - 2);
  doc.setTextColor(0, 0, 0);
}

function drawLegend(
  doc: jsPDF,
  x: number,
  y: number,
  items: Array<{ color: [number, number, number]; label: string }>,
) {
  let cx = x;
  doc.setFontSize(7);
  for (const item of items) {
    doc.setDrawColor(...item.color);
    doc.setFillColor(...item.color);
    doc.setLineWidth(1.2);
    doc.line(cx, y - 1.2, cx + 6, y - 1.2);
    doc.setTextColor(40, 40, 40);
    doc.text(pdfText(item.label), cx + 8, y);
    cx += 8 + doc.getTextWidth(pdfText(item.label)) + 8;
  }
  doc.setTextColor(0, 0, 0);
}

/** Traccia schematica lat/lon con legenda cane / conduttore / area. */
function drawTrackSchematic(
  doc: jsPDF,
  x: number,
  y: number,
  w: number,
  h: number,
  area: TrkPoint[],
  cane: TrkPoint[],
  cond: TrkPoint[],
) {
  drawFrame(doc, x, y, w, h, "Tracciato (schema)");
  const legendY = y + h + 4;
  drawLegend(doc, x, legendY, [
    { color: COLOR_AREA, label: "Area da bonificare" },
    { color: COLOR_COND, label: "Conduttore" },
    { color: COLOR_CANE, label: "Cane" },
  ]);

  const all = [...area, ...cane, ...cond];
  if (all.length < 2) {
    doc.setFontSize(9);
    doc.text("Nessuna traccia", x + 4, y + h / 2);
    return;
  }
  let minLat = Infinity;
  let maxLat = -Infinity;
  let minLon = Infinity;
  let maxLon = -Infinity;
  for (const p of all) {
    minLat = Math.min(minLat, p.lat);
    maxLat = Math.max(maxLat, p.lat);
    minLon = Math.min(minLon, p.lon);
    maxLon = Math.max(maxLon, p.lon);
  }
  const pad = 0.08;
  const dLat = Math.max(maxLat - minLat, 1e-6);
  const dLon = Math.max(maxLon - minLon, 1e-6);
  minLat -= dLat * pad;
  maxLat += dLat * pad;
  minLon -= dLon * pad;
  maxLon += dLon * pad;
  const plot = (pts: TrkPoint[], color: [number, number, number], width: number) => {
    if (pts.length < 2) return;
    doc.setDrawColor(...color);
    doc.setLineWidth(width);
    for (let i = 1; i < pts.length; i++) {
      if (pts[i]!.gapBefore) continue;
      const a = pts[i - 1]!;
      const c = pts[i]!;
      const x1 = x + ((a.lon - minLon) / (maxLon - minLon)) * w;
      const y1 = y + h - ((a.lat - minLat) / (maxLat - minLat)) * h;
      const x2 = x + ((c.lon - minLon) / (maxLon - minLon)) * w;
      const y2 = y + h - ((c.lat - minLat) / (maxLat - minLat)) * h;
      doc.line(x1, y1, x2, y2);
    }
  };
  // Area sotto, poi conduttore, cane sopra
  plot(area, COLOR_AREA, 1.1);
  plot(cond, COLOR_COND, 0.7);
  plot(cane, COLOR_CANE, 0.7);
}

function drawLineChart(
  doc: jsPDF,
  x: number,
  y: number,
  w: number,
  h: number,
  title: string,
  series: Array<{ xs: number[]; ys: number[]; color: [number, number, number]; label: string }>,
  yUnit: string,
  opts?: { maxGapX?: number },
) {
  drawFrame(doc, x, y, w, h, title);
  const usable = series.filter((s) => s.xs.length >= 2 && s.ys.length >= 2);
  if (usable.length === 0) {
    doc.setFontSize(8);
    doc.setTextColor(120);
    doc.text(pdfText("Dati insufficienti (per la velocita serve l'orario sui punti)."), x + 3, y + h / 2);
    doc.setTextColor(0, 0, 0);
    return;
  }
  let minX = Infinity;
  let maxX = -Infinity;
  let minY = 0;
  let maxY = -Infinity;
  for (const s of usable) {
    for (let i = 0; i < s.xs.length; i++) {
      minX = Math.min(minX, s.xs[i]!);
      maxX = Math.max(maxX, s.xs[i]!);
      maxY = Math.max(maxY, s.ys[i]!);
    }
  }
  if (maxX <= minX) maxX = minX + 1;
  if (maxY <= minY) maxY = minY + 1;

  const leftAxis = 14;
  const bottomPad = 8;
  const topPad = 3;
  const rightPad = 4;
  const px = x + leftAxis;
  const py = y + topPad;
  const pw = w - leftAxis - rightPad;
  const ph = h - topPad - bottomPad;

  // Griglia orizzontale + valori ordinate
  const nGrid = 4;
  doc.setFontSize(6.5);
  for (let i = 0; i <= nGrid; i++) {
    const frac = i / nGrid;
    const val = minY + (maxY - minY) * frac;
    const gy = py + ph - frac * ph;
    doc.setDrawColor(210, 210, 210);
    doc.setLineWidth(0.15);
    doc.line(px, gy, px + pw, gy);
    doc.setTextColor(90, 90, 90);
    const label = formatAxis(val, yUnit);
    doc.text(label, px - 1.5, gy + 1.2, { align: "right" });
  }
  doc.setDrawColor(120, 120, 120);
  doc.setLineWidth(0.25);
  doc.line(px, py, px, py + ph);
  doc.line(px, py + ph, px + pw, py + ph);

  const maxGap = opts?.maxGapX;
  for (const s of usable) {
    doc.setDrawColor(...s.color);
    doc.setLineWidth(0.55);
    for (let i = 1; i < s.xs.length; i++) {
      if (maxGap != null && s.xs[i]! - s.xs[i - 1]! > maxGap) continue;
      const x1 = px + ((s.xs[i - 1]! - minX) / (maxX - minX)) * pw;
      const y1 = py + ph - ((s.ys[i - 1]! - minY) / (maxY - minY)) * ph;
      const x2 = px + ((s.xs[i]! - minX) / (maxX - minX)) * pw;
      const y2 = py + ph - ((s.ys[i]! - minY) / (maxY - minY)) * ph;
      doc.line(x1, y1, x2, y2);
    }
  }

  const legendItems = usable.map((s) => ({ color: s.color, label: s.label }));
  drawLegend(doc, px, y + h - 1.5, legendItems);
  doc.setTextColor(0, 0, 0);
}

/** Genera PDF analisi area / cane / conduttore. */
export async function downloadAnalisiTrkPdf(input: {
  orgCode: string;
  orgName: string;
  area: TrkStats | null;
  cane: TrkStats | null;
  conduttore: TrkStats | null;
  areaPoints?: TrkPoint[];
  canePoints?: TrkPoint[];
  conduttorePoints?: TrkPoint[];
  /** Screenshot mappa topografica (OpenTopoMap) dalla pagina ANALISI TRK. */
  mapDataUrl?: string | null;
}): Promise<void> {
  const [{ jsPDF }, autoTableMod] = await Promise.all([import("jspdf"), import("jspdf-autotable")]);
  const autoTable =
    autoTableMod.default ??
    (autoTableMod as { autoTable?: typeof autoTableMod.default }).autoTable;
  if (!autoTable) {
    throw new Error("Libreria PDF non disponibile.");
  }

  const doc = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4" });
  const pageW = doc.internal.pageSize.getWidth();
  const pageH = doc.internal.pageSize.getHeight();
  const margin = 14;
  const when = new Date().toLocaleString("it-IT");
  const areaPts = input.areaPoints ?? [];
  const canePts = input.canePoints ?? [];
  const condPts = input.conduttorePoints ?? [];

  const logoData = await loadEnteLogo(input.orgCode);
  let logoW = 0;
  let logoH = 0;
  if (logoData) {
    try {
      const size = await imageSize(logoData);
      const scale = Math.min(22 / size.w, 12 / size.h);
      logoW = size.w * scale;
      logoH = size.h * scale;
    } catch {
      logoW = 0;
      logoH = 0;
    }
  }

  doc.setFontSize(16);
  doc.text(pdfText("TOC SAR - Analisi TRK"), margin, 18);
  doc.setFontSize(10);
  doc.text(pdfText(`${input.orgCode} - ${input.orgName}`), margin, 26);
  doc.text(pdfText(`Generato: ${when}`), margin, 32);
  doc.setDrawColor(180, 40, 40);
  doc.setLineWidth(0.6);
  doc.line(margin, 35, pageW - margin - (logoW > 0 ? logoW + 4 : 0), 35);

  let y = 42;
  const writeStats = (
    title: string,
    color: [number, number, number],
    stats: TrkStats | null,
  ) => {
    if (y > pageH - 55) {
      doc.addPage();
      y = 20;
    }
    doc.setFontSize(12);
    doc.setTextColor(...color);
    doc.text(title, margin, y);
    doc.setTextColor(0, 0, 0);
    autoTable(doc, {
      startY: y + 3,
      head: [["Campo", "Valore"]],
      body: statsRows(stats),
      margin: { left: margin, right: margin },
      styles: { fontSize: 9, cellPadding: 1.8 },
      headStyles: { fillColor: color },
    });
    y = lastTableY(doc, y) + 8;
  };

  writeStats("Area da bonificare", COLOR_AREA, input.area);
  writeStats("Conduttore", COLOR_COND, input.conduttore);
  writeStats("Cane", COLOR_CANE, input.cane);

  const sep = caneConduttoreSeparation(canePts, condPts);
  if (y > pageH - 50) {
    doc.addPage();
    y = 20;
  }
  doc.setFontSize(12);
  doc.setTextColor(...COLOR_SEP);
  doc.text(pdfText("Distanza cane - conduttore"), margin, y);
  doc.setTextColor(0, 0, 0);
  autoTable(doc, {
    startY: y + 3,
    head: [["Metrica", "Valore"]],
    body: separationRows(sep),
    margin: { left: margin, right: margin },
    styles: { fontSize: 9, cellPadding: 1.8 },
    headStyles: { fillColor: COLOR_SEP },
  });

  y = lastTableY(doc, y) + 12;
  const chartW = pageW - margin * 2;
  const figGap = 12;
  const lineChartH = 52;
  if (y + lineChartH + figGap > pageH - 16) {
    doc.addPage();
    y = 20;
  }
  drawLineChart(
    doc,
    margin,
    y,
    chartW,
    lineChartH,
    sep.hasTime
      ? "Fig.1 - Distanza cane-conduttore nel tempo (m)"
      : "Fig.1 - Distanza cane-conduttore stimata (m vs progresso %)",
    [
      {
        xs: sep.samples.map((s) => s.tSec),
        ys: sep.samples.map((s) => s.distM),
        color: COLOR_SEP,
        label: "Cane-conduttore",
      },
    ],
    "m",
  );
  y += lineChartH + figGap;

  let mapH = 58;
  const mapTitleH = 5;
  const mapLegendH = 6;
  if (y + mapTitleH + 70 + mapLegendH + figGap > pageH - 16) {
    doc.addPage();
    y = 20;
  }
  doc.setFontSize(9);
  doc.setTextColor(40, 60, 100);
  doc.text(pdfText("Fig.2 - Mappa topografica (OpenTopoMap)"), margin, y + 3.5);
  doc.setTextColor(0, 0, 0);
  y += mapTitleH;
  if (input.mapDataUrl) {
    try {
      const props = doc.getImageProperties(input.mapDataUrl);
      const ratio = props.width / Math.max(props.height, 1);
      let drawW = chartW;
      let drawH = drawW / ratio;
      const maxH = 105;
      if (drawH > maxH) {
        drawH = maxH;
        drawW = drawH * ratio;
      }
      mapH = drawH;
      const xImg = margin + (chartW - drawW) / 2;
      doc.addImage(input.mapDataUrl, "PNG", xImg, y, drawW, drawH);
    } catch {
      mapH = 58;
      drawTrackSchematic(doc, margin, y, chartW, mapH, areaPts, canePts, condPts);
    }
  } else {
    drawTrackSchematic(doc, margin, y, chartW, mapH, areaPts, canePts, condPts);
  }
  drawLegend(doc, margin, y + mapH + 3, [
    { color: COLOR_AREA, label: "Area da bonificare" },
    { color: COLOR_COND, label: "Conduttore" },
    { color: COLOR_CANE, label: "Cane" },
  ]);
  y += mapH + mapLegendH + figGap;

  if (y + lineChartH + figGap > pageH - 14) {
    doc.addPage();
    y = 20;
  }
  const caneSpAll = speedSeriesKmh(canePts);
  const condSpAll = speedSeriesKmh(condPts);
  // Taglia l’attesa iniziale/finale a GPS acceso ma fermo (la «mezza riga a zero»)
  const win = movementTimeWindow([caneSpAll, condSpAll]);
  const caneSp = win
    ? caneSpAll.filter((p) => p.tMs >= win.t0 && p.tMs <= win.t1)
    : caneSpAll;
  const condSp = win
    ? condSpAll.filter((p) => p.tMs >= win.t0 && p.tMs <= win.t1)
    : condSpAll;
  const t0 = win?.t0 ?? Math.min(
    ...[caneSp[0]?.tMs, condSp[0]?.tMs].filter((v): v is number => v != null),
    Number.POSITIVE_INFINITY,
  );
  const hasSpeed = caneSp.length >= 2 || condSp.length >= 2;
  drawLineChart(
    doc,
    margin,
    y,
    chartW,
    lineChartH,
    hasSpeed
      ? "Fig.3 - Velocita (km/h) — fase di lavoro"
      : "Fig.3 - Velocita (km/h) - assente senza orario sui punti",
    [
      {
        xs: caneSp.map((p) => (Number.isFinite(t0) ? (p.tMs - t0) / 1000 : 0)),
        ys: caneSp.map((p) => p.kmh),
        color: COLOR_CANE,
        label: "Cane",
      },
      {
        xs: condSp.map((p) => (Number.isFinite(t0) ? (p.tMs - t0) / 1000 : 0)),
        ys: condSp.map((p) => p.kmh),
        color: COLOR_COND,
        label: "Conduttore",
      },
    ],
    "km/h",
    { maxGapX: 120 },
  );
  y += lineChartH + figGap;

  doc.setFontSize(8);
  doc.setTextColor(100);
  doc.text(
    pdfText(
      "Verde=area, blu=conduttore, arancio=cane, viola=distanza. Velocita: solo finestra di moto (esclusa attesa a GPS acceso).",
    ),
    margin,
    Math.min(y, pageH - 10),
  );

  if (logoData && logoW > 0 && logoH > 0) {
    stampLogoOnAllPages(doc, logoData, logoW, logoH, pageW);
  }

  const base = `Analisi_TRK_${input.orgCode.trim().toUpperCase() || "TOC"}_${new Date()
    .toISOString()
    .slice(0, 10)}`;
  doc.save(`${base}.pdf`);

  try {
    doc.autoPrint();
    const url = doc.output("bloburl");
    window.open(url, "_blank", "noopener,noreferrer");
  } catch {
    // download gia fatto
  }
}
