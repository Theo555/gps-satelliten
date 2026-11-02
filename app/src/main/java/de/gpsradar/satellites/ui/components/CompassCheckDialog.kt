package de.gpsradar.satellites.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.CompassIssue
import de.gpsradar.satellites.model.CompassSpinState
import de.gpsradar.satellites.model.CompassWalkState
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.SpinVerdict
import java.util.Locale
import kotlin.math.roundToInt

private val AmberWarning = Color(0xFFF59E0B)

/**
 * Prüfung der Kompass-Qualität: Feldbetrag und Drehtest (Magnet gegen Kreisel).
 *
 * Reine Diagnose – es wird nichts korrigiert, nichts gespeichert und nichts an
 * der Pfeilreferenz geändert. Der Vorher/Nachher-Vergleich hilft zu erkennen,
 * ob eine Maßnahme (Hülle ab, 8er-Bewegung) gewirkt hat.
 */
@Composable
fun CompassCheckDialog(
    state: GpsLocationState,
    spin: CompassSpinState,
    walk: CompassWalkState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStartWalk: () -> Unit,
    onStopWalk: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = {
        onStop()
        onStopWalk()
        onDismiss()
    }) {
        // Solange geprüft wird, darf der Bildschirm nicht ausgehen: Im
        // Hintergrund werden die Sensoren abgemeldet und der Test stirbt mitten
        // in der Drehung. Am Gerät genau so aufgetreten (02.11.).
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("compass_check_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.compass_check_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                FieldLine(state)

                when {
                    !spin.supported -> Text(
                        text = stringResource(R.string.compass_spin_no_gyro),
                        fontSize = 12.sp,
                        color = AmberWarning
                    )
                    spin.running -> {
                        Text(
                            text = stringResource(R.string.compass_check_instruction),
                            fontSize = 12.sp
                        )
                        Text(
                            text = stringResource(
                                R.string.compass_check_progress,
                                spin.turnDegrees.roundToInt()
                            ),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.compass_check_instruction),
                            fontSize = 12.sp
                        )
                        if (spin.finished) ResultBlock(spin)
                    }
                }

                if (state.compassSuspect || (spin.finished && needsAdvice(spin.verdict))) {
                    HelpBlock()
                }

                WalkSection(walk = walk, onStart = onStartWalk, onStop = onStopWalk)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (spin.running) {
                        TextButton(
                            onClick = onStop,
                            modifier = Modifier.testTag("compass_spin_stop")
                        ) {
                            Text(stringResource(R.string.compass_check_stop))
                        }
                    } else {
                        TextButton(
                            onClick = onStart,
                            enabled = spin.supported,
                            modifier = Modifier.testTag("compass_spin_start")
                        ) {
                            Text(
                                text = stringResource(
                                    if (spin.finished) R.string.compass_check_repeat
                                    else R.string.compass_check_start
                                )
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            onStop()
                            onStopWalk()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("compass_check_close")
                    ) {
                        Text(stringResource(R.string.compass_check_close))
                    }
                }

                Text(
                    text = stringResource(R.string.compass_check_limit),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Ratschläge helfen nur, wenn das Gerät oder die Hülle verdächtig ist. */
private fun needsAdvice(verdict: SpinVerdict): Boolean =
    verdict == SpinVerdict.DOUBTFUL || verdict == SpinVerdict.CONTRADICTORY

/**
 * Geh-Test: zeichnet auf, was die Anzeige sagt und welchen Kurs das GPS misst.
 *
 * Der Drehtest prüft den Sensor gegen den Kreisel – der Geh-Test prüft die
 * Anzeige gegen eine unabhängige Wahrheit (den GPS-Kurs). Nur so lässt sich
 * ein gleichmäßiger Fehler finden, den der Drehtest grundsätzlich nicht sieht.
 */
@Composable
private fun WalkSection(
    walk: CompassWalkState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Text(
        text = stringResource(R.string.compass_walk_title),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
    Text(
        text = stringResource(R.string.compass_walk_instruction),
        fontSize = 12.sp
    )
    when {
        walk.running -> Text(
            text = stringResource(R.string.compass_walk_running, walk.secondsLeft, walk.samples),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        walk.finished -> WalkResult(walk)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        if (walk.running) {
            TextButton(onClick = onStop, modifier = Modifier.testTag("compass_walk_stop")) {
                Text(stringResource(R.string.compass_walk_stop))
            }
        } else {
            TextButton(onClick = onStart, modifier = Modifier.testTag("compass_walk_start")) {
                Text(stringResource(R.string.compass_walk_start))
            }
        }
    }
}

/** Ergebnis des Geh-Tests: zuerst der Vergleich mit dem GPS-Kurs. */
@Composable
private fun WalkResult(walk: CompassWalkState) {
    val course = walk.shownVsCourseDegrees
    val magnetic = walk.shownVsMagneticDegrees
    if (course == null && magnetic == null) {
        Text(
            text = stringResource(R.string.compass_walk_empty),
            fontSize = 12.sp,
            color = AmberWarning
        )
        return
    }
    if (course != null) {
        Text(
            text = stringResource(
                R.string.compass_walk_result_course,
                signedDegrees(course),
                walk.courseSamples
            ),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
    if (magnetic != null) {
        Text(
            text = stringResource(R.string.compass_walk_result_magnetic, signedDegrees(magnetic)),
            fontSize = 13.sp
        )
    }
    walk.fileName?.let { name ->
        Text(
            text = stringResource(R.string.compass_walk_saved, name),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Vorzeichen immer mit anzeigen – „+3,8“ und „-2,1“ sollen unterscheidbar sein. */
private fun signedDegrees(value: Float): String =
    String.format(Locale.getDefault(), "%+.1f", value)

/** Gemessener Feldbetrag mit Einordnung. */
@Composable
private fun FieldLine(state: GpsLocationState) {
    val field = state.compassFieldMicroTesla
    val text = when {
        field == null -> stringResource(R.string.compass_check_field_unknown)
        state.compassIssue == CompassIssue.FIELD_TOO_STRONG ->
            stringResource(R.string.compass_issue_field_strong, field)
        state.compassIssue == CompassIssue.FIELD_TOO_WEAK ->
            stringResource(R.string.compass_issue_field_weak, field)
        else -> stringResource(R.string.compass_check_field_ok, field)
    }
    Text(
        text = text,
        fontSize = 12.sp,
        color = if (state.compassSuspect) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Ergebnis des letzten Durchlaufs samt Vergleich zum vorigen. */
@Composable
private fun ResultBlock(spin: CompassSpinState) {
    val deviation = spin.deviationDegrees ?: 0f
    val text = when (spin.verdict) {
        SpinVerdict.OK -> stringResource(R.string.compass_spin_ok, deviation.roundToInt())
        SpinVerdict.DOUBTFUL -> stringResource(R.string.compass_spin_doubtful, deviation.roundToInt())
        SpinVerdict.CONTRADICTORY -> stringResource(R.string.compass_spin_contradictory)
        SpinVerdict.ENVIRONMENT -> stringResource(R.string.compass_spin_environment)
        SpinVerdict.INSUFFICIENT -> stringResource(R.string.compass_spin_insufficient)
    }
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (spin.verdict == SpinVerdict.OK) MaterialTheme.colorScheme.primary else AmberWarning
    )
    // Die gemessene Drehung immer mitschreiben – bei "zu wenig gedreht" ist sie
    // die Drift-Messung, sonst die Gegenprobe zum Urteil.
    Text(
        text = stringResource(R.string.compass_check_progress, spin.turnDegrees.roundToInt()),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val previous = spin.lastDeviationDegrees
    if (previous != null) {
        Text(
            text = stringResource(R.string.compass_spin_previous, previous.roundToInt()),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Was der Nutzer tun kann – die 8er-Bewegung mit Begründung. */
@Composable
private fun HelpBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.compass_check_help_title),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text = stringResource(R.string.compass_check_place_hint), fontSize = 11.sp)
        Text(text = stringResource(R.string.compass_check_help_case), fontSize = 11.sp)
        Text(text = stringResource(R.string.compass_check_help_figure8), fontSize = 11.sp)
        Text(text = stringResource(R.string.compass_check_help_distance), fontSize = 11.sp)
    }
}
