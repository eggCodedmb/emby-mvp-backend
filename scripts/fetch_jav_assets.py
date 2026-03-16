#!/usr/bin/env python3
"""
从数据库 media_items.code 拉取日本元数据详情，下载封面与演员头像到本地。

默认配置读取：src/main/resources/application.yml
- 数据库: spring.datasource.url/username/password
- 元数据源: app.jav-meta.base-url（默认 https://tools.miku.ac）
- 封面目录: app.media.poster-dir（默认 E:/Media/.posters）
- 演员目录: app.media.actor-dir（默认 E:/Media/actors）

依赖:
    pip install psycopg2-binary pyyaml requests

用法:
    python scripts/fetch_jav_assets.py --limit 50
    python scripts/fetch_jav_assets.py --codes DASS-286,IPX-367
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple
from urllib.parse import urljoin, urlparse

try:
    import yaml  # type: ignore
    import requests  # type: ignore
    import psycopg2  # type: ignore
except Exception as e:
    print("缺少依赖，请先安装: pip install psycopg2-binary pyyaml requests")
    raise


UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
)


def load_yaml_config(root: Path) -> Dict:
    yml = root / "src" / "main" / "resources" / "application.yml"
    if not yml.exists():
        return {}
    return yaml.safe_load(yml.read_text(encoding="utf-8")) or {}


def get_cfg(cfg: Dict, dotted: str, default=None):
    cur = cfg
    for k in dotted.split("."):
        if not isinstance(cur, dict) or k not in cur:
            return default
        cur = cur[k]
    return cur


def parse_pg_jdbc(jdbc_url: str) -> Tuple[str, int, str]:
    # jdbc:postgresql://localhost:5432/emby_mvp
    m = re.match(r"jdbc:postgresql://([^/:]+)(?::(\d+))?/([^?]+)", jdbc_url)
    if not m:
        raise ValueError(f"无法解析 JDBC URL: {jdbc_url}")
    host = m.group(1)
    port = int(m.group(2) or 5432)
    db = m.group(3)
    return host, port, db


def normalize_url(base_url: str, raw: Optional[str]) -> Optional[str]:
    if not raw:
        return None
    v = raw.strip()
    if not v or v.startswith("data:"):
        return None
    if v.startswith("//"):
        return "https:" + v
    if v.startswith("http://") or v.startswith("https://"):
        return v
    return urljoin(base_url.rstrip("/") + "/", v.lstrip("/"))


def ext_from_url_or_ct(url: str, content_type: str = "") -> str:
    ct = (content_type or "").lower()
    if "png" in ct:
        return ".png"
    if "webp" in ct:
        return ".webp"
    if "gif" in ct:
        return ".gif"
    if "bmp" in ct:
        return ".bmp"
    if "jpeg" in ct or "jpg" in ct:
        return ".jpg"

    p = urlparse(url).path.lower()
    for ext in (".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"):
        if p.endswith(ext):
            return ".jpg" if ext == ".jpeg" else ext
    return ".jpg"


def clean_old_variants(root: Path, stem: str):
    for ext in (".jpg", ".png", ".webp", ".gif", ".bmp"):
        p = root / f"{stem}{ext}"
        if p.exists():
            p.unlink(missing_ok=True)


def http_get_json(session: requests.Session, url: str, params: Dict, retries: int = 3) -> Optional[Dict]:
    headers = {
        "User-Agent": UA,
        "Accept": "application/json,text/plain,*/*",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Referer": "https://tools.miku.ac/",
        "Origin": "https://tools.miku.ac",
    }
    for i in range(1, retries + 1):
        try:
            r = session.get(url, params=params, headers=headers, timeout=(8, 20), allow_redirects=True)
            if r.status_code == 200:
                return r.json()
            print(f"[WARN] JSON请求失败 attempt={i} status={r.status_code} url={r.url}")
        except Exception as e:
            print(f"[WARN] JSON请求异常 attempt={i} err={e}")
        time.sleep(0.3 * i)
    return None


def download_binary(session: requests.Session, url: str, retries: int = 3) -> Optional[Tuple[bytes, str]]:
    headers = {
        "User-Agent": UA,
        "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Referer": "https://tools.miku.ac/",
        "Origin": "https://tools.miku.ac",
    }
    for i in range(1, retries + 1):
        try:
            r = session.get(url, headers=headers, timeout=(8, 20), allow_redirects=True)
            if r.status_code == 200 and r.content:
                return r.content, (r.headers.get("Content-Type") or "")
            print(f"[WARN] 下载失败 attempt={i} status={r.status_code} url={url}")
        except Exception as e:
            print(f"[WARN] 下载异常 attempt={i} err={e} url={url}")
        time.sleep(0.3 * i)
    return None


def safe_name(s: str) -> str:
    s = re.sub(r"[\\/:*?\"<>|\s]+", "_", s.strip())
    return s[:80] or "unknown"


def fetch_codes_from_db(conn, limit: int) -> List[Tuple[int, str]]:
    sql = """
        SELECT id, code
        FROM media_items
        WHERE code IS NOT NULL AND btrim(code) <> ''
        ORDER BY updated_at DESC NULLS LAST, id DESC
        LIMIT %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (limit,))
        rows = cur.fetchall()
    return [(int(r[0]), str(r[1]).strip()) for r in rows if r[1]]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=100)
    parser.add_argument("--codes", type=str, default="", help="逗号分隔 code，传入则不查数据库")
    parser.add_argument("--source", type=str, default="jp")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    cfg = load_yaml_config(root)

    base_url = get_cfg(cfg, "app.jav-meta.base-url", "https://tools.miku.ac")
    detail_api = base_url.rstrip("/") + "/api/t/jav-search/info"
    poster_dir = Path(get_cfg(cfg, "app.media.poster-dir", "E:/Media/.posters"))
    actor_dir = Path(get_cfg(cfg, "app.media.actor-dir", "E:/Media/actors"))
    poster_dir.mkdir(parents=True, exist_ok=True)
    actor_dir.mkdir(parents=True, exist_ok=True)

    codes: List[Tuple[int, str]] = []
    if args.codes.strip():
        in_codes = [c.strip() for c in args.codes.split(",") if c.strip()]
        codes = list(enumerate(in_codes, start=1))
    else:
        jdbc = get_cfg(cfg, "spring.datasource.url")
        user = get_cfg(cfg, "spring.datasource.username")
        pwd = get_cfg(cfg, "spring.datasource.password")
        if not jdbc:
            print("未找到数据库配置 spring.datasource.url")
            sys.exit(2)
        host, port, db = parse_pg_jdbc(jdbc)
        conn = psycopg2.connect(host=host, port=port, dbname=db, user=user, password=pwd)
        try:
            codes = fetch_codes_from_db(conn, args.limit)
        finally:
            conn.close()

    session = requests.Session()

    ok_cover = 0
    ok_actor = 0
    total = len(codes)

    for media_id, code in codes:
        payload = http_get_json(session, detail_api, {"id": code, "source": args.source, "type": "censored"})
        if not payload or not isinstance(payload, dict):
            print(f"[SKIP] code={code} 无有效JSON")
            continue

        data = payload.get("data") or {}
        if not isinstance(data, dict):
            print(f"[SKIP] code={code} data为空")
            continue

        # 封面（cover 失败时回退 preview[0]）
        cover_url = normalize_url(base_url, data.get("cover"))
        cover_candidates: List[str] = []
        if cover_url:
            cover_candidates.append(cover_url)
        previews = data.get("preview")
        if isinstance(previews, list) and previews:
            p0 = normalize_url(base_url, str(previews[0]))
            if p0:
                cover_candidates.append(p0)

        for cu in cover_candidates:
            res = download_binary(session, cu)
            if not res:
                continue
            content, ct = res
            ext = ext_from_url_or_ct(cu, ct)
            stem = str(media_id)
            clean_old_variants(poster_dir, stem)
            (poster_dir / f"{stem}{ext}").write_bytes(content)
            ok_cover += 1
            break

        # 演员头像
        actors = data.get("actor")
        if isinstance(actors, list):
            for a in actors:
                if not isinstance(a, dict):
                    continue
                name = str(a.get("name") or "").strip()
                avatar_url = normalize_url(base_url, a.get("avatar") or a.get("avatarUrl") or a.get("avatar_url"))
                if not name or not avatar_url:
                    continue
                res = download_binary(session, avatar_url)
                if not res:
                    continue
                content, ct = res
                ext = ext_from_url_or_ct(avatar_url, ct)
                key = hashlib.md5(name.encode("utf-8")).hexdigest()[:8]
                fname = f"{safe_name(name)}_{key}{ext}"
                (actor_dir / fname).write_bytes(content)
                ok_actor += 1

        print(f"[OK] code={code}")

    print(json.dumps({
        "totalCodes": total,
        "coverSaved": ok_cover,
        "actorAvatarSaved": ok_actor,
        "posterDir": str(poster_dir),
        "actorDir": str(actor_dir),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
