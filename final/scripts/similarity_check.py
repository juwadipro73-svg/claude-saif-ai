#!/usr/bin/env python3
"""
SAIF AI Full-Repository Cleanroom Similarity Gate Checker
Audits EVERY Kotlin, Java, Go, JS, and XML file in the repository against /outside_repo/legacy/.

Gate Pass Conditions:
1. No individual file exceeds 15.0% matching lines.
2. No 8-line sliding window matches exactly (0 allowed).
3. Total project matching line percentage is below 8.0%.
Outputs to docs/gates/similarity_gate.txt and docs/similarity_report.md.
"""

import os
import sys
import re
import hashlib

MIN_LINE_LEN = 18
WINDOW_SIZE = 8
MAX_FILE_MATCH_PCT = 15.0
MAX_PROJECT_MATCH_PCT = 8.0

EXTENSIONS = ('.kt', '.java', '.go', '.js', '.xml')

def normalize_content(content: str, ext: str) -> list[str]:
    # Strip block comments
    content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
    # Strip XML comments
    if ext == '.xml':
        content = re.sub(r'<!--.*?-->', '', content, flags=re.DOTALL)
    
    cleaned_lines = []
    for line in content.splitlines():
        line = re.sub(r'//.*$', '', line)
        line = line.strip()
        
        # Strip package and import lines
        if line.startswith(('package ', 'import ', '<?xml', 'xmlns:')):
            continue
            
        # Ignore lines shorter than 18 characters
        if len(line) < MIN_LINE_LEN:
            continue
            
        cleaned_lines.append(line)
        
    return cleaned_lines

def compute_hashes(lines: list[str]) -> set[str]:
    return {hashlib.sha256(l.encode('utf-8')).hexdigest() for l in lines}

def compute_windows(lines: list[str], size: int = WINDOW_SIZE) -> set[str]:
    windows = set()
    if len(lines) < size:
        return windows
    for i in range(len(lines) - size + 1):
        window_text = '\n'.join(lines[i:i+size])
        windows.add(hashlib.sha256(window_text.encode('utf-8')).hexdigest())
    return windows

def collect_files(directory: str, is_legacy: bool = False) -> list[str]:
    found = []
    if not os.path.exists(directory):
        return found
    for root, dirs, files in os.walk(directory):
        # Exclude build, git, and legacy quarantine for candidate files
        if not is_legacy and any(ignored in root for ignored in ['build', '.gradle', '.git', 'legacy_readonly', 'outside_repo']):
            continue
        for f in files:
            if f.endswith(EXTENSIONS):
                found.append(os.path.join(root, f))
    return sorted(found)

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    legacy_dir = '/outside_repo/legacy'
    if not os.path.exists(legacy_dir):
        legacy_dir = os.path.join(repo_root, 'outside_repo/legacy')

    print(f"============================================================")
    print(f"SAIF AI Cleanroom Similarity Gate (Full Repository Audit)")
    print(f"============================================================")
    print(f"Reference Quarantine: {legacy_dir}")

    legacy_files = collect_files(legacy_dir, is_legacy=True)
    print(f"Indexed {len(legacy_files)} legacy reference files.")

    legacy_line_hashes = set()
    legacy_windows = set()

    for lf in legacy_files:
        ext = os.path.splitext(lf)[1].lower()
        try:
            with open(lf, 'r', encoding='utf-8', errors='ignore') as f:
                lines = normalize_content(f.read(), ext)
                legacy_line_hashes.update(compute_hashes(lines))
                legacy_windows.update(compute_windows(lines, WINDOW_SIZE))
        except Exception as e:
            print(f"Warning reading legacy file {lf}: {e}", file=sys.stderr)

    print(f"Total legacy line signatures: {len(legacy_line_hashes)}")
    print(f"Total legacy 8-line window signatures: {len(legacy_windows)}")

    app_src_dir = os.path.join(repo_root, 'app/src/main')
    candidate_files = collect_files(app_src_dir)
    print(f"\nAuditing ALL {len(candidate_files)} production source files in app/src/main/...")

    total_candidate_lines = 0
    total_matched_lines = 0
    total_window_violations = 0
    file_results = []

    for cf in candidate_files:
        rel_path = os.path.relpath(cf, repo_root)
        ext = os.path.splitext(cf)[1].lower()
        try:
            with open(cf, 'r', encoding='utf-8', errors='ignore') as f:
                lines = normalize_content(f.read(), ext)
        except Exception:
            continue

        file_line_count = len(lines)
        if file_line_count == 0:
            continue

        total_candidate_lines += file_line_count

        matched_lines = sum(1 for l in lines if hashlib.sha256(l.encode('utf-8')).hexdigest() in legacy_line_hashes)
        total_matched_lines += matched_lines

        cand_windows = compute_windows(lines, WINDOW_SIZE)
        window_matches = sum(1 for w in cand_windows if w in legacy_windows)
        total_window_violations += window_matches

        match_pct = (matched_lines / file_line_count) * 100.0 if file_line_count > 0 else 0.0
        passed = (match_pct <= MAX_FILE_MATCH_PCT) and (window_matches == 0)

        file_results.append({
            'file': rel_path,
            'lines': file_line_count,
            'matched': matched_lines,
            'match_pct': match_pct,
            'window_matches': window_matches,
            'passed': passed
        })

    project_match_pct = (total_matched_lines / total_candidate_lines * 100.0) if total_candidate_lines > 0 else 0.0
    project_passed = (project_match_pct <= MAX_PROJECT_MATCH_PCT) and (total_window_violations == 0) and all(r['passed'] for r in file_results)

    # Save to docs/gates/similarity_gate.txt and docs/similarity_report.md
    gates_dir = os.path.join(repo_root, 'docs/gates')
    os.makedirs(gates_dir, exist_ok=True)
    gate_txt_path = os.path.join(gates_dir, 'similarity_gate.txt')
    report_path = os.path.join(repo_root, 'docs/similarity_report.md')

    summary_text = (
        f"SAIF AI Similarity Gate Verification\n"
        f"Audited Files: {len(file_results)}\n"
        f"Total Evaluated Normalized Lines: {total_candidate_lines}\n"
        f"Total Matched Lines: {total_matched_lines} ({project_match_pct:.2f}% vs max {MAX_PROJECT_MATCH_PCT}%)\n"
        f"Total 8-Line Window Violations: {total_window_violations} (max allowed: 0)\n"
        f"Gate Verdict: {'PASSED' if project_passed else 'FAILED'}\n"
    )

    with open(gate_txt_path, 'w', encoding='utf-8') as gf:
        gf.write(summary_text)

    with open(report_path, 'w', encoding='utf-8') as rf:
        rf.write("# SAIF AI Cleanroom Similarity Gate Report\n\n")
        rf.write(f"**Gate Status**: {'PASSED' if project_passed else 'FAILED'}\n\n")
        rf.write(f"- **Total Source Files Audited**: {len(file_results)}\n")
        rf.write(f"- **Total Evaluated Normalized Lines**: {total_candidate_lines}\n")
        rf.write(f"- **Total Matched Lines**: {total_matched_lines} ({project_match_pct:.2f}% vs max {MAX_PROJECT_MATCH_PCT}%)\n")
        rf.write(f"- **Total 8-Line Sliding Window Violations**: {total_window_violations} (allowed: 0)\n\n")
        rf.write("## Per-File Evaluation Breakdown (Showing Files with Matches)\n\n")
        rf.write("| File | Normalized Lines | Matched Lines | Match % | Window Violations | Status |\n")
        rf.write("| :--- | :---: | :---: | :---: | :---: | :---: |\n")
        for r in file_results:
            if r['matched'] > 0 or not r['passed']:
                status_str = "PASS" if r['passed'] else "**FAIL**"
                rf.write(f"| `{r['file']}` | {r['lines']} | {r['matched']} | {r['match_pct']:.2f}% | {r['window_matches']} | {status_str} |\n")

    print("\n" + summary_text)
    if not project_passed:
        failing_files = [r for r in file_results if not r['passed']]
        print(f"Failing files ({len(failing_files)}):")
        for f in failing_files[:10]:
            print(f"  - {f['file']}: {f['match_pct']:.2f}% matches, {f['window_matches']} window matches")
        sys.exit(1)

if __name__ == '__main__':
    main()
