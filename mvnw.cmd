@echo off
REM Maven Wrapper for stellar-mall project
REM Uses cached Maven from %USERPROFILE%\.m2\wrapper\dists (installed by IDEs/tools)
REM Works even if Maven is NOT on the system PATH.

setlocal

set "MVN_BIN_DIR1=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin"
set "MVN_BIN_DIR2=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin"
set "MVN_BIN_DIR3=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6-bin\439sdfsg2nbdob9ciift5h5nse\apache-maven-3.9.6\bin"
set "MVN_BIN_DIR4=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5\bin"

set "MVN_CMD="
if exist "%MVN_BIN_DIR1%\mvn.cmd" ( set "MVN_CMD=%MVN_BIN_DIR1%\mvn.cmd" )
if not defined MVN_CMD if exist "%MVN_BIN_DIR2%\mvn.cmd" ( set "MVN_CMD=%MVN_BIN_DIR2%\mvn.cmd" )
if not defined MVN_CMD if exist "%MVN_BIN_DIR3%\mvn.cmd" ( set "MVN_CMD=%MVN_BIN_DIR3%\mvn.cmd" )
if not defined MVN_CMD if exist "%MVN_BIN_DIR4%\mvn.cmd" ( set "MVN_CMD=%MVN_BIN_DIR4%\mvn.cmd" )

REM Fallback: search USERPROFILE\.m2 for the newest mvn.cmd
if not defined MVN_CMD (
  for /f "delims=" %%F in ('dir /b /s /a-d "%USERPROFILE%\.m2\wrapper\dists\apache-maven-*\bin\mvn.cmd" 2^>nul ^| sort /r') do (
    if exist "%%F" (
      set "MVN_CMD=%%F"
      goto :mvn_found
    )
  )
)
:mvn_found

if not defined MVN_CMD (
  echo [mvnw] ERROR: Apache Maven not found in USERPROFILE\.m2\wrapper\dists
  echo          Please install Maven once, e.g.: winget install Apache.Maven
  echo          Or run the official mvnw wrapper once so it downloads into .m2 cache.
  exit /b 2
)

if "%JAVA_HOME%"=="" (
  echo [mvnw] WARNING: JAVA_HOME is not set. Maven may fail if no JDK on PATH.
)

echo [mvnw] Using Maven: %MVN_CMD%
call "%MVN_CMD%" %*
exit /b %ERRORLEVEL%
