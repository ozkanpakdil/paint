import { describe, it, expect, beforeEach, vi } from 'vitest';
import { initCanvas, cropToSelection, cropToImageSize, selectAll } from './canvasActions.js';
import { createCanvas } from 'canvas';

describe('Canvas Actions', () => {
  let canvas, previewCanvas, ctx, previewCtx;
  let state, saveState, statusMsg;

  beforeEach(() => {
    canvas = createCanvas(100, 100);
    previewCanvas = createCanvas(100, 100);
    ctx = canvas.getContext('2d');
    previewCtx = previewCanvas.getContext('2d');
    
    state = {
      isPlacing: false,
      pendingImage: null,
      lastPastedRect: null,
    };
    
    saveState = vi.fn();
    statusMsg = { textContent: '' };
  });

  it('cropToSelection resizes canvas to pending image size', () => {
    const pendingImage = ctx.createImageData(50, 40);
    // Fill pending image with some color
    for (let i = 0; i < pendingImage.data.length; i += 4) {
      pendingImage.data[i] = 255; // Red
      pendingImage.data[i + 3] = 255; // Opaque
    }
    
    state.isPlacing = true;
    state.pendingImage = pendingImage;
    
    const initCanvasFn = vi.fn((w, h) => {
      canvas.width = w;
      canvas.height = h;
    });
    
    const result = cropToSelection(state, ctx, previewCtx, saveState, statusMsg, initCanvasFn);
    
    expect(result).toBe(true);
    expect(initCanvasFn).toHaveBeenCalledWith(50, 40);
    expect(canvas.width).toBe(50);
    expect(canvas.height).toBe(40);
    expect(state.isPlacing).toBe(false);
    expect(state.lastPastedRect).toEqual({ x: 0, y: 0, w: 50, h: 40 });
  });

  it('cropToImageSize resizes canvas to last pasted rect', () => {
    state.lastPastedRect = { x: 10, y: 10, w: 30, h: 20 };
    
    const initCanvasFn = vi.fn((w, h) => {
      canvas.width = w;
      canvas.height = h;
    });
    
    const result = cropToImageSize(state, ctx, saveState, statusMsg, initCanvasFn);
    
    expect(result).toBe(true);
    expect(initCanvasFn).toHaveBeenCalledWith(30, 20);
    expect(canvas.width).toBe(30);
    expect(canvas.height).toBe(20);
  });

  it('selectAll selects whole canvas and cuts it', () => {
    const drawSelectionFn = vi.fn();
    selectAll(state, ctx, statusMsg, drawSelectionFn);
    
    expect(state.isPlacing).toBe(true);
    expect(state.pendingX).toBe(0);
    expect(state.pendingY).toBe(0);
    expect(state.pendingImage).toBeDefined();
    expect(state.pendingImage.width).toBe(canvas.width);
    expect(state.pendingImage.height).toBe(canvas.height);
    expect(drawSelectionFn).toHaveBeenCalled();
  });
});
