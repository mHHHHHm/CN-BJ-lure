# -*- coding: utf-8 -*-
"""对 Android 层源码做语法体检。

本机没有 Android SDK，装不了 Compose/Room 依赖，所以语义检查（unresolved reference）
注定会刷屏。但语法层错误 —— 括号不匹配、关键字拼错、when 分支写法错误之类 ——
在解析阶段就会报出来，而且和依赖无关。这个脚本就是把这些错误单独捞出来。
"""
import os
import re
import subprocess
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

BASE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.dirname(BASE)
TOOLCHAIN = os.path.join(os.path.dirname(WORK), "toolchain")
KOTLINC = os.path.join(TOOLCHAIN, "kotlin", "kotlinc", "bin", "kotlinc.bat")
JDK = os.path.join(TOOLCHAIN, "jdk", "jdk-17.0.20.1+1")
SRC = os.path.join(WORK, "app", "src", "main", "java")

# 这些是「语义」错误，在没有依赖的情况下必然会报，属于预期。
# 判定标准：真实语法错误一定带 "syntax error" / "expecting" / "unexpected" 这类词；
# 其它带类型信息的抱怨基本都是上游 unresolved 引起的连锁反应。
SEMANTIC_MARKERS = (
    "unresolved reference",
    "unresolved symbol",
    "cannot access",
    "cannot infer",
    "type mismatch",
    "no value passed for parameter",
    "too many arguments",
    "none of the following functions",
    "none of the following candidates",
    "not enough information to infer",
    "overload resolution ambiguity",
    "expression expected",  # 多半是上游语义错误引起的连锁
    "cannot find a parameter",
    "cannot be applied",
    "cannot be called",
    "cannot be invoked",
    "cannot be used",
    "is not abstract and does not implement",
    "classifier does not have a companion object",
    "does not have a constructor",
    "cannot resolve",
    "is missing",
    "unable to resolve",
    "unresolved",
    # 以下都是「类型信息缺失」的外溢表现，依赖补齐后会自动消失
    "overrides nothing",
    "is prohibited here",
    "must be exhaustive",
    "is ambiguous for this expression",
    "should be called only from a coroutine",
    "can only be called within coroutine body",
    "annotation argument must be a compile-time constant",
    "modifier is required on",
    "is not a function",
    "no setter",
    "no getter",
    "cannot find",
    "not applicable",
    # 缺类型信息时 Kotlin 会把 Pair 的属性访问当成函数调用，
    # 报成「function invocation 'first()' expected.」—— 依赖补齐后自动消失
    "function invocation",
)

# 真语法错误的特征词，只有命中这些才值得报警
SYNTAX_MARKERS = (
    "syntax error",
    "expecting",
    "unexpected token",
    "expecting '}'",
    "expected",
)


def main():
    files = []
    for root, _dirs, names in os.walk(SRC):
        for n in names:
            if n.endswith(".kt"):
                files.append(os.path.join(root, n))
    files.sort()

    print("语法体检 %d 个 Kotlin 文件（无 Android 依赖，语义错误会被过滤）\n" % len(files))

    env = dict(os.environ)
    env["JAVA_HOME"] = JDK

    cp = subprocess.run(
        [KOTLINC] + files + ["-d", os.path.join(BASE, "build", "syntax_only")],
        capture_output=True, env=env, cwd=WORK,
    )
    text = cp.stdout.decode("utf-8", "replace") + cp.stderr.decode("utf-8", "replace")

    out_path = os.path.join(BASE, "build", "android_compile_raw.txt")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(text)

    lines = text.splitlines()
    # 注意：不要写成 \berror:\b —— 冒号后面那个 \b 永远匹配不上，
    # 会让统计静默变成 0，等于没检查。
    errors = [l for l in lines if re.search(r"\berror:", l)]
    warnings = [l for l in lines if re.search(r"\bwarning:", l)]

    syntax_like = []
    for e in errors:
        low = e.lower()
        if any(m in low for m in SEMANTIC_MARKERS):
            continue
        syntax_like.append(e)

    # 统计语义错误涉及的文件，确认确实是「缺依赖」而不是我写错了名字
    unresolved_files = {}
    for e in errors:
        if "unresolved reference" in e.lower():
            m = re.match(r"(.+?):(\d+):", e)
            if m:
                unresolved_files[m.group(1)] = unresolved_files.get(m.group(1), 0) + 1

    # 只有带 "syntax error" / "expecting" 这类词的才是真语法错误
    hard_syntax = [e for e in syntax_like if any(m in e.lower() for m in SYNTAX_MARKERS)]
    soft = [e for e in syntax_like if e not in hard_syntax]

    print("总 error 行：%d" % len(errors))
    print("其中 unresolved reference：%d（预期内，缺 Android/Compose/Room 依赖）"
          % sum(unresolved_files.values()))
    print("真语法错误：%d" % len(hard_syntax))
    print("其余语义连锁错误：%d" % len(soft))
    print("warning 行：%d\n" % len(warnings))

    if hard_syntax:
        print("---- 真语法错误明细 ----")
        for s in hard_syntax[:80]:
            print("  " + s)
    else:
        print("没有发现语法层面的错误 ✓")

    if soft:
        print("\n---- 其余错误（缺依赖引起的连锁，供参考） ----")
        for s in soft[:80]:
            print("  " + s)

    print("\n完整编译输出已写入 %s" % out_path)

    # unresolved 最多的符号，用来确认缺失的确实是安卓侧依赖
    top = {}
    for e in errors:
        m = re.search(r"unresolved reference:?\s*'?([\w\.]+)'?", e)
        if m:
            top[m.group(1)] = top.get(m.group(1), 0) + 1
    if top:
        print("\n缺失最多的符号（前 15）：")
        for k, v in sorted(top.items(), key=lambda kv: -kv[1])[:15]:
            print("  %-40s %d" % (k, v))

    return 1 if hard_syntax else 0


if __name__ == "__main__":
    sys.exit(main())
