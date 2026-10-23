/** Elenco operatori: le 5 colonne in tabella, logo dell'ente di login in alto a destra. */

const LOGIN_ENTE_LOGO: Record<string, string> = {
  NVANSMI: "/map/squad/logo_ansmi.png",
};

export type OperatorPdfRow = {
  code: string;
  name: string;
  password: string;
  ente: string;
  login: string;
  color: string;
};

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

async function loadLoginEnteLogo(orgCode: string): Promise<string | null> {
  const code = orgCode.trim().toUpperCase();
  const urls = [LOGIN_ENTE_LOGO[code], `/org/${code}.png`].filter(
    (url): url is string => Boolean(url),
  );
  for (const url of urls) {
    try {
      const res = await fetch(url);
      if (!res.ok) {
        continue;
      }
      return await blobToDataUrl(await res.blob());
    } catch {
      continue;
    }
  }
  return null;
}

export async function downloadOperatorsPdf(input: {
  orgCode: string;
  orgName: string;
  rows: OperatorPdfRow[];
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
  const margin = 12;
  const logoData = await loadLoginEnteLogo(input.orgCode);
  let logoW = 0;
  let logoH = 0;
  if (logoData) {
    try {
      const size = await imageSize(logoData);
      const maxH = 16;
      const maxW = 28;
      const scale = Math.min(maxW / size.w, maxH / size.h);
      logoW = size.w * scale;
      logoH = size.h * scale;
      doc.addImage(logoData, "PNG", pageW - margin - logoW, 10, logoW, logoH);
    } catch {
      logoW = 0;
      logoH = 0;
    }
  }

  const titleRight = pageW - margin - (logoW > 0 ? logoW + 4 : 0);
  doc.setFontSize(16);
  doc.setTextColor(20, 41, 93);
  doc.text("Operatori", margin, 16);
  doc.setFontSize(10);
  doc.setTextColor(40, 40, 40);
  const when = new Date().toLocaleString("it-IT", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
  const subtitle = pdfText(
    `Codice TOC ${input.orgCode}${input.orgName && input.orgName !== input.orgCode ? ` - ${input.orgName}` : ""}  ·  ${when}`,
  );
  doc.text(doc.splitTextToSize(subtitle, titleRight - margin), margin, 22);

  const body =
    input.rows.length === 0
      ? [["Nessun operatore", "", "", "", ""]]
      : input.rows.map((row) => [
          pdfText(row.code),
          pdfText(row.name),
          pdfText(row.password),
          pdfText(row.ente),
          pdfText(row.login),
        ]);

  autoTable(doc, {
    startY: Math.max(32, 12 + logoH + 6),
    head: [["Codice", "Cognome e nome", "Password", "Ente", "Login"]],
    body,
    theme: "grid",
    styles: { fontSize: 9, cellPadding: 1.8, overflow: "linebreak", textColor: 20 },
    headStyles: { fillColor: [20, 41, 93], textColor: 255, fontStyle: "bold" },
    columnStyles: {
      0: { cellWidth: 32, cellPadding: { top: 1.8, right: 1.6, bottom: 1.8, left: 7 } },
      1: { cellWidth: 52 },
      2: { cellWidth: 32 },
      3: { cellWidth: 42 },
      4: { cellWidth: 28 },
    },
    margin: { left: margin, right: margin },
    didDrawCell: (data) => {
      if (data.section !== "body" || data.column.index !== 0 || input.rows.length === 0) {
        return;
      }
      const color = input.rows[data.row.index]?.color;
      if (!color || !/^#[0-9A-Fa-f]{6}$/.test(color)) {
        return;
      }
      const r = parseInt(color.slice(1, 3), 16);
      const g = parseInt(color.slice(3, 5), 16);
      const b = parseInt(color.slice(5, 7), 16);
      data.doc.setFillColor(r, g, b);
      data.doc.circle(data.cell.x + 3.4, data.cell.y + data.cell.height / 2, 1.7, "F");
    },
  });

  doc.save(`Operatori_${input.orgCode.trim().toUpperCase() || "TOC"}.pdf`);
}
