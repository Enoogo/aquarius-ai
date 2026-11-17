# Install JDK 17 (Corretto fallback) + Android SDK packages into D:\AndroidDevelopment.
import os
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile

BASE = r"D:\AndroidDevelopment"
DL = os.path.join(BASE, "dl")
SDK = os.path.join(BASE, "sdk")
JDK = os.path.join(BASE, "jdk17")
os.makedirs(DL, exist_ok=True)

JDK_URLS = [
    "https://corretto.aws/downloads/latest/amazon-corretto-17-x64-windows-jdk.zip",
    "https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip",
]
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}


def download(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 50_000_000:
        print("skip (cached) %s" % dest, flush=True)
        return True
    print("downloading %s" % url, flush=True)
    part = dest + ".part"
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=180) as resp, open(part, "wb") as out:
            total = int(resp.headers.get("Content-Length") or 0)
            got = 0
            stamp = 0
            while True:
                chunk = resp.read(1 << 20)
                if not chunk:
                    break
                out.write(chunk)
                got += len(chunk)
                if got - stamp >= (50 << 20):
                    stamp = got
                    print("  %.0f/%.0f MB" % (got / 1048576.0, total / 1048576.0), flush=True)
    except Exception as exc:  # noqa: BLE001
        print("FAILED %s -> %s" % (url, exc), flush=True)
        return False
    os.replace(part, dest)
    print("done %s (%d bytes)" % (dest, os.path.getsize(dest)), flush=True)
    return True


if not os.path.isdir(os.path.join(JDK, "bin")):
    ok = False
    for url in JDK_URLS:
        if download(url, os.path.join(DL, "jdk17.zip")):
            ok = True
            break
    if not ok:
        print("NO JDK DOWNLOADED", flush=True)
        sys.exit(1)
    tmp = os.path.join(DL, "jdk-extract")
    if os.path.isdir(tmp):
        shutil.rmtree(tmp)
    with zipfile.ZipFile(os.path.join(DL, "jdk17.zip")) as zf:
        zf.extractall(tmp)
    inner = [n for n in os.listdir(tmp) if os.path.isdir(os.path.join(tmp, n))][0]
    shutil.move(os.path.join(tmp, inner), JDK)
    print("jdk17 installed at %s" % JDK, flush=True)

JAVA_HOME = JDK
env = dict(os.environ)
env["JAVA_HOME"] = JAVA_HOME
env["PATH"] = os.path.join(JAVA_HOME, "bin") + os.pathsep + env.get("PATH", "")
env["ANDROID_HOME"] = SDK
env["ANDROID_SDK_ROOT"] = SDK
env["JAVA_TOOL_OPTIONS"] = "-Dfile.encoding=UTF-8"

SDKMANAGER = os.path.join(SDK, "cmdline-tools", "latest", "bin", "sdkmanager.bat")
print("sdkmanager: %s exists=%s" % (SDKMANAGER, os.path.exists(SDKMANAGER)), flush=True)


def run(args, stdin=None):
    print("+ %s" % " ".join(args), flush=True)
    proc = subprocess.run(args, env=env, capture_output=True, text=True, input=stdin)
    if proc.stdout:
        print(proc.stdout[-4000:], flush=True)
    if proc.stderr:
        print(proc.stderr[-4000:], flush=True)
    return proc.returncode


print("=== installed packages (before)", flush=True)
run([SDKMANAGER, "--sdk_root=" + SDK, "--list_installed"], stdin=None)

print("=== accept licenses", flush=True)
run([SDKMANAGER, "--sdk_root=" + SDK, "--licenses"], stdin="y\n" * 40)

PACKAGES = [
    "platform-tools",
    "platforms;android-37",
    "build-tools;37.0.0",
]
rc = run([SDKMANAGER, "--sdk_root=" + SDK, "--install"] + PACKAGES, stdin="y\n" * 5)
if rc != 0:
    print("=== retry install one by one", flush=True)
    for pkg in PACKAGES:
        run([SDKMANAGER, "--sdk_root=" + SDK, "--install", pkg], stdin="y\n" * 5)

print("=== installed packages (after)", flush=True)
run([SDKMANAGER, "--sdk_root=" + SDK, "--list_installed"], stdin=None)
print("ALL DONE", flush=True)
