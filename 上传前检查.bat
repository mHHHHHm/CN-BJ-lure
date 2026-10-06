@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================================
echo   大肥鱼历险记 - 上传 GitHub 前的自检
echo ============================================================
echo.

REM ---------- 1. git 装了吗 ----------
echo [1/4] 检查 git...
where git >nul 2>nul
if errorlevel 1 (
  echo.
  echo   X 没装 git
  echo.
  echo   去 https://git-scm.com/download/win 下载安装，
  echo   一路点 Next 就行。装完关掉所有命令行窗口，再双击本文件。
  echo.
  pause
  exit /b 1
)
for /f "tokens=*" %%v in ('git --version') do echo   OK  %%v
echo.

REM ---------- 2. 让 git 自己算 ----------
echo [2/4] 让 git 自己判断哪些文件会被提交...
if not exist ".git" (
  git init -q
  echo   （首次运行，已初始化本地仓库）
)
git add -A 2>nul
echo.

echo   --- 敏感文件检查（下面应该什么都不显示）---
git diff --cached --name-only > "%TEMP%\_bjlure_files.txt"
findstr /i /c:"keystore" /c:"local.properties" /c:"assets/tiles" /c:".apk" /c:".p12" "%TEMP%\_bjlure_files.txt" > "%TEMP%\_bjlure_bad.txt"
for %%a in ("%TEMP%\_bjlure_bad.txt") do set BADSIZE=%%~za
if not "%BADSIZE%"=="0" (
  echo.
  echo   XXXXX  有敏感文件混进来了！先别提交！
  type "%TEMP%\_bjlure_bad.txt"
  echo.
  echo   检查 .gitignore 是不是被改过。
  echo.
  pause
  exit /b 1
)
echo   OK  干净，没有敏感文件
echo.

REM ---------- 3. 统计 ----------
echo [3/4] 即将提交的内容...
set FILECOUNT=0
for /f %%c in ('type "%TEMP%\_bjlure_files.txt" ^| find /c /v ""') do set FILECOUNT=%%c
echo   文件总数：%FILECOUNT%
echo.
echo   列表（最多显示 40 条）：
set N=0
for /f "delims=" %%f in ('type "%TEMP%\_bjlure_files.txt"') do (
  set /a N+=1
  if !N! leq 40 echo     %%f
)
if %FILECOUNT% gtr 40 echo     ...（其余略）
echo.

REM ---------- 4. 结论 ----------
echo [4/4] 结论
echo.
echo   ==========================================
echo    检查通过，可以提交了
echo   ==========================================
echo.
echo   推荐走图形界面，不用敲命令：
echo.
echo     1) 装 GitHub Desktop  https://desktop.github.com
echo     2) 打开后 File ^-^> Add local repository
echo     3) 路径选：%~dp0
echo     4) 左边 Changes 里再扫一眼，确认没有 keystore
echo     5) 左下角 Summary 填 "大肥鱼历险记 v1.2"
echo     6) 点 Commit to main
echo     7) 点上方 Publish repository
echo        仓库名用英文，比如 bjlure
echo.
echo   详细图文步骤见同目录下的「上传指南.md」
echo.
pause
