package com.memora.app.memories

import com.memora.app.prefs.AppLanguage

/** Short labels translated independently of the legacy Topilma strings. */
object MemoryText {
    enum class Key { MEMORY, PHOTO_REMINDER, NOTIFY, SKIP, READ, LATER, START, ALBUM, CAPTION, SAVE, CAMERA, PHOTO, SETTINGS }
    private val translations: Map<AppLanguage, List<String>> = mapOf(
        AppLanguage.UZ to listOf("Xotiradan", "Bugundan bir lahzani saqlang", "Eslatmalar", "O‘tkazish", "Hozir o‘qish", "1 soatdan keyin", "Boshlaymiz", "Kun izi", "Qisqa izoh", "Saqlash", "Suratga olish", "Surat", "Sozlamalar"),
        AppLanguage.EN to listOf("From your memories", "Capture a moment from today", "Reminders", "Skip", "Read now", "In 1 hour", "Let's start", "Day Trace", "Short caption", "Save", "Take a photo", "Photo", "Settings"),
        AppLanguage.RU to listOf("Из воспоминаний", "Сохраните мгновение дня", "Напоминания", "Пропустить", "Читать сейчас", "Через час", "Начнём", "След дня", "Подпись", "Сохранить", "Снять фото", "Фото", "Настройки"),
        AppLanguage.TR to listOf("Anılardan", "Bugünden bir anı yakalayın", "Hatırlatıcılar", "Geç", "Şimdi oku", "1 saat sonra", "Başlayalım", "Günün izi", "Kısa not", "Kaydet", "Fotoğraf çek", "Fotoğraf", "Ayarlar"),
        AppLanguage.FR to listOf("Souvenirs", "Capturez un moment d’aujourd’hui", "Rappels", "Ignorer", "Lire", "Dans 1 heure", "Commencer", "Trace du jour", "Légende", "Enregistrer", "Photo", "Photo", "Paramètres"),
        AppLanguage.DE to listOf("Erinnerungen", "Halte einen Moment fest", "Erinnerungen", "Überspringen", "Jetzt lesen", "In 1 Stunde", "Starten", "Tagesspur", "Bildtext", "Speichern", "Foto machen", "Foto", "Einstellungen"),
        AppLanguage.ES to listOf("Recuerdos", "Captura un momento de hoy", "Recordatorios", "Omitir", "Leer ahora", "En 1 hora", "Empezar", "Huella del día", "Nota breve", "Guardar", "Tomar foto", "Foto", "Ajustes"),
        AppLanguage.PT_BR to listOf("Memórias", "Registre um momento de hoje", "Lembretes", "Ignorar", "Ler agora", "Em 1 hora", "Começar", "Marca do dia", "Legenda", "Salvar", "Tirar foto", "Foto", "Configurações"),
        AppLanguage.ID to listOf("Kenangan", "Abadikan momen hari ini", "Pengingat", "Lewati", "Baca sekarang", "1 jam lagi", "Mulai", "Jejak hari", "Keterangan", "Simpan", "Ambil foto", "Foto", "Pengaturan"),
        AppLanguage.HI to listOf("पुरानी यादें", "आज के पल को संजोएँ", "अनुस्मारक", "छोड़ें", "अभी पढ़ें", "1 घंटे बाद", "शुरू करें", "दिन की याद", "कैप्शन", "सहेजें", "फोटो लें", "फोटो", "सेटिंग्स"),
        AppLanguage.AR to listOf("من الذكريات", "احتفظ بلحظة من اليوم", "التذكيرات", "تخطي", "اقرأ الآن", "بعد ساعة", "لنبدأ", "أثر اليوم", "تعليق", "حفظ", "التقاط صورة", "صورة", "الإعدادات"),
        AppLanguage.JA to listOf("思い出から", "今日の一瞬を残す", "リマインダー", "スキップ", "今読む", "1時間後", "始める", "今日の記録", "短いメモ", "保存", "写真を撮る", "写真", "設定"),
        AppLanguage.KO to listOf("추억에서", "오늘의 순간을 남기세요", "알림", "건너뛰기", "지금 읽기", "1시간 후", "시작", "하루의 흔적", "짧은 글", "저장", "사진 찍기", "사진", "설정")
    )
    /** Historic date is formatted externally; these strings cover all shipped locales. */
    fun writtenAt(language: AppLanguage, date: String): String = when (language) {
        AppLanguage.UZ -> "Siz bu yozuvni $date da yozgansiz"
        AppLanguage.RU -> "Вы написали это $date"
        AppLanguage.EN -> "You wrote this on $date"
        AppLanguage.TR -> "Bunu $date tarihinde yazdınız"
        AppLanguage.FR -> "Vous avez écrit ceci le $date"
        AppLanguage.DE -> "Sie haben dies am $date geschrieben"
        AppLanguage.ES -> "Escribiste esto el $date"
        AppLanguage.PT_BR -> "Você escreveu isto em $date"
        AppLanguage.ID -> "Anda menulis ini pada $date"
        AppLanguage.HI -> "आपने इसे $date को लिखा था"
        AppLanguage.AR -> "كتبت هذا في $date"
        AppLanguage.JA -> "$date に書いた記録です"
        AppLanguage.KO -> "$date 에 작성한 기록입니다"
    }

    fun albumPrompt(language: AppLanguage): String = when (language) {
        AppLanguage.UZ -> "Bugungi lahzani surat va qisqa izoh bilan saqlang"
        AppLanguage.RU -> "Сохраните мгновение дня с фотографией и подписью"
        AppLanguage.EN -> "Capture today's moment with a photo and short caption"
        AppLanguage.TR -> "Bugünden bir anı fotoğraf ve kısa notla kaydedin"
        AppLanguage.FR -> "Gardez un souvenir d’aujourd’hui avec une photo et une note"
        AppLanguage.DE -> "Halten Sie heute einen Moment mit Foto und kurzer Notiz fest"
        AppLanguage.ES -> "Guarda un momento de hoy con una foto y una nota"
        AppLanguage.PT_BR -> "Guarde um momento de hoje com foto e legenda"
        AppLanguage.ID -> "Simpan momen hari ini dengan foto dan catatan singkat"
        AppLanguage.HI -> "आज के पल को एक फोटो और छोटी टिप्पणी के साथ सहेजें"
        AppLanguage.AR -> "احفظ لحظة اليوم بصورة وتعليق قصير"
        AppLanguage.JA -> "今日の一瞬を写真と短いメモで残しましょう"
        AppLanguage.KO -> "오늘의 순간을 사진과 짧은 메모로 남겨 보세요"
    }

    fun get(language: AppLanguage, key: Key): String = translations[language]?.get(key.ordinal)
        ?: translations.getValue(AppLanguage.EN)[key.ordinal]
}
