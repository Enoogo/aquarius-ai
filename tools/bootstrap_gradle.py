# Fetch the Gradle 9.4.1 distribution through an IPv4-friendly mirror and verify its SHA-256.
import hashlib
import os
import shutil
import sys
import urllib.request

EXPECTED = "2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb"
TARGET_DIR = r"D:\AndroidDevelopment\gradle-home\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj"
ZIP = os.path.join(TARGET_DIR, "gradle-9.4.1-bin.zip")

URLS = [
    "https://mirrors.aliyun.com/gradle/gradle-9.4.1-bin.zip",
    "https://mirrors.cloud.tencent.com/gradle/gradle-9.4.1-bin.zip",
    "https://mirrors.huaweicloud.com/gradle/gradle-9.4.1-bin.zip",
    "https://mirrors.tuna.tsinghua.edu.cn/github-release/gradle/gradle/9.4.1/gradle-9.4.1-bin.zip",
    "https://downloads.gradle.org/distributions/gradle-9.4.1-bin.zip",
]


def p(msg):
    print(str(msg).encode("ascii", "replace").decode("ascii"), flush=True)


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


if os.path.exists(ZIP) and sha256_of(ZIP) == EXPECTED:
    p("gradle zip already present and verified")
    sys.exit(0)

os.makedirs(TARGET_DIR, exist_ok=True)
for name in os.listdir(TARGET_DIR):
    if name.endswith(".part") or name.endswith(".lck"):
        os.remove(os.path.join(TARGET_DIR, name))

for url in URLS:
    tmp = ZIP + ".part"
    p("try " + url)
    try:
        with urllib.request.urlopen(url, timeout=120) as resp, open(tmp, "wb") as out:
            total = int(resp.headers.get("Content-Length") or 0)
            got = 0
            while True:
                chunk = resp.read(1 << 20)
                if not chunk:
                    break
                out.write(chunk)
                got += len(chunk)
                if got % (20 << 20) < (1 << 20):
                    p("  %.0f/%.0f MB" % (got / 1048576.0, total / 1048576.0))
    except Exception as exc:  # noqa: BLE001
        p("  failed: %s" % exc)
        if os.path.exists(tmp):
            os.remove(tmp)
        continue
    digest = sha256_of(tmp)
    if digest != EXPECTED:
        p("  sha256 mismatch: %s" % digest)
        os.remove(tmp)
        continue
    os.replace(tmp, ZIP)
    p("OK -> %s" % ZIP)
    sys.exit(0)

p("NO MIRROR WORKED")
sys.exit(1)
