import os
import re
from collections import defaultdict

src_dir = r'Assets\Scripts'
results = defaultdict(list)
file_list = []

# Walk all .cs files
for root, dirs, files in os.walk(src_dir):
    for fname in files:
        if fname.endswith('.cs'):
            fpath = os.path.join(root, fname)
            file_list.append(fpath)

print(f'Total .cs files: {len(file_list)}')

# Search for icon references
for fpath in file_list:
    try:
        with open(fpath, 'r', encoding='utf-8') as f:
            lines = f.readlines()
    except:
        try:
            with open(fpath, 'r', encoding='latin-1') as f:
                lines = f.readlines()
        except:
            continue
    
    for i, line in enumerate(lines):
        if re.search(r'icon', line, re.IGNORECASE):
            rel = os.path.relpath(fpath, src_dir)
            results[rel].append((i+1, line.rstrip()))

# Also search for hardcoded numeric patterns that look like icon IDs
# (numbers in range 1000-30000 used in arrays or assignments)
hardcoded = defaultdict(list)
for fpath in file_list:
    try:
        with open(fpath, 'r', encoding='utf-8') as f:
            lines = f.readlines()
    except:
        try:
            with open(fpath, 'r', encoding='latin-1') as f:
                lines = f.readlines()
        except:
            continue
    
    for i, line in enumerate(lines):
        s = line.strip()
        # Skip comments
        if s.startswith('//') or s.startswith('/*') or s.startswith('*'):
            continue
        # Look for patterns like new int[]{1202, 1203} or direct icon number assignments
        if re.search(r'\b(icon|sprite|image)\w*\s*[=\[]\s*\d{3,5}', line, re.IGNORECASE):
            rel = os.path.relpath(fpath, src_dir)
            if (i+1, line.rstrip()) not in hardcoded[rel]:
                hardcoded[rel].append((i+1, line.rstrip()))

# Write results
out = []
out.append(f'TONG SO FILE C#: {len(file_list)}')
out.append(f'SO FILE CO ICON REFERENCE: {len(results)}')
out.append('')

# Summary first
out.append('=' * 60)
out.append('SUMMARY: FILES WITH ICON REFERENCES')
out.append('=' * 60)
for fp in sorted(results.keys()):
    out.append(f'  {fp}: {len(results[fp])} matches')
out.append('')

# Detailed results
out.append('=' * 60)
out.append('DETAILED RESULTS')
out.append('=' * 60)
out.append('')

for filepath in sorted(results.keys()):
    matches = results[filepath]
    out.append(f'=== {filepath} ({len(matches)} matches) ===')
    for lineno, content in matches:
        out.append(f'  L{lineno}: {content.strip()}')
    out.append('')

# Hardcoded icon IDs
if hardcoded:
    out.append('=' * 60)
    out.append('POTENTIAL HARDCODED ICON IDS')
    out.append('=' * 60)
    out.append('')
    for filepath in sorted(hardcoded.keys()):
        matches = hardcoded[filepath]
        out.append(f'=== {filepath} ({len(matches)} matches) ===')
        for lineno, content in matches:
            out.append(f'  L{lineno}: {content.strip()}')
        out.append('')

with open('client_scan_result.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(out))

print(f'Done! Saved to client_scan_result.txt')
print(f'Files with icon references: {len(results)}')
