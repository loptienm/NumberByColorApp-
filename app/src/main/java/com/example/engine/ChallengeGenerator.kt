package com.example.engine

import com.example.model.ChallengeMode
import com.example.model.CustomEducationLevel
import com.example.model.CustomQuestion
import com.example.model.PracticeTopic
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

data class ChallengeQuestion(
  val title: String,
  /** Each token is either a display string/emoji or null to render the dashed "?" blank box. */
  val items: List<String?>,
  val correct: String,
  val options: List<String>,
  val isSentence: Boolean = false,
)

object ChallengeGenerator {
  private val letters = ('A'..'Z').map { it.toString() }
  private var lastFingerprint = ""

  private data class SpanishWord(val en: String, val es: String, val emoji: String, val easy: Boolean)

  private data class SpanishSentence(val en: String, val es: String, val easy: Boolean)

  private data class SpanishFill(
    val sentence: String,
    val answer: String,
    val wrong: List<String>,
    val en: String,
  )

  internal val words =
    listOf(
      // 3-letter words
      "CAT" to "🐱",
      "DOG" to "🐶",
      "SUN" to "☀️",
      "BUS" to "🚌",
      "PIG" to "🐷",
      "BUG" to "🐞",
      "BED" to "🛏️",
      "HAT" to "🎩",
      "FOX" to "🦊",
      "COW" to "🐮",
      "HEN" to "🐔",
      "BEE" to "🐝",
      "ANT" to "🐜",
      "OWL" to "🦉",
      "BAT" to "🦇",
      "CAR" to "🚗",
      "KEY" to "🔑",
      "CUP" to "🥤",
      "PIE" to "🥧",
      "MAP" to "🗺️",
      // 4-letter words with clear matching emojis
      "FROG" to "🐸",
      "DUCK" to "🦆",
      "FISH" to "🐟",
      "BEAR" to "🐻",
      "LION" to "🦁",
      "STAR" to "⭐",
      "MOON" to "🌙",
      "TREE" to "🌳",
      "CAKE" to "🎂",
      "BOAT" to "⛵",
      "BIKE" to "🚲",
      "DRUM" to "🥁",
      "KITE" to "🪁",
      "BALL" to "⚽",
      "CRAB" to "🦀",
      "GOAT" to "🐐",
      "WORM" to "🪱",
      "BOOK" to "📖",
      // 5-letter words with clear matching emojis
      "APPLE" to "🍎",
      "HOUSE" to "🏠",
      "TRAIN" to "🚂",
      "WHALE" to "🐋",
      "SHARK" to "🦈",
      "SNAKE" to "🐍",
      "HORSE" to "🐴",
      "MOUSE" to "🐭",
      "PANDA" to "🐼",
      "ZEBRA" to "🦓",
      "TIGER" to "🐯",
      "ROBOT" to "🤖",
      "CLOUD" to "☁️",
      "HEART" to "❤️",
      "SHEEP" to "🐑",
      "PIZZA" to "🍕",
      "GHOST" to "👻",
      "CROWN" to "👑",
    )

  private val shortWords = words.filter { it.first.length == 3 }
  private val longerWords = words.filter { it.first.length >= 4 }

  private val countEmojis = listOf("🍎", "⭐", "🐟", "🎈", "🐶", "🌸", "🍪", "🚗", "🐞", "🦋")

  private val patterns =
    listOf(
      "🔴" to "🔵",
      "🍎" to "🍌",
      "⭐" to "🌙",
      "🐶" to "🐱",
      "🔺" to "🟩",
      "🌸" to "🐝",
      "🦋" to "🐞",
      "🐸" to "🐢",
      "🚗" to "🚂",
      "☀️" to "🌈",
    )

  private val shapeSets =
    listOf(
      listOf("🟦", "🔴", "🔺"),
      listOf("🟩", "🟡", "🔻"),
      listOf("🟪", "⚫", "🔶"),
      listOf("🟧", "🔵", "🔺"),
      listOf("⭐", "❤️", "🌙"),
      listOf("🍎", "🍌", "🍇"),
    )

  private val spanishWords =
    listOf(
      SpanishWord("one", "uno", "1️⃣", true),
      SpanishWord("two", "dos", "2️⃣", true),
      SpanishWord("three", "tres", "3️⃣", true),
      SpanishWord("four", "cuatro", "4️⃣", true),
      SpanishWord("five", "cinco", "5️⃣", true),
      SpanishWord("six", "seis", "6️⃣", true),
      SpanishWord("seven", "siete", "7️⃣", true),
      SpanishWord("eight", "ocho", "8️⃣", true),
      SpanishWord("nine", "nueve", "9️⃣", true),
      SpanishWord("ten", "diez", "🔟", true),
      SpanishWord("red", "rojo", "🔴", true),
      SpanishWord("blue", "azul", "🔵", true),
      SpanishWord("green", "verde", "🟢", true),
      SpanishWord("yellow", "amarillo", "🟡", true),
      SpanishWord("dog", "perro", "🐶", true),
      SpanishWord("cat", "gato", "🐱", true),
      SpanishWord("fish", "pez", "🐟", false),
      SpanishWord("bird", "pájaro", "🐦", false),
      SpanishWord("horse", "caballo", "🐴", false),
      SpanishWord("rabbit", "conejo", "🐰", false),
      SpanishWord("chicken", "pollo", "🐔", false),
      SpanishWord("house", "casa", "🏠", false),
      SpanishWord("water", "agua", "💧", true),
      SpanishWord("sun", "sol", "☀️", true),
      SpanishWord("moon", "luna", "🌙", false),
      SpanishWord("star", "estrella", "⭐", false),
      SpanishWord("tree", "árbol", "🌳", false),
      SpanishWord("flower", "flor", "🌸", false),
      SpanishWord("apple", "manzana", "🍎", false),
      SpanishWord("bread", "pan", "🍞", false),
      SpanishWord("milk", "leche", "🥛", false),
      SpanishWord("cheese", "queso", "🧀", false),
      SpanishWord("book", "libro", "📖", false),
      SpanishWord("heart", "corazón", "❤️", false),
      SpanishWord("school", "escuela", "🏫", false),
      SpanishWord("friend", "amigo", "🤝", false),
      SpanishWord("boy", "niño", "👦", false),
      SpanishWord("girl", "niña", "👧", false),
      SpanishWord("mother", "madre", "👩", false),
      SpanishWord("father", "padre", "👨", false),
      SpanishWord("grandmother", "abuela", "👵", false),
      SpanishWord("grandfather", "abuelo", "👴", false),
      SpanishWord("sister", "hermana", "👧", false),
      SpanishWord("brother", "hermano", "👦", false),
      SpanishWord("baby", "bebé", "👶", false),
      SpanishWord("teacher", "maestra", "🧑‍🏫", false),
      SpanishWord("hello", "hola", "👋", true),
      SpanishWord("goodbye", "adiós", "👋", false),
      SpanishWord("thank you", "gracias", "🙏", false),
      SpanishWord("please", "por favor", "🙏", false),
      SpanishWord("good morning", "buenos días", "🌅", false),
      SpanishWord("good night", "buenas noches", "🌙", false),
      SpanishWord("white", "blanco", "⚪", false),
      SpanishWord("black", "negro", "⚫", false),
      SpanishWord("big", "grande", "🐘", false),
      SpanishWord("small", "pequeño", "🐜", false),
      SpanishWord("happy", "feliz", "😀", false),
      SpanishWord("eat", "comer", "🍽️", false),
      SpanishWord("drink", "beber", "🥤", false),
    )

  private val spanishSentences =
    listOf(
      SpanishSentence("I am happy.", "Estoy feliz.", true),
      SpanishSentence("I like the dog.", "Me gusta el perro.", true),
      SpanishSentence("The cat is big.", "El gato es grande.", true),
      SpanishSentence("I have a book.", "Tengo un libro.", true),
      SpanishSentence("Mom is here.", "Mamá está aquí.", true),
      SpanishSentence("The water is cold.", "El agua está fría.", false),
      SpanishSentence("I eat an apple.", "Como una manzana.", false),
      SpanishSentence("My name is Ana.", "Me llamo Ana.", true),
      SpanishSentence("How are you?", "¿Cómo estás?", true),
      SpanishSentence("Where is the house?", "¿Dónde está la casa?", false),
      SpanishSentence("I love my mother.", "Quiero a mi madre.", false),
      SpanishSentence("The girl drinks milk.", "La niña bebe leche.", false),
      SpanishSentence("The bird is blue.", "El pájaro es azul.", false),
      SpanishSentence("We go to school.", "Vamos a la escuela.", false),
      SpanishSentence("I see the moon.", "Veo la luna.", false),
      SpanishSentence("The boy has a dog.", "El niño tiene un perro.", false),
      SpanishSentence("I am eight years old.", "Tengo ocho años.", false),
      SpanishSentence("Good morning, teacher!", "¡Buenos días, maestra!", false),
      SpanishSentence("The flowers are pretty.", "Las flores son bonitas.", false),
      SpanishSentence("My brother is small.", "Mi hermano es pequeño.", false),
    )

  private val spanishFills =
    listOf(
      SpanishFill("Yo ___ agua.", "bebo", listOf("como", "tengo", "soy"), "I drink water."),
      SpanishFill("El gato ___ grande.", "es", listOf("soy", "tengo", "bebo"), "The cat is big."),
      SpanishFill("Yo ___ una manzana.", "como", listOf("bebo", "soy", "veo"), "I eat an apple."),
      SpanishFill("___ llamo Ana.", "Me", listOf("Te", "Su", "Yo"), "My name is Ana."),
      SpanishFill("Tengo un ___.", "perro", listOf("rojo", "estoy", "grande"), "I have a dog."),
      SpanishFill("Mi ___ es muy buena.", "madre", listOf("pan", "rojo", "casa"), "My mother is very good."),
      SpanishFill("La casa es ___.", "azul", listOf("perro", "comer", "madre"), "The house is blue."),
      SpanishFill("¿Cómo ___?", "estás", listOf("como", "azul", "luna"), "How are you?"),
      SpanishFill("Mi ___ lee un libro.", "hermana", listOf("leche", "verde", "agua"), "My sister reads a book."),
      SpanishFill("Yo ___ feliz.", "estoy", listOf("bebo", "perro", "madre"), "I am happy."),
    )

  private fun generatorsForTopic(topic: PracticeTopic): List<() -> ChallengeQuestion> =
    when (topic) {
      PracticeTopic.EARLY_SPELLING ->
        listOf(
          { qMissingLetterInWord(longerOnly = false) },
          { qLetterSequence(5) },
        )
      PracticeTopic.MORE_SPELLING ->
        listOf(
          { qMissingLetterInWord(longerOnly = true) },
          ::qMissingTwoLettersInWord,
        )
      PracticeTopic.COUNTING ->
        listOf(
          { qMissingNumber(6, 4) },
          { qMissingNumber(10, 5) },
          { qNextNumber(8, 4) },
          { qCountdown(maxStart = 8, len = 4) },
          { qCount(maxCount = 9) },
          ::qSkipCount,
        )
      PracticeTopic.PATTERNS ->
        listOf(
          { qPattern(PatternStyle.AB) },
          { qPattern(PatternStyle.AAB) },
          { qPattern(PatternStyle.ABB) },
          ::qPatternMissingMiddle,
          ::qShapePattern,
        )
      PracticeTopic.EARLY_MATH ->
        listOf(
          ::qPreKEmojiAddition,
          ::qPreKNumberAddition,
          { qAdd(1, 9, 1, 9) },
          { qSub(8, 18) },
        )
      PracticeTopic.OLDER_MATH ->
        listOf(
          { qAdd(23, 89, 12, 69) },
          { qSub(40, 99) },
          ::qMultiply,
          ::qDivide,
          ::qMissingFactor,
          ::qFraction,
          ::qRound,
          ::qWordProblem,
        )
      PracticeTopic.SPANISH_BASICS ->
        listOf(
          { qSpanishWord(easyOnly = true) },
          { qSpanishSentence(easyOnly = true) },
        )
      PracticeTopic.SPANISH_INTERMEDIATE ->
        listOf(
          { qSpanishWord(easyOnly = false) },
          { qSpanishSentence(easyOnly = false) },
          ::qSpanishFill,
        )
    }

  fun nextQuestion(
    mode: ChallengeMode,
    customQuestions: List<CustomQuestion> = emptyList(),
    customLevel: CustomEducationLevel? = null,
  ): ChallengeQuestion {
    val builtInGenerators: List<() -> ChallengeQuestion> =
      if (customLevel != null && mode != ChallengeMode.OFF && customLevel.topics.isNotEmpty()) {
        customLevel.topics.flatMap { generatorsForTopic(it) }
      } else {
        when (mode) {
          ChallengeMode.PRE_K,
          ChallengeMode.OFF ->
            listOf(
              { qMissingNumber(6, 4) },
              { qMissingNumber(5, 5) },
              { qNextNumber(6, 3) },
              { qCountdown(maxStart = 6, len = 4) },
              { qCount(maxCount = 9) },
              ::qPreKEmojiAddition,
              ::qPreKNumberAddition,
              { qMissingLetterInWord(longerOnly = false) },
              { qMissingLetterInWord(longerOnly = true) },
              ::qMissingTwoLettersInWord,
              { qLetterSequence(5) },
              { qPattern(PatternStyle.AB) },
              { qPattern(PatternStyle.AAB) },
              { qPattern(PatternStyle.ABB) },
              ::qPatternMissingMiddle,
              ::qShapePattern,
            )
          ChallengeMode.KINDER ->
            listOf(
              { qMissingNumber(14, 5) },
              { qNextNumber(14, 4) },
              { qCountdown(maxStart = 15, len = 5) },
              ::qSkipCount,
              { qAdd(1, 9, 1, 9) },
              { qSub(8, 18) },
              { qLetterSequence(5) },
              { qPattern(PatternStyle.AAB) },
              { qPattern(PatternStyle.ABB) },
              ::qPatternMissingMiddle,
              ::qShapePattern,
              { qMissingLetterInWord(longerOnly = true) },
              ::qMissingTwoLettersInWord,
              { qSpanishWord(easyOnly = true) },
              { qSpanishSentence(easyOnly = true) },
            )
          ChallengeMode.OLDER ->
            listOf(
              { qAdd(23, 89, 12, 69) },
              { qSub(40, 99) },
              ::qMultiply,
              ::qDivide,
              ::qMissingFactor,
              ::qFraction,
              ::qRound,
              ::qWordProblem,
              { qSpanishWord(easyOnly = false) },
              { qSpanishSentence(easyOnly = false) },
              ::qSpanishFill,
            )
        }
      }

    val validCustom =
      customQuestions.filter { q ->
        q.prompt.isNotBlank() && q.correctAnswer.isNotBlank() && q.wrongAnswers.isNotEmpty()
      }
    val matchingCustom =
      if (customLevel != null && mode != ChallengeMode.OFF) {
        validCustom.filter { customLevel.id in it.customLevelIds }
      } else {
        validCustom.filter { mode in it.levels }
      }

    fun pickCandidate(): ChallengeQuestion {
      if (customLevel != null && mode != ChallengeMode.OFF && customLevel.topics.isEmpty() && matchingCustom.isNotEmpty()) {
        return matchingCustom.random().toChallengeQuestion()
      }
      if (matchingCustom.isNotEmpty()) {
        // Give custom questions a strong, balanced chance (up to ~40%-45%) so kids encounter them naturally.
        val customChance = min(0.45f, 0.18f + matchingCustom.size * 0.06f)
        if (Random.nextFloat() < customChance) {
          return matchingCustom.random().toChallengeQuestion()
        }
      }
      return builtInGenerators.random()()
    }

    var candidate = pickCandidate()
    for (attempt in 0 until 8) {
      val fp = candidate.title + candidate.items.joinToString("")
      if (fp != lastFingerprint) break
      candidate = pickCandidate()
    }
    lastFingerprint = candidate.title + candidate.items.joinToString("")
    return candidate
  }

  private fun rnd(lo: Int, hi: Int): Int = Random.nextInt(lo, hi + 1)

  private fun numChoices(correct: Int, deltas: List<Int>, lo: Int, hi: Int): List<String> {
    val wrong = linkedSetOf<Int>()
    val candidates = deltas.flatMap { d -> listOf(correct - d, correct + d) }.shuffled()
    for (v in candidates) {
      if (v in lo..hi && v != correct) wrong.add(v)
      if (wrong.size == 3) break
    }
    var guard = 0
    while (wrong.size < 3 && guard < 300) {
      guard++
      val v = rnd(max(lo, correct - 8), min(hi, correct + 8))
      if (v != correct) wrong.add(v)
    }
    return (listOf(correct) + wrong).shuffled().map { it.toString() }
  }

  private fun letterChoices(correct: String, near: Boolean): List<String> {
    val idx = letters.indexOf(correct)
    val pool =
      letters
        .filterIndexed { i, l -> l != correct && (!near || abs(i - idx) <= 4) }
        .shuffled()
        .take(3)
    return (listOf(correct) + pool).shuffled()
  }

  private fun twoLetterChoices(correct: String): List<String> {
    val vowels = listOf('A', 'E', 'I', 'O', 'U')
    val commonConsonants = listOf('B', 'C', 'D', 'F', 'G', 'L', 'M', 'N', 'P', 'R', 'S', 'T')
    val wrong = linkedSetOf<String>()
    val first = correct[0]
    val second = correct[1]

    var guard = 0
    while (wrong.size < 3 && guard < 120) {
      guard++
      val candidate =
        when (guard % 3) {
          0 -> {
            val altFirst = (if (first in vowels) vowels else commonConsonants).random()
            "$altFirst$second"
          }
          1 -> {
            val altSecond = (if (second in vowels) vowels else commonConsonants).random()
            "$first$altSecond"
          }
          else -> {
            val a = commonConsonants.random()
            val b = vowels.random()
            if (Random.nextBoolean()) "$a$b" else "$b$a"
          }
        }
      if (candidate != correct) {
        wrong.add(candidate)
      }
    }
    return (listOf(correct) + wrong.take(3)).shuffled()
  }

  private fun qMissingNumber(maxStart: Int, len: Int): ChallengeQuestion {
    val start = rnd(1, maxStart)
    val seq = List(len) { start + it }
    val hide = rnd(0, len - 2)
    val ans = seq[hide]
    return ChallengeQuestion(
      title = "Find the missing number!",
      items = seq.mapIndexed { i, v -> if (i == hide) null else v.toString() },
      correct = ans.toString(),
      options = numChoices(ans, listOf(1, 2), 0, maxStart + len + 3),
    )
  }

  private fun qNextNumber(maxStart: Int, len: Int): ChallengeQuestion {
    val start = rnd(1, maxStart)
    val seq = List(len + 1) { start + it }
    val ans = seq[len]
    return ChallengeQuestion(
      title = "What comes next?",
      items = seq.take(len).map { it.toString() } + null,
      correct = ans.toString(),
      options = numChoices(ans, listOf(1, 2), 0, maxStart + len + 3),
    )
  }

  internal fun qCountdown(maxStart: Int = 6, len: Int = 4): ChallengeQuestion {
    val start = rnd(len, max(len, maxStart))
    val seq = List(len) { start - it }
    val hide = rnd(1, len - 1)
    val ans = seq[hide]
    return ChallengeQuestion(
      title = "Count backwards!",
      items = seq.mapIndexed { i, v -> if (i == hide) null else v.toString() },
      correct = ans.toString(),
      options = numChoices(ans, listOf(1, 2), 0, maxStart + 3),
    )
  }

  private fun qSkipCount(): ChallengeQuestion {
    val step = listOf(2, 5, 10).random()
    val seq = (1..5).map { it * step }
    val hide = rnd(1, 3)
    val ans = seq[hide]
    return ChallengeQuestion(
      title = "Counting by ${step}s: find the missing number!",
      items = seq.mapIndexed { i, v -> if (i == hide) null else v.toString() },
      correct = ans.toString(),
      options = numChoices(ans, listOf(step, 2 * step, 1), 0, 6 * step),
    )
  }

  private fun qLetterSequence(len: Int): ChallengeQuestion {
    val start = rnd(0, 20)
    val seq = List(len) { letters[start + it] }
    val hide = rnd(0, len - 1)
    val ans = seq[hide]
    return ChallengeQuestion(
      title = "Find the missing letter!",
      items = seq.mapIndexed { i, v -> if (i == hide) null else v },
      correct = ans,
      options = letterChoices(ans, near = true),
    )
  }

  internal fun qMissingLetterInWord(longerOnly: Boolean = false): ChallengeQuestion {
    val pool = if (longerOnly) longerWords else shortWords + longerWords
    val (word, emoji) = pool.random()
    val hide = rnd(0, word.length - 1)
    val ans = word[hide].toString()
    val items = listOf(emoji) + word.mapIndexed { i, ch -> if (i == hide) null else ch.toString() }
    return ChallengeQuestion(
      title = "Find the missing letter!",
      items = items,
      correct = ans,
      options = letterChoices(ans, near = false),
    )
  }

  internal fun qMissingTwoLettersInWord(): ChallengeQuestion {
    val (word, emoji) = longerWords.random()
    val startHide = rnd(0, word.length - 2)
    val ans = word.substring(startHide, startHide + 2)
    val items =
      listOf(emoji) +
        word.mapIndexed { i, ch ->
          if (i == startHide || i == startHide + 1) null else ch.toString()
        }
    return ChallengeQuestion(
      title = "Find the 2 missing letters!",
      items = items,
      correct = ans,
      options = twoLetterChoices(ans),
    )
  }

  private fun qCount(maxCount: Int = 9): ChallengeQuestion {
    val n = rnd(1, maxCount)
    val emoji = countEmojis.random()
    return ChallengeQuestion(
      title = "How many do you see?",
      items = List(n) { emoji },
      correct = n.toString(),
      options = numChoices(n, listOf(1, 2), 1, maxCount + 1),
    )
  }

  internal fun qPreKEmojiAddition(): ChallengeQuestion {
    val a = rnd(1, 4)
    val b = rnd(1, 4)
    val sum = a + b
    val emoji = countEmojis.random()
    return ChallengeQuestion(
      title = "Add them up!",
      items = listOf(emoji.repeat(a), "+", emoji.repeat(b), "=", null),
      correct = sum.toString(),
      options = numChoices(sum, listOf(1, 2), 2, 10),
    )
  }

  internal fun qPreKNumberAddition(): ChallengeQuestion {
    val a = rnd(1, 5)
    val b = rnd(1, 4)
    val sum = a + b
    val emoji = countEmojis.random()
    return ChallengeQuestion(
      title = "How many in all?",
      items = listOf("$a", emoji, "+", "$b", emoji, "=", null),
      correct = sum.toString(),
      options = numChoices(sum, listOf(1, 2), 2, 10),
    )
  }

  internal enum class PatternStyle { AB, AAB, ABB }

  internal fun qPattern(style: PatternStyle): ChallengeQuestion {
    val (a, b) = patterns.random()
    val pat =
      when (style) {
        PatternStyle.AB -> listOf(a, b, a, b, a, b)
        PatternStyle.AAB -> listOf(a, a, b, a, a, b)
        PatternStyle.ABB -> listOf(a, b, b, a, b, b)
      }
    val others =
      patterns.filter { it.first != a }.flatMap { listOf(it.first, it.second) }.shuffled().take(2)
    return ChallengeQuestion(
      title = "What comes next?",
      items = pat.take(5) + null,
      correct = pat[5],
      options = (listOf(a, b) + others).distinct().shuffled(),
    )
  }

  internal fun qPatternMissingMiddle(): ChallengeQuestion {
    val useThree = Random.nextBoolean()
    return if (useThree) {
      val set = shapeSets.random()
      val pat = set + set
      val hide = rnd(1, 4)
      val extra = shapeSets.filter { it != set }.flatten().filter { it !in set }.random()
      ChallengeQuestion(
        title = "Complete the pattern!",
        items = pat.mapIndexed { idx, s -> if (idx == hide) null else s },
        correct = pat[hide],
        options = (set + extra).shuffled(),
      )
    } else {
      val (a, b) = patterns.random()
      val pat =
        when (rnd(0, 2)) {
          0 -> listOf(a, b, a, b, a, b)
          1 -> listOf(a, a, b, a, a, b)
          else -> listOf(a, b, b, a, b, b)
        }
      val hide = rnd(1, 4)
      val others =
        patterns.filter { it.first != a }.flatMap { listOf(it.first, it.second) }.shuffled().take(2)
      ChallengeQuestion(
        title = "Complete the pattern!",
        items = pat.mapIndexed { idx, s -> if (idx == hide) null else s },
        correct = pat[hide],
        options = (listOf(a, b) + others).distinct().shuffled(),
      )
    }
  }

  private fun qShapePattern(): ChallengeQuestion {
    val set = shapeSets.random()
    val pat = set + set
    val extra = shapeSets.filter { it != set }.flatten().filter { it !in set }.random()
    return ChallengeQuestion(
      title = "What shape comes next?",
      items = pat.take(5) + null,
      correct = pat[5],
      options = (set + extra).shuffled(),
    )
  }

  private fun qSpanishWord(easyOnly: Boolean): ChallengeQuestion {
    val pool = if (easyOnly) spanishWords.filter { it.easy } else spanishWords
    val word = pool.random()
    val others = pool.filter { it.en != word.en }.shuffled().take(3)
    return if (Random.nextBoolean()) {
      ChallengeQuestion(
        title = "How do you say \"${word.en}\" in Spanish?",
        items = listOf(word.emoji),
        correct = word.es,
        options = (listOf(word.es) + others.map { it.es }).shuffled(),
      )
    } else {
      ChallengeQuestion(
        title = "What does \"${word.es}\" mean in English?",
        items = emptyList(),
        correct = word.en,
        options = (listOf(word.en) + others.map { it.en }).shuffled(),
      )
    }
  }

  private fun qSpanishSentence(easyOnly: Boolean): ChallengeQuestion {
    val pool = if (easyOnly) spanishSentences.filter { it.easy } else spanishSentences
    val item = pool.random()
    val others = spanishSentences.filter { it.en != item.en }.shuffled().take(3)
    return if (Random.nextBoolean()) {
      ChallengeQuestion(
        title = "What does this mean?",
        items = listOf(item.es),
        correct = item.en,
        options = (listOf(item.en) + others.map { it.en }).shuffled(),
        isSentence = true,
      )
    } else {
      ChallengeQuestion(
        title = "How do you say this in Spanish?",
        items = listOf(item.en),
        correct = item.es,
        options = (listOf(item.es) + others.map { it.es }).shuffled(),
        isSentence = true,
      )
    }
  }

  private fun qSpanishFill(): ChallengeQuestion {
    val item = spanishFills.random()
    return ChallengeQuestion(
      title = "Which word is missing? (${item.en})",
      items = listOf(item.sentence),
      correct = item.answer,
      options = (listOf(item.answer) + item.wrong).shuffled(),
      isSentence = true,
    )
  }

  private fun qAdd(aLo: Int, aHi: Int, bLo: Int, bHi: Int): ChallengeQuestion {
    val a = rnd(aLo, aHi)
    val b = rnd(bLo, bHi)
    val c = a + b
    return ChallengeQuestion(
      title = "$a + $b = ?",
      items = emptyList(),
      correct = c.toString(),
      options = numChoices(c, listOf(1, 2, 10, 9, 11), 0, c + 20),
    )
  }

  private fun qSub(lo: Int, hi: Int): ChallengeQuestion {
    val a = rnd(lo, hi)
    val minB = max(1, a / 5)
    val b = rnd(minB, max(minB, a - minB))
    val c = a - b
    return ChallengeQuestion(
      title = "$a − $b = ?",
      items = emptyList(),
      correct = c.toString(),
      options = numChoices(c, listOf(1, 2, 10, 9, 11), 0, a + 5),
    )
  }

  private fun qMultiply(): ChallengeQuestion {
    val a = rnd(3, 12)
    val b = rnd(3, 12)
    val c = a * b
    return ChallengeQuestion(
      title = "$a × $b = ?",
      items = emptyList(),
      correct = c.toString(),
      options = numChoices(c, listOf(1, 2, a, b, 10), 1, 150),
    )
  }

  private fun qDivide(): ChallengeQuestion {
    val b = rnd(2, 9)
    val q = rnd(3, 12)
    val a = b * q
    return ChallengeQuestion(
      title = "$a ÷ $b = ?",
      items = emptyList(),
      correct = q.toString(),
      options = numChoices(q, listOf(1, 2, 3, b), 1, 20),
    )
  }

  private fun qMissingFactor(): ChallengeQuestion {
    val b = rnd(3, 9)
    val q = rnd(3, 12)
    return ChallengeQuestion(
      title = "Find the missing number!",
      items = listOf(null, "×", b.toString(), "=", (b * q).toString()),
      correct = q.toString(),
      options = numChoices(q, listOf(1, 2, 3), 1, 15),
    )
  }

  private fun qFraction(): ChallengeQuestion {
    val (label, denom) = listOf("½" to 2, "¼" to 4, "⅓" to 3).random()
    val q = rnd(2, 12)
    val whole = denom * q
    return ChallengeQuestion(
      title = "What is $label of $whole?",
      items = emptyList(),
      correct = q.toString(),
      options = numChoices(q, listOf(1, 2, q), 1, 60),
    )
  }

  private fun qRound(): ChallengeQuestion {
    val n = rnd(11, 98)
    val c = (n / 10.0).roundToInt() * 10
    val opts = numChoices(c / 10, listOf(1, 2), 0, 12).map { (it.toInt() * 10).toString() }
    return ChallengeQuestion(
      title = "Round $n to the nearest ten",
      items = emptyList(),
      correct = c.toString(),
      options = opts,
    )
  }

  private fun qWordProblem(): ChallengeQuestion {
    val name = listOf("Mia", "Sam", "Ava", "Leo", "Zoe", "Max").random()
    val thing = listOf("stickers", "cookies", "marbles", "cards").random()
    return when (rnd(0, 2)) {
      0 -> {
        val a = rnd(20, 60)
        val b = rnd(5, 18)
        ChallengeQuestion(
          title = "$name has $a $thing and gives away $b. How many are left?",
          items = emptyList(),
          correct = (a - b).toString(),
          options = numChoices(a - b, listOf(1, 2, 10), 0, 70),
        )
      }
      1 -> {
        val a = rnd(3, 9)
        val b = rnd(3, 9)
        ChallengeQuestion(
          title = "$name has $a bags with $b $thing in each. How many $thing in all?",
          items = emptyList(),
          correct = (a * b).toString(),
          options = numChoices(a * b, listOf(1, a, b, 10), 1, 90),
        )
      }
      else -> {
        val a = rnd(15, 45)
        val b = rnd(12, 35)
        ChallengeQuestion(
          title = "$name has $a $thing and finds $b more. How many now?",
          items = emptyList(),
          correct = (a + b).toString(),
          options = numChoices(a + b, listOf(1, 2, 10), 0, 90),
        )
      }
    }
  }
}
