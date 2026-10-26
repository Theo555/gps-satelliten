package de.gpsradar.satellites.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
    onOpenDonation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("about_screen")
    ) {
        AboutCard("Über diese App") {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = buildString {
                    append("Version ").append(versionName)
                    if (versionCode > 0) append(" · Code ").append(versionCode)
                },
                fontSize = 12.sp,
                color = AccentCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            AboutParagraph(
                "Diese App wurde von einem 70-jährigen Entwickler aus Frust über " +
                    "überladene, werbeverseuchte und datenhungrige Apps gebaut. " +
                    "Sie ist ein reines Werkzeug für GNSS-Enthusiasten und Geocacher."
            )
            Spacer(modifier = Modifier.height(4.dp))
            AboutBullet(
                "100 % Datenschutz:",
                "Keine Internet-Berechtigung, keine Tracker. Standort, Wegpunkte und " +
                    "Tracks bleiben auf dem Gerät."
            )
            AboutBullet(
                "Ehrliche Daten:",
                "Was die Hardware nicht liefert, wird als „–“ angezeigt. Keine Fantasiewerte."
            )
            AboutBullet(
                "Kostenlos & frei:",
                "Uneingeschränkt nutzbar – ohne Werbung, ohne Abos."
            )
        }

        AboutCard("Rohdaten & Messung") {
            AboutParagraph(
                "Der Messung-Tab zeigt pro Satellit und Frequenzband die Roh-Messungen des " +
                    "GNSS-Chipsatzes. Der CSV-Export enthält automatisch die letzten 60 Sekunden; " +
                    "für längere Reihen gibt es „5 Min. aufzeichnen“. Wie viele Werte ankommen, " +
                    "hängt vom Gerät ab: bei schwacher Sicht oder im Stromsparmodus des Chips " +
                    "fehlen Code-Lock, TOW oder ganze Signale."
            )
            Spacer(modifier = Modifier.height(4.dp))
            AboutBullet(
                "Mehr Rohdaten:",
                "In den Android-Entwickleroptionen gibt es „Vollst. GNSS-Messung erzwingen“. " +
                    "Damit trackt der Chip dauerhaft vollständig – auf Kosten des Akkuverbrauchs."
            )
            AboutBullet(
                "Pseudorange:",
                "Wird nur berechnet, wenn die Zeitbasis stimmt. Für GLONASS und BeiDou " +
                    "bleibt sie deshalb leer – die App schätzt nichts."
            )
        }

        AboutCard("Unterstützung") {
            AboutParagraph(
                "Wenn dir diese App gefällt und du die Weiterentwicklung unterstützen " +
                    "möchtest, freue ich mich über eine kleine freiwillige Spende. " +
                    "Wenn nicht, nutze die App einfach weiter und hab Spaß damit!"
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tipp: Bei der Zahlung kannst du eine kurze Nachricht hinterlassen " +
                    "(z. B. deinen Geocaching-Namen) – dann weiß ich, von wem die " +
                    "Unterstützung kommt.",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onOpenDonation,
                modifier = Modifier.fillMaxWidth().testTag("about_donate_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.LocalCafe,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Spende für den Entwickler", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "„Buy me a coffee“ – freiwillig, jederzeit und ohne Gegenleistung.",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )
        }

        AboutCard("Technik") {
            AboutBullet("Sprache & UI:", "Kotlin 2.2 · Jetpack Compose (Material 3)")
            AboutBullet("System:", "Android 7.0 oder neuer")
            AboutBullet("GNSS:", "GnssStatus, GnssMeasurements, GnssNavigationMessage, LocationManager")
            AboutBullet("Sensoren:", "Kompass aus Beschleunigungs- und Magnetfeldsensor")
            AboutBullet("Karte:", "wird an eine installierte Karten-App übergeben")
        }

        AboutCard("Lizenz") {
            Text(
                text = "MIT-Lizenz · © 2026 TK",
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1)
            )
            Text(
                text = "Der Quelltext ist frei einsehbar und darf unter den Bedingungen " +
                    "der MIT-Lizenz weiterverwendet werden.",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 16.sp
            )
            TextButton(
                onClick = onOpenRepository,
                modifier = Modifier.testTag("about_repository_btn")
            ) {
                Text("Quellcode auf GitHub", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
