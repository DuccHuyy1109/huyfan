# HUY FAN – Trợ lý ảo học tiếng Hàn (Android)

Gọi **“HUY FAN”** → app đáp **“HUY FAN đây!”** → hỏi bằng giọng nói: *“Rửa bát tiếng Hàn là gì?”*
→ HUY FAN trả lời *“Rửa bát là 설거지하다 nha ông!”*, hiện bảng **Tiếng Việt | Tiếng Hàn | Hán Hàn | Câu mẫu**,
đọc to bằng giọng Việt + Hàn và **tự lưu từ vào sổ theo ngày** (có thể đồng bộ lên Google Sheets).

## Tính năng

| Nhóm | Chi tiết |
|---|---|
| Gọi bằng giọng nói | Từ khoá “HUY FAN” nhận diện **offline** (Vosk, không gửi âm thanh lên mạng). Chỉ chạy khi bạn bật → không hao pin vô ích, tự tắt sau X phút. |
| Phím tắt khởi động | Phím sườn Samsung (nhấn 2 lần → “Hỏi HUY FAN”), trợ lý kỹ thuật số (giữ phím sườn / vuốt góc), ô Cài đặt nhanh, widget, lối tắt, nút tai nghe Bluetooth. |
| Từ vựng | Bảng Tiếng Việt / Tiếng Hàn (+ phiên âm) / Hán Hàn (+ âm Hán Việt) / Câu mẫu (+ nghĩa). Chạm để nghe phát âm. |
| Sổ từ theo ngày | Nhóm theo ngày, tìm kiếm, đánh dấu sao, mức thuộc bài, xuất Excel/CSV, đồng bộ Google Sheets. |
| Ôn tập | Flashcard lật 3D, đổi chiều Hàn↔Việt, xáo trộn, chấm nhớ/chưa nhớ; hoặc để HUY FAN đố bằng giọng nói. |
| Hội thoại | Dịch câu, giải thích ngữ pháp, luyện nói tiếng Hàn (nút **한** để nghe tiếng Hàn), trò chuyện liên tục rảnh tay. |
| Phong cách | Bạn thân (“Sao vậy ông?”, “Danh sách của ông đây!”), Bro, hoặc Gia sư. |
| Cài đặt | Link API, API key, model, prompt (sửa được), giọng đọc, tốc độ, từ khoá đánh thức, pin, giao diện tối/sáng, lịch sử. |
| AI miễn phí | **Gemini** (mặc định, tự chọn model Flash mới nhất, tắt “suy nghĩ” cho nhanh) hoặc bất kỳ API chuẩn OpenAI: **Groq**, OpenRouter, Ollama… Có API dự phòng khi hết hạn mức. |

## Cài lên điện thoại

File APK: `app/build/outputs/apk/release/app-release.apk`

**Cách 1 – cắm cáp (khuyên dùng, cập nhật cũng tiện):**
1. Trên Samsung: *Cài đặt → Thông tin điện thoại → Thông tin phần mềm* → chạm **Số hiệu bản tạo** 7 lần.
2. *Cài đặt → Tùy chọn nhà phát triển* → bật **Gỡ lỗi USB**.
3. Cắm cáp vào máy tính, trên điện thoại chọn **Cho phép** gỡ lỗi USB.
4. Trên máy tính:
   ```bash
   C:/Users/DuccHuyy/AppData/Local/Android/Sdk/platform-tools/adb.exe install -r app/build/outputs/apk/release/app-release.apk
   ```

**Cách 2:** chép file `.apk` sang điện thoại rồi mở để cài (cho phép “Cài ứng dụng không rõ nguồn gốc”).

## Thiết lập lần đầu (2 phút)

1. Mở app → cho phép **Micro** và **Thông báo**.
2. Lấy **Gemini API key miễn phí**: https://aistudio.google.com/apikey → *Create API key* → copy.
   Vào **Cài đặt → AI & API** dán key → bấm **Kiểm tra kết nối**.
3. **Cài đặt → Quyền & trạng thái**: bật *Hiển thị trên ứng dụng khác* (để bảng HUY FAN bật lên khi bạn gọi, kể cả màn hình khoá) và *Không tối ưu pin*.
4. Nên dùng **Google Text-to-speech** và tải giọng **Tiếng Việt + Tiếng Hàn** (mục “Giọng đọc Việt & Hàn” có nút tải).

## Khởi động bằng phím tắt (tiết kiệm pin)

- **Phím sườn**: *Cài đặt → Tính năng nâng cao → Phím bên → Nhấn hai lần → Mở ứng dụng → **Hỏi HUY FAN***.
  Nhấn đúp phím sườn là HUY FAN bật lên và nghe ngay.
- **Trợ lý kỹ thuật số**: *Cài đặt → Ứng dụng → Chọn ứng dụng mặc định → Ứng dụng trợ lý kỹ thuật số → HUY FAN*.
  Sau đó *Phím bên → Nhấn và giữ → Trợ lý kỹ thuật số* (One UI 7+).
- **Chế độ chờ “HUY FAN”** (gọi bằng giọng nói): bật bằng ô **HUY FAN chờ** trong bảng Cài đặt nhanh, widget, hoặc chip *Chờ* trên màn hình chat. Tự tắt sau 30 phút không dùng (chỉnh được).

## Đồng bộ Google Sheets

*Cài đặt → Google Sheets → Hướng dẫn & mã Apps Script*: app có sẵn mã + nút sao chép.
Tóm tắt: tạo Sheet → *Tiện ích mở rộng → Apps Script* → dán mã → *Triển khai → Ứng dụng web* (Thực thi: Tôi, Truy cập: Bất kỳ ai) → dán URL `/exec` vào app → **Gửi thử**.
Mã script nằm ở `app/src/main/res/raw/apps_script.txt`.

## Build lại sau khi sửa code

```bash
./gradlew assembleRelease
```
(Cần JDK 17+; SDK đã cài ở `C:\Users\DuccHuyy\AppData\Local\Android\Sdk`. Mở bằng Android Studio cũng được.)

## Cấu trúc mã nguồn

```
app/src/main/java/com/hankukko/huyfan/
├─ ai/            Gemini + OpenAI-compatible client, prompt mặc định, parse JSON trả về
├─ assistant/     ConversationEngine (nghe → hỏi AI → lưu → đọc → nghe tiếp), lệnh nhanh offline
├─ voice/         WakeWordEngine (Vosk), SpeechInput (nhận diện), Speaker (đọc song ngữ)
├─ service/       StandbyService (chế độ chờ), Quick Settings tiles, widget, thông báo
├─ data/          Room (sổ từ, lịch sử), DataStore (cài đặt), đồng bộ Google Sheets
└─ ui/            Jetpack Compose: Chat, Sổ từ, Ôn tập, Lịch sử, Cài đặt, bảng trợ lý bật lên
app/src/main/assets/vosk-model-vn/   Mô hình nhận diện tiếng Việt offline (Apache 2.0)
```

## Quyền riêng tư

- Âm thanh chờ từ khoá xử lý hoàn toàn trên máy.
- Câu hỏi (dạng chữ) được gửi tới API AI bạn chọn; nhận diện giọng nói dùng dịch vụ của Google trên máy.
- API key lưu trong máy (DataStore), không gửi đi đâu khác ngoài nhà cung cấp AI.
