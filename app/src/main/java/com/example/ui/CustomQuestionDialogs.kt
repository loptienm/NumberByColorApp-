package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.engine.QuestionExportEngine
import com.example.model.ChallengeMode
import com.example.model.CustomEducationLevel
import com.example.model.CustomQuestion
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.MarigoldAccent
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted

private val QUICK_CLUE_TOKENS =
  listOf("?", "⭐", "🍎", "🐞", "🐸", "🐶", "🐱", "🦋", "+", "−", "×", "÷", "=")

private val QUICK_ANSWER_EMOJIS =
  listOf("⭐", "🌙", "🍎", "🍌", "🐞", "🐸", "🐶", "🐱", "🦋", "❤️", "☀️", "🌈", "🔴", "🔵", "🟢", "🟡", "🔺", "🟩")

private val CLUE_OPERATORS = setOf("?", "_", "+", "−", "-", "×", "*", "÷", "/", "=")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomQuestionsAdminSection(
  questions: List<CustomQuestion>,
  syncState: SyncSettingsUiState,
  onCreateNew: () -> Unit,
  onEditQuestion: (CustomQuestion) -> Unit,
  onDeleteOrRevertQuestion: (String) -> Unit,
  onPublishQuestion: (CustomQuestion) -> Unit,
  onPublishAllQuestions: (List<CustomQuestion>) -> Unit,
  customLevels: List<CustomEducationLevel> = emptyList(),
) {
  val publishableQuestions = remember(questions) { questions.filter { it.isCustom } }
  val upToDateQuestions = remember(questions) { questions.filter { !it.isCustom && it.isSyncedOfficial } }
  val levelNamesById = remember(customLevels) { customLevels.associate { it.id to it.name } }

  fun formatAssignedLevels(q: CustomQuestion): String {
    val builtIn = q.levels.map { it.name.replace('_', '-') }
    val custom = q.customLevelIds.map { id -> levelNamesById[id] ?: id }
    return (builtIn + custom).joinToString(", ").ifEmpty { "Pre-K" }
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = stringResource(R.string.custom_questions_title),
      style = MaterialTheme.typography.titleLarge,
      color = IndigoDeep,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(
        onClick = onCreateNew,
        modifier = Modifier.weight(1f).height(48.dp).testTag("admin_new_question_button"),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
      ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.new_question),
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (publishableQuestions.isNotEmpty()) {
        Button(
          onClick = { onPublishAllQuestions(publishableQuestions) },
          enabled = !syncState.isPublishing && !syncState.isSyncing,
          modifier =
            Modifier.weight(1f).height(48.dp).testTag("admin_publish_all_questions_button"),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
          if (syncState.isPublishingAll && syncState.publishingQuestionId != null) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = Color.White,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = syncState.publishProgressText.ifBlank { "Publishing…" },
              maxLines = 1,
              softWrap = false,
            )
          } else {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Publish Qs (${publishableQuestions.size})",
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (publishableQuestions.isEmpty() && upToDateQuestions.isEmpty()) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, BorderSoft),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "No custom questions yet. Tap \"New Question\" to add personalized spelling, math, or trivia questions and publish them to GitHub.",
          style = MaterialTheme.typography.bodyMedium,
          color = SlateMuted,
          modifier = Modifier.padding(14.dp),
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        publishableQuestions.forEach { q ->
          val cleanId =
            if (q.id.startsWith("q_")) {
              QuestionExportEngine.sanitizeQuestionId(q.prompt).ifBlank { q.id }
            } else {
              q.id
            }
          val isThisPublishing =
            syncState.isPublishing &&
              (syncState.publishingQuestionId == q.id || syncState.publishingQuestionId == cleanId)
          val clueSummary =
            if (q.clueItems.isNotEmpty()) {
              CustomQuestion.formatClueTokens(q.clueItems, q.isSentence)
            } else {
              ""
            }
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, BorderSoft),
            modifier = Modifier.fillMaxWidth().testTag("admin_custom_question_${q.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = q.prompt,
                  style = MaterialTheme.typography.titleMedium,
                  color = IndigoDeep,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (clueSummary.isNotEmpty()) {
                  Text(
                    text = clueSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Text(
                  text = "${formatAssignedLevels(q)} • Answer: ${q.correctAnswer}",
                  style = MaterialTheme.typography.bodySmall,
                  color = SlateMuted,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              IconButton(
                onClick = { onEditQuestion(q) },
                modifier = Modifier.size(40.dp).testTag("admin_edit_question_${q.id}"),
              ) {
                Icon(
                  Icons.Filled.Edit,
                  contentDescription = "Edit question",
                  tint = IndigoPrimary,
                  modifier = Modifier.size(18.dp),
                )
              }

              IconButton(
                onClick = { onDeleteOrRevertQuestion(q.id) },
                modifier = Modifier.size(40.dp).testTag("admin_delete_question_${q.id}"),
              ) {
                Icon(
                  Icons.Filled.Delete,
                  contentDescription = "Delete question",
                  tint = CoralError,
                  modifier = Modifier.size(18.dp),
                )
              }

              Button(
                onClick = { onPublishQuestion(q) },
                enabled = !syncState.isPublishing && !syncState.isSyncing,
                modifier = Modifier.height(40.dp).testTag("admin_publish_question_${q.id}"),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
              ) {
                if (isThisPublishing) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                  )
                } else {
                  Icon(
                    Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Publish", maxLines = 1, softWrap = false)
                }
              }
            }
          }
        }

        upToDateQuestions.forEach { q ->
          val clueSummary =
            if (q.clueItems.isNotEmpty()) {
              CustomQuestion.formatClueTokens(q.clueItems, q.isSentence)
            } else {
              ""
            }
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = EmeraldSuccess.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("admin_uptodate_question_${q.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = q.prompt,
                  style = MaterialTheme.typography.titleMedium,
                  color = IndigoDeep,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (clueSummary.isNotEmpty()) {
                  Text(
                    text = clueSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Text(
                  text = "${formatAssignedLevels(q)} • Answer: ${q.correctAnswer}",
                  style = MaterialTheme.typography.bodySmall,
                  color = SlateMuted,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(
                onClick = { onEditQuestion(q) },
                modifier = Modifier.size(40.dp).testTag("admin_edit_synced_question_${q.id}"),
              ) {
                Icon(
                  Icons.Filled.Edit,
                  contentDescription = "Edit synced question",
                  tint = IndigoPrimary,
                  modifier = Modifier.size(18.dp),
                )
              }
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = EmeraldSuccess.copy(alpha = 0.16f),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = "Up to date",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldSuccess,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomQuestionEditorDialog(
  initialQuestion: CustomQuestion?,
  defaultMode: ChallengeMode,
  onSave: (question: CustomQuestion, editingExisting: CustomQuestion?) -> Unit,
  onDismiss: () -> Unit,
  customLevels: List<CustomEducationLevel> = emptyList(),
  defaultCustomLevelId: String? = null,
) {
  val starterMode = if (defaultMode == ChallengeMode.OFF) ChallengeMode.PRE_K else defaultMode
  var prompt by remember(initialQuestion) { mutableStateOf(initialQuestion?.prompt.orEmpty()) }
  var isSentence by remember(initialQuestion) { mutableStateOf(initialQuestion?.isSentence ?: false) }
  val initialClueText =
    remember(initialQuestion) {
      if (initialQuestion != null) {
        CustomQuestion.formatClueTokens(initialQuestion.clueItems, initialQuestion.isSentence)
      } else {
        ""
      }
    }
  var clueField by remember(initialQuestion) {
    mutableStateOf(
      TextFieldValue(text = initialClueText, selection = TextRange(initialClueText.length))
    )
  }
  val clueText = clueField.text
  val initialCorrect = initialQuestion?.correctAnswer.orEmpty()
  val initialWrong1 = initialQuestion?.wrongAnswers?.getOrNull(0).orEmpty()
  val initialWrong2 = initialQuestion?.wrongAnswers?.getOrNull(1).orEmpty()
  val initialWrong3 = initialQuestion?.wrongAnswers?.getOrNull(2).orEmpty()

  var correctField by remember(initialQuestion) {
    mutableStateOf(TextFieldValue(initialCorrect, TextRange(initialCorrect.length)))
  }
  var wrong1Field by remember(initialQuestion) {
    mutableStateOf(TextFieldValue(initialWrong1, TextRange(initialWrong1.length)))
  }
  var wrong2Field by remember(initialQuestion) {
    mutableStateOf(TextFieldValue(initialWrong2, TextRange(initialWrong2.length)))
  }
  var wrong3Field by remember(initialQuestion) {
    mutableStateOf(TextFieldValue(initialWrong3, TextRange(initialWrong3.length)))
  }
  var activeAnswerTarget by remember { mutableIntStateOf(0) }
  var selectedCustomLevels by remember(initialQuestion, defaultCustomLevelId) {
    mutableStateOf(
      initialQuestion?.customLevelIds
        ?: if (!defaultCustomLevelId.isNullOrBlank()) setOf(defaultCustomLevelId) else emptySet()
    )
  }
  var selectedLevels by remember(initialQuestion, defaultCustomLevelId) {
    mutableStateOf(
      initialQuestion?.levels?.filter { it != ChallengeMode.OFF }?.toSet()
        ?: if (!defaultCustomLevelId.isNullOrBlank()) emptySet() else setOf(starterMode)
    )
  }
  var validationError by remember { mutableStateOf<String?>(null) }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val (clueFocus, correctFocus, wrong1Focus, wrong2Focus, wrong3Focus) =
    remember { FocusRequester.createRefs() }

  val parsedClueTokens =
    remember(clueText, isSentence) { CustomQuestion.parseClueTokens(clueText, isSentence) }
  val answerEmojiOptions =
    remember(parsedClueTokens) {
      val clueEmojis =
        parsedClueTokens.filter { tok ->
          tok !in CLUE_OPERATORS && tok.any { ch -> ch.code > 127 || !ch.isLetterOrDigit() }
        }
      (clueEmojis + QUICK_ANSWER_EMOJIS).distinct()
    }

  fun insertEmojiIntoAnswer(targetIndex: Int, emoji: String) {
    fun insertInto(field: TextFieldValue): TextFieldValue {
      val start = field.selection.start.coerceIn(0, field.text.length)
      val end = field.selection.end.coerceIn(start, field.text.length)
      val nextText = field.text.substring(0, start) + emoji + field.text.substring(end)
      if (nextText.length > 60) return field
      val cursor = start + emoji.length
      return TextFieldValue(text = nextText, selection = TextRange(cursor))
    }
    when (targetIndex) {
      0 -> correctField = insertInto(correctField)
      1 -> wrong1Field = insertInto(wrong1Field)
      2 -> wrong2Field = insertInto(wrong2Field)
      3 -> wrong3Field = insertInto(wrong3Field)
    }
    validationError = null
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp)
          .widthIn(max = 500.dp)
          .testTag("custom_question_editor_dialog"),
      shape = RoundedCornerShape(24.dp),
      color = Color.White,
      border = BorderStroke(3.dp, BorderSoft),
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = if (initialQuestion == null) "Create Custom Question" else "Edit Question",
            style = MaterialTheme.typography.headlineSmall,
            color = IndigoDeep,
          )
          IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
          }
        }

        Column(
          modifier =
            Modifier.fillMaxWidth()
              .weight(1f, fill = false)
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Unlock Levels",
            style = MaterialTheme.typography.labelLarge,
            color = SlateInk,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            listOf(ChallengeMode.PRE_K, ChallengeMode.KINDER, ChallengeMode.OLDER).forEach { mode ->
              val selected = mode in selectedLevels
              FilterChip(
                selected = selected,
                onClick = {
                  val totalSelected = selectedLevels.size + selectedCustomLevels.size
                  selectedLevels =
                    if (selected && totalSelected > 1) selectedLevels - mode
                    else selectedLevels + mode
                },
                leadingIcon =
                  if (selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                  } else null,
                label = { Text(mode.label, maxLines = 1) },
                modifier = Modifier.testTag("question_level_chip_${mode.key}"),
              )
            }
          }

          if (customLevels.isNotEmpty()) {
            Text(
              text = "Custom Education Levels",
              style = MaterialTheme.typography.labelLarge,
              color = SlateInk,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              customLevels.forEach { lvl ->
                val selected = lvl.id in selectedCustomLevels
                FilterChip(
                  selected = selected,
                  onClick = {
                    val totalSelected = selectedLevels.size + selectedCustomLevels.size
                    selectedCustomLevels =
                      if (selected && totalSelected > 1) selectedCustomLevels - lvl.id
                      else selectedCustomLevels + lvl.id
                  },
                  leadingIcon =
                    if (selected) {
                      { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                  label = { Text(lvl.name, maxLines = 1) },
                  modifier = Modifier.testTag("question_custom_level_chip_${lvl.id}"),
                )
              }
            }
          }

          OutlinedTextField(
            value = prompt,
            onValueChange = {
              if (it.length <= 180) prompt = it
              validationError = null
            },
            label = { Text("Question Prompt *") },
            placeholder = { Text("e.g. Find the missing letter!") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { clueFocus.requestFocus() }),
            modifier = Modifier.fillMaxWidth().testTag("question_prompt_input"),
            shape = RoundedCornerShape(14.dp),
          )

          OutlinedTextField(
            value = clueField,
            onValueChange = { updated ->
              if (updated.text.length <= 160) clueField = updated
            },
            label = { Text("Visual Clue / Pattern (optional)") },
            placeholder = {
              Text(if (isSentence) "Sentence or clue text" else "e.g. ⭐, S, ?, A, R  or  🍎🍎, +, 🍎, =, ?")
            },
            supportingText = {
              Text(
                if (isSentence) "Shown as a single sentence above the choices"
                else "Separate items with commas or spaces. Use ? for a blank box.",
              )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { correctFocus.requestFocus() }),
            modifier =
              Modifier.fillMaxWidth()
                .focusRequester(clueFocus)
                .testTag("question_clue_input"),
            shape = RoundedCornerShape(14.dp),
          )

          if (!isSentence) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              QUICK_CLUE_TOKENS.forEach { token ->
                Surface(
                  onClick = {
                    val trimmed = clueField.text.trimEnd()
                    val nextText =
                      when {
                        trimmed.isEmpty() -> token
                        trimmed.endsWith(",") -> "$trimmed $token"
                        else -> "$trimmed, $token"
                      }
                    if (nextText.length <= 160) {
                      clueField = TextFieldValue(text = nextText, selection = TextRange(nextText.length))
                    }
                  },
                  shape = RoundedCornerShape(10.dp),
                  color = IndigoContainer.copy(alpha = 0.7f),
                  modifier = Modifier.testTag("question_quick_token_$token"),
                ) {
                  Text(
                    text = if (token == "?") "+ [?] Blank" else token,
                    style = MaterialTheme.typography.labelLarge,
                    color = IndigoDeep,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Show clue as smaller sentence text",
              style = MaterialTheme.typography.bodyMedium,
              color = SlateInk,
            )
            Switch(
              checked = isSentence,
              onCheckedChange = { isSentence = it },
              modifier = Modifier.testTag("question_sentence_switch"),
            )
          }

          if (parsedClueTokens.isNotEmpty() || prompt.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.5.dp, Color(0xFFFCD34D)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier =
                  Modifier.background(
                      Brush.verticalGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFFEDD5)))
                    )
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = prompt.ifBlank { "Live Preview" },
                  style = MaterialTheme.typography.titleSmall,
                  color = SlateInk,
                  textAlign = TextAlign.Center,
                )
                if (parsedClueTokens.isNotEmpty()) {
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    parsedClueTokens.forEach { token ->
                      if (token == "?" || token == "_") {
                        Surface(
                          shape = RoundedCornerShape(10.dp),
                          color = Color.White,
                          border = BorderStroke(2.dp, MarigoldAccent),
                        ) {
                          Text(
                            text = "?",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MarigoldAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                          )
                        }
                      } else {
                        Text(
                          text = token,
                          fontSize = if (isSentence) 16.sp else 22.sp,
                          fontWeight = FontWeight.Bold,
                          color = IndigoPrimary,
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          OutlinedTextField(
            value = correctField,
            onValueChange = { updated ->
              if (updated.text.length <= 60) correctField = updated
              validationError = null
            },
            label = { Text("Correct Answer *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { wrong1Focus.requestFocus() }),
            trailingIcon = {
              IconButton(
                onClick = { activeAnswerTarget = 0 },
                modifier = Modifier.testTag("question_answer_emoji_target_0"),
              ) {
                Icon(
                  imageVector = Icons.Filled.EmojiEmotions,
                  contentDescription = "Send quick emojis to Correct Answer",
                  tint = if (activeAnswerTarget == 0) IndigoPrimary else SlateMuted,
                )
              }
            },
            modifier =
              Modifier.fillMaxWidth()
                .focusRequester(correctFocus)
                .onFocusChanged { if (it.isFocused) activeAnswerTarget = 0 }
                .testTag("question_correct_input"),
            shape = RoundedCornerShape(14.dp),
          )

          OutlinedTextField(
            value = wrong1Field,
            onValueChange = { updated ->
              if (updated.text.length <= 60) wrong1Field = updated
              validationError = null
            },
            label = { Text("Wrong Choice 1 *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { wrong2Focus.requestFocus() }),
            trailingIcon = {
              IconButton(
                onClick = { activeAnswerTarget = 1 },
                modifier = Modifier.testTag("question_answer_emoji_target_1"),
              ) {
                Icon(
                  imageVector = Icons.Filled.EmojiEmotions,
                  contentDescription = "Send quick emojis to Wrong Choice 1",
                  tint = if (activeAnswerTarget == 1) IndigoPrimary else SlateMuted,
                )
              }
            },
            modifier =
              Modifier.fillMaxWidth()
                .focusRequester(wrong1Focus)
                .onFocusChanged { if (it.isFocused) activeAnswerTarget = 1 }
                .testTag("question_wrong_1_input"),
            shape = RoundedCornerShape(14.dp),
          )

          OutlinedTextField(
            value = wrong2Field,
            onValueChange = { updated ->
              if (updated.text.length <= 60) wrong2Field = updated
            },
            label = { Text("Wrong Choice 2") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { wrong3Focus.requestFocus() }),
            trailingIcon = {
              IconButton(
                onClick = { activeAnswerTarget = 2 },
                modifier = Modifier.testTag("question_answer_emoji_target_2"),
              ) {
                Icon(
                  imageVector = Icons.Filled.EmojiEmotions,
                  contentDescription = "Send quick emojis to Wrong Choice 2",
                  tint = if (activeAnswerTarget == 2) IndigoPrimary else SlateMuted,
                )
              }
            },
            modifier =
              Modifier.fillMaxWidth()
                .focusRequester(wrong2Focus)
                .onFocusChanged { if (it.isFocused) activeAnswerTarget = 2 }
                .testTag("question_wrong_2_input"),
            shape = RoundedCornerShape(14.dp),
          )

          OutlinedTextField(
            value = wrong3Field,
            onValueChange = { updated ->
              if (updated.text.length <= 60) wrong3Field = updated
            },
            label = { Text("Wrong Choice 3") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
              KeyboardActions(
                onDone = {
                  keyboardController?.hide()
                  focusManager.clearFocus(force = true)
                },
              ),
            trailingIcon = {
              IconButton(
                onClick = { activeAnswerTarget = 3 },
                modifier = Modifier.testTag("question_answer_emoji_target_3"),
              ) {
                Icon(
                  imageVector = Icons.Filled.EmojiEmotions,
                  contentDescription = "Send quick emojis to Wrong Choice 3",
                  tint = if (activeAnswerTarget == 3) IndigoPrimary else SlateMuted,
                )
              }
            },
            modifier =
              Modifier.fillMaxWidth()
                .focusRequester(wrong3Focus)
                .onFocusChanged { if (it.isFocused) activeAnswerTarget = 3 }
                .testTag("question_wrong_3_input"),
            shape = RoundedCornerShape(14.dp),
          )

          if (validationError != null) {
            Text(
              text = validationError!!,
              style = MaterialTheme.typography.bodySmall,
              color = CoralError,
              modifier = Modifier.testTag("question_validation_error"),
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFFF8FAFC),
          border = BorderStroke(1.dp, BorderSoft),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "Emojis →",
                style = MaterialTheme.typography.labelMedium,
                color = SlateInk,
                modifier = Modifier.align(Alignment.CenterVertically),
              )
              listOf("Correct", "Wrong 1", "Wrong 2", "Wrong 3").forEachIndexed { idx, label ->
                val selected = activeAnswerTarget == idx
                Surface(
                  onClick = { activeAnswerTarget = idx },
                  shape = RoundedCornerShape(8.dp),
                  color = if (selected) IndigoPrimary else IndigoContainer.copy(alpha = 0.5f),
                  modifier = Modifier.testTag("question_answer_target_chip_$idx"),
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White else IndigoDeep,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              answerEmojiOptions.forEach { emoji ->
                Surface(
                  onClick = { insertEmojiIntoAnswer(activeAnswerTarget, emoji) },
                  shape = RoundedCornerShape(10.dp),
                  color = IndigoContainer.copy(alpha = 0.7f),
                  modifier = Modifier.testTag("question_answer_emoji_$emoji"),
                ) {
                  Text(
                    text = emoji,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(0.8f).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
          ) {
            Text(
              text = "Cancel",
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Button(
            onClick = {
              val cleanPrompt = prompt.trim()
              val cleanCorrect = correctField.text.trim()
              val wrongList =
                listOf(wrong1Field.text, wrong2Field.text, wrong3Field.text)
                  .map { it.trim() }
                  .filter { it.isNotEmpty() && !it.equals(cleanCorrect, ignoreCase = false) }
                  .distinct()

              when {
                cleanPrompt.isEmpty() -> validationError = "Enter a question prompt"
                cleanCorrect.isEmpty() -> validationError = "Enter the correct answer"
                wrongList.isEmpty() -> validationError = "Enter at least 1 wrong choice (different from correct answer)"
                else -> {
                  val draft =
                    CustomQuestion(
                      id = initialQuestion?.id ?: "",
                      prompt = cleanPrompt,
                      clueItems = parsedClueTokens,
                      correctAnswer = cleanCorrect,
                      wrongAnswers = wrongList,
                      levels = selectedLevels,
                      customLevelIds = selectedCustomLevels,
                      isSentence = isSentence,
                      version = initialQuestion?.version ?: 1,
                    )
                  onSave(draft, initialQuestion)
                  onDismiss()
                }
              }
            },
            modifier = Modifier.weight(1.3f).height(48.dp).testTag("question_save_button"),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
          ) {
            Text(
              text = if (initialQuestion == null) "Save Question" else "Save Changes",
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}
