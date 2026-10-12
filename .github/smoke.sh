#!/bin/bash
# 装到模拟器上，启动后随机点 800 下，看会不会崩
adb install -r out/app.apk
adb logcat -c
adb shell pm grant app.chisha android.permission.POST_NOTIFICATIONS || true
adb shell am start -n app.chisha/.MainActivity
sleep 10
adb shell monkey -p app.chisha --throttle 120 --pct-syskeys 4 --pct-appswitch 0 -s 42 -v 800 > monkey.log 2>&1 || true
sleep 2
# 第二轮：苹果外观
adb shell am force-stop app.chisha
adb shell am start -n app.chisha/.MainActivity --es skin apple
sleep 8
adb shell monkey -p app.chisha --throttle 120 --pct-syskeys 4 --pct-appswitch 0 -s 7 -v 800 >> monkey.log 2>&1 || true
sleep 3
adb logcat -d > logcat.txt
if grep -q "FATAL EXCEPTION" logcat.txt || grep -q "// CRASH" monkey.log; then
  { grep -A30 "FATAL EXCEPTION" logcat.txt | head -60; grep -A20 "// CRASH" monkey.log | head -40; } > crash.txt
  exit 1
fi
echo "smoke ok: $(grep -c 'Sending' monkey.log) events"
