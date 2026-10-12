package app.swiftdrop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.ui.theme.SwiftDropDesign

// SwiftDrop @feature Codex 12/10/2026 Reference-inspired, native Material components.
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Surface(modifier,shape=MaterialTheme.shapes.small,shadowElevation=2.dp,color=Color.Transparent) {
        Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(SwiftDropDesign.Cyan,SwiftDropDesign.Blue))),contentAlignment=Alignment.Center) {
            Icon(painterResource(R.drawable.ic_brand),null,Modifier.fillMaxSize(.62f),tint=Color.White)
        }
    }
}

@Composable
fun BrandHeader(onSettings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        BrandMark()
        Column(Modifier.weight(1f)) {
            Text(buildAnnotatedString {
                append("Swift")
                withStyle(SpanStyle(color=MaterialTheme.colorScheme.primary)) { append("Drop") }
            },style=MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.brand_tagline),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(onSettings!=null) IconButton(onSettings,Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {
            AppIcon(R.drawable.ic_settings,description=stringResource(R.string.settings))
        }
    }
}

@Composable
fun GradientAction(icon: Int,title: String,caption: String,onClick: () -> Unit,modifier: Modifier = Modifier,cyan: Boolean = false) {
    val colors=if(cyan) listOf(Color(0xFF087F9B),Color(0xFF046C86)) else listOf(SwiftDropDesign.Blue,Color(0xFF0849D7))
    Card(onClick,modifier,shape=MaterialTheme.shapes.medium,colors=CardDefaults.cardColors(containerColor=Color.Transparent),
        elevation=CardDefaults.cardElevation(defaultElevation=2.dp)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(colors)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            AppIcon(icon,Modifier.size(32.dp),tint=Color.White)
            Text(title,style=MaterialTheme.typography.titleLarge,color=Color.White)
            Text(caption,style=MaterialTheme.typography.bodySmall,color=Color.White)
        }
    }
}

@Composable
fun categoryColor(icon: Int): Color {
    val base=when(icon) {
    R.drawable.ic_photos -> Color(0xFFC72158)
    R.drawable.ic_videos -> Color(0xFF7736BE)
    R.drawable.ic_applications -> Color(0xFF177647)
    R.drawable.ic_music -> Color(0xFFC52661)
    R.drawable.ic_documents -> Color(0xFF986300)
    else -> Color(0xFF456277)
    }
    return if(MaterialTheme.colorScheme.background.luminance()<.2f) lerp(base,Color.White,.4f) else base
}

@Composable
fun CategoryBadge(icon: Int,modifier: Modifier = Modifier) {
    val color=categoryColor(icon)
    Surface(modifier,shape=MaterialTheme.shapes.small,color=color.copy(alpha=.12f)) {
        Box(Modifier.size(44.dp),contentAlignment=Alignment.Center) { AppIcon(icon,tint=color) }
    }
}

@Composable
fun ErrorNotice(message: String) {
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            AppIcon(R.drawable.ic_error,tint=MaterialTheme.colorScheme.onErrorContainer)
            Text(message,Modifier.weight(1f),color=MaterialTheme.colorScheme.onErrorContainer,style=MaterialTheme.typography.bodyMedium)
        }
    }
}
