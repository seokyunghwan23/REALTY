# IP 차단 로그 분석 PowerShell 스크립트

$LogsDir = "logs\blocked-ips"
$BlacklistFile = "config\ip-blacklist.txt"

Write-Host "=============================================="
Write-Host "IP 차단 로그 분석"
Write-Host "=============================================="
Write-Host ""

# 1. 가장 많이 차단된 IP Top 10
Write-Host "📊 가장 많이 차단된 IP (Top 10):"
Write-Host "----------------------------------------------"
if (Test-Path $LogsDir) {
    Get-Content "$LogsDir\blocked-*.log" -ErrorAction SilentlyContinue |
    Select-String "IP=(\d+\.\d+\.\d+\.\d+)" |
    ForEach-Object { $_.Matches.Groups[1].Value } |
    Group-Object |
    Sort-Object Count -Descending |
    Select-Object -First 10 |
    ForEach-Object {
        Write-Host ("{0,3}회  {1}" -f $_.Count, $_.Name)
    }
} else {
    Write-Host "로그 디렉토리 없음"
}
Write-Host ""

# 2. 오늘 차단된 IP
$Today = Get-Date -Format "yyyy-MM-dd"
Write-Host "📅 오늘($Today) 차단된 IP:"
Write-Host "----------------------------------------------"
$TodayLog = "$LogsDir\blocked-$Today.log"
if (Test-Path $TodayLog) {
    Get-Content $TodayLog |
    Select-String "IP=(\d+\.\d+\.\d+\.\d+)" |
    ForEach-Object { $_.Matches.Groups[1].Value } |
    Group-Object |
    Sort-Object Count -Descending |
    ForEach-Object {
        Write-Host ("{0,3}회  {1}" -f $_.Count, $_.Name)
    }
} else {
    Write-Host "오늘 차단된 IP 없음"
}
Write-Host ""

# 3. 현재 블랙리스트 IP
Write-Host "🚫 현재 블랙리스트:"
Write-Host "----------------------------------------------"
if (Test-Path $BlacklistFile) {
    Get-Content $BlacklistFile |
    Where-Object { $_ -notmatch "^#" -and $_ -ne "" } |
    ForEach-Object {
        $parts = $_ -split ","
        $ip = $parts[0]
        $time = if ($parts.Length -gt 1) { $parts[1] } else { "N/A" }
        Write-Host ("{0,-15}  (차단: {1})" -f $ip, $time)
    }
} else {
    Write-Host "블랙리스트 파일 없음"
}
Write-Host ""

# 4. 차단 사유별 통계
Write-Host "📈 차단 사유별 통계:"
Write-Host "----------------------------------------------"
if (Test-Path $LogsDir) {
    Get-Content "$LogsDir\blocked-*.log" -ErrorAction SilentlyContinue |
    Select-String "Reason=(.+)" |
    ForEach-Object { $_.Matches.Groups[1].Value } |
    Group-Object |
    Sort-Object Count -Descending |
    Select-Object -First 5 |
    ForEach-Object {
        Write-Host ("{0,3}회  {1}" -f $_.Count, $_.Name)
    }
} else {
    Write-Host "로그 없음"
}
Write-Host ""

Write-Host "=============================================="

# 5. 자동으로 블랙리스트 추가할 IP 추천 (10회 이상 차단된 IP)
Write-Host ""
Write-Host "💡 블랙리스트 추가 추천 (10회 이상 차단된 IP):"
Write-Host "----------------------------------------------"
if (Test-Path $LogsDir) {
    $currentBlacklist = @()
    if (Test-Path $BlacklistFile) {
        $currentBlacklist = Get-Content $BlacklistFile |
        Where-Object { $_ -notmatch "^#" -and $_ -ne "" } |
        ForEach-Object { ($_ -split ",")[0] }
    }

    Get-Content "$LogsDir\blocked-*.log" -ErrorAction SilentlyContinue |
    Select-String "IP=(\d+\.\d+\.\d+\.\d+)" |
    ForEach-Object { $_.Matches.Groups[1].Value } |
    Group-Object |
    Where-Object { $_.Count -ge 10 } |
    Sort-Object Count -Descending |
    ForEach-Object {
        if ($currentBlacklist -notcontains $_.Name) {
            $timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ss"
            Write-Host ("{0,3}회  {1}  → 추가 명령: {2},{3}" -f $_.Count, $_.Name, $_.Name, $timestamp)
        }
    }
}
Write-Host ""
