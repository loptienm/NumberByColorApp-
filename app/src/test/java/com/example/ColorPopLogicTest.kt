package com.example

import androidx.compose.ui.geometry.Offset
import com.example.data.ALL_BUILT_IN_TEMPLATES
import com.example.engine.ChallengeGenerator
import com.example.model.ChallengeMode
import com.example.model.STICKER_MAX_SCALE
import com.example.model.STICKER_MIN_SCALE
import com.example.model.charToColorNumber
import com.example.model.clampStickerFraction
import com.example.model.clampStickerScale
import com.example.model.colorNumberToChar
import com.example.model.deserializePaintedGrid
import com.example.model.serializePaintedGrid
import com.example.ui.clampPanOffset
import com.example.ui.computeViewportMetrics
import com.example.ui.screenToGridCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPopLogicTest {

  @Test
  fun viewportCellMapping_isExactAcrossZoomAndPan() {
    val gridW = 32
    val gridH = 32
    val canvasW = 1200f
    val canvasH = 800f
    val railPx = 160f

    listOf(1f to (0f to 0f), 2.5f to (-140f to 95f), 6f to (320f to -280f)).forEach { (zoom, pan) ->
      val base = computeViewportMetrics(canvasW, canvasH, railPx, gridW, gridH, zoom, 0f, 0f)
      val (cx, cy) = clampPanOffset(pan.first, pan.second, base, canvasW, canvasH, railPx, zoom)
      val metrics = computeViewportMetrics(canvasW, canvasH, railPx, gridW, gridH, zoom, cx, cy)

      for (r in listOf(0, 7, 15, 31)) {
        for (c in listOf(0, 8, 21, 31)) {
          val cellCenter =
            Offset(
              metrics.originX + (c + 0.5f) * metrics.cellSize,
              metrics.originY + (r + 0.5f) * metrics.cellSize,
            )
          assertEquals(r to c, screenToGridCell(cellCenter, metrics, gridW, gridH))
        }
      }
    }
  }

  @Test
  fun allBuiltInTemplates_areConsistent() {
    assertEquals(25, ALL_BUILT_IN_TEMPLATES.size)
    assertEquals(ALL_BUILT_IN_TEMPLATES.size, ALL_BUILT_IN_TEMPLATES.map { it.id }.distinct().size)
    ALL_BUILT_IN_TEMPLATES.forEach { tpl ->
      assertEquals("${tpl.id} height mismatch", tpl.gridHeight, tpl.grid.size)
      assertTrue("${tpl.id} empty palette", tpl.palette.isNotEmpty())
      var paintable = 0
      val usedInGrid = mutableSetOf<Int>()
      for (r in 0 until tpl.gridHeight) {
        assertEquals("${tpl.id} row $r width mismatch", tpl.gridWidth, tpl.grid[r].length)
        for (c in 0 until tpl.gridWidth) {
          val num = tpl.cellNumber(r, c)
          if (num > 0) {
            paintable++
            usedInGrid.add(num)
            assertTrue("${tpl.id} missing color $num in palette", num in tpl.palette)
          }
        }
      }
      assertTrue("${tpl.id} has no paintable cells", paintable > 0)
      if (tpl.frames.isNotEmpty()) {
        assertEquals("${tpl.id} frame 0 should match grid", tpl.grid, tpl.frames.first())
        tpl.frames.forEachIndexed { fIdx, frame ->
          assertEquals("${tpl.id} frame $fIdx height mismatch", tpl.gridHeight, frame.size)
          for (r in 0 until tpl.gridHeight) {
            assertEquals("${tpl.id} frame $fIdx row $r width mismatch", tpl.gridWidth, frame[r].length)
            for (c in 0 until tpl.gridWidth) {
              val num = tpl.frameCellNumber(fIdx, r, c)
              if (num > 0) {
                assertTrue("${tpl.id} frame $fIdx missing color $num in palette", num in tpl.palette)
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun base36AndGridSerialization_roundTrips() {
    for (n in 1..20) {
      assertEquals(n, charToColorNumber(colorNumberToChar(n)))
    }
    val grid =
      arrayOf(
        intArrayOf(0, 1, 9, 10, 12),
        intArrayOf(2, 0, 5, 11, 0),
      )
    val serialized = serializePaintedGrid(grid)
    val restored = deserializePaintedGrid(serialized, width = 5, height = 2)
    assertEquals(2, restored.size)
    assertTrue(grid[0].contentEquals(restored[0]))
    assertTrue(grid[1].contentEquals(restored[1]))
  }

  @Test
  fun challengeGenerator_producesValidQuestionsForAllModes() {
    listOf(ChallengeMode.PRE_K, ChallengeMode.KINDER, ChallengeMode.OLDER).forEach { mode ->
      repeat(30) {
        val q = ChallengeGenerator.nextQuestion(mode)
        assertTrue("Question title blank for $mode", q.title.isNotBlank())
        assertTrue("Expected at least 2 options for $mode", q.options.size >= 2)
        assertEquals("Options not distinct for $mode: ${q.options}", q.options.size, q.options.distinct().size)
        assertTrue("Correct answer missing from options for $mode", q.correct in q.options)
      }
    }
  }

  @Test
  fun stickerBoundsAndScaleClamping_keepStickersInsidePageAndWithinRange() {
    assertEquals(STICKER_MIN_SCALE, clampStickerScale(0.1f), 0.0001f)
    assertEquals(STICKER_MAX_SCALE, clampStickerScale(5.0f), 0.0001f)
    assertEquals(1.25f, clampStickerScale(1.25f), 0.0001f)

    val minPos = clampStickerFraction(-0.5f, halfStickerFraction = 0.12f)
    val maxPos = clampStickerFraction(1.8f, halfStickerFraction = 0.12f)
    assertTrue("Expected sticker clamped inside left/top margin, got $minPos", minPos > 0f && minPos < 0.12f)
    assertTrue("Expected sticker clamped inside right/bottom margin, got $maxPos", maxPos < 1f && maxPos > 0.88f)
    assertEquals(0.5f, clampStickerFraction(0.5f, halfStickerFraction = 0.12f), 0.0001f)
  }

  @Test
  fun preKUpgrades_includeLadybugForBug_twoLetterBlanks_longerWords_emojiMath_and_advancedPatterns() {
    val bugEntry = ChallengeGenerator.words.first { it.first == "BUG" }
    assertEquals("🐞", bugEntry.second)
    assertTrue(ChallengeGenerator.words.any { it.first.length == 4 })
    assertTrue(ChallengeGenerator.words.any { it.first.length == 5 })

    repeat(20) {
      val twoMissing = ChallengeGenerator.qMissingTwoLettersInWord()
      assertEquals(2, twoMissing.items.count { it == null })
      assertEquals(2, twoMissing.correct.length)
      assertEquals(4, twoMissing.options.distinct().size)
      assertTrue(twoMissing.correct in twoMissing.options)

      val emojiAdd = ChallengeGenerator.qPreKEmojiAddition()
      assertEquals(listOf("+", "=", null), listOf(emojiAdd.items[1], emojiAdd.items[3], emojiAdd.items[4]))
      assertTrue(emojiAdd.correct in emojiAdd.options)

      val numAdd = ChallengeGenerator.qPreKNumberAddition()
      assertEquals(7, numAdd.items.size)
      assertTrue(numAdd.correct in numAdd.options)

      val midPat = ChallengeGenerator.qPatternMissingMiddle()
      assertEquals(1, midPat.items.count { it == null })
      assertTrue(midPat.correct in midPat.options)

      val countdown = ChallengeGenerator.qCountdown()
      assertTrue(countdown.correct in countdown.options)
    }

    val aab = ChallengeGenerator.qPattern(ChallengeGenerator.PatternStyle.AAB)
    assertEquals(aab.items[0], aab.items[1])
    assertEquals(aab.items[2], aab.correct)

    val abb = ChallengeGenerator.qPattern(ChallengeGenerator.PatternStyle.ABB)
    assertEquals(abb.items[1], abb.items[2])
    assertEquals(abb.items[1], abb.correct)
  }

  @Test
  fun customQuestion_convertsBlankTokensAndMixesByDifficultyLevel() {
    val customQ =
      com.example.model.CustomQuestion(
        id = "q_star",
        prompt = "Spell STAR!",
        clueItems = listOf("⭐", "S", "?", "A", "R"),
        correctAnswer = "T",
        wrongAnswers = listOf("M", "P", "L"),
        levels = setOf(ChallengeMode.PRE_K),
      )
    val generated = customQ.toChallengeQuestion()
    assertEquals("Spell STAR!", generated.title)
    assertEquals(listOf("⭐", "S", null, "A", "R"), generated.items)
    assertEquals("T", generated.correct)
    assertEquals(setOf("T", "M", "P", "L"), generated.options.toSet())

    // Filtered out when active mode doesn't match
    var sawCustomInPreK = false
    repeat(80) {
      val q = ChallengeGenerator.nextQuestion(ChallengeMode.PRE_K, listOf(customQ))
      if (q.title == "Spell STAR!") sawCustomInPreK = true
    }
    assertTrue("Expected custom Pre-K question to appear in Pre-K pool", sawCustomInPreK)

    repeat(30) {
      val q = ChallengeGenerator.nextQuestion(ChallengeMode.OLDER, listOf(customQ))
      org.junit.Assert.assertNotEquals("Spell STAR!", q.title)
    }
  }
}
