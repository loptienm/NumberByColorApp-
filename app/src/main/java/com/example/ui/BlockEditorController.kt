package com.example.ui

import com.example.engine.BlockEditorEngine
import com.example.engine.EditorShapeType
import com.example.engine.EditorTool
import com.example.engine.ShapeDraft
import com.example.model.PictureTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_EDITOR_UNDO = 40
private val FRAME_SPEED_PRESETS = listOf(150, 250, 400, 600, 900)

data class BlockEditorSnapshot(
  val frames: List<List<String>>,
  val activeFrameIndex: Int,
  val palette: Map<Int, Int>,
  val frameMs: Int,
  val visibleColors: Set<Int>,
)

data class BlockEditorUiState(
  val baseTemplate: PictureTemplate? = null,
  val originScreen: AppScreen = AppScreen.Home,
  val editingExistingId: String? = null,
  val title: String = "My Drawing",
  val gridWidth: Int = 16,
  val gridHeight: Int = 16,
  val palette: Map<Int, Int> = emptyMap(),
  val frames: List<List<String>> = emptyList(),
  val activeFrameIndex: Int = 0,
  val frameMs: Int = 400,
  val isPlayingPreview: Boolean = false,
  val activeTool: EditorTool = EditorTool.DRAW,
  val activeColorNumber: Int? = 1,
  val fillWithEraser: Boolean = false,
  val visibleColors: Set<Int> = emptySet(),
  val globalReplaceAllFrames: Boolean = true,
  val shapeType: EditorShapeType = EditorShapeType.RECTANGLE,
  val shapeFilled: Boolean = false,
  val activeShape: ShapeDraft? = null,
  val showNumbers: Boolean = true,
  val showGridLines: Boolean = true,
  val hasUnsavedChanges: Boolean = false,
  val undoStack: List<BlockEditorSnapshot> = emptyList(),
  val redoStack: List<BlockEditorSnapshot> = emptyList(),
) {
  val canUndo: Boolean get() = undoStack.isNotEmpty()
  val canRedo: Boolean get() = redoStack.isNotEmpty()
}

class BlockEditorController(private val onToast: (String) -> Unit) {
  private val _state = MutableStateFlow(BlockEditorUiState())
  val state: StateFlow<BlockEditorUiState> = _state.asStateFlow()

  private var pendingStrokeSnapshot: BlockEditorSnapshot? = null
  private var strokeMutatedAnything: Boolean = false
  private var previousNonPanTool: EditorTool = EditorTool.DRAW

  fun loadTemplate(
    template: PictureTemplate,
    originScreen: AppScreen,
    editingExistingId: String?,
  ) {
    val normalizedFrames = BlockEditorEngine.normalizeFrames(template)
    val sortedPalette = template.palette.toSortedMap()
    val firstColor = sortedPalette.keys.minOrNull() ?: 1
    pendingStrokeSnapshot = null
    strokeMutatedAnything = false
    previousNonPanTool = EditorTool.DRAW
    _state.value =
      BlockEditorUiState(
        baseTemplate = template,
        originScreen = originScreen,
        editingExistingId = editingExistingId,
        title = template.title,
        gridWidth = template.gridWidth,
        gridHeight = template.gridHeight,
        palette = sortedPalette,
        frames = normalizedFrames,
        activeFrameIndex = 0,
        frameMs = template.frameMs.coerceIn(80, 1200),
        isPlayingPreview = false,
        activeTool = EditorTool.DRAW,
        activeColorNumber = firstColor,
        fillWithEraser = false,
        visibleColors = sortedPalette.keys.toSet(),
        globalReplaceAllFrames = true,
        shapeType = EditorShapeType.RECTANGLE,
        shapeFilled = false,
        activeShape = null,
        showNumbers = true,
        showGridLines = true,
        hasUnsavedChanges = false,
        undoStack = emptyList(),
        redoStack = emptyList(),
      )
  }

  private fun BlockEditorUiState.toSnapshot(): BlockEditorSnapshot =
    BlockEditorSnapshot(
      frames = frames,
      activeFrameIndex = activeFrameIndex,
      palette = palette,
      frameMs = frameMs,
      visibleColors = visibleColors,
    )

  private fun pushSnapshot(
    currentUndo: List<BlockEditorSnapshot>,
    snap: BlockEditorSnapshot,
  ): List<BlockEditorSnapshot> = (currentUndo + snap).takeLast(MAX_EDITOR_UNDO)

  fun undo() {
    _state.update { st ->
      val prev = st.undoStack.lastOrNull() ?: return@update st
      val currentSnap = st.toSnapshot()
      val safeColor =
        when (val cur = st.activeColorNumber) {
          null -> null
          in prev.palette -> cur
          else -> prev.palette.keys.minOrNull()
        }
      st.copy(
        frames = prev.frames,
        activeFrameIndex = prev.activeFrameIndex.coerceIn(0, (prev.frames.size - 1).coerceAtLeast(0)),
        palette = prev.palette,
        frameMs = prev.frameMs,
        visibleColors = prev.visibleColors.intersect(prev.palette.keys),
        activeColorNumber = safeColor,
        activeShape = null,
        isPlayingPreview = false,
        undoStack = st.undoStack.dropLast(1),
        redoStack = (st.redoStack + currentSnap).takeLast(MAX_EDITOR_UNDO),
        hasUnsavedChanges = true,
      )
    }
  }

  fun redo() {
    _state.update { st ->
      val next = st.redoStack.lastOrNull() ?: return@update st
      val currentSnap = st.toSnapshot()
      val safeColor =
        when (val cur = st.activeColorNumber) {
          null -> null
          in next.palette -> cur
          else -> next.palette.keys.minOrNull()
        }
      st.copy(
        frames = next.frames,
        activeFrameIndex = next.activeFrameIndex.coerceIn(0, (next.frames.size - 1).coerceAtLeast(0)),
        palette = next.palette,
        frameMs = next.frameMs,
        visibleColors = next.visibleColors.intersect(next.palette.keys),
        activeColorNumber = safeColor,
        activeShape = null,
        isPlayingPreview = false,
        undoStack = (st.undoStack + currentSnap).takeLast(MAX_EDITOR_UNDO),
        redoStack = st.redoStack.dropLast(1),
        hasUnsavedChanges = true,
      )
    }
  }

  fun updateTitle(title: String) {
    _state.update { it.copy(title = title, hasUnsavedChanges = true) }
  }

  fun selectTool(tool: EditorTool) {
    if (tool != EditorTool.PAN) previousNonPanTool = tool
    _state.update { st ->
      val nextColor =
        when (tool) {
          EditorTool.ERASE -> null
          EditorTool.DRAW, EditorTool.FLOOD_FILL, EditorTool.GLOBAL_REPLACE, EditorTool.SHAPE ->
            st.activeColorNumber ?: st.palette.keys.minOrNull()
          EditorTool.EYEDROPPER, EditorTool.PAN -> st.activeColorNumber
        }
      st.copy(
        activeTool = tool,
        activeColorNumber = nextColor,
        activeShape = if (tool == EditorTool.SHAPE) st.activeShape else null,
        isPlayingPreview = false,
      )
    }
  }

  fun togglePanTool() {
    _state.update { st ->
      val nextTool =
        if (st.activeTool == EditorTool.PAN) previousNonPanTool
        else {
          previousNonPanTool = st.activeTool
          EditorTool.PAN
        }
      st.copy(activeTool = nextTool, isPlayingPreview = false)
    }
  }

  fun toggleGlobalScope() {
    _state.update { it.copy(globalReplaceAllFrames = !it.globalReplaceAllFrames) }
  }

  fun toggleFillWithEraser() {
    _state.update { it.copy(fillWithEraser = !it.fillWithEraser) }
  }

  fun toggleNumbers() {
    _state.update { it.copy(showNumbers = !it.showNumbers) }
  }

  fun toggleGridLines() {
    _state.update { it.copy(showGridLines = !it.showGridLines) }
  }

  fun beginStroke(row: Int, col: Int) {
    val st = _state.value
    if (st.activeTool != EditorTool.DRAW && st.activeTool != EditorTool.ERASE) return
    val idx = st.activeFrameIndex.coerceIn(0, (st.frames.size - 1).coerceAtLeast(0))
    val activeFrame = st.frames.getOrNull(idx) ?: return
    val paintVal =
      if (st.activeTool == EditorTool.ERASE) 0
      else st.activeColorNumber ?: st.palette.keys.minOrNull() ?: return

    pendingStrokeSnapshot = st.toSnapshot()
    strokeMutatedAnything = false

    val nextFrame = BlockEditorEngine.paintCell(activeFrame, row, col, paintVal)
    if (nextFrame !== activeFrame) {
      val updatedFrames = st.frames.toMutableList().apply { set(idx, nextFrame) }
      val snap = pendingStrokeSnapshot!!
      pendingStrokeSnapshot = null
      strokeMutatedAnything = true
      val nextVisible =
        if (paintVal > 0) st.visibleColors + paintVal else st.visibleColors
      _state.update { cur ->
        cur.copy(
          frames = updatedFrames,
          visibleColors = nextVisible,
          isPlayingPreview = false,
          hasUnsavedChanges = true,
          undoStack = pushSnapshot(cur.undoStack, snap),
          redoStack = emptyList(),
        )
      }
    } else if (st.isPlayingPreview) {
      _state.update { it.copy(isPlayingPreview = false) }
    }
  }

  fun stepStroke(fromRow: Int?, fromCol: Int?, toRow: Int, toCol: Int) {
    val st = _state.value
    if (st.activeTool != EditorTool.DRAW && st.activeTool != EditorTool.ERASE) return
    val idx = st.activeFrameIndex.coerceIn(0, (st.frames.size - 1).coerceAtLeast(0))
    val activeFrame = st.frames.getOrNull(idx) ?: return
    val paintVal =
      if (st.activeTool == EditorTool.ERASE) 0
      else st.activeColorNumber ?: st.palette.keys.minOrNull() ?: return

    val nextFrame = BlockEditorEngine.paintStroke(activeFrame, fromRow, fromCol, toRow, toCol, paintVal)
    if (nextFrame !== activeFrame) {
      val updatedFrames = st.frames.toMutableList().apply { set(idx, nextFrame) }
      val snapToCommit = if (!strokeMutatedAnything) pendingStrokeSnapshot else null
      pendingStrokeSnapshot = null
      strokeMutatedAnything = true
      val nextVisible =
        if (paintVal > 0) st.visibleColors + paintVal else st.visibleColors
      _state.update { cur ->
        cur.copy(
          frames = updatedFrames,
          visibleColors = nextVisible,
          hasUnsavedChanges = true,
          undoStack =
            if (snapToCommit != null) pushSnapshot(cur.undoStack, snapToCommit)
            else cur.undoStack,
          redoStack = if (snapToCommit != null) emptyList() else cur.redoStack,
        )
      }
    }
  }

  fun tapCellAction(row: Int, col: Int) {
    val st = _state.value
    val idx = st.activeFrameIndex.coerceIn(0, (st.frames.size - 1).coerceAtLeast(0))
    val activeFrame = st.frames.getOrNull(idx) ?: return
    val tappedColor = BlockEditorEngine.cellAt(activeFrame, row, col)

    when (st.activeTool) {
      EditorTool.EYEDROPPER -> {
        if (tappedColor <= 0) {
          previousNonPanTool = EditorTool.ERASE
          _state.update { it.copy(activeTool = EditorTool.ERASE, isPlayingPreview = false) }
          onToast("Switched to Eraser")
        } else {
          previousNonPanTool = EditorTool.DRAW
          _state.update {
            it.copy(
              activeColorNumber = tappedColor,
              activeTool = EditorTool.DRAW,
              fillWithEraser = false,
              visibleColors = it.visibleColors + tappedColor,
              isPlayingPreview = false,
            )
          }
          onToast("Picked color #$tappedColor")
        }
      }

      EditorTool.FLOOD_FILL -> {
        val replacement =
          if (st.fillWithEraser) 0
          else st.activeColorNumber ?: st.palette.keys.minOrNull() ?: return
        val nextFrame = BlockEditorEngine.floodFill(activeFrame, row, col, replacement)
        if (nextFrame === activeFrame) return
        val snap = st.toSnapshot()
        val updatedFrames = st.frames.toMutableList().apply { set(idx, nextFrame) }
        val nextVisible = if (replacement > 0) st.visibleColors + replacement else st.visibleColors
        _state.update {
          it.copy(
            frames = updatedFrames,
            visibleColors = nextVisible,
            isPlayingPreview = false,
            hasUnsavedChanges = true,
            undoStack = pushSnapshot(it.undoStack, snap),
            redoStack = emptyList(),
          )
        }
      }

      EditorTool.GLOBAL_REPLACE -> {
        if (tappedColor <= 0) {
          onToast("Tap a colored block to replace all matches")
          return
        }
        val replacement =
          if (st.fillWithEraser) 0
          else st.activeColorNumber ?: st.palette.keys.minOrNull() ?: return
        if (tappedColor == replacement) return
        val snap = st.toSnapshot()
        val updatedFrames =
          BlockEditorEngine.globalReplace(
            frames = st.frames,
            activeFrameIndex = idx,
            targetColor = tappedColor,
            replacementColor = replacement,
            allFrames = st.globalReplaceAllFrames,
          )
        val nextVisible = if (replacement > 0) st.visibleColors + replacement else st.visibleColors
        _state.update {
          it.copy(
            frames = updatedFrames,
            visibleColors = nextVisible,
            isPlayingPreview = false,
            hasUnsavedChanges = true,
            undoStack = pushSnapshot(it.undoStack, snap),
            redoStack = emptyList(),
          )
        }
        val actionText =
          if (replacement == 0) "Erased all #$tappedColor blocks"
          else "Replaced all #$tappedColor with #$replacement"
        onToast(actionText)
      }

      else -> Unit
    }
  }

  fun selectShapeType(type: EditorShapeType) {
    _state.update { st ->
      val updatedDraft =
        st.activeShape?.let { draft ->
          if (type == EditorShapeType.LINE && draft.shapeType != EditorShapeType.LINE) {
            draft.copy(
              shapeType = type,
              lineStartCol = draft.centerCol - draft.halfWidth,
              lineStartRow = draft.centerRow - draft.halfHeight,
              lineEndCol = draft.centerCol + draft.halfWidth,
              lineEndRow = draft.centerRow + draft.halfHeight,
            )
          } else if (type != EditorShapeType.LINE && draft.shapeType == EditorShapeType.LINE) {
            val minC = minOf(draft.lineStartCol, draft.lineEndCol)
            val maxC = maxOf(draft.lineStartCol, draft.lineEndCol)
            val minR = minOf(draft.lineStartRow, draft.lineEndRow)
            val maxR = maxOf(draft.lineStartRow, draft.lineEndRow)
            draft.copy(
              shapeType = type,
              centerCol = (minC + maxC) / 2f,
              centerRow = (minR + maxR) / 2f,
              halfWidth = ((maxC - minC) / 2f).coerceAtLeast(1.5f),
              halfHeight = ((maxR - minR) / 2f).coerceAtLeast(1.5f),
              rotationDeg = 0f,
              filled = st.shapeFilled,
            )
          } else {
            draft.copy(shapeType = type, filled = st.shapeFilled)
          }
        }
      st.copy(shapeType = type, activeShape = updatedDraft)
    }
  }

  fun toggleShapeFilled() {
    _state.update { st ->
      val nextFilled = !st.shapeFilled
      st.copy(
        shapeFilled = nextFilled,
        activeShape = st.activeShape?.copy(filled = nextFilled),
      )
    }
  }

  fun updateShapeDraft(draft: ShapeDraft?) {
    _state.update { it.copy(activeShape = draft, isPlayingPreview = false) }
  }

  fun discardShapeDraft() {
    _state.update { it.copy(activeShape = null) }
  }

  fun commitShapeDraft() {
    val st = _state.value
    val draft = st.activeShape ?: return
    val idx = st.activeFrameIndex.coerceIn(0, (st.frames.size - 1).coerceAtLeast(0))
    val activeFrame = st.frames.getOrNull(idx) ?: return
    val paintVal =
      if (st.fillWithEraser) 0
      else st.activeColorNumber ?: st.palette.keys.minOrNull() ?: return

    val cells = BlockEditorEngine.rasterizeShapeCells(draft, st.gridWidth, st.gridHeight)
    val nextFrame = BlockEditorEngine.applyShapeCells(activeFrame, cells, paintVal)
    if (nextFrame === activeFrame) {
      _state.update { it.copy(activeShape = null) }
      return
    }
    val snap = st.toSnapshot()
    val updatedFrames = st.frames.toMutableList().apply { set(idx, nextFrame) }
    val nextVisible = if (paintVal > 0) st.visibleColors + paintVal else st.visibleColors
    _state.update {
      it.copy(
        frames = updatedFrames,
        visibleColors = nextVisible,
        activeShape = null,
        hasUnsavedChanges = true,
        undoStack = pushSnapshot(it.undoStack, snap),
        redoStack = emptyList(),
      )
    }
  }

  fun selectColor(colorNum: Int) {
    _state.update { st ->
      val nextTool =
        if (st.activeTool == EditorTool.ERASE || st.activeTool == EditorTool.PAN) EditorTool.DRAW
        else st.activeTool
      previousNonPanTool = nextTool
      st.copy(
        activeColorNumber = colorNum,
        activeTool = nextTool,
        fillWithEraser = false,
      )
    }
  }

  fun toggleColorVisibility(colorNum: Int) {
    _state.update { st ->
      val nextVisible =
        if (colorNum in st.visibleColors) st.visibleColors - colorNum
        else st.visibleColors + colorNum
      st.copy(visibleColors = nextVisible)
    }
  }

  fun showAllColors() {
    _state.update { it.copy(visibleColors = it.palette.keys) }
  }

  fun uncolorAll() {
    _state.update { it.copy(visibleColors = emptySet()) }
  }

  fun swapColors(colorA: Int, colorB: Int) {
    val st = _state.value
    if (colorA == colorB || colorA !in st.palette || colorB !in st.palette) return
    val snap = st.toSnapshot()
    val res =
      BlockEditorEngine.swapPaletteColors(
        palette = st.palette,
        frames = st.frames,
        visibleColors = st.visibleColors,
        colorA = colorA,
        colorB = colorB,
      )
    val newActive = st.activeColorNumber?.let { res.oldToNewMap[it] ?: 1 }
    _state.update {
      it.copy(
        palette = res.palette,
        frames = res.frames,
        visibleColors = res.visibleColors,
        activeColorNumber = newActive,
        hasUnsavedChanges = true,
        undoStack = pushSnapshot(it.undoStack, snap),
        redoStack = emptyList(),
      )
    }
    onToast("Swapped #$colorA and #$colorB")
  }

  fun reorderPalette(orderedOldKeys: List<Int>) {
    val st = _state.value
    if (orderedOldKeys == st.palette.keys.sorted()) return
    val snap = st.toSnapshot()
    val res =
      BlockEditorEngine.reorderPalette(
        palette = st.palette,
        frames = st.frames,
        visibleColors = st.visibleColors,
        orderedOldKeys = orderedOldKeys,
      )
    val newActive = st.activeColorNumber?.let { res.oldToNewMap[it] ?: 1 }
    _state.update {
      it.copy(
        palette = res.palette,
        frames = res.frames,
        visibleColors = res.visibleColors,
        activeColorNumber = newActive,
        hasUnsavedChanges = true,
        undoStack = pushSnapshot(it.undoStack, snap),
        redoStack = emptyList(),
      )
    }
  }

  fun addColor(argb: Int) {
    val st = _state.value
    val added = BlockEditorEngine.addPaletteColor(st.palette, st.visibleColors, argb)
    if (added == null) {
      onToast("Palette is at max (${BlockEditorEngine.MAX_PALETTE_COLORS} colors)")
      return
    }
    val (nextPalette, nextVisible, newNum) = added
    val snap = st.toSnapshot()
    val nextTool =
      if (st.activeTool == EditorTool.ERASE || st.activeTool == EditorTool.PAN) EditorTool.DRAW
      else st.activeTool
    _state.update {
      it.copy(
        palette = nextPalette,
        visibleColors = nextVisible,
        activeColorNumber = newNum,
        activeTool = nextTool,
        fillWithEraser = false,
        hasUnsavedChanges = true,
        undoStack = pushSnapshot(it.undoStack, snap),
        redoStack = emptyList(),
      )
    }
  }

  fun updateColorShade(colorNum: Int, argb: Int) {
    val st = _state.value
    if (colorNum !in st.palette) return
    val opaque = argb or (0xFF shl 24)
    if (st.palette[colorNum] == opaque) return
    val snap = st.toSnapshot()
    val nextPalette = st.palette.toMutableMap().apply { put(colorNum, opaque) }.toSortedMap()
    _state.update {
      it.copy(
        palette = nextPalette,
        visibleColors = it.visibleColors + colorNum,
        hasUnsavedChanges = true,
        undoStack = pushSnapshot(it.undoStack, snap),
        redoStack = emptyList(),
      )
    }
  }

  fun mergeColor(sourceNum: Int, targetNum: Int) {
    val st = _state.value
    val snap = st.toSnapshot()
    val res =
      BlockEditorEngine.mergePaletteColor(
        palette = st.palette,
        frames = st.frames,
        visibleColors = st.visibleColors,
        sourceColor = sourceNum,
        targetColor = targetNum,
      )
    val newTarget = res.oldToNewMap[targetNum] ?: 1
    _state.update {
      it.copy(
        palette = res.palette,
        frames = res.frames,
        visibleColors = res.visibleColors,
        activeColorNumber = newTarget,
        hasUnsavedChanges = true,
        undoStack = pushSnapshot(it.undoStack, snap),
        redoStack = emptyList(),
      )
    }
    onToast("Merged #$sourceNum into #$newTarget")
  }

  fun deleteUnusedColor(colorNum: Int) {
    val st = _state.value
    if (st.palette.size <= 1) return
    val snap = st.toSnapshot()
    val res =
      BlockEditorEngine.deletePaletteColor(
        palette = st.palette,
        frames = st.frames,
        visibleColors = st.visibleColors,
        targetColor = colorNum,
      )
    val nextActive = res.palette.keys.minOrNull()
    _state.update {
      it.copy(
        palette = res.palette,
        frames = res.frames,
        visibleColors = res.visibleColors,
        activeColorNumber = nextActive,
        hasUnsavedChanges = true,
        undoStack = pushSnapshot(it.undoStack, snap),
        redoStack = emptyList(),
      )
    }
  }

  fun selectFrame(index: Int) {
    _state.update { st ->
      val safe = index.coerceIn(0, (st.frames.size - 1).coerceAtLeast(0))
      st.copy(activeFrameIndex = safe, activeShape = null, isPlayingPreview = false)
    }
  }

  fun advancePlaybackFrame() {
    _state.update { st ->
      if (st.frames.size <= 1) return@update st.copy(isPlayingPreview = false)
      st.copy(activeFrameIndex = (st.activeFrameIndex + 1) % st.frames.size)
    }
  }

  fun togglePlay() {
    _state.update { st ->
      if (st.frames.size <= 1) st else st.copy(isPlayingPreview = !st.isPlayingPreview, activeShape = null)
    }
  }

  fun cycleSpeed() {
    _state.update { st ->
      val curIdx = FRAME_SPEED_PRESETS.indexOfFirst { it >= st.frameMs }.let { if (it >= 0) it else 0 }
      val nextMs = FRAME_SPEED_PRESETS[(curIdx + 1) % FRAME_SPEED_PRESETS.size]
      st.copy(frameMs = nextMs, hasUnsavedChanges = true)
    }
  }

  fun insertFrame(insertIndex: Int, copyCurrent: Boolean) {
    val st = _state.value
    if (st.frames.size >= BlockEditorEngine.MAX_FRAMES) {
      onToast("Maximum ${BlockEditorEngine.MAX_FRAMES} frames reached")
      return
    }
    val snap = st.toSnapshot()
    val curFrame = st.frames.getOrNull(st.activeFrameIndex)
    val nextFrames =
      BlockEditorEngine.insertFrame(
        frames = st.frames,
        insertIndex = insertIndex,
        width = st.gridWidth,
        height = st.gridHeight,
        copyFrom = if (copyCurrent) curFrame else null,
      )
    val newIdx = insertIndex.coerceIn(0, nextFrames.lastIndex)
    _state.update {
      it.copy(
        frames = nextFrames,
        activeFrameIndex = newIdx,
        activeShape = null,
        isPlayingPreview = false,
        hasUnsavedChanges = true,
        undoStack = pushSnapshot(it.undoStack, snap),
        redoStack = emptyList(),
      )
    }
  }

  fun deleteCurrentFrame() {
    val st = _state.value
    if (st.frames.size <= 1) return
    val snap = st.toSnapshot()
    val nextFrames = BlockEditorEngine.deleteFrame(st.frames, st.activeFrameIndex)
    val nextIdx = st.activeFrameIndex.coerceIn(0, nextFrames.lastIndex)
    _state.update {
      it.copy(
        frames = nextFrames,
        activeFrameIndex = nextIdx,
        activeShape = null,
        isPlayingPreview = false,
        hasUnsavedChanges = true,
        undoStack = pushSnapshot(it.undoStack, snap),
        redoStack = emptyList(),
      )
    }
  }

  fun buildEditedTemplate(): PictureTemplate? {
    val st = _state.value
    val base = st.baseTemplate ?: return null
    return BlockEditorEngine.buildTemplate(
      baseTemplate = base,
      title = st.title,
      palette = st.palette,
      frames = st.frames,
      frameMs = st.frameMs,
    )
  }
}
