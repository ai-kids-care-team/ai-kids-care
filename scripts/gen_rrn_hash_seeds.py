#!/usr/bin/env python
"""
ADR-0024 Phase 1 seed regeneration script.

Generates rrn_hash values for all seed rows:
- children: child_id=1 uses REDACTED-RRN, child_id=2 uses REDACTED-RRN,
  others use child_id zero-padded to 7 digits as back7.
- guardians: guardian_id zero-padded to 7 digits as back7.
- teachers: teacher_id zero-padded to 7 digits as back7.

rrn_encrypted is set to NULL for all rows (Strategy B: full HMAC seeds).
"""
import hmac
import hashlib
import base64
import re
import sys

PEPPER = "test-pepper-not-secret-2026"
SEED_DIR = "C:/ai-kids-care/db/initdb"


def compute_hash(pepper, first6, back7):
    msg = (first6 + back7).encode("utf-8")
    key = pepper.encode("utf-8")
    h = hmac.new(key, msg, hashlib.sha256).digest()
    return base64.urlsafe_b64encode(h).rstrip(b"=").decode("ascii")


# Real RRN data for specific child IDs
REAL_RRN_CHILDREN = {
    1: ("200921", "4037926"),
    2: ("200319", "3045123"),
}


def extract_insert_rrn_first6(line):
    """Extract rrn_first6 value from an INSERT line."""
    # Match pattern: VALUES (id, kg_id, ..., 'XXXXXX', '$2a$...',
    # The rrn_first6 is before rrn_encrypted (BCrypt hash starting with $2a$)
    m = re.search(r"'(\d{4,6})', '\$2a\$", line)
    if m:
        return m.group(1)
    return None


def extract_pk(line):
    """Extract primary key (first numeric value in VALUES clause)."""
    m = re.search(r"VALUES \('?(\d+)'?", line)
    if m:
        return int(m.group(1))
    return None


def replace_rrn_encrypted_with_null_and_add_hash(line, rrn_hash):
    """
    Transform INSERT line:
    - Add rrn_hash column to column list (after rrn_encrypted)
    - Replace BCrypt hash value with NULL and add rrn_hash value
    """
    # Replace column list: add rrn_hash after rrn_encrypted
    line = re.sub(
        r"(rrn_encrypted)(,\s*birth_date)",
        r"\1, rrn_hash\2",
        line
    )
    # Replace BCrypt value: '200921', '$2a$...' -> '200921', NULL, '<hash>'
    # Pattern: 'rrn_first6', '$2a$XXXXX', 'birth_date_value'
    line = re.sub(
        r"'(\d{4,6})', '\$2a\$[^']+',(\s*'\d{4}-\d{2}-\d{2}')",
        lambda m: f"'{m.group(1)}', NULL, '{rrn_hash}',{m.group(2)}",
        line
    )
    return line


def replace_rrn_encrypted_teachers(line, rrn_hash):
    """
    For teachers: column order is different (rrn_encrypted before rrn_first6).
    Column list: staff_no, name, gender, emergency_contact_name, emergency_contact_phone, rrn_encrypted, rrn_first6, level
    """
    # Add rrn_hash after rrn_encrypted in column list
    line = re.sub(
        r"(rrn_encrypted)(,\s*rrn_first6)",
        r"\1, rrn_hash\2",
        line
    )
    # Replace BCrypt value before rrn_first6
    # Pattern: '$2a$XXXXX', 'rrn_first6', 'level_value'
    line = re.sub(
        r"'\$2a\$[^']+',(\s*'(\d{4,6})')",
        lambda m: f"NULL, '{rrn_hash}',{m.group(1)}",
        line
    )
    return line


def process_children():
    """Process 26_children_seed.sql."""
    path = f"{SEED_DIR}/26_children_seed.sql"
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    output = []
    for line in lines:
        stripped = line.strip()
        if not stripped.startswith("INSERT"):
            output.append(line)
            continue

        child_id = extract_pk(stripped)
        if child_id is None:
            output.append(line)
            continue

        if child_id in REAL_RRN_CHILDREN:
            first6, back7 = REAL_RRN_CHILDREN[child_id]
        else:
            first6_extracted = extract_insert_rrn_first6(stripped)
            first6 = first6_extracted if first6_extracted else "000000"
            back7 = str(child_id).zfill(7)

        rrn_hash = compute_hash(PEPPER, first6, back7)
        new_line = replace_rrn_encrypted_with_null_and_add_hash(stripped, rrn_hash)
        output.append(new_line + "\n")

    return output


def process_guardians():
    """Process 29_guardians_seed.sql."""
    path = f"{SEED_DIR}/29_guardians_seed.sql"
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    output = []
    for line in lines:
        stripped = line.strip()
        if not stripped.startswith("INSERT"):
            output.append(line)
            continue

        guardian_id = extract_pk(stripped)
        if guardian_id is None:
            output.append(line)
            continue

        first6 = extract_insert_rrn_first6(stripped)
        if first6 is None:
            # Fallback: find rrn_first6 differently
            m = re.search(r"'(\d{4,6})'", stripped)
            first6 = m.group(1) if m else "000000"
        back7 = str(guardian_id).zfill(7)
        rrn_hash = compute_hash(PEPPER, first6, back7)

        new_line = replace_rrn_encrypted_with_null_and_add_hash(stripped, rrn_hash)
        output.append(new_line + "\n")

    return output


def process_teachers():
    """Process 30_teachers_seed.sql."""
    path = f"{SEED_DIR}/30_teachers_seed.sql"
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    output = []
    for line in lines:
        stripped = line.strip()
        if not stripped.startswith("INSERT"):
            output.append(line)
            continue

        teacher_id = extract_pk(stripped)
        if teacher_id is None:
            output.append(line)
            continue

        # For teachers, rrn_first6 comes AFTER BCrypt hash
        # Format: ..., rrn_encrypted, rrn_first6, ...
        # BCrypt is before rrn_first6
        m = re.search(r"'\$2a\$[^']+',\s*'(\d{4,6})'", stripped)
        if m:
            first6 = m.group(1)
        else:
            first6 = "000000"
        back7 = str(teacher_id).zfill(7)
        rrn_hash = compute_hash(PEPPER, first6, back7)

        new_line = replace_rrn_encrypted_teachers(stripped, rrn_hash)
        output.append(new_line + "\n")

    return output


def verify_uniqueness(lines, table_name):
    """Verify that all rrn_hash values are unique."""
    hashes = []
    for line in lines:
        m = re.search(r"NULL, '([A-Za-z0-9_\-]+)'", line)
        if m:
            hashes.append(m.group(1))
    unique = set(hashes)
    print(f"{table_name}: {len(hashes)} rows, {len(unique)} unique hashes")
    if len(hashes) != len(unique):
        print(f"ERROR: Duplicate hashes found in {table_name}!")
        return False
    return True


if __name__ == "__main__":
    print("Generating children seed...")
    children_lines = process_children()
    if not verify_uniqueness(children_lines, "children"):
        sys.exit(1)

    print("Generating guardians seed...")
    guardians_lines = process_guardians()
    if not verify_uniqueness(guardians_lines, "guardians"):
        sys.exit(1)

    print("Generating teachers seed...")
    teachers_lines = process_teachers()
    if not verify_uniqueness(teachers_lines, "teachers"):
        sys.exit(1)

    # Write output files
    with open(f"{SEED_DIR}/26_children_seed.sql", "w", encoding="utf-8", newline="\n") as f:
        f.writelines(children_lines)
    print(f"Written {len(children_lines)} lines to 26_children_seed.sql")

    with open(f"{SEED_DIR}/29_guardians_seed.sql", "w", encoding="utf-8", newline="\n") as f:
        f.writelines(guardians_lines)
    print(f"Written {len(guardians_lines)} lines to 29_guardians_seed.sql")

    with open(f"{SEED_DIR}/30_teachers_seed.sql", "w", encoding="utf-8", newline="\n") as f:
        f.writelines(teachers_lines)
    print(f"Written {len(teachers_lines)} lines to 30_teachers_seed.sql")

    print("Done.")
