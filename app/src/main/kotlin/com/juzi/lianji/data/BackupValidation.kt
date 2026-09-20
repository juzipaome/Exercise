package com.juzi.lianji.data

import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.serialization.json.Json

internal fun backupFingerprint(backup: BackupEnvelope): String = MessageDigest.getInstance("SHA-256")
    .digest(Json.encodeToString(backup).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal fun validateBackup(backup: BackupEnvelope) {
    require(backup.schemaVersion in 1..6) { "不支持的备份版本 ${backup.schemaVersion}" }
    require(backup.exportedAt>=0) { "备份时间无效" }
    fun <T> unique(ids: List<T>) { require(ids.distinct().size==ids.size) { "备份中存在重复 ID" } }
    fun name(value: String) { require(value.isNotBlank()&&value.length<=1_000) { "名称为空或过长" } }
    fun number(value: Double) { require(value.isFinite()&&value>=0) { "数值必须是有限的非负数" } }
    fun rest(value: Int) { require(value in 0..86_400) { "休息时长无效" } }
    fun mode(value: String) { require(value in setOf(TrackingMode.STRENGTH,TrackingMode.CARDIO)) { "未知记录方式" } }
    unique(backup.customExercises.map{it.id}); unique(backup.plans.map{it.id})
    unique(backup.schedules.map{it.id}); unique(backup.sessions.map{it.id})
    unique(backup.exerciseNameOverrides.map{it.id})
    unique(backup.plans.flatMap{p->p.items.map{it.id}})
    unique(backup.sessions.flatMap{s->s.exercises.map{it.id}})
    unique(backup.sessions.flatMap{s->s.exercises.flatMap{e->e.sets.map{it.id}}})
    backup.customExercises.forEach {
        require(it.id.startsWith("custom-")) { "自定义动作 ID 无效" }
        name(it.nameZh)
        if(it.trackingMode.isNotBlank())mode(it.trackingMode)
        listOfNotNull(it.imagePath,it.gifPath).forEach { path ->
            require(path.startsWith("exercise_dataset/")&&!path.contains("..")&&!path.contains('\\')) { "媒体路径无效" }
        }
    }
    backup.exerciseNameOverrides.forEach { require(it.id.isNotBlank()); name(it.nameZh) }
    backup.plans.forEach { plan ->
        require(plan.id>0); name(plan.name)
        unique(plan.items.map{it.exerciseId}); unique(plan.items.map{it.position})
        plan.items.forEach {
            require(it.id>0&&it.planId==plan.id&&it.position>=0&&it.exerciseId.isNotBlank()) { "计划动作引用无效" }
            require(it.defaultSets in 1..1_000&&it.defaultReps>=0) { "组数或次数无效" }
            number(it.defaultWeightKg); rest(it.restSeconds)
        }
    }
    backup.schedules.forEach {
        require(it.id>0); name(it.planName); LocalDate.parse(it.scheduledDate)
        require(it.status in setOf("PLANNED","COMPLETED","SKIPPED","CANCELLED")) { "日程状态无效" }
        require(it.planId==null||backup.plans.any{p->p.id==it.planId}) { "日程引用了不存在的计划" }
    }
    backup.sessions.forEach { session ->
        require(session.id>0); name(session.planName); LocalDate.parse(session.localDate)
        require(session.status in setOf("ACTIVE","COMPLETED","DISCARDED","INTERRUPTED")) { "训练状态无效" }
        require(session.startedAt>=0&&(session.endedAt==null||session.endedAt>=session.startedAt)) { "训练时间范围无效" }
        require(session.status!="COMPLETED"||session.endedAt!=null) { "完成训练缺少结束时间" }
        unique(session.exercises.map{it.position}); unique(session.exercises.map{it.exerciseId})
        session.exercises.forEach { exercise ->
            require(exercise.id>0&&exercise.position>=0&&exercise.exerciseId.isNotBlank()); name(exercise.name)
            mode(exercise.trackingMode); rest(exercise.restSeconds)
            unique(exercise.sets.map{it.position})
            exercise.sets.forEach { set ->
                require(set.id>0&&set.position>=0&&set.reps>=0&&set.durationSeconds>=0&&set.restDurationSeconds>=0&&set.pausedDurationMillis>=0) { "训练组数值无效" }
                number(set.weightKg); number(set.distanceKm)
                require(listOfNotNull(set.startedAt,set.completedAt,set.restStartedAt,set.restEndedAt,set.pausedAt).all{it>=0})
                require(set.completedAt==null||set.startedAt==null||set.completedAt>=set.startedAt) { "完成时间早于开始时间" }
                require(set.pausedAt==null||(set.startedAt!=null&&set.pausedAt>=set.startedAt&&!set.completed)) { "暂停时间无效" }
                require(set.restEndedAt==null||(set.restStartedAt!=null&&set.restEndedAt>=set.restStartedAt)) { "休息时间无效" }
            }
        }
    }
    backup.settings?.let {
        require(it.themeMode in setOf("SYSTEM","LIGHT","DARK"))
        require(it.navigationBarStyle in setOf("STANDARD","FLOATING","LIQUID"))
        require(it.navigationBarMode in setOf("ICON_AND_TEXT","ICON_ONLY","SELECTED_LABEL"))
        require(it.floatingNavigationBarPosition in setOf("CENTER","START","END"))
        rest(it.defaultRestSeconds)
    }
}
