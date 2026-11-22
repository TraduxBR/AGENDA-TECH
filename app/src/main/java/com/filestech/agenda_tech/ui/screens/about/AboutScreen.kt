package com.filestech.agenda_tech.ui.screens.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.filestech.agenda_tech.ui.theme.LogoShape
import com.filestech.agenda_tech.R
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * About screen, mirroring the Files Tech design language (cf. SMS Tech / PDF Tech): centered icon
 * header with a version pill, a privacy "badges" card, grouped feature cards, the security posture,
 * the permission list with a plain-language justification for each, then links and legal credits.
 *
 * Every claim here is verifiable from the manifest and the public source — the app declares no
 * Internet permission, so all links hand off to the browser via `ACTION_VIEW`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            HeaderBlock(onCheckUpdate = { openUrl(context, URL_RELEASES) })

            Spacer(Modifier.size(28.dp))
            SectionTitle(stringResource(R.string.about_privacy_title))
            Spacer(Modifier.size(8.dp))
            PrivacyCard()

            Spacer(Modifier.size(24.dp))
            SectionTitle(stringResource(R.string.about_features_title))
            Spacer(Modifier.size(8.dp))
            features().forEach { FeatureRow(icon = it.icon, label = it.label, desc = it.desc) }

            Spacer(Modifier.size(24.dp))
            SectionTitle(stringResource(R.string.about_security_title))
            Spacer(Modifier.size(8.dp))
            SecurityCard()

            Spacer(Modifier.size(24.dp))
            SectionTitle(stringResource(R.string.about_permissions_title))
            Spacer(Modifier.size(8.dp))
            permissions().forEach { PermissionLine(name = it.name, why = it.why) }

            Spacer(Modifier.size(24.dp))
            SectionTitle(stringResource(R.string.about_author_title))
            Spacer(Modifier.size(8.dp))
            AuthorCard()

            Spacer(Modifier.size(24.dp))
            SectionTitle(stringResource(R.string.about_links_title))
            Spacer(Modifier.size(8.dp))
            LinkItem(
                icon = Icons.Outlined.Code,
                label = stringResource(R.string.about_source),
                trailing = Icons.Outlined.Public,
                onClick = { openUrl(context, URL_SOURCE) },
            )
            LinkItem(
                icon = Icons.Outlined.BugReport,
                label = stringResource(R.string.about_report_issue),
                onClick = { reportIssue(context) },
            )
            LinkItem(
                icon = Icons.Outlined.Language,
                label = stringResource(R.string.about_website),
                supporting = URL_WEBSITE,
                onClick = { openUrl(context, URL_WEBSITE) },
            )
            LinkItem(
                icon = Icons.Outlined.Gavel,
                label = stringResource(R.string.about_license),
                supporting = stringResource(R.string.about_license_name),
                onClick = { openUrl(context, URL_LICENSE) },
            )
            // Localised URL: the French app must point at PRIVACY.fr.md, the English one at
            // PRIVACY.md. Reading it as a resource lets Android pick, rather than a branch here.
            val privacyPolicyUrl = stringResource(R.string.about_privacy_policy_url)
            LinkItem(
                icon = Icons.Outlined.PrivacyTip,
                label = stringResource(R.string.about_privacy_policy),
                onClick = { openUrl(context, privacyPolicyUrl) },
            )
            // Same mechanism as the privacy policy: one resource per language, so each language
            // opens its own TERMS.xx.md. tools/check-translations.py fails the build if one of those
            // files does not exist.
            val termsUrl = stringResource(R.string.about_terms_url)
            LinkItem(
                icon = Icons.Outlined.Description,
                label = stringResource(R.string.about_terms),
                onClick = { openUrl(context, termsUrl) },
            )

            Spacer(Modifier.size(24.dp))
            // A thank-you to those who report, test, suggest and translate, just above "Made with care by", as in
            // SMS Tech (Patrice, 2026-11-22). It names no one: see the comment on `about_thanks_body`.
            ThanksNote()
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.about_credits_body, AUTHOR_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.about_legal_body, COPYRIGHT_YEAR, AUTHOR_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(24.dp))
        }
    }
}

// --- Thank-you note -----------------------------------------------------------

/**
 * The thank-you note, framed by a light grey border that sets it apart from "Made with care by" and the
 * legal line below, and headed by folded hands — the same note as SMS Tech's and Notes Tech's.
 *
 * The folded hands are the emoji U+1F64F (Unicode 6.0, on every Android version this app supports): the
 * Material icon set has none. It is decorative, so a screen reader reads only the text. The border follows
 * the theme (`outlineVariant`: light grey in light mode, discreet in dark mode).
 */
@Composable
private fun ThanksNote() {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // An emoji keeps its own colours whatever the text colour. WHITE hands with a grey outline, and a line
        // between the two hands, in three layers:
        // - the silhouette in grey, drawn all around itself: the outline;
        // - the emoji brought close to white, its inner strokes kept pale (see [towardsWhite]);
        // - the join of the hands, wiped out with the other strokes: a grey line down the middle, clipped to the
        //   emoji's shape (`DstIn`). Every font draws 🙏 symmetric, the hands joined at the centre.
        // A mid grey in both themes: `outline` is the frame's light grey, too pale for an outline, and the
        // secondary text grey at full strength made the hands look heavy.
        val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = OUTLINE_OPACITY)
        Text(
            text = FOLDED_HANDS,
            fontSize = 22.sp,
            modifier = Modifier
                .clearAndSetSemantics {}
                .drawWithCache {
                    val width = OUTLINE_WIDTH.toPx()
                    val area = Rect(-width, -width, size.width + width, size.height + width)
                    val outlinePaint = Paint().apply { colorFilter = ColorFilter.tint(outline, BlendMode.SrcIn) }
                    val fillPaint = Paint().apply { colorFilter = ColorFilter.colorMatrix(towardsWhite(FLOOR)) }
                    val maskPaint = Paint().apply { blendMode = BlendMode.DstIn }
                    val middle = size.width / 2
                    val offsets = (0 until OUTLINE_DIRECTIONS).map { i ->
                        val angle = 2 * PI * i / OUTLINE_DIRECTIONS
                        Offset((cos(angle) * width).toFloat(), (sin(angle) * width).toFloat())
                    }
                    onDrawWithContent {
                        drawIntoCanvas { canvas ->
                            offsets.forEach { offset ->
                                canvas.saveLayer(area, outlinePaint)
                                translate(offset.x, offset.y) { this@onDrawWithContent.drawContent() }
                                canvas.restore()
                            }
                            canvas.saveLayer(area, fillPaint)
                            drawContent()
                            canvas.restore()
                            canvas.saveLayer(area, Paint())
                            drawLine(outline, Offset(middle, 0f), Offset(middle, size.height), JOIN_WIDTH.toPx())
                            canvas.saveLayer(area, maskPaint)
                            drawContent()
                            canvas.restore()
                            canvas.restore()
                        }
                    }
                },
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.about_thanks_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Folded hands, U+1F64F (UTF-8 source). */
private const val FOLDED_HANDS = "🙏"

/** The grey outline: its width, its strength, and the directions it is drawn in around the emoji. */
private val OUTLINE_WIDTH = 1.5.dp
private const val OUTLINE_OPACITY = 0.6f
private const val OUTLINE_DIRECTIONS = 12

/** The width of the line between the two hands, in the outline's grey. */
private val JOIN_WIDTH = 1.dp

/** The darkest grey the fill keeps — its inner strokes, pale: 0 would keep black, 1 a flat white. */
private const val FLOOR = 0.72f

/**
 * A matrix that brings an image close to WHITE while keeping its relief, pale: each pixel takes its luminance
 * (the weights of `ColorMatrix.setToSaturation(0)`), squeezed into [[floor], 1] — light turns white, dark strokes
 * a light grey. Alpha is kept, so the shape stays the emoji's.
 *
 * ⚠️ The offset column is in 0-255 units, as Android's `ColorMatrix` reads it (Compose passes the values as is).
 */
internal fun towardsWhite(floor: Float): ColorMatrix {
    val range = 1f - floor
    val values = FloatArray(MATRIX_COLUMNS * MATRIX_ROWS)
    for (row in 0 until ALPHA_ROW) {
        values[row * MATRIX_COLUMNS] = LUMA_R * range
        values[row * MATRIX_COLUMNS + 1] = LUMA_G * range
        values[row * MATRIX_COLUMNS + 2] = LUMA_B * range
        values[row * MATRIX_COLUMNS + OFFSET_COLUMN] = floor * MAX_8_BITS
    }
    // Row A: the input alpha, unchanged.
    values[ALPHA_ROW * MATRIX_COLUMNS + ALPHA_ROW] = 1f
    return ColorMatrix(values)
}

// Android's luminance weights (`ColorMatrix.setSaturation(0)`), and the shape of a 4 × 5 colour matrix.
private const val LUMA_R = 0.213f
private const val LUMA_G = 0.715f
private const val LUMA_B = 0.072f
private const val MATRIX_ROWS = 4
private const val MATRIX_COLUMNS = 5
private const val ALPHA_ROW = 3
private const val OFFSET_COLUMN = 4
private const val MAX_8_BITS = 255f

// --- Header -----------------------------------------------------------------

@Composable
private fun HeaderBlock(onCheckUpdate: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.size(80.dp).clip(LogoShape),
        )
        Spacer(Modifier.size(14.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.size(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(cs.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                text = versionName(context),
                color = cs.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(14.dp))
        // Opens the releases page in the browser — the app itself never talks to the network.
        Button(onClick = onCheckUpdate) {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.about_check_update))
        }
    }
}

// --- Privacy ----------------------------------------------------------------

@Composable
private fun PrivacyCard() {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        // Same light blue-grey as every other panel; the coloured icon and bold title carry the
        // emphasis, and the hairline outline keeps the card readable against the near-white page.
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = PrivacyGreen,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.about_privacy_card_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = cs.onSurface,
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.about_privacy_body),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            BadgesFlow(privacyBadges())
        }
    }
}

@Composable
private fun BadgesFlow(badges: List<PrivacyBadge>) {
    // Split into two balanced rows — keeps the layout stable without pulling in FlowRow.
    val mid = (badges.size + 1) / 2
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(badges.take(mid), badges.drop(mid)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { Badge(icon = it.icon, label = it.label, color = it.color) }
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(width = 1.dp, color = color.copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(5.dp))
        Text(text = label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// --- Features ---------------------------------------------------------------

@Composable
private fun FeatureRow(icon: ImageVector, label: String, desc: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        // Outlined: the default card container is white in the light theme, same as the page.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            },
            headlineContent = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                        .copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                )
            },
            supportingContent = {
                Text(text = desc, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

// --- Security ---------------------------------------------------------------

@Composable
private fun SecurityCard() {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        // Same light blue-grey as every other panel; the coloured icon and bold title carry the
        // emphasis, and the hairline outline keeps the card readable against the near-white page.
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.about_security_card_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = cs.onSurface,
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.about_security_body),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// --- Permissions ------------------------------------------------------------

@Composable
private fun PermissionLine(name: String, why: String) {
    ListItem(
        headlineContent = { Text(name, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = { Text(why, style = MaterialTheme.typography.bodySmall) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

// --- Author -----------------------------------------------------------------

@Composable
private fun AuthorCard() {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        ListItem(
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = cs.primary)
                }
            },
            headlineContent = { Text(AUTHOR_NAME) },
            supportingContent = { Text(stringResource(R.string.about_author_role)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

// --- Links ------------------------------------------------------------------

@Composable
private fun LinkItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    supporting: String? = null,
    trailing: ImageVector? = null,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = trailing?.let { { Icon(it, contentDescription = null) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

// --- Helpers ----------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 2.dp),
    )
}

private fun versionName(context: Context): String {
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val name = info?.versionName ?: "?"
    val code = info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    return context.getString(R.string.about_version, name, code)
}

private fun reportIssue(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL")).apply {
        putExtra(
            Intent.EXTRA_SUBJECT,
            "${context.getString(R.string.app_name)} ${versionName(context)}",
        )
    }
    safeStartActivity(context, intent)
}

private fun openUrl(context: Context, url: String) {
    safeStartActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

/** Never crash when no app can handle the intent (e.g. no browser / no mail client installed). */
private fun safeStartActivity(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Timber.w(it, "AboutScreen: no handler for %s", intent.action) }
}

// --- Static data ------------------------------------------------------------

private val PrivacyGreen = Color(0xFF43A047)

private const val AUTHOR_NAME = "Patrice Haltaya"
private const val COPYRIGHT_YEAR = "2026"
private const val CONTACT_EMAIL = "contact@files-tech.com"
private const val URL_WEBSITE = "https://files-tech.com"
private const val URL_SOURCE = "https://github.com/gitubpatrice/AGENDA-TECH"
private const val URL_RELEASES = "https://github.com/gitubpatrice/AGENDA-TECH/releases/latest"
private const val URL_LICENSE = "https://www.apache.org/licenses/LICENSE-2.0"

private data class PrivacyBadge(val icon: ImageVector, val label: String, val color: Color)

@Composable
private fun privacyBadges(): List<PrivacyBadge> = listOf(
    PrivacyBadge(Icons.Outlined.CloudOff, stringResource(R.string.about_badge_no_network), Color(0xFF1976D2)),
    PrivacyBadge(Icons.Outlined.Block, stringResource(R.string.about_badge_no_ads), Color(0xFFE53935)),
    PrivacyBadge(Icons.Outlined.VisibilityOff, stringResource(R.string.about_badge_no_tracker), Color(0xFFFF7043)),
    PrivacyBadge(Icons.Outlined.PrivacyTip, stringResource(R.string.about_badge_no_account), Color(0xFF7B1FA2)),
)

private data class Feature(val icon: ImageVector, val label: String, val desc: String)

@Composable
private fun features(): List<Feature> = listOf(
    Feature(
        Icons.Outlined.CalendarMonth,
        stringResource(R.string.about_feat_views_label),
        stringResource(R.string.about_feat_views_desc),
    ),
    Feature(
        Icons.Outlined.Flag,
        stringResource(R.string.about_feat_holidays_label),
        stringResource(R.string.about_feat_holidays_desc),
    ),
    Feature(
        Icons.Outlined.Repeat,
        stringResource(R.string.about_feat_recurrence_label),
        stringResource(R.string.about_feat_recurrence_desc),
    ),
    Feature(
        Icons.Outlined.Alarm,
        stringResource(R.string.about_feat_reminders_label),
        stringResource(R.string.about_feat_reminders_desc),
    ),
    Feature(
        Icons.Outlined.ImportExport,
        stringResource(R.string.about_feat_import_label),
        stringResource(R.string.about_feat_import_desc),
    ),
    Feature(
        Icons.Outlined.Share,
        stringResource(R.string.about_feat_share_label),
        stringResource(R.string.about_feat_share_desc),
    ),
    Feature(
        Icons.Outlined.Widgets,
        stringResource(R.string.about_feat_widgets_label),
        stringResource(R.string.about_feat_widgets_desc),
    ),
    Feature(
        Icons.Outlined.Fingerprint,
        stringResource(R.string.about_feat_lock_label),
        stringResource(R.string.about_feat_lock_desc),
    ),
    Feature(
        Icons.Outlined.Lock,
        stringResource(R.string.about_feat_encryption_label),
        stringResource(R.string.about_feat_encryption_desc),
    ),
    Feature(
        Icons.Outlined.DarkMode,
        stringResource(R.string.about_feat_theme_label),
        stringResource(R.string.about_feat_theme_desc),
    ),
    Feature(
        Icons.Outlined.Translate,
        stringResource(R.string.about_feat_languages_label),
        stringResource(R.string.about_feat_languages_desc),
    ),
)

private data class PermissionInfo(val name: String, val why: String)

@Composable
private fun permissions(): List<PermissionInfo> = listOf(
    PermissionInfo("READ_CALENDAR", stringResource(R.string.about_perm_read_calendar)),
    PermissionInfo("POST_NOTIFICATIONS", stringResource(R.string.about_perm_notifications)),
    PermissionInfo("USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM", stringResource(R.string.about_perm_exact_alarm)),
    PermissionInfo("RECEIVE_BOOT_COMPLETED", stringResource(R.string.about_perm_boot)),
    PermissionInfo("VIBRATE", stringResource(R.string.about_perm_vibrate)),
    PermissionInfo(stringResource(R.string.about_perm_no_internet_name), stringResource(R.string.about_perm_no_internet)),
)
