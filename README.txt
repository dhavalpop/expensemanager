KHARCHA DIARY - ANDROID APP PROJECT
====================================

This is a real Android app. It shows your Kharcha Diary and adds alarms that go off even when
the app is closed, with a different sound for each type: SIP, NPS, LIC, mediclaim, car
insurance, bike insurance, education fee and other. You can pick any tone from your phone for
each type inside the app (Reminders tab > Alert sounds).

It has to be built once into an installable file called an APK. Pick ONE way.

WAY 1 - Build on a computer with Android Studio
  1. Install Android Studio (free) from developer.android.com/studio
  2. Choose Open, and select this folder (the one that contains settings.gradle).
  3. Wait for the Gradle sync to finish (a few minutes the first time).
  4. Menu: Build > Build Bundle(s) / APK(s) > Build APK(s).
  5. Click "locate" in the pop-up. The file is app-debug.apk.
  6. Copy it to your phone and open it. Allow "Install unknown apps" when asked.

WAY 2 - Build online with GitHub (no computer software needed)
  1. Create a free GitHub account and an empty repository.
  2. Upload everything in this folder to it, keeping the folders. The hidden folder
     .github must be included.
  3. Open the Actions tab and wait for "Build Android app" to finish (about 5 minutes).
  4. Open the Releases section of the repository on your phone and tap KharchaDiary.apk.

Updating later (your data is kept)
  Every build is signed with the same key (signing/kharcha.jks), so a new APK installs over the
  old app. Just open the new APK and tap Update. Do NOT uninstall first, because uninstalling
  erases the app's data. As a second safety net, the app keeps a copy of your data in your
  Downloads folder as KharchaDiary-backup.json. After an uninstall, open the app, choose
  Restore backup, and pick that file.

After installing
  - Open the app and tap Allow when it asks to send notifications.
  - Go to Reminders, add your SIP, NPS, LIC, mediclaim, insurance and fee dates, and choose how
    many days before each one should alert.
  - Under Alert sounds, tap Test to hear each tone, and Change to use your own.
  - On Xiaomi, Oppo, Vivo, OnePlus and Samsung phones, open Battery settings in the app and set
    Kharcha Diary to Unrestricted, and allow auto-start, or the phone may block alarms.

Your entries are stored inside the app on the phone. Use Savings > Data and backup > Backup
file now and then.
