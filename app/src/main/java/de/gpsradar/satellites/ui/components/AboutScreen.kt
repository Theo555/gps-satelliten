package de.gpsradar.satellites.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.ui.theme.AccentCyan
import de.gpsradar.satellites.ui.theme.CardNavy

/**
 * "Über die App" — eigene Seite wie Radar/Signale/…, erreichbar über das
 * Fragezeichen in der Kopfzeile. Bewusst ohne eigenen Eintrag in der unteren
 * Leiste, damit diese bei ihren sechs Zielen bleibt.
 */
@Composable
fun AboutScreen(
    versionName: String,
    versionCode: Long,
    onOpenRepository: () -> Unit,
    onOpenReadme: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("about_screen")
    ) {
        AboutCard(stringResource(R.string.about_title)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = if (versionCode > 0) {
                    stringResource(R.string.about_version_code, versionName, versionCode)
                } else {
                    stringResource(R.string.about_version, versionName)
                },
                fontSize = 12.sp,
                color = AccentCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            AboutParagraph(stringResource(R.string.about_intro))
            Spacer(modifier = Modifier.height(4.dp))
            AboutBullet(
                stringResource(R.string.about_privacy_label),
                stringResource(R.string.about_privacy_text)
            )
            AboutBullet(
                stringResource(R.string.about_honest_label),
                stringResource(R.string.about_honest_text)
            )
            AboutBullet(
                stringResource(R.string.about_free_label),
                stringResource(R.string.about_free_text)
            )
        }

        AboutCard(stringResource(R.string.about_raw_title)) {
            AboutParagraph(stringResource(R.string.about_raw_para))
            Spacer(modifier = Modifier.height(4.dp))
            AboutBullet(
                stringResource(R.string.about_raw_more_label),
                stringResource(R.string.about_raw_more_text)
            )
            AboutBullet(
                stringResource(R.string.about_pseudorange_label),
                stringResource(R.string.about_pseudorange_text)
            )
        }

        AboutCard(stringResource(R.string.about_docs_title)) {
            AboutParagraph(stringResource(R.string.about_docs_para))
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onOpenReadme,
                modifier = Modifier.testTag("about_readme_btn")
            ) {
                Text(
                    stringResource(R.string.about_docs_btn),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        AboutCard(stringResource(R.string.about_tech_title)) {
            AboutBullet(
                stringResource(R.string.about_tech_lang_label),
                "Kotlin 2.2 · Jetpack Compose (Material 3)"
            )
            AboutBullet(
                stringResource(R.string.about_tech_system_label),
                stringResource(R.string.about_tech_system_text)
            )
            AboutBullet(
                stringResource(R.string.about_tech_gnss_label),
                "GnssStatus, GnssMeasurements, GnssNavigationMessage, LocationManager"
            )
            AboutBullet(
                stringResource(R.string.about_tech_sensors_label),
                stringResource(R.string.about_tech_sensors_text)
            )
            AboutBullet(
                stringResource(R.string.about_tech_map_label),
                stringResource(R.string.about_tech_map_text)
            )
        }

        AboutCard(stringResource(R.string.about_license_title)) {
            Text(
                text = stringResource(R.string.about_license_line),
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1)
            )
            Text(
                text = stringResource(R.string.about_license_text),
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 16.sp
            )
            TextButton(
                onClick = onOpenRepository,
                modifier = Modifier.testTag("about_repository_btn")
            ) {
                Text(
                    stringResource(R.string.about_license_btn),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun AboutParagraph(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = Color(0xFFCBD5E1),
        lineHeight = 17.sp
    )
}

@Composable
private fun AboutBullet(label: String, text: String) {
    Row(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(text = "•", fontSize = 12.sp, color = AccentCyan)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                    append(label)
                }
                append(" ")
                append(text)
            },
            fontSize = 12.sp,
            color = Color(0xFFCBD5E1),
            lineHeight = 17.sp
        )
    }
}
