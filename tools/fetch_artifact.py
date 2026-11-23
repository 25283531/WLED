"""下载 GitHub Actions 的 artifact。

本机 api.github.com 的 TLS 会被 SNI 级干扰，故需重试；artifact 下载接口会 302 到
Azure Blob，必须用 curl -L 跟随跳转（Python urllib 会把 Authorization 头带到跳转后
的地址而被拒 401）。

用法: python fetch_artifact.py <run_id> <artifact_name> <输出目录>
"""
import json
import os
import ssl
import subprocess
import sys
import time
import urllib.request

TOKEN = os.environ.get("GITHUB_TOKEN", "")
if not TOKEN:
    raise SystemExit("请先设置环境变量 GITHUB_TOKEN")
API = "https://api.github.com/repos/25283531/WLED"
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE


def api_get(path, tries=8):
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(
                API + path,
                headers={
                    "Authorization": f"token {TOKEN}",
                    "Accept": "application/vnd.github+json",
                    "User-Agent": "fetch-artifact",
                },
            )
            with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
                return json.loads(resp.read().decode())
        except Exception as exc:
            last = exc
            time.sleep(2 + i)
    raise SystemExit(f"API failed: {last}")


def main():
    run_id, name, outdir = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(outdir, exist_ok=True)

    data = api_get(f"/actions/runs/{run_id}/artifacts")
    arts = data.get("artifacts", [])
    if not arts:
        raise SystemExit("no artifacts")
    for a in arts:
        print(f"artifact: {a['name']} ({a['size_in_bytes']} bytes) id={a['id']}")

    target = next((a for a in arts if a["name"] == name), None)
    if target is None:
        raise SystemExit(f"artifact {name} not found")

    url = f"{API}/actions/artifacts/{target['id']}/zip"
    zip_path = os.path.join(outdir, f"{name}.zip")
    cmd = [
        "curl", "-sL", "--max-time", "300",
        "-H", f"Authorization: token {TOKEN}",
        "-H", "Accept: application/vnd.github+json",
        "-o", zip_path, url,
    ]
    print("downloading ...")
    subprocess.run(cmd, check=True)
    print(f"saved {zip_path} ({os.path.getsize(zip_path)} bytes)")


if __name__ == "__main__":
    main()
