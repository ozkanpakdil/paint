use gtk4::prelude::*;
use gtk4::{Application, ApplicationWindow, DrawingArea, Box, Orientation, Label, GestureClick, EventControllerMotion, Button, ColorDialogButton, ColorDialog, FileDialog, Grid, Image, Scale, ScrolledWindow, CssProvider, MenuButton, AboutDialog, Entry};
use gtk4::gio::{SimpleAction, Menu};
use gdk4::{Texture, Key};
use std::rc::Rc;
use std::cell::RefCell;
use cairo::{ImageSurface, Context, Format};
use std::collections::VecDeque;
use std::fs::File;

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Tool {
    Pencil,
    Line,
    Rect,
    Oval,
    RoundedRect,
    Eraser,
    Text,
    RectFilled,
    OvalFilled,
    RoundedRectFilled,
    Bucket,
    Move,
    Highlighter,
    Arrow,
}

struct AppState {
    surface: RefCell<Option<ImageSurface>>,
    current_tool: RefCell<Tool>,
    stroke_color: RefCell<(f64, f64, f64, f64)>,
    stroke_size: RefCell<f64>,
    highlighter_opacity: RefCell<f64>,
    start_pos: RefCell<(f64, f64)>,
    is_drawing: RefCell<bool>,
    temp_surface: RefCell<Option<ImageSurface>>, // For previewing shapes while dragging
    undo_stack: RefCell<Vec<ImageSurface>>,
    redo_stack: RefCell<Vec<ImageSurface>>,
    
    // Selection and placement
    selection_rect: RefCell<Option<(f64, f64, f64, f64)>>, // (x, y, w, h)
    pending_image: RefCell<Option<ImageSurface>>,
    pending_pos: RefCell<(f64, f64)>,
    last_pasted_rect: RefCell<Option<(f64, f64, f64, f64)>>,
    text_entry: RefCell<Option<gtk4::Entry>>,
}

fn main() {
    let app = Application::builder()
        .application_id("io.github.ozkanpakdil.paint")
        .build();

    app.connect_activate(build_ui);
    app.run();
}

fn build_ui(app: &Application) {
    let window = ApplicationWindow::builder()
        .application(app)
        .title("Rust Paint")
        .default_width(1200)
        .default_height(640)
        .build();

    let header_bar = gtk4::HeaderBar::new();
    window.set_titlebar(Some(&header_bar));

    let state = Rc::new(AppState {
        surface: RefCell::new(None),
        current_tool: RefCell::new(Tool::Pencil),
        stroke_color: RefCell::new((0.0, 0.0, 0.0, 1.0)),
        stroke_size: RefCell::new(2.0),
        highlighter_opacity: RefCell::new(0.5),
        start_pos: RefCell::new((0.0, 0.0)),
        is_drawing: RefCell::new(false),
        temp_surface: RefCell::new(None),
        undo_stack: RefCell::new(Vec::new()),
        redo_stack: RefCell::new(Vec::new()),
        selection_rect: RefCell::new(None),
        pending_image: RefCell::new(None),
        pending_pos: RefCell::new((0.0, 0.0)),
        last_pasted_rect: RefCell::new(None),
        text_entry: RefCell::new(None),
    });

    let overlay = gtk4::Overlay::new();
    overlay.set_hexpand(true);
    overlay.set_vexpand(true);

    let drawing_area = DrawingArea::new();
    drawing_area.set_hexpand(true);
    drawing_area.set_vexpand(true);
    overlay.set_child(Some(&drawing_area));

    // Actions
    let state_new = state.clone();
    let area_new = drawing_area.clone();
    let new_action = SimpleAction::new("new", None);
    new_action.connect_activate(move |_, _| {
        commit_text_editor(&state_new, &area_new);
        if let Some(surface) = state_new.surface.borrow().as_ref() {
            let cr = Context::new(surface).expect("Failed to create context");
            cr.set_source_rgb(1.0, 1.0, 1.0);
            cr.paint().expect("Failed to paint");
            state_new.undo_stack.borrow_mut().clear();
            state_new.redo_stack.borrow_mut().clear();
            area_new.queue_draw();
        }
    });
    window.add_action(&new_action);

    let state_save = state.clone();
    let area_save = drawing_area.clone();
    let window_save = window.clone();
    let save_action = SimpleAction::new("save", None);
    save_action.connect_activate(move |_, _| {
        commit_text_editor(&state_save, &area_save);
        let file_dialog = FileDialog::new();
        file_dialog.set_initial_name(Some("untitled.png"));
        let state_dialog = state_save.clone();
        file_dialog.save(Some(&window_save), gtk4::gio::Cancellable::NONE, move |res| {
            if let Ok(file) = res {
                if let Some(path) = file.path() {
                    if let Some(surface) = state_dialog.surface.borrow().as_ref() {
                        let mut file = File::create(path).expect("Couldn't create file");
                        let surface: &ImageSurface = surface;
                        surface.write_to_png(&mut file).expect("Couldn't write PNG");
                    }
                }
            }
        });
    });
    window.add_action(&save_action);

    let state_open = state.clone();
    let window_open = window.clone();
    let area_open = drawing_area.clone();
    let open_action = SimpleAction::new("open", None);
    open_action.connect_activate(move |_, _| {
        let file_dialog = FileDialog::new();
        let state_dialog = state_open.clone();
        let area_dialog = area_open.clone();
        file_dialog.open(Some(&window_open), gtk4::gio::Cancellable::NONE, move |res| {
            if let Ok(file) = res {
                if let Some(path) = file.path() {
                    if let Ok(surface) = ImageSurface::create_from_png(&mut File::open(path).expect("Cant open file")) {
                        state_dialog.undo_stack.borrow_mut().push(copy_surface(state_dialog.surface.borrow().as_ref().unwrap()));
                        *state_dialog.pending_image.borrow_mut() = Some(surface);
                        *state_dialog.pending_pos.borrow_mut() = (0.0, 0.0);
                        area_dialog.queue_draw();
                    }
                }
            }
        });
    });
    window.add_action(&open_action);

    let state_undo = state.clone();
    let area_undo = drawing_area.clone();
    let undo_action = SimpleAction::new("undo", None);
    undo_action.connect_activate(move |_, _| {
        commit_text_editor(&state_undo, &area_undo);
        let mut undo_stack = state_undo.undo_stack.borrow_mut();
        if let Some(prev_surface) = undo_stack.pop() {
            if let Some(current_surface) = state_undo.surface.borrow().as_ref() {
                state_undo.redo_stack.borrow_mut().push(copy_surface(current_surface));
            }
            *state_undo.surface.borrow_mut() = Some(prev_surface);
            area_undo.queue_draw();
        }
    });
    window.add_action(&undo_action);

    let state_redo = state.clone();
    let area_redo = drawing_area.clone();
    let redo_action = SimpleAction::new("redo", None);
    redo_action.connect_activate(move |_, _| {
        commit_text_editor(&state_redo, &area_redo);
        let mut redo_stack = state_redo.redo_stack.borrow_mut();
        if let Some(next_surface) = redo_stack.pop() {
            if let Some(current_surface) = state_redo.surface.borrow().as_ref() {
                state_redo.undo_stack.borrow_mut().push(copy_surface(current_surface));
            }
            *state_redo.surface.borrow_mut() = Some(next_surface);
            area_redo.queue_draw();
        }
    });
    window.add_action(&redo_action);

    let state_sel_all = state.clone();
    let area_sel_all = drawing_area.clone();
    let sel_all_action = SimpleAction::new("select-all", None);
    sel_all_action.connect_activate(move |_, _| {
        if let Some(surface) = state_sel_all.surface.borrow().as_ref() {
            let surface: &ImageSurface = surface;
            *state_sel_all.selection_rect.borrow_mut() = Some((0.0, 0.0, surface.width() as f64, surface.height() as f64));
            area_sel_all.queue_draw();
        }
    });
    window.add_action(&sel_all_action);

    let state_crop = state.clone();
    let area_crop = drawing_area.clone();
    let crop_action = SimpleAction::new("crop", None);
    crop_action.connect_activate(move |_, _| {
        // Case A: Pending image (matching Java logic)
        let pending_opt = state_crop.pending_image.borrow().as_ref().map(|p| copy_surface(p));
        if let Some(pending) = pending_opt {
            if let Some(surface) = state_crop.surface.borrow().as_ref() {
                state_crop.undo_stack.borrow_mut().push(copy_surface(surface));
            }
            *state_crop.surface.borrow_mut() = Some(pending);
            *state_crop.pending_image.borrow_mut() = None;
            area_crop.queue_draw();
            return;
        }

        // Case B: Marquee selection
        let sel = *state_crop.selection_rect.borrow();
        if let Some((sx, sy, sw, sh)) = sel {
            if let Some(surface) = state_crop.surface.borrow().as_ref() {
                state_crop.undo_stack.borrow_mut().push(copy_surface(surface));
                let new_surface = ImageSurface::create(Format::ARgb32, sw as i32, sh as i32)
                    .expect("Can't create surface");
                let cr = Context::new(&new_surface).expect("Failed to create context");
                cr.set_source_surface(surface, -sx, -sy).expect("Failed to set source");
                cr.paint().expect("Failed to paint");
                *state_crop.surface.borrow_mut() = Some(new_surface);
                *state_crop.selection_rect.borrow_mut() = None;
                area_crop.queue_draw();
            }
        }
    });
    window.add_action(&crop_action);

    let state_copy = state.clone();
    let area_copy = drawing_area.clone();
    let window_copy = window.clone();
    let copy_action = SimpleAction::new("copy", None);
    copy_action.connect_activate(move |_, _| {
        commit_text_editor(&state_copy, &area_copy);
        let sel = *state_copy.selection_rect.borrow();
        let surface_to_copy = if let Some((sx, sy, sw, sh)) = sel {
            state_copy.surface.borrow().as_ref().map(|surface| {
                let copy = ImageSurface::create(Format::ARgb32, sw as i32, sh as i32)
                    .expect("Can't create surface");
                let cr = Context::new(&copy).expect("Failed to create context");
                cr.set_source_surface(surface, -sx, -sy).expect("Failed to set source");
                cr.paint().expect("Failed to paint");
                (copy, sx + 10.0, sy + 10.0)
            })
        } else {
            None
        };

        if let Some((mut copy, px, py)) = surface_to_copy {
            // Internal copy
            *state_copy.pending_image.borrow_mut() = Some(copy_surface(&copy));
            *state_copy.pending_pos.borrow_mut() = (px, py);

            // System clipboard copy
            let display = gtk4::prelude::WidgetExt::display(&window_copy);
            let clipboard = display.clipboard();
            
            // Convert Cairo surface to GDK Texture
            // We need to write to a buffer and create a texture from it, or use more direct methods if available
            // Easiest is to go via PNG in memory if needed, but GTK4 has better ways.
            // Actually, we can create a Texture from the surface data.
            let width = copy.width();
            let height = copy.height();
            let stride = copy.stride();
            let data = copy.data().expect("Failed to get surface data");
            let bytes = glib::Bytes::from(&*data);
            let texture = gdk4::MemoryTexture::new(
                width,
                height,
                gdk4::MemoryFormat::B8g8r8a8Premultiplied, // Cairo Format::ARgb32 is typically this
                &bytes,
                stride as usize,
            );
            clipboard.set_texture(&texture);
        }
    });
    window.add_action(&copy_action);

    let state_paste = state.clone();
    let area_paste = drawing_area.clone();
    let window_paste = window.clone();
    let paste_action = SimpleAction::new("paste", None);
    paste_action.connect_activate(move |_, _| {
        commit_text_editor(&state_paste, &area_paste);
        let display = gtk4::prelude::WidgetExt::display(&window_paste);
        let clipboard = display.clipboard();
        let state_p = state_paste.clone();
        let area_p = area_paste.clone();
        
        clipboard.read_texture_async(None::<&gtk4::gio::Cancellable>, move |res: Result<Option<Texture>, glib::Error>| {
            match res {
                Ok(Some(texture)) => {
                    let width = texture.width();
                    let height = texture.height();
                    
                    let mut surface = ImageSurface::create(Format::ARgb32, width, height)
                        .expect("Failed to create surface");
                    
                    // Use TextureDownloader to ensure we get the data in the format Cairo expects (B8G8R8A8)
                    let mut downloader = gdk4::TextureDownloader::new(&texture);
                    downloader.set_format(gdk4::MemoryFormat::B8g8r8a8Premultiplied);
                    let (bytes, stride) = downloader.download_bytes();
                    
                    {
                        let surf_stride = surface.stride() as usize;
                        let mut surf_data = surface.data().expect("Failed to get surface data");
                        if stride == surf_stride {
                            surf_data.copy_from_slice(&bytes);
                        } else {
                            // Row by row copy
                            let row_size = (width * 4) as usize;
                            for y in 0..height as usize {
                                let surf_offset = y * surf_stride;
                                let bytes_offset = y * stride;
                                surf_data[surf_offset..surf_offset + row_size]
                                    .copy_from_slice(&bytes[bytes_offset..bytes_offset + row_size]);
                            }
                        }
                    }
                    
                    *state_p.pending_image.borrow_mut() = Some(surface);
                    *state_p.pending_pos.borrow_mut() = (10.0, 10.0);
                    area_p.queue_draw();
                }
                Ok(None) => {
                    eprintln!("Clipboard does not contain an image");
                }
                Err(e) => {
                    eprintln!("Failed to read texture from clipboard: {}", e);
                }
            }
        });
    });
    window.add_action(&paste_action);

    let window_shortcuts = window.clone();
    let shortcuts_action = SimpleAction::new("shortcuts", None);
    shortcuts_action.connect_activate(move |_, _| {
        let ctrl_key = if cfg!(target_os = "macos") { "Cmd" } else { "Ctrl" };
        let comments = format!(
            "Keyboard Shortcuts:
- New: {0}+N
- Open: {0}+O
- Save: {0}+S
- Copy: {0}+C
- Paste: {0}+V
- Undo: {0}+Z
- Redo: {0}+Y
- Select All: {0}+A
- Crop: {0}+Shift+X",
            ctrl_key
        );
        let about = AboutDialog::builder()
            .transient_for(&window_shortcuts)
            .modal(true)
            .program_name("Rust Paint")
            .comments(comments)
            .build();
        about.present();
    });
    window.add_action(&shortcuts_action);

    let state_resize = state.clone();
    let area_resize = drawing_area.clone();
    let resize_action = SimpleAction::new("resize", Some(glib::VariantTy::new("(ii)").unwrap()));
    resize_action.connect_activate(move |_, param| {
        if let Some(v) = param {
            let (w, h): (i32, i32) = v.get().unwrap();
            if let Some(surface) = state_resize.surface.borrow().as_ref() {
                if surface.width() == w && surface.height() == h {
                    return;
                }
                state_resize.undo_stack.borrow_mut().push(copy_surface(surface));
                let new_surface = ImageSurface::create(Format::ARgb32, w, h)
                    .expect("Can't create surface");
                let cr = Context::new(&new_surface).expect("Failed to create context");
                cr.set_source_rgb(1.0, 1.0, 1.0);
                cr.paint().expect("Failed to paint");
                cr.set_source_surface(surface, 0.0, 0.0).expect("Failed to set source");
                cr.paint().expect("Failed to paint");
                *state_resize.surface.borrow_mut() = Some(new_surface);
                area_resize.queue_draw();
            }
        }
    });
    window.add_action(&resize_action);

    let window_exit = window.clone();
    let exit_action = SimpleAction::new("exit", None);
    exit_action.connect_activate(move |_, _| {
        window_exit.close();
    });
    window.add_action(&exit_action);

    // Menu Model
    let menu_model = Menu::new();
    
    let file_menu = Menu::new();
    file_menu.append(Some("New"), Some("win.new"));
    file_menu.append(Some("Open"), Some("win.open"));
    file_menu.append(Some("Save"), Some("win.save"));
    file_menu.append(Some("Exit"), Some("win.exit"));
    menu_model.append_section(Some("File"), &file_menu);

    let edit_menu = Menu::new();
    edit_menu.append(Some("Undo"), Some("win.undo"));
    edit_menu.append(Some("Redo"), Some("win.redo"));
    edit_menu.append(Some("Copy"), Some("win.copy"));
    edit_menu.append(Some("Paste"), Some("win.paste"));
    edit_menu.append(Some("Select All"), Some("win.select-all"));
    edit_menu.append(Some("Crop to Selection"), Some("win.crop"));
    menu_model.append_section(Some("Edit"), &edit_menu);

    let help_menu = Menu::new();
    help_menu.append(Some("Keyboard Shortcuts"), Some("win.shortcuts"));
    menu_model.append_section(Some("Help"), &help_menu);

    let menu_button = MenuButton::builder()
        .icon_name("open-menu-symbolic")
        .menu_model(&menu_model)
        .tooltip_text("Menu")
        .build();
    header_bar.pack_start(&menu_button);

    let main_vbox = Box::new(Orientation::Vertical, 0);
    window.set_child(Some(&main_vbox));

    let main_box = Box::new(Orientation::Horizontal, 0);
    main_box.set_vexpand(true);
    main_vbox.append(&main_box);

    let bottom_bar = Box::new(Orientation::Horizontal, 5);
    bottom_bar.set_margin_start(5);
    bottom_bar.set_margin_end(5);
    bottom_bar.set_margin_top(2);
    bottom_bar.set_margin_bottom(2);
    main_vbox.append(&bottom_bar);

    let ctrl_key = if cfg!(target_os = "macos") { "Cmd" } else { "Ctrl" };

    // Header Bar Actions (Simplified - most are in the Menu now)
    let new_btn = Button::builder().icon_name("document-new-symbolic").tooltip_text("Clear Canvas").build();
    new_btn.set_action_name(Some("win.new"));
    header_bar.pack_start(&new_btn);

    let save_btn = Button::builder().icon_name("document-save-symbolic").tooltip_text("Save as PNG").build();
    save_btn.set_action_name(Some("win.save"));
    header_bar.pack_start(&save_btn);

    let undo_btn = Button::builder().icon_name("edit-undo-symbolic").tooltip_text(format!("Undo ({}+Z)", ctrl_key)).build();
    undo_btn.set_action_name(Some("win.undo"));
    header_bar.pack_start(&undo_btn);

    let redo_btn = Button::builder().icon_name("edit-redo-symbolic").tooltip_text(format!("Redo ({}+Y)", ctrl_key)).build();
    redo_btn.set_action_name(Some("win.redo"));
    header_bar.pack_start(&redo_btn);

    let copy_btn = Button::builder().icon_name("edit-copy-symbolic").tooltip_text(format!("Copy ({}+C)", ctrl_key)).build();
    copy_btn.set_action_name(Some("win.copy"));
    header_bar.pack_start(&copy_btn);

    let paste_btn = Button::builder().icon_name("edit-paste-symbolic").tooltip_text(format!("Paste ({}+V)", ctrl_key)).build();
    paste_btn.set_action_name(Some("win.paste"));
    header_bar.pack_start(&paste_btn);

    let crop_to_sel_btn = Button::builder().icon_name("transform-crop-symbolic").tooltip_text(format!("Crop to Selection ({}+Shift+X)", ctrl_key)).build();
    crop_to_sel_btn.set_action_name(Some("win.crop"));
    header_bar.pack_end(&crop_to_sel_btn);

    let select_all_btn = Button::builder().icon_name("edit-select-all-symbolic").tooltip_text(format!("Select All ({}+A)", ctrl_key)).build();
    select_all_btn.set_action_name(Some("win.select-all"));
    header_bar.pack_end(&select_all_btn);

    let sidebar_scroll = ScrolledWindow::builder()
        .hscrollbar_policy(gtk4::PolicyType::Never)
        .build();
    sidebar_scroll.set_width_request(80);

    let sidebar = Box::new(Orientation::Vertical, 2);
    sidebar.set_margin_top(2);
    sidebar.set_margin_bottom(2);
    sidebar.set_margin_start(2);
    sidebar.set_margin_end(2);
    sidebar_scroll.set_child(Some(&sidebar));

    // CSS for styling
    let provider = CssProvider::new();
    provider.load_from_data("
        headerbar { min-height: 24px; padding: 1px 4px; }
        headerbar button { padding: 1px 3px; margin: 0 1px; }
        .sidebar { background-color: #f6f6f6; border-right: 1px solid #ddd; }
        .bottom-bar { background-color: #f6f6f6; border-top: 1px solid #ddd; padding: 2px 5px; }
        .tool-button { padding: 2px; min-width: 28px; min-height: 28px; }
        .color-swatch { min-width: 16px; min-height: 16px; border: 1px solid #ccc; border-radius: 2px; }
        .color-preview { min-width: 24px; min-height: 24px; border: 2px solid #888; border-radius: 4px; }
        .bottom-label { font-size: 0.85em; }
        label { font-weight: bold; font-size: 0.85em; }
        entry { min-height: 16px; padding: 2px; font-size: 0.85em; }
        .text-tool-entry { font-family: Sans; font-size: 20px; padding: 0; border: 1px solid #777; background: rgba(255, 255, 255, 0.8); min-height: 0; line-height: 1; }
        .text-tool-entry text { padding: 0; margin: 0; }
        scale { min-height: 20px; }
        button { font-size: 0.85em; }
    ");
    gtk4::style_context_add_provider_for_display(
        &gtk4::gdk::Display::default().expect("Could not connect to a display."),
        &provider,
        gtk4::STYLE_PROVIDER_PRIORITY_APPLICATION,
    );
    sidebar.add_css_class("sidebar");
    bottom_bar.add_css_class("bottom-bar");

    // sidebar.append(&Label::new(Some("Tools"))); // Compact
    let tool_grid = Grid::new();
    tool_grid.set_column_spacing(2);
    tool_grid.set_row_spacing(2);

    let tools = [
        ("pencil.png", Tool::Pencil, "Pencil"),
        ("line-tool.png", Tool::Line, "Line"),
        ("rectangle.png", Tool::Rect, "Rect"),
        ("rectangle_fill.png", Tool::RectFilled, "Rect Filled"),
        ("oval.png", Tool::Oval, "Oval"),
        ("oval_fill.png", Tool::OvalFilled, "Oval Filled"),
        ("rectangle.png", Tool::RoundedRect, "Rounded"), // Reuse rectangle for now
        ("rectangle_fill.png", Tool::RoundedRectFilled, "Rounded Filled"),
        ("eraser.png", Tool::Eraser, "Eraser"),
        ("bucket.png", Tool::Bucket, "Bucket"),
        ("move.png", Tool::Move, "Move"),
        ("highlight.png", Tool::Highlighter, "Highlighter"),
        ("arrow.png", Tool::Arrow, "Arrow"),
        ("text.png", Tool::Text, "Text"),
    ];

    for (i, (icon_name, tool, tooltip)) in tools.into_iter().enumerate() {
        let icon_path = format!("assets/{}", icon_name);
        let btn = if std::path::Path::new(&icon_path).exists() {
            let img = Image::from_file(&icon_path);
            img.set_pixel_size(24);
            Button::builder().child(&img).tooltip_text(tooltip).css_classes(["tool-button"]).build()
        } else {
            Button::builder().label(tooltip).tooltip_text(tooltip).css_classes(["tool-button"]).build()
        };

        let state_btn = state.clone();
        let area_btn = drawing_area.clone();
        btn.connect_clicked(move |_| {
            commit_text_editor(&state_btn, &area_btn);
            *state_btn.current_tool.borrow_mut() = tool;
        });
        tool_grid.attach(&btn, (i % 2) as i32, (i / 2) as i32, 1, 1);
    }
    sidebar.append(&tool_grid);

    // Color and Stroke moved to bottom_bar
    let color_hbox = Box::new(Orientation::Horizontal, 5);
    bottom_bar.append(&color_hbox);
    
    // Foreground color preview
    let color_preview = Button::builder().css_classes(["color-preview"]).valign(gtk4::Align::Center).build();
    let update_preview = {
        let color_preview = color_preview.clone();
        move |r: f64, g: f64, b: f64| {
            let swatch_provider = CssProvider::new();
            swatch_provider.load_from_data(&format!(
                "button {{ background: rgb({}, {}, {}); }}",
                (r * 255.0) as u8, (g * 255.0) as u8, (b * 255.0) as u8
            ));
            color_preview.style_context().add_provider(&swatch_provider, gtk4::STYLE_PROVIDER_PRIORITY_APPLICATION);
        }
    };
    update_preview(0.0, 0.0, 0.0);
    color_hbox.append(&color_preview);

    let color_grid = Grid::new();
    color_grid.set_column_spacing(1);
    color_grid.set_row_spacing(1);

    let colors = [
        (0.0, 0.0, 0.0), (0.0, 0.0, 1.0), (0.0, 1.0, 1.0), (0.33, 0.33, 0.33), (0.5, 0.5, 0.5), (0.0, 1.0, 0.0), (0.75, 0.75, 0.75), 
        (1.0, 0.0, 1.0), (1.0, 0.65, 0.0), (1.0, 0.75, 0.8), (1.0, 0.0, 0.0), (1.0, 1.0, 1.0), (1.0, 1.0, 0.0), (1.0, 1.0, 1.0)
    ];

    for (i, (r, g, b)) in colors.into_iter().enumerate() {
        let color_btn = Button::builder().css_classes(["color-swatch"]).build();
        
        let swatch_provider = CssProvider::new();
        swatch_provider.load_from_data(&format!(
            "button {{ background: rgb({}, {}, {}); }}",
            (r * 255.0) as u8, (g * 255.0) as u8, (b * 255.0) as u8
        ));
        color_btn.style_context().add_provider(&swatch_provider, gtk4::STYLE_PROVIDER_PRIORITY_APPLICATION);

        let state_color = state.clone();
        let up = update_preview.clone();
        color_btn.connect_clicked(move |_| {
            *state_color.stroke_color.borrow_mut() = (r, g, b, 1.0);
            up(r, g, b);
        });
        color_grid.attach(&color_btn, (i % 7) as i32, (i / 7) as i32, 1, 1);
    }
    color_hbox.append(&color_grid);

    let color_dialog = ColorDialog::new();
    let color_btn = ColorDialogButton::new(Some(color_dialog));
    color_btn.set_valign(gtk4::Align::Center);
    let state_color = state.clone();
    let up_dialog = update_preview.clone();
    color_btn.connect_rgba_notify(move |btn| {
        let rgba = btn.rgba();
        let (r, g, b, a) = (rgba.red() as f64, rgba.green() as f64, rgba.blue() as f64, rgba.alpha() as f64);
        *state_color.stroke_color.borrow_mut() = (r, g, b, a);
        up_dialog(r, g, b);
    });
    color_hbox.append(&color_btn);

    bottom_bar.append(&gtk4::Separator::new(Orientation::Vertical));

    let stroke_box = Box::new(Orientation::Horizontal, 5);
    stroke_box.append(&Label::new(Some("S:")));
    let stroke_scale = Scale::with_range(Orientation::Horizontal, 1.0, 20.0, 1.0);
    stroke_scale.set_width_request(80);
    stroke_scale.set_value(*state.stroke_size.borrow());
    stroke_scale.set_draw_value(false);
    let state_stroke = state.clone();
    stroke_scale.connect_value_changed(move |s| {
        *state_stroke.stroke_size.borrow_mut() = s.value();
    });
    stroke_box.append(&stroke_scale);
    
    stroke_box.append(&Label::new(Some("O:")));
    let opacity_scale = Scale::with_range(Orientation::Horizontal, 0.05, 1.0, 0.05);
    opacity_scale.set_width_request(80);
    opacity_scale.set_value(*state.highlighter_opacity.borrow());
    opacity_scale.set_draw_value(false);
    let state_opacity = state.clone();
    opacity_scale.connect_value_changed(move |s| {
        *state_opacity.highlighter_opacity.borrow_mut() = s.value();
    });
    stroke_box.append(&opacity_scale);
    bottom_bar.append(&stroke_box);

    bottom_bar.append(&gtk4::Separator::new(Orientation::Vertical));

    // Resize Controls
    let resize_hbox = Box::new(Orientation::Horizontal, 5);
    resize_hbox.append(&Label::new(Some("Canvas: W:")));
    let width_entry = gtk4::Entry::builder().width_chars(5).text("1200").build();
    resize_hbox.append(&width_entry);
    resize_hbox.append(&Label::new(Some("H:")));
    let height_entry = gtk4::Entry::builder().width_chars(5).text("640").build();
    resize_hbox.append(&height_entry);
    
    let resize_btn = Button::with_label("Resize");
    let window_res = window.clone();
    let w_entry = width_entry.clone();
    let h_entry = height_entry.clone();
    resize_btn.connect_clicked(move |_| {
        let w: i32 = w_entry.text().parse().unwrap_or(1200);
        let h: i32 = h_entry.text().parse().unwrap_or(640);
        ActionGroupExt::activate_action(&window_res, "resize", Some(&(w, h).to_variant()));
    });
    resize_hbox.append(&resize_btn);
    bottom_bar.append(&resize_hbox);

    // Current size label
    let size_label = Label::new(Some("Canvas: 1200 x 640"));
    size_label.add_css_class("bottom-label");
    size_label.set_margin_start(5);
    bottom_bar.append(&size_label);

    let state_clone = state.clone();
    let s_label = size_label.clone();
    drawing_area.set_draw_func(move |_area, cr, width, height| {
        s_label.set_text(&format!("Canvas: {} x {}", width, height));
        let mut surface_opt = state_clone.surface.borrow_mut();
        if surface_opt.is_none() {
            let surface = ImageSurface::create(Format::ARgb32, width, height)
                .expect("Can't create surface");
            
            // Fill with white background
            let cr_surface = Context::new(&surface).expect("Failed to create context");
            cr_surface.set_source_rgb(1.0, 1.0, 1.0);
            cr_surface.paint().expect("Failed to paint");
            
            *surface_opt = Some(surface);
        }

        if let Some(surface) = surface_opt.as_ref() {
            cr.set_source_surface(surface, 0.0, 0.0).expect("Failed to set source surface");
            cr.paint().expect("Failed to paint");
        }

        // Draw preview
        if let Some(temp_surface) = state_clone.temp_surface.borrow().as_ref() {
            cr.set_source_surface(temp_surface, 0.0, 0.0).expect("Failed to set source surface");
            cr.paint().expect("Failed to paint");
        }

        // Draw selection rectangle
        if let Some((sx, sy, sw, sh)) = *state_clone.selection_rect.borrow() {
            cr.set_source_rgba(0.0, 0.0, 1.0, 0.5);
            cr.set_dash(&[5.0], 0.0);
            cr.set_line_width(1.0);
            cr.rectangle(sx, sy, sw, sh);
            cr.stroke().expect("Failed to stroke selection");
            cr.set_dash(&[], 0.0);
        }

        // Draw pending image
        if let Some(pending) = state_clone.pending_image.borrow().as_ref() {
            let (px, py) = *state_clone.pending_pos.borrow();
            cr.set_source_surface(pending, px, py).expect("Failed to set pending source");
            cr.paint().expect("Failed to paint pending");
            
            // Selection frame around pending
            cr.set_source_rgba(0.0, 1.0, 0.0, 0.5);
            cr.set_dash(&[5.0], 0.0);
            cr.rectangle(px, py, pending.width() as f64, pending.height() as f64);
            cr.stroke().expect("Failed to stroke pending");
            cr.set_dash(&[], 0.0);
        }
    });

    let drag = GestureClick::new();
    let state_press = state.clone();
    let area_press = drawing_area.clone();
    let overlay_press = overlay.clone();
    drag.connect_pressed(move |_gesture, _n_press, x, y| {
        let tool = *state_press.current_tool.borrow();

        // If we have a pending image, check if we clicked inside it to move it, or outside to commit
        let (has_pending, should_commit, px, py, pw, ph) = {
            let pending_borrow = state_press.pending_image.borrow();
            if let Some(pending) = pending_borrow.as_ref() {
                let (px, py) = *state_press.pending_pos.borrow();
                let pw = pending.width() as f64;
                let ph = pending.height() as f64;
                if x >= px && x <= px + pw && y >= py && y <= py + ph {
                    (true, false, px, py, pw, ph)
                } else {
                    (true, true, px, py, pw, ph)
                }
            } else {
                (false, false, 0.0, 0.0, 0.0, 0.0)
            }
        };

        if has_pending {
            if !should_commit {
                // Clicked inside: start moving
                *state_press.is_drawing.borrow_mut() = true;
                *state_press.start_pos.borrow_mut() = (x - px, y - py); // Store offset
                return;
            } else {
                // Clicked outside: commit pending image
                if let Some(surface) = state_press.surface.borrow().as_ref() {
                    let cr = Context::new(surface).expect("Failed to create context");
                    let pending_borrow = state_press.pending_image.borrow();
                    if let Some(pending) = pending_borrow.as_ref() {
                        cr.set_source_surface(pending, px, py).expect("Failed to set source");
                        cr.paint().expect("Failed to paint");
                    }
                    *state_press.last_pasted_rect.borrow_mut() = Some((px, py, pw, ph));
                }
                *state_press.pending_image.borrow_mut() = None;
                area_press.queue_draw();
                // Continue with tool action
            }
        }
        
        // Push undo state before action
        if let Some(surface) = state_press.surface.borrow().as_ref() {
            state_press.undo_stack.borrow_mut().push(copy_surface(surface));
            state_press.redo_stack.borrow_mut().clear();
        }

        if tool == Tool::Bucket {
            if let Some(surface) = state_press.surface.borrow_mut().as_mut() {
                bucket_fill(surface, x as i32, y as i32, *state_press.stroke_color.borrow());
                area_press.queue_draw();
            }
            return;
        }

        *state_press.is_drawing.borrow_mut() = true;
        *state_press.start_pos.borrow_mut() = (x, y);
        *state_press.selection_rect.borrow_mut() = None;
        
        match tool {
            Tool::Pencil | Tool::Eraser | Tool::Highlighter => {
                if let Some(surface) = state_press.surface.borrow().as_ref() {
                    let cr = Context::new(surface).expect("Failed to create context");
                    let color = state_press.stroke_color.borrow();
                    if tool == Tool::Eraser {
                        cr.set_source_rgb(1.0, 1.0, 1.0);
                    } else {
                        cr.set_source_rgba(color.0, color.1, color.2, color.3);
                    }
                    cr.set_line_width(*state_press.stroke_size.borrow());
                    cr.set_line_cap(cairo::LineCap::Round);
                    cr.move_to(x, y);
                    cr.line_to(x, y);
                    cr.stroke().expect("Failed to stroke");
                    area_press.queue_draw();
                }
            }
            Tool::Text => {
                commit_text_editor(&state_press, &area_press);
                
                let entry = Entry::builder()
                    .margin_top(y as i32)
                    .margin_start(x as i32)
                    .css_classes(["text-tool-entry"])
                    .width_chars(10)
                    .build();

                entry.connect_changed(|e| {
                    let len = e.text().chars().count();
                    e.set_width_chars(10.max(len as i32 + 1));
                });
                
                let state_entry = state_press.clone();
                let area_entry = area_press.clone();
                entry.connect_activate(move |_| {
                    commit_text_editor(&state_entry, &area_entry);
                });
                
                let state_key = state_press.clone();
                let area_key = area_press.clone();
                let key_controller = gtk4::EventControllerKey::new();
                key_controller.connect_key_pressed(move |_, key, _, _| {
                    if key == Key::Escape {
                        let entry_opt = state_key.text_entry.borrow_mut().take();
                        if let Some(entry) = entry_opt {
                            entry.unparent();
                        }
                        area_key.queue_draw();
                        glib::Propagation::Stop
                    } else {
                        glib::Propagation::Proceed
                    }
                });
                entry.add_controller(key_controller);

                let state_focus = state_press.clone();
                let area_focus = area_press.clone();
                let focus_controller = gtk4::EventControllerFocus::new();
                focus_controller.connect_leave(move |_| {
                    commit_text_editor(&state_focus, &area_focus);
                });
                entry.add_controller(focus_controller);

                overlay_press.add_overlay(&entry);
                entry.grab_focus();
                
                *state_press.text_entry.borrow_mut() = Some(entry);
                *state_press.start_pos.borrow_mut() = (x, y);
                *state_press.is_drawing.borrow_mut() = false;
                return;
            }
            _ => {}
        }
    });

    let state_release = state.clone();
    let area_release = drawing_area.clone();
    drag.connect_released(move |_gesture, _n_press, x, y| {
        if !*state_release.is_drawing.borrow() { return; }
        *state_release.is_drawing.borrow_mut() = false;
        
        let tool = *state_release.current_tool.borrow();

        // If we were moving a pending image, we are done
        if state_release.pending_image.borrow().is_some() {
            return;
        }

        match tool {
            Tool::Line | Tool::Rect | Tool::Oval | Tool::RoundedRect | Tool::RectFilled | Tool::OvalFilled | Tool::RoundedRectFilled | Tool::Arrow => {
                // Commit preview to main surface
                if let Some(surface) = state_release.surface.borrow().as_ref() {
                    let cr = Context::new(surface).expect("Failed to create context");
                    let start = *state_release.start_pos.borrow();
                    draw_shape(&cr, tool, start.0, start.1, x, y, &state_release);
                }
                *state_release.temp_surface.borrow_mut() = None;
                area_release.queue_draw();
            }
            Tool::Move => {
                let start = *state_release.start_pos.borrow();
                let sx = start.0.min(x);
                let sy = start.1.min(y);
                let sw = (start.0 - x).abs();
                let sh = (start.1 - y).abs();
                if sw > 0.0 && sh > 0.0 {
                    *state_release.selection_rect.borrow_mut() = Some((sx, sy, sw, sh));
                }
                *state_release.temp_surface.borrow_mut() = None;
                area_release.queue_draw();
            }
            _ => {}
        }
    });

    let motion = EventControllerMotion::new();
    let state_motion = state.clone();
    let area_motion = drawing_area.clone();
    motion.connect_motion(move |_controller, x, y| {
        if *state_motion.is_drawing.borrow() {
            let tool = *state_motion.current_tool.borrow();

            // If we are moving a pending image
            if state_motion.pending_image.borrow().is_some() {
                let offset = *state_motion.start_pos.borrow();
                *state_motion.pending_pos.borrow_mut() = (x - offset.0, y - offset.1);
                area_motion.queue_draw();
                return;
            }

            match tool {
                Tool::Pencil | Tool::Eraser | Tool::Highlighter => {
                    if let Some(surface) = state_motion.surface.borrow().as_ref() {
                        let cr = Context::new(surface).expect("Failed to create context");
                        let color = state_motion.stroke_color.borrow();
                        if tool == Tool::Eraser {
                            cr.set_source_rgb(1.0, 1.0, 1.0);
                        } else if tool == Tool::Highlighter {
                            cr.set_source_rgba(color.0, color.1, color.2, *state_motion.highlighter_opacity.borrow());
                        } else {
                            cr.set_source_rgba(color.0, color.1, color.2, color.3);
                        }
                        cr.set_line_width(*state_motion.stroke_size.borrow());
                        cr.set_line_cap(cairo::LineCap::Round);
                        
                        let mut last_pos = state_motion.start_pos.borrow_mut();
                        cr.move_to(last_pos.0, last_pos.1);
                        cr.line_to(x, y);
                        cr.stroke().expect("Failed to stroke");
                        *last_pos = (x, y);
                        area_motion.queue_draw();
                    }
                }
                Tool::Line | Tool::Rect | Tool::Oval | Tool::RoundedRect | Tool::RectFilled | Tool::OvalFilled | Tool::RoundedRectFilled | Tool::Arrow | Tool::Text => {
                    // Update preview surface
                    if let Some(surface) = state_motion.surface.borrow().as_ref() {
                        let temp_surface = ImageSurface::create(Format::ARgb32, surface.width(), surface.height())
                            .expect("Can't create temp surface");
                        let cr = Context::new(&temp_surface).expect("Failed to create context");
                        let start = *state_motion.start_pos.borrow();
                        draw_shape(&cr, tool, start.0, start.1, x, y, &state_motion);
                        *state_motion.temp_surface.borrow_mut() = Some(temp_surface);
                        area_motion.queue_draw();
                    }
                }
                Tool::Move => {
                    // Draw selection rectangle preview
                    if let Some(surface) = state_motion.surface.borrow().as_ref() {
                        let temp_surface = ImageSurface::create(Format::ARgb32, surface.width(), surface.height())
                            .expect("Can't create temp surface");
                        let cr = Context::new(&temp_surface).expect("Failed to create context");
                        let start = *state_motion.start_pos.borrow();
                        cr.set_source_rgba(0.0, 0.0, 1.0, 0.5);
                        cr.set_dash(&[5.0], 0.0);
                        cr.rectangle(start.0.min(x), start.1.min(y), (start.0 - x).abs(), (start.1 - y).abs());
                        cr.stroke().expect("Failed to stroke selection preview");
                        *state_motion.temp_surface.borrow_mut() = Some(temp_surface);
                        area_motion.queue_draw();
                    }
                }
                _ => {}
            }
        }
    });

    drawing_area.add_controller(drag);
    drawing_area.add_controller(motion);

    main_box.append(&sidebar_scroll);
    main_box.append(&overlay);

    // Keyboard Shortcuts (Accelerators)
    let primary = if cfg!(target_os = "macos") { "<Meta>" } else { "<Primary>" };
    
    app.set_accels_for_action("win.new", &[&format!("{}n", primary), "<Primary>n"]);
    app.set_accels_for_action("win.open", &[&format!("{}o", primary), "<Primary>o"]);
    app.set_accels_for_action("win.save", &[&format!("{}s", primary), "<Primary>s"]);
    app.set_accels_for_action("win.copy", &[&format!("{}c", primary), "<Primary>c"]);
    app.set_accels_for_action("win.paste", &[&format!("{}v", primary), "<Primary>v"]);
    app.set_accels_for_action("win.undo", &[&format!("{}z", primary), "<Primary>z"]);
    app.set_accels_for_action("win.redo", &[&format!("{}y", primary), "<Primary>y"]);
    app.set_accels_for_action("win.select-all", &[&format!("{}a", primary), "<Primary>a"]);
    app.set_accels_for_action("win.crop", &[&format!("{}<Shift>x", primary), "<Primary><Shift>x"]);
    app.set_accels_for_action("win.exit", &[&format!("{}q", primary), "<Primary>q"]);

    window.present();
}

fn copy_surface(surface: &ImageSurface) -> ImageSurface {
    let copy = ImageSurface::create(surface.format(), surface.width(), surface.height())
        .expect("Can't create surface copy");
    let cr = Context::new(&copy).expect("Failed to create context");
    cr.set_source_surface(surface, 0.0, 0.0).expect("Failed to set source surface");
    cr.paint().expect("Failed to paint");
    copy
}

fn commit_text_editor(state: &Rc<AppState>, area: &DrawingArea) {
    let entry_opt = state.text_entry.borrow_mut().take();
    if let Some(entry) = entry_opt {
        let text = entry.text();
        if !text.is_empty() {
            if let Some(surface) = state.surface.borrow().as_ref() {
                state.undo_stack.borrow_mut().push(copy_surface(surface));
                let cr = Context::new(surface).expect("Failed to create context");
                
                let color = state.stroke_color.borrow();
                cr.set_source_rgba(color.0, color.1, color.2, color.3);
                
                // Using a default font size and face for now, similar to what was in draw_shape
                cr.select_font_face("Sans", cairo::FontSlant::Normal, cairo::FontWeight::Normal);
                let font_size = 20.0;
                cr.set_font_size(font_size);
                
                let extents = cr.font_extents().expect("Failed to get font extents");
                
                // Position the text. Entry's x,y corresponds to the top-left.
                // Cairo's show_text uses the baseline.
                let (x, y) = *state.start_pos.borrow();
                cr.move_to(x, y + extents.ascent());
                cr.show_text(&text).expect("Failed to show text");
                
                state.redo_stack.borrow_mut().clear();
            }
        }
        entry.unparent();
        area.queue_draw();
    }
}

fn draw_shape(cr: &Context, tool: Tool, x1: f64, y1: f64, x2: f64, y2: f64, state: &AppState) {
    let color = state.stroke_color.borrow();
    cr.set_source_rgba(color.0, color.1, color.2, color.3);
    cr.set_line_width(*state.stroke_size.borrow());
    
    let x = x1.min(x2);
    let y = y1.min(y2);
    let w = (x1 - x2).abs();
    let h = (y1 - y2).abs();

    match tool {
        Tool::Line => {
            cr.move_to(x1, y1);
            cr.line_to(x2, y2);
            cr.stroke().expect("Failed to stroke");
        }
        Tool::Rect | Tool::RectFilled => {
            cr.rectangle(x, y, w, h);
            if tool == Tool::RectFilled {
                cr.fill().expect("Failed to fill");
            } else {
                cr.stroke().expect("Failed to stroke");
            }
        }
        Tool::Oval | Tool::OvalFilled => {
            cr.save().expect("Failed to save");
            cr.translate(x + w / 2.0, y + h / 2.0);
            cr.scale(w / 2.0, h / 2.0);
            cr.arc(0.0, 0.0, 1.0, 0.0, 2.0 * std::f64::consts::PI);
            cr.restore().expect("Failed to restore");
            if tool == Tool::OvalFilled {
                cr.fill().expect("Failed to fill");
            } else {
                cr.stroke().expect("Failed to stroke");
            }
        }
        Tool::RoundedRect | Tool::RoundedRectFilled => {
            let radius = 10.0;
            let degrees = std::f64::consts::PI / 180.0;
            cr.new_sub_path();
            cr.arc(x + w - radius, y + radius, radius, -90.0 * degrees, 0.0 * degrees);
            cr.arc(x + w - radius, y + h - radius, radius, 0.0 * degrees, 90.0 * degrees);
            cr.arc(x + radius, y + h - radius, radius, 90.0 * degrees, 180.0 * degrees);
            cr.arc(x + radius, y + radius, radius, 180.0 * degrees, 270.0 * degrees);
            cr.close_path();
            if tool == Tool::RoundedRectFilled {
                cr.fill().expect("Failed to fill");
            } else {
                cr.stroke().expect("Failed to stroke");
            }
        }
        Tool::Highlighter => {
            cr.set_source_rgba(color.0, color.1, color.2, *state.highlighter_opacity.borrow());
            cr.move_to(x1, y1);
            cr.line_to(x2, y2);
            cr.stroke().expect("Failed to stroke");
        }
        Tool::Arrow => {
            // Draw line
            cr.move_to(x1, y1);
            cr.line_to(x2, y2);
            cr.stroke().expect("Failed to stroke");
            // Draw arrowhead
            let angle = (y2 - y1).atan2(x2 - x1);
            let arrow_len = 15.0;
            cr.move_to(x2, y2);
            cr.line_to(x2 - arrow_len * (angle - 30.0 * std::f64::consts::PI / 180.0).cos(),
                       y2 - arrow_len * (angle - 30.0 * std::f64::consts::PI / 180.0).sin());
            cr.move_to(x2, y2);
            cr.line_to(x2 - arrow_len * (angle + 30.0 * std::f64::consts::PI / 180.0).cos(),
                       y2 - arrow_len * (angle + 30.0 * std::f64::consts::PI / 180.0).sin());
            cr.stroke().expect("Failed to stroke");
        }
        Tool::Text => {}
        _ => {}
    }
}

fn bucket_fill(surface: &mut ImageSurface, x: i32, y: i32, color: (f64, f64, f64, f64)) {
    let width = surface.width();
    let height = surface.height();
    let stride = surface.stride() as usize;
    
    if x < 0 || x >= width || y < 0 || y >= height {
        return;
    }

    let mut data = surface.data().expect("Can't get surface data");
    
    let get_pixel = |data: &[u8], x: i32, y: i32| -> (u8, u8, u8, u8) {
        let offset = (y as usize * stride) + (x as usize * 4);
        (data[offset + 2], data[offset + 1], data[offset], data[offset + 3]) // B G R A in Cairo
    };

    let target_color = get_pixel(&data, x, y);
    let fill_color = (
        (color.0 * 255.0) as u8,
        (color.1 * 255.0) as u8,
        (color.2 * 255.0) as u8,
        (color.3 * 255.0) as u8,
    );

    if target_color == fill_color {
        return;
    }

    let mut stack = VecDeque::new();
    stack.push_back((x, y));

    while let Some((cx, cy)) = stack.pop_back() {
        let offset = (cy as usize * stride) + (cx as usize * 4);
        if get_pixel(&data, cx, cy) == target_color {
            data[offset] = fill_color.2;     // R
            data[offset + 1] = fill_color.1; // G
            data[offset + 2] = fill_color.0; // B
            data[offset + 3] = fill_color.3; // A

            if cx > 0 { stack.push_back((cx - 1, cy)); }
            if cx < width - 1 { stack.push_back((cx + 1, cy)); }
            if cy > 0 { stack.push_back((cx, cy - 1)); }
            if cy < height - 1 { stack.push_back((cx, cy + 1)); }
        }
    }
}
