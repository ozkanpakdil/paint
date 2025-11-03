package io.github.ozkanpakdil.paint;

import java.awt.event.KeyEvent;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;



public class Main extends JFrame {
	private GUI gui;
	
	public Main() throws IOException{
		initializeGUI();
		Menu();
		initializeWindow();
	}
	
	
	public void initializeGUI() throws IOException{
		gui=new GUI();
		add(gui);
	}
	
	public void initializeWindow(){
		setTitle("Paint");
		setName("mainFrame");
		setSize(1200, 640);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocation(100, 0);
		setResizable(true);
		setVisible(true);
	}
	public void Menu(){
		JMenuBar jMenuBar=new JMenuBar();
		JMenu file=new JMenu("File");
		JMenu edit=new JMenu("Edit");
  JMenu tools=new JMenu("Tools");
  tools.setMnemonic(KeyEvent.VK_T);
  file.setMnemonic(KeyEvent.VK_F);
  JMenuItem exitMenuItem=new JMenuItem("Exit");
  exitMenuItem.setMnemonic(KeyEvent.VK_F);
  exitMenuItem.setToolTipText("Exit ");
  exitMenuItem.addActionListener(e -> System.exit(0));
  JMenuItem newMenuItem=new JMenuItem("New");
  newMenuItem.setMnemonic(KeyEvent.VK_N);
  newMenuItem.setToolTipText("New");
  newMenuItem.addActionListener(e -> {
      if (gui != null) gui.getDrawArea().clearCanvas();
  });
  // Crop to Image Size menu item
  JMenuItem cropMenuItem=new JMenuItem("Crop to Image Size");
  cropMenuItem.setName("cropToImage");
  cropMenuItem.setToolTipText("Crop canvas to the last pasted image size");
  cropMenuItem.addActionListener(e -> {
      if (gui != null) gui.getDrawArea().cropToImageSize();
  });
  edit.add(cropMenuItem);

  // Tools > Move tool selector
  JMenuItem moveToolItem = new JMenuItem("Move");
  moveToolItem.setName("moveTool");
  moveToolItem.setToolTipText("Select Move tool");
  moveToolItem.addActionListener(e -> {
      if (gui != null) gui.getSideMenu().selectTool(Tool.MOVE);
  });
  tools.add(moveToolItem);

  file.add(newMenuItem);
  file.add(exitMenuItem);
  jMenuBar.add(file);
  jMenuBar.add(edit);
  jMenuBar.add(tools);
  setJMenuBar(jMenuBar);
	}
	
	public static void main(String[] args){
        // Workaround for GraalVM native image: AWT/Swing FontConfiguration requires 'java.home' to be set.
        if (System.getProperty("java.home") == null) {
            System.setProperty("java.home", "/");
        }

        System.setProperty("java.awt.headless", "false");
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.err.println("This application requires a graphical desktop session. Headless mode detected.\n" +
                    "Hint: On Linux, ensure you are running under X11/Wayland and that the DISPLAY variable is set (e.g., :0).\n" +
                    "Example: export DISPLAY=:0");
            System.exit(1);
        }
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ex) {
            // Fallback to default LAF if Nimbus is not available
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                new Main();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
	}
	
	
}