package com.juzi.lianji.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SourceExercise(
    val id: String,
    val name: String,
    val category: String,
    @SerialName("body_part") val bodyPart: String,
    val equipment: String,
    val instructions: Map<String, String> = emptyMap(),
    @SerialName("muscle_group") val muscleGroup: String = "",
    @SerialName("secondary_muscles") val secondaryMuscles: List<String> = emptyList(),
    val target: String = "",
    val image: String? = null,
    @SerialName("gif_url") val gifUrl: String? = null,
    val attribution: String = "© Gym visual — https://gymvisual.com/",
)

object ExerciseImporter {
    const val ExpectedCount = 1324
    private val json = Json { ignoreUnknownKeys = true }
    private val seedMutex = Mutex()
    private const val DatasetVersion = 2

    suspend fun seedIfNeeded(context: Context, db: LianJiDatabase) = withContext(Dispatchers.IO) { seedMutex.withLock {
        val dao=db.exerciseDao()
        val preferences=context.getSharedPreferences("dataset_seed",Context.MODE_PRIVATE)
        val mediaNames=context.assets.list("exercise_dataset/images").orEmpty().sorted()+context.assets.list("exercise_dataset/videos").orEmpty().sorted()
        val signature="$DatasetVersion:${mediaNames.hashCode()}"
        if (dao.builtinCount() == ExpectedCount && preferences.getString("signature",null)==signature) return@withLock
        val raw = context.assets.open("exercise_dataset/data/exercises.json").bufferedReader().use { it.readText() }
        val namesRaw = context.assets.open("exercise_dataset/data/names_zh.json").bufferedReader().use { it.readText() }
        val namesZh = json.decodeFromString<Map<String,String>>(namesRaw)
        val source = json.decodeFromString<List<SourceExercise>>(raw)
        val availableMedia = buildSet {
            context.assets.list("exercise_dataset/images").orEmpty().forEach {
                add("exercise_dataset/images/$it")
            }
            context.assets.list("exercise_dataset/videos").orEmpty().forEach {
                add("exercise_dataset/videos/$it")
            }
        }
        require(source.size == ExpectedCount) { "动作数据应有 $ExpectedCount 条，实际 ${source.size} 条" }
        require(source.map { it.id }.distinct().size == ExpectedCount) { "动作 ID 不唯一" }
        require(namesZh.size == ExpectedCount) { "中文动作名应有 $ExpectedCount 条，实际 ${namesZh.size} 条" }
        db.withTransaction {
            val existing=dao.getBuiltin().associateBy { it.id }
            dao.upsertAll(source.map {
                val item=it.toEntity(namesZh[it.id] ?: it.name,availableMedia)
                val previous=existing[it.id]
                item.copy(nameZh=previous?.nameZh?.takeIf{_->previous.nameZh!=previous.datasetNameZh}?:item.nameZh,isFavorite=previous?.isFavorite?:false)
            })
        }
        preferences.edit().putString("signature",signature).apply()
    } }

    private fun SourceExercise.toEntity(
        translatedName: String,
        availableMedia: Set<String>,
    ) = ExerciseEntity(
        id = id,
        nameEn = name,
        nameZh = translatedName,
        datasetNameZh = translatedName,
        bodyPart = bodyPart.ifBlank { category },
        equipment = equipment,
        target = target,
        muscleGroup = muscleGroup,
        secondaryMuscles = secondaryMuscles.joinToString("|") ,
        instructionsZh = instructions["zh"].orEmpty(),
        instructionsEn = instructions["en"].orEmpty(),
        imagePath = image?.let { "exercise_dataset/$it" }?.takeIf(availableMedia::contains),
        gifPath = gifUrl?.let { "exercise_dataset/$it" }?.takeIf(availableMedia::contains),
        attribution = attribution,
        trackingMode = if (category == "cardio" || bodyPart == "cardio") TrackingMode.CARDIO else TrackingMode.STRENGTH,
    )

}
