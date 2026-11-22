package com.example.model

import com.example.engine.ChallengeQuestion

enum class PracticeTopic(val key: String, val label: String, val description: String) {
  EARLY_SPELLING("early_spelling", "Early Spelling (3-letter)", "3-letter words & A–Z letter order"),
  MORE_SPELLING("more_spelling", "Spelling (4–5 letters)", "Longer words & 2-letter phonics"),
  COUNTING("counting", "Counting & Numbers", "Counting objects, missing numbers & skip counting"),
  PATTERNS("patterns", "Patterns & Shapes", "Visual AB, AAB, ABB & shape patterns"),
  EARLY_MATH("early_math", "Simple Math (+ / −)", "Emoji addition & early subtraction"),
  OLDER_MATH("older_math", "Older Kids Math (× / ÷)", "2-digit math, × / ÷, fractions & word problems"),
  SPANISH_BASICS("spanish_basics", "Targeted Spanish (Basics)", "Spanish words, numbers, colors & easy phrases"),
  SPANISH_INTERMEDIATE("spanish_more", "Targeted Spanish (More)", "Spanish sentences & fill-in-the-blank practice"),
  ;

  companion object {
    fun fromKey(key: String): PracticeTopic? = entries.firstOrNull { it.key == key }
  }
}

data class CustomEducationLevel(
  val id: String,
  val name: String,
  val description: String = "",
  val topics: Set<PracticeTopic> = emptySet(),
  val version: Int = 1,
  val isCustom: Boolean = true,
  val isSyncedOfficial: Boolean = false,
)

data class CustomQuestion(
  val id: String,
  val prompt: String,
  val clueItems: List<String> = emptyList(),
  val correctAnswer: String,
  val wrongAnswers: List<String>,
  val levels: Set<ChallengeMode> = setOf(ChallengeMode.PRE_K),
  val customLevelIds: Set<String> = emptySet(),
  val isSentence: Boolean = false,
  val version: Int = 1,
  val isCustom: Boolean = true,
  val isSyncedOfficial: Boolean = false,
) {
  fun toChallengeQuestion(): ChallengeQuestion {
    val cleanCorrect = correctAnswer.trim()
    val cleanWrong =
      wrongAnswers
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals(cleanCorrect, ignoreCase = false) }
        .distinct()
        .take(3)
    val resolvedItems: List<String?> =
      clueItems
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { token -> if (token == "?" || token == "_") null else token }

    return ChallengeQuestion(
      title = prompt.trim(),
      items = resolvedItems,
      correct = cleanCorrect,
      options = (listOf(cleanCorrect) + cleanWrong).shuffled(),
      isSentence = isSentence,
    )
  }

  companion object {
    fun parseClueTokens(rawInput: String, isSentence: Boolean): List<String> {
      val trimmed = rawInput.trim()
      if (trimmed.isEmpty()) return emptyList()
      if (isSentence) return listOf(trimmed)
      val tokens =
        if (trimmed.contains(',')) {
          trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        } else {
          trimmed.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        }
      return tokens
    }

    fun formatClueTokens(clueItems: List<String>, isSentence: Boolean): String {
      if (clueItems.isEmpty()) return ""
      return if (isSentence) clueItems.joinToString(" ") else clueItems.joinToString(", ")
    }
  }
}
