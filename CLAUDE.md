# dynamic-parser

Generischer Parser, dessen Grammatik während des Parsens verändert werden kann.

## Zusammenarbeit

- Anweisungen auf Sinnhaftigkeit prüfen. Wenn etwas komisch wirkt oder vom üblichen Vorgehen abweicht: nachfragen, bevor umgesetzt wird.
- Vorschläge des Nutzers kritisch bewerten, nicht einfach zustimmen.

## Arbeitsablauf mit GitHub Issues

- Planung und Umsetzung laufen über GitHub Issues: Ein Feature wird besprochen, daraus werden gemeinsam ein oder mehrere Issues formuliert.
- Issues werden einzeln und nur auf ausdrückliche Anweisung umgesetzt.
- Jeder Commit nennt die Nummer des zugehörigen Issues.
- Commits ohne Issue gibt es nur in Sonderfällen, die der Nutzer ausdrücklich genehmigt. Genehmigte Sonderfälle:
  - Änderungen an dieser Datei (`CLAUDE.md`).
- Pull Requests nur auf ausdrückliche Anweisung anlegen.

## Technik

- Java 17 (Sprachlevel und Zielplattform: `maven.compiler.release` = 17).
- Build mit Maven 3.9.x.
- Der Parser ist ein Modul im Sinne des Java Module System (`module-info.java`). Nur die Pakete der Programmierschnittstelle werden exportiert; Implementierungspakete bleiben nicht exportiert.
