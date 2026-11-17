#!/usr/bin/env python3
"""Static guard for user-data safety (docs/v68). Fails when code could destroy or silently reset stored workout data:
 - destructive Room calls (fallbackToDestructiveMigration, deleteDatabase, clearAllTables, dropAllTables)
 - a Room DELETE other than the two reviewed ones (session_drafts by draftId, space_equipment by spaceId)
 - DROP TABLE / DROP COLUMN / table renames inside migrations
 - a database version without an exported schema file or without an unbroken chain of migrations 1->version
This does not prove that data survives an upgrade (the migration tests do); it stops the obvious ways of losing it. Comments are ignored."""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parent.parent
main = root / "app/src/main/java"
errors = []

def code_of(path):
    s = path.read_text()
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)       # block comments
    return re.sub(r"(?m)//[^\n]*", "", s)            # line comments

sources = {p: code_of(p) for p in main.rglob("*.kt")}
for p, s in sources.items():
    rel = p.relative_to(root)
    for token in ("fallbackToDestructiveMigration", "deleteDatabase", "clearAllTables", "dropAllTables"):
        if token in s: errors.append(f"{rel}: destructive call '{token}'")
    for m in re.finditer(r"DELETE\s+FROM\s+(\w+)([^\"]*)", s, flags=re.I):
        table, rest = m.group(1), m.group(2)
        allowed = (table == "session_drafts" and "draftId" in rest) or (table == "space_equipment" and "spaceId" in rest)
        if not allowed: errors.append(f"{rel}: unreviewed DELETE FROM {table}{rest[:40]}")
    if re.search(r"@Delete\b", s): errors.append(f"{rel}: Room @Delete is not allowed on user data")

mig = next((s for p, s in sources.items() if p.name == "RoutiveMigrations.kt"), "")
for m in re.finditer(r"DROP\s+(TABLE|COLUMN)|RENAME\s+(TO|COLUMN)", mig, flags=re.I):
    errors.append(f"RoutiveMigrations.kt: '{m.group(0)}' in a migration")

db = next((s for p, s in sources.items() if p.name == "WorkoutDatabase.kt"), "")
version = int(re.search(r"version\s*=\s*(\d+)", db).group(1))
schemas = sorted(int(p.stem) for p in (root / "app/schemas").rglob("*.json") if p.stem.isdigit())
if schemas != list(range(1, version + 1)):
    errors.append(f"exported schemas {schemas} do not cover 1..{version}")
pairs = {(int(a), int(b)) for a, b in re.findall(r"Migration\((\d+),\s*(\d+)\)", mig)}
for v in range(1, version):
    if (v, v + 1) not in pairs: errors.append(f"no Migration({v}, {v + 1})")

if errors:
    print("FAIL: data-safety guard"); [print(" -", e) for e in errors]; sys.exit(1)
print(f"PASS: no destructive data calls; Room v{version} has schemas 1..{version} and migrations 1->{version}")
