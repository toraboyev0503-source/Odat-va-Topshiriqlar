package com.memora.app.ui.components

import com.memora.app.prefs.AppLanguage

/** User-facing image selection controls use the app's thirteen interface languages. */
data class SelectionExportLabels(
    val title: String,
    val preparing: String,
    val save: String,
    val share: String,
    val close: String,
    val saved: String,
    val savedMultiple: String
) {
    companion object {
        fun forLanguage(language: AppLanguage): SelectionExportLabels = when (language) {
            AppLanguage.UZ -> SelectionExportLabels("Memora Paper rasmi", "Rasm tayyorlanmoqda", "Saqlash", "Ulashish", "Yopish", "Rasm saqlandi", "Rasmlar saqlandi")
            AppLanguage.RU -> SelectionExportLabels("Изображение Memora Paper", "Подготовка изображения", "Сохранить", "Поделиться", "Закрыть", "Изображение сохранено", "Изображения сохранены")
            AppLanguage.EN -> SelectionExportLabels("Memora Paper image", "Preparing image", "Save", "Share", "Close", "Image saved", "Images saved")
            AppLanguage.AR -> SelectionExportLabels("صورة Memora Paper", "جارٍ تجهيز الصورة", "حفظ", "مشاركة", "إغلاق", "تم حفظ الصورة", "تم حفظ الصور")
            AppLanguage.TR -> SelectionExportLabels("Memora Paper görseli", "Görsel hazırlanıyor", "Kaydet", "Paylaş", "Kapat", "Görsel kaydedildi", "Görseller kaydedildi")
            AppLanguage.FR -> SelectionExportLabels("Image Memora Paper", "Préparation de l’image", "Enregistrer", "Partager", "Fermer", "Image enregistrée", "Images enregistrées")
            AppLanguage.DE -> SelectionExportLabels("Memora Paper-Bild", "Bild wird erstellt", "Speichern", "Teilen", "Schließen", "Bild gespeichert", "Bilder gespeichert")
            AppLanguage.ES -> SelectionExportLabels("Imagen Memora Paper", "Preparando imagen", "Guardar", "Compartir", "Cerrar", "Imagen guardada", "Imágenes guardadas")
            AppLanguage.PT_BR -> SelectionExportLabels("Imagem Memora Paper", "Preparando imagem", "Salvar", "Compartilhar", "Fechar", "Imagem salva", "Imagens salvas")
            AppLanguage.HI -> SelectionExportLabels("Memora Paper चित्र", "चित्र बनाया जा रहा है", "सहेजें", "साझा करें", "बंद करें", "चित्र सहेजा गया", "चित्र सहेजे गए")
            AppLanguage.ID -> SelectionExportLabels("Gambar Memora Paper", "Menyiapkan gambar", "Simpan", "Bagikan", "Tutup", "Gambar disimpan", "Gambar-gambar disimpan")
            AppLanguage.JA -> SelectionExportLabels("Memora Paper画像", "画像を準備中", "保存", "共有", "閉じる", "画像を保存しました", "画像を保存しました")
            AppLanguage.KO -> SelectionExportLabels("Memora Paper 이미지", "이미지 준비 중", "저장", "공유", "닫기", "이미지를 저장했습니다", "이미지들을 저장했습니다")
        }
    }
}
