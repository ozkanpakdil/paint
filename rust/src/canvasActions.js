export function initCanvas(canvas, previewCanvas, canvasWrapper, canvasW, canvasH, ctx, saveState, width = 800, height = 600) {
  canvas.width = width;
  canvas.height = height;
  previewCanvas.width = width;
  previewCanvas.height = height;
  if (canvasWrapper) {
    canvasWrapper.style.width = `${width}px`;
    canvasWrapper.style.height = `${height}px`;
  }
  
  ctx.fillStyle = "white";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  if (canvasW) canvasW.value = width;
  if (canvasH) canvasH.value = height;
  
  if (saveState) saveState();
}

export function cropToSelection(state, ctx, previewCtx, saveState, statusMsg, initCanvasFn) {
  if (state.isPlacing && state.pendingImage) {
    const w = state.pendingImage.width;
    const h = state.pendingImage.height;
    const imgData = state.pendingImage;
    
    initCanvasFn(w, h);
    ctx.putImageData(imgData, 0, 0);
    state.isPlacing = false;
    state.pendingImage = null;
    state.lastPastedRect = { x: 0, y: 0, w: w, h: h };
    previewCtx.clearRect(0, 0, previewCtx.canvas.width, previewCtx.canvas.height);
    if (saveState) saveState();
    if (statusMsg) statusMsg.textContent = `Cropped to selection: ${w}x${h}`;
    return true;
  } else {
    if (statusMsg) statusMsg.textContent = "No selection to crop to.";
    return false;
  }
}

export function cropToImageSize(state, ctx, saveState, statusMsg, initCanvasFn) {
  if (state.lastPastedRect) {
    const { x, y, w, h } = state.lastPastedRect;
    const imgData = ctx.getImageData(x, y, w, h);
    initCanvasFn(w, h);
    ctx.putImageData(imgData, 0, 0);
    state.lastPastedRect = { x: 0, y: 0, w: w, h: h };
    if (saveState) saveState();
    if (statusMsg) statusMsg.textContent = `Cropped to image size: ${w}x${h}`;
    return true;
  } else {
    if (statusMsg) statusMsg.textContent = "No recently pasted image to crop to.";
    return false;
  }
}

export function selectAll(state, ctx, statusMsg, drawSelectionFn) {
  state.pendingImage = ctx.getImageData(0, 0, ctx.canvas.width, ctx.canvas.height);
  state.pendingX = 0;
  state.pendingY = 0;
  state.isPlacing = true;
  state.isSelecting = false;
  
  // Clear the canvas (cut action)
  ctx.fillStyle = "white";
  ctx.fillRect(0, 0, ctx.canvas.width, ctx.canvas.height);
  
  if (statusMsg) statusMsg.textContent = "Selected all. Drag to move, click outside to commit.";
  if (drawSelectionFn) drawSelectionFn();
}

export function floodFill(ctx, startX, startY, fillColor, saveState) {
  const canvas = ctx.canvas;
  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
  const pixels = imageData.data;
  const width = canvas.width;
  const height = canvas.height;

  const startPos = (Math.floor(startY) * width + Math.floor(startX)) * 4;
  const startR = pixels[startPos];
  const startG = pixels[startPos + 1];
  const startB = pixels[startPos + 2];
  const startA = pixels[startPos + 3];

  // Convert fillColor to RGBA
  const dummy = document.createElement("div");
  dummy.style.color = fillColor;
  document.body.appendChild(dummy);
  const colorStr = window.getComputedStyle(dummy).color;
  document.body.removeChild(dummy);
  
  const colorMatch = colorStr.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*(\d+(?:\.\d+)?))?\)/);
  if (!colorMatch) return;
  const fillR = parseInt(colorMatch[1]);
  const fillG = parseInt(colorMatch[2]);
  const fillB = parseInt(colorMatch[3]);
  const fillA = colorMatch[4] ? Math.round(parseFloat(colorMatch[4]) * 255) : 255;

  if (startR === fillR && startG === fillG && startB === fillB && startA === fillA) {
    return;
  }

  const matchColor = (pixels, pos, r, g, b, a) => {
      return pixels[pos] === r && pixels[pos + 1] === g && pixels[pos + 2] === b && pixels[pos + 3] === a;
  };

  const stack = [[Math.floor(startX), Math.floor(startY)]];

  while (stack.length > 0) {
    let [x, y] = stack.pop();
    let pos = (y * width + x) * 4;

    while (y >= 0 && matchColor(pixels, pos, startR, startG, startB, startA)) {
      y--;
      pos -= width * 4;
    }

    pos += width * 4;
    y++;

    let reachLeft = false;
    let reachRight = false;

    while (y < height && matchColor(pixels, pos, startR, startG, startB, startA)) {
      pixels[pos] = fillR;
      pixels[pos + 1] = fillG;
      pixels[pos + 2] = fillB;
      pixels[pos + 3] = fillA;

      if (x > 0) {
        if (matchColor(pixels, pos - 4, startR, startG, startB, startA)) {
          if (!reachLeft) {
            stack.push([x - 1, y]);
            reachLeft = true;
          }
        } else if (reachLeft) {
          reachLeft = false;
        }
      }

      if (x < width - 1) {
        if (matchColor(pixels, pos + 4, startR, startG, startB, startA)) {
          if (!reachRight) {
            stack.push([x + 1, y]);
            reachRight = true;
          }
        } else if (reachRight) {
          reachRight = false;
        }
      }

      y++;
      pos += width * 4;
    }
  }

  ctx.putImageData(imageData, 0, 0);
  if (saveState) saveState();
}
