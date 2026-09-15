"""La guida iOS è unificata in TOC_SAR_Guida_utente.pdf.

Esegui questo script o docs/build_guida_utente.py: stesso PDF, con le eccezioni iPhone nel testo.
"""

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_guida_utente import build

if __name__ == "__main__":
    path = build()
    print(path)
    print("(Guida unica Android + iPhone: eccezioni iPhone nel PDF.)")
