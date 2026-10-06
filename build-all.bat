@echo off
setlocal
node scripts\build-all.mjs
exit /b %errorlevel%
