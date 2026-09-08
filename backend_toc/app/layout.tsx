import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TOC SAR",
  description: "Sala operativa TOC SAR — operatori, notifiche, LOG, Config e anagrafica",
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
