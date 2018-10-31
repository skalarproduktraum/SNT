/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2018 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package tracing.plot;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.jzy3d.bridge.awt.FrameAWT;
import org.jzy3d.chart.AWTChart;
import org.jzy3d.chart.Chart;
import org.jzy3d.chart.controllers.ControllerType;
import org.jzy3d.chart.controllers.camera.AbstractCameraController;
import org.jzy3d.chart.controllers.mouse.AWTMouseUtilities;
import org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController;
import org.jzy3d.chart.factories.IFrame;
import org.jzy3d.colors.Color;
import org.jzy3d.io.IGLLoader;
import org.jzy3d.io.obj.OBJFile;
import org.jzy3d.maths.BoundingBox3d;
import org.jzy3d.maths.Coord2d;
import org.jzy3d.maths.Coord3d;
import org.jzy3d.maths.Rectangle;
import org.jzy3d.plot3d.primitives.AbstractDrawable;
import org.jzy3d.plot3d.primitives.LineStrip;
import org.jzy3d.plot3d.primitives.Point;
import org.jzy3d.plot3d.primitives.Shape;
import org.jzy3d.plot3d.primitives.vbo.drawable.DrawableVBO;
import org.jzy3d.plot3d.rendering.canvas.ICanvas;
import org.jzy3d.plot3d.rendering.canvas.Quality;
import org.jzy3d.plot3d.rendering.legends.colorbars.AWTColorbarLegend;
import org.jzy3d.plot3d.rendering.lights.LightSet;
import org.jzy3d.plot3d.rendering.scene.Scene;
import org.jzy3d.plot3d.rendering.view.View;
import org.jzy3d.plot3d.rendering.view.ViewportMode;
import org.jzy3d.plot3d.rendering.view.modes.CameraMode;
import org.jzy3d.plot3d.rendering.view.modes.ViewBoundMode;
import org.jzy3d.plot3d.rendering.view.modes.ViewPositionMode;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.awt.AWTWindows;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;
import org.scijava.util.FileUtils;

import com.jidesoft.swing.CheckBoxList;
import com.jidesoft.swing.ListSearchable;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLException;

import ij.gui.HTMLDialog;
import net.imagej.ImageJ;
import net.imagej.display.ColorTables;
import net.imglib2.display.ColorTable;
import tracing.Path;
import tracing.SNT;
import tracing.SNTService;
import tracing.Tree;
import tracing.analysis.TreeColorizer;
import tracing.gui.GuiUtils;
import tracing.gui.IconFactory;
import tracing.gui.IconFactory.GLYPH;
import tracing.gui.cmds.ColorRampCmd;
import tracing.gui.cmds.LoadObjCmd;
import tracing.gui.cmds.LoadReconstructionCmd;
import tracing.gui.cmds.MLImporterCmd;
import tracing.gui.cmds.NMImporterCmd;
import tracing.util.PointInImage;


/**
 * Implements the SNT Reconstruction Viewer. Relies heavily on the
 * {@code org.jzy3d} package.
 * 
 * @author Tiago Ferreira
 */
public class TreePlot3D {

	private final static String ALLEN_MESH_LABEL = "MouseBrainAllen.obj";
	private final static String PATH_MANAGER_TREE_LABEL = "Path Manager Contents";
	private final static float DEF_NODE_RADIUS = 3f;
	private static final Color DEF_COLOR = new Color(1f, 1f, 1f, 0.05f);
	private static final Color INVERTED_DEF_COLOR = new Color(0f, 0f, 0f, 0.05f);

	/* Maps for plotted objects */
	private final Map<String, Shape> plottedTrees;
	private final Map<String, RemountableDrawableVBO> plottedObjs;

	/* Settings */
	private Color defColor;
	private float defThickness = DEF_NODE_RADIUS;
	private String screenshotDir;

	/* Color Bar */
	private AWTColorbarLegend cbar;
	private Shape cBarShape;

	/* Manager */
	private CheckBoxList managerList;
	private DefaultListModel<Object> managerModel;

	private Chart chart;
	private View view;
	private ViewerFrame frame;
	private GuiUtils gUtils;
	private KeyController keyController;
	private MouseController mouseController;
	private boolean viewUpdatesEnabled = true;
	final UUID uuid;

	@Parameter
	private CommandService cmdService;

	@Parameter
	private SNTService sntService;


	/**
	 * Instantiates a non-interactive TreePlot3D
	 */
	public TreePlot3D() {
		plottedTrees = new LinkedHashMap<>();
		plottedObjs = new LinkedHashMap<>();
		initView();
		setScreenshotDirectory("");
		initManagerList();
		uuid = UUID.randomUUID();
	}

	/**
	 * Instantiates an interactive TreePlot3D
	 * 
	 * @param context the SciJava application context providing the services
	 *                required by the class
	 */
	public TreePlot3D(final Context context) {
		this();
		context.inject(this);
	}

	/**
	 * Sets whether Plot's View should update (refresh) every time a new
	 * reconstruction (or mesh) is added/removed from the scene. Should be set to
	 * false when performing bulk operations;
	 *
	 * @param enabled Whether view updates should be enabled
	 */
	public void setViewUpdatesEnabled(final boolean enabled) {
		viewUpdatesEnabled = enabled;
	}

	private boolean chartExists() {
		return chart != null && chart.getCanvas() != null;
	}

	/* returns true if chart was initialized */
	private boolean initView() {
		if (chartExists())
			return false;
		chart = new AWTChart(Quality.Nicest); // There does not seem to be a swing implementation of
		// ICameraMouseController so we are stuck with AWT
		chart.black();
		view = chart.getView();
		view.setBoundMode(ViewBoundMode.AUTO_FIT);
		keyController = new KeyController(chart);
		mouseController = new MouseController(chart);
		chart.getCanvas().addKeyController(keyController);
		chart.getCanvas().addMouseController(mouseController);
		chart.setAxeDisplayed(false);
		view.getCamera().setViewportMode(ViewportMode.STRETCH_TO_FILL);
		gUtils = new GuiUtils((Component) chart.getCanvas());
		return true;
	}

	private void rebuild() {
		SNT.log("Rebuilding scene...");
		try {
			final boolean lighModeOn = !isDarkModeOn();
			chart.stopAnimator();
			chart.dispose();
			chart = null;
			initView();
			addAllObjects();
			updateView();
			if (lighModeOn) keyController.toggleDarkMode();
			managerList.selectAll();
		} catch (final GLException exc) {
			SNT.error("Rebuild Error", exc);
		}
		if (frame != null) frame.replaceCurrentChart(chart);
		updateView();
	}

	/**
	 * Checks if all drawables in the 3D scene are being rendered properly,
	 * rebuilding the entire scene if not. Useful to "hard-reset" the plot, e.g., to
	 * ensure all meshes are redraw.
	 * 
	 * @see #updateView()
	 */
	public void validate() {
		if (!sceneIsOK()) rebuild();
	}

	private boolean isDarkModeOn() {
		return view.getBackgroundColor() == Color.BLACK;
	}

	private void addAllObjects() {
		if (cBarShape != null && cbar != null) {
			chart.add(cBarShape, false);
			setColorbarColors(isDarkModeOn());
		}
		plottedObjs.forEach((k, drawableVBO) -> {
			drawableVBO.unmount();
			chart.add(drawableVBO, false);
		});
		plottedTrees.forEach((k, surface) -> {
			chart.add(surface, false);
		});
	}

	private void initManagerList() {
		managerModel = new DefaultListModel<Object>();
		managerList = new CheckBoxList(managerModel);
		managerModel.addElement(CheckBoxList.ALL_ENTRY);
		managerList.getCheckBoxListSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(final ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					final List<String> selectedKeys = getLabelsCheckedInManager();
					plottedTrees.forEach((k, surface) -> {
						surface.setDisplayed(selectedKeys.contains(k));
					});
					plottedObjs.forEach((k, drawableVBO) -> {
						drawableVBO.setDisplayed(selectedKeys.contains(k));
					});
					//view.shoot();
				}
			}
		});
	}

	private Color fromAWTColor(final java.awt.Color color) {
		return (color == null) ? getDefColor()
				: new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	private Color fromColorRGB(final ColorRGB color) {
		return (color == null) ? getDefColor()
				: new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	private String makeUniqueKey(final Map<String, ?> map, final String key) {
		for (int i = 2; i <= 100; i++) {
			final String candidate = key + " (" + i + ")";
			if (!map.containsKey(candidate)) return candidate;
		}
		return key + " (" + UUID.randomUUID() + ")";
	}

	private String getUniqueLabel(final Map<String, ?> map, final String fallbackPrefix, final String candidate) {
		final String label = (candidate == null || candidate.trim().isEmpty()) ? fallbackPrefix : candidate;
		return (map.containsKey(label)) ? makeUniqueKey(map, label) : label;
	}

	/**
	 * Adds a tree to this plot. Note that calling {@link #updateView()} may be
	 * required to ensure that the current View's bounding box includes the added
	 * Tree.
	 *
	 * @param tree the {@link Tree)} to be added. The Tree's label will be used as
	 *             identifier. It is expected to be unique when plotting multiple
	 *             Trees, if not (or no label exists) a unique label will be
	 *             generated.
	 * 
	 * @see {@link Tree#getLabel()}
	 * @see #remove(String)
	 * @see #updateView()
	 */
	public void add(final Tree tree) {
		final Shape surface = getShape(tree);
		final String label = getUniqueLabel(plottedTrees, "Tree ", tree.getLabel());
		plottedTrees.put(label, surface);
		addItemToManager(label);
		chart.add(surface, viewUpdatesEnabled);
	}

	private Shape getShape(final Tree tree) {
		final List<LineStrip> lines = new ArrayList<>();
		for (final Path p : tree.list()) {
			final LineStrip line = new LineStrip(p.size());
			for (int i = 0; i < p.size(); ++i) {
				final PointInImage pim = p.getPointInImage(i);
				final Coord3d coord = new Coord3d(pim.x, pim.y, pim.z);
				final Color color = fromAWTColor(p.hasNodeColors() ? p.getNodeColor(i) : p.getColor());
				final float width = Math.max((float) p.getNodeRadius(i), DEF_NODE_RADIUS);
				line.add(new Point(coord, color, width));
			}
			line.setShowPoints(true);
			line.setWireframeWidth(defThickness);
			lines.add(line);
		}

		// group all lines into a Composite
		final Shape surface = new Shape();
		surface.add(lines);
		surface.setFaceDisplayed(true);
		surface.setWireframeDisplayed(true);
		return surface;
	}

	private void addItemToManager(final String label) {
		final int[] indices = managerList.getCheckBoxListSelectedIndices();
		final int index = managerModel.size() - 1;
		managerModel.insertElementAt(label, index);
		//managerList.ensureIndexIsVisible(index);
		managerList.addCheckBoxListSelectedIndex(index);
		for (final int i : indices) managerList.addCheckBoxListSelectedIndex(i);
	}

	private boolean deleteItemFromManager(final String label) {
		return managerModel.removeElement(label);
	}

	/**
	 * Updates the plot's view, ensuring all objects are rendered within axes
	 * dimensions.
	 * @see #rebuild()
	 */
	public void updateView() {
		if (view != null) {
			view.shoot(); // !? without forceRepaint() dimensions are not updated
			view.lookToBox(view.getScene().getGraph().getBounds());
		}
	}

	/**
	 * Adds a color bar legend (LUT ramp).
	 *
	 * @param colorTable the color table
	 * @param min        the minimum value in the color table
	 * @param max        the maximum value in the color table
	 */
	public void addColorBarLegend(final ColorTable colorTable, final float min, final float max) {
		cBarShape = new Shape();
		cBarShape.setColorMapper(new ColorTableMapper(colorTable, min, max));
		cbar = new AWTColorbarLegend(cBarShape, view.getAxe().getLayout());
		setColorbarColors(view.getBackgroundColor() == Color.BLACK);
		// cbar.setMinimumSize(new Dimension(100, 600));
		cBarShape.setLegend(cbar);
		chart.add(cBarShape, viewUpdatesEnabled);
	}

	private void setColorbarColors(final boolean darkMode) {
		if (cbar == null)
			return;
		if (darkMode) {
			cbar.setBackground(Color.BLACK);
			cbar.setForeground(Color.WHITE);
		} else {
			cbar.setBackground(Color.WHITE);
			cbar.setForeground(Color.BLACK);
		}
	}

	/**
	 * Shows this TreePlot and returns a reference to its frame. If the frame has
	 * been made displayable, this will simply make the frame visible. Should only
	 * be called once all objects have been added to the Plot.
	 *
	 * @param showManager whether the 'Reconstruction Manager' dialog should be
	 *                    displayed. It is only respect the first time the method is
	 *                    called, i.e., when the frame is first made displayable.
	 * @return the frame containing the plot.
	 */
	public Frame show(final boolean showManager) {
		final boolean viewInitialized = initView();
		if (!viewInitialized && frame != null) {
			updateView();
			frame.setVisible(true);
			return frame;
		} else if (viewInitialized) {
			plottedTrees.forEach((k, surface) -> {
				chart.add(surface, viewUpdatesEnabled);
			});
			plottedObjs.forEach((k, drawableVBO) -> {
				chart.add(drawableVBO, viewUpdatesEnabled);
			});
		}
		frame = new ViewerFrame(chart, showManager);
		displayMsg("Press 'H' or 'F1' for help", 3000);
		return frame;
	}

	private void displayMsg(final String msg) {
		displayMsg(msg, 2500);
	}

	private void displayMsg(final String msg, final int msecs) {
		if (gUtils != null && chartExists()) {
			gUtils.setTmpMsgTimeOut(msecs);
			gUtils.tempMsg(msg);
		} else {
			System.out.println(msg);
		}
	}

	/**
	 * Returns the Collection of Trees in this plot.
	 *
	 * @return the plotted Trees (keys being the Tree identifier as per
	 *         {@link #add(Tree)})
	 */
	public Map<String, Shape> getTrees() {
		return plottedTrees;
	}

	/**
	 * Returns the Collection of OBJ meshes imported into this plot.
	 *
	 * @return the plotted Meshes (keys being the filename of the imported OBJ file
	 *         as per {@link #loadOBJ(String, ColorRGB, double)}
	 */
	public Map<String, DrawableVBO> getOBJs() {
		final Map<String,DrawableVBO> newMap =new LinkedHashMap<String,DrawableVBO>();
		plottedObjs.forEach((k, drawable) -> {
			newMap.put(k, (DrawableVBO) drawable);
		});
		return newMap;
	}

	/**
	 * Removes the specified Tree.
	 *
	 * @param treeLabel the key defining the tree to be removed.
	 * @return true, if tree was successfully removed.
	 * @see #add(Tree)
	 */
	public boolean remove(final String treeLabel) {
		return removeDrawable(plottedTrees, treeLabel);
	}

	/**
	 * Removes the specified OBJ mesh.
	 *
	 * @param meshLabel the key defining the OBJ mesh to be removed.
	 * @return true, if mesh was successfully removed.
	 * @see #loadOBJ(String, ColorRGB, double)
	 */
	public boolean removeOBJ(final String meshLabel) {
		return removeDrawable(plottedObjs, meshLabel);
	}

	/**
	 * Removes all loaded OBJ meshes from current plot
	 */
	public void removeAllOBJs() {
		removeAll(plottedObjs);
	}

	/**
	 * Removes all the Trees from current plot
	 */
	public void removeAll() {
		removeAll(plottedTrees);
	}

	private <T extends AbstractDrawable>void removeAll(final Map<String, T> map) {
		final Iterator<Entry<String, T>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry<String, T> entry = it.next();
			chart.getScene().getGraph().remove(entry.getValue(), false);
			managerModel.removeElement(entry.getKey());
			it.remove();
		}
		if (viewUpdatesEnabled) chart.render();
	}

	@SuppressWarnings("unchecked")
	private List<String> getLabelsCheckedInManager() {
		final Object[] values = managerList.getCheckBoxListSelectedValues();
		final List<String> list = (List<String>) (List<?>) Arrays.asList(values);
		return list;
	}

	private <T extends AbstractDrawable> boolean allDrawablesRendered(final BoundingBox3d viewBounds, 
			final Map<String, T> map, final List<String> selectedKeys) {
		final Iterator<Entry<String, T>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry<String, T> entry = it.next();
			final T drawable = entry.getValue();
			final BoundingBox3d bounds = drawable.getBounds();
			if (bounds == null || !viewBounds.contains(bounds)) return false;
			if ((selectedKeys.contains(entry.getKey()) && !drawable.isDisplayed())) {
				drawable.setDisplayed(true);
				if (!drawable.isDisplayed()) return false;
			}
		}
		return true;
	}

	private synchronized <T extends AbstractDrawable> boolean removeDrawable(final Map<String, T> map, final String label) {
		final T drawable = map.get(label);
		if (drawable == null)
			return false;
		boolean removed = map.remove(label) != null;
		if (chart != null) {
			removed = removed && chart.getScene().getGraph().remove(drawable, viewUpdatesEnabled);
			if (removed) deleteItemFromManager(label);
		}
		return removed;
	}

	/**
	 * (Re)loads the current list of Paths in the Path Manager list.
	 *
	 * @return true, if synchronization was apparently successful, false otherwise
	 * @throws UnsupportedOperationException if SNT is not running
	 */
	public boolean syncPathManagerList() throws UnsupportedOperationException{
		if (SNT.getPluginInstance() == null)
			throw new IllegalArgumentException("SNT is not running.");
		final Tree tree = new Tree(SNT.getPluginInstance().getPathAndFillManager().getPathsFiltered());
		if (plottedTrees.containsKey(PATH_MANAGER_TREE_LABEL)) {
			chart.getScene().getGraph().remove(plottedTrees.get(PATH_MANAGER_TREE_LABEL));
			final Shape newShape = getShape(tree);
			plottedTrees.put(PATH_MANAGER_TREE_LABEL, newShape);
			chart.add(newShape, viewUpdatesEnabled);
		} else {
			tree.setLabel(PATH_MANAGER_TREE_LABEL);
			add(tree);
		}
		updateView();
		return plottedTrees.get(PATH_MANAGER_TREE_LABEL).isDisplayed();
	}

	private boolean isValid(final AbstractDrawable drawable) {
		return drawable.getBounds() != null
				&& drawable.getBounds().getRange().distanceSq(new Coord3d(0f, 0f, 0f)) > 0f;
	}

	private boolean sceneIsOK() {
		try {
			updateView();
		} catch (final GLException ignored) {
			SNT.log("Upate view failed...");
			return false;
		}
		// now check that everything  is visible
		final List<String> selectedKeys = getLabelsCheckedInManager();
		final BoundingBox3d viewBounds = chart.view().getBounds();
		return allDrawablesRendered(viewBounds, plottedTrees, selectedKeys)
				&& allDrawablesRendered(viewBounds, plottedObjs, selectedKeys);
	}

	/** returns true if a drawable was removed */
	@SuppressWarnings("unused")
	private <T extends AbstractDrawable> boolean removeInvalid(final Map<String, T> map) {
		final Iterator<Entry<String, T>> it = map.entrySet().iterator();
		final int initialSize = map.size();
		while (it.hasNext()) {
			final Entry<String, T> entry = it.next();
			if (!isValid(entry.getValue())) {
				if (chart.getScene().getGraph().remove(entry.getValue(), false))
					deleteItemFromManager(entry.getKey());
				it.remove();
			}
		}
		return initialSize > map.size();
	}

	/**
	 * Toggles the visibility of a plotted Tree or a loaded OBJ mesh.
	 *
	 * @param treeOrObjLabel the unique identifier of the Tree (as per
	 *                       {@link #add(Tree)}), or the filename of the loaded OBJ
	 *                       {@link #loadOBJ(String, java.awt.Color)}
	 * @param visible        whether the Object should be displayed
	 */
	public void setVisible(final String treeOrObjLabel, final boolean visible) {
		final Shape tree = plottedTrees.get(treeOrObjLabel);
		if (tree != null)
			tree.setDisplayed(visible);
		final DrawableVBO obj = plottedObjs.get(treeOrObjLabel);
		if (obj != null)
			obj.setDisplayed(visible);
	}

	/**
	 * Sets the screenshot directory.
	 *
	 * @param screenshotDir the absolute file path of the screenshot saving
	 *                      directory. Set it to {@code null} to have screenshots
	 *                      saved in the default directory: the Desktop folder of
	 *                      the user's home directory
	 */
	public void setScreenshotDirectory(final String screenshotDir) {
		if (screenshotDir == null || screenshotDir.isEmpty()) {
			this.screenshotDir = System.getProperty("user.home") + File.separator + "Desktop";
		} else {
			this.screenshotDir = screenshotDir;
		}
	}

	/**
	 * Gets the screenshot directory.
	 *
	 * @return the screenshot directory
	 */
	public String getScreenshotDirectory() {
		return screenshotDir;
	}

	/**
	 * Saves a screenshot of current plot as a PNG image. Image is saved using an
	 * unique time stamp as a file name in the directory specified by
	 * {@link #getScreenshotDirectory()}
	 * 
	 *
	 * @return true, if successful
	 * @throws IllegalArgumentException if Viewer is not available, i.e.,
	 *                                  {@link #getView()} is null
	 */
	public boolean saveScreenshot() throws IllegalArgumentException {
		if (!chartExists()) {
			throw new IllegalArgumentException("Viewer is not visible");
		}
		final String file = new SimpleDateFormat("'SNT 'yyyy-MM-dd HH-mm-ss'.png'").format(new Date());
		try {
			final File f = new File(screenshotDir, file);
			SNT.log("Saving snapshot to " + f);
			chart.screenshot(f);
		} catch (final IOException e) {
			SNT.error("IOException", e);
			return false;
		}
		return true;
	}

	/**
	 * Loads a Wavefront .OBJ file. Files should be loaded _before_ displaying the
	 * scene, otherwise, if the scene is already visible, {@link #validate()} should
	 * be called to ensure all meshes are visible.
	 *
	 * @param filePath            the absolute file path (or URL) of the file to be
	 *                            imported. The filename is used as unique
	 *                            identifier of the object (see
	 *                            {@link #setVisible(String, boolean)})
	 * @param color               the color to render the imported file
	 * @param transparencyPercent the color transparency (in percentage)
	 * @throws IllegalArgumentException if filePath is invalid or file does not
	 *                                  contain a compilable mesh
	 */
	public void loadOBJ(final String filePath, final ColorRGB color, final double transparencyPercent)
			throws IllegalArgumentException {
		if (filePath == null || filePath.isEmpty()) {
			throw new IllegalArgumentException("Invalid file path");
		}
		final ColorRGB inputColor = (color == null) ? Colors.WHITE : color;
		final Color c = new Color(inputColor.getRed(), inputColor.getGreen(), inputColor.getBlue(),
				(int) Math.round((100 - transparencyPercent) * 255 / 100));
		SNT.log("Retrieving "+ filePath);
		final URL url;
		try {
			// see https://stackoverflow.com/a/402771
			if (filePath.startsWith("jar")) {
				final URL jarUrl = new URL(filePath);
				final JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
				url = connection.getJarFileURL();
			} else if (!filePath.startsWith("http")) {
				url = (new File(filePath)).toURI().toURL();
			} else {
				url = new URL(filePath);
			}
		} catch (final ClassCastException | IOException e) {
			throw new IllegalArgumentException("Invalid path: "+ filePath);
		}
		loadOBJ(url, c);
	}

	/**
	 * Loads the contour meshes for the Allen Mouse Brain Atlas. It will simply
	 * make the mesh visible if has already been loaded.
	 *
	 * @throws IllegalArgumentException if Viewer is not available, i.e.,
	 *                                  {@link #getView()} is null
	 */
	public void loadMouseRefBrain() throws IllegalArgumentException {
		if (getOBJs().keySet().contains(ALLEN_MESH_LABEL)) {
			setVisible(ALLEN_MESH_LABEL, true);
			return;
		}
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();
		final URL url = loader.getResource("meshes/" + ALLEN_MESH_LABEL);
		if (url == null)
			throw new IllegalArgumentException(ALLEN_MESH_LABEL + " not found");
		loadOBJ(url, getNonUserDefColor());
	}

	private Color getNonUserDefColor() {
		return (isDarkModeOn()) ? DEF_COLOR : INVERTED_DEF_COLOR;
	}

	private Color getDefColor() {
		return (defColor == null) ? getNonUserDefColor() : defColor; 
	}

	private void loadOBJ(final URL url, final Color color) throws IllegalArgumentException {
			final OBJFileLoaderPlus loader = new OBJFileLoaderPlus(url);
			if (!loader.compileModel()) {
				throw new IllegalArgumentException("Mesh could not be compiled. Invalid file?");
			}
			final RemountableDrawableVBO drawable = new RemountableDrawableVBO(loader);
			// drawable.setQuality(chart.getQuality());
			drawable.setColor(color);
			chart.add(drawable, false); // GLException if true
			final String label = loader.getLabel();
			plottedObjs.put(label, drawable);
			addItemToManager(label);
	}

	/**
	 * Returns this plot's {@link View} holding {@link Scene}, {@link LightSet},
	 * {@link ICanvas}, etc.
	 * 
	 * @return this plot's View, or null if it was disposed after {@link #show()}
	 *         has been called
	 */
	public View getView() {
		return (chart == null) ? null : view;
	}

	//NB: MouseContoller does not seem to work with FrameSWing so we are stuck with AWT
	private class ViewerFrame extends FrameAWT implements IFrame {

		private static final long serialVersionUID = 1L;
		private Chart chart;
		private Component canvas;
		private JDialog manager;

		/**
		 * Instantiates a new viewer frame.
		 *
		 * @param chart          the chart to be rendered in the frame
		 * @param includeManager whether the "Reconstruction Viewer Manager" dialog
		 *                       should be made visible
		 */
		public ViewerFrame(final Chart chart, final boolean includeManager) {
			final String title = (isSNTInstance()) ? " (SNT)" : "";
			initialize(chart, new Rectangle(800, 600), "Reconstruction Viewer" + title);
			if (includeManager) {
				manager = getManager();
				managerList.selectAll();
				manager.setVisible(true);
			}
			toFront();
		}

		public void replaceCurrentChart(final Chart chart) {
			this.chart = chart;
			canvas = (Component) chart.getCanvas();
			removeAll();
			add(canvas);
			//doLayout();
			revalidate();
			//update(getGraphics());
		}

		public JDialog getManager() {
			final JDialog dialog = new JDialog(this, "RV Controls");
			dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			//dialog.setLocationRelativeTo(this);
			final java.awt.Point parentLoc = getLocation();
			dialog.setLocation(parentLoc.x + getWidth() + 5, parentLoc.y);
			final JPanel panel = new ManagerPanel(new GuiUtils(dialog));
			dialog.setContentPane(panel);
			dialog.pack();
			return dialog;
		}

		/* (non-Javadoc)
		 * @see org.jzy3d.bridge.awt.FrameAWT#initialize(org.jzy3d.chart.Chart, org.jzy3d.maths.Rectangle, java.lang.String)
		 */
		@Override
		public void initialize(final Chart chart, final Rectangle bounds, final String title) {
			this.chart = chart;
			canvas = (Component) chart.getCanvas();
			setTitle(title);
			add(canvas);
			pack();
			setSize(new Dimension(bounds.width, bounds.height));
			AWTWindows.centerWindow(this);
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(final WindowEvent e) {
					ViewerFrame.this.remove(canvas);
					ViewerFrame.this.chart.dispose();
					ViewerFrame.this.chart = null;
					if (ViewerFrame.this.manager != null)
						ViewerFrame.this.manager.dispose();
					ViewerFrame.this.dispose();
				}
			});
			setVisible(true);
		}

		/* (non-Javadoc)
		 * @see org.jzy3d.bridge.awt.FrameAWT#initialize(org.jzy3d.chart.Chart, org.jzy3d.maths.Rectangle, java.lang.String, java.lang.String)
		 */
		@Override
		public void initialize(final Chart chart, final Rectangle bounds, final String title, final String message) {
			initialize(chart, bounds, title + message);
		}
	}

	private class ManagerPanel extends JPanel {

		private static final long serialVersionUID = 1L;
		private final GuiUtils guiUtils;

		public ManagerPanel(final GuiUtils guiUtils) {
			super();
			this.guiUtils = guiUtils;
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			final JScrollPane scrollPane = new JScrollPane(managerList);
			new ListSearchable(managerList);
			scrollPane.setBorder(null);
			scrollPane.setViewportView(managerList);
			add(scrollPane);
			scrollPane.revalidate();
			add(buttonPanel());
		}

		private JPanel buttonPanel() {
			final JPanel buttonPanel = new JPanel(new GridLayout(1, 3));
			buttonPanel.setBorder(null);
			// do not allow panel to resize vertically
			buttonPanel.setMaximumSize(new Dimension(buttonPanel.getMaximumSize().width,
					(int) buttonPanel.getPreferredSize().getHeight()));
			buttonPanel.add(menuButton(GLYPH.SYNC, reloadMenu(), "Reload/Synchronize"));
			buttonPanel.add(menuButton(GLYPH.ATOM, addMenu(), "Scene Elements"));
			buttonPanel.add(menuButton(GLYPH.SLIDERS, optionsMenu(), "Options & Customizations"));
			return buttonPanel;
		}

		private JButton menuButton(final GLYPH glyph, final JPopupMenu menu, final String tooltipMsg) {
			final JButton button = new JButton(IconFactory.getButtonIcon(glyph));
			button.setToolTipText(tooltipMsg);
			button.addActionListener(e -> menu.show(button, button.getWidth() / 2, button.getHeight() / 2));
			return button;
		}

		private JPopupMenu reloadMenu() {
			final JPopupMenu reloadMenu = new JPopupMenu();
			JMenuItem mi = new JMenuItem("Sync Path Manager Changes");
			mi.addActionListener(e -> {
				try {
					if (!syncPathManagerList()) rebuild();
					displayMsg("Path Manager contents updated");
				} catch (final IllegalArgumentException ex) {
					guiUtils.error(ex.getMessage());
				}
			});
			reloadMenu.add(mi);
			mi = new JMenuItem("Reload Scene/Update Bounds");
			mi.addActionListener(e -> {
				if (!sceneIsOK() && guiUtils.getConfirmation(
						"Scene was reloaded but some objects have invalid attributes. "//
						+ "Rebuild 3D Scene Completely?", "Rebuild Required")) {
					rebuild();
				} else {
					displayMsg("Scene reloaded");
				}
			});
			reloadMenu.add(mi);
			reloadMenu.addSeparator();
			mi = new JMenuItem("Rebuild Scene...");
			mi.addActionListener(e -> {
				if (guiUtils.getConfirmation("Rebuild 3D Scene Completely?", "Force Rebuild")) {
					rebuild();
				}
			});
			reloadMenu.add(mi);
			return reloadMenu;
		}

		private JPopupMenu optionsMenu() {
			final JPopupMenu optionsMenu = new JPopupMenu();
			JMenuItem mi = new JMenuItem("Path Thickness...");
			mi.addActionListener(e -> {
				if (plottedTrees.isEmpty()) {
					guiUtils.error("There are no loaded reconstructions");
					return;
				}
				String msg = "<HTML><body><div style='width:500;'>"
						+ "Please specify a constant thickness to be applied "
						+ "to all reconstructions.";
				if (isSNTInstance()) {
					msg += " This value will only affect how Paths are displayed "
							+ "in the Reconstruction Viewer.";
				}
				final Double thickness = guiUtils.getDouble(msg,
								"Path Thickness", getDefaultThickness());
				if (thickness == null) {
					return; // user pressed cancel
				}
				if (Double.isNaN(thickness) || thickness <= 0) {
					guiUtils.error("Invalid thickness value.");
					return;
				}
				applyThicknessToPlottedTrees(thickness.floatValue());
			});
			optionsMenu.add(mi);
			mi = new JMenuItem("Recolor Visible Meshes...");
			mi.addActionListener(e -> {
				if (plottedObjs.isEmpty()) {
					guiUtils.error("There are no loaded meshes.");
					return;
				}
				final Set<String> labels = plottedObjs.keySet();
				labels.retainAll(getLabelsCheckedInManager());
				if (labels.isEmpty()) {
					guiUtils.error("There are no visbile meshes.");
					return;
				}
				final java.awt.Color c = guiUtils.getColor("Mesh(es) Color", java.awt.Color.WHITE, "HSB");
				if (c == null) {
					return; // user pressed cancel
				}
				final Color color = fromAWTColor(c);
				for (final String label : labels) {
					plottedObjs.get(label).setColor(color);
				}
			});
			optionsMenu.add(mi);
			optionsMenu.addSeparator();
			mi = new JMenuItem("Screenshot Directory...");
			mi.addActionListener(e -> {
				final File oldDir = new File(getScreenshotDirectory());
				final File newDir = guiUtils
						.chooseDirectory("Choose Directory for saving Rec. Viewer's screenshots", oldDir);
				if (newDir != null) {
					final String newPath = newDir.getAbsolutePath();
					setScreenshotDirectory(newPath);
					displayMsg("Screenshot directory is now "+ FileUtils.limitPath(newPath, 50));
				}
			});
			optionsMenu.add(mi);
			optionsMenu.addSeparator();
			mi = new JMenuItem("Keyboard Operations...");
			mi.addActionListener(e -> keyController.showHelp(true));
			optionsMenu.add(mi);
			return optionsMenu;
		}

		private JPopupMenu addMenu() {
			final JPopupMenu addMenu = new JPopupMenu();
			final JMenu legendMenu = new JMenu("Color Legends");
			final JMenu meshMenu = new JMenu("Meshes");
			final JMenu tracesMenu = new JMenu("Reconstructions");
			addMenu.add(legendMenu);
			addMenu.add(meshMenu);
			addMenu.add(tracesMenu);

			// Traces Menu
			JMenuItem mi = new JMenuItem("Import File...");
			mi.addActionListener(e -> {
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("importDir", false);
				runCmd(LoadReconstructionCmd.class, inputs, CmdWorker.DO_NOTHING);
			});			tracesMenu.add(mi);
			mi = new JMenuItem("Import Directory...");
			mi.addActionListener(e -> {
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("importDir", true);
				runCmd(LoadReconstructionCmd.class, inputs, CmdWorker.DO_NOTHING);
			});
			tracesMenu.add(mi);
			tracesMenu.addSeparator();
			mi = new JMenuItem("Import from MouseLight...");
			mi.addActionListener(e -> runCmd(MLImporterCmd.class, null, CmdWorker.DO_NOTHING));
			tracesMenu.add(mi);
			mi = new JMenuItem("Import from NeuroMorpho...");
			mi.addActionListener(e -> runCmd(NMImporterCmd.class, null, CmdWorker.DO_NOTHING));
			tracesMenu.add(mi);
			tracesMenu.addSeparator();
			mi = new JMenuItem("Remove All...");
			mi.addActionListener(e -> {
				if (!guiUtils.getConfirmation("Remove all reconstructions from scene?", "Remove All Reconstructions?")) {
					return;
				}
				getOuter().removeAll();
			});
			tracesMenu.add(mi);

			// Legend Menu
			mi = new JMenuItem("Add...");
			mi.addActionListener(e -> runCmd(ColorRampCmd.class, null, CmdWorker.DO_NOTHING));
			legendMenu.add(mi);
			meshMenu.addSeparator();
			mi = new JMenuItem("Remove All...");
			mi.addActionListener(e -> {
				if (!guiUtils.getConfirmation("Remove all color legends from scene?", "Remove All Legends?")) {
					return;
				}
				final List<AbstractDrawable> allDrawables = chart.getScene().getGraph().getAll();
				final Iterator<AbstractDrawable> iterator = allDrawables.iterator();
				while(iterator.hasNext()) {
					final AbstractDrawable drawable = iterator.next();
					if (drawable != null && drawable.hasLegend() && drawable.isLegendDisplayed()) {
						iterator.remove();
					}
				}
				cbar = null;
				cBarShape = null;
			});
			legendMenu.add(mi);

			// Meshes Menu
			mi = new JMenuItem("Import OBJ File(s)...");
			mi.addActionListener(e -> runCmd(LoadObjCmd.class, null, CmdWorker.DO_NOTHING));
			meshMenu.add(mi);
			mi = new JMenuItem("Load Allen Mouse Brain Atlas Contour");
			mi.addActionListener(e -> {
				if (getOBJs().keySet().contains(ALLEN_MESH_LABEL)) {
					guiUtils.error(ALLEN_MESH_LABEL + " is already loaded.");
					managerList.addCheckBoxListSelectedValue(ALLEN_MESH_LABEL, true);
					return;
				}
				loadMouseRefBrain();
				getOuter().validate();
			});
			meshMenu.add(mi);
			meshMenu.addSeparator();
			mi = new JMenuItem("Remove All...");
			mi.addActionListener(e -> {
				if (!guiUtils.getConfirmation("Remove all meshes from scene?", "Remove All Meshes?")) {
					return;
				}
				removeAllOBJs();
			});
			meshMenu.add(mi);
			return addMenu;
		}

		private void runCmd(final Class<? extends Command> cmdClass, final Map<String, Object> inputs,
				final int cmdType) {
			if (cmdService == null) {
				guiUtils.error("This command requires Reconstruction Viewer to be aware of a Scijava Context");
				return;
			}
			SwingUtilities.invokeLater(() -> {
			(new CmdWorker(cmdClass, inputs, cmdType)).execute();});
		}
	}

	private TreePlot3D getOuter() {
		return this;
	}

	private class CmdWorker extends SwingWorker<Boolean, Object> {

		private static final int DO_NOTHING = 0;
		private static final int VALIDATE_SCENE = 1;

		private final Class<? extends Command> cmd;
		private final Map<String, Object> inputs;
		private final int type;

		public CmdWorker(final Class<? extends Command> cmd, 
				final Map<String, Object> inputs, final int type) {
			this.cmd = cmd;
			this.inputs = inputs;
			this.type = type;
		}

		@Override
		public Boolean doInBackground() {
			try {
				final Map<String, Object> input = new HashMap<>();
				input.put("recViewer", getOuter());
				if (inputs != null) input.putAll(inputs);
				cmdService.run(cmd, true, input).get();
				return true;
			} catch (final NullPointerException e1) {
				return false;
			} catch (InterruptedException | ExecutionException e2) {
				gUtils.error("Unfortunately an exception occured. See console for details.");
				SNT.error("Error", e2);
				return false;
			}
		}

		@Override
		protected void done() {
			boolean status = false;
			try {
				status = get();
				if (status) {
					switch (type) {
					case VALIDATE_SCENE:
						validate();
						break;
					case DO_NOTHING:
					default:
						break;
					}
				}
			} catch (final Exception ignored) {
				// do nothing
			}
		};
	}

	private class MouseController extends AWTCameraMouseController {

		private final float PAN_FACTOR = 1f; // lower values mean more responsive pan
		private boolean panDone;
		private Coord3d prevMouse3d;

		public MouseController(final Chart chart) {
			super(chart);
		}

		private int getY(final MouseEvent e) {
			return -e.getY() + chart.getCanvas().getRendererHeight();
		}

		private void rotateLive(final Coord2d move) {
			rotate(move, true);
		}

		/* see AWTMousePickingPan2dController */
		public void pan(final Coord3d from, final Coord3d to) {
			final BoundingBox3d viewBounds = view.getBounds();
			final Coord3d offset = to.sub(from).div(-PAN_FACTOR);
			final BoundingBox3d newBounds = viewBounds.shift(offset);
			view.setBoundManual(newBounds);
			view.shoot();
			fireControllerEvent(ControllerType.PAN, offset);
		}

		public void zoom(final float factor) {
			final BoundingBox3d viewBounds = view.getBounds();
			final BoundingBox3d newBounds = viewBounds.scale(new Coord3d(factor, factor, factor));
			view.setBoundManual(newBounds);
			view.shoot();
			fireControllerEvent(ControllerType.ZOOM, factor);
		}

		public void snapToNextView() {
			final ViewPositionMode[] modes = { ViewPositionMode.FREE, ViewPositionMode.PROFILE, ViewPositionMode.TOP };
			final String[] descriptions = { "Unconstrained", "Side Constrained", "Top Constrained" };
			final ViewPositionMode currentView = chart.getViewMode();
			int nextViewIdx = 0;
			for (int i = 0; i < modes.length; i++) {
				if (modes[i] == currentView) {
					nextViewIdx = i + 1;
					break;
				}
			}
			if (nextViewIdx == modes.length)
				nextViewIdx = 0;
			stopThreadController();
			chart.setViewMode(modes[nextViewIdx]);
			displayMsg("View Mode: " + descriptions[nextViewIdx]);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController#
		 * mousePressed(java.awt.event.MouseEvent)
		 */
		@Override
		public void mousePressed(final MouseEvent e) {
			if (e.isControlDown() && AWTMouseUtilities.isLeftDown(e)) {
				snapToNextView();
			} else {
				super.mousePressed(e);
			}
			prevMouse3d = view.projectMouse(e.getX(), getY(e));
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController#
		 * mouseWheelMoved(java.awt.event.MouseWheelEvent)
		 */
		@Override
		public void mouseWheelMoved(final MouseWheelEvent e) {
			stopThreadController();
			final float factor = 1 + (e.getWheelRotation() / 10.0f);
			zoom(factor);
			prevMouse3d = view.projectMouse(e.getX(), getY(e));
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController#
		 * mouseDragged(java.awt.event.MouseEvent)
		 */
		@Override
		public void mouseDragged(final MouseEvent e) {

			final Coord2d mouse = xy(e);

			// Rotate on left-click
			if (AWTMouseUtilities.isLeftDown(e)) {
				final Coord2d move = mouse.sub(prevMouse).div(100);
				rotate(move);
			}

			// Pan on right-click
			else if (AWTMouseUtilities.isRightDown(e)) {
				final Coord3d thisMouse3d = view.projectMouse(e.getX(), getY(e));
				if (!panDone) { // 1/2 pan for cleaner rendering
					pan(prevMouse3d, thisMouse3d);
					panDone = true;
				} else {
					panDone = false;
				}
				prevMouse3d = thisMouse3d;
			}
			prevMouse = mouse;
		}
	}

	private class KeyController extends AbstractCameraController implements KeyListener {

		private static final float STEP = 0.1f;

		public KeyController(final Chart chart) {
			register(chart);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent)
		 */
		@Override
		public void keyPressed(final KeyEvent e) {
			switch (e.getKeyChar()) {
			case 'a':
			case 'A':
				chart.setAxeDisplayed(!view.isAxeBoxDisplayed());
				break;
			case 'c':
			case 'C':
				changeCameraMode();
				break;
			case 'd':
			case 'D':
				toggleDarkMode();
				break;
			case 'h':
			case 'H':
				showHelp(false);
				break;
			case 'r':
			case 'R':
				chart.setViewPoint(View.DEFAULT_VIEW);
				chart.setViewMode(ViewPositionMode.FREE);
				view.setBoundMode(ViewBoundMode.AUTO_FIT);
				displayMsg("View reset");
				break;
			case 's':
			case 'S':
				saveScreenshot();
				displayMsg("Screenshot saved to "+ FileUtils.limitPath(getScreenshotDirectory(), 50));
				break;
			case '+':
			case '=':
				mouseController.zoom(0.9f);
				break;
			case '-':
			case '_':
				mouseController.zoom(1.1f);
				break;
			default:
				switch (e.getKeyCode()) {
				case KeyEvent.VK_F1:
					showHelp(true);
					break;
				case KeyEvent.VK_DOWN:
					mouseController.rotateLive(new Coord2d(0f, -STEP));
					break;
				case KeyEvent.VK_UP:
					mouseController.rotateLive(new Coord2d(0f, STEP));
					break;
				case KeyEvent.VK_LEFT:
					mouseController.rotateLive(new Coord2d(-STEP, 0));
					break;
				case KeyEvent.VK_RIGHT:
					mouseController.rotateLive(new Coord2d(STEP, 0));
					break;
				default:
					break;
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.awt.event.KeyListener#keyTyped(java.awt.event.KeyEvent)
		 */
		@Override
		public void keyTyped(final KeyEvent e) {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent)
		 */
		@Override
		public void keyReleased(final KeyEvent e) {
		}

		private void changeCameraMode() {
			final CameraMode newMode = (view.getCameraMode() == CameraMode.ORTHOGONAL) ? CameraMode.PERSPECTIVE
					: CameraMode.ORTHOGONAL;
			view.setCameraMode(newMode);
			final String mode = (newMode == CameraMode.ORTHOGONAL) ? "Orthogonal" : "Perspective";
			displayMsg("Camera mode changed to \"" + mode + "\"");
		}

		/* This seems to work only at initialization */
		@SuppressWarnings("unused")
		private void changeQuality() {
			final Quality[] levels = { Quality.Fastest, Quality.Intermediate, Quality.Advanced, Quality.Nicest };
			final String[] grades = { "Fastest", "Intermediate", "High", "Best" };
			final Quality currentLevel = chart.getQuality();
			int nextLevelIdx = 0;
			for (int i = 0; i < levels.length; i++) {
				if (levels[i] == currentLevel) {
					nextLevelIdx = i + 1;
					break;
				}
			}
			if (nextLevelIdx == levels.length)
				nextLevelIdx = 0;
			chart.setQuality(levels[nextLevelIdx]);
			displayMsg("Quality level changed to '" + grades[nextLevelIdx] + "'");
		}

		private void toggleDarkMode() {
			if (chart == null)
				return;
			Color newForeground;
			Color newBackground;
			if (view.getBackgroundColor() == Color.BLACK) {
				newForeground = Color.BLACK;
				newBackground = Color.WHITE;
				setColorbarColors(false);
			} else {
				newForeground = Color.WHITE;
				newBackground = Color.BLACK;
				setColorbarColors(true);
			}
			view.setBackgroundColor(newBackground);
			view.getAxe().getLayout().setGridColor(newForeground);
			view.getAxe().getLayout().setMainColor(newForeground);

			// Apply foreground color to trees with background color
			plottedTrees.values().forEach(shape -> {
				if (isSameRGB(shape.getColor(), newBackground)) {
					shape.setColor(newForeground);
					return; // replaces continue in lambda expression;
				}
				for (int i = 0; i < shape.size(); i++) {
					if (shape.get(i) instanceof LineStrip) {
						final List<Point> points = ((LineStrip) shape.get(i)).getPoints();
						points.stream().forEach(p -> {
							final Color pColor = p.getColor();
							if (isSameRGB(pColor, newBackground)) {
								changeRGB(pColor, newForeground);
							}
						});
					}
				}
			});

			// Apply foreground color to meshes with background color
			plottedObjs.values().forEach(obj -> {
				final Color objColor = obj.getColor();
				if (isSameRGB(objColor, newBackground)) {
					changeRGB(objColor, newForeground);
				}
			});

		}

		private boolean isSameRGB(final Color c1, final Color c2) {
			return c1 != null && c1.r == c2.r && c1.g == c2.g && c1.b == c2.b;
		}

		private void changeRGB(final Color from, final Color to) {
			from.r = to.r;
			from.g = to.g;
			from.b = to.b;
		}

		private void showHelp(final boolean showInDialog) {
			final StringBuffer sb = new StringBuffer("<HTML>");
			sb.append("<table>");
			sb.append("  <tr>");
			sb.append("    <td>Pan</td>");
			sb.append("    <td>Right-click &amp; drag</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Rotate</td>");
			sb.append("    <td>Left-click &amp; drag (or arrow keys)</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Scale</td>");
			sb.append("    <td>Scroll (or + / - keys)</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Animate</td>");
			sb.append("    <td>Double left-click</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Snap to View</td>");
			sb.append("    <td>Ctrl + left-click</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Toggle <u>A</u>xes</td>");
			sb.append("    <td>Press 'A'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Toggle <u>C</u>amera Mode &nbsp;</td>");
			sb.append("    <td>Press 'C'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Toggle <u>D</u>ark Mode</td>");
			sb.append("    <td>Press 'D'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td><u>R</u>eset View</td>");
			sb.append("    <td>Press 'R'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td><u>S</u>creenshot</td>");
			sb.append("    <td>Press 'S'</td>");
			sb.append("  </tr>");
			if (showInDialog) {
				sb.append("  <tr>");
				sb.append("    <td><u>H</u>elp</td>");
				sb.append("    <td>Press 'H' (notification) or F1 (list)</td>");
				sb.append("  </tr>");
			}
			sb.append("</table>");
			if (showInDialog) {
				new HTMLDialog("Reconstruction Viewer Shortcuts", sb.toString(), false);
			} else {
				displayMsg(sb.toString(), 9000);
			}
			
		}
	}



	
	/**
	 * This is just to make {@link DrawableVBO#hasMountedOnce()} accessible,
	 * allowing to force the re-loading of meshes during an interactive session
	 */
	private class RemountableDrawableVBO extends DrawableVBO {

		public RemountableDrawableVBO(final IGLLoader<DrawableVBO> loader) {
			super(loader);
		}

		public void unmount() {
			super.hasMountedOnce = false;
		}

	}

	/**
	 * This is a shameless version of {@link #OBJFileLoader} with extra methods that
	 * allow to check if OBJFile is valid before converting it into a Drawable #
	 */
	private class OBJFileLoaderPlus implements IGLLoader<DrawableVBO>{

		protected URL url;
		protected OBJFile obj;

		public OBJFileLoaderPlus(final URL url) {
			this.url = url;
			if (url == null) throw new IllegalArgumentException("Null URL");
		}

		public String getLabel() {
			String label = url.toString();
			label = label.substring(label.lastIndexOf("/") + 1);
			return getUniqueLabel(plottedObjs, "Mesh", label);
		}

		public boolean compileModel() {
			obj = new OBJFile();
			SNT.log("Loading OBJ file '" + url + "'");
			if (!obj.loadModelFromURL(url)) {
				SNT.log("Loading failed. Invalid file?");
				return false;
			}
			obj.compileModel();
			SNT.log(String.format("Meshed compiled: %d vertices and %d triangles", obj.getPositionCount(),
					(obj.getIndexCount() / 3)));
			return obj.getPositionCount() > 0;
		}

		@Override
		public void load(final GL gl, final DrawableVBO drawable) {
			final int size = obj.getIndexCount();
			final int indexSize = size * Buffers.SIZEOF_INT;
			final int vertexSize = obj.getCompiledVertexCount() * Buffers.SIZEOF_FLOAT;
			final int byteOffset = obj.getCompiledVertexSize() * Buffers.SIZEOF_FLOAT;
			final int normalOffset = obj.getCompiledNormalOffset() * Buffers.SIZEOF_FLOAT;
			final int dimensions = obj.getPositionSize();
			final int pointer = 0;
			final FloatBuffer vertices = obj.getCompiledVertices();
			final IntBuffer indices = obj.getCompiledIndices();
			final BoundingBox3d bounds = obj.computeBoundingBox();
			drawable.doConfigure(pointer, size, byteOffset, normalOffset, dimensions);
			drawable.doLoadArrayFloatBuffer(gl, vertexSize, vertices);
			drawable.doLoadElementIntBuffer(gl, indexSize, indices);
			drawable.doSetBoundingBox(bounds);
		}
	}

	/**
	 * Sets the line thickness for rendering {@link Tree}s that have no specified
	 * radius.
	 *
	 * @param thickness the new line thickness. Note that this value only applies to
	 *                  Paths that have no specified radius
	 */
	public void setDefaultThickness(final float thickness) {
		this.defThickness = thickness;
	}

	/**
	 * Sets the default color for rendering {@link Tree}s.
	 *
	 * @param color the new color. Note that this value only applies to Paths that
	 *              have no specified color and no colors assigned to its nodes
	 */
	public void setDefaultColor(final ColorRGB color) {
		this.defColor = fromColorRGB(color);
	}

	/**
	 * Returns the default line thickness.
	 *
	 * @return the default line thickness used to render Paths without radius
	 */
	private float getDefaultThickness() {
		return defThickness;
	}

	/**
	 * Applies a constant thickness (line width) to plotted trees.
	 *
	 * @param thickness the thickness
	 */
	protected void applyThicknessToPlottedTrees(final float thickness) {
		plottedTrees.values().forEach(shape -> {
			for (int i = 0; i < shape.size(); i++) {
				if (shape.get(i) instanceof LineStrip) {
					((LineStrip) shape.get(i)).setWireframeWidth(thickness);
				}
			}
		});
	}

	/**
	 * Applies a constant color to plotted meshes.
	 *
	 * @param color the color
	 */
	protected void applyColorToPlottedObjs(final Color color) {
		plottedObjs.values().forEach(drawable -> {
			drawable.setColor(color);
		});
	}

	public boolean isSNTInstance() {
		return sntService !=null && sntService.isActive() && sntService.getUI() != null
				&& this.equals(sntService.getUI().getReconstructionViewer(false));
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (o == null) return false;
		if (getClass() != o.getClass()) return false;
		return uuid.equals(((TreePlot3D)o).uuid);
	}

	/* IDE debug method */
	public static void main(final String[] args) throws InterruptedException {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Tree tree = new Tree("/home/tferr/code/test-files/AA0100.swc");
		final TreeColorizer colorizer = new TreeColorizer(ij.getContext());
		colorizer.colorize(tree, TreeColorizer.BRANCH_ORDER, ColorTables.ICE);
		final double[] bounds = colorizer.getMinMax();
		SNT.setDebugMode(true);
		final TreePlot3D jzy3D = new TreePlot3D(ij.context());
		jzy3D.addColorBarLegend(ColorTables.ICE, (float) bounds[0], (float) bounds[1]);
		jzy3D.add(tree);
		jzy3D.loadMouseRefBrain();
		jzy3D.show(true);
	}

}
