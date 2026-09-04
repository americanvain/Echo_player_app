#!/usr/bin/env python3
"""从 ECDICT（MIT，skywind3000/ECDICT）生成随 APK 内置的精简英汉词典。

输出 app/src/main/assets/dict/ecdict.db，一张表：
  words(word PRIMARY KEY, phonetic, translation, lemma, rank)
    - lemma：屈折形式指回原形（blew → blow），点词时两条一起显示
    - rank：词频名次（min(frq, bnc)），0 = 未知；用来挑"本句里的难词"

收录：Oxford 3000、Collins 星级词、BNC / 当代语料前 3 万、中高考四六级考研托福雅思 GRE 词表，
再加上这些词的全部屈折形式，以及内置阅读资源里出现的每一个词。

用法：python3 tools/build_dictionary.py ~/tools/ecdict/stardict.db
"""
import glob
import json
import os
import re
import sqlite3
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "dict", "ecdict.db")
MATERIALS = os.path.join(ROOT, "app", "src", "main", "assets", "materials")

BASE_WHERE = (
    "oxford=1 or collins>=1 or (bnc>0 and bnc<=30000) or (frq>0 and frq<=30000)"
    " or tag like '%zk%' or tag like '%gk%' or tag like '%cet4%' or tag like '%cet6%'"
    " or tag like '%ky%' or tag like '%toefl%' or tag like '%ielts%' or tag like '%gre%'"
)

# exchange 字段里的形态代号 → 中文
FORM_NAMES = {
    "p": "过去式", "d": "过去分词", "i": "现在分词", "3": "第三人称单数",
    "s": "复数", "r": "比较级", "t": "最高级",
}


def clean_translation(t: str, max_lines: int = 3, max_chars: int = 170) -> str:
    if not t:
        return ""
    lines = [ln.strip() for ln in t.replace("\r", "").split("\n") if ln.strip()]
    lines = [ln for ln in lines if not ln.startswith("[")] or lines  # 去掉 [计] 之类的领域标签行（若全是就保留）
    out = "\n".join(lines[:max_lines])
    if len(out) > max_chars:
        out = out[:max_chars].rstrip(",，;； ") + "…"
    return out


def material_words():
    words = set()
    for path in glob.glob(os.path.join(MATERIALS, "*.json")):
        if path.endswith("index.json"):
            continue
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for seg in data.get("segments", []):
            for u in seg.get("units", []):
                for w in re.findall(r"[A-Za-z][A-Za-z'\-]*", u["text"]):
                    words.add(w.lower().strip("'-"))
    return {w for w in words if w}


def main(src):
    if os.path.exists(OUT):
        os.remove(OUT)
    src_db = sqlite3.connect(src)
    src_db.row_factory = sqlite3.Row
    cur = src_db.cursor()

    print("① 基础词表…")
    base = {}
    for r in cur.execute(f"select word, phonetic, translation, exchange, frq, bnc from stardict where {BASE_WHERE}"):
        w = r["word"].strip()
        if not w or " " in w or not re.match(r"^[A-Za-z][A-Za-z'\-]*$", w):
            continue
        base[w.lower()] = dict(r)
    print("   ", len(base))

    print("② 内置资源里的词…")
    extra = material_words() - set(base)
    if extra:
        q = ",".join("?" * len(extra))
        for r in cur.execute(f"select word, phonetic, translation, exchange, frq, bnc from stardict where word in ({q})", list(extra)):
            base[r["word"].lower()] = dict(r)
    print("   +", len(extra), "→", len(base))

    print("③ 屈折形式…")
    rows = {}  # word -> (phonetic, translation, lemma, rank)

    def rank_of(r):
        vals = [v for v in (r.get("frq") or 0, r.get("bnc") or 0) if v and v > 0]
        return min(vals) if vals else 0

    for w, r in base.items():
        lemma = None
        ex = r.get("exchange") or ""
        m = re.search(r"(?:^|/)0:([^/]+)", ex)
        if m and m.group(1).lower() != w:
            lemma = m.group(1).lower()
        rows[w] = (r.get("phonetic") or "", clean_translation(r.get("translation") or ""), lemma, rank_of(r))

    derived = 0
    for w, r in list(base.items()):
        ex = r.get("exchange") or ""
        for part in ex.split("/"):
            if ":" not in part:
                continue
            code, form = part.split(":", 1)
            form = form.strip().lower()
            if code in ("0", "1") or not form or form == w or not re.match(r"^[a-z][a-z'\-]*$", form):
                continue
            if form in rows:
                continue
            name = FORM_NAMES.get(code)
            if not name:
                continue
            rows[form] = ("", f"{w}的{name}", w, 0)
            derived += 1
    print("   +", derived, "→", len(rows))

    # 把 0:lemma 指向的原形补进来（若不在表里）
    print("④ 补齐被指向的原形…")
    missing = {v[2] for v in rows.values() if v[2] and v[2] not in rows}
    if missing:
        q = ",".join("?" * len(missing))
        n = 0
        for r in cur.execute(f"select word, phonetic, translation, frq, bnc from stardict where word in ({q})", list(missing)):
            rows[r["word"].lower()] = (r["phonetic"] or "", clean_translation(r["translation"] or ""), None, rank_of(dict(r)))
            n += 1
        print("   +", n)

    print("⑤ 写库…")
    out = sqlite3.connect(OUT)
    out.execute("pragma page_size=4096")
    out.execute("create table words(word text primary key not null, phonetic text, translation text not null, lemma text, rank integer not null)")
    out.executemany("insert into words values(?,?,?,?,?)", ((w, p or None, t, l, rk) for w, (p, t, l, rk) in rows.items() if t))
    out.execute("create table meta(key text primary key, value text)")
    out.executemany("insert into meta values(?,?)", [("source", "ECDICT 1.0.28 (MIT) https://github.com/skywind3000/ECDICT"), ("entries", str(len(rows))), ("schema", "1")])
    out.commit()
    out.execute("vacuum")
    out.close()
    size = os.path.getsize(OUT)
    print(f"   {OUT}  {size/1048576:.1f} MB, {len(rows)} 条")

    # 抽查
    chk = sqlite3.connect(OUT)
    for w in ("blew", "tighter", "cloak", "shone", "traveler", "traveller", "parsimony", "the", "run", "running"):
        print("   ", w, "→", chk.execute("select phonetic, substr(translation,1,40), lemma, rank from words where word=?", (w,)).fetchone())
    miss = [w for w in sorted(material_words()) if not chk.execute("select 1 from words where word=?", (w,)).fetchone()]
    print("   内置资源里查不到的词：", miss[:30], "…共", len(miss))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/tools/ecdict/stardict.db"))
