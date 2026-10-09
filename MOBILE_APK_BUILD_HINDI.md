# UE Assistant APK — मोबाइल से बनाने के स्टेप

**ध्यान दें:** यह ZIP प्रोजेक्ट का source code है, APK नहीं। GitHub का cloud runner APK बनाने की कोशिश करेगा; सफल build की गारंटी नहीं है।

## Android मोबाइल से
1. ब्राउज़र में https://github.com खोलें और sign in करें या account बनाएँ।
2. `+` → **New repository** → नाम `UE-Assistant-Android` → **Create repository**.
3. ZIP को Downloads में extract करें। `UE_Assistant_Android` फोल्डर के **अंदर की सभी फाइलें और फोल्डर** repository के root में upload करें। `app/`, `build.gradle.kts`, `settings.gradle.kts` और `.github/workflows/build-apk.yml` root पर होने चाहिए। अगर `.github` hidden हो और upload न हो, GitHub पर वही workflow file हाथ से बनाएँ।
4. **Commit changes** दबाएँ।
5. Repository में **Actions** tab खोलें → `Build UE Assistant APK` → **Run workflow** → हरा **Run workflow** बटन।
6. Run पूरा होने तक प्रतीक्षा करें। हरा successful run खुलने पर नीचे **Artifacts** में `UE-Assistant-debug-APK` डाउनलोड करें।
7. Artifact ZIP को Extract करें। `app-debug.apk` पर tap करके install करें। Android पूछे तो केवल अपनी बनाई हुई APK के लिए browser/files app की **Install unknown apps** अनुमति दें।

## अभी की सुविधाएँ
- UE ON/OFF, voice toggle, Hindi/Hinglish/English
- बोलकर या लिखकर commands देना
- समय, तारीख, बैटरी, notes सेव/देखना
- YouTube, Google search, weather search, Maps, WhatsApp, Clock/alarm screen, settings, dialer,  browser खोलना
- Optional HTTPS AI backend URL field

## अभी बाकी / सीमाएँ
- `Hey UE` हमेशा सुनने वाला wake-word engine अभी नहीं है; switch सिर्फ placeholder है।
- AI उत्तरों के लिए आपको compatible HTTPS backend host/configure करना होगा। App backend को `{ "message": "...", "language": "...", "assistant": "UE" }` भेजता है और `{ "reply": "..." }` की अपेक्षा करता है। OpenAI API key को APK या public GitHub repository में न डालें।
- Alarm command Clock app खोलती है; UE अपने आप reminder schedule नहीं करता। Weather Google search खोलता है। Calls/messages अपने आप नहीं भेजे जाते।
- Female voice फोन पर installed TTS voice पर निर्भर है।
- अगर build fail हो, Actions run के लाल error/log का screenshot भेजें ताकि ठीक किया जा सके।
