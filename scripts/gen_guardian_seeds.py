#!/usr/bin/env python
"""Fix guardians seed - add rrn_hash and set rrn_encrypted to NULL."""
import hmac
import hashlib
import base64
import re

PEPPER = "test-pepper-not-secret-2026"
SEED_FILE = "C:/ai-kids-care/db/initdb/29_guardians_seed.sql"


def compute_hash(pepper, first6, back7):
    msg = (first6 + back7).encode("utf-8")
    key = pepper.encode("utf-8")
    h = hmac.new(key, msg, hashlib.sha256).digest()
    return base64.urlsafe_b64encode(h).rstrip(b"=").decode("ascii")


with open(SEED_FILE, "r", encoding="utf-8") as f:
    lines = f.readlines()

output = []
hashes_seen = set()
errors = 0

for line in lines:
    stripped = line.strip()
    if not stripped.startswith("INSERT"):
        output.append(line)
        continue

    # Extract primary key (guardian_id is quoted: '1')
    pk_match = re.search(r"VALUES \('?(\d+)'?", stripped)
    if not pk_match:
        print(f"ERROR: no pk found: {stripped[:60]}")
        output.append(line)
        errors += 1
        continue
    guardian_id = int(pk_match.group(1))

    # Extract rrn_first6: it comes AFTER BCrypt hash
    # Column order: rrn_encrypted, rrn_first6
    # Pattern: '$2a$...', 'rrn_first6', 'MALE/FEMALE'
    # Note: rrn_first6 can be 3-6 digits (seed data has varying lengths)
    first6_match = re.search(r"'\$2a\$[^']+',\s*'(\d{1,8})'", stripped)
    if not first6_match:
        print(f"ERROR: no first6 found for guardian {guardian_id}: {stripped[:80]}")
        output.append(line)
        errors += 1
        continue
    first6 = first6_match.group(1)

    back7 = str(guardian_id).zfill(7)
    rrn_hash = compute_hash(PEPPER, first6, back7)

    if rrn_hash in hashes_seen:
        print(f"COLLISION: guardian {guardian_id} hash {rrn_hash} already seen!")
        errors += 1
    hashes_seen.add(rrn_hash)

    # Step 1: Add rrn_hash column after rrn_encrypted in column list
    # Column list has: rrn_encrypted, rrn_first6
    # Add rrn_hash between them: rrn_encrypted, rrn_hash, rrn_first6
    new_line = re.sub(
        r"(rrn_encrypted)(,\s*rrn_first6)",
        r"\1, rrn_hash\2",
        stripped
    )

    # Step 2: Replace BCrypt value with NULL and add rrn_hash value
    # Pattern: '$2a$XXXXX', 'rrn_first6' -> NULL, 'rrn_hash', 'rrn_first6'
    new_line = re.sub(
        r"'\$2a\$[^']+',(\s*'(\d{1,8})')",
        lambda m: f"NULL, '{rrn_hash}',{m.group(1)}",
        new_line
    )

    output.append(new_line + "\n")

print(f"Processed {len(output)} lines, {len(hashes_seen)} unique hashes, {errors} errors")

if errors == 0:
    with open(SEED_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.writelines(output)
    print("Written successfully.")
else:
    print("Errors found, not writing.")
