@echo off
chcp 65001 >nul
cd /d "%~dp0"
set REPO=https://github.com/25283531/WLED.git
set BRANCH=main

where git >nul 2>nul || (echo [错误] 未安装 git: https://git-scm.com/download/win & pause & exit /b 1)

if not exist ".git" (
  git init -b %BRANCH%
  git remote add origin %REPO%
)
git remote get-url origin >nul 2>nul || git remote add origin %REPO%

git add -A
git update-index --chmod=+x app/gradlew 2>nul
git commit -m "color-lamp: 固件适配原理图+音频律动+灯效去重; App 设置页重构+多语言(默认中文)"

git fetch origin %BRANCH% 2>nul && (
  git pull origin %BRANCH% --allow-unrelated-histories --no-edit
)
git push -u origin %BRANCH%
echo.
echo 完成。之后每次修改只需再次双击本脚本（可修改上面的 commit 说明）。
pause
