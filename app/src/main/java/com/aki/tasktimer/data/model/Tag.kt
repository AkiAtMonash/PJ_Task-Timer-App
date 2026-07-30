package com.aki.tasktimer.data.model

/**
 * タグ。v1 では追加・編集 UI を作らない固定 6 種（docs/01_SPEC.md 3.4）。
 *
 * DB テーブルではなく enum にしている理由：
 * docs/02_ARCHITECTURE.md の Entity 一覧は 4 種（Session / Extension / TaskPreset /
 * ExtensionPreset）で TagEntity が無く、Tag は data/model 側に置かれている。
 * 固定集合なので enum のほうがコンパイル時に守られる。
 *
 * [label] は **Notion の既存 DB と同じ表記**。v2 の同期で突き合わせのキーになるので、
 * 表示都合で勝手に変えないこと。DB にもこの文字列がそのまま入る（Converters 参照）。
 *
 * 色は UI の関心なので ui/theme 側で対応づける（Phase 2）。
 */
enum class Tag(val label: String) {
    SLEEP("Sleep"),
    UNI_STUDY("Uni Study"),
    JOB_HUNTING("Job Hunting"),
    HOBBY("Hobby"),
    CHORE("Chore"),
    FOR_MYSELF("For Myself"),
    ;

    companion object {
        fun fromLabel(label: String): Tag? = entries.firstOrNull { it.label == label }
    }
}
