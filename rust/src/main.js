const { invoke } = window.__TAURI__.core;
import { drawOval, drawRoundedRect, drawArrow, drawRect, drawPolygon, drawLine } from './drawing.js';
import { initCanvas, cropToSelection, cropToImageSize, selectAll, floodFill } from './canvasActions.js';

window.addEventListener("DOMContentLoaded", () => {
  const canvas = document.querySelector("#paint-canvas");
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  const previewCanvas = document.querySelector("#preview-canvas");
  const previewCtx = previewCanvas.getContext("2d");
  const canvasWrapper = document.querySelector("#canvas-wrapper");
  
  // Initialize UI components
  const sizeSlider = document.querySelector("#size-slider");
  const opacitySlider = document.querySelector("#opacity-slider");
  const colorSwatches = document.querySelectorAll(".color-swatch");
  const toolBtns = document.querySelectorAll(".tool-btn");
  const canvasW = document.querySelector("#canvas-w");
  const canvasH = document.querySelector("#canvas-h");
  const resizeBtn = document.querySelector("#resize-btn");
  const statusMsg = document.querySelector("#status-message");
  const activeColorPreview = document.querySelector("#active-color-preview");
  const textControls = document.querySelector("#text-controls");
  const fontFamilySelect = document.querySelector("#font-family");
  const fontSizeInput = document.querySelector("#font-size");
  const opacityLabel = document.querySelector("#opacity-label");

  // File handling
  const fileNew = document.querySelector("#file-new");
  const fileOpen = document.querySelector("#file-open");
  const fileSave = document.querySelector("#file-save");
  const fileExit = document.querySelector("#file-exit");

  fileNew.addEventListener("click", () => {
    if (confirm("Clear canvas and start new drawing?")) {
      wrappedInitCanvas(canvas.width, canvas.height);
      statusMsg.textContent = "New drawing started";
    }
  });

  fileOpen.addEventListener("click", async () => {
    try {
      const { open } = window.__TAURI__.dialog;
      const { readFile } = window.__TAURI__.fs;
      
      const selected = await open({
        multiple: false,
        filters: [{
          name: 'Images',
          extensions: ['png', 'jpg', 'jpeg']
        }]
      });
      
      if (selected) {
        const fileData = await readFile(selected);
        const blob = new Blob([fileData], { type: 'image/png' });
        const url = URL.createObjectURL(blob);
        const img = new Image();
        img.onload = () => {
          wrappedInitCanvas(img.width, img.height);
          ctx.drawImage(img, 0, 0);
          state.lastPastedRect = { x: 0, y: 0, w: img.width, h: img.height };
          saveState();
          URL.revokeObjectURL(url);
          statusMsg.textContent = `Opened: ${selected}`;
        };
        img.src = url;
      }
    } catch (err) {
      console.error(err);
      statusMsg.textContent = "Error opening file";
    }
  });

  fileSave.addEventListener("click", async () => {
    try {
      const { save } = window.__TAURI__.dialog;
      const { writeFile } = window.__TAURI__.fs;
      
      const path = await save({
        filters: [{
          name: 'PNG Image',
          extensions: ['png']
        }]
      });
      
      if (path) {
        canvas.toBlob(async (blob) => {
          const buffer = await blob.arrayBuffer();
          await writeFile(path, new Uint8Array(buffer));
          statusMsg.textContent = `Saved to: ${path}`;
        }, 'image/png');
      }
    } catch (err) {
      console.error(err);
      statusMsg.textContent = "Error saving file";
    }
  });

  async function confirmExit() {
    if (confirm("Are you sure you want to exit?")) {
      try {
        const { getCurrentWindow } = window.__TAURI__.window;
        const win = getCurrentWindow();
        await win.destroy();
      } catch (err) {
        console.error("Failed to close window:", err);
        statusMsg.textContent = "Error closing window";
      }
    }
  }

  fileExit.addEventListener("click", confirmExit);

  // Edit handling
  const editUndo = document.querySelector("#edit-undo");
  const editRedo = document.querySelector("#edit-redo");
  const editClear = document.querySelector("#edit-clear");

  editUndo.addEventListener("click", undo);
  editRedo.addEventListener("click", redo);
  editClear.addEventListener("click", () => {
    ctx.fillStyle = "white";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    saveState();
    statusMsg.textContent = "Canvas cleared";
  });

  const editCopy = document.querySelector("#edit-copy");
  const editSelectAll = document.querySelector("#edit-select-all");

  editCopy.addEventListener("click", copyToClipboard);
  editSelectAll.addEventListener("click", () => {
    if (state.tool !== "move") {
      document.querySelector("#tool-move").click();
    }
    selectAll(state, ctx, statusMsg, drawSelection);
  });

  const editCropSelection = document.querySelector("#edit-crop-selection");
  const editCropImage = document.querySelector("#edit-crop-image");

  editCropSelection.addEventListener("click", () => cropToSelection(state, ctx, previewCtx, saveState, statusMsg, wrappedInitCanvas));
  editCropImage.addEventListener("click", () => cropToImageSize(state, ctx, saveState, statusMsg, wrappedInitCanvas));

  const state = {
    tool: "pencil",
    color: "black",
    size: 5,
    opacity: 0.3,
    fontFamily: "Arial",
    fontSize: 14,
    isDrawing: false,
    startX: 0,
    startY: 0,
    undoStack: [],
    redoStack: [],
    polygonPoints: [],
    selectionRect: null,
    isSelecting: false,
    isPlacing: false,
    pendingImage: null,
    pendingX: 0,
    pendingY: 0,
    dragOffsetX: 0,
    dragOffsetY: 0,
    lastPastedRect: null,
  };

  const MAX_UNDO = 25;

  function wrappedInitCanvas(width, height) {
    initCanvas(canvas, previewCanvas, canvasWrapper, canvasW, canvasH, ctx, saveState, width, height);
  }

  function saveState() {
    state.undoStack.push(ctx.getImageData(0, 0, canvas.width, canvas.height));
    if (state.undoStack.length > MAX_UNDO) {
      state.undoStack.shift();
    }
    state.redoStack = []; // Clear redo stack on new action
  }

  function undo() {
    if (state.undoStack.length > 1) {
      state.redoStack.push(state.undoStack.pop());
      const imageData = state.undoStack[state.undoStack.length - 1];
      ctx.putImageData(imageData, 0, 0);
      statusMsg.textContent = "Undo";
    }
  }

  function redo() {
    if (state.redoStack.length > 0) {
      const imageData = state.redoStack.pop();
      state.undoStack.push(imageData);
      ctx.putImageData(imageData, 0, 0);
      statusMsg.textContent = "Redo";
    }
  }

  // Keyboard shortcuts
  window.addEventListener("keydown", (e) => {
    if (e.ctrlKey || e.metaKey) {
      if (e.key === "z") {
        if (e.shiftKey) redo();
        else undo();
      } else if (e.key === "y") {
        redo();
      } else if (e.key === "n") {
        e.preventDefault();
        fileNew.click();
      } else if (e.key === "o") {
        e.preventDefault();
        fileOpen.click();
      } else if (e.key === "s") {
        e.preventDefault();
        fileSave.click();
      } else if (e.key === "c") {
        e.preventDefault();
        copyToClipboard();
      } else if (e.key === "a") {
        e.preventDefault();
        // Switch to move tool if not already
        if (state.tool !== "move") {
          document.querySelector("#tool-move").click();
        }
        selectAll(state, ctx, statusMsg, drawSelection);
      } else if (e.key === "m") {
        e.preventDefault();
        document.querySelector("#tool-move").click();
      } else if (e.key === "q") {
        e.preventDefault();
        confirmExit();
      }
    }
  });

  async function copyToClipboard() {
    let sourceCanvas = canvas;
    if (state.isPlacing && state.pendingImage) {
      const tempCanvas = document.createElement("canvas");
      tempCanvas.width = state.pendingImage.width;
      tempCanvas.height = state.pendingImage.height;
      tempCanvas.getContext("2d").putImageData(state.pendingImage, 0, 0);
      sourceCanvas = tempCanvas;
    }
    
    sourceCanvas.toBlob(async (blob) => {
      try {
        await navigator.clipboard.write([
          new ClipboardItem({ 'image/png': blob })
        ]);
        statusMsg.textContent = "Copied to clipboard";
      } catch (err) {
        console.error("Failed to copy: ", err);
        statusMsg.textContent = "Error copying to clipboard";
      }
    });
  }

  wrappedInitCanvas();

  // Menu item helpers
  document.querySelector("#help-shortcuts").addEventListener("click", () => {
    alert("Shortcuts:\n- New: Ctrl+N\n- Open: Ctrl+O\n- Save: Ctrl+S\n- Copy: Ctrl+C\n- Select All: Ctrl+A\n- Move Tool: Ctrl+M\n- Undo: Ctrl+Z\n- Redo: Ctrl+Y or Ctrl+Shift+Z\n- Exit: Ctrl+Q");
  });

  const menuTools = [
    "pencil", "line", "rect", "oval", "rounded-rect", "polygon", 
    "rect-filled", "oval-filled", "rounded-rect-filled", "polygon-filled", 
    "bucket", "move"
  ];
  menuTools.forEach(tool => {
    const el = document.querySelector(`#tools-${tool}`);
    if (el) {
      el.addEventListener("click", () => {
        const btn = document.querySelector(`#tool-${tool}`);
        if (btn) btn.click();
      });
    }
  });
  
  document.querySelector("#tools-color").addEventListener("click", () => {
    statusMsg.textContent = "Click a color in the palette below.";
  });

  const TOOL_ICONS = {
    pencil: "pencil.png",
    highlighter: "highlight.png",
    eraser: "eraser.png",
    text: "text.png",
    bucket: "bucket.png",
    move: "move.png",
    arrow: "arrow.png",
    rect: "rectangle.png",
    oval: "oval.png",
    "rounded-rect": "rectangle.png",
    polygon: "polygon.png",
    "rect-filled": "rectangle_fill.png",
    "oval-filled": "oval_fill.png",
    "rounded-rect-filled": "rectangle_fill.png",
    "polygon-filled": "polygon_fill.png",
    line: "line-tool.png",
  };

  // Tool selection logic
  toolBtns.forEach(btn => {
    btn.addEventListener("click", () => {
      // Commit pending actions when switching tools
      if (state.tool === "move" && state.isPlacing) {
        commitPlacement();
      }
      if ((state.tool === "polygon" || state.tool === "polygon-filled") && state.polygonPoints.length > 0) {
        finishPolygon();
      }

      toolBtns.forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      const previousTool = state.tool;
      state.tool = btn.id.replace("tool-", "");
      statusMsg.textContent = `Tool: ${state.tool.charAt(0).toUpperCase() + state.tool.slice(1)}`;
      
      // Update ribbon controls visibility
      if (state.tool === "text") {
        textControls.style.display = "flex";
      } else {
        textControls.style.display = "none";
      }

      if (state.tool === "highlighter") {
        opacityLabel.style.opacity = "1";
        opacitySlider.style.opacity = "1";
        opacitySlider.disabled = false;
      } else {
        // Reset opacity to 100% when switching away from highlighter
        if (previousTool === "highlighter") {
          state.opacity = 1.0;
          opacitySlider.value = 100;
        }
        opacityLabel.style.opacity = "0.5";
        opacitySlider.style.opacity = "0.5";
        opacitySlider.disabled = true;
      }

      updateCursor();
    });
  });

  function updateCursor() {
    if (state.tool === "move") {
      canvasWrapper.style.cursor = "move";
    } else if (state.tool === "text") {
      canvasWrapper.style.cursor = "text";
    } else if (TOOL_ICONS[state.tool]) {
      // Use custom cursor if icon exists, fallback to crosshair
      canvasWrapper.style.cursor = `url(assets/images/${TOOL_ICONS[state.tool]}) 0 24, crosshair`;
    } else {
      canvasWrapper.style.cursor = "crosshair";
    }
  }

  function finishPolygon() {
    if (state.polygonPoints.length > 1) {
      previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
      ctx.lineWidth = state.size;
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
      ctx.strokeStyle = state.color;
      ctx.fillStyle = state.color;
      
      drawPolygon(ctx, state.polygonPoints, true, state.tool === "polygon-filled");
      saveState();
    }
    state.polygonPoints = [];
    state.isDrawing = false;
    previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
  }

  canvasWrapper.addEventListener("dblclick", (e) => {
    if (state.tool === "polygon" || state.tool === "polygon-filled") {
      finishPolygon();
    }
  });

  // Color selection logic
  colorSwatches.forEach(swatch => {
    swatch.addEventListener("click", () => {
      colorSwatches.forEach(s => s.classList.remove("active"));
      swatch.classList.add("active");
      state.color = swatch.style.backgroundColor;
      activeColorPreview.style.backgroundColor = state.color;
    });
  });

  // Sliders logic
  sizeSlider.addEventListener("input", (e) => {
    state.size = e.target.value;
  });

  opacitySlider.addEventListener("input", (e) => {
    state.opacity = e.target.value / 100;
  });

  fontFamilySelect.addEventListener("change", (e) => {
    state.fontFamily = e.target.value;
  });

  fontSizeInput.addEventListener("input", (e) => {
    state.fontSize = e.target.value;
  });

  // Resize logic
  resizeBtn.addEventListener("click", () => {
    const w = parseInt(canvasW.value);
    const h = parseInt(canvasH.value);
    
    // Preserve the image
    const tempImage = ctx.getImageData(0, 0, canvas.width, canvas.height);
    
    wrappedInitCanvas(w, h);
    ctx.putImageData(tempImage, 0, 0);
    
    saveState();
    statusMsg.textContent = `Resized to ${w}x${h}`;
  });

  // Drawing logic
  function getCoordinates(e) {
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    return {
      x: (e.clientX - rect.left) * scaleX,
      y: (e.clientY - rect.top) * scaleY
    };
  }

  function startDrawing(e) {
    const { x, y } = getCoordinates(e);

    if (state.tool === "move") {
      if (state.isPlacing) {
        // Check if click is inside pending image
        if (x >= state.pendingX && x <= state.pendingX + state.pendingImage.width &&
            y >= state.pendingY && y <= state.pendingY + state.pendingImage.height) {
          state.isDrawing = true;
          state.dragOffsetX = x - state.pendingX;
          state.dragOffsetY = y - state.pendingY;
        } else {
          commitPlacement();
        }
        return;
      } else {
        state.isSelecting = true;
        state.isDrawing = true;
        state.startX = x;
        state.startY = y;
        return;
      }
    }

    if (state.tool === "polygon" || state.tool === "polygon-filled") {
      if (state.polygonPoints.length === 0) {
        state.isDrawing = true;
        state.polygonPoints.push({ x, y });
        state.startX = x;
        state.startY = y;
      } else {
        state.polygonPoints.push({ x, y });
      }
      return;
    }

    state.isDrawing = true;
    state.startX = x;
    state.startY = y;
    state.lastX = x;
    state.lastY = y;
    
    if (state.tool === "pencil" || state.tool === "highlighter" || state.tool === "eraser") {
        ctx.beginPath();
        ctx.moveTo(x, y);
    } else if (state.tool === "bucket") {
        floodFill(ctx, x, y, state.color, saveState);
    } else if (state.tool === "text") {
        showTextEditor(x, y);
    }
  }

  function commitPlacement() {
    if (state.isPlacing && state.pendingImage) {
      ctx.putImageData(state.pendingImage, state.pendingX, state.pendingY);
      state.lastPastedRect = { 
        x: state.pendingX, 
        y: state.pendingY, 
        w: state.pendingImage.width, 
        h: state.pendingImage.height 
      };
      state.isPlacing = false;
      state.pendingImage = null;
      previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
      saveState();
      statusMsg.textContent = "Move committed";
    }
  }

  function showTextEditor(x, y) {
    if (document.querySelector("#text-editor")) return;

    const input = document.createElement("input");
    input.id = "text-editor";
    input.type = "text";
    input.style.position = "absolute";
    input.style.left = `${x}px`;
    input.style.top = `${y}px`;
    input.style.font = `${state.fontSize}px ${state.fontFamily}`;
    input.style.color = state.color;
    input.style.background = "rgba(255, 255, 255, 0.9)";
    input.style.border = "1px dashed #666";
    input.style.outline = "none";
    input.style.padding = "2px";
    input.style.margin = "0";
    input.style.zIndex = "100";
    input.style.minWidth = "100px";

    canvas.parentElement.appendChild(input);
    input.focus();

    const commitText = () => {
      const text = input.value.trim();
      if (text) {
        ctx.font = `${state.fontSize}px ${state.fontFamily}`;
        ctx.fillStyle = state.color;
        ctx.textBaseline = "top";
        ctx.fillText(text, x, y);
        saveState();
      }
      input.remove();
    };

    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        commitText();
      } else if (e.key === "Escape") {
        e.preventDefault();
        input.remove();
      }
    });

    input.addEventListener("blur", commitText);
  }

  function stopDrawing() {
    if (!state.isDrawing) return;
    state.isDrawing = false;
    
    if (state.tool === "move") {
      if (state.isSelecting) {
        state.isSelecting = false;
        const x = Math.min(state.startX, state.lastX);
        const y = Math.min(state.startY, state.lastY);
        const w = Math.abs(state.startX - state.lastX);
        const h = Math.abs(state.startY - state.lastY);
        
        if (w > 0 && h > 0) {
          state.pendingImage = ctx.getImageData(x, y, w, h);
          state.pendingX = x;
          state.pendingY = y;
          state.isPlacing = true;
          
          // Clear the selected area in main canvas
          ctx.fillStyle = "white";
          ctx.fillRect(x, y, w, h);
          
          statusMsg.textContent = "Selection ready. Drag to move, click outside to commit.";
          drawSelection();
        } else {
          previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
        }
      }
      return;
    }

    if (state.tool === "polygon" || state.tool === "polygon-filled") {
      // Don't stop drawing until double click or manual finish
      state.isDrawing = true; 
      return;
    }

    // For shapes, commit the preview to the main canvas
    const shapes = ["rect", "rect-filled", "rounded-rect", "rounded-rect-filled", "oval", "oval-filled", "line", "arrow"];
    if (shapes.includes(state.tool)) {
      ctx.drawImage(previewCanvas, 0, 0);
      previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
    }
    
    saveState();
  }

  function drawSelection() {
    if (state.isPlacing && state.pendingImage) {
      previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
      // Create a temporary canvas to draw the ImageData
      const tempCanvas = document.createElement("canvas");
      tempCanvas.width = state.pendingImage.width;
      tempCanvas.height = state.pendingImage.height;
      tempCanvas.getContext("2d").putImageData(state.pendingImage, 0, 0);
      
      previewCtx.drawImage(tempCanvas, state.pendingX, state.pendingY);
      
      // Draw dashed border
      previewCtx.setLineDash([5, 5]);
      previewCtx.strokeStyle = "#000";
      previewCtx.lineWidth = 1;
      previewCtx.strokeRect(state.pendingX, state.pendingY, state.pendingImage.width, state.pendingImage.height);
      previewCtx.setLineDash([]);
    }
  }

  function cropToSelection() {
    if (state.isPlacing && state.pendingImage) {
      // Crop to the image currently being moved
      const w = state.pendingImage.width;
      const h = state.pendingImage.height;
      const imgData = state.pendingImage;
      
      initCanvas(w, h);
      ctx.putImageData(imgData, 0, 0);
      state.isPlacing = false;
      state.pendingImage = null;
      state.lastPastedRect = { x: 0, y: 0, w: w, h: h };
      previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
      saveState();
      statusMsg.textContent = `Cropped to selection: ${w}x${h}`;
    } else {
      statusMsg.textContent = "No selection to crop to.";
    }
  }

  function cropToImageSize() {
    if (state.lastPastedRect) {
      const { x, y, w, h } = state.lastPastedRect;
      const imgData = ctx.getImageData(x, y, w, h);
      initCanvas(w, h);
      ctx.putImageData(imgData, 0, 0);
      state.lastPastedRect = { x: 0, y: 0, w: w, h: h };
      saveState();
      statusMsg.textContent = `Cropped to image size: ${w}x${h}`;
    } else {
      statusMsg.textContent = "No recently pasted image to crop to.";
    }
  }

  function draw(e) {
    const { x, y } = getCoordinates(e);

    if (state.tool === "move") {
      if (state.isDrawing) {
        if (state.isSelecting) {
          previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
          previewCtx.setLineDash([5, 5]);
          previewCtx.strokeStyle = "#000";
          previewCtx.lineWidth = 1;
          previewCtx.strokeRect(state.startX, state.startY, x - state.startX, y - state.startY);
          previewCtx.setLineDash([]);
        } else if (state.isPlacing) {
          state.pendingX = x - state.dragOffsetX;
          state.pendingY = y - state.dragOffsetY;
          drawSelection();
        }
      }
      state.lastX = x;
      state.lastY = y;
      return;
    }

    if (!state.isDrawing) return;
    
    const shapes = ["rect", "rect-filled", "rounded-rect", "rounded-rect-filled", "oval", "oval-filled", "line", "arrow", "polygon", "polygon-filled"];
    const targetCtx = shapes.includes(state.tool) ? previewCtx : ctx;
    
    if (targetCtx === previewCtx) {
      previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
    }

    targetCtx.lineWidth = state.size;
    targetCtx.lineCap = "round";
    targetCtx.lineJoin = "round";
    targetCtx.strokeStyle = state.color;
    targetCtx.fillStyle = state.color;
    
    switch (state.tool) {
      case "pencil":
        ctx.globalAlpha = 1.0;
        ctx.globalCompositeOperation = "source-over";
        ctx.lineTo(x, y);
        ctx.stroke();
        break;
      case "highlighter":
        ctx.globalAlpha = state.opacity;
        ctx.globalCompositeOperation = "source-over";
        ctx.lineTo(x, y);
        ctx.stroke();
        break;
      case "eraser":
        ctx.globalAlpha = 1.0;
        ctx.globalCompositeOperation = "destination-out";
        ctx.lineTo(x, y);
        ctx.stroke();
        break;
      case "line":
        drawLine(previewCtx, state.startX, state.startY, x, y);
        break;
      case "rect":
        drawRect(previewCtx, state.startX, state.startY, x, y, false);
        break;
      case "rect-filled":
        drawRect(previewCtx, state.startX, state.startY, x, y, true);
        break;
      case "rounded-rect":
        drawRoundedRect(previewCtx, state.startX, state.startY, x, y, 10, false);
        break;
      case "rounded-rect-filled":
        drawRoundedRect(previewCtx, state.startX, state.startY, x, y, 10, true);
        break;
      case "oval":
        drawOval(previewCtx, state.startX, state.startY, x, y, false);
        break;
      case "oval-filled":
        drawOval(previewCtx, state.startX, state.startY, x, y, true);
        break;
      case "polygon":
      case "polygon-filled":
        if (state.polygonPoints.length > 0) {
          drawPolygon(previewCtx, [...state.polygonPoints, { x, y }], false, false);
        }
        break;
      case "arrow":
        drawArrow(previewCtx, state.startX, state.startY, x, y, state.size);
        break;
      case "bucket":
        break;
    }

    state.lastX = x;
    state.lastY = y;
  }

  canvasWrapper.addEventListener("mousedown", startDrawing);
  canvasWrapper.addEventListener("mousemove", draw);
  window.addEventListener("mouseup", stopDrawing);

  // Drag and Drop support
  canvasWrapper.addEventListener("dragover", (e) => {
    e.preventDefault();
    canvasWrapper.classList.add("drag-over");
  });

  canvasWrapper.addEventListener("dragleave", () => {
    canvasWrapper.classList.remove("drag-over");
  });

  canvasWrapper.addEventListener("drop", async (e) => {
    e.preventDefault();
    canvasWrapper.classList.remove("drag-over");
    
    const files = e.dataTransfer.files;
    if (files.length > 0) {
      const file = files[0];
      if (file.type.startsWith("image/")) {
        loadImageToPlacement(file);
      }
    }
  });

  // Paste support
  window.addEventListener("paste", async (e) => {
    e.preventDefault();
    const items = e.clipboardData?.items;
    if (!items) return;
    
    for (let i = 0; i < items.length; i++) {
      if (items[i].type.indexOf("image") !== -1) {
        const blob = items[i].getAsFile();
        if (blob) {
          loadImageToPlacement(blob);
        }
        break;
      }
    }
  });

  function loadImageToPlacement(fileOrBlob) {
    const reader = new FileReader();
    reader.onload = (event) => {
      const img = new Image();
      img.onload = () => {
        const tempCanvas = document.createElement("canvas");
        tempCanvas.width = img.width;
        tempCanvas.height = img.height;
        const tCtx = tempCanvas.getContext("2d");
        tCtx.drawImage(img, 0, 0);
        
        state.pendingImage = tCtx.getImageData(0, 0, img.width, img.height);
        state.pendingX = 0;
        state.pendingY = 0;
        state.isPlacing = true;
        
        // Switch to move tool
        toolBtns.forEach(btn => {
          btn.classList.remove("active");
          if (btn.id === "tool-move") btn.classList.add("active");
        });
        state.tool = "move";
        canvasWrapper.style.cursor = "move";
        
        drawSelection();
        statusMsg.textContent = "Image loaded. Drag to move, click outside to commit.";
      };
      img.src = event.target.result;
    };
    reader.readAsDataURL(fileOrBlob);
  }

  console.log("Paint app initialized with modular tools");
});
