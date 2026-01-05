// Paint application in Rust using egui
// Port of the Java Swing paint application

use eframe::egui;
use egui::{Color32, ColorImage, Key, Pos2, Rect, TextureHandle, Vec2, ViewportCommand};
use egui_extras::RetainedImage;
use arboard::{Clipboard, ImageData};
use std::borrow::Cow;
use image::{GenericImage, Rgba, RgbaImage};

#[derive(Clone, Copy, Debug, PartialEq)]
enum Tool {
    Pencil,
    Line,
    Rectangle,
    Oval,
    RoundedRect,
    Eraser,
    Text,
    RectangleFilled,
    OvalFilled,
    RoundedRectFilled,
    Bucket,
    Move,
    Highlighter,
    Arrow,
}

const PALETTE: [Color32; 13] = [
    Color32::BLACK,
    Color32::from_rgb(0, 0, 255),
    Color32::from_rgb(0, 255, 255),
    Color32::from_rgb(64, 64, 64),
    Color32::from_rgb(128, 128, 128),
    Color32::from_rgb(0, 128, 0),
    Color32::from_rgb(211, 211, 211),
    Color32::from_rgb(255, 0, 255),
    Color32::from_rgb(255, 165, 0),
    Color32::from_rgb(255, 192, 203),
    Color32::from_rgb(255, 0, 0),
    Color32::WHITE,
    Color32::from_rgb(255, 255, 0),
];

struct PaintApp {
    selected_tool: Tool,
    stroke_size: f32,
    selected_color: Color32,
    highlighter_opacity: f32,
    canvas_size: Vec2,
    status_message: String,
    
    // Drawing state
    image: RgbaImage,
    texture: Option<TextureHandle>,
    last_mouse_pos: Option<Pos2>,
    drag_start_pos: Option<Pos2>,
    preview_image: Option<RgbaImage>,
    
    // History for Undo/Redo
    undo_stack: Vec<RgbaImage>,
    redo_stack: Vec<RgbaImage>,
    
    // Selection state for Move tool
    selection_rect: Option<Rect>,
    selection_image: Option<RgbaImage>,
    selection_pos: Option<Pos2>,
    clipboard_image: Option<RgbaImage>,
    last_pasted_rect: Option<Rect>,

    // Text tool state
    text_input: String,

    // UI state
    show_about: bool,
    show_exit_confirm: bool,

    // Cached UI resources
    tool_icons: Vec<(Tool, RetainedImage)>,
}

impl Default for PaintApp {
    fn default() -> Self {
        let width = 800;
        let height = 600;
        let image = RgbaImage::from_pixel(width, height, Rgba([255, 255, 255, 255]));
        
        Self {
            selected_tool: Tool::Pencil,
            stroke_size: 2.0,
            selected_color: Color32::BLACK,
            highlighter_opacity: 0.3, // 30%
            canvas_size: Vec2::new(width as f32, height as f32),
            status_message: "Ready".to_string(),
            image,
            texture: None,
            last_mouse_pos: None,
            drag_start_pos: None,
            preview_image: None,
            undo_stack: Vec::new(),
            redo_stack: Vec::new(),
            selection_rect: None,
            selection_image: None,
            selection_pos: None,
            clipboard_image: None,
            last_pasted_rect: None,
            text_input: String::new(),
            show_about: false,
            show_exit_confirm: false,
            tool_icons: Vec::new(),
        }
    }
}

impl PaintApp {
    fn save_undo_snapshot(&mut self) {
        self.undo_stack.push(self.image.clone());
        if self.undo_stack.len() > 25 {
            self.undo_stack.remove(0);
        }
        self.redo_stack.clear();
    }

    fn undo(&mut self) {
        if let Some(prev_image) = self.undo_stack.pop() {
            self.redo_stack.push(self.image.clone());
            self.image = prev_image;
        }
    }

    fn redo(&mut self) {
        if let Some(next_image) = self.redo_stack.pop() {
            self.undo_stack.push(self.image.clone());
            self.image = next_image;
        }
    }

    fn new_canvas(&mut self) {
        self.save_undo_snapshot();
        let width = 800u32;
        let height = 600u32;
        self.image = RgbaImage::from_pixel(width, height, Rgba([255, 255, 255, 255]));
        self.canvas_size = Vec2::new(width as f32, height as f32);
        self.preview_image = None;
        self.selection_rect = None;
        self.selection_image = None;
        self.selection_pos = None;
        self.last_pasted_rect = None;
        self.status_message = "New canvas".to_string();
    }

    fn clear_canvas(&mut self) {
        self.save_undo_snapshot();
        let width = self.canvas_size.x.max(1.0) as u32;
        let height = self.canvas_size.y.max(1.0) as u32;
        self.image = RgbaImage::from_pixel(width, height, Rgba([255, 255, 255, 255]));
        self.preview_image = None;
        self.selection_rect = None;
        self.selection_image = None;
        self.selection_pos = None;
        self.last_pasted_rect = None;
        self.status_message = "Canvas cleared".to_string();
    }

    fn resize_canvas(&mut self, new_w: u32, new_h: u32) {
        let new_w = new_w.max(1);
        let new_h = new_h.max(1);
        if new_w == self.image.width() && new_h == self.image.height() {
            return;
        }

        self.save_undo_snapshot();

        let mut resized = RgbaImage::from_pixel(new_w, new_h, Rgba([255, 255, 255, 255]));
        let copy_w = new_w.min(self.image.width());
        let copy_h = new_h.min(self.image.height());
        for y in 0..copy_h {
            for x in 0..copy_w {
                let px = *self.image.get_pixel(x, y);
                resized.put_pixel(x, y, px);
            }
        }

        self.image = resized;
        self.canvas_size = Vec2::new(new_w as f32, new_h as f32);
        self.preview_image = None;
        self.selection_rect = None;
        self.selection_image = None;
        self.selection_pos = None;
        self.last_pasted_rect = None;
        self.status_message = format!("Canvas resized to {} x {}", new_w, new_h);
    }

    fn crop_to_selection(&mut self) {
        if let Some(rect) = self.selection_rect {
            let x_min = rect.min.x.max(0.0) as u32;
            let y_min = rect.min.y.max(0.0) as u32;
            let x_max = rect.max.x.min(self.image.width() as f32) as u32;
            let y_max = rect.max.y.min(self.image.height() as f32) as u32;

            if x_max > x_min && y_max > y_min {
                self.save_undo_snapshot();
                let width = x_max - x_min;
                let height = y_max - y_min;
                let mut new_img = RgbaImage::new(width, height);
                for y in 0..height {
                    for x in 0..width {
                        let px = self.image.get_pixel(x_min + x, y_min + y);
                        new_img.put_pixel(x, y, *px);
                    }
                }
                self.image = new_img;
                self.canvas_size = Vec2::new(width as f32, height as f32);
                self.selection_rect = None;
                self.selection_image = None;
                self.selection_pos = None;
                self.status_message = "Cropped to selection".to_string();
            }
        }
    }

    fn crop_to_image_size(&mut self) {
        if let Some(rect) = self.last_pasted_rect {
            let x_min = rect.min.x.max(0.0) as u32;
            let y_min = rect.min.y.max(0.0) as u32;
            let x_max = rect.max.x.min(self.image.width() as f32) as u32;
            let y_max = rect.max.y.min(self.image.height() as f32) as u32;

            if x_max > x_min && y_max > y_min {
                self.save_undo_snapshot();
                let width = x_max - x_min;
                let height = y_max - y_min;
                let mut new_img = RgbaImage::new(width, height);
                for y in 0..height {
                    for x in 0..width {
                        let px = self.image.get_pixel(x_min + x, y_min + y);
                        new_img.put_pixel(x, y, *px);
                    }
                }
                self.image = new_img;
                self.canvas_size = Vec2::new(width as f32, height as f32);
                self.selection_rect = None;
                self.selection_image = None;
                self.selection_pos = None;
                self.status_message = "Cropped to image size".to_string();
            }
        } else {
            self.status_message = "No pasted image to crop".to_string();
        }
    }

    fn to_clipboard_image(img: &RgbaImage) -> ImageData<'static> {
        let mut data = Vec::with_capacity((img.width() * img.height() * 4) as usize);
        for pixel in img.pixels() {
            // arboard expects BGRA order
            data.push(pixel[2]);
            data.push(pixel[1]);
            data.push(pixel[0]);
            data.push(pixel[3]);
        }
        ImageData {
            width: img.width() as usize,
            height: img.height() as usize,
            bytes: Cow::Owned(data),
        }
    }

    fn copy_image_to_system_clipboard(&mut self, img: &RgbaImage) {
        if img.width() == 0 || img.height() == 0 {
            return;
        }
        if let Ok(mut cb) = Clipboard::new() {
            let _ = cb.set_image(Self::to_clipboard_image(img));
        }
    }

    fn image_from_system_clipboard(&mut self) -> Option<RgbaImage> {
        let mut cb = Clipboard::new().ok()?;
        let img = cb.get_image().ok()?;
        let w: u32 = img.width.try_into().ok()?;
        let h: u32 = img.height.try_into().ok()?;
        if img.bytes.len() < (w * h * 4) as usize {
            return None;
        }
        let mut out = RgbaImage::new(w, h);
        for (pixel, chunk) in out.pixels_mut().zip(img.bytes.chunks_exact(4)) {
            // Convert BGRA to RGBA
            *pixel = Rgba([chunk[2], chunk[1], chunk[0], chunk[3]]);
        }
        Some(out)
    }

    fn copy_selection(&mut self) {
        let mut copied: Option<RgbaImage> = None;

        if let Some(sel_img) = &self.selection_image {
            copied = Some(sel_img.clone());
        } else if let Some(rect) = self.selection_rect {
            let x_min = rect.min.x.max(0.0) as u32;
            let y_min = rect.min.y.max(0.0) as u32;
            let x_max = rect.max.x.min(self.image.width() as f32) as u32;
            let y_max = rect.max.y.min(self.image.height() as f32) as u32;
            if x_max > x_min && y_max > y_min {
                let width = x_max - x_min;
                let height = y_max - y_min;
                let mut sel = RgbaImage::new(width, height);
                for y in 0..height {
                    for x in 0..width {
                        let px = self.image.get_pixel(x_min + x, y_min + y);
                        sel.put_pixel(x, y, *px);
                    }
                }
                copied = Some(sel);
            }
        }

        if copied.is_none() {
            copied = Some(self.image.clone());
        }

        if let Some(img) = copied {
            self.clipboard_image = Some(img.clone());
            self.copy_image_to_system_clipboard(&img);
            self.status_message = "Copied to clipboard".to_string();
        }
    }

    fn paste_clipboard(&mut self) {
        let from_system = self.image_from_system_clipboard();
        let img = from_system.or_else(|| self.clipboard_image.clone());

        if let Some(img) = img {
            let size = Vec2::new(img.width() as f32, img.height() as f32);
            self.selection_pos = Some(Pos2::new(0.0, 0.0));
            self.selection_rect = Some(Rect::from_min_size(Pos2::ZERO, size));
            self.selection_image = Some(img.clone());
            self.clipboard_image = Some(img);
            self.last_pasted_rect = Some(Rect::from_min_size(Pos2::ZERO, size));
            self.selected_tool = Tool::Move;
            self.status_message = "Pasted selection".to_string();
        } else {
            self.status_message = "Clipboard is empty".to_string();
        }
    }

    fn select_all(&mut self) {
        let width = self.image.width();
        let height = self.image.height();
        if width == 0 || height == 0 {
            return;
        }
        self.save_undo_snapshot();
        let mut sel = RgbaImage::new(width, height);
        let _ = sel.copy_from(&self.image, 0, 0);
        self.selection_image = Some(sel);
        self.selection_pos = Some(Pos2::new(0.0, 0.0));
        self.selection_rect = Some(Rect::from_min_size(Pos2::ZERO, Vec2::new(width as f32, height as f32)));
        self.image = RgbaImage::from_pixel(width, height, Rgba([255, 255, 255, 255]));
        self.selected_tool = Tool::Move;
        self.status_message = "Selected all".to_string();
    }

    fn ensure_tool_icons(&mut self) {
        if !self.tool_icons.is_empty() {
            return;
        }

        let icons: &[(Tool, &'static [u8], &'static str)] = &[
            (Tool::Pencil, include_bytes!("../assets/pencil.png"), "Pencil"),
            (Tool::Line, include_bytes!("../assets/line-tool.png"), "Line"),
            (Tool::Rectangle, include_bytes!("../assets/rectangle.png"), "Rectangle"),
            (Tool::RectangleFilled, include_bytes!("../assets/rectangle_fill.png"), "Rectangle (Filled)"),
            (Tool::Oval, include_bytes!("../assets/oval.png"), "Oval"),
            (Tool::OvalFilled, include_bytes!("../assets/oval_fill.png"), "Oval (Filled)"),
            (Tool::RoundedRect, include_bytes!("../assets/rectangle.png"), "Rounded Rect"),
            (Tool::RoundedRectFilled, include_bytes!("../assets/rectangle_fill.png"), "Rounded Rect (Filled)"),
            (Tool::Eraser, include_bytes!("../assets/eraser.png"), "Eraser"),
            (Tool::Text, include_bytes!("../assets/text.png"), "Text"),
            (Tool::Bucket, include_bytes!("../assets/bucket.png"), "Bucket"),
            (Tool::Move, include_bytes!("../assets/arrow.png"), "Move"),
            (Tool::Highlighter, include_bytes!("../assets/highlight.png"), "Highlighter"),
            (Tool::Arrow, include_bytes!("../assets/arrow.png"), "Arrow"),
        ];

        for (tool, bytes, name) in icons {
            if let Ok(img) = RetainedImage::from_image_bytes(*name, *bytes) {
                self.tool_icons.push((*tool, img));
            }
        }
    }

    fn icon_for(&self, tool: Tool) -> Option<&RetainedImage> {
        self.tool_icons
            .iter()
            .find(|(t, _)| *t == tool)
            .map(|(_, img)| img)
    }

    fn handle_shortcuts(&mut self, ctx: &egui::Context) {
        // Avoid stealing shortcuts while typing text
        if ctx.wants_keyboard_input() {
            return;
        }

        ctx.input(|input| {
            let command = input.modifiers.command;
            let shift = input.modifiers.shift;

            if command && input.key_pressed(Key::Z) {
                if shift {
                    self.redo();
                } else {
                    self.undo();
                }
            }

            if command && (input.key_pressed(Key::Y) || (shift && input.key_pressed(Key::Z))) {
                self.redo();
            }

            if command && input.key_pressed(Key::N) {
                self.new_canvas();
            }

            if command && input.key_pressed(Key::S) {
                if let Some(path) = rfd::FileDialog::new()
                    .add_filter("PNG Image", &["png"])
                    .save_file()
                {
                    self.save_image(path);
                }
            }

            if command && input.key_pressed(Key::O) {
                if let Some(path) = rfd::FileDialog::new()
                    .add_filter("Images", &["png", "jpg", "jpeg", "bmp"])
                    .pick_file()
                {
                    self.open_image(path);
                }
            }

            if command && input.key_pressed(Key::M) {
                self.selected_tool = Tool::Move;
                self.status_message = "Move tool".to_string();
            }

            if command && input.key_pressed(Key::A) {
                self.select_all();
            }

            if command && input.key_pressed(Key::C) {
                self.copy_selection();
            }

            if command && input.key_pressed(Key::V) {
                self.paste_clipboard();
            }

            if command && shift && input.key_pressed(Key::X) {
                self.crop_to_selection();
            }
        });
    }

    fn open_image(&mut self, path: std::path::PathBuf) {
        if let Ok(img) = image::open(path) {
            self.save_undo_snapshot();
            let rgba = img.to_rgba8();
            self.image = rgba;
            self.canvas_size = Vec2::new(self.image.width() as f32, self.image.height() as f32);
            self.preview_image = None;
            self.selection_rect = None;
            self.selection_image = None;
            self.selection_pos = None;
            self.last_pasted_rect = None;
            self.status_message = "Image opened".to_string();
        } else {
            self.status_message = "Failed to open image".to_string();
        }
    }

    fn save_image(&mut self, path: std::path::PathBuf) {
        if self.image.save(path).is_ok() {
            self.status_message = "Image saved".to_string();
        } else {
            self.status_message = "Failed to save image".to_string();
        }
    }

    fn commit_selection(&mut self) {
        if let (Some(sel_img), Some(pos)) = (&self.selection_image, self.selection_pos) {
            let x_start = pos.x as i32;
            let y_start = pos.y as i32;
            for y in 0..sel_img.height() {
                for x in 0..sel_img.width() {
                    let px = x_start + x as i32;
                    let py = y_start + y as i32;
                    if px >= 0 && px < self.image.width() as i32 && py >= 0 && py < self.image.height() as i32 {
                        let pixel = sel_img.get_pixel(x, y);
                        // Only draw non-transparent pixels if we wanted, but selection usually has a background
                        self.image.put_pixel(px as u32, py as u32, *pixel);
                    }
                }
            }
            self.last_pasted_rect = Some(Rect::from_min_size(pos, Vec2::new(sel_img.width() as f32, sel_img.height() as f32)));
        }
        self.selection_image = None;
        self.selection_rect = None;
        self.selection_pos = None;
    }

    fn draw_line_on_image(image: &mut RgbaImage, start: Pos2, end: Pos2, color: Color32, size: f32) {
        let color_rgba = Rgba([color.r(), color.g(), color.b(), color.a()]);
        
        let x0 = start.x as i32;
        let y0 = start.y as i32;
        let x1 = end.x as i32;
        let y1 = end.y as i32;
        
        let dx = (x1 - x0).abs();
        let dy = -(y1 - y0).abs();
        let sx = if x0 < x1 { 1 } else { -1 };
        let sy = if y0 < y1 { 1 } else { -1 };
        let mut err = dx + dy;
        
        let mut x = x0;
        let mut y = y0;
        
        loop {
            let radius = (size / 2.0) as i32;
            for dy_brush in -radius..=radius {
                for dx_brush in -radius..=radius {
                    let px = x + dx_brush;
                    let py = y + dy_brush;
                    if px >= 0 && px < image.width() as i32 && py >= 0 && py < image.height() as i32 {
                        image.put_pixel(px as u32, py as u32, color_rgba);
                    }
                }
            }
            
            if x == x1 && y == y1 { break; }
            let e2 = 2 * err;
            if e2 >= dy {
                err += dy;
                x += sx;
            }
            if e2 <= dx {
                err += dx;
                y += sy;
            }
        }
    }

    fn draw_rect_on_image(image: &mut RgbaImage, start: Pos2, end: Pos2, color: Color32, size: f32, filled: bool) {
        let color_rgba = Rgba([color.r(), color.g(), color.b(), color.a()]);
        let x_min = start.x.min(end.x) as i32;
        let y_min = start.y.min(end.y) as i32;
        let x_max = start.x.max(end.x) as i32;
        let y_max = start.y.max(end.y) as i32;

        if filled {
            for y in y_min..=y_max {
                for x in x_min..=x_max {
                    if x >= 0 && x < image.width() as i32 && y >= 0 && y < image.height() as i32 {
                        image.put_pixel(x as u32, y as u32, color_rgba);
                    }
                }
            }
        } else {
            // Draw four lines
            Self::draw_line_on_image(image, Pos2::new(x_min as f32, y_min as f32), Pos2::new(x_max as f32, y_min as f32), color, size);
            Self::draw_line_on_image(image, Pos2::new(x_max as f32, y_min as f32), Pos2::new(x_max as f32, y_max as f32), color, size);
            Self::draw_line_on_image(image, Pos2::new(x_max as f32, y_max as f32), Pos2::new(x_min as f32, y_max as f32), color, size);
            Self::draw_line_on_image(image, Pos2::new(x_min as f32, y_max as f32), Pos2::new(x_min as f32, y_min as f32), color, size);
        }
    }

    fn draw_oval_on_image(image: &mut RgbaImage, start: Pos2, end: Pos2, color: Color32, size: f32, filled: bool) {
        let color_rgba = Rgba([color.r(), color.g(), color.b(), color.a()]);
        let x_min = start.x.min(end.x) as f32;
        let y_min = start.y.min(end.y) as f32;
        let x_max = start.x.max(end.x) as f32;
        let y_max = start.y.max(end.y) as f32;
        
        let cx = (x_min + x_max) / 2.0;
        let cy = (y_min + y_max) / 2.0;
        let rx = (x_max - x_min) / 2.0;
        let ry = (y_max - y_min) / 2.0;

        if rx <= 0.0 || ry <= 0.0 { return; }

        if filled {
            for y in (y_min as i32)..=(y_max as i32) {
                for x in (x_min as i32)..=(x_max as i32) {
                    let dx = (x as f32 - cx) / rx;
                    let dy = (y as f32 - cy) / ry;
                    if dx * dx + dy * dy <= 1.0 {
                        if x >= 0 && x < image.width() as i32 && y >= 0 && y < image.height() as i32 {
                            image.put_pixel(x as u32, y as u32, color_rgba);
                        }
                    }
                }
            }
        } else {
            // Simple outline drawing for oval
            let num_segments = 100;
            let mut last_p = Pos2::new(cx + rx, cy);
            for i in 1..=num_segments {
                let angle = (i as f32) / (num_segments as f32) * std::f32::consts::TAU;
                let next_p = Pos2::new(cx + rx * angle.cos(), cy + ry * angle.sin());
                Self::draw_line_on_image(image, last_p, next_p, color, size);
                last_p = next_p;
            }
        }
    }

    fn draw_rounded_rect_on_image(image: &mut RgbaImage, start: Pos2, end: Pos2, color: Color32, size: f32, filled: bool) {
        let color_rgba = Rgba([color.r(), color.g(), color.b(), color.a()]);
        let x_min = start.x.min(end.x) as i32;
        let y_min = start.y.min(end.y) as i32;
        let x_max = start.x.max(end.x) as i32;
        let y_max = start.y.max(end.y) as i32;
        
        let radius = 10.0f32; // Fixed corner radius like in Java version (ROUNDED_ARC = 10)
        
        if filled {
            for y in y_min..=y_max {
                for x in x_min..=x_max {
                    let mut inside = true;
                    // Check corners
                    let dx = if x < x_min + radius as i32 { (x_min + radius as i32) - x }
                            else if x > x_max - radius as i32 { x - (x_max - radius as i32) }
                            else { 0 };
                    let dy = if y < y_min + radius as i32 { (y_min + radius as i32) - y }
                            else if y > y_max - radius as i32 { y - (y_max - radius as i32) }
                            else { 0 };
                    
                    if dx > 0 && dy > 0 {
                        if (dx*dx + dy*dy) as f32 > radius * radius {
                            inside = false;
                        }
                    }

                    if inside && x >= 0 && x < image.width() as i32 && y >= 0 && y < image.height() as i32 {
                        image.put_pixel(x as u32, y as u32, color_rgba);
                    }
                }
            }
        } else {
            // Draw four lines (truncated) and four arcs
            let r = radius as i32;
            // Horizontal lines
            Self::draw_line_on_image(image, Pos2::new((x_min + r) as f32, y_min as f32), Pos2::new((x_max - r) as f32, y_min as f32), color, size);
            Self::draw_line_on_image(image, Pos2::new((x_min + r) as f32, y_max as f32), Pos2::new((x_max - r) as f32, y_max as f32), color, size);
            // Vertical lines
            Self::draw_line_on_image(image, Pos2::new(x_min as f32, (y_min + r) as f32), Pos2::new(x_min as f32, (y_max - r) as f32), color, size);
            Self::draw_line_on_image(image, Pos2::new(x_max as f32, (y_min + r) as f32), Pos2::new(x_max as f32, (y_max - r) as f32), color, size);
            
            // Arcs (simplified by drawing many small lines)
            let draw_arc = |image: &mut RgbaImage, cx: f32, cy: f32, start_angle: f32| {
                let num_segments = 10;
                let mut last_p = Pos2::new(cx + radius * start_angle.cos(), cy + radius * start_angle.sin());
                for i in 1..=num_segments {
                    let angle = start_angle + (i as f32 / num_segments as f32) * (std::f32::consts::PI / 2.0);
                    let next_p = Pos2::new(cx + radius * angle.cos(), cy + radius * angle.sin());
                    Self::draw_line_on_image(image, last_p, next_p, color, size);
                    last_p = next_p;
                }
            };
            
            draw_arc(image, (x_max - r) as f32, (y_min + r) as f32, -std::f32::consts::PI / 2.0); // Top-right
            draw_arc(image, (x_max - r) as f32, (y_max - r) as f32, 0.0); // Bottom-right
            draw_arc(image, (x_min + r) as f32, (y_max - r) as f32, std::f32::consts::PI / 2.0); // Bottom-left
            draw_arc(image, (x_min + r) as f32, (y_min + r) as f32, std::f32::consts::PI); // Top-left
        }
    }

    fn draw_arrow_on_image(image: &mut RgbaImage, start: Pos2, end: Pos2, color: Color32, size: f32) {
        Self::draw_line_on_image(image, start, end, color, size);
        
        let dx = end.x - start.x;
        let dy = end.y - start.y;
        let angle = dy.atan2(dx);
        let arrow_len = 20.0f32;
        let arrow_angle = 0.5f32; // radians
        
        let p1 = Pos2::new(
            end.x - arrow_len * (angle - arrow_angle).cos(),
            end.y - arrow_len * (angle - arrow_angle).sin(),
        );
        let p2 = Pos2::new(
            end.x - arrow_len * (angle + arrow_angle).cos(),
            end.y - arrow_len * (angle + arrow_angle).sin(),
        );
        
        Self::draw_line_on_image(image, end, p1, color, size);
        Self::draw_line_on_image(image, end, p2, color, size);
    }

    fn flood_fill(image: &mut RgbaImage, pos: Pos2, color: Color32) {
        let x = pos.x as i32;
        let y = pos.y as i32;
        if x < 0 || x >= image.width() as i32 || y < 0 || y >= image.height() as i32 {
            return;
        }

        let target_color = image.get_pixel(x as u32, y as u32).clone();
        let replacement_color = Rgba([color.r(), color.g(), color.b(), color.a()]);
        if target_color == replacement_color {
            return;
        }

        let mut queue = std::collections::VecDeque::new();
        queue.push_back((x as u32, y as u32));

        while let Some((cx, cy)) = queue.pop_front() {
            if image.get_pixel(cx, cy) != &target_color {
                continue;
            }

            // Find leftmost and rightmost bounds of the span
            let mut left = cx;
            while left > 0 && image.get_pixel(left - 1, cy) == &target_color {
                left -= 1;
            }
            let mut right = cx;
            while right < image.width() - 1 && image.get_pixel(right + 1, cy) == &target_color {
                right += 1;
            }

            // Fill the span and check rows above and below
            for x_span in left..=right {
                image.put_pixel(x_span, cy, replacement_color);
                
                if cy > 0 && image.get_pixel(x_span, cy - 1) == &target_color {
                    if x_span == left || image.get_pixel(x_span - 1, cy - 1) != &target_color {
                        queue.push_back((x_span, cy - 1));
                    }
                }
                if cy < image.height() - 1 && image.get_pixel(x_span, cy + 1) == &target_color {
                    if x_span == left || image.get_pixel(x_span - 1, cy + 1) != &target_color {
                        queue.push_back((x_span, cy + 1));
                    }
                }
            }
        }
    }

    fn draw_text_on_image(image: &mut RgbaImage, pos: Pos2, text: &str, color: Color32, size: f32) {
        let color_rgba = Rgba([color.r(), color.g(), color.b(), color.a()]);
        let x_start = pos.x as i32;
        let y_start = pos.y as i32;
        
        for (i, _) in text.chars().enumerate() {
            let x_off = i as i32 * (size as i32 * 4);
            for dy in 0..(size as i32 * 6) {
                for dx in 0..(size as i32 * 3) {
                    let px = x_start + x_off + dx;
                    let py = y_start + dy;
                    if px >= 0 && px < image.width() as i32 && py >= 0 && py < image.height() as i32 {
                        image.put_pixel(px as u32, py as u32, color_rgba);
                    }
                }
            }
        }
    }
}

impl eframe::App for PaintApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        if ctx.input(|i| i.viewport().close_requested()) {
            ctx.send_viewport_cmd(ViewportCommand::CancelClose);
            self.show_exit_confirm = true;
        }
        self.handle_shortcuts(ctx);
        self.ensure_tool_icons();

        // Update texture from image
        let image_to_show = self.preview_image.as_ref().unwrap_or(&self.image);
        let color_image = ColorImage::from_rgba_unmultiplied(
            [image_to_show.width() as usize, image_to_show.height() as usize],
            image_to_show.as_flat_samples().as_slice(),
        );
        self.texture.get_or_insert_with(|| {
            ctx.load_texture("canvas", color_image.clone(), Default::default())
        }).set(color_image, Default::default());

        egui::TopBottomPanel::top("top_panel").show(ctx, |ui| {
            egui::menu::bar(ui, |ui| {
                ui.menu_button("File", |ui| {
                    if ui.button("New (Ctrl+N)").clicked() {
                        self.new_canvas();
                        ui.close_menu();
                    }
                    if ui.button("Open… (Ctrl+O)").clicked() {
                        if let Some(path) = rfd::FileDialog::new()
                            .add_filter("Images", &["png", "jpg", "jpeg", "bmp"])
                            .pick_file()
                        {
                            self.open_image(path);
                        }
                        ui.close_menu();
                    }
                    if ui.button("Save As… (Ctrl+S)").clicked() {
                        if let Some(path) = rfd::FileDialog::new()
                            .add_filter("PNG Image", &["png"])
                            .save_file()
                        {
                            self.save_image(path);
                        }
                        ui.close_menu();
                    }
                    ui.separator();
                    if ui.button("Clear Canvas").clicked() {
                        self.clear_canvas();
                        ui.close_menu();
                    }
                    ui.separator();
                    if ui.button("Exit").clicked() {
                        self.show_exit_confirm = true;
                        ui.close_menu();
                    }
                });
                ui.menu_button("Edit", |ui| {
                    if ui.button("Undo (Ctrl+Z)").clicked() {
                        self.undo();
                        ui.close_menu();
                    }
                    if ui.button("Redo (Ctrl+Y / Ctrl+Shift+Z)").clicked() {
                        self.redo();
                        ui.close_menu();
                    }
                    ui.separator();
                    if ui.button("Copy (Ctrl+C)").clicked() {
                        self.copy_selection();
                        ui.close_menu();
                    }
                    if ui.button("Paste (Ctrl+V)").clicked() {
                        self.paste_clipboard();
                        ui.close_menu();
                    }
                    if ui.button("Select All (Ctrl+A)").clicked() {
                        self.select_all();
                        ui.close_menu();
                    }
                    if ui.button("Crop to Image Size").clicked() {
                        self.crop_to_image_size();
                        ui.close_menu();
                    }
                    if ui.button("Crop to Selection (Ctrl+Shift+X)").clicked() {
                        self.crop_to_selection();
                        ui.close_menu();
                    }
                });
                ui.menu_button("Tools", |ui| {
                    ui.label("Choose tool");
                    ui.separator();
                    let tool_items = [
                        (Tool::Pencil, "Pencil"),
                        (Tool::Line, "Line"),
                        (Tool::Rectangle, "Rectangle"),
                        (Tool::RectangleFilled, "Rectangle (Filled)"),
                        (Tool::Oval, "Oval"),
                        (Tool::OvalFilled, "Oval (Filled)"),
                        (Tool::RoundedRect, "Rounded Rect"),
                        (Tool::RoundedRectFilled, "Rounded Rect (Filled)"),
                        (Tool::Arrow, "Arrow"),
                        (Tool::Bucket, "Bucket"),
                        (Tool::Eraser, "Eraser"),
                        (Tool::Highlighter, "Highlighter"),
                        (Tool::Text, "Text"),
                        (Tool::Move, "Move (Ctrl+M)"),
                    ];
                    for (tool, label) in tool_items {
                        if ui.selectable_label(self.selected_tool == tool, label).clicked() {
                            self.selected_tool = tool;
                            ui.close_menu();
                        }
                    }
                    ui.separator();
                    ui.label("Stroke presets");
                    let strokes = [1.0, 2.0, 3.0, 5.0, 8.0, 12.0, 20.0];
                    for size in strokes {
                        if ui.button(format!("{size:.0} px")).clicked() {
                            self.stroke_size = size;
                        }
                    }
                });
                ui.menu_button("Text", |ui| {
                    ui.label("Text content");
                    ui.text_edit_singleline(&mut self.text_input);
                    ui.separator();
                    ui.label("Size");
                    ui.add(egui::Slider::new(&mut self.stroke_size, 1.0..=20.0).text(""));
                });
                ui.menu_button("Help", |ui| {
                    if ui.button("About Paint").clicked() {
                        self.show_about = true;
                        ui.close_menu();
                    }
                });
                
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    egui::widgets::global_theme_preference_buttons(ui);
                });
            });
        });

        egui::SidePanel::left("tool_panel")
            .resizable(false)
            .default_width(78.0)
            .show(ctx, |ui| {
                ui.spacing_mut().item_spacing = egui::vec2(4.0, 4.0);
                let button_size = egui::vec2(28.0, 28.0);
                let tools = [
                    (Tool::Pencil, "Pencil"),
                    (Tool::Line, "Line"),
                    (Tool::Rectangle, "Rectangle"),
                    (Tool::Oval, "Oval"),
                    (Tool::RoundedRect, "Rounded Rect"),
                    (Tool::Eraser, "Eraser"),
                    (Tool::Text, "Text"),
                    (Tool::RectangleFilled, "Rectangle (Filled)"),
                    (Tool::OvalFilled, "Oval (Filled)"),
                    (Tool::RoundedRectFilled, "Rounded Rect (Filled)"),
                    (Tool::Bucket, "Bucket"),
                    (Tool::Move, "Move"),
                    (Tool::Highlighter, "Highlighter"),
                    (Tool::Arrow, "Arrow"),
                ];

                egui::Grid::new("tool_grid")
                    .num_columns(2)
                    .spacing([6.0, 6.0])
                    .show(ui, |ui| {
                        for (i, (tool, label)) in tools.into_iter().enumerate() {
                            if let Some(icon) = self.icon_for(tool) {
                                let tex = icon.texture_id(ui.ctx());
                                let mut resp = ui.add(
                                    egui::ImageButton::new((tex, button_size))
                                        .frame(false)
                                        .sense(egui::Sense::click()),
                                );
                                if resp.clicked() {
                                    self.selected_tool = tool;
                                }
                                if resp.hovered() {
                                    resp = resp.on_hover_text(label);
                                }
                                if self.selected_tool == tool {
                                    let stroke = ui.visuals().selection.stroke;
                                    ui.painter().rect_stroke(resp.rect.expand(2.0), 6.0, stroke);
                                }
                            } else {
                                if ui.selectable_label(self.selected_tool == tool, label).clicked() {
                                    self.selected_tool = tool;
                                }
                            }

                            if (i + 1) % 2 == 0 {
                                ui.end_row();
                            }
                        }
                    });
            });

        egui::TopBottomPanel::bottom("status_panel").show(ctx, |ui| {
            ui.spacing_mut().item_spacing = egui::vec2(6.0, 4.0);
            ui.horizontal(|ui| {
                ui.label(&self.status_message);
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    ui.label(format!("{} x {}", self.canvas_size.x as u32, self.canvas_size.y as u32));
                    ui.label("Canvas:");
                });
            });

            ui.add_space(4.0);
            ui.horizontal_wrapped(|ui| {
                ui.label("Colors");
                for color in PALETTE.iter() {
                    let stroke = if *color == self.selected_color {
                        egui::Stroke::new(2.0, ui.visuals().selection.stroke.color)
                    } else {
                        egui::Stroke::new(1.0, ui.visuals().widgets.noninteractive.fg_stroke.color)
                    };
                    let resp = ui.add_sized(
                        [16.0, 16.0],
                        egui::Button::new("").fill(*color).stroke(stroke),
                    );
                    if resp.clicked() {
                        self.selected_color = *color;
                    }
                }
                ui.color_edit_button_srgba(&mut self.selected_color);

                ui.separator();
                ui.label("Size");
                let mut w = self.canvas_size.x.round().max(1.0) as u32;
                let mut h = self.canvas_size.y.round().max(1.0) as u32;
                ui.add(
                    egui::DragValue::new(&mut w)
                        .range(1..=20000)
                        .speed(10.0)
                        .suffix(" px"),
                );
                ui.add(
                    egui::DragValue::new(&mut h)
                        .range(1..=20000)
                        .speed(10.0)
                        .suffix(" px"),
                );
                if ui.button("Resize").clicked() {
                    self.resize_canvas(w, h);
                }
            });
        });

        egui::CentralPanel::default().show(ctx, |ui| {
            ui.spacing_mut().item_spacing = egui::vec2(8.0, 6.0);
            // Compact ribbon at the top
            ui.horizontal_wrapped(|ui| {
                ui.label("Stroke");
                ui.add_sized(
                    [120.0, 20.0],
                    egui::Slider::new(&mut self.stroke_size, 1.0..=20.0).show_value(false),
                );

                if self.selected_tool == Tool::Highlighter {
                    ui.label("Opacity");
                    let mut op = (self.highlighter_opacity * 100.0).clamp(5.0, 100.0);
                    ui.add_sized([100.0, 20.0], egui::Slider::new(&mut op, 5.0..=100.0).show_value(false));
                    self.highlighter_opacity = op / 100.0;
                }

                ui.separator();
                ui.label("Color");
                ui.color_edit_button_srgba(&mut self.selected_color);

                if self.selected_tool == Tool::Text {
                    ui.separator();
                    ui.label("Text");
                    ui.add_sized([180.0, 22.0], egui::TextEdit::singleline(&mut self.text_input));
                }
            });
            ui.add_space(4.0);
            ui.separator();

            // Drawing Canvas Area
            egui::ScrollArea::both().show(ui, |ui| {
                let (rect, response) = ui.allocate_at_least(self.canvas_size, egui::Sense::drag());
                
                // Draw backing image
                if let Some(texture) = &self.texture {
                    ui.painter().image(texture.id(), rect, Rect::from_min_max(Pos2::ZERO, Pos2::new(1.0, 1.0)), Color32::WHITE);
                }
                
                // Handle drawing input
                if let Some(pointer_pos) = response.interact_pointer_pos() {
                    let pos = pointer_pos - rect.min;
                    let canvas_pos = Pos2::new(pos.x, pos.y);

                    if response.drag_started_by(egui::PointerButton::Primary) {
                        if self.selected_tool == Tool::Move {
                            if let Some(selection_rect) = self.selection_rect {
                                if selection_rect.contains(canvas_pos) {
                                    // Start dragging existing selection
                                    self.drag_start_pos = Some(canvas_pos);
                                    return;
                                } else {
                                    // Commit existing selection before starting new one
                                    self.commit_selection();
                                }
                            }
                        }
                        self.drag_start_pos = Some(canvas_pos);
                    }

                    if response.dragged_by(egui::PointerButton::Primary) {
                        if let Some(start) = self.drag_start_pos {
                            match self.selected_tool {
                                Tool::Pencil | Tool::Eraser | Tool::Highlighter => {
                                    if self.last_mouse_pos.is_none() {
                                        self.save_undo_snapshot();
                                    }
                                    if let Some(last_pos) = self.last_mouse_pos {
                                        let color = match self.selected_tool {
                                            Tool::Eraser => Color32::WHITE,
                                            Tool::Highlighter => {
                                                let alpha = (self.highlighter_opacity * 255.0) as u8;
                                                Color32::from_rgba_unmultiplied(self.selected_color.r(), self.selected_color.g(), self.selected_color.b(), alpha)
                                            },
                                            _ => self.selected_color,
                                        };
                                        Self::draw_line_on_image(&mut self.image, last_pos, canvas_pos, color, self.stroke_size);
                                    }
                                    self.last_mouse_pos = Some(canvas_pos);
                                }
                                Tool::Line | Tool::Rectangle | Tool::RectangleFilled | Tool::Oval | Tool::OvalFilled | Tool::RoundedRect | Tool::RoundedRectFilled | Tool::Arrow => {
                                    if self.preview_image.is_none() {
                                        self.save_undo_snapshot();
                                    }
                                    let mut preview = self.image.clone();
                                    match self.selected_tool {
                                        Tool::Line => Self::draw_line_on_image(&mut preview, start, canvas_pos, self.selected_color, self.stroke_size),
                                        Tool::Rectangle => Self::draw_rect_on_image(&mut preview, start, canvas_pos, self.selected_color, self.stroke_size, false),
                                        Tool::RectangleFilled => Self::draw_rect_on_image(&mut preview, start, canvas_pos, self.selected_color, self.stroke_size, true),
                                        Tool::Oval => Self::draw_oval_on_image(&mut preview, start, canvas_pos, self.selected_color, self.stroke_size, false),
                                        Tool::OvalFilled => Self::draw_oval_on_image(&mut preview, start, canvas_pos, self.selected_color, self.stroke_size, true),
                                        Tool::RoundedRect => Self::draw_rounded_rect_on_image(&mut preview, start, canvas_pos, self.selected_color, self.stroke_size, false),
                                        Tool::RoundedRectFilled => Self::draw_rounded_rect_on_image(&mut preview, start, canvas_pos, self.selected_color, self.stroke_size, true),
                                        Tool::Arrow => Self::draw_arrow_on_image(&mut preview, start, canvas_pos, self.selected_color, self.stroke_size),
                                        _ => {}
                                    }
                                    self.preview_image = Some(preview);
                                }
                                Tool::Move => {
                                    if let (Some(sel_img), Some(sel_pos)) = (&self.selection_image, self.selection_pos) {
                                        let delta = canvas_pos - start;
                                        let new_pos = sel_pos + delta;
                                        self.selection_pos = Some(new_pos);
                                        self.selection_rect = Some(Rect::from_min_size(new_pos, Vec2::new(sel_img.width() as f32, sel_img.height() as f32)));
                                        self.drag_start_pos = Some(canvas_pos); // Update for next delta
                                    } else {
                                        // Defining selection rectangle
                                        self.selection_rect = Some(Rect::from_two_pos(start, canvas_pos));
                                    }
                                }
                                Tool::Bucket => {
                                    if self.drag_start_pos.is_none() {
                                        self.save_undo_snapshot();
                                        Self::flood_fill(&mut self.image, canvas_pos, self.selected_color);
                                        self.drag_start_pos = Some(canvas_pos); // Use as flag to prevent repeated fill while dragging
                                    }
                                }
                                Tool::Text => {
                                    if self.drag_start_pos.is_none() {
                                        self.save_undo_snapshot();
                                        Self::draw_text_on_image(&mut self.image, canvas_pos, &self.text_input, self.selected_color, self.stroke_size);
                                        self.drag_start_pos = Some(canvas_pos);
                                    }
                                }
                            }
                        }
                    }

                    if response.drag_stopped_by(egui::PointerButton::Primary) {
                        if let Some(_start) = self.drag_start_pos {
                            match self.selected_tool {
                                Tool::Line | Tool::Rectangle | Tool::RectangleFilled | Tool::Oval | Tool::OvalFilled | Tool::RoundedRect | Tool::RoundedRectFilled | Tool::Arrow => {
                                    if let Some(preview) = self.preview_image.take() {
                                        self.image = preview;
                                    }
                                }
                                Tool::Move => {
                                    if self.selection_image.is_none() {
                                        if let Some(rect) = self.selection_rect {
                                            let x_min = rect.min.x.max(0.0) as u32;
                                            let y_min = rect.min.y.max(0.0) as u32;
                                            let x_max = rect.max.x.min(self.image.width() as f32) as u32;
                                            let y_max = rect.max.y.min(self.image.height() as f32) as u32;
                                            
                                            if x_max > x_min && y_max > y_min {
                                                self.save_undo_snapshot();
                                                let width = x_max - x_min;
                                                let height = y_max - y_min;
                                                let mut sel_img = RgbaImage::new(width, height);
                                                for y in 0..height {
                                                    for x in 0..width {
                                                        let pixel = self.image.get_pixel(x_min + x, y_min + y);
                                                        sel_img.put_pixel(x, y, *pixel);
                                                        // Clear the source area
                                                        self.image.put_pixel(x_min + x, y_min + y, Rgba([255, 255, 255, 255]));
                                                    }
                                                }
                                                self.selection_image = Some(sel_img);
                                                self.selection_pos = Some(Pos2::new(x_min as f32, y_min as f32));
                                            }
                                        }
                                    }
                                }
                                _ => {}
                            }
                        }
                        self.drag_start_pos = None;
                        self.last_mouse_pos = None;
                        self.preview_image = None;
                    }

                    if response.clicked() {
                        match self.selected_tool {
                            Tool::Bucket => {
                                self.save_undo_snapshot();
                                Self::flood_fill(&mut self.image, canvas_pos, self.selected_color);
                            }
                            Tool::Text => {
                                self.save_undo_snapshot();
                                Self::draw_text_on_image(&mut self.image, canvas_pos, &self.text_input, self.selected_color, self.stroke_size);
                            }
                            Tool::Move => {
                                if let Some(selection_rect) = self.selection_rect {
                                    if !selection_rect.contains(canvas_pos) {
                                        self.commit_selection();
                                    }
                                }
                            }
                            _ => {}
                        }
                    }
                } else {
                    self.last_mouse_pos = None;
                }

                // Visual feedback for selection
                if let Some(rect) = self.selection_rect {
                    let painter_rect = Rect::from_min_max(rect.min + response.rect.min.to_vec2(), rect.max + response.rect.min.to_vec2());
                    ui.painter().rect_stroke(painter_rect, 0.0, egui::Stroke::new(1.0, Color32::GRAY));
                    
                    if let (Some(sel_img), Some(_pos)) = (&self.selection_image, self.selection_pos) {
                        let color_image = ColorImage::from_rgba_unmultiplied(
                            [sel_img.width() as usize, sel_img.height() as usize],
                            sel_img.as_flat_samples().as_slice(),
                        );
                        let tex = ctx.load_texture("selection", color_image, Default::default());
                        ui.painter().image(tex.id(), painter_rect, Rect::from_min_max(Pos2::ZERO, Pos2::new(1.0, 1.0)), Color32::WHITE);
                    }
                }
            });
        });

        if self.show_exit_confirm {
            egui::Window::new("Exit")
                .collapsible(false)
                .resizable(false)
                .show(ctx, |ui| {
                    ui.label("Are you sure you want to exit?");
                    ui.horizontal(|ui| {
                        if ui.button("OK").clicked() {
                            self.show_exit_confirm = false;
                            ctx.send_viewport_cmd(ViewportCommand::Close);
                        }
                        if ui.button("Cancel").clicked() {
                            self.show_exit_confirm = false;
                        }
                    });
                });
        }

        if self.show_about {
            egui::Window::new("About Paint")
                .open(&mut self.show_about)
                .resizable(false)
                .show(ctx, |ui| {
                    ui.heading("Paint (Rust egui)");
                    ui.label("A port of the Java Swing paint application with matching tools, menus, and shortcuts.");
                    ui.separator();
                    ui.label("Shortcuts: Ctrl+N (New), Ctrl+O (Open), Ctrl+S (Save As), Ctrl+Z/Y (Undo/Redo), Ctrl+C/V/A, Ctrl+Shift+X (Crop)");
                    ui.label("Tools: Pencil, shapes, arrow, bucket, text, highlighter, move.");
                });
        }
    }
}

fn main() -> Result<(), eframe::Error> {
    let args: Vec<String> = std::env::args().collect();
    let mut app = PaintApp::default();
    
    if args.len() > 1 {
        let path = std::path::PathBuf::from(&args[1]);
        if path.exists() {
            app.open_image(path);
        }
    }

    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1200.0, 800.0])
            .with_title("Paint - Rust egui"),
        ..Default::default()
    };

    eframe::run_native(
        "Paint",
        options,
        Box::new(|_cc| Ok(Box::new(app))),
    )
}
