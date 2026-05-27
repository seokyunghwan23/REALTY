#!/bin/bash
# IP 차단 로그 분석 스크립트

LOGS_DIR="logs/blocked-ips"
BLACKLIST_FILE="config/ip-blacklist.txt"

echo "=============================================="
echo "IP 차단 로그 분석"
echo "=============================================="
echo ""

# 1. 가장 많이 차단된 IP Top 10
echo "📊 가장 많이 차단된 IP (Top 10):"
echo "----------------------------------------------"
if [ -d "$LOGS_DIR" ]; then
    grep "BLOCKED" $LOGS_DIR/blocked-*.log 2>/dev/null | \
    grep -oP "IP=\K[\d.]+" | \
    sort | uniq -c | sort -rn | head -10 | \
    awk '{printf "%3d회  %s\n", $1, $2}'
else
    echo "로그 디렉토리 없음"
fi
echo ""

# 2. 오늘 차단된 IP
TODAY=$(date +%Y-%m-%d)
echo "📅 오늘($TODAY) 차단된 IP:"
echo "----------------------------------------------"
if [ -f "$LOGS_DIR/blocked-$TODAY.log" ]; then
    grep "BLOCKED" $LOGS_DIR/blocked-$TODAY.log | \
    grep -oP "IP=\K[\d.]+" | \
    sort | uniq -c | \
    awk '{printf "%3d회  %s\n", $1, $2}'
else
    echo "오늘 차단된 IP 없음"
fi
echo ""

# 3. 현재 블랙리스트 IP
echo "🚫 현재 블랙리스트:"
echo "----------------------------------------------"
if [ -f "$BLACKLIST_FILE" ]; then
    grep -v "^#" $BLACKLIST_FILE | grep -v "^$" | \
    awk -F',' '{printf "%-15s  (차단: %s)\n", $1, $2}'
else
    echo "블랙리스트 파일 없음"
fi
echo ""

# 4. 차단 사유별 통계
echo "📈 차단 사유별 통계:"
echo "----------------------------------------------"
if [ -d "$LOGS_DIR" ]; then
    grep "BLOCKED" $LOGS_DIR/blocked-*.log 2>/dev/null | \
    grep -oP "Reason=\K[^$]+" | \
    sort | uniq -c | sort -rn | head -5 | \
    awk '{count=$1; $1=""; printf "%3d회  %s\n", count, $0}'
else
    echo "로그 없음"
fi
echo ""

echo "=============================================="
