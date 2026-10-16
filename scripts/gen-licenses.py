#!/usr/bin/env python3
"""오픈소스 고지 자동 생성 (명세 §4.2 P1, 배포 전 필수). 표준 라이브러리만 쓴다.

  python3 scripts/gen-licenses.py                 # 저장소 루트에서
  GRADLE_ARGS="-I /tmp/mirror.init.gradle" python3 scripts/gen-licenses.py   # Maven Central이 막힌 세션

모으는 것
  1. Rust: `cargo metadata --filter-platform aarch64-linux-android`로 wipi_android가 실제로 링크하는 크레이트
     (일반 의존성만 — build·dev 의존성과 proc-macro는 앱에 들어가지 않으므로 뺀다, 작업 공간 크레이트도 뺀다)
     라이선스 원문은 크레이트 폴더(git 의존성은 저장소 루트까지)의 LICENSE*/COPYING*/NOTICE* 파일
  2. Android: `:app:dependencies --configuration releaseRuntimeClasspath`의 라이브러리,
     라이선스 이름은 Gradle 캐시의 POM <licenses>. Apache-2.0 원문은 크레이트에서 찾은 것을 쓴다
결과: android/app/src/main/assets/licenses/third_party.txt (앱 설정 > 정보 > 오픈소스 라이선스 > 자동 생성 목록)
손으로 쓴 고지(wie·RustJava·smaf·rodio/cpal·Neo둥근모·rustysynth·GeneralUser GS)는 Settings.kt에 그대로 둔다.
의존성(Cargo.lock·build.gradle.kts)이 바뀌면 다시 돌려 커밋한다.
"""
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "licenses", "third_party.txt")
LICENSE_FILE = re.compile(r"^(LICEN[CS]E|COPYING|NOTICE|COPYRIGHT|UNLICENSE)([-_.].*)?$", re.I)


def rust_packages():
    meta = json.loads(subprocess.check_output(
        ["cargo", "metadata", "--format-version", "1", "--filter-platform", "aarch64-linux-android"],
        cwd=os.path.join(ROOT, "rust")))
    pkgs = {p["id"]: p for p in meta["packages"]}
    nodes = {n["id"]: n for n in meta["resolve"]["nodes"]}
    members = set(meta["workspace_members"])
    root = next(i for i in members if pkgs[i]["name"] == "wipi_android")

    def is_proc_macro(pid):
        return any("proc-macro" in t["kind"] for t in pkgs[pid]["targets"])

    seen, stack = set(), [root]
    while stack:
        pid = stack.pop()
        for dep in nodes[pid]["deps"]:
            normal = any(k["kind"] is None for k in dep["dep_kinds"])
            d = dep["pkg"]
            if not normal or d in seen or is_proc_macro(d):
                continue
            seen.add(d)
            stack.append(d)
    out = []
    for pid in seen:
        if pid in members:
            continue
        p = pkgs[pid]
        out.append({
            "name": p["name"], "version": p["version"], "license": p.get("license") or "(see file)",
            "url": p.get("repository") or "", "texts": license_texts(p), "authors": p.get("authors") or [],
        })
    return sorted(out, key=lambda x: (x["name"], x["version"]))


def license_texts(p):
    d = os.path.dirname(p["manifest_path"])
    files = []
    if p.get("license_file"):
        files.append(os.path.join(d, p["license_file"]))
    # 크레이트 폴더에 없으면(작업 공간형 git 저장소) 위로 올라가며 찾는다 — cargo 캐시 폴더를 넘지 않게
    cur = d
    for _ in range(4):
        found = [os.path.join(cur, f) for f in sorted(os.listdir(cur)) if LICENSE_FILE.match(f) and os.path.isfile(os.path.join(cur, f))]
        if found:
            files += found
            break
        parent = os.path.dirname(cur)
        if parent == cur or os.path.basename(parent) in ("checkouts", "src", "registry"):
            break
        cur = parent
    texts = []
    for f in dict.fromkeys(files):
        try:
            with open(f, encoding="utf-8", errors="replace") as fh:
                texts.append(fh.read().strip())
        except OSError:
            pass
    return texts


def android_libraries():
    args = os.environ.get("GRADLE_ARGS", "").split()
    gradlew = os.path.join(ROOT, "android", "gradlew")
    out = subprocess.check_output(
        [gradlew, *args, "-q", ":app:dependencies", "--configuration", "releaseRuntimeClasspath"],
        cwd=os.path.join(ROOT, "android"), text=True)
    libs = {}
    for line in out.splitlines():
        m = re.search(r"[+\\]--- ([\w.\-]+):([\w.\-]+):([\w.\-+]+)(?: -> ([\w.\-+]+))?", line)
        if not m or "project " in line:
            continue
        group, artifact, ver, resolved = m.groups()
        ver = resolved or ver
        libs[(group, artifact)] = ver
    cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
    result = []
    for (group, artifact), ver in sorted(libs.items()):
        result.append({"name": f"{group}:{artifact}", "version": ver, "license": pom_license(cache, group, artifact, ver) or "(unknown)"})
    return result


def pom_license(cache, group, artifact, ver, depth=0):
    d = os.path.join(cache, group, artifact, ver)
    if not os.path.isdir(d) or depth > 3:
        return None
    for h in os.listdir(d):
        pom = os.path.join(d, h, f"{artifact}-{ver}.pom")
        if not os.path.exists(pom):
            continue
        try:
            root = ET.parse(pom).getroot()
        except ET.ParseError:
            return None
        ns = {"m": root.tag.split("}")[0].strip("{")} if root.tag.startswith("{") else {"m": ""}
        q = (lambda t: f"m:{t}") if ns["m"] else (lambda t: t)
        names = [e.text.strip() for e in root.findall(f".//{q('licenses')}/{q('license')}/{q('name')}", ns) if e.text]
        if names:
            return " / ".join(dict.fromkeys(names))
        parent = root.find(q("parent"), ns)
        if parent is not None:
            g = parent.findtext(q("groupId"), namespaces=ns)
            a = parent.findtext(q("artifactId"), namespaces=ns)
            v = parent.findtext(q("version"), namespaces=ns)
            if g and a and v:
                return pom_license(cache, g.strip(), a.strip(), v.strip(), depth + 1)
    return None


MIT_TEMPLATE = """MIT License

Copyright (c) {holders}

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."""


def fill_missing(rust):
    """원문 파일이 없는 크레이트: Apache-2.0을 고를 수 있으면 Apache 원문(목록에 이름만 더함), MIT만이면 메타데이터 저자로 MIT 원문"""
    for p in rust:
        if p["texts"]:
            continue
        lic = p["license"]
        if "Apache-2.0" in lic:
            p["apache_only"] = True
        elif "MIT" in lic:
            holders = ", ".join(re.sub(r"\s*<[^>]*>", "", a).strip() for a in p["authors"]) or f'the {p["name"]} authors'
            p["texts"] = [MIT_TEMPLATE.format(holders=holders)]
            p["generated"] = True


def norm(t):
    return re.sub(r"\s+", " ", t).strip()


def main():
    rust = rust_packages()
    fill_missing(rust)
    android = android_libraries()

    # 같은 원문은 한 번만: 원문(공백 정규화) 해시 → 쓰는 패키지 목록
    groups = {}
    for p in rust:
        for t in p["texts"]:
            h = hashlib.sha1(norm(t).encode()).hexdigest()
            groups.setdefault(h, {"text": t, "users": []})["users"].append(f'{p["name"]} {p["version"]}')
    apache = next((g for g in groups.values() if "Apache License" in g["text"] and "Version 2.0" in g["text"] and "TERMS AND CONDITIONS" in g["text"]), None)
    android_apache = [f'{a["name"]} {a["version"]}' for a in android if "apache" in a["license"].lower()]
    if apache is not None:
        apache["users"] += android_apache + [f'{p["name"]} {p["version"]}' for p in rust if p.get("apache_only")]

    lines = []
    lines.append("Third-party software notices / 오픈소스 고지 (자동 생성)")
    lines.append(f"Generated {datetime.date.today().isoformat()} by scripts/gen-licenses.py")
    lines.append("")
    lines.append(f"== Rust crates in the native library (aarch64-linux-android): {len(rust)} ==")
    for p in rust:
        lines.append(f'{p["name"]} {p["version"]} — {p["license"]}' + (f' — {p["url"]}' if p["url"] else ""))
    lines.append("")
    lines.append(f"== Android libraries (release runtime): {len(android)} ==")
    for a in android:
        lines.append(f'{a["name"]} {a["version"]} — {a["license"]}')
    lines.append("")
    no_text = [p for p in rust if p.get("apache_only") or p.get("generated")]
    if no_text:
        lines.append("== Crates without a license file in their package ==")
        lines.append("(MIT OR Apache-2.0: used under Apache-2.0, text below. MIT only: MIT text with the authors from the crate metadata.)")
        lines += [f'{p["name"]} {p["version"]} ({p["license"]})' for p in no_text]
        lines.append("")
    lines.append("== License texts ==")
    for g in sorted(groups.values(), key=lambda g: -len(g["users"])):
        lines.append("")
        lines.append("-" * 60)
        lines.append("Used by: " + ", ".join(sorted(set(g["users"]))))
        lines.append("-" * 60)
        lines.append(g["text"])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    unknown = [a["name"] for a in android if a["license"] == "(unknown)"]
    print(f"rust {len(rust)} crates ({len(no_text)} without file), android {len(android)} libs ({len(unknown)} unknown), "
          f"{len(groups)} distinct texts, {os.path.getsize(OUT) // 1024} KB -> {os.path.relpath(OUT, ROOT)}")
    if unknown:
        print("unknown android licenses:", ", ".join(unknown))
    if apache is None and android_apache:
        print("WARNING: no Apache-2.0 full text found among crates", file=sys.stderr)


if __name__ == "__main__":
    main()
