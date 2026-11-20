import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TOC SAR - GPS & Cartografia",
  description: "TOC SAR - GPS & Cartografia — sala operativa, operatori, mappa, LOG e analisi tracce",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="it">
      <body>{children}</body>
    </html>
  );
}
