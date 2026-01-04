import { describe, it, expect, beforeEach } from 'vitest';
import { drawOval, drawRoundedRect, drawArrow, drawRect, drawPolygon, drawLine } from './drawing.js';
import { createCanvas } from 'canvas';

describe('Drawing Tools', () => {
  let canvas;
  let ctx;

  beforeEach(() => {
    canvas = createCanvas(100, 100);
    ctx = canvas.getContext('2d');
    ctx.strokeStyle = 'black';
    ctx.fillStyle = 'black';
  });

  it('drawArrow draws something on the canvas', () => {
    drawArrow(ctx, 10, 10, 50, 50, 2);
    
    const imageData = ctx.getImageData(0, 0, 100, 100);
    const data = imageData.data;
    
    let changed = false;
    for (let i = 0; i < data.length; i += 4) {
      if (data[i + 3] !== 0) { // Check alpha channel
        changed = true;
        break;
      }
    }
    expect(changed).toBe(true);
  });

  it('drawOval draws something on the canvas', () => {
    drawOval(ctx, 10, 10, 50, 50, false);
    
    const imageData = ctx.getImageData(0, 0, 100, 100);
    const data = imageData.data;
    
    let changed = false;
    for (let i = 0; i < data.length; i += 4) {
      if (data[i + 3] !== 0) {
        changed = true;
        break;
      }
    }
    expect(changed).toBe(true);
  });

  it('drawRoundedRect draws something on the canvas', () => {
    drawRoundedRect(ctx, 10, 10, 50, 50, 5, false);
    
    const imageData = ctx.getImageData(0, 0, 100, 100);
    const data = imageData.data;
    
    let changed = false;
    for (let i = 0; i < data.length; i += 4) {
      if (data[i + 3] !== 0) {
        changed = true;
        break;
      }
    }
    expect(changed).toBe(true);
  });

  it('drawRect draws something on the canvas', () => {
    drawRect(ctx, 10, 10, 50, 50, false);
    
    const imageData = ctx.getImageData(0, 0, 100, 100);
    const data = imageData.data;
    
    let changed = false;
    for (let i = 0; i < data.length; i += 4) {
      if (data[i + 3] !== 0) {
        changed = true;
        break;
      }
    }
    expect(changed).toBe(true);
  });

  it('drawPolygon draws something on the canvas', () => {
    const points = [{x: 10, y: 10}, {x: 50, y: 10}, {x: 30, y: 50}];
    drawPolygon(ctx, points, true, false);
    
    const imageData = ctx.getImageData(0, 0, 100, 100);
    const data = imageData.data;
    
    let changed = false;
    for (let i = 0; i < data.length; i += 4) {
      if (data[i + 3] !== 0) {
        changed = true;
        break;
      }
    }
    expect(changed).toBe(true);
  });

  it('drawLine draws something on the canvas', () => {
    drawLine(ctx, 10, 10, 50, 50);
    
    const imageData = ctx.getImageData(0, 0, 100, 100);
    const data = imageData.data;
    
    let changed = false;
    for (let i = 0; i < data.length; i += 4) {
      if (data[i + 3] !== 0) {
        changed = true;
        break;
      }
    }
    expect(changed).toBe(true);
  });
});
