# Bootstrap the Android toolchain into D:\AndroidDevelopment (no schannel: uses urllib/OpenSSL).
import os
import shutil
import sys
import time
import urllib.request
import zipfile

BASE = r"D:\AndroidDevelopment"
DL = os.path.join(BASE, "dl")
os.makedirs(DL, exist_ok=True)

TARGETS = [
    (
        "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
        os.path.join(DL, "cmdline-tools.zip"),
    ),
    (
        "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse",
        os.path.join(DL, "jdk17.zip"),
    ),
]


def download(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 10_000_000:
        print("skip (cached) %s (%d bytes)" % (dest, os.path.getsize(dest)), flush=True)
        return
    print("downloading %s" % url, flush=True)
    started = time.time()
    part = dest + ".part"
    with urllib.request.urlopen(url, timeout=180) as resp, open(part, "wb") as out:
        total = int(resp.headers.get("Content-Length") or 0)
        got = 0
        last_report = 0
        while True:
            chunk = resp.read(1 << 20)
            if not chunk:
                break
            out.write(chunk)
            got += len(chunk)
            if got - last_report >= (50 << 20):
                last_report = got
                speed = got / 1048576.0 / max(time.time() - started, 0.001)
                print("  %.0f/%.0f MB  %.1f MB/s" % (got / 1048576.0, total / 1048576.0, speed), flush=True)
    os.replace(part, dest)
    print("done %s (%d bytes)" % (dest, os.path.getsize(dest)), flush=True)


def unzip(zip_path, out_dir):
    print("extracting %s -> %s" % (zip_path, out_dir), flush=True)
    os.makedirs(out_dir, exist_ok=True)
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(out_dir)


for _url, _dest in TARGETS:
    download(_url, _dest)

sdk_dir = os.path.join(BASE, "sdk")
tools_latest = os.path.join(sdk_dir, "cmdline-tools", "latest")
if not os.path.isdir(os.path.join(tools_latest, "bin")):
    tmp = os.path.join(DL, "cmdline-extract")
    if os.path.isdir(tmp):
        shutil.rmtree(tmp)
    unzip(os.path.join(DL, "cmdline-tools.zip"), tmp)
    os.makedirs(os.path.dirname(tools_latest), exist_ok=True)
    if os.path.isdir(tools_latest):
        shutil.rmtree(tools_latest)
    shutil.move(os.path.join(tmp, "cmdline-tools"), tools_latest)
    print("cmdline-tools installed at %s" % tools_latest, flush=True)

jdk_dir = os.path.join(BASE, "jdk17")
if not os.path.isdir(os.path.join(jdk_dir, "bin")):
    tmp2 = os.path.join(DL, "jdk-extract")
    if os.path.isdir(tmp2):
        shutil.rmtree(tmp2)
    unzip(os.path.join(DL, "jdk17.zip"), tmp2)
    inner = [n for n in os.listdir(tmp2) if os.path.isdir(os.path.join(tmp2, n))][0]
    shutil.move(os.path.join(tmp2, inner), jdk_dir)
    print("jdk17 installed at %s" % jdk_dir, flush=True)

print("ALL DONE", flush=True)
