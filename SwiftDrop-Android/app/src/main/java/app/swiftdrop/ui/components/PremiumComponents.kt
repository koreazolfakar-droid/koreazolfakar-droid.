package app.swiftdrop.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.ui.theme.SwiftDropDesign

// SwiftDrop @feature Codex 12/10/2026 Original brand and compact, shared Native Pro controls.
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher), null, modifier.size(48.dp))
}

@Composable
fun BrandHeader(onSettings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        BrandMark()
        Column(Modifier.weight(1f)) {
            Text("SwiftDrop", style=MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.brand_tagline), style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(onSettings!=null) IconButton(onSettings) {
            AppIcon(R.drawable.ic_settings, description=stringResource(R.string.settings),
                tint=MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun categoryColor(icon: Int): Color {
    val dark=MaterialTheme.colorScheme.background.luminance()<.2f
    return Color(when(icon) {
        R.drawable.ic_photos -> if(dark) 0xFFFF84AD else 0xFFC72C67
        R.drawable.ic_videos -> if(dark) 0xFFC09AFF else 0xFF7D45CB
        R.drawable.ic_music -> if(dark) 0xFFFF918D else 0xFFCC3B45
        R.drawable.ic_applications -> if(dark) 0xFF7AD7A5 else 0xFF198052
        R.drawable.ic_folder -> if(dark) 0xFFFFCE66 else 0xFFAA7100
        R.drawable.ic_documents -> if(dark) 0xFF83B8FF else 0xFF176BD4
        else -> if(dark) 0xFFADB8C8 else 0xFF626C7D
    })
}

@Composable
fun CategoryBadge(icon: Int, modifier: Modifier = Modifier) {
    val color=categoryColor(icon)
    Surface(modifier.size(44.dp), shape=MaterialTheme.shapes.small, color=color.copy(alpha=.12f)) {
        Box(contentAlignment=Alignment.Center) { AppIcon(icon, tint=color) }
    }
}

@Composable
fun ErrorNotice(message: String) {
    Surface(shape=MaterialTheme.shapes.medium, color=MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            AppIcon(R.drawable.ic_error, tint=MaterialTheme.colorScheme.onErrorContainer)
            Text(message, Modifier.weight(1f), color=MaterialTheme.colorScheme.onErrorContainer,
                style=MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Honors Android's disabled animator setting; ripple remains native. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val context=LocalContext.current
    val motion=remember(context) { runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)>0f
    }.getOrDefault(true) }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed && motion) .98f else 1f,
        tween(if(motion) SwiftDropDesign.MotionMillis else 0), label="press")
    return this.graphicsLayer { scaleX=scale; scaleY=scale }
}

@Composable
fun SectionHeading(title: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp), verticalAlignment=Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style=MaterialTheme.typography.titleLarge)
        if(action!=null && actionLabel!=null) TextButton(action) {
            Text(actionLabel)
            AppIcon(R.drawable.ic_chevron, Modifier.size(16.dp))
        }
    }
}

@Composable
fun SwiftSearchField(query: String, change: (String) -> Unit, hint: String,
    modifier: Modifier = Modifier) {
    OutlinedTextField(query, change, modifier.heightIn(min=48.dp), singleLine=true,
        placeholder={Text(hint, maxLines=1, overflow=TextOverflow.Ellipsis)},
        leadingIcon={AppIcon(R.drawable.ic_search, tint=MaterialTheme.colorScheme.onSurfaceVariant)},
        trailingIcon=if(query.isEmpty()) null else ({ IconButton({change("")}) {
            AppIcon(R.drawable.ic_close, description=stringResource(R.string.media_clear),
                tint=MaterialTheme.colorScheme.onSurfaceVariant)
        } }), shape=MaterialTheme.shapes.medium, textStyle=MaterialTheme.typography.bodyMedium,
        colors=OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor=MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor=MaterialTheme.colorScheme.surfaceVariant,
            unfocusedBorderColor=Color.Transparent))
}

// SwiftDrop @tweak Codex 12/10/2026 Preserve small-text contrast on the blue transfer action.
fun actionCaptionColor(ink:Color):Color=ink.copy(alpha=.94f)

/** A light coral transport button needs a dark glyph in the dark theme. */
fun musicControlInk(accent:Color):Color=
    if(accent.luminance()>.4f) Color(0xFF24181A) else Color.White
