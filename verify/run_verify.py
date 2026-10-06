# -*- coding: utf-8 -*-
"""一键离线验证：用便携 JDK + kotlinc 编译领域逻辑与测试，跑出结果。

不用 Gradle、不用 Android SDK —— 目的只是让核心业务规则在进 APK 之前
就已经在真实编译器下验证过一遍。
"""
import os
import subprocess
import sys

# Windows 控制台常是 GBK，测试输出里有 ✓ 等符号，先兜住编码
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

BASE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.dirname(BASE)
TOOLCHAIN = os.path.join(os.path.dirname(WORK), "toolchain")

JDK = os.path.join(TOOLCHAIN, "jdk", "jdk-17.0.20.1+1")
KOTLINC = os.path.join(TOOLCHAIN, "kotlin", "kotlinc", "bin", "kotlinc.bat")
DOMAIN_SRC = os.path.join(WORK, "app", "src", "main", "java", "com", "bjlure", "app", "domain")
TEST_SRC = os.path.join(BASE, "src", "DomainTests.kt")
OUT = os.path.join(BASE, "build")
JAR = os.path.join(OUT, "verify.jar")
RESULT = os.path.join(OUT, "test_result.txt")


def main():
    if not os.path.exists(KOTLINC):
        print("找不到 kotlinc：%s" % KOTLINC)
        return 2
    os.makedirs(OUT, exist_ok=True)

    sources = []
    for root, _dirs, files in os.walk(DOMAIN_SRC):
        for f in files:
            if f.endswith(".kt"):
                sources.append(os.path.join(root, f))
    sources.append(TEST_SRC)

    env = dict(os.environ)
    env["JAVA_HOME"] = JDK

    print("编译 %d 个 Kotlin 文件 ..." % len(sources))
    cp = subprocess.run(
        [KOTLINC] + sources + ["-include-runtime", "-d", JAR] + ["-nowarn"],
        capture_output=True, env=env, cwd=WORK,
    )
    compile_out = cp.stdout.decode("utf-8", "replace") + cp.stderr.decode("utf-8", "replace")
    if compile_out.strip():
        print(compile_out.strip())
    if not os.path.exists(JAR):
        print("!! 编译失败")
        return 1
    print("编译通过 -> %s (%.1f KB)" % (JAR, os.path.getsize(JAR) / 1024))

    print("运行验证套件 ...\n")
    rp = subprocess.run(
        [os.path.join(JDK, "bin", "java.exe"), "-Dfile.encoding=UTF-8", "-jar", JAR],
        capture_output=True, cwd=WORK,
    )
    out = rp.stdout.decode("utf-8", "replace")
    err = rp.stderr.decode("utf-8", "replace")
    with open(RESULT, "w", encoding="utf-8") as f:
        f.write(out)
        if err.strip():
            f.write("\n--- stderr ---\n" + err)
    print(out)
    if err.strip():
        print("--- stderr ---")
        print(err)
    print("结果已写入 %s" % RESULT)
    return rp.returncode


if __name__ == "__main__":
    sys.exit(main())
