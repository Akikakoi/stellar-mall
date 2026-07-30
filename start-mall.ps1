[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$PROJECT_ROOT = $PSScriptRoot
$BACKEND_PORT = 8082
$RAG_PORT = 8000
$FRONTEND_PORT = 5173
$BACKEND_DIR = Join-Path $PROJECT_ROOT "stellar-server"
$RAG_DIR = Join-Path $PROJECT_ROOT "rag-backend"
$FRONTEND_DIR = Join-Path $PROJECT_ROOT "frontend"

# ---------- 杈撳嚭杈呭姪 ----------
function Write-Info { param([string]$Message) Write-Host $Message -ForegroundColor Gray }
function Write-Ok { param([string]$Message) Write-Host $Message -ForegroundColor Green }
function Write-Warn { param([string]$Message) Write-Host $Message -ForegroundColor Yellow }
function Write-Err { param([string]$Message) Write-Host $Message -ForegroundColor Red }

# ---------- 绔彛鍗犵敤妫€娴嬶紙瀹夊叏鐗堟湰锛?----------
function Stop-ProcessByPort {
    param([int]$Port, [string[]]$AllowedPatterns)
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $conn) { return }

    $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
    if (-not $proc) {
        Write-Warn "Port $Port is occupied but process not found, skipping"
        return
    }

    try {
        $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId=$($proc.Id)").CommandLine
    } catch {
        $cmdLine = ""
    }

    $isAllowed = $false
    foreach ($pat in $AllowedPatterns) {
        if ($cmdLine -like "*$pat*") { $isAllowed = $true; break }
    }

    if (-not $isAllowed) {
        Write-Err "Port $Port is occupied by an unknown process (PID=$($proc.Id)): $cmdLine"
        Write-Err "Please free port $Port manually, or confirm this process can be terminated."
        exit 1
    }

    Write-Warn "Port $Port is used by PID=$($proc.Id), killing allowed process..."
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    if (-not (Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue)) {
        Write-Ok "Port $Port released"
    } else {
        Write-Warn "Port $Port still occupied after kill attempt"
    }
}

function Test-Port {
    param([int]$Port)
    try {
        $tcp = New-Object System.Net.Sockets.TCPClient
        $tcp.Connect("127.0.0.1", $Port)
        $tcp.Close()
        return $true
    } catch {
        return $false
    }
}

function Wait-ForPort {
    param([int]$Port, [string]$Name, [int]$MaxSeconds = 120)
    Write-Host "Waiting for $Name (port $Port)..." -ForegroundColor Gray -NoNewline
    for ($i = 0; $i -lt $MaxSeconds; $i++) {
        if (Test-Port -Port $Port) {
            Write-Host " started!" -ForegroundColor Green
            return $true
        }
        Start-Sleep -Seconds 1
        Write-Host "." -ForegroundColor Gray -NoNewline
    }
    Write-Host " timeout!" -ForegroundColor Red
    return $false
}

# ---------- 閰嶇疆涓€鑷存€ф牎楠?----------
function Get-YamlValue {
    param([string]$Path, [string]$Key)
    if (-not (Test-Path $Path)) { return $null }
    $lines = Get-Content $Path
    # 支持点号路径，如 stellar.jwt.admin-secret-key 只匹配最后一级 key
    $leafKey = $Key.Split('.')[-1]
    $regex = "^\s*$([regex]::Escape($leafKey))\s*[:=]\s*(.+?)\s*$"
    foreach ($line in $lines) {
        if ($line -match $regex) {
            $val = $Matches[1] -replace '^["'']' -replace '["'']$'
            return $val
        }
    }
    return $null
}

function Get-DotEnvValue {
    param([string]$Path, [string]$Key)
    if (-not (Test-Path $Path)) { return $null }
    $lines = Get-Content $Path
    $regex = "^\s*$([regex]::Escape($Key))\s*=\s*(.+?)\s*$"
    foreach ($line in $lines) {
        if ($line -match $regex) {
            $val = $Matches[1] -replace '^["'']' -replace '["'']$'
            return $val
        }
    }
    return $null
}

function Test-ConfigConsistency {
    $javaConfig = Join-Path $BACKEND_DIR "src\main\resources\application-dev.yml"
    $ragEnv = Join-Path $RAG_DIR ".env"

    Write-Info "Checking configuration files..."

    if (-not (Test-Path $javaConfig)) {
        Write-Err "Java config not found: $javaConfig"
        Write-Err "Please copy from application-dev.yml.example and fill in correct values."
        exit 1
    }
    if (-not (Test-Path $ragEnv)) {
        Write-Err "RAG backend .env not found: $ragEnv"
        Write-Err "Please copy from .env.example and fill in correct values."
        exit 1
    }

    # 璇诲彇鍏抽敭瀵嗛挜
    $javaAdminKey = Get-YamlValue $javaConfig "stellar.jwt.admin-secret-key"
    $javaUserKey  = Get-YamlValue $javaConfig "stellar.jwt.user-secret-key"
    $javaSyncSec  = Get-YamlValue $javaConfig "stellar.rag.internal-sync-secret"
    $ragAdminKey  = Get-DotEnvValue $ragEnv "STELLAR_ADMIN_SECRET_KEY"
    $ragUserKey   = Get-DotEnvValue $ragEnv "STELLAR_USER_SECRET_KEY"
    $ragSyncSec   = Get-DotEnvValue $ragEnv "STELLAR_RAG_INTERNAL_SYNC_SECRET"
    $ragMallUrl   = Get-DotEnvValue $ragEnv "MALL_API_BASE_URL"

    $errors = 0
    if ($javaAdminKey -ne $ragAdminKey) {
        Write-Err "JWT admin secret mismatch: Java vs RAG .env STELLAR_ADMIN_SECRET_KEY"
        $errors++
    }
    if ($javaUserKey -ne $ragUserKey) {
        Write-Err "JWT user secret mismatch: Java vs RAG .env STELLAR_USER_SECRET_KEY"
        $errors++
    }
    if ($javaSyncSec -ne $ragSyncSec) {
        Write-Err "Internal sync secret mismatch: Java vs RAG .env STELLAR_RAG_INTERNAL_SYNC_SECRET"
        $errors++
    }

    $expectedMallUrl = "http://127.0.0.1:$BACKEND_PORT"
    if ($ragMallUrl -and $ragMallUrl -ne $expectedMallUrl) {
        Write-Warn "MALL_API_BASE_URL=$ragMallUrl, but Java backend port is $BACKEND_PORT"
        Write-Warn "Expected: $expectedMallUrl"
    }

    # 寮卞瘑閽ユ彁绀
    @($javaAdminKey, $javaUserKey, $ragAdminKey, $ragUserKey) | ForEach-Object {
        if ($_ -and $_.Length -lt 32) {
            Write-Warn "JWT secret is too short (< 32 chars), please use a stronger key in production"
        }
    }

    if ($errors -gt 0) {
        Write-Err "Configuration consistency check failed. RAG backend will return 401/500 if keys mismatch."
        exit 1
    }

    Write-Ok "Configuration consistency check passed"
}

# ---------- 渚濊禆鏈嶅姟妫€娴?----------
function Test-DependencyServices {
    $javaConfig = Join-Path $BACKEND_DIR "src\main\resources\application-dev.yml"

    # 榛樿绔彛锛屽皾璇曚粠閰嶇疆璇诲彇
    $mysqlHost = "127.0.0.1"
    $mysqlPort = 3306
    $redisHost = "127.0.0.1"
    $redisPort = 6379

    $dbUrl = Get-YamlValue $javaConfig "spring.datasource.url"
    if ($dbUrl -match 'jdbc:mysql://([^:/]+)(?::(\d+))?') {
        $mysqlHost = $Matches[1]
        if ($Matches[2]) { $mysqlPort = [int]$Matches[2] }
    }

    $redisHostVal = Get-YamlValue $javaConfig "spring.redis.host"
    $redisPortVal = Get-YamlValue $javaConfig "spring.redis.port"
    if ($redisHostVal) { $redisHost = $redisHostVal }
    if ($redisPortVal) { $redisPort = [int]$redisPortVal }

    Write-Info "Checking MySQL at ${mysqlHost}:${mysqlPort}..."
    if (-not (Test-Port -Port $mysqlPort)) {
        Write-Err "MySQL is not reachable at ${mysqlHost}:${mysqlPort}"
        Write-Err "Please start MySQL and create database 'stellar_mall' before running this script."
        exit 1
    }
    Write-Ok "MySQL is reachable"

    Write-Info "Checking Redis at ${redisHost}:${redisPort}..."
    if (-not (Test-Port -Port $redisPort)) {
        Write-Warn "Redis is not reachable at ${redisHost}:${redisPort}"
        Write-Warn "Java backend will auto-degrade to local cache, but RAG/session features may be limited."
    } else {
        Write-Ok "Redis is reachable"
    }
}

# ---------- 涓绘祦绋?----------
try {
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host "     Stellar Mall Quick Start" -ForegroundColor Cyan
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Info "Checking environment..."

    if (-not (Test-Path $BACKEND_DIR)) {
        Write-Err "Backend dir not found: $BACKEND_DIR"
        exit 1
    }
    if (-not (Test-Path $FRONTEND_DIR)) {
        Write-Err "Frontend dir not found: $FRONTEND_DIR"
        exit 1
    }

    Test-ConfigConsistency
    Test-DependencyServices

    Stop-ProcessByPort -Port $BACKEND_PORT -AllowedPatterns @("java", "maven", "mvnw")
    Stop-ProcessByPort -Port $RAG_PORT -AllowedPatterns @("python", "uvicorn")
    Stop-ProcessByPort -Port $FRONTEND_PORT -AllowedPatterns @("node", "npm", "vite")

    Write-Host ""
    Write-Host "===== Installing Java dependencies =====" -ForegroundColor Cyan
    $env:MAVEN_OPTS = "-Xms512m -Xmx1024m"
    & "$PROJECT_ROOT\mvnw.cmd" install -pl stellar-pojo,stellar-common -DskipTests -q
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Java dependency install failed!"
        exit 1
    }
    Write-Ok "Java dependencies installed"

    Write-Host ""
    Write-Host "===== Starting Java Backend (Port: $BACKEND_PORT) =====" -ForegroundColor Cyan
    # 阿里云 OSS 凭据（通过环境变量注入，不写入配置文件）
    $env:STELLAR_OSS_ACCESS_KEY_ID = $env:STELLAR_OSS_ACCESS_KEY_ID
    $env:STELLAR_OSS_ACCESS_KEY_SECRET = $env:STELLAR_OSS_ACCESS_KEY_SECRET
    $backendCmd = "Set-Location `"$BACKEND_DIR`"; & `"$PROJECT_ROOT\mvnw.cmd`" spring-boot:run"
    $backendProc = Start-Process -FilePath powershell.exe `
        -ArgumentList "-NoExit", "-Command", $backendCmd `
        -WindowStyle Minimized -PassThru

    if (-not (Wait-ForPort -Port $BACKEND_PORT -Name "Java Backend" -MaxSeconds 180)) {
        Write-Err "Java backend startup timeout, check the backend PowerShell window for errors"
        exit 1
    }
    Write-Ok "API: http://localhost:$BACKEND_PORT"
    Write-Ok "Swagger: http://localhost:$BACKEND_PORT/doc.html"

    Write-Host ""
    Write-Host "===== Starting RAG Backend (Port: $RAG_PORT) =====" -ForegroundColor Cyan
    if (Test-Path $RAG_DIR) {
        $ragCmd = "Set-Location `"$RAG_DIR`"; python -m uvicorn app.main:app --host 0.0.0.0 --port $RAG_PORT"
        $ragProc = Start-Process -FilePath powershell.exe `
            -ArgumentList "-NoExit", "-Command", $ragCmd `
            -WindowStyle Minimized -PassThru

        Wait-ForPort -Port $RAG_PORT -Name "RAG Backend" -MaxSeconds 60 | Out-Null
        Write-Ok "RAG Backend: http://localhost:$RAG_PORT"
        Write-Ok "Docs: http://localhost:$RAG_PORT/docs"
    } else {
        Write-Warn "Skipping RAG backend (directory not found)"
    }

    Write-Host ""
    Write-Host "===== Starting Frontend (Port: $FRONTEND_PORT) =====" -ForegroundColor Cyan
    $frontendCmd = "Set-Location `"$FRONTEND_DIR`"; npm run dev"
    $frontendProc = Start-Process -FilePath powershell.exe `
        -ArgumentList "-NoExit", "-Command", $frontendCmd `
        -WindowStyle Minimized -PassThru

    if (-not (Wait-ForPort -Port $FRONTEND_PORT -Name "Frontend" -MaxSeconds 60)) {
        Write-Err "Frontend startup timeout, check the frontend PowerShell window for errors"
        exit 1
    }

    Write-Host ""
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host "     All Services Started" -ForegroundColor Cyan
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Ok "Java Backend: http://localhost:$BACKEND_PORT"
    Write-Ok "RAG Backend:  http://localhost:$RAG_PORT"
    Write-Ok "Frontend:     http://localhost:$FRONTEND_PORT"
    Write-Ok "Admin:        http://localhost:$FRONTEND_PORT/admin/login"
    Write-Host ""
    Write-Info "Opening browser..."
    Start-Process "http://localhost:$FRONTEND_PORT"

    Write-Host ""
    Write-Info "Press any key to stop all services..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

    Write-Host ""
    Write-Info "Stopping services..."
    if ($backendProc) { Stop-Process -Id $backendProc.Id -Force -ErrorAction SilentlyContinue }
    if ($ragProc)      { Stop-Process -Id $ragProc.Id -Force -ErrorAction SilentlyContinue }
    if ($frontendProc){ Stop-Process -Id $frontendProc.Id -Force -ErrorAction SilentlyContinue }

    Start-Sleep -Seconds 1
    Stop-ProcessByPort -Port $BACKEND_PORT -AllowedPatterns @("java", "maven", "mvnw")
    Stop-ProcessByPort -Port $RAG_PORT -AllowedPatterns @("python", "uvicorn")
    Stop-ProcessByPort -Port $FRONTEND_PORT -AllowedPatterns @("node", "npm", "vite")

    Write-Ok "All services stopped"
} catch {
    Write-Err "ERROR: $_"
    Write-Host $_.ScriptStackTrace -ForegroundColor DarkGray
    Read-Host "Press Enter to exit"
}
