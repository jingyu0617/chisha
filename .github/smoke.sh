#!/bin/bash
# 装到模拟器上，启动后随机点 800 下，看会不会崩
adb install -r out/app.apk
adb logcat -c
adb shell am start -n app.chisha/.MainActivity
sleep 10
adb shell monkey -p app.chisha --throttle 120 --pct-syskeys 0 --pct-appswitch 0 -s 42 -v 800 > monkey.log 2>&1 || true
sleep 3
adb logcat -d > logcat.txt
if grep -q "FATAL EXCEPTION" logcat.txt || grep -q "// CRASH" monkey.log; then
  { grep -A30 "FATAL EXCEPTION" logcat.txt | head -60; grep -A20 "// CRASH" monkey.log | head -40; } > crash.txt
  exit 1
fi
echo "smoke ok: $(grep -c 'Sending' monkey.log) events"
