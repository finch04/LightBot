import os

replacements = [
    ("background: #fff;", "background: var(--color-canvas);"),
    ("background: #ffffff;", "background: var(--color-canvas);"),
    ("background: #f4f4f5;", "background: var(--color-canvas-soft-2);"),
    ("background: #f5f5f5;", "background: var(--color-canvas-soft-2);"),
    ("background: #f0f0f0;", "background: var(--color-canvas-soft-3);"),
    ("background: #f9fafb;", "background: var(--color-canvas-soft);"),
    ("background: #f8fafc;", "background: var(--color-canvas-soft);"),
    ("background: #fafafa;", "background: var(--color-canvas-soft);"),
    ("background: #f9f9f9;", "background: var(--color-canvas-soft-2);"),
    ("background: #fef2f2;", "background: var(--color-error-bg);"),
    ("background: #fff5f5;", "background: var(--color-error-bg);"),
    ("background: #fef3c7;", "background: var(--color-warn-bg-deep);"),
    ("background: #fffbeb;", "background: var(--color-warn-bg);"),
    ("background: #fefce8;", "background: var(--color-warn-bg);"),
    ("background: #fff7ed;", "background: var(--color-warn-bg);"),
    ("background: #f0fdf4;", "background: var(--color-success-bg);"),
    ("background: #dcfce7;", "background: var(--color-success-bg);"),
    ("background: #e8f4ff;", "background: var(--color-info-bg);"),
    ("background: #e6f4ff;", "background: var(--color-info-bg);"),
    ("background: #eff6ff;", "background: var(--color-info-bg);"),
    ("background: #dbeafe;", "background: var(--color-info-bg);"),
    ("background: #f5f3ff;", "background: var(--color-purple-bg);"),
    ("background: #faf8ff;", "background: var(--color-purple-bg);"),
    ("background: #ede9fe;", "background: var(--color-purple-bg);"),
    ("border: 1px solid #e4e4e7", "border: 1px solid var(--color-hairline)"),
    ("border: 1px solid #d4d4d8", "border: 1px solid var(--color-hairline)"),
    ("border: 1px solid #e8e8e8", "border: 1px solid var(--color-hairline)"),
    ("border: 1px solid #e5e7eb", "border: 1px solid var(--color-hairline)"),
    ("border: 1px solid #e2e8f0", "border: 1px solid var(--color-border-slate)"),
    ("border-color: #e4e4e7", "border-color: var(--color-hairline)"),
    ("border-color: #d4d4d8", "border-color: var(--color-hairline)"),
    ("border-top: 1px solid #f0f0f0", "border-top: 1px solid var(--color-hairline)"),
    ("border-top: 1px solid #ebebeb", "border-top: 1px solid var(--color-hairline)"),
    ("border-top: 1px solid #e4e4e7", "border-top: 1px solid var(--color-hairline)"),
    ("border-top: 1px solid #e5e7eb", "border-top: 1px solid var(--color-hairline)"),
    ("border-top: 1px solid #e2e8f0", "border-top: 1px solid var(--color-border-slate)"),
    ("border-top: 1px solid #e8e8e8", "border-top: 1px solid var(--color-hairline)"),
    ("border-top: 1px solid #f1f5f9", "border-top: 1px solid var(--color-hairline)"),
    ("border-bottom: 1px solid #ebebeb", "border-bottom: 1px solid var(--color-hairline)"),
    ("border-bottom: 1px solid #e4e4e7", "border-bottom: 1px solid var(--color-hairline)"),
    ("border-bottom: 1px solid #e5e7eb", "border-bottom: 1px solid var(--color-hairline)"),
    ("border-bottom: 1px solid #e2e8f0", "border-bottom: 1px solid var(--color-border-slate)"),
    ("border-bottom: 1px solid #e8e8e8", "border-bottom: 1px solid var(--color-hairline)"),
    ("border-bottom: 1px solid #f0f0f0", "border-bottom: 1px solid var(--color-hairline)"),
    ("border-bottom: 1px solid #f1f5f9", "border-bottom: 1px solid var(--color-hairline)"),
    ("border-bottom: 1px solid #f4f4f5", "border-bottom: 1px solid var(--color-hairline)"),
    ("border-left: 1px solid #e8e8e8", "border-left: 1px solid var(--color-hairline)"),
    ("border-top: 1px dashed #ebebeb", "border-top: 1px dashed var(--color-hairline)"),
    ("border-top: 1px dashed #e4e4e7", "border-top: 1px dashed var(--color-hairline)"),
    ("color: #a1a1aa;", "color: var(--color-mute);"),
    ("color: #8c8c8c;", "color: var(--color-mute);"),
    ("color: #71717a;", "color: var(--color-mute);"),
    ("color: #94a3b8;", "color: var(--color-mute);"),
    ("color: #64748b;", "color: var(--color-mute);"),
    ("color: #52525b;", "color: var(--color-body);"),
    ("color: #595959;", "color: var(--color-body);"),
    ("color: #3f3f46;", "color: var(--color-ink);"),
    ("color: #262626;", "color: var(--color-ink);"),
    ("color: #334155;", "color: var(--color-text-dark);"),
    ("color: #1e293b;", "color: var(--color-text-code);"),
]

vue_files = []
for root, dirs, files in os.walk("src"):
    for f in files:
        if f.endswith(".vue"):
            vue_files.append(os.path.join(root, f))

total = 0
changed = []
for fpath in vue_files:
    with open(fpath, "r", encoding="utf-8") as f:
        content = f.read()
    orig = content
    cnt = 0
    for old, new in replacements:
        c = content.count(old)
        if c > 0:
            content = content.replace(old, new)
            cnt += c
    if content != orig:
        with open(fpath, "w", encoding="utf-8") as f:
            f.write(content)
        total += cnt
        changed.append((fpath, cnt))

changed.sort(key=lambda x: -x[1])
print(f"Total: {total} replacements across {len(changed)} files")
for fp, c in changed[:30]:
    print(f"  {c:3d}  {fp}")
if len(changed) > 30:
    print(f"  ... and {len(changed) - 30} more")
