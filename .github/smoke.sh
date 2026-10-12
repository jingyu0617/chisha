#!/bin/bash
# 1) 每个主要页面截图（浅色、深色），放到 shots/ 给人看
# 2) 随机点 800 下（含返回键），看会不会崩
adb install -r out/app.apk
adb logcat -c
adb shell pm grant app.chisha android.permission.POST_NOTIFICATIONS || true
mkdir -p shots
# 等模拟器桌面完全起来，避免“桌面无响应”弹窗
sleep 40
adb shell input keyevent 3
sleep 5

# 按屏幕上的文字（或读屏说明）找到控件并点击；可以给多个候选，点第一个找到的
tap_text() {
  clear_dialogs
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local b
  b=$(python3 - "$@" <<'EOF'
import re, sys
x = open('/tmp/ui.xml', encoding='utf-8').read()
nodes = re.findall(r'<node [^>]*>', x)
for t in sys.argv[1:]:
    for n in nodes:
        if f'text="{t}"' in n or f'content-desc="{t}"' in n:
            a = list(map(int, re.findall(r'\d+', re.search(r'bounds="([^"]+)"', n).group(1))))
            print((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
            sys.exit()
EOF
)
  if [ -n "$b" ]; then adb shell input tap $b; sleep 2; else echo "找不到：$*"; fi
}
# 模拟器自己的“桌面无响应”之类系统弹窗：点“等待”关掉
clear_dialogs() {
  for i in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
    grep -q "isn't responding\|没有响应" /tmp/ui.xml 2>/dev/null || return 0
    b=$(python3 -c "
import re
x=open('/tmp/ui.xml',encoding='utf-8').read()
for n in re.findall(r'<node [^>]*>',x):
    if 'text=\"Wait\"' in n or 'text=\"等待\"' in n:
        a=list(map(int,re.findall(r'\\d+',re.search(r'bounds=\"([^\"]+)\"',n).group(1))));print((a[0]+a[2])//2,(a[1]+a[3])//2);break
")
    [ -n "$b" ] && adb shell input tap $b
    sleep 2
  done
}
shot() { clear_dialogs; adb exec-out screencap -p > "shots/$1.png"; }

shoot_all() {
  local m=$1
  adb shell am force-stop app.chisha
  adb shell am start -n app.chisha/.MainActivity
  sleep 8
  shot "$m-01-冰箱"
  adb shell input swipe 540 1700 540 700 500; sleep 2; shot "$m-02-冰箱-往下滑"
  adb shell input tap 400 210; sleep 2; adb shell input text "egg"; sleep 2; shot "$m-02b-搜索展开"
  adb shell input keyevent 111; sleep 1          # 收起键盘（ESC，不会退出 App）
  tap_text "返回"; sleep 1
  adb shell am start -n app.chisha/.MainActivity >/dev/null; sleep 2   # 保证 App 在前台
  tap_text "做什么"; shot "$m-03-做什么"
  tap_text "清单"
  if [ "$m" = "浅色" ]; then
    tap_text "要买什么"; adb shell input text "milk%sbeef%sapple"; adb shell input keyevent 66; sleep 1
    adb shell input keyevent 4; sleep 1
  fi
  shot "$m-04-清单"
  tap_text "抽签";   shot "$m-05-抽签"
  tap_text "菜谱";   shot "$m-06-菜谱"
  tap_text "京酱肉丝" "农家一碗香" "凉拌鸡丝" "番茄炒蛋"; sleep 1; shot "$m-07-菜谱详情"
  adb shell input swipe 540 1800 540 500 600; sleep 2; shot "$m-07b-菜谱详情-往下"
  adb shell input keyevent 4; sleep 2
  tap_text "冰箱"
  tap_text "设置";   shot "$m-08-设置"
  adb shell input keyevent 4; sleep 2
  tap_text "更多操作"; shot "$m-09-加号菜单"
  tap_text "添加食材"; sleep 1; shot "$m-10-添加食材"
  adb shell input keyevent 4; sleep 2
}

adb shell cmd uimode night no
shoot_all "浅色"
adb shell cmd uimode night yes
shoot_all "深色"
adb shell cmd uimode night no

adb shell am force-stop app.chisha
adb shell am start -n app.chisha/.MainActivity
sleep 8
adb shell monkey -p app.chisha --throttle 120 --pct-syskeys 4 --pct-appswitch 0 -s 42 -v 800 > monkey.log 2>&1 || true
sleep 3
adb logcat -d > logcat.txt
if grep -q "FATAL EXCEPTION" logcat.txt || grep -q "// CRASH" monkey.log; then
  { grep -A30 "FATAL EXCEPTION" logcat.txt | head -60; grep -A20 "// CRASH" monkey.log | head -40; } > crash.txt
  exit 1
fi
echo "smoke ok: $(grep -c 'Sending' monkey.log) events"
