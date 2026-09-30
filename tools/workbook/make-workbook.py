"""The whole catalogue as one .xlsx, for rewriting it in a spreadsheet (CONTENT_GUIDE.md section 2).

    python tools/workbook/make-workbook.py

Four sheets: the cards, the game prompts, a read-me, and the vocabulary an author must draw from.
Every column the importer needs is there, in the order a person wants to read them rather than the
order the files store them — the importer reads by header name, so that costs nothing.

Written by hand as a zip of XML because it needs no dependency at all: the Node tools have no zip
library, and adding one to ship an authoring convenience was the worse trade. Read it back with
read-workbook.py, which turns it into the two TSV files `npm --prefix tools run import` takes.
"""
import io
import json
import os
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT_DIR = os.path.join(ROOT, "content", "authoring")
OUT = os.path.join(OUT_DIR, "afterhours-content.xlsx")

# Column order for people, not for the importer: it reads by header name, so this is free.
ITEM_COLUMNS = [
    "id", "title", "subtitle", "body", "intensity", "pack", "category", "tags", "moods",
    "interactionType", "durationMin", "modes", "chapterKinds", "excludedByBoundaries",
    "boostedByPreferences", "requiredMutualPreferences", "status", "requiresMedia",
    "noveltyWeight", "repeatCooldownDays", "version", "locale", "createdAt", "updatedAt",
]
PROMPT_COLUMNS = [
    "id", "text", "options", "kind", "intensity", "game", "category", "tags", "moods",
    "seconds", "modes", "excludedByBoundaries", "boostedByPreferences",
    "requiredMutualPreferences", "status", "noveltyWeight", "repeatCooldownDays",
    "version", "locale", "createdAt", "updatedAt",
]
# Which columns are wide, and which wrap: body and text are prose, the rest are short.
WIDTHS = {
    "id": 34, "title": 26, "subtitle": 28, "body": 80, "text": 80, "options": 44,
    "tags": 24, "moods": 20, "excludedByBoundaries": 30, "boostedByPreferences": 28,
    "requiredMutualPreferences": 26, "chapterKinds": 22, "interactionType": 16,
    "createdAt": 22, "updatedAt": 22, "category": 16, "pack": 18, "game": 18,
}
WRAPPED = {"body", "text", "subtitle", "options"}


def esc(text):
    out = []
    for ch in str(text):
        if ch == "&":
            out.append("&amp;")
        elif ch == "<":
            out.append("&lt;")
        elif ch == ">":
            out.append("&gt;")
        elif ch == '"':
            out.append("&quot;")
        elif ord(ch) < 32 and ch not in "\t\n":
            continue
        else:
            out.append(ch)
    return "".join(out)


def col_name(index):
    name = ""
    index += 1
    while index:
        index, rest = divmod(index - 1, 26)
        name = chr(65 + rest) + name
    return name


def cell(row, index, value, style):
    ref = f"{col_name(index)}{row}"
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return f'<c r="{ref}" s="{style}"><v>{value}</v></c>'
    text = "" if value is None else str(value)
    if text == "":
        return f'<c r="{ref}" s="{style}"/>'
    return f'<c r="{ref}" s="{style}" t="inlineStr"><is><t xml:space="preserve">{esc(text)}</t></is></c>'


def sheet_xml(columns, rows, validations=()):
    cols = "".join(
        f'<col min="{i + 1}" max="{i + 1}" width="{WIDTHS.get(name, 14)}" customWidth="1"/>'
        for i, name in enumerate(columns)
    )
    body = [
        "<row r=\"1\">"
        + "".join(cell(1, i, name, 1) for i, name in enumerate(columns))
        + "</row>"
    ]
    for number, record in enumerate(rows, start=2):
        cells = []
        for i, name in enumerate(columns):
            style = 2 if name in WRAPPED else 0
            cells.append(cell(number, i, record.get(name, ""), style))
        body.append(f'<row r="{number}">' + "".join(cells) + "</row>")
    rule = "".join(validations)
    validation = f'<dataValidations count="{len(validations)}">{rule}</dataValidations>' if rule else ""
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        '<sheetViews><sheetView workbookViewId="0">'
        '<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>'
        "</sheetView></sheetViews>"
        f"<cols>{cols}</cols>"
        f'<sheetData>{"".join(body)}</sheetData>'
        f"{validation}"
        "</worksheet>"
    )


def listed(values, first, last):
    """A dropdown of [values] down one column."""
    joined = ",".join(values)
    return (
        f'<dataValidation type="list" allowBlank="0" showInputMessage="1" showErrorMessage="1" '
        f'sqref="{first}:{last}"><formula1>"{esc(joined)}"</formula1></dataValidation>'
    )


def flatten(value):
    return " | ".join(value) if isinstance(value, list) and any("," in str(v) for v in value) \
        else ", ".join(value) if isinstance(value, list) else ("" if value is None else value)


# --- what goes in it -------------------------------------------------------------------------------
content = os.path.join(ROOT, "content")
packs = json.load(io.open(os.path.join(content, "packs.json"), encoding="utf-8"))["packs"]
games = json.load(io.open(os.path.join(content, "games.json"), encoding="utf-8"))["games"]
vocab = json.load(io.open(os.path.join(content, "vocabulary.json"), encoding="utf-8"))
taxonomy = json.load(io.open(os.path.join(content, "taxonomy.json"), encoding="utf-8"))

items, prompts = [], []
for pack in packs:
    for raw in json.load(io.open(os.path.join(content, "packs", pack["file"]), encoding="utf-8"))["items"]:
        items.append({key: flatten(raw.get(key)) for key in ITEM_COLUMNS})
for game in games:
    for raw in json.load(io.open(os.path.join(content, "prompts", game["file"]), encoding="utf-8"))["items"]:
        prompts.append({key: flatten(raw.get(key)) for key in PROMPT_COLUMNS})

themes = [t["id"] for category in taxonomy["categories"] for t in category["themes"]]
prefs = [i["id"] for category in taxonomy["categories"] for t in category["themes"] for i in t["items"]]
categories = [c["id"] for c in taxonomy["categories"]]

READ_ME = [
    ("Afterhours content", ""),
    ("", ""),
    ("How to use this file", "Edit the Cards and Prompts sheets. Then send the file back and it goes"),
    ("", "straight into the app: every row is matched by its id, so a row you change"),
    ("", "replaces that card and a row you add is a new one."),
    ("", ""),
    ("Do not rename the header row", "The columns are read by name. Reordering them is fine; renaming is not."),
    ("To remove a card", "Set its status to archived. Deleting the row leaves the card in the app."),
    ("To add a card", "Copy a neighbouring row, give it an id nobody else has, starting with its"),
    ("", "pack's prefix: afterdark_, flirt_, tease_, power_, roleplay_, sensory_,"),
    ("", "surprise_, deep_, challenge_, distance_, fantasy_, confess_."),
    ("", ""),
    ("The five levels", "intensity is 1 to 5, and it is what a couple picks on the dial."),
    ("1  Mild", "Warm, no risk. Nothing anyone would hesitate over."),
    ("2  Flirty", "Playful and charged. Clothes stay on."),
    ("3  Spicy", "Clear intent. The evening has turned."),
    ("4  Hot", "Explicit in intent, and needs both of them to have said yes."),
    ("5  Extreme", "The furthest this goes. Nothing above it — so a level 5 card should"),
    ("", "read as the top of the whole range, not the top of its own pack."),
    ("", ""),
    ("What the checker refuses", "A body that repeats another one too closely."),
    ("", "A card naming something sensitive without the theme that covers it in"),
    ("", "excludedByBoundaries — that theme is how a couple says no to it."),
    ("", "Anything on the prohibited list: under 18, non-consent, intoxication,"),
    ("", "incest or bestiality, acts needing safety knowledge the app cannot give,"),
    ("", "anyone outside the couple, bystanders, and explicit anatomy."),
    ("", "A level of a pack falling under its minimum number of cards."),
    ("", ""),
    ("Lists in a cell", "Comma-separated: tags, moods, modes, excludedByBoundaries."),
    ("", "Where one entry has a comma of its own, the cell uses pipes instead."),
    ("", "Use only the values on the Vocabulary sheet. Anything else is refused."),
]

VOCAB_ROWS = []
for name, values in (
    ("moods", vocab["moods"]),
    ("tags", vocab["tags"]),
    ("interactionType", vocab["interactionTypes"]),
    ("chapterKinds", vocab["chapterKinds"]),
    ("status", vocab["statuses"]),
    ("modes", ["TOGETHER", "APART"]),
    ("category", categories),
    ("pack", [p["id"] for p in packs]),
    ("game", [g["id"] for g in games]),
    ("excludedByBoundaries (themes)", themes),
    ("boostedByPreferences / requiredMutualPreferences", prefs),
):
    VOCAB_ROWS.append({"field": name, "allowed values": ", ".join(values)})

# --- the workbook -----------------------------------------------------------------------------------
STYLES = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
    '<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font>'
    '<font><b/><sz val="11"/><name val="Calibri"/></font></fonts>'
    '<fills count="3"><fill><patternFill patternType="none"/></fill>'
    '<fill><patternFill patternType="gray125"/></fill>'
    '<fill><patternFill patternType="solid"><fgColor rgb="FFEDE7F6"/>'
    '<bgColor indexed="64"/></patternFill></fill></fills>'
    '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>'
    '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
    '<cellXfs count="3">'
    '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1">'
    '<alignment vertical="top"/></xf>'
    '<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>'
    '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1">'
    '<alignment wrapText="1" vertical="top"/></xf>'
    "</cellXfs></styleSheet>"
)

SHEETS = [
    ("Read me", sheet_xml(["", " "], [{"": a, " ": b} for a, b in READ_ME])),
    ("Cards", sheet_xml(
        ITEM_COLUMNS,
        items,
        [
            listed(["1", "2", "3", "4", "5"], "E2", f"E{len(items) + 200}"),
            listed(vocab["statuses"], "Q2", f"Q{len(items) + 200}"),
        ],
    )),
    ("Prompts", sheet_xml(
        PROMPT_COLUMNS,
        prompts,
        [
            listed(["1", "2", "3", "4", "5"], "E2", f"E{len(prompts) + 200}"),
            listed(vocab["statuses"], "O2", f"O{len(prompts) + 200}"),
        ],
    )),
    ("Vocabulary", sheet_xml(["field", "allowed values"], VOCAB_ROWS)),
]

sheet_tags = "".join(
    f'<sheet name="{esc(name)}" sheetId="{i + 1}" r:id="rId{i + 1}"/>'
    for i, (name, _) in enumerate(SHEETS)
)
workbook = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
    'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
    f"<sheets>{sheet_tags}</sheets></workbook>"
)
rels = "".join(
    f'<Relationship Id="rId{i + 1}" '
    'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" '
    f'Target="worksheets/sheet{i + 1}.xml"/>'
    for i in range(len(SHEETS))
)
workbook_rels = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
    f'{rels}<Relationship Id="rIdStyles" '
    'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" '
    'Target="styles.xml"/></Relationships>'
)
overrides = "".join(
    f'<Override PartName="/xl/worksheets/sheet{i + 1}.xml" '
    'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
    for i in range(len(SHEETS))
)
content_types = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
    '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
    '<Default Extension="xml" ContentType="application/xml"/>'
    '<Override PartName="/xl/workbook.xml" '
    'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
    '<Override PartName="/xl/styles.xml" '
    'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
    f"{overrides}</Types>"
)
root_rels = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
    '<Relationship Id="rId1" '
    'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
    'Target="xl/workbook.xml"/></Relationships>'
)

os.makedirs(OUT_DIR, exist_ok=True)
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as book:
    book.writestr("[Content_Types].xml", content_types)
    book.writestr("_rels/.rels", root_rels)
    book.writestr("xl/workbook.xml", workbook)
    book.writestr("xl/_rels/workbook.xml.rels", workbook_rels)
    book.writestr("xl/styles.xml", STYLES)
    for i, (_, xml) in enumerate(SHEETS):
        book.writestr(f"xl/worksheets/sheet{i + 1}.xml", xml)

print(f"wrote {OUT}")
print(f"  Cards: {len(items)} rows, Prompts: {len(prompts)} rows, Vocabulary: {len(VOCAB_ROWS)} fields")
