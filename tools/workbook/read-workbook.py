"""The filled workbook, back out as the two TSV files the importer takes.

    python tools/workbook/read-workbook.py <book.xlsx> <out-dir>
    npm --prefix tools run import -- <out-dir>/cards.tsv --write
    npm --prefix tools run import -- <out-dir>/prompts.tsv --write

Writes cards.tsv and prompts.tsv. Handles both inline strings (what the generator writes) and
the shared string table (what Excel writes when it saves).
"""
import os
import sys
import zipfile
import xml.etree.ElementTree as ET

NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"


def column_of(ref):
    """'BC12' -> 54: the zero-based column index in a cell reference."""
    letters = "".join(ch for ch in ref if ch.isalpha())
    index = 0
    for ch in letters:
        index = index * 26 + (ord(ch.upper()) - 64)
    return index - 1


def shared_strings(book):
    if "xl/sharedStrings.xml" not in book.namelist():
        return []
    root = ET.fromstring(book.read("xl/sharedStrings.xml"))
    out = []
    for si in root.findall(f"{NS}si"):
        out.append("".join(node.text or "" for node in si.iter(f"{NS}t")))
    return out


def sheet_rows(book, path, strings):
    root = ET.fromstring(book.read(path))
    data = root.find(f"{NS}sheetData")
    rows = []
    for row in data.findall(f"{NS}row"):
        cells = {}
        for c in row.findall(f"{NS}c"):
            at = column_of(c.get("r", "A1"))
            kind = c.get("t")
            if kind == "inlineStr":
                node = c.find(f"{NS}is")
                text = "".join(t.text or "" for t in node.iter(f"{NS}t")) if node is not None else ""
            elif kind == "s":
                v = c.find(f"{NS}v")
                text = strings[int(v.text)] if v is not None and v.text else ""
            elif kind == "str":
                v = c.find(f"{NS}f/..") or c
                node = v.find(f"{NS}v")
                text = node.text or "" if node is not None else ""
            else:
                v = c.find(f"{NS}v")
                text = v.text or "" if v is not None else ""
            cells[at] = text
        width = max(cells) + 1 if cells else 0
        rows.append([cells.get(i, "") for i in range(width)])
    return rows


def named_sheets(book):
    """Sheet name -> part path, through the workbook's relationships."""
    rels = ET.fromstring(book.read("xl/_rels/workbook.xml.rels"))
    parts = set(book.namelist())
    target = {}
    for rel in rels:
        # A relationship target is relative to xl/, but a workbook saved elsewhere may already
        # carry the prefix ("xl/worksheets/sheet1.xml") or lead with a slash. Take whichever of
        # the readings names a part that is actually in the file.
        raw = rel.get("Target", "").lstrip("/")
        for candidate in (f"xl/{raw}", raw, raw.removeprefix("xl/")):
            if candidate in parts:
                target[rel.get("Id")] = candidate
                break
    root = ET.fromstring(book.read("xl/workbook.xml"))
    r = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
    out = {}
    for sheet in root.find(f"{NS}sheets"):
        out[sheet.get("name")] = target[sheet.get(r)]
    return out


def to_tsv(rows):
    lines = []
    for row in rows:
        cells = []
        for value in row:
            flat = " ".join(str(value).split()) if "\n" in str(value) or "\t" in str(value) else str(value)
            cells.append(f'"{flat}"' if '"' in flat else flat)
        lines.append("\t".join(cells))
    return "﻿" + "\n".join(lines) + "\n"


def main():
    if len(sys.argv) < 3:
        raise SystemExit("usage: read_xlsx.py <book.xlsx> <out-dir>")
    path, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    with zipfile.ZipFile(path) as book:
        strings = shared_strings(book)
        sheets = named_sheets(book)
        for name, file in (("Cards", "cards.tsv"), ("Prompts", "prompts.tsv")):
            if name not in sheets:
                print(f"no {name} sheet, skipped")
                continue
            rows = [r for r in sheet_rows(book, sheets[name], strings) if any(str(c).strip() for c in r)]
            open(os.path.join(out_dir, file), "w", encoding="utf-8", newline="\n").write(to_tsv(rows))
            print(f"{file}: {len(rows) - 1} rows")


if __name__ == "__main__":
    main()
