# 📊 Smart Finance Tracker bertenaga AI

Aplikasi manajemen keuangan personal berbasis Android yang cerdas, adaptif, dan interaktif. Proyek ini mengintegrasikan **Groq Cloud API (Llama 3.1-8b-instant)** sebagai core engine pemrosesan bahasa alami (NLP) untuk merombak catatan keuangan harian secara otomatis langsung dari obrolan chat teks kasual.

---

## ✨ Fitur Utama (Premium Interface)

* **🎙️ Smart AI Chat Input**: Catat transaksi hanya dengan mengetik kalimat santai (Contoh: *"kemarin gajian 2.300.000"* atau *"beli rokok 20.000"*). Core AI secara otomatis mengurai data menjadi objek transaksi digital yang presisi.
* **📅 Asisten Kalender Dinamis (Backdate & Future Date)**: AI mampu membaca konteks waktu masa lalu maupun masa depan dan menyimpannya sesuai tanggal kejadian nyata, bukan sekadar dipaksa masuk ke tanggal hari ini.
* **🛡️ Pertahanan Ganda (Recovery Engine)**: Dilengkapi sistem deteksi cadangan berbasis kecocokan string lokal. Jika format JSON dari server cloud melenceng, data transaksimu dijamin tetap aman tercatat ke database tanpa memicu crash aplikasi.
* **📱 Dashboard Penuh & Simetris Ala Money Lover**: 
    * Menggunakan desain **Google Material 3** yang rata kanan-kiri memanfaatkan ruang layar secara penuh.
    * Menampilkan visualisasi grafis *Mini Donut Chart* yang responsif.
    * Angka ringkasan Pemasukan dan Pengeluaran berjejer simetris secara horizontal (kiri-kanan) di atas grafik.
    * Sektor analisis data 3 Pengeluaran Teratas (*Top 3 Expenses*) yang bisa difilter per minggu atau bulan.
    * *Grid Placeholder State* abu-abu yang elegan untuk menjaga estetika UI tetap rapi walaupun database masih kosong.
* **📜 Riwayat Transaksi Buku Kas**: Daftar mutasi kas super lengkap di navigasi bawah yang dikelompokkan otomatis per hari dan tanggal dengan navigasi geser bulan.
* **⏰ Pelacak Utang & Piutang**: Sinkronisasi otomatis dari chat ke menu pelaksanaan utang-piutang lengkap dengan hitungan sisa hari jatuh tempo per kontak agar rekaman pinjaman tetap terkendali.
* **🗄️ Inisialisasi Otomatis (Room SQLite)**: Menyuntikkan 15 kategori dasar preset premium langsung saat pertama kali aplikasi dibuat (*onCreate*) untuk mencegah error *Foreign Key Constraint*.

---

## 🛠️ Arsitektur Teknologi

Aplikasi ini dibangun menggunakan kombinasi teknologi modern Android:
* **Kotlin** - Bahasa pemrograman utama (100% Type-Safe & Coroutines Dispatchers).
* **Room Database (SQLite)** - Penyimpanan data lokal kaku dengan arsitektur DAO terpisah.
* **Groq Cloud API (Llama 3.1)** - Engine AI pemroses bahasa alami berkecepatan tinggi dengan latensi rendah.
* **Google Material 3 Desain** - Komponen antarmuka visual premium, simetris, dan dinamis.

---

## 🚀 Cara Instalasi & Menjalankan

1. Clone repositori ini ke lokal atau buka via Android Studio:
   ```bash
   git clone [https://github.com/umblox/Smart-Finance-Tracker-bertenaga-AI.git](https://github.com/umblox/Smart-Finance-Tracker-bertenaga-AI.git)
2. Pastikan Anda telah menyuntikkan GROQ_API_KEY ke dalam sistem build environment atau berkas local.properties agar kompilasi biner BuildConfig bisa mengenali token enkripsi API secara aman.
3. Lakukan Sync Project with Gradle Files.
4. Jalankan aplikasi di Emulator atau perangkat HP Android fisik Anda.
   
📄 Lisensi

Proyek ini dilisensikan di bawah MIT License - Lihat file LICENSE untuk detail lebih lanjut. Siapa pun diperbolehkan menggunakan, mengubah, dan menyebarkan kode ini secara bebas dengan syarat wajib menyertakan atribusi/sumber utama ke repositori ini.
