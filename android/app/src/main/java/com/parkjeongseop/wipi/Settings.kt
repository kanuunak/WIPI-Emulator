// 설정 (SharedPreferences) + 설정/오픈소스 고지 플로우.
// 화면은 Material 3 표준 설정 패턴: Scaffold + TopAppBar(뒤로가기) + ListItem 행 +
// 컬러 카테고리 서브헤더. 변경은 즉시 적용되므로 "완료" 버튼 없음 (Android 관례).
// 기능·항목 구성(사운드/진동/정보, 고지 목록→상세)은 iOS와 동일.

package com.parkjeongseop.wipi

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlin.math.roundToInt

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var soundEnabled: Boolean
        get() = prefs.getBoolean("soundEnabled", true)
        set(value) = prefs.edit { putBoolean("soundEnabled", value) }

    var pcmVolume: Float
        get() = prefs.getFloat("pcmVolume", 1.0f)
        set(value) = prefs.edit { putFloat("pcmVolume", value) }

    var midiVolume: Float
        get() = prefs.getFloat("midiVolume", 1.0f)
        set(value) = prefs.edit { putFloat("midiVolume", value) }

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean("vibrationEnabled", true)
        set(value) = prefs.edit { putBoolean("vibrationEnabled", value) }

    var vibrationScale: Float
        get() = prefs.getFloat("vibrationScale", 1.0f)
        set(value) = prefs.edit { putFloat("vibrationScale", value) }

    var keypadHaptics: Boolean
        get() = prefs.getBoolean("keypadHaptics", true)
        set(value) = prefs.edit { putBoolean("keypadHaptics", value) }

    /** 게임 화면 배치: 자동(화면 모양으로) / 위아래(STACK) / 좌우(SPLIT) */
    var layoutMode: LayoutMode
        get() = runCatching { LayoutMode.valueOf(prefs.getString("layoutMode", null) ?: "AUTO") }.getOrDefault(LayoutMode.AUTO)
        set(value) = prefs.edit { putString("layoutMode", value.name) }

    /** 게임 화면 필터 */
    var screenFilter: ScreenFilter
        get() = runCatching { ScreenFilter.valueOf(prefs.getString("screenFilter", null) ?: "SHARP") }.getOrDefault(ScreenFilter.SHARP)
        set(value) = prefs.edit { putString("screenFilter", value.name) }

    /** 키패드 테마(프리셋 + 모양·투명도·라벨 크기, [KeypadTheme.serialize]) */
    var keypadTheme: KeypadTheme
        get() = KeypadTheme.parse(prefs.getString("keypadTheme", null)) ?: KeypadTheme.DEFAULT
        set(value) = prefs.edit { putString("keypadTheme", value.serialize()) }

    /** 배치 편집으로 저장한 배치(화면 크기 키별, [CustomLayout.serialize]). 없으면 null */
    fun customLayout(key: String): String? = prefs.getString("customLayout.$key", null)

    /** null이면 삭제(자동 배치로) */
    fun setCustomLayout(key: String, value: String?) = prefs.edit {
        if (value == null) remove("customLayout.$key") else putString("customLayout.$key", value)
    }

    /** 현재 설정 기준 유효 볼륨 (pcm, midi — 사운드 off면 0) */
    fun effectiveVolumes(): Pair<Float, Float> =
        if (soundEnabled) pcmVolume to midiVolume else 0f to 0f
}

// ── 설정 플로우: 설정 ⇄ 고지 목록 ⇄ 라이선스 상세 ──

private enum class SettingsPage { Settings, Licenses }

@Composable
fun SettingsFlow(
    settings: Settings,
    onVolumeChanged: () -> Unit,
    onLayoutModeChanged: (LayoutMode) -> Unit,
    onScreenFilterChanged: (ScreenFilter) -> Unit,
    onKeypadThemeChanged: (KeypadTheme) -> Unit,
    onDiagnostics: () -> Unit,
    onClose: () -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.Settings) }
    var detail by remember { mutableStateOf<License?>(null) }

    BackHandler {
        when {
            detail != null -> detail = null
            page == SettingsPage.Licenses -> page = SettingsPage.Settings
            else -> onClose()
        }
    }

    when {
        detail != null -> LicenseDetailPage(detail!!, onBack = { detail = null })
        page == SettingsPage.Licenses -> LicensesPage(
            onSelect = { detail = it },
            onBack = { page = SettingsPage.Settings },
        )
        else -> SettingsPage(
            settings = settings,
            onVolumeChanged = onVolumeChanged,
            onLayoutModeChanged = onLayoutModeChanged,
            onScreenFilterChanged = onScreenFilterChanged,
            onKeypadThemeChanged = onKeypadThemeChanged,
            onDiagnostics = onDiagnostics,
            onShowLicenses = { page = SettingsPage.Licenses },
            onBack = onClose,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}

/** Material 설정 화면의 카테고리 서브헤더 */
@Composable
private fun CategoryHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(
    settings: Settings,
    onVolumeChanged: () -> Unit,
    onLayoutModeChanged: (LayoutMode) -> Unit,
    onScreenFilterChanged: (ScreenFilter) -> Unit,
    onKeypadThemeChanged: (KeypadTheme) -> Unit,
    onDiagnostics: () -> Unit,
    onShowLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    var soundEnabled by remember { mutableStateOf(settings.soundEnabled) }
    var pcmVolume by remember { mutableStateOf(settings.pcmVolume) }
    var midiVolume by remember { mutableStateOf(settings.midiVolume) }
    var vibrationEnabled by remember { mutableStateOf(settings.vibrationEnabled) }
    var vibrationScale by remember { mutableStateOf(settings.vibrationScale) }
    var keypadHaptics by remember { mutableStateOf(settings.keypadHaptics) }
    var layoutMode by remember { mutableStateOf(settings.layoutMode) }
    var screenFilter by remember { mutableStateOf(settings.screenFilter) }
    var keypadTheme by remember { mutableStateOf(settings.keypadTheme) }

    SettingsScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { modifier ->
        Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CategoryHeader(stringResource(R.string.settings_sound_header))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_sound)) },
                supportingContent = { Text(stringResource(R.string.settings_sound_desc)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) },
                trailingContent = {
                    Switch(checked = soundEnabled, onCheckedChange = {
                        soundEnabled = it
                        settings.soundEnabled = it
                        onVolumeChanged()
                    })
                },
            )
            if (soundEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_music)) },
                    supportingContent = {
                        Slider(value = midiVolume, valueRange = 0f..1f, onValueChange = {
                            midiVolume = it
                            settings.midiVolume = it
                            onVolumeChanged()
                        })
                    },
                    trailingContent = { Text("${(midiVolume * 100).toInt()}%") },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_effects)) },
                    supportingContent = {
                        Column {
                            Slider(value = pcmVolume, valueRange = 0f..1f, onValueChange = {
                                pcmVolume = it
                                settings.pcmVolume = it
                                onVolumeChanged()
                            })
                            Text(
                                stringResource(R.string.settings_sound_footer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingContent = { Text("${(pcmVolume * 100).toInt()}%") },
                )
            }

            CategoryHeader(stringResource(R.string.settings_vibration_header))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_vibration)) },
                supportingContent = { Text(stringResource(R.string.settings_vibration_desc)) },
                leadingContent = { Icon(Icons.Filled.Vibration, null) },
                trailingContent = {
                    Switch(checked = vibrationEnabled, onCheckedChange = {
                        vibrationEnabled = it
                        settings.vibrationEnabled = it
                    })
                },
            )
            if (vibrationEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_vibration_strength)) },
                    supportingContent = {
                        Column {
                            Slider(value = vibrationScale, valueRange = 0f..1.5f, onValueChange = {
                                vibrationScale = it
                                settings.vibrationScale = it
                            })
                            Text(
                                stringResource(R.string.settings_vibration_footer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingContent = { Text("${(vibrationScale * 100).toInt()}%") },
                )
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_keypad_haptics)) },
                supportingContent = { Text(stringResource(R.string.settings_keypad_haptics_desc)) },
                trailingContent = {
                    Switch(checked = keypadHaptics, onCheckedChange = {
                        keypadHaptics = it
                        settings.keypadHaptics = it
                    })
                },
            )

            CategoryHeader(stringResource(R.string.settings_screen_header))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_layout)) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.settings_layout_desc))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            val options = listOf(
                                LayoutMode.AUTO to R.string.settings_layout_auto,
                                LayoutMode.STACK to R.string.settings_layout_stack,
                                LayoutMode.SPLIT to R.string.settings_layout_split,
                            )
                            options.forEachIndexed { i, (mode, label) ->
                                SegmentedButton(
                                    selected = layoutMode == mode,
                                    onClick = {
                                        layoutMode = mode
                                        settings.layoutMode = mode
                                        onLayoutModeChanged(mode)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                ) { Text(stringResource(label)) }
                            }
                        }
                    }
                },
                leadingContent = { Icon(Icons.Filled.ViewQuilt, null) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_filter)) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.settings_filter_desc))
                        val options = listOf(
                            ScreenFilter.SHARP to R.string.settings_filter_sharp,
                            ScreenFilter.SMOOTH to R.string.settings_filter_smooth,
                            ScreenFilter.SCANLINES to R.string.settings_filter_scanlines,
                            ScreenFilter.LCD to R.string.settings_filter_lcd,
                            ScreenFilter.UPSCALE to R.string.settings_filter_upscale,
                        )
                        options.forEach { (filter, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    screenFilter = filter
                                    settings.screenFilter = filter
                                    onScreenFilterChanged(filter)
                                },
                            ) {
                                RadioButton(selected = screenFilter == filter, onClick = null)
                                Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                leadingContent = { Icon(Icons.Filled.Tune, null) },
            )
            KeypadThemeItem(keypadTheme) {
                keypadTheme = it
                settings.keypadTheme = it
                onKeypadThemeChanged(it)
            }

            CategoryHeader(stringResource(R.string.settings_info_header))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_licenses)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.clickable(onClick = onShowLicenses),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_diag_log)) },
                supportingContent = { Text(stringResource(R.string.settings_diag_log_desc)) },
                leadingContent = { Icon(Icons.Filled.Share, null) },
                modifier = Modifier.clickable(onClick = onDiagnostics),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
        }
    }
}

// ── 키패드 테마: 프리셋(미리보기 + 라디오) → 모양·라벨 크기·투명도 (프리셋을 고르면 셋 다 프리셋 기본값으로) ──

private val presetLabels = listOf(
    KeypadPreset.GRAY to R.string.settings_keypad_theme_gray,
    KeypadPreset.FOLDER_BLACK to R.string.settings_keypad_theme_folder_black,
    KeypadPreset.SLIDE_WHITE to R.string.settings_keypad_theme_slide_white,
    KeypadPreset.TRANSLUCENT to R.string.settings_keypad_theme_translucent,
)

/** 미리보기 행: 방향키 둘레 3행(소프트키·방향키·OK). ▲를 눌린 모습으로 그려 눌림 표시도 보여 준다 */
private val previewRows = KeypadModel.UPPER_ROWS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeypadThemeItem(theme: KeypadTheme, onChange: (KeypadTheme) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_keypad_theme)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_keypad_theme_desc))
                // 미리보기: 반투명 테마가 어떻게 보이는지 알 수 있게 게임 화면 같은 무늬 위에 그린다
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(EMULATOR_BG, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    PreviewBackdrop(Modifier.fillMaxSize())
                    KeypadPreview(
                        rows = previewRows,
                        theme = theme,
                        down = setOf("UP"),
                        modifier = Modifier.width(240.dp).height((previewRows.size * 40f + (previewRows.size - 1) * KEYPAD_SPACING_DP + 2 * KEYPAD_MARGIN_DP).dp),
                    )
                }
                presetLabels.forEach { (preset, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onChange(preset.defaults()) },
                    ) {
                        RadioButton(selected = theme.preset == preset, onClick = null)
                        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Text(stringResource(R.string.settings_keypad_shape), modifier = Modifier.padding(top = 8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    val shapes = listOf(
                        KeyShape.ROUNDED to R.string.settings_keypad_shape_rounded,
                        KeyShape.SQUARE to R.string.settings_keypad_shape_square,
                        KeyShape.PILL to R.string.settings_keypad_shape_pill,
                    )
                    shapes.forEachIndexed { i, (shape, label) ->
                        SegmentedButton(
                            selected = theme.shape == shape,
                            onClick = { onChange(theme.copy(shape = shape)) },
                            shape = SegmentedButtonDefaults.itemShape(i, shapes.size),
                        ) { Text(stringResource(label)) }
                    }
                }

                Text(stringResource(R.string.settings_keypad_label_size), modifier = Modifier.padding(top = 8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    val sizes = listOf(
                        LabelSize.SMALL to R.string.settings_keypad_label_small,
                        LabelSize.MEDIUM to R.string.settings_keypad_label_medium,
                        LabelSize.LARGE to R.string.settings_keypad_label_large,
                    )
                    sizes.forEachIndexed { i, (size, label) ->
                        SegmentedButton(
                            selected = theme.labelSize == size,
                            onClick = { onChange(theme.copy(labelSize = size)) },
                            shape = SegmentedButtonDefaults.itemShape(i, sizes.size),
                        ) { Text(stringResource(label)) }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.settings_keypad_opacity), modifier = Modifier.weight(1f))
                    Text("${(theme.opacity * 100).roundToInt()}%")
                }
                Slider(
                    value = theme.opacity,
                    valueRange = KeypadTheme.MIN_OPACITY..1f,
                    onValueChange = { onChange(theme.copy(opacity = it)) },
                )
            }
        },
        leadingContent = { Icon(Icons.Filled.Dialpad, null) },
    )
}

/** 테마 미리보기 뒤 무늬(게임 화면 대신): 굵은 대각선 줄무늬 */
@Composable
private fun PreviewBackdrop(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val step = 24.dp.toPx()
        var x = -size.height
        var i = 0
        while (x < size.width) {
            drawLine(
                color = if (i % 2 == 0) Color(0xFF3A6EA5) else Color(0xFFE0A030),
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = step / 2f,
            )
            x += step
            i++
        }
    }
}

// ── 오픈소스 고지 (iOS와 동일 내용, 목록 → 상세) ──

private fun mit(copyright: String) = """
$copyright

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
""".trimIndent()

private val neodgmLicense = """
Copyright: Original font was released under the public domain by Jungtae Kim in 1990s. Conversion and additional character design by Dalgona <me@dalgona.dev>.

Neo Dunggeunmo (NeoDunggeunmo) 1.530 is licensed under the SIL Open Font License, Version 1.1 (https://scripts.sil.org/OFL).

---

SIL OPEN FONT LICENSE

Version 1.1 - 26 February 2007

PREAMBLE

The goals of the Open Font License (OFL) are to stimulate worldwide development of collaborative font projects, to support the font creation efforts of academic and linguistic communities, and to provide a free and open framework in which fonts may be shared and improved in partnership with others.

The OFL allows the licensed fonts to be used, studied, modified and redistributed freely as long as they are not sold by themselves. The fonts, including any derivative works, can be bundled, embedded, redistributed and/or sold with any software provided that any reserved names are not used by derivative works. The fonts and derivatives, however, cannot be released under any other type of license. The requirement for fonts to remain under this license does not apply to any document created using the fonts or their derivatives.

DEFINITIONS

"Font Software" refers to the set of files released by the Copyright Holder(s) under this license and clearly marked as such. This may include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the copyright statement(s).

"Original Version" refers to the collection of Font Software components as distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting, or substituting — in part or in whole — any of the components of the Original Version, by changing formats or by porting the Font Software to a new environment.

"Author" refers to any designer, engineer, programmer, technical writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS

Permission is hereby granted, free of charge, to any person obtaining a copy of the Font Software, to use, study, copy, merge, embed, modify, redistribute, and sell modified and unmodified copies of the Font Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components, in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled, redistributed and/or sold with any software, provided that each copy contains the above copyright notice and this license. These can be included either as stand-alone text files, human-readable headers or in the appropriate machine-readable metadata fields within text or binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font Name(s) unless explicit written permission is granted by the corresponding Copyright Holder. This restriction only applies to the primary font name as presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font Software shall not be used to promote, endorse or advertise any Modified Version, except to acknowledge the contribution(s) of the Copyright Holder(s) and the Author(s) or with their explicit written permission.

5) The Font Software, modified or unmodified, in part or in whole, must be distributed entirely under this license, and must not be distributed under any other license. The requirement for fonts to remain under this license does not apply to any document created using the Font Software.

TERMINATION

This license becomes null and void if any of the above conditions are not met.

DISCLAIMER

THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM OTHER DEALINGS IN THE FONT SOFTWARE.
""".trimIndent()

data class License(val name: String, val roleRes: Int, val text: String)

private val licenses = listOf(
    License("wie", R.string.license_role_wie, mit("Copyright 2020 Inseok Lee")),
    License("RustJava", R.string.license_role_rustjava, mit("Copyright 2020 Inseok Lee")),
    License("smaf", R.string.license_role_smaf, mit("Copyright 2020 Inseok Lee")),
    License(
        "rodio / cpal", R.string.license_role_rodio,
        mit("Copyright (c) The Rodio Project Contributors") +
            "\n\n---\n\ncpal is licensed under the Apache License, Version 2.0.\nhttp://www.apache.org/licenses/LICENSE-2.0"
    ),
    License("NeoDunggeunmo", R.string.license_role_font, neodgmLicense),
    License("rustysynth", R.string.license_role_rustysynth, mit("Copyright (c) 2021 Nobuaki Tanaka")),
    License(
        "GeneralUser GS", R.string.license_role_soundfont,
        "GeneralUser GS by S. Christian Collins\n(schristiancollins.com/generaluser.php)\n\n" +
            "Licensed under the GeneralUser GS License v2.0: free to use, modify and distribute, " +
            "including in commercial software, with attribution appreciated. No warranty is provided."
    ),
)

@Composable
private fun LicensesPage(onSelect: (License) -> Unit, onBack: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.licenses_title), onBack = onBack) { modifier ->
        LazyColumn(modifier = modifier.fillMaxSize()) {
            item {
                Text(
                    stringResource(R.string.licenses_credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(licenses) { license ->
                ListItem(
                    headlineContent = { Text(license.name) },
                    supportingContent = { Text(stringResource(license.roleRes)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    modifier = Modifier.clickable { onSelect(license) },
                )
                HorizontalDivider()
            }
            item {
                Text(
                    stringResource(R.string.licenses_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun LicenseDetailPage(license: License, onBack: () -> Unit) {
    SettingsScaffold(title = license.name, onBack = onBack) { modifier ->
        Text(
            license.text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        )
    }
}
