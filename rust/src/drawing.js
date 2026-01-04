export function drawOval(context, startX, startY, endX, endY, filled) {
  context.beginPath();
  const radiusX = Math.abs(endX - startX) / 2;
  const radiusY = Math.abs(endY - startY) / 2;
  const centerX = startX + (endX - startX) / 2;
  const centerY = startY + (endY - startY) / 2;
  context.ellipse(centerX, centerY, radiusX, radiusY, 0, 0, Math.PI * 2);
  if (filled) context.fill();
  context.stroke();
}

export function drawRoundedRect(context, startX, startY, endX, endY, radius, filled) {
  const x = Math.min(startX, endX);
  const y = Math.min(startY, endY);
  const width = Math.abs(endX - startX);
  const height = Math.abs(endY - startY);
  
  if (width < 2 * radius) radius = width / 2;
  if (height < 2 * radius) radius = height / 2;
  
  context.beginPath();
  context.moveTo(x + radius, y);
  context.arcTo(x + width, y, x + width, y + height, radius);
  context.arcTo(x + width, y + height, x, y + height, radius);
  context.arcTo(x, y + height, x, y, radius);
  context.arcTo(x, y, x + width, y, radius);
  context.closePath();
  if (filled) context.fill();
  context.stroke();
}

export function drawArrow(context, fromx, fromy, tox, toy, width) {
  const headlen = 10 + parseInt(width); // length of head in pixels
  const dx = tox - fromx;
  const dy = toy - fromy;
  const angle = Math.atan2(dy, dx);
  context.beginPath();
  context.moveTo(fromx, fromy);
  context.lineTo(tox, toy);
  context.stroke();
  
  context.beginPath();
  context.moveTo(tox, toy);
  context.lineTo(tox - headlen * Math.cos(angle - Math.PI / 6), toy - headlen * Math.sin(angle - Math.PI / 6));
  context.lineTo(tox - headlen * Math.cos(angle + Math.PI / 6), toy - headlen * Math.sin(angle + Math.PI / 6));
  context.closePath();
  context.fill();
}

export function drawRect(context, startX, startY, endX, endY, filled) {
  context.beginPath();
  context.rect(startX, startY, endX - startX, endY - startY);
  if (filled) context.fill();
  context.stroke();
}

export function drawPolygon(context, points, isClosed, filled) {
  if (points.length < 1) return;
  context.beginPath();
  context.moveTo(points[0].x, points[0].y);
  for (let i = 1; i < points.length; i++) {
    context.lineTo(points[i].x, points[i].y);
  }
  if (isClosed) {
    context.closePath();
    if (filled) context.fill();
  }
  context.stroke();
}

export function drawLine(context, startX, startY, endX, endY) {
  context.beginPath();
  context.moveTo(startX, startY);
  context.lineTo(endX, endY);
  context.stroke();
}
