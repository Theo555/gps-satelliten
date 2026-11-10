package de.gpsradar.satellites.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.CACHE_TYPES
import de.gpsradar.satellites.model.Coordinates
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.model.cacheTypeEmoji
import java.util.Locale
@Composable
internal fun AddWaypointDialog(
    onConfirm: (name: String, code: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.btn_save_position)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dlg_waypoint_name),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ph_parking)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dlg_waypoint_code),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ph_gc)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, code) }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}


@Composable
internal fun ManualWaypointDialog(
    onConfirm: (name: String, code: String?, latitude: Double, longitude: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var coords by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val errorCoordinates = stringResource(R.string.error_coordinates)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dlg_manual_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dlg_waypoint_name),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ph_final_stage)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dlg_waypoint_code),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ph_gc)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dlg_coordinates),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = coords,
                    onValueChange = { coords = it; error = null },
                    singleLine = true,
                    isError = error != null,
                    placeholder = { Text(stringResource(R.string.ph_coordinates)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (error != null) {
                    Text(
                        text = error!!,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = stringResource(R.string.dlg_formats_examples),
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = Coordinates.parse(coords)
                if (parsed == null) {
                    error = errorCoordinates
                } else {
                    onConfirm(name, code, parsed.latitude, parsed.longitude)
                }
            }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}


@Composable
internal fun EditWaypointDialog(
    waypoint: Waypoint,
    onConfirm: (Waypoint) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(waypoint.name) }
    var code by remember { mutableStateOf(waypoint.code ?: "") }
    var coords by remember {
        mutableStateOf(String.format(Locale.US, "%.6f, %.6f", waypoint.latitude, waypoint.longitude))
    }
    var hint by remember { mutableStateOf(waypoint.hint ?: "") }
    var difficulty by remember { mutableStateOf(waypoint.difficulty?.toString() ?: "") }
    var terrain by remember { mutableStateOf(waypoint.terrain?.toString() ?: "") }
    var cacheType by remember { mutableStateOf(waypoint.cacheType ?: "") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val errorCoordinates = stringResource(R.string.error_coordinates)
    val defaultWaypointName = stringResource(R.string.default_waypoint_name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dlg_edit_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dlg_name), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.dlg_waypoint_code), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.dlg_coordinates), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = coords,
                    onValueChange = { coords = it; error = null },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.dlg_hint), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    singleLine = false,
                    placeholder = { Text(stringResource(R.string.ph_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = difficulty,
                        onValueChange = { difficulty = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_difficulty), fontSize = 11.sp) },
                        placeholder = { Text(stringResource(R.string.ph_rating)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = terrain,
                        onValueChange = { terrain = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_terrain), fontSize = 11.sp) },
                        placeholder = { Text(stringResource(R.string.ph_rating)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.dlg_cache_type), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedActionButton(
                        onClick = { typeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (cacheType.isBlank()) {
                                stringResource(R.string.cache_type_choose)
                            } else {
                                "${cacheTypeEmoji(cacheType)} $cacheType"
                            },
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cache_type_none)) },
                            onClick = { cacheType = ""; typeMenuExpanded = false }
                        )
                        CACHE_TYPES.forEach { type ->
                            DropdownMenuItem(
                                text = { Text("${cacheTypeEmoji(type)} $type") },
                                onClick = { cacheType = type; typeMenuExpanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (error != null) {
                    Text(error!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(stringResource(R.string.dlg_formats_short), fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = Coordinates.parse(coords)
                if (parsed == null) {
                    error = errorCoordinates
                } else {
                    onConfirm(
                        waypoint.copy(
                            name = name.ifBlank { defaultWaypointName },
                            code = code.trim().ifBlank { null },
                            latitude = parsed.latitude,
                            longitude = parsed.longitude,
                            hint = hint.trim().ifBlank { null },
                            difficulty = difficulty.toDoubleOrNull()?.takeIf { it in 1.0..5.0 },
                            terrain = terrain.toDoubleOrNull()?.takeIf { it in 1.0..5.0 },
                            cacheType = cacheType.trim().ifBlank { null }
                        )
                    )
                }
            }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}


@Composable
internal fun ProjectionDialog(
    baseName: String?,
    onConfirm: (name: String, bearingDegrees: Double, distanceMeters: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var bearing by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val errorProjection = stringResource(R.string.error_projection)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dlg_projection_title)) },
        text = {
            Column {
                if (baseName == null) {
                    Text(
                        text = stringResource(R.string.proj_no_base),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(stringResource(R.string.proj_base, baseName), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.proj_bearing), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bearing,
                        onValueChange = { bearing = it; error = null },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.ph_bearing)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.proj_distance), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = distance,
                        onValueChange = { distance = it; error = null },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.ph_distance)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.proj_new_name), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (error != null) {
                        Text(error!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = baseName != null,
                onClick = {
                    val b = bearing.toDoubleOrNull()
                    val d = distance.toDoubleOrNull()
                    if (b == null || b !in 0.0..360.0 || d == null || d <= 0.0) {
                        error = errorProjection
                    } else {
                        onConfirm(name, b, d)
                    }
                }
            ) {
                Text(stringResource(R.string.btn_calculate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}


internal fun openInMap(context: Context, waypoint: Waypoint) {
    val uri = Uri.parse(
        "geo:${waypoint.latitude},${waypoint.longitude}" +
            "?q=${waypoint.latitude},${waypoint.longitude}(${Uri.encode(waypoint.name)})"
    )
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_no_map_app), Toast.LENGTH_SHORT).show()
    }
}
