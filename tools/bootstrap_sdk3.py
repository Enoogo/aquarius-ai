# Install Android SDK packages (licenses + platform-tools + platform + build-tools).
import os
import subprocess
import sys

BASE = r"D:\AndroidDevelopment"
SDK = os.path.join(BASE, "sdk")
JDK = os.path.join(BASE, "jdk17")
SDKM = os.path.join(SDK, "cmdline-tools", "latest", "bin", "sdkmanager.bat")


def p(msg):
    text = str(msg)
    try:
        print(text, flush=True)
    except UnicodeEncodeError:
        print(text.encode("ascii", "replace").decode("ascii"), flush=True)


env = dict(os.environ)
env["JAVA_HOME"] = JDK
env["PATH"] = os.path.join(JDK, "bin") + os.pathsep + env.get("PATH", "")
env["ANDROID_HOME"] = SDK
env["ANDROID_SDK_ROOT"] = SDK
env["JAVA_TOOL_OPTIONS"] = "-Dfile.encoding=UTF-8"


def run(args, stdin=None):
    p("+ " + " ".join(args))
    proc = subprocess.run(args, env=env, capture_output=True, input=stdin, text=True, errors="replace")
    if proc.stdout:
        p(proc.stdout[-2500:])
    if proc.stderr:
        p("STDERR: " + proc.stderr[-2500:])
    p("rc=%d" % proc.returncode)
    return proc.returncode


run([SDKM, "--sdk_root=" + SDK, "--licenses"], stdin="y\n" * 80)

packages = ["platform-tools", "platforms;android-37", "build-tools;37.0.0"]
rc = run([SDKM, "--sdk_root=" + SDK, "--install"] + packages, stdin="y\n" * 20)
if rc != 0:
    p("=== bulk install failed; probing available packages")
    run([SDKM, "--sdk_root=" + SDK, "--list"])
p("=== installed")
run([SDKM, "--sdk_root=" + SDK, "--list_installed"])
p("DONE")
