package com.ai.phoneagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.platform.LocalContext
import com.ai.phoneagent.R
import com.ai.phoneagent.core.settings.AppUiPrefs
import com.ai.phoneagent.ui.components.AquariusPageTopBar

/**
 * 「外观 → 语言」界面。
 *
 * 选中即写入 [AppUiPrefs] 并立刻切换应用 Locale：AppCompat 会重建当前 Activity，
 * 因此返回后整站文案（含设置页自身）已是新语言，无需重启应用。
 */
@Composable
fun LanguageScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppUiPrefs.languageTag(context)) }

    fun select(tag: String) {
        if (tag == selected) return
        selected = tag
        AppUiPrefs.setLanguageTag(context, tag)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AquariusPageTopBar(stringResource(R.string.settings_language_title), onNavigateBack) },
    ) { inner ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aquarius_page_padding)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)),
        ) {
            item {
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    LanguageOption(
                        label = stringResource(R.string.settings_language_zh),
                        selected = selected == AppUiPrefs.LANGUAGE_ZH,
                        onClick = { select(AppUiPrefs.LANGUAGE_ZH) },
                    )
                    LanguageOption(
                        label = stringResource(R.string.settings_language_en),
                        selected = selected == AppUiPrefs.LANGUAGE_EN,
                        onClick = { select(AppUiPrefs.LANGUAGE_EN) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
