package com.github.jonmarsh;

import ij.CommandListener;
import ij.Executer;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.RoiListener;
import ij.gui.Toolbar;
import ij.io.SaveDialog;
import ij.plugin.PlugIn;
import ij.text.TextWindow;
import java.awt.Button;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;
import java.util.TreeMap;

/**
 *
 *
 * @author Jon N. Marsh
 */
public class Glomeruli_Labeler implements PlugIn, RoiListener, CommandListener, KeyListener {

	static final String PLUGIN_TITLE = "Glomeruli Labeler";
	ImagePlus imp;
	int width, height;
	String originalTitle, originalPath;
	static final int RANDOM_SEED = 3;
	static final int DEFAULT_FONT_SIZE = 14;
	static final Font DEFAULT_LABEL_FONT = new Font(Font.MONOSPACED, Font.BOLD, DEFAULT_FONT_SIZE);
	static final Color DEFAULT_LABEL_COLOR = Color.ORANGE;
	static final Color DEFAULT_NORMAL_COLOR = Color.GREEN;
	static final Color DEFAULT_SCLEROSED_COLOR = Color.RED;
	static final Color DEFAULT_UNCLASSIFIED_COLOR = Color.YELLOW;
	static Color[] defaultColors = new Color[]{DEFAULT_NORMAL_COLOR, DEFAULT_SCLEROSED_COLOR, DEFAULT_UNCLASSIFIED_COLOR};
	ArrayList<Color> annotationColors;
	static final String DEFAULT_NORMAL_LABEL = "N";
	static final String DEFAULT_SCLEROSED_LABEL = "GS";
	static final String DEFAULT_UNCLASSIFIED_LABEL = "X";
	static String[] defaultLabels = {DEFAULT_NORMAL_LABEL, DEFAULT_SCLEROSED_LABEL, DEFAULT_UNCLASSIFIED_LABEL};
	ArrayList<String> annotationLabels;
	HashMap<String, Integer> labelCount;
	CustomWindow customWindow;
	ImageCanvas canvas;
	Instant startTime;
	TreeMap<Instant, String> actions;
	static final String PLUGIN_OPENED = "plugin opened";
	static final String ADD_ANNOTATION = "annotation added";
	static final String REMOVE_ANNOTATION = "annotation removed";
	static final String CHANGE_ANNOTATION = "annotation changed";
	static final String LOAD_ANNOTATIONS = "annotations loaded";
	static final String CLEAR_ANNOTATIONS = "annotations cleared";
	StringBuilder log;

	@Override
	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.noImage();
			return;
		}
		if (imp.isHyperStack()) {
			IJ.error(PLUGIN_TITLE, "Does not work with hyperstacks.");
			return;
		}
		if (imp.getStackSize() > 1) {
			IJ.error(PLUGIN_TITLE, "Does not work with stacks");
			return;
		}
		log = new StringBuilder("Paneth cell annotation log");
		actions = new TreeMap();
		startTime = Instant.now();
		addActionToLog(startTime, PLUGIN_OPENED);

		IJ.run("Select None");
		IJ.run("Labels...", "color=white font=" + DEFAULT_FONT_SIZE + " show use bold");
		IJ.setTool(Toolbar.OVAL);
		originalTitle = imp.getTitle();
		width = imp.getWidth();
		height = imp.getHeight();
		Overlay overlay = imp.getOverlay();
		if (overlay == null) {
			overlay = new Overlay();
		}
		overlay.setLabelFont(DEFAULT_LABEL_FONT);
		overlay.setLabelColor(DEFAULT_LABEL_COLOR);
		overlay.drawLabels(true);
		overlay.drawNames(true);
		annotationLabels = new ArrayList<>();
		annotationColors = new ArrayList<>();
		annotationLabels.addAll(Arrays.asList(defaultLabels));
		annotationColors.addAll(Arrays.asList(defaultColors));
		Random rnd = new Random(RANDOM_SEED);
		Roi[] rois = overlay.toArray();
		for (Roi r : rois) {
			String name = r.getName();
			if (name != null) {
				if (!annotationLabels.contains(name)) { // if ROI name isn't already part of label list, add it
					annotationLabels.add(name);
					annotationColors.add(new Color(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat())); // set random color for new label
				}
			} else { // no name for this ROI, so set to unclassified
				name = DEFAULT_UNCLASSIFIED_LABEL;
				r.setName(name);
			}
			// set ROI stroke color appropriate for its label
			int i = annotationLabels.indexOf(name);
			if (i >= 0) {
				r.setStrokeColor(annotationColors.get(i));
			}
			if (r instanceof OvalRoi) {

			}
		}
		labelCount = new HashMap<>();
		updateLabelCount(true);

		canvas = new ImageCanvas(imp);
		customWindow = new CustomWindow(imp, canvas);
		customWindow.removeKeyListener(IJ.getInstance());
		canvas.removeKeyListener(IJ.getInstance());
		customWindow.addKeyListener(this);
		canvas.addKeyListener(this);
		Roi.addRoiListener(this);
		Executer.addCommandListener(this);

		canvas.requestFocus();
	}

	private void updateLabelCount(boolean resetBeforeCounting) {
		if (resetBeforeCounting) {
			labelCount.put(DEFAULT_NORMAL_LABEL, 0);
			labelCount.put(DEFAULT_SCLEROSED_LABEL, 0);
			labelCount.put(DEFAULT_UNCLASSIFIED_LABEL, 0);
		}
		Overlay overlay = imp.getOverlay();
		if (overlay != null) {
			Roi[] rois = overlay.toArray();
			if (rois != null) {
				for (Roi r : rois) {
					String label = r.getName();
					if (label != null && !label.equals("")) {
						Integer currentCount = labelCount.get(label);
						if (currentCount == null || currentCount <= 0) {
							labelCount.put(label, 1);
						} else {
							labelCount.put(label, currentCount + 1);
						}
					}
				}
			}
		}
		if (customWindow != null) {
			if (customWindow.countLabel != null) {
				String countText = "N = " + labelCount.get(DEFAULT_NORMAL_LABEL) + ", GS = " + labelCount.get(DEFAULT_SCLEROSED_LABEL) + ", X = " + labelCount.get(DEFAULT_UNCLASSIFIED_LABEL);
				customWindow.countLabel.setText(countText);
			}
		}
	}

	private void createNewAnnotationRoi(int x, int y, int width, int height, String label, Color strokeColor) {
		OvalRoi r = new OvalRoi(x, y, width, height);
		r.setName(label);
		r.setStrokeColor(strokeColor);
	}

	@Override
	public void roiModified(ImagePlus ip, int id) {
		String type = "UNKNOWN";
		switch (id) {
			case CREATED:
				type = "CREATED";
				break;
			case MOVED:
				type = "MOVED";
				break;
			case MODIFIED:
				type = "MODIFIED";
				break;
			case EXTENDED:
				type = "EXTENDED";
				break;
			case COMPLETED:
				type = "COMPLETED";
				break;
			case DELETED:
				type = "DELETED";
				break;
		}
		IJ.log("ROI Modified: " + ip.getRoi().getBounds().toString() + ", " + type);
	}

	private void removeListeners() {
		Roi.removeRoiListener(this);
		Executer.removeCommandListener(this);
		customWindow.removeKeyListener(this);
		canvas.removeKeyListener(this);
	}

	@Override
	public String commandExecuting(String command) {
		IJ.log("Executed \"" + command + "\" command");
		return command;
	}

	@Override
	public void keyPressed(KeyEvent e) {
		IJ.log(e.toString());
		if (imp != null) {
			switch (e.getKeyChar()) {
				case '=': // +/= key, zoom in
					IJ.run(imp, "In [+]", "");
					break;
				case '-': // -/_ key, zoom out
					IJ.run(imp, "Out [-]", "");
					break;
				case ' ': // space bar toggles magnification tool
					int toolID = Toolbar.getToolId();
					if (toolID == Toolbar.OVAL) {
						IJ.setTool(Toolbar.HAND);
					} else {
						IJ.setTool(Toolbar.OVAL);
					}
					break;
				default:
					break;
			}
			Roi roi = imp.getRoi();
			if (roi != null && roi instanceof OvalRoi) {
				if (e.getKeyChar() == 'n') {
					roi.setName(DEFAULT_NORMAL_LABEL);
					roi.setStrokeColor(DEFAULT_NORMAL_COLOR);
					IJ.run(imp, "Add Selection...", "");
					imp.killRoi();
					updateLabelCount(true);
				} else if (e.getKeyChar() == 'g') {
					roi.setName(DEFAULT_SCLEROSED_LABEL);
					roi.setStrokeColor(DEFAULT_SCLEROSED_COLOR);
					IJ.run(imp, "Add Selection...", "");
					imp.killRoi();
					updateLabelCount(true);
				} else if (e.getKeyCode() == 8) { // delete key, remove active ROI
					removeActiveRoiFromOverlay();
					imp.killRoi();
					updateLabelCount(true);
				} else if (e.getKeyCode() == 45) { // - key, zoom out
					IJ.run(imp, "Out [-]", "");
				}
			}
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void keyReleased(KeyEvent e) {
	}

	private void removeActiveRoiFromOverlay() {
		Overlay overlay = imp.getOverlay();
		Roi activeRoi = imp.getRoi();
		overlay.remove(activeRoi);
		imp.setOverlay(overlay);

	}

	class CustomWindow extends ImageWindow {

		Label countLabel;
		Button loadAnnotationButton, saveAnnotationButton, listAnnotationButton, clearAnnotationButton, finishButton;

		CustomWindow(ImagePlus imp, ImageCanvas ic) {
			super(imp, ic);
			setLayout(new FlowLayout());
			addPanel();
		}

		void addPanel() {
			Panel panel = new Panel();
			panel.setLayout(new GridLayout(10, 1));

			countLabel = new Label("N = " + labelCount.get(DEFAULT_NORMAL_LABEL) + ", GS = " + labelCount.get(DEFAULT_SCLEROSED_LABEL) + ", X = " + labelCount.get(DEFAULT_UNCLASSIFIED_LABEL));
			panel.add(countLabel);

			loadAnnotationButton = new Button("Load annotations...");
			loadAnnotationButton.addActionListener((ActionEvent e) -> {
				loadAnnotationTextFile();
			});
			panel.add(loadAnnotationButton);

			saveAnnotationButton = new Button("Save annotations...");
			saveAnnotationButton.addActionListener((ActionEvent e) -> {
				saveAnnotationTextFile();
			});
			panel.add(saveAnnotationButton);

			listAnnotationButton = new Button("List annotations");
			listAnnotationButton.addActionListener((ActionEvent e) -> {
				listAnnotations();
			});
			panel.add(listAnnotationButton);

			clearAnnotationButton = new Button("Clear annotations");
			clearAnnotationButton.addActionListener((ActionEvent e) -> {
				clearAnnotations();
			});
			panel.add(clearAnnotationButton);

			finishButton = new Button("Done");
			finishButton.addActionListener((ActionEvent e) -> {
				finish();
			});
			panel.add(finishButton);

			add(panel);
			pack();
		}

		void saveAnnotationTextFile() {
			Overlay overlay = imp.getOverlay();
			if (overlay == null || overlay.size() == 0) {
				IJ.showMessage("No annotations to save");
			} else {
				Roi[] rois = overlay.toArray();
				if (rois != null) {
					StringBuilder output = new StringBuilder("X\tY\tWidth\tHeight\tLabel");
					for (Roi roi : rois) {
						if (roi != null) {
							Rectangle r = roi.getBounds();
							output.append("\n").append(r.x).append("\t").append(r.y).append("\t").append(r.width).append("\t").append(r.height).append("\t").append(roi.getName());
						}
					}
					saveStringToFile(output.toString(), "Save Annotation Data...", "", imp.getTitle() + "-annotation_list");
				}
			}
		}

		void listAnnotations() {
			Overlay overlay = imp.getOverlay();
			if (overlay != null && overlay.size() != 0) {
				Roi[] rois = overlay.toArray();
				if (rois != null) {
					TextWindow textWindow = new TextWindow("Annotations", "X\tY\tWidth\tHeight\tLabel", "", 300, 800);
					for (Roi roi : rois) {
						if (roi != null) {
							Rectangle r = roi.getBounds();
							textWindow.append(r.x + "\t" + r.y + "\t" + r.width + "\t" + r.height + "\t" + roi.getName());
						}
					}
					canvas.requestFocus();
				}

			}
		}

		void loadAnnotationTextFile() {
			IJ.log("TODO: load annotation button press");
		}

		void clearAnnotations() {
			Overlay overlay = imp.getOverlay();
			if (overlay != null && overlay.size() != 0) {
				boolean clearAnnotations = IJ.showMessageWithCancel("Clear Annotations", "Clear all annotations?");
				if (clearAnnotations) {
					overlay.clear();
					imp.setOverlay(overlay);
					updateLabelCount(true);
					addActionToLog(Instant.now(), CLEAR_ANNOTATIONS);
				}
			}
		}

		void finish() {
			IJ.run(imp, "Select None", "");
			ImagePlus originalImp = imp.duplicate();
			originalImp.setTitle(originalTitle);
			originalImp.changes = true; // ensures user gets a prompt to save image when finished
			addActionToLog(Instant.now(), "finished");
			Duration totalTime = Duration.between(actions.firstKey(), actions.lastKey());
			log.append(String.format("\nTotal time: %d seconds", totalTime.toMillis() / 1000));
			originalImp.setProperty("Info", log.toString());
			//	originalImp.setOverlay(null);
			this.close();
			originalImp.show();
			Glomeruli_Labeler.this.removeListeners();
		}
	}

	String saveStringToFile(String string, String dialogTitle, String path, String defaultFileName) {
		if (path == null || path.equals("")) {
			SaveDialog sd = new SaveDialog(dialogTitle, defaultFileName, ".txt");
			String name = sd.getFileName();
			if (name == null) {
				return null;
			}
			path = sd.getDirectory() + name;
		}
		try (BufferedWriter out = new BufferedWriter(new FileWriter(path, false))) {
			out.write(string);
		} catch (IOException e) {
			return "" + e;
		}
		return null;
	}

	public void showAbout() {
		IJ.showMessage("Glomeruli Labeler", "A plugin for annotating glomeruli on whole slide images ");
	}

	String instantToPrettyString(Instant timestamp) {
		LocalDateTime ldt = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());
		return String.format("%s %d %d at %d:%d:%d", ldt.getMonth().getDisplayName(TextStyle.FULL, Locale.US), ldt.getDayOfMonth(), ldt.getYear(), ldt.getHour(), ldt.getMinute(), ldt.getSecond());
	}

	private void addActionToLog(Instant timeStamp, String description) {
		actions.put(timeStamp, description);
		log.append(System.lineSeparator()).append(instantToPrettyString(timeStamp)).append(":").append(description);
	}

	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ,
	 * loads an image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args unused
	 */
	public static void main(String[] args) {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		Class<?> c = Glomeruli_Labeler.class;
		String url = c.getResource("/" + c.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(), url.length() - c.getName().length() - ".class".length());
		System.setProperty("plugins.dir", pluginsDir);

		// start ImageJ
		new ImageJ();

		// open the Clown sample
		ImagePlus image = IJ.openImage();
		image.show();

		// run the plugin
		IJ.runPlugIn(c.getName(), "");
	}
}
