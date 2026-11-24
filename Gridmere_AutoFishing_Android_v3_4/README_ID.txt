GRIDMERE AUTO FISHING ANDROID v3.4 BETA
======================================

STATUS
------
Ini adalah proyek Android source code, bukan APK yang sudah terkompilasi. APK belum
bisa dibuat di lingkungan penyusunan ini karena Android SDK/Gradle tidak tersedia dan
unduhan komponen build tidak dapat diakses dari lingkungan ini.

PERBAIKAN YANG DIBAWA DARI PC v3.4
---------------------------------
- Ambang pemicu tarik diturunkan menjadi 98,5%.
- Indikator harus terbaca penuh dua kali berturut-turut sebelum tombol tarik dikirim.
- Satu frame marker yang tidak terbaca ditoleransi; dua frame hilang berturut-turut
  menghapus hitungan konfirmasi.
- Posisi indikator dihitung dari ujung kanan marker (advancing edge), bukan titik tengah.
- Perbaikan penting Android: koordinat komponen marker sudah berupa koordinat layar,
  sehingga offset awal area deteksi tidak ditambahkan untuk kedua kalinya.
- Kehilangan deteksi kotak pemain ditoleransi 4,5 detik.
- Progres tetap dipantau terpisah; hilangnya marker ditoleransi 3 detik sebelum
  area minigame dianggap hilang.

BATASAN VERSI ANDROID
--------------------
- Cast/lempar kail pertama dilakukan manual di game.
- Saat dialog hasil tangkapan muncul, pilih/tutup dialog secara manual.
- Setelah dialog tertutup, hentikan lalu mulai kembali Auto Fish untuk siklus berikutnya.
- Kontrol kiri/kanan dan tarik menggunakan Android Accessibility Gesture.
- Pembacaan layar menggunakan MediaProjection (dengan persetujuan layar Android).
- Area deteksi minigame masih memakai geometri referensi 16:9 dari versi PC. Perlu
  diuji dan mungkin dikalibrasi ulang berdasarkan screenshot game Android.
- Ini versi beta; uji satu siklus sambil mengawasi game. Jangan langsung ditinggal.

BUILD APK DENGAN GITHUB ACTIONS (TANPA ANDROID STUDIO)
------------------------------------------------------
1. Ekstrak ZIP ini di komputer.
2. Buka repository GitHub yang akan dipakai untuk proyek ini.
3. Upload seluruh ISI folder Gridmere_AutoFishing_Android_v3_4 ke root repository
   (jangan upload folder pembungkusnya sebagai satu subfolder).
4. Commit perubahan. Workflow otomatis berjalan setelah push ke branch main/master.
5. Di tab Actions, buka proses "Build Gridmere AutoFishing APK".
6. Setelah proses berstatus hijau (success), buka bagian Artifacts dan unduh
   "Gridmere-AutoFishing-Android-v3.4". File ZIP artifact berisi app-debug.apk.

BUILD APK DENGAN ANDROID STUDIO
-------------------------------
1. Ekstrak ZIP ini.
2. Buka folder Gridmere_AutoFishing_Android_v3_4 di Android Studio.
3. Gunakan JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, dan Android SDK 35.
4. Jalankan Gradle Sync, lalu Build > Build Bundle(s) / APK(s) > Build APK(s).
5. APK debug berada di app/build/outputs/apk/debug/app-debug.apk.

CARA PAKAI SETELAH APK TERPASANG
--------------------------------
1. Buka Gridmere AutoFishing 3.4.
2. Tekan "Buka pengaturan Accessibility" dan aktifkan layanan Gridmere AutoFishing.
3. Kembali ke aplikasi, tekan "Izinkan tangkapan layar", lalu setujui dialog Android.
4. Atur posisi pusat tombol kiri, kanan, dan tarik sebagai persen layar (0-100).
5. Tekan "SIMPAN PENGATURAN".
6. Lempar kail secara manual di Gridmere.
7. Tekan "MULAI AUTO FISH", lalu kembali ke game.
8. Pantau nilai Progress dan Fase di aplikasi. Untuk berhenti, tekan "BERHENTI / EMERGENCY STOP".
9. Untuk menghentikan capture sepenuhnya, tekan "HENTIKAN TANGKAPAN LAYAR".

CATATAN
-------
APK final belum dihasilkan atau diuji pada perangkat Android. Paket ini adalah proyek
v3.4 yang siap dibangun melalui GitHub Actions atau Android Studio. Deteksi dan posisi
sentuh harus diuji pada game serta resolusi perangkat yang sebenarnya.
