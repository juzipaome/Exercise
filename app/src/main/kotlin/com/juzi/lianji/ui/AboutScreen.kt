package com.juzi.lianji.ui

import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import com.juzi.lianji.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AboutScreen(onBack:()->Unit) {
    val context=LocalContext.current
    val uriHandler=LocalUriHandler.current
    val listState=rememberLazyListState()
    val scrollBehavior=MiuixScrollBehavior()
    val collapseDistance=with(LocalDensity.current){220.dp.toPx()}
    val scrollProgress by remember { derivedStateOf { if(listState.firstVisibleItemIndex>0)1f else (listState.firstVisibleItemScrollOffset/collapseDistance).coerceIn(0f,1f) } }
    val surface=MiuixTheme.colorScheme.surface
    val dark=surface.luminance()<.5f
    DisposableEffect(dark){
        val window=(context as? Activity)?.window
        val controller=window?.let { WindowInsetsControllerCompat(it,it.decorView) }
        val oldLightStatus=controller?.isAppearanceLightStatusBars
        val oldLightNavigation=controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars=!dark
        controller?.isAppearanceLightNavigationBars=!dark
        onDispose {
            if(oldLightStatus!=null)controller.isAppearanceLightStatusBars=oldLightStatus
            if(oldLightNavigation!=null)controller.isAppearanceLightNavigationBars=oldLightNavigation
        }
    }
    val backdrop=if(isRuntimeShaderSupported())rememberLayerBackdrop{drawRect(surface);drawContent()}else null
    val collapsed=scrollProgress==1f
    val version=remember(context){context.packageManager.getPackageInfo(context.packageName,PackageManager.PackageInfoFlags.of(0)).versionName?:"0.2.0"}

    Scaffold(topBar={
        Box(modifier=if(backdrop!=null&&collapsed)Modifier.textureBlur(backdrop,RectangleShape,25f,colors=BlurDefaults.blurColors(blendColors=listOf(BlendColorEntry(surface.copy(alpha=.8f)))))else Modifier){
            SmallTopAppBar(
                title="关于",
                titleColor=MiuixTheme.colorScheme.onSurface.copy(alpha=((scrollProgress-.35f)/.65f).coerceIn(0f,1f)),
                color=if(collapsed&&backdrop==null)surface else Color.Transparent,
                scrollBehavior=scrollBehavior,
                navigationIcon={BackButton(onBack)},
            )
        }
    }){padding->
        Box(Modifier.fillMaxSize()){
            AboutBackground(
                dark=dark,
                alpha={1f-scrollProgress},
                modifier=Modifier.fillMaxSize().then(if(backdrop!=null)Modifier.layerBackdrop(backdrop)else Modifier),
            )
            LazyColumn(
                state=listState,
                modifier=Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding=PaddingValues(top=padding.calculateTopPadding()+40.dp,bottom=padding.calculateBottomPadding()+24.dp),
            ){
                item(key="identity"){
                    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){
                        val iconProgress=((scrollProgress-.35f)/.15f).coerceIn(0f,1f)
                        val iconBackground=ImageBitmap.imageResource(R.drawable.ic_launcher_background)
                        val iconForeground=ImageBitmap.imageResource(R.drawable.ic_launcher_foreground)
                        Canvas(Modifier.size(88.dp).graphicsLayer{alpha=1f-iconProgress;scaleX=1f-iconProgress*.05f;scaleY=scaleX}.clip(RoundedCornerShape(24.dp)).background(Color.White).semantics{contentDescription="练迹应用图标"}){
                            val iconSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt())
                            drawImage(iconBackground,dstSize=iconSize)
                            drawImage(iconForeground,dstSize=iconSize)
                        }
                        val nameProgress=((scrollProgress-.2f)/.15f).coerceIn(0f,1f)
                        Text("练迹",Modifier.padding(top=12.dp,bottom=5.dp).graphicsLayer{alpha=1f-nameProgress;scaleX=1f-nameProgress*.05f;scaleY=scaleX},fontWeight=FontWeight.Bold,fontSize=35.sp)
                        Text("v$version · 私人离线健身记录",Modifier.fillMaxWidth().graphicsLayer{alpha=1f-(scrollProgress/.15f).coerceIn(0f,1f)},color=MiuixTheme.colorScheme.onSurfaceVariantSummary,textAlign=TextAlign.Center,fontSize=14.sp)
                        Spacer(Modifier.height(126.dp))
                    }
                }
                item(key="sources"){
                    AboutGlassCard(backdrop,dark){
                        ArrowPreference(title="动作数据",summary="hasaneyldrm/exercises-dataset",endActions={AboutValue("MIT")},onClick={uriHandler.openUri("https://github.com/hasaneyldrm/exercises-dataset")})
                        ArrowPreference(title="界面框架",summary="Miuix for Compose",endActions={AboutValue("0.9.4")},onClick={uriHandler.openUri("https://github.com/compose-miuix-ui/miuix")})
                    }
                }
                item(key="privacy"){
                    Spacer(Modifier.height(12.dp))
                    AboutGlassCard(backdrop,dark){
                        BasicComponent(title="隐私",summary="离线优先，训练计划、日程和历史仅保存在本机。")
                        ArrowPreference(title="动作媒体",summary="Gym visual · 180 × 180",endActions={AboutValue("个人授权")},onClick={uriHandler.openUri("https://gymvisual.com/content/3-terms-and-conditions-of-use")})
                        BasicComponent(title="媒体使用",summary="未经重新确认授权，不应公开分发包含动作图片或 GIF 的版本。")
                    }
                }
            }
            VerticalScrollBar(rememberScrollBarAdapter(listState),Modifier.align(Alignment.CenterEnd).fillMaxHeight(),trackPadding=padding)
        }
    }
}

@Composable
private fun AboutGlassCard(backdrop:LayerBackdrop?,dark:Boolean,content:@Composable ColumnScope.()->Unit) {
    val modifier=if(backdrop==null)Modifier else Modifier.textureBlur(
        backdrop=backdrop,
        shape=RoundedCornerShape(16.dp),
        blurRadius=60f,
        colors=BlurDefaults.blurColors(blendColors=if(dark)listOf(
            BlendColorEntry(Color(0x4DA9A9A9),BlurBlendMode.Luminosity),
            BlendColorEntry(Color(0x1A9C9C9C),BlurBlendMode.PlusDarker),
        )else listOf(
            BlendColorEntry(Color(0x340034F9),BlurBlendMode.Overlay),
            BlendColorEntry(Color(0xB3FFFFFF),BlurBlendMode.HardLight),
        )),
    )
    Card(
        modifier=Modifier.padding(horizontal=12.dp).then(modifier),
        colors=CardDefaults.defaultColors(if(backdrop!=null)Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,Color.Transparent),
        content=content,
    )
}

@Composable
private fun AboutValue(text:String)=Text(text,fontSize=MiuixTheme.textStyles.body2.fontSize,color=MiuixTheme.colorScheme.onSurfaceVariantActions)
