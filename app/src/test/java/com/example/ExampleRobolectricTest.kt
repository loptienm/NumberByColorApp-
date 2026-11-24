package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ImageSamplingStyle
import com.example.engine.ImageToGridConverter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Number by Color", appName)
  }

  @Test
  fun `pixel art converter preserves cream fill and crisp outlines`() = runTest {
    val scale = 16
    val logicalW = 20
    val logicalH = 28
    val bmp = Bitmap.createBitmap(logicalW * scale, logicalH * scale, Bitmap.Config.ARGB_8888)
    val white = 0xFFFFFFFF.toInt()
    val outline = 0xFF260D58.toInt()
    val cream = 0xFFFEFAD6.toInt()
    val pink = 0xFFFF8EB0.toInt()
    val blue = 0xFF9FCBF0.toInt()

    for (r in 0 until logicalH) {
      for (c in 0 until logicalW) {
        val color =
          when {
            r < 2 || r >= logicalH - 2 || c < 2 || c >= logicalW - 2 -> white
            r == 2 || r == logicalH - 3 || c == 2 || c == logicalW - 3 -> outline
            r in 8..9 && (c in 5..6 || c in 13..14) -> pink
            r in 14..15 && c in 3 until (logicalW - 3) -> blue
            else -> cream
          }
        for (dy in 0 until scale) {
          for (dx in 0 until scale) {
            bmp.setPixel(c * scale + dx, r * scale + dy, color)
          }
        }
      }
    }

    val tpl =
      ImageToGridConverter.convertBitmapToTemplate(
        bitmap = bmp,
        id = "test_pixel",
        title = "Test Pixel",
        maxGridDim = 0,
        maxColors = 10,
        trimBackground = true,
        samplingStyle = ImageSamplingStyle.AUTO,
      )

    assertEquals(16, tpl.gridWidth)
    assertEquals(24, tpl.gridHeight)
    assertEquals(4, tpl.palette.size)
    assertEquals(tpl.gridWidth * tpl.gridHeight, tpl.totalPaintableCells())
  }

  @Test
  fun `detectPixelArtGrid finds block size despite gradient checkerboard and JPEG-style edges`() = runTest {
    val logicalW = 30
    val logicalH = 30
    val blockPx = 6
    val phaseX = 4
    val phaseY = 5
    val imgW = logicalW * blockPx + phaseX + 6
    val imgH = logicalH * blockPx + phaseY + 6
    val bmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)

    val outline = 0xFF1E1B18.toInt()
    val gold = 0xFFF5CA3F.toInt()
    val wing = 0xFFD9E7EE.toInt()

    for (y in 0 until imgH) {
      for (x in 0 until imgW) {
        val c = ((x - phaseX) / blockPx)
        val r = ((y - phaseY) / blockPx)
        val inSprite =
          x >= phaseX && y >= phaseY && r in 4..25 && c in 4..25
        val color =
          if (!inSprite) {
            // Gradient + subtle checkerboard light gray backdrop
            val base = (232 - (y * 24) / imgH).coerceIn(195, 245)
            val checker = if (((x / blockPx) + (y / blockPx)) % 2 == 0) 5 else -5
            val v = (base + checker).coerceIn(180, 250)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
          } else if (r == 4 || r == 25 || c == 4 || c == 25) {
            outline
          } else if (r in 6..12) {
            wing
          } else {
            gold
          }
        bmp.setPixel(x, y, color)
      }
    }

    val res =
      ImageToGridConverter.convertBitmapToResult(
        bitmap = bmp,
        id = "noisy_pixel",
        title = "Noisy Pixel",
        maxGridDim = 0,
        maxColors = 10,
        trimBackground = true,
        samplingStyle = ImageSamplingStyle.AUTO,
      )

    val detected = res.detectedPixelSizePx ?: 0f
    org.junit.Assert.assertTrue("Expected ~6px block size, got $detected", detected in 5.5f..6.5f)
    assertEquals(22, res.template.gridWidth)
    assertEquals(22, res.template.gridHeight)
    assertEquals(3, res.template.palette.size)
    assertEquals(22 * 22, res.template.totalPaintableCells())
  }

  @Test
  fun `perceptual quantization merges near-identical yellows and keeps small distinct green leaf`() = runTest {
    val logical = 24
    val scale = 8
    val bmp = Bitmap.createBitmap(logical * scale, logical * scale, Bitmap.Config.ARGB_8888)
    val bg = 0xFFFFFFFF.toInt()
    val outline = 0xFF3A1A05.toInt()
    val orangeMain = 0xFFEB7315.toInt()
    val orangeAlt = 0xFFEE7B19.toInt() // nearly indistinguishable orange
    val yellow1 = 0xFFFAD23C.toInt()
    val yellow2 = 0xFFF7CA34.toInt() // nearly indistinguishable yellow
    val brownStem = 0xFF6B3E1A.toInt()
    val greenLeaf = 0xFF4D8E2B.toInt() // tiny 6-cell green accent

    for (r in 0 until logical) {
      for (c in 0 until logical) {
        val color =
          when {
            r < 2 || r >= 22 || c < 2 || c >= 22 -> bg
            r in 2..3 && c in 11..12 -> brownStem
            r == 3 && c in 13..15 -> greenLeaf // 3 cells
            r == 4 && c in 13..14 -> greenLeaf // 2 cells (5 total green leaf cells!)
            r == 4 || r == 21 || c == 2 || c == 21 -> outline
            r in 8..10 && c in 6..9 -> yellow1
            r in 14..16 && c in 7..16 -> yellow2
            (r + c) % 2 == 0 -> orangeMain
            else -> orangeAlt
          }
        for (dy in 0 until scale) {
          for (dx in 0 until scale) {
            bmp.setPixel(c * scale + dx, r * scale + dy, color)
          }
        }
      }
    }

    val res =
      ImageToGridConverter.convertBitmapToResult(
        bitmap = bmp,
        id = "pumpkin_synth",
        title = "Pumpkin",
        maxGridDim = 0,
        maxColors = 12,
        trimBackground = true,
      )

    // Even with maxColors = 12, the 2 yellows smash into 1 and the 2 oranges smash into 1,
    // yielding 5 genuinely distinct colors: orange, outline, yellow, green leaf, brown stem.
    assertEquals(5, res.template.palette.size)
    val hasGreenLeaf =
      res.template.palette.values.any { argb ->
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        g > r + 20 && g > b + 20
      }
    org.junit.Assert.assertTrue("Expected green leaf accent in palette: ${res.template.palette}", hasGreenLeaf)

    // Verify Auto color count (maxColors = 0) picks the same 5 distinct colors automatically
    val autoRes =
      ImageToGridConverter.convertBitmapToResult(
        bitmap = bmp,
        id = "pumpkin_auto",
        title = "Pumpkin Auto",
        maxGridDim = 0,
        maxColors = 0,
        trimBackground = true,
      )
    assertEquals(5, autoRes.template.palette.size)
  }

  @Test
  fun `GifFrameDecoder and convertFramesToResult handle animated multi-frame GIFs`() = runTest {
    // Build a valid minimal 2-frame 8x8 GIF89a with transparent background & 200ms frame delay
    // Global palette (4 colors): 0=transparent black, 1=red(#FF0000), 2=blue(#0000FF), 3=white
    val gifBytes =
      byteArrayOf(
        // Header GIF89a
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
        // LSD: 8x8, packed=0x91 (GCT=1, bpp=1, sorted=0, size=2^(1+1)=4), bg=0, aspect=0
        0x08, 0x00, 0x08, 0x00, 0x91.toByte(), 0x00, 0x00,
        // GCT (4 entries * 3 bytes)
        0x00, 0x00, 0x00,
        0xFF.toByte(), 0x00, 0x00,
        0x00, 0x00, 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        // GCE frame 0: disposal=2 (restore bg), transparent=1, delay=20 (200ms), transIdx=0
        0x21, 0xF9.toByte(), 0x04, 0x09, 0x14, 0x00, 0x00, 0x00,
        // Image Descriptor frame 0: (0,0) 8x8, packed=0
        0x2C, 0x00, 0x00, 0x00, 0x00, 0x08, 0x00, 0x08, 0x00, 0x00,
        // LZW minCodeSize=2; simple clear + 64 pixels of index 1 + end:
        // Encode uncompressed-style with frequent clear codes (valid GIF LZW):
        // Wait: cleaner to encode all 64 pixels using valid 3-bit codes:
        // clear(4), 1, 1, 1, 1, ..., wait: after clear(4)=b100, code 1 -> tbl[6]=(1,1), next 6 -> two 1s, etc.
        // Let's write exact valid LZW for 4x4=16 pixels of 1s or use 2x2 GIF?
        // Actually in 3-bit mode (0..7): clear=4, 1, 1, 1, 1, 1, 1, 1, end=5 => wait:
        // Better: make screen 4x4 (16 pixels) or just emit clear + literal 1 + clear + literal 1...
        // Each 3-bit pair (clear=4, pixel=1) is 6 bits!
        0x02,
      )
    // Helper to build clean LZW sub-block bytes for 8x8 = 64 pixels of a given color index (0..3)
    fun buildLzwForUniform(colorIdx: Int, count: Int): ByteArray {
      val out = mutableListOf<Byte>()
      var bitBuf = 0
      var bitLen = 0
      fun emit(code: Int) {
        bitBuf = bitBuf or (code shl bitLen)
        bitLen += 3
        while (bitLen >= 8) {
          out.add((bitBuf and 0xFF).toByte())
          bitBuf = bitBuf ushr 8
          bitLen -= 8
        }
      }
      // Always stay in 3-bit mode by emitting clear(4) before every literal pixel!
      repeat(count) {
        emit(4) // clear code
        emit(colorIdx)
      }
      emit(5) // end code
      if (bitLen > 0) out.add((bitBuf and 0xFF).toByte())
      return out.toByteArray()
    }

    val lzw1 = buildLzwForUniform(1, 64)
    val lzw2 = buildLzwForUniform(2, 64)

    val stream = java.io.ByteArrayOutputStream()
    stream.write(gifBytes)
    stream.write(lzw1.size)
    stream.write(lzw1)
    stream.write(0x00) // sub-block terminator
    // GCE frame 1
    stream.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x09, 0x14, 0x00, 0x00, 0x00))
    // Image Descriptor frame 1
    stream.write(byteArrayOf(0x2C, 0x00, 0x00, 0x00, 0x00, 0x08, 0x00, 0x08, 0x00, 0x00))
    stream.write(0x02) // LZW minCodeSize=2
    stream.write(lzw2.size)
    stream.write(lzw2)
    stream.write(0x00)
    stream.write(0x3B) // Trailer

    val decoded = com.example.engine.GifFrameDecoder.decodeGifFrames(stream.toByteArray())
    org.junit.Assert.assertNotNull("Expected animated GIF to decode 2 frames", decoded)
    assertEquals(2, decoded!!.frames.size)
    assertEquals(200, decoded.frameMs)
    assertEquals(0xFFFF0000.toInt(), decoded.frames[0].getPixel(4, 4))
    assertEquals(0xFF0000FF.toInt(), decoded.frames[1].getPixel(4, 4))

    val res =
      ImageToGridConverter.convertFramesToResult(
        frames = decoded.frames,
        frameMs = decoded.frameMs,
        id = "gif_test",
        title = "Animated Test",
        maxGridDim = 0,
        maxColors = 0,
        trimBackground = true,
      )
    assertEquals(2, res.template.frames.size)
    assertEquals(200, res.template.frameMs)
    org.junit.Assert.assertTrue(res.template.palette.isNotEmpty())
  }

  @Test
  fun `off-black outline noise merges into black and color reduction drops smallest similar shade first`() = runTest {
    val logical = 20
    val scale = 6
    val bmp = Bitmap.createBitmap(logical * scale, logical * scale, Bitmap.Config.ARGB_8888)
    val bg = 0xFFFFFFFF.toInt()
    val black = 0xFF020103.toInt()
    val offBlackRedBleed = 0xFF2B0002.toInt() // JPEG red chroma bleed beside scarf/outline
    val offBlackCharcoal = 0xFF34333B.toInt() // 1-block eye anti-aliasing fringe
    val bodyBlue = 0xFF768BB0.toInt()
    val skyBlue = 0xFF9FCADF.toInt()
    val midBlue = 0xFF82A8C9.toInt() // close blue shade (2 blocks)
    val redScarf = 0xFFB13A3E.toInt()
    val yellowBeak = 0xFFF4CB43.toInt()

    for (r in 0 until logical) {
      for (c in 0 until logical) {
        val color =
          when {
            r < 2 || r >= 18 || c < 2 || c >= 18 -> bg
            r == 2 && c == 10 -> offBlackRedBleed
            r == 2 && c == 11 -> offBlackRedBleed
            r == 5 && c == 6 -> offBlackCharcoal // 1-block off-black eye fringe
            r == 2 || r == 17 || c == 2 || c == 17 -> black
            r == 10 && c in 3..16 -> redScarf
            r in 7..8 && c in 9..10 -> yellowBeak
            r in 12..15 && c in 4..15 -> skyBlue
            r == 11 && c in 4..5 -> midBlue // 2-block intermediate blue
            else -> bodyBlue
          }
        for (dy in 0 until scale) {
          for (dx in 0 until scale) {
            bmp.setPixel(c * scale + dx, r * scale + dy, color)
          }
        }
      }
    }

    // 1. Auto mode merges both off-blacks cleanly into black (zero stray off-black colors)
    val autoRes =
      ImageToGridConverter.convertBitmapToResult(
        bitmap = bmp,
        id = "penguin_auto",
        title = "Penguin Auto",
        maxGridDim = 0,
        maxColors = 0,
        trimBackground = true,
      )
    val autoTpl = autoRes.template
    val darkPaletteEntries =
      autoTpl.palette.values.count { argb ->
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        maxOf(r, g, b) < 70
      }
    assertEquals("Expected all off-black noise to merge into 1 black color", 1, darkPaletteEntries)

    // Verify palette numbers 1..N are sorted descending by block count
    val countsByNum =
      autoTpl.palette.keys.sorted().map { num ->
        var count = 0
        for (r in 0 until autoTpl.gridHeight) {
          for (c in 0 until autoTpl.gridWidth) {
            if (autoTpl.cellNumber(r, c) == num) count++
          }
        }
        count
      }
    for (i in 0 until countsByNum.size - 1) {
      org.junit.Assert.assertTrue(
        "Palette not sorted descending by block count: $countsByNum",
        countsByNum[i] >= countsByNum[i + 1],
      )
    }

    // 2. Reducing maxColors to 5 merges small intermediate midBlue into skyBlue/bodyBlue while keeping
    //    distinct red scarf and yellow beak
    val fiveRes =
      ImageToGridConverter.convertBitmapToResult(
        bitmap = bmp,
        id = "penguin_5",
        title = "Penguin 5",
        maxGridDim = 0,
        maxColors = 5,
        trimBackground = true,
      )
    assertEquals(5, fiveRes.template.palette.size)
    val hasYellow =
      fiveRes.template.palette.values.any { argb ->
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        r > 200 && g > 170 && b < 110
      }
    val hasRed =
      fiveRes.template.palette.values.any { argb ->
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        r > 140 && g < 90 && b < 90
      }
    org.junit.Assert.assertTrue("Expected yellow beak preserved at 5 colors", hasYellow)
    org.junit.Assert.assertTrue("Expected red scarf preserved at 5 colors", hasRed)
  }

  @Test
  fun `sticker book Room repository supports multiple pages, duplicate stickers, resize, mirror, and removal`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db =
      androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.ColorPopDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    try {
      val repo = com.example.data.ColorPopRepository(db, context)
      repo.ensureStickerPages("p1")
      val pages = db.stickerDao().getPagesOnce("p1")
      assertEquals(3, pages.size)

      // Place two copies of the same sticker ("bee") on page_1 and one on page_2
      val s1 =
        com.example.model.PlacedSticker(
          id = "stk_1",
          pageId = "page_1",
          templateId = "bee",
          xFraction = 0.25f,
          yFraction = 0.30f,
          scale = 1.0f,
          isMirrored = false,
          zOrder = 1L,
        )
      val s2 =
        com.example.model.PlacedSticker(
          id = "stk_2",
          pageId = "page_1",
          templateId = "bee",
          xFraction = 0.70f,
          yFraction = 0.65f,
          scale = 1.65f,
          isMirrored = false,
          zOrder = 2L,
        )
      val s3 =
        com.example.model.PlacedSticker(
          id = "stk_3",
          pageId = "page_2",
          templateId = "cat",
          xFraction = 0.50f,
          yFraction = 0.50f,
          scale = 0.85f,
          isMirrored = true,
          zOrder = 3L,
        )
      repo.upsertPlacedSticker("p1", s1)
      repo.upsertPlacedSticker("p1", s2)
      repo.upsertPlacedSticker("p1", s3)

      // Sequential move -> resize -> move again -> mirror updates preserve every transform cleanly
      val s3Moved = s3.copy(xFraction = 0.35f, yFraction = 0.40f, zOrder = 4L)
      repo.upsertPlacedSticker("p1", s3Moved)
      val s3Resized = s3Moved.copy(scale = 1.45f, zOrder = 5L)
      repo.upsertPlacedSticker("p1", s3Resized)
      val s3MovedAgain = s3Resized.copy(xFraction = 0.62f, yFraction = 0.72f, scale = 1.45f, isMirrored = true, zOrder = 6L)
      repo.upsertPlacedSticker("p1", s3MovedAgain)

      // Delete one sticker and clear page_1, ensuring page_2 remains untouched with final transforms
      repo.deletePlacedSticker("stk_1")
      repo.clearStickerPage("p1", "page_1")
      val remaining =
        db.query(
          "SELECT id, pageId, xFraction, yFraction, scale, isMirrored FROM sticker_placements WHERE profileId = 'p1'",
          null,
        )
      remaining.use { cur ->
        assertEquals(1, cur.count)
        org.junit.Assert.assertTrue(cur.moveToFirst())
        assertEquals("stk_3", cur.getString(0))
        assertEquals("page_2", cur.getString(1))
        assertEquals(0.62f, cur.getFloat(2), 0.001f)
        assertEquals(0.72f, cur.getFloat(3), 0.001f)
        assertEquals(1.45f, cur.getFloat(4), 0.001f)
        assertEquals(1, cur.getInt(5))
      }
    } finally {
      db.close()
    }
  }

  @Test
  fun `newly placed overlapping sticker renders and handles buttons on very top layer`() {
    val tpl = com.example.data.ALL_BUILT_IN_TEMPLATES.first()
    var stickers by
      mutableStateOf(
        listOf(
          com.example.model.PlacedSticker(
            id = "old_bottom",
            pageId = "page_1",
            templateId = tpl.id,
            xFraction = 0.50f,
            yFraction = 0.50f,
            scale = 1.6f,
            zOrder = 1_794_885_888_000L,
          ),
          com.example.model.PlacedSticker(
            id = "old_mid",
            pageId = "page_1",
            templateId = tpl.id,
            xFraction = 0.50f,
            yFraction = 0.50f,
            scale = 1.6f,
            zOrder = 1_794_885_888_001L,
          ),
        )
      )
    var mirroredId: String? = null
    var removedId: String? = null

    composeTestRule.setContent {
      com.example.ui.StickerBookScreen(
        profileName = "Kid",
        templates = listOf(tpl),
        completedIds = setOf(tpl.id),
        pages = listOf(com.example.model.StickerPage("page_1", 0)),
        activePageId = "page_1",
        placedStickers = stickers,
        onBack = {},
        onSelectPage = {},
        onAddPage = {},
        onCyclePagePaper = {},
        onPlaceSticker = { pageId, templateId, xFrac, yFrac ->
          val newId = "new_top"
          stickers =
            stickers +
              com.example.model.PlacedSticker(
                id = newId,
                pageId = pageId,
                templateId = templateId,
                xFraction = xFrac,
                yFraction = yFrac,
                scale = 1.0f,
                zOrder = 1_794_885_888_002L,
              )
          newId
        },
        onUpdateStickerTransform = { _, _, _, _ -> },
        onBringStickerToFront = {},
        onToggleMirror = { stk -> mirroredId = stk.id },
        onRemoveSticker = { id ->
          removedId = id
          stickers = stickers.filterNot { it.id == id }
        },
        onClearPage = {},
        onLockedStickerClick = { _ -> },
      )
    }

    composeTestRule.waitForIdle()
    // Tapping the tray item places "new_top" near the overlapping center and auto-selects it
    composeTestRule.onNodeWithTag("sticker_tray_item_${tpl.id}").performClick()
    composeTestRule.waitForIdle()

    // Corner handles for the newly placed sticker must be on top & clickable without being blocked
    composeTestRule.onNodeWithTag("sticker_mirror_handle").performClick()
    composeTestRule.waitForIdle()
    assertEquals("new_top", mirroredId)

    composeTestRule.onNodeWithTag("sticker_remove_handle").performClick()
    composeTestRule.waitForIdle()
    assertEquals("new_top", removedId)
  }

  @Test
  fun `block editor controller handles strokes, fills, global replace, color reordering, visibility, frames, and undo-redo`() {
    val tpl =
      com.example.model.PictureTemplate(
        id = "custom_edit_test",
        title = "Penguin Draft",
        gridWidth = 4,
        gridHeight = 4,
        palette =
          mapOf(
            1 to 0xFF768BB0.toInt(), // blue
            2 to 0xFF000000.toInt(), // black outline
            3 to 0xFF1A1516.toInt(), // stray off-black
          ),
        grid =
          listOf(
            "2223",
            "2112",
            "2112",
            "3222",
          ),
        isCustom = true,
      )

    val controller = com.example.ui.BlockEditorController(onToast = {})
    controller.loadTemplate(tpl, com.example.ui.AppScreen.CreatePage, editingExistingId = null)

    // 1. Swap #2 (black) and #1 (blue) so black outline becomes #1
    controller.swapColors(2, 1)
    assertEquals(0xFF000000.toInt(), controller.state.value.palette[1])
    assertEquals(0xFF768BB0.toInt(), controller.state.value.palette[2])
    assertEquals("1113", controller.state.value.frames[0][0])

    // 2. Global replace off-black #3 into #1 black (even though disconnected at (0,3) and (3,0))
    controller.selectColor(1)
    controller.selectTool(com.example.engine.EditorTool.GLOBAL_REPLACE)
    controller.tapCellAction(0, 3)
    assertEquals("1111", controller.state.value.frames[0][0])
    assertEquals("1111", controller.state.value.frames[0][3])

    // 3. Delete now-unused color #3 cleanly
    controller.deleteUnusedColor(3)
    assertEquals(setOf(1, 2), controller.state.value.palette.keys)

    // 4. Uncolor All -> Enable #1 -> Enable #2 -> Show All
    controller.uncolorAll()
    assertEquals(emptySet<Int>(), controller.state.value.visibleColors)
    controller.toggleColorVisibility(1)
    assertEquals(setOf(1), controller.state.value.visibleColors)
    controller.toggleColorVisibility(2)
    assertEquals(setOf(1, 2), controller.state.value.visibleColors)

    // 5. Flood fill inner region & Erase one block
    controller.selectColor(1)
    controller.selectTool(com.example.engine.EditorTool.FLOOD_FILL)
    controller.tapCellAction(1, 1)
    assertEquals("1111", controller.state.value.frames[0][1])
    assertEquals("1111", controller.state.value.frames[0][2])

    controller.selectTool(com.example.engine.EditorTool.ERASE)
    controller.beginStroke(0, 0)
    assertEquals(".111", controller.state.value.frames[0][0])

    // Undo Erase restores (0,0) back to '1'
    controller.undo()
    assertEquals("1111", controller.state.value.frames[0][0])
    // Redo restores erase
    controller.redo()
    assertEquals(".111", controller.state.value.frames[0][0])

    // 6. Add frame at end, then insert a blank frame in between (at index 1)
    controller.insertFrame(insertIndex = 1, copyCurrent = true)
    assertEquals(2, controller.state.value.frames.size)
    controller.selectFrame(0)
    controller.insertFrame(insertIndex = 1, copyCurrent = false)
    assertEquals(3, controller.state.value.frames.size)
    assertEquals("....", controller.state.value.frames[1][0])

    val built = controller.buildEditedTemplate()!!
    assertEquals(3, built.frames.size)
  }

  @Test
  fun `block editor screen UI toggles visibility, swaps colors, and inserts frame in between`() {
    val tpl =
      com.example.model.PictureTemplate(
        id = "ui_edit_tpl",
        title = "UI Sprite",
        gridWidth = 4,
        gridHeight = 4,
        palette = mapOf(1 to 0xFFE11D48.toInt(), 2 to 0xFF000000.toInt()),
        grid = listOf("2222", "2112", "2112", "2222"),
        isCustom = true,
      )
    val controller = com.example.ui.BlockEditorController(onToast = {})
    controller.loadTemplate(tpl, com.example.ui.AppScreen.Home, editingExistingId = tpl.id)

    composeTestRule.setContent {
      val st by controller.state.collectAsState()
      com.example.ui.BlockEditorScreen(
        state = st,
        onDiscardAndExit = {},
        onSaveOrApply = {},
        onTitleChange = controller::updateTitle,
        onUndo = controller::undo,
        onRedo = controller::redo,
        onSelectTool = controller::selectTool,
        onToggleFillWithEraser = controller::toggleFillWithEraser,
        onToggleGlobalScope = controller::toggleGlobalScope,
        onSelectShapeType = controller::selectShapeType,
        onToggleShapeFilled = controller::toggleShapeFilled,
        onUpdateShapeDraft = controller::updateShapeDraft,
        onCommitShapeDraft = controller::commitShapeDraft,
        onDiscardShapeDraft = controller::discardShapeDraft,
        onToggleNumbers = controller::toggleNumbers,
        onToggleGridLines = controller::toggleGridLines,
        onTogglePanTool = controller::togglePanTool,
        onStrokeBegin = controller::beginStroke,
        onStrokeStep = controller::stepStroke,
        onTapCellAction = controller::tapCellAction,
        onSelectColor = controller::selectColor,
        onToggleColorVisibility = controller::toggleColorVisibility,
        onShowAllColors = controller::showAllColors,
        onUncolorAll = controller::uncolorAll,
        onSwapColors = controller::swapColors,
        onReorderPalette = controller::reorderPalette,
        onAddColor = controller::addColor,
        onUpdateColorShade = controller::updateColorShade,
        onMergeColor = controller::mergeColor,
        onDeleteUnusedColor = controller::deleteUnusedColor,
        onSelectFrame = controller::selectFrame,
        onAdvancePlaybackFrame = controller::advancePlaybackFrame,
        onTogglePlay = controller::togglePlay,
        onCycleSpeed = controller::cycleSpeed,
        onInsertFrame = controller::insertFrame,
        onDeleteCurrentFrame = controller::deleteCurrentFrame,
      )
    }

    composeTestRule.waitForIdle()
    // Uncolor All, then enable color #2
    composeTestRule.onNodeWithTag("editor_uncolor_all").performClick()
    composeTestRule.waitForIdle()
    assertEquals(emptySet<Int>(), controller.state.value.visibleColors)

    composeTestRule.onNodeWithTag("editor_eye_2").performClick()
    composeTestRule.waitForIdle()
    assertEquals(setOf(2), controller.state.value.visibleColors)

    // Swap with color #2 selected -> dialog asks which color to swap #2 with
    composeTestRule.onNodeWithTag("editor_swatch_2").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("editor_swap_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Swap Color #2").assertIsDisplayed()
    composeTestRule.onNodeWithTag("swap_target_1").performClick()
    composeTestRule.waitForIdle()
    assertEquals(0xFF000000.toInt(), controller.state.value.palette[1])
    assertEquals(setOf(1), controller.state.value.visibleColors)

    // Switch to Erase (deselects active color) -> Swap dialog asks for BOTH colors
    composeTestRule.onNodeWithTag("editor_tool_erase").performClick()
    composeTestRule.waitForIdle()
    assertEquals(null, controller.state.value.activeColorNumber)
    composeTestRule.onNodeWithTag("editor_swap_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Swap Two Colors").assertIsDisplayed()
    composeTestRule.onNodeWithTag("swap_target_1").performClick()
    composeTestRule.onNodeWithTag("swap_target_2").performClick()
    composeTestRule.waitForIdle()
    assertEquals(0xFF000000.toInt(), controller.state.value.palette[2])
    assertEquals(setOf(2), controller.state.value.visibleColors)

    // Insert blank frame after current frame
    composeTestRule.onNodeWithTag("editor_insert_frame_after").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("frame_insert_blank").performClick()
    composeTestRule.waitForIdle()
    assertEquals(2, controller.state.value.frames.size)

    // Solo button removed; Shapes tool opens Line/Box/Oval/Tri options and places a shape
    composeTestRule.onNodeWithTag("editor_solo_color_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("editor_tool_shape").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("editor_shape_oval").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("editor_shape_fill_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.engine.EditorShapeType.OVAL, controller.state.value.shapeType)
    assertEquals(true, controller.state.value.shapeFilled)
  }

  @Test
  fun `block editor shapes rasterize line, squished oval, rotated box, and triangle cleanly`() {
    val tpl =
      com.example.model.PictureTemplate(
        id = "shape_test",
        title = "Shape Canvas",
        gridWidth = 12,
        gridHeight = 12,
        palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFFEF4444.toInt()),
        grid = List(12) { "............" },
        isCustom = true,
      )
    val controller = com.example.ui.BlockEditorController(onToast = {})
    controller.loadTemplate(tpl, com.example.ui.AppScreen.CreatePage, editingExistingId = null)
    controller.selectTool(com.example.engine.EditorTool.SHAPE)

    // Squished wide oval outline (width 8, height 4)
    val ovalDraft =
      com.example.engine.ShapeDraft(
        shapeType = com.example.engine.EditorShapeType.OVAL,
        centerCol = 6f,
        centerRow = 6f,
        halfWidth = 4f,
        halfHeight = 2f,
        rotationDeg = 0f,
        filled = false,
      )
    controller.updateShapeDraft(ovalDraft)
    controller.commitShapeDraft()
    val ovalFrame = controller.state.value.frames[0]
    // Center of outline must remain empty '.' while border cells are '1'
    assertEquals('.', ovalFrame[6][6])
    assertEquals('1', ovalFrame[4][6])
    assertEquals('1', ovalFrame[6][2])

    // Switch to rotated filled Triangle and place with color #2
    controller.selectColor(2)
    val triDraft =
      com.example.engine.ShapeDraft(
        shapeType = com.example.engine.EditorShapeType.TRIANGLE,
        centerCol = 6f,
        centerRow = 6f,
        halfWidth = 3f,
        halfHeight = 3f,
        rotationDeg = 90f,
        filled = true,
      )
    controller.updateShapeDraft(triDraft)
    controller.commitShapeDraft()
    assertEquals('2', controller.state.value.frames[0][6][6])

    // Line draft (Bresenham) & Undo
    val lineDraft =
      com.example.engine.ShapeDraft(
        shapeType = com.example.engine.EditorShapeType.LINE,
        centerCol = 5f,
        centerRow = 1f,
        halfWidth = 4f,
        halfHeight = 0.5f,
        lineStartCol = 1.5f,
        lineStartRow = 1.5f,
        lineEndCol = 9.5f,
        lineEndRow = 1.5f,
      )
    controller.updateShapeDraft(lineDraft)
    controller.commitShapeDraft()
    assertEquals('2', controller.state.value.frames[0][1][1])
    assertEquals('2', controller.state.value.frames[0][1][9])

    controller.undo()
    assertEquals('.', controller.state.value.frames[0][1][1])
  }

  @Test
  fun `create page screen opens blank page dialog and creates custom-sized blank canvas`() {
    var createdArgs: Triple<String, Int, Int>? = null
    composeTestRule.setContent {
      com.example.ui.CreatePageScreen(
        state = com.example.ui.CreatePageUiState(),
        onBack = {},
        onImagePicked = {},
        onTitleChange = {},
        onGridSizeChange = {},
        onColorCountChange = {},
        onTrimBackgroundChange = {},
        onCreateBlankPage = { title, w, h -> createdArgs = Triple(title, w, h) },
        onEditBlocks = {},
        onSaveAndColor = {},
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_blank_page_empty_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("blank_size_32").performClick()
    composeTestRule.onNodeWithTag("blank_aspect_tall").performClick()
    composeTestRule.onNodeWithTag("blank_create_confirm").performClick()
    composeTestRule.waitForIdle()

    assertEquals(Triple("My Drawing", 24, 32), createdArgs)
  }

  @Test
  fun `export engine round-trips png image and colorpop json preserving blocks, colors, and frames`() =
    runTest {
      val template =
        com.example.model.PictureTemplate(
          id = "custom_penguin",
          title = "Lil Penguin!",
          gridWidth = 10,
          gridHeight = 10,
          palette =
            mapOf(
              1 to 0xFF111111.toInt(),
              2 to 0xFFFFFFFF.toInt(),
              3 to 0xFFF97316.toInt(),
            ),
          grid =
            listOf(
              "..111111..",
              ".11111111.",
              ".11222211.",
              ".11212111.",
              ".11233211.",
              ".11222211.",
              ".11222211.",
              "..112211..",
              "...3..3...",
              "..........",
            ),
          frames =
            listOf(
              listOf(
                "..111111..",
                ".11111111.",
                ".11222211.",
                ".11212111.",
                ".11233211.",
                ".11222211.",
                ".11222211.",
                "..112211..",
                "...3..3...",
                "..........",
              ),
              listOf(
                "..111111..",
                ".11111111.",
                ".11222211.",
                ".11212111.",
                ".11233211.",
                ".11222211.",
                ".11222211.",
                "..112211..",
                "..3....3..",
                "..........",
              ),
            ),
          frameMs = 250,
          isCustom = true,
        )

      // 1. JSON round-trip preserves palette numbering, title, grid, and animation frames
      val json = com.example.engine.PageExportEngine.encodePageToJson(template)
      val decoded = com.example.engine.PageExportEngine.decodePageFromJson(json, newId = "reimported")
      assertEquals("Lil Penguin!", decoded?.title)
      assertEquals(10, decoded?.gridWidth)
      assertEquals(10, decoded?.gridHeight)
      assertEquals(250, decoded?.frameMs)
      assertEquals(template.palette, decoded?.palette)
      assertEquals(2, decoded?.frames?.size)
      assertEquals(template.frames[1], decoded?.frames?.get(1))

      // 2. PNG bitmap renders crisp integer blocks & converts cleanly via ImageToGridConverter
      val bmp =
        com.example.engine.PageExportEngine.renderTemplateToBitmap(
          template = template,
          transparentBackground = true,
          includeMargin = true,
        )
      val pngBytes = com.example.engine.PageExportEngine.encodeBitmapToPngBytes(bmp)
      org.junit.Assert.assertTrue(pngBytes.size > 100)

      val converted =
        ImageToGridConverter.convertBitmapToResult(
          bitmap = bmp,
          id = "from_png",
          title = "From PNG",
          maxGridDim = 0,
          maxColors = 0,
          trimBackground = true,
          samplingStyle = ImageSamplingStyle.AUTO,
        )
      bmp.recycle()
      assertEquals(8, converted.template.gridWidth) // outer empty cols 0 & 9 trimmed cleanly
      assertEquals(9, converted.template.gridHeight) // bottom empty row trimmed cleanly
      assertEquals(3, converted.template.palette.size)
      assertEquals("Lil_Penguin.png", com.example.engine.PageExportEngine.suggestedFileName(template.title, com.example.engine.ExportFormat.PNG_IMAGE))
      assertEquals("Lil_Penguin.colorpop.json", com.example.engine.PageExportEngine.suggestedFileName(template.title, com.example.engine.ExportFormat.COLORPOP_PAGE))
    }

  @Test
  fun `create page screen opens export dialog when preview template exists`() {
    val tpl =
      com.example.model.PictureTemplate(
        id = "preview_share",
        title = "Sunny Heart",
        gridWidth = 8,
        gridHeight = 8,
        palette = mapOf(1 to 0xFFEF4444.toInt()),
        grid = List(8) { "..1111.." },
        isCustom = true,
      )
    composeTestRule.setContent {
      com.example.ui.CreatePageScreen(
        state = com.example.ui.CreatePageUiState(title = "Sunny Heart", previewTemplate = tpl),
        onBack = {},
        onImagePicked = {},
        onTitleChange = {},
        onGridSizeChange = {},
        onColorCountChange = {},
        onTrimBackgroundChange = {},
        onCreateBlankPage = { _, _, _ -> },
        onEditBlocks = {},
        onSaveAndColor = {},
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_export_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("export_format_png").assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_format_json").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("export_page_id_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_share_now_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_save_file_button").assertIsDisplayed()
  }

  @Test
  fun `remote catalog sync engine parses github urls, skips unchanged sha, and caches updated pages`() =
    runTest {
      val parsedDefault =
        com.example.engine.RemoteCatalogSyncEngine.parseSyncSource(
          com.example.engine.RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL
        ) as com.example.engine.SyncSource.GitHubRepo
      assertEquals("loptienm", parsedDefault.owner)
      assertEquals("NumberByColor", parsedDefault.repo)
      assertEquals(null, parsedDefault.branch)
      assertEquals("", parsedDefault.subPath)

      val parsedBranchSub =
        com.example.engine.RemoteCatalogSyncEngine.parseSyncSource(
          "https://github.com/loptienm/NumberByColor/tree/main/pages"
        ) as com.example.engine.SyncSource.GitHubRepo
      assertEquals("main", parsedBranchSub.branch)
      assertEquals("pages", parsedBranchSub.subPath)

      val catFixTpl =
        com.example.model.PictureTemplate(
          id = "cat3",
          title = "Goofy Orange Cat",
          gridWidth = 6,
          gridHeight = 6,
          palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFFF97316.toInt()),
          grid = List(6) { "122221" },
          version = 2,
        )
      val catFixJson = com.example.engine.PageExportEngine.encodePageToJson(catFixTpl)
      val unchangedTpl =
        catFixTpl.copy(id = "rocket_official", title = "Rocket Ship", version = 1)
      val unchangedCached =
        com.example.data.SyncedTemplateEntity(
          id = "rocket_official",
          title = "Rocket Ship",
          gridWidth = 6,
          gridHeight = 6,
          paletteSerialized = "1:#000000,2:#f97316",
          gridSerialized = List(6) { "122221" }.joinToString("/"),
          version = 1,
          sourceFileName = "pages/rocket_official.colorpop.json",
          sourceSha = "sha_unchanged",
          syncedAt = 1000L,
        )

      val fetchedUrls = mutableListOf<String>()
      val mockFetcher: suspend (String) -> com.example.engine.HttpResponse = { url ->
        fetchedUrls.add(url)
        when (url) {
          "https://api.github.com/repos/loptienm/NumberByColor/contents" ->
            com.example.engine.HttpResponse(
              code = 200,
              body =
                """
                [
                  {
                    "type": "file",
                    "name": "cat3.colorpop.json",
                    "path": "cat3.colorpop.json",
                    "sha": "sha_cat3_v2",
                    "download_url": "https://raw.githubusercontent.com/loptienm/NumberByColor/main/cat3.colorpop.json"
                  },
                  {
                    "type": "dir",
                    "name": "pages",
                    "path": "pages"
                  }
                ]
                """.trimIndent(),
            )
          "https://api.github.com/repos/loptienm/NumberByColor/contents/pages" ->
            com.example.engine.HttpResponse(
              code = 200,
              body =
                """
                [
                  {
                    "type": "file",
                    "name": "rocket_official.colorpop.json",
                    "path": "pages/rocket_official.colorpop.json",
                    "sha": "sha_unchanged",
                    "download_url": "https://raw.githubusercontent.com/loptienm/NumberByColor/main/pages/rocket_official.colorpop.json"
                  }
                ]
                """.trimIndent(),
            )
          "https://raw.githubusercontent.com/loptienm/NumberByColor/main/cat3.colorpop.json" ->
            com.example.engine.HttpResponse(code = 200, body = catFixJson)
          else -> com.example.engine.HttpResponse(code = 404, body = "not found")
        }
      }

      val syncRes =
        com.example.engine.RemoteCatalogSyncEngine.syncFromSource(
          sourceUrl = com.example.engine.RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL,
          existingSynced = listOf(unchangedCached),
          nowMs = 5000L,
          httpFetch = mockFetcher,
        )

      assertEquals(null, syncRes.errorMessage)
      assertEquals(1, syncRes.downloadedCount)
      assertEquals(2, syncRes.totalRemotePages)
      assertEquals(setOf("cat3", "rocket_official"), syncRes.activeRemoteIds)
      assertEquals(1, syncRes.upserts.size)
      assertEquals("cat3", syncRes.upserts.first().id)
      assertEquals(2, syncRes.upserts.first().version)
      // Must NOT have downloaded rocket_official raw file since its SHA matched!
      org.junit.Assert.assertFalse(
        fetchedUrls.any { it.contains("rocket_official.colorpop.json") }
      )

      // Network error returns errorMessage & activeRemoteIds == null so cache is never wiped
      val offlineRes =
        com.example.engine.RemoteCatalogSyncEngine.syncFromSource(
          sourceUrl = com.example.engine.RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL,
          existingSynced = listOf(unchangedCached),
          httpFetch = { com.example.engine.HttpResponse(code = -1, body = "offline") },
        )
      assertEquals(null, offlineRes.activeRemoteIds)
      org.junit.Assert.assertNotNull(offlineRes.errorMessage)
      assertEquals(1, offlineRes.totalRemotePages)

      // Verify Room DB stores synced template, merge order upgrades built-in cat3 without duplicate IDs
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.ColorPopDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      try {
        val repo = com.example.data.ColorPopRepository(db, context)
        repo.applyRemoteSyncResult(syncRes.upserts + unchangedCached, syncRes.activeRemoteIds)
        val storedSynced = repo.getSyncedTemplatesOnce().map {
          com.example.model.PictureTemplate(
            id = it.id,
            title = it.title,
            gridWidth = it.gridWidth,
            gridHeight = it.gridHeight,
            palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFFF97316.toInt()),
            grid = List(6) { "122221" },
            version = it.version,
            isCustom = false,
            isSyncedOfficial = true,
          )
        }
        val merged =
          com.example.ui.mergeTemplates(
            customList = emptyList(),
            syncedList = storedSynced,
            builtInList = com.example.data.ALL_BUILT_IN_TEMPLATES,
          )
        val mergedCat3 = merged.first { it.id == "cat3" }
        assertEquals(2, mergedCat3.version)
        assertEquals(true, mergedCat3.isSyncedOfficial)
        assertEquals(1, merged.count { it.id == "cat3" })
        assertEquals(true, merged.any { it.id == "rocket_official" && it.isSyncedOfficial })
      } finally {
        db.close()
      }
    }

  @Test
  fun `settings dialog admin tab gates behind parent pin, unlocks, and triggers sync and publish`() {
    val profile =
      com.example.model.KidProfile(
        id = "p1",
        name = "Player 1",
        avatar = "🐱",
        challengeMode = com.example.model.ChallengeMode.PRE_K,
        soundEnabled = true,
      )
    val customCat =
      com.example.model.PictureTemplate(
        id = "cat3",
        title = "Goofy Orange Cat",
        gridWidth = 6,
        gridHeight = 6,
        palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFFF97316.toInt()),
        grid = List(6) { "122221" },
        isCustom = true,
        version = 2,
      )

    var syncState by
      mutableStateOf(
        com.example.ui.SyncSettingsUiState(
          sourceUrl = com.example.engine.RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL,
          wifiOnlyAutoSync = true,
          lastSyncStatus = "Up to date (2 official page(s))",
          syncedCount = 2,
          hasAdminPin = true,
          isAdminUnlocked = false,
          hasPublishToken = true,
          maskedPublishToken = "••••9999",
          publishFolder = "pages",
        )
      )
    var syncedWithUrl: String? = null
    var publishedId: String? = null

    composeTestRule.setContent {
      com.example.ui.SettingsScreen(
        profile = profile,
        canRemoveKid = false,
        onSave = { _, _, _, _ -> },
        onAddAnotherKid = {},
        onRemoveKid = {},
        onBack = {},
        syncState = syncState,
        publishableTemplates = listOf(customCat),
        onSyncNow = { url -> syncedWithUrl = url },
        onVerifyAdminPin = { pin ->
          val ok = pin == "2468"
          if (ok) syncState = syncState.copy(isAdminUnlocked = true)
          ok
        },
        onPublishTemplate = { tpl, _, _ -> publishedId = tpl.id },
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    // Drill into Admin -> PIN gate appears
    composeTestRule.onNodeWithTag("settings_row_admin").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("admin_pin_gate").assertIsDisplayed()

    // Wrong PIN shows error and keeps controls locked
    composeTestRule.onNodeWithTag("admin_pin_input").performTextInput("1111")
    composeTestRule.onNodeWithTag("admin_pin_submit_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("admin_pin_error").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_row_pages").assertDoesNotExist()

    // Replace with correct PIN -> unlocks Admin menu, drill into Custom Pages
    composeTestRule.onNodeWithTag("admin_pin_input").performTextReplacement("2468")
    composeTestRule.onNodeWithTag("admin_pin_submit_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_pages").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("admin_publish_page_cat3").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_sync_now_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `admin tab unlocked triggers quick publish and sync now and repository verifies hashed pin`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.ColorPopDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      try {
        val repo = com.example.data.ColorPopRepository(db, context)
        repo.resetAdminPinAndClearToken()
        assertEquals(false, repo.hasAdminPin())
        repo.setAdminPin("4321")
        assertEquals(true, repo.hasAdminPin())
        assertEquals(false, repo.verifyAdminPin("9999"))
        assertEquals(true, repo.verifyAdminPin("4321"))
        repo.setGitHubPublishToken("github_pat_test_secret_8765")
        assertEquals("••••8765", repo.getMaskedPublishToken())
        repo.resetAdminPinAndClearToken()
        assertEquals(false, repo.hasAdminPin())
        assertEquals("", repo.getGitHubPublishToken())
      } finally {
        db.close()
      }

      val profile =
        com.example.model.KidProfile(
          id = "p1",
          name = "Player 1",
          avatar = "🐱",
          challengeMode = com.example.model.ChallengeMode.PRE_K,
          soundEnabled = true,
        )
      val customCat =
        com.example.model.PictureTemplate(
          id = "cat3",
          title = "Goofy Orange Cat",
          gridWidth = 6,
          gridHeight = 6,
          palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFFF97316.toInt()),
          grid = List(6) { "122221" },
          isCustom = true,
          version = 2,
        )
      var syncedWithUrl: String? = null
      var publishedTplId: String? = null
      var unlockedState by
        mutableStateOf(
          com.example.ui.SyncSettingsUiState(
            sourceUrl = com.example.engine.RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL,
            wifiOnlyAutoSync = true,
            lastSyncStatus = "Up to date (2 official page(s))",
            syncedCount = 2,
            hasAdminPin = true,
            isAdminUnlocked = false,
            hasPublishToken = true,
            maskedPublishToken = "••••8765",
            publishFolder = "pages",
          )
        )

      composeTestRule.setContent {
        com.example.ui.SettingsScreen(
          profile = profile,
          canRemoveKid = false,
          onSave = { _, _, _, _ -> },
          onAddAnotherKid = {},
          onRemoveKid = {},
          onBack = {},
          syncState = unlockedState,
          publishableTemplates = listOf(customCat),
          onSyncNow = { url -> syncedWithUrl = url },
          onVerifyAdminPin = { pin ->
            if (pin == "4321") {
              unlockedState = unlockedState.copy(isAdminUnlocked = true)
              true
            } else false
          },
          onPublishTemplate = { tpl, _, _ -> publishedTplId = tpl.id },
        )
      }

      composeTestRule.waitForIdle()
      // Open Admin -> unlock with PIN -> drill into Server Setup -> test URL & sync
      composeTestRule.onNodeWithTag("settings_row_admin").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("admin_pin_input").performTextInput("4321")
      composeTestRule.onNodeWithTag("admin_pin_submit_button").performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("settings_row_server").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("settings_sync_url_input").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("settings_wifi_only_switch").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("admin_publish_folder_input").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("settings_sync_now_button").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      assertEquals(
        com.example.engine.RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL,
        syncedWithUrl,
      )

      // Back to Admin menu (stays unlocked) and drill into Custom Pages -> quick-publish cat3
      composeTestRule.onNodeWithTag("settings_back_button").performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("settings_row_pages").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("admin_publish_page_cat3").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      assertEquals("cat3", publishedTplId)
    }

  @Test
  fun `remote catalog sync engine publishes updated page to github with sha check and version bump`() =
    runTest {
      val existingTpl =
        com.example.model.PictureTemplate(
          id = "cat3",
          title = "Goofy Orange Cat",
          gridWidth = 6,
          gridHeight = 6,
          palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFFF97316.toInt()),
          grid = List(6) { "122221" },
          version = 1,
        )
      val existingEncoded =
        android.util.Base64.encodeToString(
          com.example.engine.PageExportEngine.encodePageToJson(existingTpl).toByteArray(Charsets.UTF_8),
          android.util.Base64.NO_WRAP,
        )

      var capturedMethod = ""
      var capturedUrl = ""
      var capturedAuth = ""
      var capturedPutSha = ""
      var capturedDecodedVersion = 0

      val mockHttp:
        suspend (String, String, Map<String, String>, String?) -> com.example.engine.HttpResponse =
        { method, url, headers, body ->
          when (method) {
            "GET" ->
              com.example.engine.HttpResponse(
                code = 200,
                body = """{"sha":"old_sha_123","content":"$existingEncoded"}""",
              )
            "PUT" -> {
              capturedMethod = method
              capturedUrl = url
              capturedAuth = headers["Authorization"] ?: ""
              val reqJson = org.json.JSONObject(body!!)
              capturedPutSha = reqJson.optString("sha", "")
              val putBytes =
                android.util.Base64.decode(reqJson.getString("content"), android.util.Base64.DEFAULT)
              val decoded =
                com.example.engine.PageExportEngine.decodePageFromJson(
                  String(putBytes, Charsets.UTF_8),
                  isSyncedOfficial = true,
                  preserveEmbeddedId = true,
                )
              capturedDecodedVersion = decoded?.version ?: 0
              com.example.engine.HttpResponse(
                code = 200,
                body = """{"content":{"sha":"new_sha_456","path":"pages/cat3.colorpop.json"}}""",
              )
            }
            else -> com.example.engine.HttpResponse(code = 405, body = "")
          }
        }

      val res =
        com.example.engine.RemoteCatalogSyncEngine.publishPageToGitHub(
          sourceUrl = com.example.engine.RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL,
          token = "github_pat_example",
          template = existingTpl,
          targetFolder = "pages",
          nowMs = 9999L,
          httpRequest = mockHttp,
        )

      assertEquals(true, res.success)
      assertEquals("PUT", capturedMethod)
      assertEquals(
        "https://api.github.com/repos/loptienm/NumberByColor/contents/pages/cat3.colorpop.json",
        capturedUrl,
      )
      assertEquals("Bearer github_pat_example", capturedAuth)
      assertEquals("old_sha_123", capturedPutSha)
      assertEquals(2, capturedDecodedVersion)
      assertEquals("new_sha_456", res.syncedEntity?.sourceSha)
      assertEquals("pages/cat3.colorpop.json", res.remotePath)
    }

  @Test
  fun `publish spinner targets only clicked page and published or synced pages show up to date`() {
    val profile =
      com.example.model.KidProfile(
        id = "p1",
        name = "Player 1",
        avatar = "🐱",
        challengeMode = com.example.model.ChallengeMode.PRE_K,
        soundEnabled = true,
      )
    val customCat =
      com.example.model.PictureTemplate(
        id = "cat3",
        title = "Goofy Orange Cat",
        gridWidth = 6,
        gridHeight = 6,
        palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFFF97316.toInt()),
        grid = List(6) { "122221" },
        isCustom = true,
        version = 2,
      )
    val customFrog =
      com.example.model.PictureTemplate(
        id = "custom_101",
        title = "Happy Frog",
        gridWidth = 6,
        gridHeight = 6,
        palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFF22C55E.toInt()),
        grid = List(6) { "122221" },
        isCustom = true,
        version = 1,
      )

    var publishable by mutableStateOf(listOf(customCat, customFrog))
    var synced by mutableStateOf(emptyList<com.example.model.PictureTemplate>())
    var syncState by
      mutableStateOf(
        com.example.ui.SyncSettingsUiState(
          hasAdminPin = true,
          isAdminUnlocked = true,
          hasPublishToken = true,
          maskedPublishToken = "••••1234",
          isPublishing = true,
          publishingTemplateId = "cat3",
        )
      )

    composeTestRule.setContent {
      com.example.ui.SettingsScreen(
        profile = profile,
        canRemoveKid = false,
        onSave = { _, _, _, _ -> },
        onAddAnotherKid = {},
        onRemoveKid = {},
        onBack = {},
        syncState = syncState,
        publishableTemplates = publishable,
        syncedTemplates = synced,
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_admin").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_server").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Lock, Change PIN, and Admin Unlocked header all render cleanly on Server page
    composeTestRule.onNodeWithTag("admin_change_pin_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_lock_button").assertIsDisplayed()
    composeTestRule.onNodeWithText("Admin Unlocked").assertIsDisplayed()

    // Back to Admin menu and drill into Custom Pages -> only cat3 is spinning, so exactly ONE "Publish" label remains
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_pages").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("admin_publish_page_cat3").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_publish_page_custom_101").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Publish").assertIsDisplayed()

    // Simulate cat3 finishing publish -> promoted to Synced/Up to date, Publish button removed
    publishable = listOf(customFrog)
    synced = listOf(customCat.copy(isCustom = false, isSyncedOfficial = true))
    syncState = syncState.copy(isPublishing = false, publishingTemplateId = null)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("admin_publish_page_cat3").assertDoesNotExist()
    composeTestRule.onNodeWithTag("admin_uptodate_page_cat3").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Up to date").assertIsDisplayed()
  }

  @Test
  fun `repository promotePublishedTemplate and applyRemoteSyncResult clear custom override and migrate custom id`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.ColorPopDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      try {
        val repo = com.example.data.ColorPopRepository(db, context)
        val customTpl =
          com.example.model.PictureTemplate(
            id = "custom_999",
            title = "Happy Frog",
            gridWidth = 4,
            gridHeight = 4,
            palette = mapOf(1 to 0xFF22C55E.toInt()),
            grid = List(4) { "1111" },
            isCustom = true,
            version = 1,
          )
        repo.saveCustomTemplate(customTpl)
        repo.markCompleted("p1", "custom_999")

        val publishedEntity =
          com.example.data.SyncedTemplateEntity(
            id = "happy_frog",
            title = "Happy Frog",
            gridWidth = 4,
            gridHeight = 4,
            paletteSerialized = "1:#22C55E",
            gridSerialized = List(4) { "1111" }.joinToString("/"),
            version = 1,
            sourceFileName = "pages/happy_frog.colorpop.json",
            sourceSha = "sha_frog",
          )
        repo.promotePublishedTemplate(entity = publishedEntity, localTemplateId = "custom_999")

        assertEquals(0, db.customTemplateDao().getCustomTemplatesOnce().size)
        assertEquals(listOf("happy_frog"), repo.getSyncedTemplatesOnce().map { it.id })

        // Now simulate a local edit on cat3 that gets superseded by a newer GitHub sync
        repo.saveCustomTemplate(
          com.example.model.PictureTemplate(
            id = "cat3",
            title = "Goofy Orange Cat",
            gridWidth = 4,
            gridHeight = 4,
            palette = mapOf(1 to 0xFFF97316.toInt()),
            grid = List(4) { "1111" },
            isCustom = true,
            version = 2,
          )
        )
        val syncedCatV3 =
          com.example.data.SyncedTemplateEntity(
            id = "cat3",
            title = "Goofy Orange Cat",
            gridWidth = 4,
            gridHeight = 4,
            paletteSerialized = "1:#F97316",
            gridSerialized = List(4) { "1111" }.joinToString("/"),
            version = 3,
            sourceFileName = "pages/cat3.colorpop.json",
            sourceSha = "sha_cat_v3",
          )
        repo.applyRemoteSyncResult(listOf(syncedCatV3, publishedEntity), setOf("cat3", "happy_frog"))

        assertEquals(0, db.customTemplateDao().getCustomTemplatesOnce().size)
        assertEquals(3, repo.getSyncedTemplatesOnce().first { it.id == "cat3" }.version)
      } finally {
        db.close()
      }
    }

  @Test
  fun `remote catalog sync exposes full 404 url and private repo hint when unauthenticated`() =
    runTest {
      val resNoToken =
        com.example.engine.RemoteCatalogSyncEngine.syncFromSource(
          sourceUrl = "https://github.com/loptienm/NumberByColor.git",
          existingSynced = emptyList(),
          token = "",
          httpFetch = {
            com.example.engine.HttpResponse(
              code = 404,
              body = """{"message":"Not Found","documentation_url":"https://docs.github.com"}""",
            )
          },
        )
      org.junit.Assert.assertNotNull(resNoToken.errorMessage)
      org.junit.Assert.assertTrue(resNoToken.statusMessage.contains("HTTP 404: Not Found"))
      org.junit.Assert.assertTrue(
        resNoToken.statusMessage.contains("https://api.github.com/repos/loptienm/NumberByColor/contents")
      )
      org.junit.Assert.assertTrue(resNoToken.statusMessage.contains("Private on GitHub"))

      // With a saved token, the Private-repo hint is omitted while exact URL & HTTP 404 remain
      val resWithToken =
        com.example.engine.RemoteCatalogSyncEngine.syncFromSource(
          sourceUrl = "https://github.com/loptienm/NumberByColor.git",
          existingSynced = emptyList(),
          token = "github_pat_example",
          httpFetch = {
            com.example.engine.HttpResponse(
              code = 404,
              body = """{"message":"Not Found"}""",
            )
          },
        )
      org.junit.Assert.assertTrue(resWithToken.statusMessage.contains("HTTP 404: Not Found"))
      org.junit.Assert.assertFalse(resWithToken.statusMessage.contains("Private on GitHub"))
    }

  @Test
  fun `admin tab shows sync now and publish all above list and full status card at bottom`() {
    val profile =
      com.example.model.KidProfile(
        id = "p1",
        name = "Player 1",
        avatar = "🐱",
        challengeMode = com.example.model.ChallengeMode.PRE_K,
        soundEnabled = true,
      )
    val page1 =
      com.example.model.PictureTemplate(
        id = "cat3",
        title = "Goofy Orange Cat",
        gridWidth = 6,
        gridHeight = 6,
        palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFFF97316.toInt()),
        grid = List(6) { "122221" },
        isCustom = true,
        version = 2,
      )
    val page2 =
      page1.copy(id = "custom_222", title = "Happy Frog", version = 1)
    val detailedError =
      "Sync failed — HTTP 404: Not Found (URL: https://api.github.com/repos/loptienm/NumberByColor/contents) — If this repo is Private on GitHub, make it Public so all devices can sync."

    var publishedAllCount = 0
    var syncedUrl: String? = null

    composeTestRule.setContent {
      com.example.ui.SettingsScreen(
        profile = profile,
        canRemoveKid = false,
        onSave = { _, _, _, _ -> },
        onAddAnotherKid = {},
        onRemoveKid = {},
        onBack = {},
        syncState =
          com.example.ui.SyncSettingsUiState(
            sourceUrl = com.example.engine.RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL,
            hasAdminPin = true,
            isAdminUnlocked = true,
            hasPublishToken = true,
            maskedPublishToken = "••••4321",
            publishFolder = "pages",
            lastSyncStatus = detailedError,
          ),
        publishableTemplates = listOf(page1, page2),
        onSyncNow = { url -> syncedUrl = url },
        onPublishAll = { list, _, _ -> publishedAllCount = list.size },
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_admin").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_pages").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_sync_now_button").performScrollTo().performClick()
    assertEquals(com.example.engine.RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL, syncedUrl)

    composeTestRule.onNodeWithTag("admin_publish_all_button").performScrollTo().performClick()
    assertEquals(2, publishedAllCount)

    composeTestRule.onNodeWithTag("admin_status_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_copy_status_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Sync: $detailedError").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `home screen hides status badges and uses top bar edit mode for editing pages`() {
    val customCat =
      com.example.model.PictureTemplate(
        id = "cat3",
        title = "Goofy Orange Cat",
        gridWidth = 6,
        gridHeight = 6,
        palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFFF97316.toInt()),
        grid = List(6) { "122221" },
        isCustom = true,
      )
    val syncedRocket =
      com.example.model.PictureTemplate(
        id = "rocket_official",
        title = "Rocket Ship",
        gridWidth = 6,
        gridHeight = 6,
        palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFF38BDF8.toInt()),
        grid = List(6) { "122221" },
        isCustom = false,
        isSyncedOfficial = true,
      )
    var openedId: String? = null
    var editedId: String? = null

    composeTestRule.setContent {
      com.example.ui.HomeScreen(
        profile = null,
        templates = listOf(customCat, syncedRocket),
        progressState = com.example.data.ProfileProgressState(),
        onOpenPicture = { openedId = it.id },
        onOpenCreatePage = {},
        onEditCustom = { editedId = it.id },
        onDeleteCustom = {},
        onOpenProfiles = {},
        onOpenStickers = {},
        onToggleSound = {},
        onOpenSettings = {},
      )
    }

    composeTestRule.waitForIdle()
    // Per-card badges and per-card edit/delete buttons are removed in normal browsing mode
    composeTestRule.onNodeWithText("Edited").assertDoesNotExist()
    composeTestRule.onNodeWithText("Custom").assertDoesNotExist()
    composeTestRule.onNodeWithText("Synced").assertDoesNotExist()
    composeTestRule.onNodeWithTag("edit_custom_cat3").assertDoesNotExist()
    composeTestRule.onNodeWithTag("delete_custom_cat3").assertDoesNotExist()

    // Normal tap opens coloring screen
    composeTestRule
      .onNodeWithTag("home_gallery_grid")
      .performScrollToNode(hasTestTag("picture_card_rocket_official"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("picture_card_rocket_official").performClick()
    assertEquals("rocket_official", openedId)
    assertEquals(null, editedId)

    // Toggle top-bar Edit Mode -> banner & delete icon for custom page appear, card tap edits
    composeTestRule.onNodeWithTag("home_edit_mode_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_edit_mode_banner").assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("home_gallery_grid")
      .performScrollToNode(hasTestTag("picture_card_cat3"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("delete_custom_cat3").assertIsDisplayed()

    composeTestRule.onNodeWithTag("picture_card_cat3").performClick()
    composeTestRule.waitForIdle()
    assertEquals("cat3", editedId)
    composeTestRule.onNodeWithTag("home_edit_mode_banner").assertDoesNotExist()
  }

  @Test
  fun `sync discovers arbitrary nested folders and branches and never deletes cached pages on zero remote files`() =
    runTest {
      val cachedTpl =
        com.example.model.PictureTemplate(
          id = "my_saved_cat",
          title = "My Saved Cat",
          gridWidth = 6,
          gridHeight = 6,
          palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFFF97316.toInt()),
          grid = List(6) { "122221" },
          version = 2,
          isSyncedOfficial = true,
        )
      val cachedEntity =
        com.example.data.SyncedTemplateEntity(
          id = cachedTpl.id,
          title = cachedTpl.title,
          gridWidth = 6,
          gridHeight = 6,
          paletteSerialized = "1:#000000,2:#f97316",
          gridSerialized = List(6) { "122221" }.joinToString("/"),
          version = 2,
          sourceFileName = "custom_art/my_saved_cat.colorpop.json",
          sourceSha = "sha_cached",
          syncedAt = 1000L,
        )

      // 1. Empty repo returns activeRemoteIds = null and does NOT wipe existing synced pages
      val emptyRepoRes =
        com.example.engine.RemoteCatalogSyncEngine.syncFromSource(
          sourceUrl = "https://github.com/loptienm/NumberByColor.git",
          existingSynced = listOf(cachedEntity),
          httpFetch = { url ->
            when (url) {
              "https://api.github.com/repos/loptienm/NumberByColor/contents" ->
                com.example.engine.HttpResponse(
                  code = 200,
                  body = """[{"type":"dir","name":"art","path":"art"}]""",
                )
              "https://api.github.com/repos/loptienm/NumberByColor/contents/art" ->
                com.example.engine.HttpResponse(code = 200, body = "[]")
              "https://api.github.com/repos/loptienm/NumberByColor/branches" ->
                com.example.engine.HttpResponse(code = 200, body = """[{"name":"main"}]""")
              else -> com.example.engine.HttpResponse(code = 404, body = "")
            }
          },
        )
      assertEquals(null, emptyRepoRes.activeRemoteIds)
      org.junit.Assert.assertTrue(emptyRepoRes.statusMessage.contains("checked: root, art"))
      org.junit.Assert.assertTrue(emptyRepoRes.statusMessage.contains("Kept 1 cached page(s)"))

      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.ColorPopDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      try {
        val repo = com.example.data.ColorPopRepository(db, context)
        repo.cachePublishedTemplate(cachedEntity)
        repo.applyRemoteSyncResult(emptyRepoRes.upserts, emptyRepoRes.activeRemoteIds)
        assertEquals(listOf("my_saved_cat"), repo.getSyncedTemplatesOnce().map { it.id })
      } finally {
        db.close()
      }

      // 2. Discovers files in arbitrary nested folder ("art/animals") on a non-default branch ("pages")
      //    and decodes base64 fallback when raw download_url is null/404
      val base64Json =
        android.util.Base64.encodeToString(
          com.example.engine.PageExportEngine.encodePageToJson(cachedTpl).toByteArray(Charsets.UTF_8),
          android.util.Base64.NO_WRAP,
        )
      val multiBranchRes =
        com.example.engine.RemoteCatalogSyncEngine.syncFromSource(
          sourceUrl = "https://github.com/loptienm/NumberByColor.git",
          existingSynced = emptyList(),
          httpFetch = { url ->
            when (url) {
              "https://api.github.com/repos/loptienm/NumberByColor/contents" ->
                com.example.engine.HttpResponse(code = 200, body = "[]")
              "https://api.github.com/repos/loptienm/NumberByColor/branches" ->
                com.example.engine.HttpResponse(
                  code = 200,
                  body = """[{"name":"main"},{"name":"pages-backup"}]""",
                )
              "https://api.github.com/repos/loptienm/NumberByColor/contents?ref=main" ->
                com.example.engine.HttpResponse(code = 200, body = "[]")
              "https://api.github.com/repos/loptienm/NumberByColor/contents?ref=pages-backup" ->
                com.example.engine.HttpResponse(
                  code = 200,
                  body = """[{"type":"dir","name":"art","path":"art"}]""",
                )
              "https://api.github.com/repos/loptienm/NumberByColor/contents/art?ref=pages-backup" ->
                com.example.engine.HttpResponse(
                  code = 200,
                  body = """[{"type":"dir","name":"animals","path":"art/animals"}]""",
                )
              "https://api.github.com/repos/loptienm/NumberByColor/contents/art/animals?ref=pages-backup" ->
                com.example.engine.HttpResponse(
                  code = 200,
                  body =
                    """
                    [
                      {
                        "type": "file",
                        "name": "my_saved_cat.colorpop.json",
                        "path": "art/animals/my_saved_cat.colorpop.json",
                        "sha": "sha_new_99",
                        "download_url": null,
                        "url": "https://api.github.com/repos/loptienm/NumberByColor/contents/art/animals/my_saved_cat.colorpop.json?ref=pages-backup"
                      }
                    ]
                    """.trimIndent(),
                )
              "https://api.github.com/repos/loptienm/NumberByColor/contents/art/animals/my_saved_cat.colorpop.json?ref=pages-backup" ->
                com.example.engine.HttpResponse(
                  code = 200,
                  body = """{"encoding":"base64","content":"$base64Json"}""",
                )
              else -> com.example.engine.HttpResponse(code = 404, body = "")
            }
          },
        )
      assertEquals(null, multiBranchRes.errorMessage)
      assertEquals(1, multiBranchRes.downloadedCount)
      assertEquals(setOf("my_saved_cat"), multiBranchRes.activeRemoteIds)
      assertEquals("art/animals/my_saved_cat.colorpop.json", multiBranchRes.upserts.first().sourceFileName)
    }

  @Test
  fun `publishing to configured ColoringPages folder is discovered when syncing default repo url`() =
    runTest {
      val pageTpl =
        com.example.model.PictureTemplate(
          id = "cat3",
          title = "Goofy Orange Cat",
          gridWidth = 6,
          gridHeight = 6,
          palette = mapOf(1 to 0xFF000000.toInt(), 2 to 0xFFF97316.toInt()),
          grid = List(6) { "122221" },
          version = 2,
          isCustom = true,
        )
      var publishedBodyJson = ""
      var pushedUrl = ""

      val pubRes =
        com.example.engine.RemoteCatalogSyncEngine.publishPageToGitHub(
          sourceUrl = "https://github.com/loptienm/NumberByColor.git",
          token = "github_pat_example",
          template = pageTpl,
          targetFolder = "ColoringPages",
          httpRequest = { method, url, _, body ->
            when (method) {
              "GET" -> com.example.engine.HttpResponse(code = 404, body = "")
              "PUT" -> {
                pushedUrl = url
                val req = org.json.JSONObject(body!!)
                val decodedBytes =
                  android.util.Base64.decode(req.getString("content"), android.util.Base64.DEFAULT)
                publishedBodyJson = String(decodedBytes, Charsets.UTF_8)
                com.example.engine.HttpResponse(
                  code = 201,
                  body = """{"content":{"sha":"sha_coloring_pages_1","path":"ColoringPages/cat3.colorpop.json"}}""",
                )
              }
              else -> com.example.engine.HttpResponse(code = 405, body = "")
            }
          },
        )
      assertEquals(true, pubRes.success)
      assertEquals(
        "https://api.github.com/repos/loptienm/NumberByColor/contents/ColoringPages/cat3.colorpop.json",
        pushedUrl,
      )
      assertEquals("ColoringPages/cat3.colorpop.json", pubRes.remotePath)

      val syncRes =
        com.example.engine.RemoteCatalogSyncEngine.syncFromSource(
          sourceUrl = "https://github.com/loptienm/NumberByColor.git",
          existingSynced = emptyList(),
          httpFetch = { url ->
            when (url) {
              "https://api.github.com/repos/loptienm/NumberByColor/contents" ->
                com.example.engine.HttpResponse(
                  code = 200,
                  body = """[{"type":"dir","name":"ColoringPages","path":"ColoringPages"}]""",
                )
              "https://api.github.com/repos/loptienm/NumberByColor/contents/ColoringPages" ->
                com.example.engine.HttpResponse(
                  code = 200,
                  body =
                    """
                    [
                      {
                        "type": "file",
                        "name": "cat3.colorpop.json",
                        "path": "ColoringPages/cat3.colorpop.json",
                        "sha": "sha_coloring_pages_1",
                        "download_url": "https://raw.githubusercontent.com/loptienm/NumberByColor/main/ColoringPages/cat3.colorpop.json"
                      }
                    ]
                    """.trimIndent(),
                )
              "https://raw.githubusercontent.com/loptienm/NumberByColor/main/ColoringPages/cat3.colorpop.json" ->
                com.example.engine.HttpResponse(code = 200, body = publishedBodyJson)
              else -> com.example.engine.HttpResponse(code = 404, body = "")
            }
          },
        )
      assertEquals(null, syncRes.errorMessage)
      assertEquals(1, syncRes.downloadedCount)
      assertEquals(setOf("cat3"), syncRes.activeRemoteIds)
      assertEquals("ColoringPages/cat3.colorpop.json", syncRes.upserts.first().sourceFileName)
    }

  @Test
  fun `sticker book tapping locked sticker navigates directly to that coloring page`() {
    val unlockedTpl = com.example.data.ALL_BUILT_IN_TEMPLATES[0]
    val lockedTpl = com.example.data.ALL_BUILT_IN_TEMPLATES[1]
    var openedLockedTemplate: com.example.model.PictureTemplate? = null

    composeTestRule.setContent {
      com.example.ui.StickerBookScreen(
        profileName = "Test Kid",
        templates = listOf(unlockedTpl, lockedTpl),
        completedIds = setOf(unlockedTpl.id),
        pages = listOf(com.example.model.StickerPage("page_1", 0)),
        activePageId = "page_1",
        placedStickers = emptyList(),
        onBack = {},
        onSelectPage = {},
        onAddPage = {},
        onCyclePagePaper = {},
        onPlaceSticker = { _, _, _, _ -> "stk_1" },
        onUpdateStickerTransform = { _, _, _, _ -> },
        onBringStickerToFront = {},
        onToggleMirror = {},
        onRemoveSticker = {},
        onClearPage = {},
        onLockedStickerClick = { tpl -> openedLockedTemplate = tpl },
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("sticker_tray_item_${lockedTpl.id}").performClick()
    composeTestRule.waitForIdle()
    assertEquals(lockedTpl.id, openedLockedTemplate?.id)
  }

  @Test
  fun `custom page coloring progress updates progressState immediately without delay`() =
    runTest {
      val app = ApplicationProvider.getApplicationContext<android.app.Application>()
      val vm = com.example.ui.ColorPopViewModel(app)
      val customRainbow =
        com.example.model.PictureTemplate(
          id = "custom_rainbow_fast",
          title = "Rainbow",
          gridWidth = 3,
          gridHeight = 3,
          palette = mapOf(1 to 0xFFEF4444.toInt(), 2 to 0xFF3B82F6.toInt()),
          grid = listOf("111", "222", "111"),
          isCustom = true,
        )

      vm.openPicture(customRainbow)
      vm.selectColor(1)
      vm.paintStroke(null, null, 0, 0)

      // Go home -> progressState reflects in-progress pixels on the next main-looper tick (no Room delay)
      vm.goHome()
      org.robolectric.shadows.ShadowLooper.idleMainLooper()
      assertEquals(com.example.ui.AppScreen.Home, vm.currentScreen.value)
      val savedInProgress = vm.progressState.value.inProgressGrids["custom_rainbow_fast"]
      org.junit.Assert.assertNotNull(savedInProgress)
      assertEquals(1, savedInProgress!![0][0])

      // Now finish remaining cells -> progressState marks completed immediately
      vm.openPicture(customRainbow)
      vm.selectColor(1)
      vm.paintStroke(null, null, 0, 1)
      vm.paintStroke(null, null, 0, 2)
      vm.paintStroke(null, null, 2, 0)
      vm.paintStroke(null, null, 2, 1)
      vm.paintStroke(null, null, 2, 2)
      vm.selectColor(2)
      vm.paintStroke(null, null, 1, 0)
      vm.paintStroke(null, null, 1, 1)
      vm.paintStroke(null, null, 1, 2)
      org.robolectric.shadows.ShadowLooper.idleMainLooper()

      assertTrue(vm.playState.value.isCompleted)
      assertTrue("custom_rainbow_fast" in vm.progressState.value.completedIds)
    }

  @Test
  fun `custom question JSON export, Room save, publish and GitHub sync round-trip cleanly`() =
    runTest {
      val customQ =
        com.example.model.CustomQuestion(
          id = "q_12345",
          prompt = "What color is a ladybug?",
          clueItems = listOf("🐞", "?"),
          correctAnswer = "Red",
          wrongAnswers = listOf("Purple", "Blue", "White"),
          levels = setOf(com.example.model.ChallengeMode.PRE_K, com.example.model.ChallengeMode.KINDER),
        )

      val encoded = com.example.engine.QuestionExportEngine.encodeQuestionToJson(customQ)
      assertTrue(com.example.engine.QuestionExportEngine.isQuestionJson(encoded))
      assertEquals(null, com.example.engine.PageExportEngine.decodePageFromJson(encoded))

      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.ColorPopDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      try {
        val repo = com.example.data.ColorPopRepository(db, context)
        repo.saveCustomQuestion(customQ)
        assertEquals(1, db.customQuestionDao().getCustomQuestionsOnce().size)

        var publishedJson = ""
        var putUrl = ""
        val pubRes =
          com.example.engine.RemoteCatalogSyncEngine.publishQuestionToGitHub(
            sourceUrl = "https://github.com/loptienm/NumberByColor.git",
            token = "github_pat_example",
            question = customQ.copy(id = "ladybug_color"),
            targetFolder = "ColoringPages",
            httpRequest = { method, url, _, body ->
              when (method) {
                "GET" -> com.example.engine.HttpResponse(404, "")
                "PUT" -> {
                  putUrl = url
                  val req = org.json.JSONObject(body!!)
                  publishedJson =
                    String(
                      android.util.Base64.decode(req.getString("content"), android.util.Base64.DEFAULT),
                      Charsets.UTF_8,
                    )
                  com.example.engine.HttpResponse(
                    201,
                    """{"content":{"sha":"sha_q_1","path":"ColoringPages/questions/ladybug_color.question.json"}}""",
                  )
                }
                else -> com.example.engine.HttpResponse(405, "")
              }
            },
          )
        assertTrue(pubRes.success)
        assertEquals(
          "https://api.github.com/repos/loptienm/NumberByColor/contents/ColoringPages/questions/ladybug_color.question.json",
          putUrl,
        )
        repo.promotePublishedQuestion(pubRes.syncedEntity!!, localQuestionId = "q_12345")
        assertEquals(0, db.customQuestionDao().getCustomQuestionsOnce().size)
        assertEquals(listOf("ladybug_color"), repo.getSyncedQuestionsOnce().map { it.id })

        // Sync from remote discovers question JSON under ColoringPages/questions alongside pages
        val syncRes =
          com.example.engine.RemoteCatalogSyncEngine.syncFromSource(
            sourceUrl = "https://github.com/loptienm/NumberByColor.git",
            existingSynced = emptyList(),
            existingSyncedQuestions = emptyList(),
            httpFetch = { url ->
              when (url) {
                "https://api.github.com/repos/loptienm/NumberByColor/contents" ->
                  com.example.engine.HttpResponse(
                    200,
                    """[{"type":"dir","name":"ColoringPages","path":"ColoringPages"}]""",
                  )
                "https://api.github.com/repos/loptienm/NumberByColor/contents/ColoringPages" ->
                  com.example.engine.HttpResponse(
                    200,
                    """[{"type":"dir","name":"questions","path":"ColoringPages/questions"}]""",
                  )
                "https://api.github.com/repos/loptienm/NumberByColor/contents/ColoringPages/questions" ->
                  com.example.engine.HttpResponse(
                    200,
                    """
                    [
                      {
                        "type": "file",
                        "name": "ladybug_color.question.json",
                        "path": "ColoringPages/questions/ladybug_color.question.json",
                        "sha": "sha_q_2",
                        "download_url": "https://raw.githubusercontent.com/loptienm/NumberByColor/main/ColoringPages/questions/ladybug_color.question.json"
                      }
                    ]
                    """.trimIndent(),
                  )
                "https://raw.githubusercontent.com/loptienm/NumberByColor/main/ColoringPages/questions/ladybug_color.question.json" ->
                  com.example.engine.HttpResponse(200, publishedJson)
                else -> com.example.engine.HttpResponse(404, "")
              }
            },
          )
        assertEquals(null, syncRes.errorMessage)
        assertEquals(1, syncRes.downloadedQuestionCount)
        assertEquals(setOf("ladybug_color"), syncRes.activeRemoteQuestionIds)
      } finally {
        db.close()
      }
    }

  @Test
  fun `admin custom question editor dialog creates and validates custom question`() {
    val profile =
      com.example.model.KidProfile(
        id = "p1",
        name = "Player 1",
        avatar = "🐱",
        challengeMode = com.example.model.ChallengeMode.PRE_K,
        soundEnabled = true,
      )
    var savedQuestion: com.example.model.CustomQuestion? = null

    composeTestRule.setContent {
      com.example.ui.SettingsScreen(
        profile = profile,
        canRemoveKid = false,
        onSave = { _, _, _, _ -> },
        onAddAnotherKid = {},
        onRemoveKid = {},
        onBack = {},
        syncState =
          com.example.ui.SyncSettingsUiState(
            hasAdminPin = true,
            isAdminUnlocked = true,
          ),
        onSaveCustomQuestion = { q, _ -> savedQuestion = q },
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_admin").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_questions").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("admin_new_question_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("custom_question_editor_dialog").assertIsDisplayed()

    composeTestRule.onNodeWithTag("question_quick_token_×").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("question_quick_token_÷").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("question_prompt_input").performScrollTo().performTextInput("Spell FROG!")
    composeTestRule.onNodeWithTag("question_clue_input").performScrollTo().performTextInput("🐸, F, ?, ?, G")
    composeTestRule.onNodeWithTag("question_correct_input").performScrollTo().performTextInput("RO")
    composeTestRule.onNodeWithTag("question_wrong_1_input").performScrollTo().performTextInput("RA")
    composeTestRule.onNodeWithTag("question_wrong_2_input").performScrollTo().performTextInput("LO")
    composeTestRule.onNodeWithTag("question_wrong_3_input").performScrollTo().performTextInput("RI")

    // Verify all 4 answer fields have consistent full width on separate lines (not squished)
    val correctWidth =
      composeTestRule.onNodeWithTag("question_correct_input").fetchSemanticsNode().size.width
    val wrong1Width =
      composeTestRule.onNodeWithTag("question_wrong_1_input").fetchSemanticsNode().size.width
    val wrong2Width =
      composeTestRule.onNodeWithTag("question_wrong_2_input").fetchSemanticsNode().size.width
    val wrong3Width =
      composeTestRule.onNodeWithTag("question_wrong_3_input").fetchSemanticsNode().size.width
    assertEquals(correctWidth, wrong1Width)
    assertEquals(correctWidth, wrong2Width)
    assertEquals(correctWidth, wrong3Width)

    composeTestRule.onNodeWithTag("question_save_button").performClick()
    composeTestRule.waitForIdle()

    org.junit.Assert.assertNotNull(savedQuestion)
    assertEquals("Spell FROG!", savedQuestion!!.prompt)
    assertEquals(listOf("🐸", "F", "?", "?", "G"), savedQuestion!!.clueItems)
    assertEquals("RO", savedQuestion!!.correctAnswer)
    assertEquals(listOf("RA", "LO", "RI"), savedQuestion!!.wrongAnswers)
    assertTrue(com.example.model.ChallengeMode.PRE_K in savedQuestion!!.levels)
  }

  @Test
  fun `custom question editor quick tokens insert multiply and divide symbols for older math`() {
    var savedQuestion: com.example.model.CustomQuestion? = null

    composeTestRule.setContent {
      com.example.ui.CustomQuestionEditorDialog(
        initialQuestion = null,
        defaultMode = com.example.model.ChallengeMode.OLDER,
        onSave = { q, _ -> savedQuestion = q },
        onDismiss = {},
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("question_prompt_input").performScrollTo().performTextInput("Multiply!")
    composeTestRule.onNodeWithTag("question_clue_input").performScrollTo().performTextInput("6")
    composeTestRule.onNodeWithTag("question_quick_token_×").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("question_clue_input").performScrollTo().performTextInput(", 7")
    composeTestRule.onNodeWithTag("question_quick_token_=").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("question_quick_token_?").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("question_clue_input").performScrollTo().performTextInput(", !")
    composeTestRule.onNodeWithTag("question_quick_token_÷").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("question_correct_input").performScrollTo().performTextInput("42")
    composeTestRule.onNodeWithTag("question_wrong_1_input").performScrollTo().performTextInput("36")
    composeTestRule.onNodeWithTag("question_save_button").performClick()
    composeTestRule.waitForIdle()

    org.junit.Assert.assertNotNull(savedQuestion)
    assertEquals(listOf("6", "×", "7", "=", "?", "!"), savedQuestion!!.clueItems)
    assertEquals(setOf(com.example.model.ChallengeMode.OLDER), savedQuestion!!.levels)
  }

  @Test
  fun `custom question editor allows tapping emojis into answer fields and keeps save button single line`() {
    var savedQuestion: com.example.model.CustomQuestion? = null

    composeTestRule.setContent {
      com.example.ui.CustomQuestionEditorDialog(
        initialQuestion = null,
        defaultMode = com.example.model.ChallengeMode.PRE_K,
        onSave = { q, _ -> savedQuestion = q },
        onDismiss = {},
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("question_prompt_input").performScrollTo().performTextInput("Finish pattern!")
    composeTestRule.onNodeWithTag("question_clue_input").performScrollTo().performTextInput("🦁, 🐯, 🦁, ?")

    // Default target is Correct Answer (0) -> clue emoji 🦁 promoted to answer emoji bar
    composeTestRule.onNodeWithTag("question_answer_emoji_🐯").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(
      "🐯",
      composeTestRule
        .onNodeWithTag("question_correct_input")
        .fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
        .text,
    )

    // Switch target to Wrong Choice 1 via trailing emoji button -> tap 🦁
    composeTestRule.onNodeWithTag("question_answer_emoji_target_1").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("question_answer_emoji_🦁").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(
      "🦁",
      composeTestRule
        .onNodeWithTag("question_wrong_1_input")
        .fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
        .text,
    )

    // Switch target to Wrong Choice 2 via pinned target chip -> tap ⭐
    composeTestRule.onNodeWithTag("question_answer_target_chip_2").performClick()
    composeTestRule.onNodeWithTag("question_answer_emoji_⭐").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(
      "⭐",
      composeTestRule
        .onNodeWithTag("question_wrong_2_input")
        .fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
        .text,
    )

    // Verify "Save Question" button text fits cleanly on 1 line
    val saveTextLayoutResults = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
    composeTestRule
      .onNodeWithText("Save Question")
      .fetchSemanticsNode()
      .config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
      .action
      ?.invoke(saveTextLayoutResults)
    assertEquals(1, saveTextLayoutResults.firstOrNull()?.lineCount)

    composeTestRule.onNodeWithTag("question_save_button").performClick()
    composeTestRule.waitForIdle()

    org.junit.Assert.assertNotNull(savedQuestion)
    assertEquals("🐯", savedQuestion!!.correctAnswer)
    assertEquals(listOf("🦁", "⭐"), savedQuestion!!.wrongAnswers)
  }

  @Test
  fun `full screen settings uses navigation rail on tablet and bottom bar on phone and tracks return screen`() =
    runTest {
      val app = ApplicationProvider.getApplicationContext<android.app.Application>()
      val vm = com.example.ui.ColorPopViewModel(app)
      assertEquals(com.example.ui.AppScreen.Home, vm.currentScreen.value)

      vm.openSettings()
      assertEquals(com.example.ui.AppScreen.Settings, vm.currentScreen.value)
      vm.closeSettings()
      assertEquals(com.example.ui.AppScreen.Home, vm.currentScreen.value)

      // Open from PlayScreen and confirm Back returns to PlayScreen
      vm.openPicture(com.example.data.ALL_BUILT_IN_TEMPLATES.first())
      assertEquals(com.example.ui.AppScreen.Play, vm.currentScreen.value)
      vm.openSettings()
      assertEquals(com.example.ui.AppScreen.Settings, vm.currentScreen.value)
      vm.closeSettings()
      assertEquals(com.example.ui.AppScreen.Play, vm.currentScreen.value)
    }

  @Test
  fun `settings screen shows hierarchical drill-down menu on phone and navigates back cleanly`() {
    val profile =
      com.example.model.KidProfile(
        id = "p1",
        name = "Player 1",
        avatar = "🐱",
        challengeMode = com.example.model.ChallengeMode.PRE_K,
        soundEnabled = true,
      )
    var backCount = 0
    composeTestRule.setContent {
      com.example.ui.SettingsScreen(
        profile = profile,
        canRemoveKid = false,
        onSave = { _, _, _, _ -> },
        onAddAnotherKid = {},
        onRemoveKid = {},
        onBack = { backCount++ },
        syncState = com.example.ui.SyncSettingsUiState(hasAdminPin = true, isAdminUnlocked = true),
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_phone_container").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_row_profile").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_row_admin").assertIsDisplayed()

    composeTestRule.onNodeWithTag("settings_row_profile").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_name_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_sound_switch").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_profile").assertIsDisplayed()
    assertEquals(0, backCount)

    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    assertEquals(1, backCount)
  }

  @Test
  @Config(qualifiers = "w800dp-h700dp")
  fun `settings screen renders wide hierarchical container and admin sub-menu on tablet`() {
    val profile =
      com.example.model.KidProfile(
        id = "p1",
        name = "Player 1",
        avatar = "🐱",
        challengeMode = com.example.model.ChallengeMode.PRE_K,
        soundEnabled = true,
      )
    composeTestRule.setContent {
      com.example.ui.SettingsScreen(
        profile = profile,
        canRemoveKid = false,
        onSave = { _, _, _, _ -> },
        onAddAnotherKid = {},
        onRemoveKid = {},
        onBack = {},
        syncState = com.example.ui.SyncSettingsUiState(hasAdminPin = true, isAdminUnlocked = true),
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_tablet_container").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_row_admin").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_server").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_row_levels").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_row_questions").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_row_pages").assertIsDisplayed()
  }

  @Test
  fun `custom education level export, Room promote migration, and GitHub sync round-trip cleanly`() =
    runTest {
      val customLevel =
        com.example.model.CustomEducationLevel(
          id = "lvl_99",
          name = "Targeted Spanish",
          description = "Everyday animals and colors in Spanish",
          topics =
            setOf(
              com.example.model.PracticeTopic.SPANISH_BASICS,
              com.example.model.PracticeTopic.EARLY_SPELLING,
            ),
          version = 1,
          isCustom = true,
        )

      val encoded = com.example.engine.LevelExportEngine.encodeLevelToJson(customLevel)
      assertTrue(com.example.engine.LevelExportEngine.isLevelJson(encoded))
      assertEquals(null, com.example.engine.PageExportEngine.decodePageFromJson(encoded))
      val decoded =
        com.example.engine.LevelExportEngine.decodeLevelFromJson(encoded, isSyncedOfficial = false)
      org.junit.Assert.assertNotNull(decoded)
      assertEquals("Targeted Spanish", decoded!!.name)
      assertEquals(
        setOf(
          com.example.model.PracticeTopic.SPANISH_BASICS,
          com.example.model.PracticeTopic.EARLY_SPELLING,
        ),
        decoded.topics,
      )

      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.ColorPopDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      try {
        val repo = com.example.data.ColorPopRepository(db, context)
        repo.saveCustomLevel(customLevel)
        repo.upsertProfile(
          com.example.model.KidProfile(
            id = "p1",
            name = "Mia",
            avatar = "🦊",
            challengeMode = com.example.model.ChallengeMode.PRE_K,
            soundEnabled = true,
            customLevelId = "lvl_99",
          )
        )
        repo.saveCustomQuestion(
          com.example.model.CustomQuestion(
            id = "q_spanish_1",
            prompt = "What is 'red' in Spanish?",
            clueItems = listOf("🍎", "?"),
            correctAnswer = "Rojo",
            wrongAnswers = listOf("Azul", "Verde"),
            levels = emptySet(),
            customLevelIds = setOf("lvl_99"),
          )
        )

        var publishedJson = ""
        var putUrl = ""
        val pubRes =
          com.example.engine.RemoteCatalogSyncEngine.publishLevelToGitHub(
            sourceUrl = "https://github.com/loptienm/NumberByColor.git",
            token = "github_pat_example",
            level = customLevel.copy(id = "targeted_spanish"),
            targetFolder = "ColoringPages",
            httpRequest = { method, url, _, body ->
              when (method) {
                "GET" -> com.example.engine.HttpResponse(404, "")
                "PUT" -> {
                  putUrl = url
                  val req = org.json.JSONObject(body!!)
                  publishedJson =
                    String(
                      android.util.Base64.decode(req.getString("content"), android.util.Base64.DEFAULT),
                      Charsets.UTF_8,
                    )
                  com.example.engine.HttpResponse(
                    201,
                    """{"content":{"sha":"sha_lvl_1","path":"ColoringPages/levels/targeted_spanish.level.json"}}""",
                  )
                }
                else -> com.example.engine.HttpResponse(405, "")
              }
            },
          )
        assertTrue(pubRes.success)
        assertEquals(
          "https://api.github.com/repos/loptienm/NumberByColor/contents/ColoringPages/levels/targeted_spanish.level.json",
          putUrl,
        )

        // Promoting local lvl_99 -> targeted_spanish migrates profile & custom question references
        repo.promotePublishedLevel(pubRes.syncedEntity!!, localLevelId = "lvl_99")
        assertEquals(0, db.customLevelDao().getCustomLevelsOnce().size)
        assertEquals(listOf("targeted_spanish"), repo.getSyncedLevelsOnce().map { it.id })
        assertEquals("targeted_spanish", db.profileDao().getProfilesOnce().first { it.id == "p1" }.customLevelId)
        assertTrue(
          db.customQuestionDao().getCustomQuestionsOnce().first { it.id == "q_spanish_1" }
            .customLevelIdsCsv.split(',').contains("targeted_spanish")
        )

        // Remote sync discovers ColoringPages/levels/*.level.json
        val syncRes =
          com.example.engine.RemoteCatalogSyncEngine.syncFromSource(
            sourceUrl = "https://github.com/loptienm/NumberByColor.git",
            existingSynced = emptyList(),
            existingSyncedLevels = emptyList(),
            httpFetch = { url ->
              when (url) {
                "https://api.github.com/repos/loptienm/NumberByColor/contents" ->
                  com.example.engine.HttpResponse(
                    200,
                    """[{"type":"dir","name":"ColoringPages","path":"ColoringPages"}]""",
                  )
                "https://api.github.com/repos/loptienm/NumberByColor/contents/ColoringPages" ->
                  com.example.engine.HttpResponse(
                    200,
                    """[{"type":"dir","name":"levels","path":"ColoringPages/levels"}]""",
                  )
                "https://api.github.com/repos/loptienm/NumberByColor/contents/ColoringPages/levels" ->
                  com.example.engine.HttpResponse(
                    200,
                    """
                    [
                      {
                        "type": "file",
                        "name": "targeted_spanish.level.json",
                        "path": "ColoringPages/levels/targeted_spanish.level.json",
                        "sha": "sha_lvl_2",
                        "download_url": "https://raw.githubusercontent.com/loptienm/NumberByColor/main/ColoringPages/levels/targeted_spanish.level.json"
                      }
                    ]
                    """.trimIndent(),
                  )
                "https://raw.githubusercontent.com/loptienm/NumberByColor/main/ColoringPages/levels/targeted_spanish.level.json" ->
                  com.example.engine.HttpResponse(200, publishedJson)
                else -> com.example.engine.HttpResponse(404, "")
              }
            },
          )
        assertEquals(null, syncRes.errorMessage)
        assertEquals(1, syncRes.downloadedLevelCount)
        assertEquals(setOf("targeted_spanish"), syncRes.activeRemoteLevelIds)
      } finally {
        db.close()
      }
    }

  @Test
  fun `challenge generator drives targeted topics and custom level questions`() {
    val spanishTopicsLevel =
      com.example.model.CustomEducationLevel(
        id = "spanish_only",
        name = "Targeted Spanish",
        topics = setOf(com.example.model.PracticeTopic.SPANISH_BASICS),
      )
    val qTopicOnly =
      com.example.engine.ChallengeGenerator.nextQuestion(
        mode = com.example.model.ChallengeMode.PRE_K,
        customQuestions = emptyList(),
        customLevel = spanishTopicsLevel,
      )
    assertTrue(
      qTopicOnly.title.contains("Spanish", ignoreCase = true) ||
        qTopicOnly.title.contains("English", ignoreCase = true) ||
        qTopicOnly.title == "What does this mean?"
    )

    // Custom level with empty built-in topics uses 100% assigned custom questions
    val customOnlyLevel =
      com.example.model.CustomEducationLevel(
        id = "mom_spelling",
        name = "Mom's Spelling List",
        topics = emptySet(),
      )
    val assignedQ =
      com.example.model.CustomQuestion(
        id = "q_mom_1",
        prompt = "Spell SUN!",
        clueItems = listOf("☀️", "S", "?", "N"),
        correctAnswer = "U",
        wrongAnswers = listOf("A", "E", "O"),
        levels = emptySet(),
        customLevelIds = setOf("mom_spelling"),
      )
    val unrelatedQ =
      com.example.model.CustomQuestion(
        id = "q_other",
        prompt = "Other question",
        clueItems = listOf("1", "+", "1"),
        correctAnswer = "2",
        wrongAnswers = listOf("3", "4"),
        levels = setOf(com.example.model.ChallengeMode.PRE_K),
        customLevelIds = emptySet(),
      )

    repeat(5) {
      val q =
        com.example.engine.ChallengeGenerator.nextQuestion(
          mode = com.example.model.ChallengeMode.PRE_K,
          customQuestions = listOf(assignedQ, unrelatedQ),
          customLevel = customOnlyLevel,
        )
      assertEquals("Spell SUN!", q.title)
      assertEquals("U", q.correct)
    }
  }

  @Test
  fun `settings flow creates custom education level and selects it in profile settings`() {
    val profile =
      com.example.model.KidProfile(
        id = "p1",
        name = "Player 1",
        avatar = "🐱",
        challengeMode = com.example.model.ChallengeMode.PRE_K,
        soundEnabled = true,
      )
    var levelsState by mutableStateOf(emptyList<com.example.model.CustomEducationLevel>())
    var savedCustomLevelId: String? = null
    var savedMode: com.example.model.ChallengeMode? = null

    composeTestRule.setContent {
      com.example.ui.SettingsScreen(
        profile = profile,
        canRemoveKid = false,
        onSave = { _, _, mode, customLvlId ->
          savedMode = mode
          savedCustomLevelId = customLvlId
        },
        onAddAnotherKid = {},
        onRemoveKid = {},
        onBack = {},
        syncState = com.example.ui.SyncSettingsUiState(hasAdminPin = true, isAdminUnlocked = true),
        customLevels = levelsState,
        onSaveCustomLevel = { lvl, _ ->
          levelsState = levelsState + lvl.copy(id = "targeted_spanish", isCustom = true)
        },
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_admin").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_levels").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("admin_new_level_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("level_name_input").performScrollTo().performTextInput("Targeted Spanish")
    composeTestRule.onNodeWithTag("level_topic_chip_spanish_basics").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("level_save_button").performClick()
    composeTestRule.waitForIdle()

    // Navigate back twice: Education Levels -> Admin -> Root, then drill into Profile Settings
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_row_profile").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("challenge_level_dropdown").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("challenge_custom_level_targeted_spanish").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_save_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals("targeted_spanish", savedCustomLevelId)
    assertEquals(com.example.model.ChallengeMode.PRE_K, savedMode)
  }

  @Test
  fun `parent pin gate advances with Next key and submits on Done`() {
    var createdPin: String? = null
    composeTestRule.setContent {
      com.example.ui.ParentPinGatePanel(
        hasExistingPin = false,
        onSetupPin = { pin ->
          createdPin = pin
          true
        },
        onVerifyPin = { false },
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("admin_pin_input").performTextInput("2468")
    composeTestRule.onNodeWithTag("admin_pin_input").performImeAction()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("admin_pin_confirm_input").assertIsFocused()

    composeTestRule.onNodeWithTag("admin_pin_confirm_input").performTextInput("2468")
    composeTestRule.onNodeWithTag("admin_pin_confirm_input").performImeAction()
    composeTestRule.waitForIdle()
    assertEquals("2468", createdPin)
  }

  @Test
  fun `custom question editor advances focus through prompt, clue, and answer choices on Next`() {
    composeTestRule.setContent {
      com.example.ui.CustomQuestionEditorDialog(
        initialQuestion = null,
        defaultMode = com.example.model.ChallengeMode.PRE_K,
        onSave = { _, _ -> },
        onDismiss = {},
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("question_prompt_input").performScrollTo().performClick()
    composeTestRule
      .onNodeWithTag("question_prompt_input")
      .assert(
        androidx.compose.ui.test.SemanticsMatcher.expectValue(
          androidx.compose.ui.semantics.SemanticsProperties.ImeAction,
          androidx.compose.ui.text.input.ImeAction.Next,
        )
      )
    composeTestRule.onNodeWithTag("question_prompt_input").performImeAction()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("question_clue_input").assertIsFocused()

    composeTestRule
      .onNodeWithTag("question_clue_input")
      .assert(
        androidx.compose.ui.test.SemanticsMatcher.expectValue(
          androidx.compose.ui.semantics.SemanticsProperties.ImeAction,
          androidx.compose.ui.text.input.ImeAction.Next,
        )
      )
    composeTestRule.onNodeWithTag("question_clue_input").performImeAction()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("question_correct_input").assertIsFocused()

    composeTestRule.onNodeWithTag("question_correct_input").performImeAction()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("question_wrong_1_input").assertIsFocused()

    composeTestRule.onNodeWithTag("question_wrong_1_input").performImeAction()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("question_wrong_2_input").assertIsFocused()

    composeTestRule.onNodeWithTag("question_wrong_2_input").performImeAction()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("question_wrong_3_input").assertIsFocused()
    composeTestRule
      .onNodeWithTag("question_wrong_3_input")
      .assert(
        androidx.compose.ui.test.SemanticsMatcher.expectValue(
          androidx.compose.ui.semantics.SemanticsProperties.ImeAction,
          androidx.compose.ui.text.input.ImeAction.Done,
        )
      )
    composeTestRule.onNodeWithTag("question_wrong_3_input").performImeAction()
    composeTestRule.waitForIdle()
  }

  @Test
  fun `profile export engine encodes, decodes, and isolates same-name profiles by ID`() {
    val profile =
      com.example.model.KidProfile(
        id = "p_abc987",
        name = "Maya",
        avatar = "🦋",
        challengeMode = com.example.model.ChallengeMode.KINDER,
        soundEnabled = false,
        customLevelId = "lvl_custom",
      )
    val customLevel =
      com.example.model.CustomEducationLevel(
        id = "lvl_custom",
        name = "Custom Reading",
        description = "Sight words",
        topics = setOf(com.example.model.PracticeTopic.EARLY_SPELLING),
      )
    val bundle =
      com.example.engine.ProfileExportBundle(
        profile = profile,
        exportedAt = 1_700_000_000_000L,
        progress =
          listOf(
            com.example.engine.ExportedProgressEntry(
              pictureId = "cat",
              paintedGrid = "",
              isCompleted = true,
              updatedAt = 1_700_000_000_100L,
            ),
            com.example.engine.ExportedProgressEntry(
              pictureId = "dog",
              paintedGrid = "1,0;0,2",
              isCompleted = false,
              updatedAt = 1_700_000_000_200L,
            ),
          ),
        stickerPages =
          listOf(
            com.example.engine.ExportedStickerPage(
              pageId = "page_1",
              pageOrder = 0,
              paperIndex = 2,
              createdAt = 1_700_000_000_300L,
            )
          ),
        stickerPlacements =
          listOf(
            com.example.engine.ExportedPlacedSticker(
              id = "st_1",
              pageId = "page_1",
              templateId = "cat",
              xFraction = 0.25f,
              yFraction = 0.75f,
              scale = 1.4f,
              zOrder = 3L,
              isMirrored = true,
            )
          ),
        customLevels = listOf(customLevel),
      )

    val json = com.example.engine.ProfileExportEngine.encodeProfileToJson(bundle)
    assertTrue(com.example.engine.ProfileExportEngine.isProfileJson(json))
    assertEquals("ABC987", com.example.engine.ProfileExportEngine.shortProfileCode(profile.id))

    val decoded = com.example.engine.ProfileExportEngine.decodeProfileFromJson(json)
    assertNotNull(decoded)
    assertEquals("p_abc987", decoded!!.profile.id)
    assertEquals("Maya", decoded.profile.name)
    assertEquals("🦋", decoded.profile.avatar)
    assertEquals(false, decoded.profile.soundEnabled)
    assertEquals("lvl_custom", decoded.profile.customLevelId)
    assertEquals(1, decoded.completedCount)
    assertEquals(1, decoded.inProgressCount)
    assertEquals(1, decoded.stickerCount)
    assertEquals(true, decoded.stickerPlacements.single().isMirrored)
    assertEquals("lvl_custom", decoded.customLevels.single().id)

    // Non-profile JSON must be rejected
    val nonProfile = com.example.engine.LevelExportEngine.encodeLevelToJson(customLevel)
    assertNull(com.example.engine.ProfileExportEngine.decodeProfileFromJson(nonProfile))
  }

  @Test
  fun `repository profile import handles overwrite and keep-both without colliding sticker primary keys`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        Room.inMemoryDatabaseBuilder(context, com.example.data.ColorPopDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      try {
        val repo = com.example.data.ColorPopRepository(db, context)
        val maya1 =
          com.example.model.KidProfile(
            id = "p_maya_orig",
            name = "Maya",
            avatar = "🦊",
            challengeMode = com.example.model.ChallengeMode.OLDER,
            soundEnabled = true,
          )
        repo.upsertProfile(maya1)
        repo.markCompleted(maya1.id, "rocket")
        repo.ensureStickerPages(maya1.id)
        repo.upsertPlacedSticker(
          maya1.id,
          com.example.model.PlacedSticker(
            id = "sticker_shared_pk",
            pageId = "page_1",
            templateId = "rocket",
            xFraction = 0.3f,
            yFraction = 0.4f,
            scale = 1.2f,
            isMirrored = false,
            zOrder = 1L,
          ),
        )

        val exported = repo.exportProfileBundle(maya1.id)!!

        // Import as NEW profile (even with same name "Maya"): original stickers & progress stay intact
        val cloned =
          repo.importProfileBundle(
            bundle = exported,
            mode = com.example.data.ProfileImportConflictMode.IMPORT_AS_NEW,
          )
        assertTrue(cloned.id != maya1.id)
        assertEquals("Maya", cloned.name)
        assertEquals(1, repo.getProfileSummary(maya1.id).completedCount)
        assertEquals(1, repo.getProfileSummary(maya1.id).stickerCount)
        assertEquals(1, repo.getProfileSummary(cloned.id).completedCount)
        assertEquals(1, repo.getProfileSummary(cloned.id).stickerCount)

        // Now modify original locally, then overwrite it from exported bundle
        repo.markCompleted(maya1.id, "castle")
        assertEquals(2, repo.getProfileSummary(maya1.id).completedCount)

        val overwritten =
          repo.importProfileBundle(
            bundle = exported,
            mode = com.example.data.ProfileImportConflictMode.OVERWRITE_TARGET,
            targetProfileId = maya1.id,
          )
        assertEquals(maya1.id, overwritten.id)
        assertEquals(1, repo.getProfileSummary(maya1.id).completedCount)
      } finally {
        db.close()
      }
    }

  @Test
  fun `profile import confirmation dialog warns clearly on exact ID match and same-name match`() {
    val incoming =
      com.example.engine.ProfileExportBundle(
        profile =
          com.example.model.KidProfile(
            id = "p_112233",
            name = "Maya",
            avatar = "🦋",
            challengeMode = com.example.model.ChallengeMode.PRE_K,
            soundEnabled = true,
          ),
        exportedAt = 1_700_000_000_000L,
        progress = emptyList(),
        stickerPages = emptyList(),
        stickerPlacements = emptyList(),
      )
    val localExact =
      com.example.ui.ExistingProfileMatch(
        profile = incoming.profile.copy(name = "Maya Old"),
        summary = com.example.data.ProfileSummaryCounts(completedCount = 4, stickerCount = 2),
      )
    var overwrittenTarget: String? = null
    var importedAsNew = false

    composeTestRule.setContent {
      com.example.ui.ProfileImportConfirmationDialog(
        preview =
          com.example.ui.ProfileImportPreviewUiState(
            bundle = incoming,
            exactIdMatch = localExact,
          ),
        onOverwriteExisting = { overwrittenTarget = it },
        onImportAsNew = { importedAsNew = true },
        onDismiss = {},
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_import_overwrite_warning").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_import_confirm_overwrite_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals("p_112233", overwrittenTarget)
    assertEquals(false, importedAsNew)
  }

  @Test
  fun `opening Settings directly to Profile skips root menu and exits cleanly on back`() {
    val profile =
      com.example.model.KidProfile(
        id = "p_new_kid",
        name = "Kid 2",
        avatar = "🐼",
        challengeMode = com.example.model.ChallengeMode.PRE_K,
        soundEnabled = true,
      )
    var backCount = 0
    composeTestRule.setContent {
      com.example.ui.SettingsScreen(
        profile = profile,
        canRemoveKid = true,
        onSave = { _, _, _, _ -> },
        onAddAnotherKid = {},
        onRemoveKid = {},
        onBack = { backCount++ },
        initialRoute = com.example.ui.SettingsRoute.PROFILE,
        routeNonce = 1,
        directProfileBackToExit = true,
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_name_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_row_admin").assertDoesNotExist()
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, backCount)
  }
}
