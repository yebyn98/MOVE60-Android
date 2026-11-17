#!/usr/bin/env python3
"""Release gate for docs/v66 P0 item 2 (NOT part of the normal PR checks): fails while the operator-supplied privacy policy URL is
missing. Passing only means the value LOOKS valid (https, real host); the operator must still confirm the page is live and matches
the app's real data handling, and that the same URL is set in Google Play Console."""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parent.parent
xml = (root / "app/src/main/res/values/legal.xml").read_text()
m = re.search(r'<string name="privacy_policy_url"[^>]*>([^<]*)</string>', xml)
url = (m.group(1).strip() if m else "")
blocked = ("example.", "localhost", ".invalid", ".test", "yourdomain", "domain.com")
parts = url.split("/")
host = parts[2] if url.startswith("https://") and len(parts) > 2 else ""
if not host or "." not in host or any(b in host for b in blocked):
    print("BLOCKED: privacy_policy_url is empty, not https, or a placeholder (docs/v67). Do not release."); sys.exit(1)
print("OK (format only):", url, "- confirm it is live, matches real data handling and is set in Play Console")
