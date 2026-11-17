# Extract cmdline-tools (script 1 died before unzip) and install the SDK packages.
import os
import shutil
import subprocess
import zipfile

BASE = r"D:\AndroidDevelopment"
DL = os.path.join(BASE, "dl")
SDK = os.path.join(BASE, "sdk")
JDK = os.path.join(BASE, "jdk17")
latest = os.path.join(SDK, "cmdline-tools", "latest")

zip_path = os.path.join(DL, "cmdline-tools.zip")
print("zip exists:", os.path.exists(zip_path), flush=True)
if not os.path.isdir(os.path.join(latest, "bin")):
    tmp = os.path.join(DL, "cmdline-extract")
    if os.path.isdir(tmp):
        shutil.rmtree(tmp)
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(tmp)
    os.makedirs(os.path.dirname(latest), exist_ok=True)
    if os.path.isdir(latest):
        shutil.rmtree(latest)
    shutil.move(os.path.join(tmp, "cmdline-tools"), latest)
    print("extracted ->", latest, flush=True)

SDKM = os.path.join(latest, "bin", "sdkmanager.bat")
print("sdkmanager exists:", os.path.exists(SDKM), flush=True)

env = dict(os.environ)
env["JAVA_HOME"] = JDK
env["PATH"] = os.path.join(JDK, "bin") + os.pathsep + env.get("PATH", "")
env["ANDROID_HOME"] = SDK
env["ANDROID_SDK_ROOT"] = SDK
env["JAVA_TOOL_OPTIONS"] = "-Dfile.encoding=UTF-8"


def run(args, stdin=None):
    print("+ " + " ".join(args), flush=True)
    proc = subprocess.run(args, env=env, capture_output=True, text=True, input=stdin, errors="replace")
    out = (proc.stdout or "")[-3000:]
    err = (proc.stderr or "")[-3000:]
    if out:
        print(out, flush=True)
    if err:
        print("STDERR: " + err, flush=True)
    return proc.returncode


run([SDKM, "--sdk_root=" + SDK, "--version"])
run([SDKM, "--sdk_root=" + SDK, "--licenses"], stdin="y\n" * 60)

packages = ["platform-tools", "platforms;android-37", "build-tools;37.0.0"]
rc = run([SDKM, "--sdk_root=" + SDK, "--install"] + packages, stdin="y\n" * 10)
if rc != 0:
    print("=== bulk install failed, list available build-tools", flush=True)
    run([SDKM, "--sdk_root=" + SDK, "--list"])
print("=== installed", flush=True)
run([SDKM, "--sdk_root=" + SDK, "--list_installed"])
print("DONE", flush=True)
