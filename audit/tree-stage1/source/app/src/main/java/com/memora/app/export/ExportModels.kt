package com.memora.app.export

enum class ExportFormat { WORD, MULTIMEDIA, BACKUP }
enum class ExportPeriod { MONTH, YEAR, ALL }
enum class BackupScope { SHORT, LONG, BOTH }
enum class WordLayout { CHRONOLOGICAL, GROUP_BY_TITLE }
enum class ExportTarget { NOTES, TOPILMALAR }

data class ExportRequest(
    val format: ExportFormat,
    val period: ExportPeriod,
    val year: Int? = null,
    val month: Int? = null,
    val backupScope: BackupScope = BackupScope.BOTH,
    val wordLayout: WordLayout = WordLayout.CHRONOLOGICAL,
    val target: ExportTarget = ExportTarget.NOTES,
    val includeComments: Boolean = true
)
