package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.engine.ProfileExportEngine
import com.example.model.BUCKET_COLOR_LIMIT
import com.example.model.BrushType
import com.example.model.KidProfile
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.MarigoldAccent
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChallengeDialog(
  challenge: ChallengeUiState,
  onSelectOption: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val toolName =
    if (challenge.isRefill) "Refill Bucket"
    else
      when (challenge.targetBrush) {
        BrushType.CROSS -> "Cross Brush"
        BrushType.BLOB -> "Blob Brush"
        BrushType.BUCKET -> "Fill Bucket"
        BrushType.DOT -> "Dot Brush"
      }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp).testTag("challenge_dialog"),
      shape = RoundedCornerShape(28.dp),
      border = BorderStroke(4.dp, Color(0xFFFCD34D)),
      shadowElevation = 14.dp,
    ) {
      Column(
        modifier =
          Modifier.background(
              Brush.verticalGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFFEDD5)))
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Surface(shape = CircleShape, color = Color(0xFFFBBF24)) {
          Text(
            text = "UNLOCK $toolName".uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF451A03),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = challenge.question.title,
          style = MaterialTheme.typography.headlineSmall,
          color = SlateInk,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text =
            "⭐".repeat(challenge.completedCount) +
              "☆".repeat((challenge.totalRequired - challenge.completedCount).coerceAtLeast(0)),
          fontSize = 22.sp,
          letterSpacing = 4.sp,
        )

        Text(
          text =
            if (challenge.isRefill) {
              "Question ${challenge.completedCount + 1} of ${challenge.totalRequired}: refill the Bucket for $BUCKET_COLOR_LIMIT more colors!"
            } else {
              "Question ${challenge.completedCount + 1} of ${challenge.totalRequired}: answer to unlock!"
            },
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFF78350F),
          textAlign = TextAlign.Center,
        )

        if (challenge.question.items.isNotEmpty()) {
          Spacer(modifier = Modifier.height(12.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            challenge.question.items.forEach { item ->
              if (item == null) {
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = Color.White,
                  border = BorderStroke(3.dp, MarigoldAccent),
                ) {
                  Text(
                    text = "?",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MarigoldAccent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                  )
                }
              } else {
                Text(
                  text = item,
                  fontSize = if (challenge.question.isSentence) 22.sp else 32.sp,
                  fontWeight = FontWeight.Black,
                  color = IndigoPrimary,
                  textAlign = TextAlign.Center,
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val options = challenge.question.options
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          options.chunked(2).forEach { rowOpts ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              rowOpts.forEach { option ->
                val isCorrectAndSolved =
                  challenge.solvedCorrectly && option == challenge.question.correct
                val isWrong = option in challenge.wrongOptions
                val shakeOffset = remember { Animatable(0f) }

                LaunchedEffect(isWrong) {
                  if (isWrong) {
                    shakeOffset.animateTo(8f, tween(50))
                    shakeOffset.animateTo(-8f, tween(50))
                    shakeOffset.animateTo(0f, tween(50))
                  }
                }

                val bg =
                  when {
                    isCorrectAndSolved -> EmeraldSuccess
                    isWrong -> Color(0xFFFEE2E2)
                    else -> Color.White
                  }
                val fg =
                  when {
                    isCorrectAndSolved -> Color.White
                    isWrong -> CoralError
                    else -> Color(0xFF451A03)
                  }
                val border =
                  when {
                    isCorrectAndSolved -> Color(0xFF059669)
                    isWrong -> Color(0xFFF87171)
                    else -> Color(0xFFFCD34D)
                  }

                Surface(
                  onClick = { onSelectOption(option) },
                  enabled = !challenge.solvedCorrectly,
                  modifier =
                    Modifier.weight(1f)
                      .heightIn(min = 60.dp)
                      .rotate(shakeOffset.value)
                      .testTag("challenge_option_$option"),
                  shape = RoundedCornerShape(16.dp),
                  color = bg,
                  border = BorderStroke(3.dp, border),
                  shadowElevation = 3.dp,
                ) {
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                  ) {
                    Text(
                      text = option,
                      fontSize =
                        when {
                          option.length > 16 -> 15.sp
                          option.length > 7 -> 19.sp
                          else -> 26.sp
                        },
                      fontWeight = FontWeight.ExtraBold,
                      color = fg,
                      textAlign = TextAlign.Center,
                    )
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(onClick = onDismiss, modifier = Modifier.testTag("challenge_dismiss_button")) {
          Text(
            text = stringResource(R.string.keep_dot_brush),
            color = SlateMuted,
            textDecoration = TextDecoration.Underline,
          )
        }
      }
    }
  }
}

@Composable
fun ProfileSwitcherDialog(
  profiles: List<KidProfile>,
  activeProfileId: String,
  onSelectProfile: (String) -> Unit,
  onAddNewKid: () -> Unit,
  onDismiss: () -> Unit,
  onImportProfileUri: ((Uri) -> Unit)? = null,
) {
  val duplicateNames =
    remember(profiles) {
      profiles
        .groupingBy { it.name.trim().lowercase() }
        .eachCount()
        .filterValues { it > 1 }
        .keys
    }
  val importLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
      if (uri != null) {
        onImportProfileUri?.invoke(uri)
      }
    }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp).testTag("profiles_dialog"),
      shape = RoundedCornerShape(24.dp),
      color = Color.White,
      border = BorderStroke(4.dp, BorderSoft),
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.who_is_playing),
            style = MaterialTheme.typography.headlineSmall,
            color = IndigoDeep,
          )
          IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 100.dp),
          modifier = Modifier.heightIn(max = 360.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          itemsIndexed(profiles, key = { _, p -> p.id }) { _, profile ->
            val selected = profile.id == activeProfileId
            val hasDuplicateName = profile.name.trim().lowercase() in duplicateNames
            Card(
              onClick = { onSelectProfile(profile.id) },
              modifier = Modifier.testTag("profile_card_${profile.id}"),
              shape = RoundedCornerShape(18.dp),
              colors =
                CardDefaults.cardColors(
                  containerColor = if (selected) IndigoContainer else Color(0xFFEEF2FF)
                ),
              border =
                BorderStroke(
                  width = 3.dp,
                  color = if (selected) IndigoPrimary else BorderSoft,
                ),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(text = profile.avatar, fontSize = 38.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = profile.name,
                  style = MaterialTheme.typography.titleMedium,
                  color = IndigoDeep,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (hasDuplicateName) {
                  Text(
                    text = "#${ProfileExportEngine.shortProfileCode(profile.id)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted,
                    maxLines = 1,
                  )
                }
              }
            }
          }

          item {
            Card(
              onClick = onAddNewKid,
              modifier = Modifier.testTag("add_new_kid_card"),
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(2.dp, BorderSoft),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Icon(
                  imageVector = Icons.Filled.Add,
                  contentDescription = null,
                  tint = IndigoPrimary,
                  modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = stringResource(R.string.new_kid),
                  style = MaterialTheme.typography.titleMedium,
                  color = IndigoPrimary,
                )
              }
            }
          }
        }

        if (onImportProfileUri != null) {
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profiles_import_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.import_profile_json))
          }
        }
      }
    }
  }
}



@Composable
fun ParentPinGatePanel(
  hasExistingPin: Boolean,
  titleOverride: String? = null,
  onSetupPin: (String) -> Boolean,
  onVerifyPin: (String) -> Boolean,
  onResetPinAndToken: (() -> Unit)? = null,
) {
  var pin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }
  var confirmReset by remember { mutableStateOf(false) }
  val confirmPinFocus = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  fun submitPin() {
    if (hasExistingPin) {
      if (!onVerifyPin(pin)) {
        errorText = "Incorrect PIN — please try again"
      }
    } else {
      when {
        pin.length < 4 -> errorText = "PIN must be at least 4 digits"
        pin != confirmPin -> errorText = "PINs do not match"
        else -> if (!onSetupPin(pin)) errorText = "PIN must be 4–8 digits"
      }
    }
  }

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = Color(0xFFF8FAFC),
    border = BorderStroke(1.5.dp, BorderSoft),
    modifier = Modifier.fillMaxWidth().testTag("admin_pin_gate"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = IndigoPrimary)
        Text(
          text =
            titleOverride
              ?: if (hasExistingPin) stringResource(R.string.enter_parent_pin)
              else stringResource(R.string.create_parent_pin),
          style = MaterialTheme.typography.titleMedium,
          color = IndigoDeep,
        )
      }

      Text(
        text =
          if (hasExistingPin) {
            "Enter your Parent PIN to unlock GitHub publishing and sync settings."
          } else {
            "Set a 4–8 digit Parent PIN so kids cannot edit sync URLs or push drawings to GitHub."
          },
        style = MaterialTheme.typography.bodySmall,
        color = SlateMuted,
      )

      OutlinedTextField(
        value = pin,
        onValueChange = { raw ->
          if (raw.length <= 8 && raw.all { it.isDigit() }) {
            pin = raw
            errorText = null
          }
        },
        label = { Text(if (hasExistingPin) "Parent PIN" else "New PIN (4–8 digits)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
          KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = if (hasExistingPin) ImeAction.Done else ImeAction.Next,
          ),
        keyboardActions =
          KeyboardActions(
            onNext = { confirmPinFocus.requestFocus() },
            onDone = {
              keyboardController?.hide()
              focusManager.clearFocus(force = true)
              submitPin()
            },
          ),
        modifier = Modifier.fillMaxWidth().testTag("admin_pin_input"),
        shape = RoundedCornerShape(12.dp),
      )

      if (!hasExistingPin) {
        OutlinedTextField(
          value = confirmPin,
          onValueChange = { raw ->
            if (raw.length <= 8 && raw.all { it.isDigit() }) {
              confirmPin = raw
              errorText = null
            }
          },
          label = { Text("Confirm PIN") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions =
            KeyboardOptions(
              keyboardType = KeyboardType.NumberPassword,
              imeAction = ImeAction.Done,
            ),
          keyboardActions =
            KeyboardActions(
              onDone = {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                submitPin()
              },
            ),
          modifier =
            Modifier.fillMaxWidth()
              .focusRequester(confirmPinFocus)
              .testTag("admin_pin_confirm_input"),
          shape = RoundedCornerShape(12.dp),
        )
      }

      if (errorText != null) {
        Text(
          text = errorText!!,
          style = MaterialTheme.typography.bodySmall,
          color = CoralError,
          modifier = Modifier.testTag("admin_pin_error"),
        )
      }

      Button(
        onClick = ::submitPin,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_pin_submit_button"),
        shape = RoundedCornerShape(12.dp),
      ) {
        Text(if (hasExistingPin) "Unlock Admin" else "Save PIN & Unlock")
      }

      if (hasExistingPin && onResetPinAndToken != null) {
        TextButton(
          onClick = { confirmReset = true },
          modifier = Modifier.align(Alignment.End).testTag("admin_forgot_pin_button"),
        ) {
          Text("Forgot PIN? Reset", color = SlateMuted)
        }
      }
    }
  }

  if (confirmReset && onResetPinAndToken != null) {
    AlertDialog(
      onDismissRequest = { confirmReset = false },
      title = { Text("Reset Parent PIN?") },
      text = {
        Text("For security, resetting the Parent PIN also clears any saved GitHub token on this device.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmReset = false
            pin = ""
            errorText = null
            onResetPinAndToken()
          }
        ) {
          Text("Reset & Clear Token", color = CoralError, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
fun ParentPinPromptDialog(
  hasExistingPin: Boolean,
  titleOverride: String? = null,
  onSetupPin: (String) -> Boolean,
  onVerifyPin: (String) -> Boolean,
  onResetPinAndToken: (() -> Unit)? = null,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
      shape = RoundedCornerShape(24.dp),
      color = Color.White,
      border = BorderStroke(3.dp, BorderSoft),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        ParentPinGatePanel(
          hasExistingPin = hasExistingPin,
          titleOverride = titleOverride,
          onSetupPin = onSetupPin,
          onVerifyPin = onVerifyPin,
          onResetPinAndToken = onResetPinAndToken,
        )
        Spacer(modifier = Modifier.height(6.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
          Text("Cancel")
        }
      }
    }
  }
}
