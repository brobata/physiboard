package it.palsoftware.pastiera.inputmethod

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import it.palsoftware.pastiera.commands.CommandIcon
import it.palsoftware.pastiera.commands.CommandSourceId
import it.palsoftware.pastiera.commands.CommandTarget

fun commandMaterialIcon(command: CommandTarget): ImageVector {
    return when {
        command.source == CommandSourceId.Apps -> Icons.Default.Apps

        command.source == CommandSourceId.AppActions && command.id.contains("agenda") -> Icons.Default.Event
        command.source == CommandSourceId.AppActions && command.id.contains("tasker") -> Icons.Default.TaskAlt
        command.source == CommandSourceId.AppActions && command.id.contains("homeassistant.assist") -> Icons.Default.Mic
        command.source == CommandSourceId.AppActions && command.id.contains("homeassistant.voice") -> Icons.Default.Mic
        command.source == CommandSourceId.AppActions && command.id.contains("homeassistant") -> Icons.Default.Home
        command.source == CommandSourceId.AppActions -> Icons.Default.Search

        command.id == "pastiera.quick_launcher" -> Icons.Default.Search
        command.id == "pastiera.main" -> Icons.Default.Settings

        command.id == "device.media.play_pause" -> Icons.Default.PlayArrow
        command.id == "device.media.previous" -> Icons.Default.SkipPrevious
        command.id == "device.media.next" -> Icons.Default.SkipNext
        command.id == "device.volume.up" -> Icons.AutoMirrored.Filled.VolumeUp
        command.id == "device.volume.down" -> Icons.AutoMirrored.Filled.VolumeDown
        command.id == "device.volume.mute" -> Icons.AutoMirrored.Filled.VolumeOff
        command.id == "device.brightness.up" -> Icons.Default.WbSunny
        command.id == "device.brightness.down" -> Icons.Default.WbSunny
        command.id.contains("default_apps") || command.id == "settings.android.apps" -> Icons.Default.Apps
        command.id.contains("input_method") -> Icons.Default.Keyboard
        command.id.contains("accessibility") -> Icons.Default.AccessibilityNew
        command.id.contains("language") || command.id.contains("locale") -> Icons.Default.Language
        command.id.contains("bluetooth") -> Icons.Default.Bluetooth
        command.id.contains("wifi") || command.id.contains("internet") -> Icons.Default.Wifi
        command.id.contains("display") -> Icons.Default.WbSunny
        command.id.contains("sound") -> Icons.AutoMirrored.Filled.VolumeUp
        command.id.contains("nfc") -> Icons.Default.Contactless
        command.id.contains("battery") -> Icons.Default.BatterySaver
        command.id.contains("notification") -> Icons.Default.Notifications

        command.id == "nav.keycode.DPAD_UP" -> Icons.Default.KeyboardArrowUp
        command.id == "nav.keycode.DPAD_DOWN" -> Icons.Default.KeyboardArrowDown
        command.id == "nav.keycode.DPAD_LEFT" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
        command.id == "nav.keycode.DPAD_RIGHT" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        command.id == "nav.keycode.TAB" -> Icons.AutoMirrored.Filled.KeyboardTab
        command.id == "nav.keycode.MOVE_HOME" -> Icons.Default.FirstPage
        command.id == "nav.keycode.MOVE_END" -> Icons.AutoMirrored.Filled.LastPage
        command.id == "nav.keycode.PAGE_UP" -> Icons.Default.VerticalAlignTop
        command.id == "nav.keycode.PAGE_DOWN" -> Icons.Default.VerticalAlignBottom
        command.id == "nav.keycode.ESCAPE" -> Icons.Default.Close
        command.id == "nav.keycode.FORWARD_DEL" -> Icons.AutoMirrored.Filled.Backspace
        command.id == "nav.action.copy" -> Icons.Default.ContentCopy
        command.id == "nav.action.paste" -> Icons.Default.ContentPaste
        command.id == "nav.action.cut" -> Icons.Default.ContentCut
        command.id == "nav.action.undo" -> Icons.AutoMirrored.Filled.Undo
        command.id == "nav.action.select_all" -> Icons.Default.SelectAll
        command.id == "nav.action.expand_selection_left" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
        command.id == "nav.action.expand_selection_right" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        command.id == "nav.action.move_word_left" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
        command.id == "nav.action.move_word_right" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        command.id == "nav.action.expand_selection_word_left" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
        command.id == "nav.action.expand_selection_word_right" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        command.id == "nav.action.page_start" -> Icons.Default.FirstPage
        command.id == "nav.action.page_end" -> Icons.AutoMirrored.Filled.LastPage
        command.id == "nav.action.toggle_minimal_ui" -> Icons.Default.KeyboardHide
        command.id == "nav.action.media_play_pause" -> Icons.Default.PlayArrow
        command.id == "nav.action.media_previous" -> Icons.Default.SkipPrevious
        command.id == "nav.action.media_next" -> Icons.Default.SkipNext

        command.source == CommandSourceId.DeviceControl -> Icons.Default.Settings
        command.source == CommandSourceId.Pastiera -> Icons.Default.KeyboardCommandKey
        command.source == CommandSourceId.NavActions -> Icons.Default.KeyboardCommandKey
        command.icon == CommandIcon.Settings -> Icons.Default.Settings
        command.icon == CommandIcon.DeviceControl -> Icons.Default.Settings
        command.icon == CommandIcon.Navigation -> Icons.Default.KeyboardCommandKey
        else -> Icons.Default.Search
    }
}
