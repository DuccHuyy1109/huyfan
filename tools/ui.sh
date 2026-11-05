# Tiện ích điều khiển điện thoại qua adb để test giao diện.
# Dùng: source tools/ui.sh ; tap_text "Sổ từ" ; type_text "xin chao" ; shot ten_anh
export MSYS_NO_PATHCONV=1
ADB=/c/Users/DuccHuyy/AppData/Local/Android/Sdk/platform-tools/adb.exe
SHOTS=/e/AI_hankukko/screenshots

dump() {
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  $ADB shell cat /sdcard/ui.xml > $SHOTS/ui.xml
}

# bounds_of <attr> <value> [index]  → "x y" tâm của phần tử
bounds_of() {
  local attr="$1" idx="${3:-1}"
  local val
  val=$(printf '%s' "$2" | sed 's/[][\.*^$()+?{}|]/\\&/g')
  local b
  b=$(grep -oE "$attr=\"$val\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" $SHOTS/ui.xml | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | sed -n "${idx}p")
  [ -z "$b" ] && return 1
  echo "$b" | tr '[],' '   ' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
}

tap_xy() { $ADB shell input tap $1 $2; }

# tap_text "Chữ" [thứ tự]
tap_text() {
  dump
  local xy
  xy=$(bounds_of text "$1" "$2") || { echo "!! không thấy text: $1"; return 1; }
  tap_xy $xy
}

# tap_desc "Mô tả" [thứ tự]
tap_desc() {
  dump
  local xy
  xy=$(bounds_of content-desc "$1" "$2") || { echo "!! không thấy desc: $1"; return 1; }
  tap_xy $xy
}

long_text() {
  dump
  local xy
  xy=$(bounds_of text "$1" "$2") || { echo "!! không thấy text: $1"; return 1; }
  set -- $xy
  $ADB shell input swipe $1 $2 $1 $2 900
}

# Gõ chữ không dấu (adb không gõ được tiếng Việt có dấu)
type_text() { $ADB shell input text "$(echo "$1" | sed 's/ /%s/g')"; }

focus_input() {
  dump
  local b
  b=$(grep -oE 'class="android.widget.EditText"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' $SHOTS/ui.xml | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | sed -n "${1:-1}p")
  [ -z "$b" ] && { echo "!! không thấy ô nhập"; return 1; }
  tap_xy $(echo "$b" | tr '[],' '   ' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}')
}

back() { $ADB shell input keyevent KEYCODE_BACK; }
swipe_up() { $ADB shell input swipe 540 1700 540 800 300; }
swipe_down() { $ADB shell input swipe 540 800 540 1700 300; }
shot() { $ADB exec-out screencap -p > $SHOTS/$1.png; }
texts() { dump; grep -oE 'text="[^"]{2,200}"' $SHOTS/ui.xml | sed 's/text=//' ; }

# Hỏi 1 câu trong tab Chat
ask() {
  focus_input || return 1
  sleep 0.5
  type_text "$1"
  sleep 0.5
  tap_desc "Gửi"
  back
}
