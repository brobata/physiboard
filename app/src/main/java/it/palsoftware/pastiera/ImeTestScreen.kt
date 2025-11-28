package it.palsoftware.pastiera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler

/**
 * IME Test Screen - Contains all possible Android input field types and IME actions
 * for testing the IME behavior.
 */
@Composable
fun ImeTestScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // State for all text fields
    var textPlain by remember { mutableStateOf(TextFieldValue("")) }
    var textCapCharacters by remember { mutableStateOf(TextFieldValue("")) }
    var textCapWords by remember { mutableStateOf(TextFieldValue("")) }
    var textCapSentences by remember { mutableStateOf(TextFieldValue("")) }
    var textEmail by remember { mutableStateOf(TextFieldValue("")) }
    var textPassword by remember { mutableStateOf(TextFieldValue("")) }
    var textVisiblePassword by remember { mutableStateOf(TextFieldValue("")) }
    var textWebPassword by remember { mutableStateOf(TextFieldValue("")) }
    var textUri by remember { mutableStateOf(TextFieldValue("")) }
    var textPersonName by remember { mutableStateOf(TextFieldValue("")) }
    var textPostalAddress by remember { mutableStateOf(TextFieldValue("")) }
    var textMultiLine by remember { mutableStateOf(TextFieldValue("")) }
    var textNoSuggestions by remember { mutableStateOf(TextFieldValue("")) }
    var textAutoComplete by remember { mutableStateOf(TextFieldValue("")) }
    var textAutoCorrect by remember { mutableStateOf(TextFieldValue("")) }
    var number by remember { mutableStateOf(TextFieldValue("")) }
    var numberSigned by remember { mutableStateOf(TextFieldValue("")) }
    var numberDecimal by remember { mutableStateOf(TextFieldValue("")) }
    var phone by remember { mutableStateOf(TextFieldValue("")) }
    var datetime by remember { mutableStateOf(TextFieldValue("")) }
    var date by remember { mutableStateOf(TextFieldValue("")) }
    var time by remember { mutableStateOf(TextFieldValue("")) }
    
    // IME Action fields
    var actionNone by remember { mutableStateOf(TextFieldValue("")) }
    var actionGo by remember { mutableStateOf(TextFieldValue("")) }
    var actionSearch by remember { mutableStateOf(TextFieldValue("")) }
    var actionSend by remember { mutableStateOf(TextFieldValue("")) }
    var actionNext by remember { mutableStateOf(TextFieldValue("")) }
    var actionDone by remember { mutableStateOf(TextFieldValue("")) }
    var actionPrevious by remember { mutableStateOf(TextFieldValue("")) }
    var actionUnspecified by remember { mutableStateOf(TextFieldValue("")) }
    
    BackHandler { onBack() }
    
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp)
                    )
                    Text(
                        text = "IME Test Screen",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Text Input Types Section
            Text(
                text = "Text Input Types",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            InputField(
                label = "text (Plain Text)",
                value = textPlain,
                onValueChange = { textPlain = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "textCapCharacters (All Caps)",
                value = textCapCharacters,
                onValueChange = { textCapCharacters = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "textCapWords (Title Case)",
                value = textCapWords,
                onValueChange = { textCapWords = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "textCapSentences (Sentence Case)",
                value = textCapSentences,
                onValueChange = { textCapSentences = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "textEmailAddress",
                value = textEmail,
                onValueChange = { textEmail = it },
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "textPassword",
                value = textPassword,
                onValueChange = { textPassword = it },
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Default,
                visualTransformation = PasswordVisualTransformation()
            )
            
            InputField(
                label = "textVisiblePassword",
                value = textVisiblePassword,
                onValueChange = { textVisiblePassword = it },
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "textWebPassword",
                value = textWebPassword,
                onValueChange = { textWebPassword = it },
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Default,
                visualTransformation = PasswordVisualTransformation()
            )
            
            InputField(
                label = "textUri",
                value = textUri,
                onValueChange = { textUri = it },
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "textPersonName",
                value = textPersonName,
                onValueChange = { textPersonName = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "textPostalAddress",
                value = textPostalAddress,
                onValueChange = { textPostalAddress = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "textMultiLine",
                value = textMultiLine,
                onValueChange = { textMultiLine = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default,
                maxLines = 5
            )
            
            InputField(
                label = "textNoSuggestions",
                value = textNoSuggestions,
                onValueChange = { textNoSuggestions = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "textAutoComplete",
                value = textAutoComplete,
                onValueChange = { textAutoComplete = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "textAutoCorrect",
                value = textAutoCorrect,
                onValueChange = { textAutoCorrect = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Numeric Input Types Section
            Text(
                text = "Numeric Input Types",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            InputField(
                label = "number",
                value = number,
                onValueChange = { number = it },
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "numberSigned",
                value = numberSigned,
                onValueChange = { numberSigned = it },
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "numberDecimal",
                value = numberDecimal,
                onValueChange = { numberDecimal = it },
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Default
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Other Input Types Section
            Text(
                text = "Other Input Types",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            InputField(
                label = "phone",
                value = phone,
                onValueChange = { phone = it },
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "datetime",
                value = datetime,
                onValueChange = { datetime = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "date",
                value = date,
                onValueChange = { date = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            InputField(
                label = "time",
                value = time,
                onValueChange = { time = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // IME Actions Section
            Text(
                text = "IME Actions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            InputField(
                label = "actionNone",
                value = actionNone,
                onValueChange = { actionNone = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.None,
                maxLines = 3
            )
            
            InputField(
                label = "actionGo",
                value = actionGo,
                onValueChange = { actionGo = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Go,
                maxLines = 3,
                onImeActionPerformed = {
                    // Handle action
                }
            )
            
            InputField(
                label = "actionSearch",
                value = actionSearch,
                onValueChange = { actionSearch = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
                maxLines = 3,
                onImeActionPerformed = {
                    // Handle action
                }
            )
            
            InputField(
                label = "actionSend",
                value = actionSend,
                onValueChange = { actionSend = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Send,
                maxLines = 3,
                onImeActionPerformed = {
                    // Handle action
                }
            )
            
            InputField(
                label = "actionNext",
                value = actionNext,
                onValueChange = { actionNext = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                maxLines = 3,
                onImeActionPerformed = {
                    // Handle action
                }
            )
            
            InputField(
                label = "actionDone",
                value = actionDone,
                onValueChange = { actionDone = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
                maxLines = 3,
                onImeActionPerformed = {
                    // Handle action
                }
            )
            
            InputField(
                label = "actionPrevious",
                value = actionPrevious,
                onValueChange = { actionPrevious = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Previous,
                maxLines = 3,
                onImeActionPerformed = {
                    // Handle action
                }
            )
            
            InputField(
                label = "actionUnspecified (Default)",
                value = actionUnspecified,
                onValueChange = { actionUnspecified = it },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default,
                maxLines = 3
            )
        }
    }
}

@Composable
private fun InputField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    maxLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onImeActionPerformed: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = if (onImeActionPerformed != null) {
                androidx.compose.foundation.text.KeyboardActions(
                    onDone = { onImeActionPerformed() },
                    onGo = { onImeActionPerformed() },
                    onNext = { onImeActionPerformed() },
                    onPrevious = { onImeActionPerformed() },
                    onSearch = { onImeActionPerformed() },
                    onSend = { onImeActionPerformed() }
                )
            } else {
                androidx.compose.foundation.text.KeyboardActions()
            },
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
            maxLines = maxLines,
            singleLine = maxLines == 1
        )
    }
}

