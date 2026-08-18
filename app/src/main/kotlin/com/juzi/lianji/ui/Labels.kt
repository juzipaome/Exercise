package com.juzi.lianji.ui

import com.juzi.lianji.data.ExercisePersonalBest
import com.juzi.lianji.data.TrackingMode
import java.math.BigDecimal

fun bodyPartLabel(value:String)=mapOf("upper arms" to "上臂","upper legs" to "大腿","back" to "背部","waist" to "腰腹","chest" to "胸部","shoulders" to "肩部","lower legs" to "小腿","lower arms" to "前臂","cardio" to "有氧","neck" to "颈部")[value] ?: value
fun equipmentLabel(value:String)=mapOf("assisted" to "辅助器械","band" to "弹力带","barbell" to "杠铃","body weight" to "自重","bosu ball" to "波速球","cable" to "绳索","dumbbell" to "哑铃","elliptical machine" to "椭圆机","ez barbell" to "曲杆杠铃","hammer" to "锤式器械","kettlebell" to "壶铃","leverage machine" to "固定器械","medicine ball" to "药球","olympic barbell" to "奥杆","resistance band" to "阻力带","roller" to "泡沫轴","rope" to "训练绳","skierg machine" to "滑雪机","sled machine" to "雪橇机","smith machine" to "史密斯机","stability ball" to "健身球","stationary bike" to "动感单车","stepmill machine" to "登阶机","tire" to "轮胎","trap bar" to "六角杠铃","upper body ergometer" to "上肢功率车","weighted" to "负重","wheel roller" to "健腹轮")[value] ?: value

fun personalBestLabel(best:ExercisePersonalBest?):String {
    if(best==null)return "PB · 暂无记录"
    if(best.maxWeightKg<=0&&best.maxReps<=0&&best.maxDistanceKm<=0&&best.maxDurationSeconds<=0)return "PB · 暂无有效记录"
    return if(best.trackingMode==TrackingMode.CARDIO)buildString {
        append("PB")
        if(best.maxDistanceKm>0)append(" · 最远 ${trimDecimal(best.maxDistanceKm)} km")
        if(best.maxDurationSeconds>0)append(" · 最久 ${formatDuration(best.maxDurationSeconds.toLong())}")
    } else buildString {
        append("PB")
        if(best.maxWeightKg>0)append(" · 最高 ${trimDecimal(best.maxWeightKg)} kg")
        if(best.maxReps>0)append(" · 最多 ${best.maxReps} 次")
    }
}

fun trimDecimal(value:Double)=BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
