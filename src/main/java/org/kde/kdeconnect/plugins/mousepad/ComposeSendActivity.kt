/*
 * SPDX-FileCopyrightText: 2023 Dmitry Yudin <dgyudin@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.mousepad

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.ui.compose.KdeTextButton
import org.kde.kdeconnect.ui.compose.KdeTextField
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.KdeTopAppBar
import org.kde.kdeconnect.extensions.safeDrawPadding
import org.kde.kdeconnect_tp.R
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

private const val INPUT_CACHE_KEY = "compose_send_input_cache"

class ComposeSendActivity : AppCompatActivity() {
    private var deviceId: String? = null
    private val userInput = mutableStateOf(String())
    private val charDelayStr = mutableStateOf("80")
    private val lineDelayStr = mutableStateOf("1250")
    private val rLineDelayStr = mutableStateOf("3200")
    private var buttonText = mutableStateOf("Send")
    private var job: Job? = null
    private var isTrimChecked = mutableStateOf(true)
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        prefs.getString(INPUT_CACHE_KEY, null)?.let { userInput.value = it }

        setContent { ComposeSendScreen() }

        deviceId = intent.getStringExtra("org.kde.kdeconnect.plugins.mousepad.deviceId")
    }

    override fun onStop() {
        super.onStop()
        prefs.edit {
            if (userInput.value.isNotBlank()) putString(INPUT_CACHE_KEY,userInput.value)
            else remove(INPUT_CACHE_KEY)
        }
    }

    private fun sendComposed() {
        val plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin::class.java)
        if (plugin == null) {
            finish()
            return
        }

        if (job != null){
            job?.cancel()
            buttonText.value = "Send"
            return
        }

        val text = userInput.value ?: ""
        if (text == "") return
        val charDelay = (charDelayStr.value.trim().toIntOrNull() ?: 80).coerceIn(0,100)
        val lineDelay = (lineDelayStr.value.trim().toIntOrNull() ?: 1250).coerceIn(0,2000)
        val randomLineDelay = (rLineDelayStr.value.trim().toIntOrNull() ?: 3200).coerceIn(0,10000)
        val tempList = if (isTrimChecked.value) text.split("\n").map { it.trim() } else text.split("\n")

        if (charDelay == 0 && lineDelay == 0 &&  randomLineDelay == 0){
            job =lifecycleScope.launch {
                buttonText.value = "Stop"
                for(line in tempList){
                    plugin.sendText(line);
                    plugin.sendEnter();
                }
            }
            job?.invokeOnCompletion {
                buttonText.value = "Send"
                job = null
            }
        }else{
            var nPauses : Int = if(lineDelay > 0 && randomLineDelay > 0) tempList.size else -1

            job = lifecycleScope.launch {
                buttonText.value = "Stop"
                for ((index,tempStr) in tempList.withIndex()) {

                    if (!tempStr.isEmpty()) {
                        for (char in tempStr) {
                            plugin.sendText(char.toString())
                            delay((charDelay..charDelay + 100).random().toLong())
                        }
                    }

                    plugin.sendEnter()

                    if (tempList.size - 1 == index) continue

                    if ((0..100).random() <= 30 && nPauses > 0) {
                        delay((randomLineDelay..randomLineDelay + 1000).random().toLong())
                        nPauses -= 1
                    } else if (lineDelay > 0) {
                        delay((lineDelay..lineDelay + 500).random().toLong())
                    } else {
                        continue
                    }
                }
            }
            job?.invokeOnCompletion {
                buttonText.value = "Send"
                job = null
            }
        }
        clearComposeInput()
    }

    private fun clearComposeInput() {
        userInput.value = String()
    }

    @Composable
    private fun ComposeSendScreen() {
        KdeTheme(this) {
            Scaffold(
                modifier = Modifier.safeDrawPadding(),
                topBar = {
                    KdeTopAppBar(
                        title = stringResource(R.string.compose_send_title),
                        navIconOnClick = { onBackPressedDispatcher.onBackPressed() },
                        navIconDescription = getString(androidx.appcompat.R.string.abc_action_bar_up_description),
                        actions = {
                            KdeTextButton(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                onClick = { clearComposeInput() },
                                text = stringResource(R.string.clear_compose),
                            )
                        }
                    )
                },
            ) { scaffoldPadding ->
                Box(modifier = Modifier.padding(scaffoldPadding).fillMaxSize()) {
                    KdeTextField(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 150.dp)
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                        input = userInput,
                        label = stringResource(R.string.click_here_to_type),
                    )
                    KdeTextField(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(start = 150.dp)
                            .padding(bottom = 10.dp)
                            .align(Alignment.BottomEnd),
                        input = rLineDelayStr,
                        label = stringResource(R.string.rline_delay),
                    )
                    KdeTextField(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .width(140.dp)
                            .padding(bottom = 10.dp)
                            .align(Alignment.BottomStart),
                        input = lineDelayStr,
                        label = stringResource(R.string.line_delay),
                    )
                    KdeTextField(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .width(140.dp)
                            .padding(bottom = 80.dp)
                            .align(Alignment.BottomStart),
                        input = charDelayStr,
                        label = stringResource(R.string.char_delay),
                    )
                    Checkbox(
                        checked = isTrimChecked.value,
                        onCheckedChange = {isChecked: Boolean -> isTrimChecked.value = isChecked},
                        colors = CheckboxDefaults.colors(),
                        modifier = Modifier
                            .padding(bottom = 98.dp)
                            .padding(start = 150.dp)
                            .align(Alignment.BottomStart)

                    )
                    Text(
                        text = "Trim",
                        modifier = Modifier
                            .padding(bottom = 110.dp)
                            .padding(start = 190.dp)
                            .align(Alignment.BottomStart),
                        color = MaterialTheme.colorScheme.primary
                    )
                    KdeTextButton(
                        onClick = { sendComposed() },
                        modifier = Modifier
                            .padding(all = 16.dp)
                            .padding(bottom = 80.dp)
                            .align(Alignment.BottomEnd),
                        enabled = userInput.value.isNotEmpty() || job != null,
                        text = buttonText.value,
                        iconLeft = Icons.Default.Send,
                    )
                }
            }
        }
    }
}
