package com.routehotspots.karoo.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.routehotspots.karoo.ALL_POI_CATEGORIES
import com.routehotspots.karoo.CategoryPrefs
import com.routehotspots.karoo.DetourPrefs
import com.routehotspots.karoo.FeaturePrefs
import com.routehotspots.karoo.R
import com.routehotspots.karoo.theme.AppTheme

/**
 * Eerste scherm na "OPEN" vanuit de Karoo-extensielijst: terug-knop + een
 * korte uitlegregel boven de categorie-aan/uit-lijst (F2) — geen apart
 * openingsscherm, de uitleg staat gewoon bovenaan dit ene scherm. Elke tik
 * op een schakelaar schrijft meteen naar [CategoryPrefs] (geen opslaan-knop)
 * — [com.routehotspots.karoo.RouteHotspotsExtension] leest dat bij de
 * eerstvolgende route-query.
 */
@Composable
fun MainScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(CategoryPrefs.loadEnabled(context)) }
    var featureOn by remember { mutableStateOf(FeaturePrefs.isEnabled(context)) }
    var maxDetour by remember { mutableStateOf(DetourPrefs.maxDetourMeters(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(onBack = onBack)
        Text(
            text = stringResource(id = R.string.onboarding_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = featureOn,
                    onValueChange = { checked ->
                        featureOn = checked
                        FeaturePrefs.setEnabled(context, checked)
                    },
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(id = R.string.feature_toggle_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = featureOn, onCheckedChange = null)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (featureOn) 1f else 0.4f),
        ) {
            item(key = "detour-header") {
                Text(
                    text = stringResource(id = R.string.detour_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                )
                Text(
                    text = stringResource(id = R.string.detour_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            items(DetourPrefs.OPTIONS, key = { "detour-${it.first}" }) { (meters, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = maxDetour == meters,
                            enabled = featureOn,
                            onValueChange = {
                                maxDetour = meters
                                DetourPrefs.setMaxDetourMeters(context, meters)
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    RadioButton(selected = maxDetour == meters, onClick = null, enabled = featureOn)
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            item(key = "categories-header") {
                Text(
                    text = stringResource(id = R.string.categories_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(ALL_POI_CATEGORIES, key = { it.key }) { category ->
                val isOn = category.key in enabled
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = isOn,
                            enabled = featureOn,
                            onValueChange = { checked ->
                                val next = if (checked) enabled + category.key else enabled - category.key
                                enabled = next
                                CategoryPrefs.setEnabled(context, next)
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(text = category.emoji, modifier = Modifier.padding(end = 12.dp))
                    Text(
                        text = stringResource(id = category.labelRes),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = isOn, onCheckedChange = null, enabled = featureOn)
                }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Text(
                text = "←",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Preview(
    widthDp = 256,
    heightDp = 426,
)
@Composable
fun DefaultPreview() {
    AppTheme {
        MainScreen()
    }
}
