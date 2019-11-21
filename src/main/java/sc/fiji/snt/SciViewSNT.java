/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2019 Fiji developers.
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

package sc.fiji.snt;

import cleargl.GLVector;
import graphics.scenery.*;
import graphics.scenery.volumes.Volume;
import net.imagej.Dataset;
import net.imagej.ImageJ;

import org.scijava.Context;
import org.scijava.NullContextException;
import org.scijava.io.IOService;
import org.scijava.plugin.Parameter;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;
import sc.fiji.snt.util.PointInImage;
import sc.fiji.snt.util.SNTPoint;
import sc.iview.SciView;
import sc.iview.SciViewService;
import sc.iview.node.Line3D;
import sc.iview.vector.ClearGLVector3;
import sc.iview.vector.DoubleVector3;
import sc.iview.vector.FloatVector3;
import sc.iview.vector.Vector3;

import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;

import javax.swing.SwingUtilities;

import java.util.*;

/**
 * Bridges SNT to {@link SciView}, allowing {@link Tree}s to be rendered as Scenery objects
 *
 * @author Kyle Harrington
 * @author Tiago Ferreira
 */
public class SciViewSNT {
	private final static String PATH_MANAGER_TREE_LABEL = "Path Manager Contents";

	@Parameter
	private SciViewService sciViewService;

	private SNT snt;
	private SciView sciView;

	private final Map<String, ShapeTree> plottedTrees;

	/**
	 * Instantiates a new SciViewSNT instance.
	 *
	 * @param context the SciJava application context providing the services
	 *                required by the class
	 * @throws NullContextException If context is null
	 */
	public SciViewSNT(final Context context) {
		if (context == null) throw new NullContextException();
		context.inject(this);
		plottedTrees = new TreeMap<String,ShapeTree>();
		snt = null;
	}

	/**
	 * Instantiates SciViewSNT from an existing SciView instance.
	 *
	 * @param sciView the SciView instance to be associated with this SciViewSNT
	 *                instance
	 * @throws NullPointerException If sciView is null
	 */
	public SciViewSNT(final SciView sciView) {
		if (sciView == null) throw new NullPointerException();
		this.sciView = sciView;
		sciView.getScijavaContext().inject(this);
		plottedTrees = new TreeMap<String,ShapeTree>();
		snt = null;
	}

	protected SciViewSNT(final SNT snt) {
		this(snt.getContext());
		this.snt = snt;
		if (snt.getUI() != null) snt.getUI().setSciViewSNT(this);
		initSciView();
	}

	private void initSciView() {
		if (sciView == null) {
			if (SwingUtilities.isEventDispatchThread())
				SNTUtils.log("Initializing active SciView from EDT");
			setSciView(sciViewService.getOrCreateActiveSciView());
		}
	}

	/**
	 * Gets the SciView instance currently in use.
	 *
	 * @return the SciView instance. It is never null: A new instance is created if
	 *         none has been specified
	 */
	public SciView getSciView() {
		initSciView();
		return sciView;
	}

	/**
	 * Sets the SciView to be used.
	 *
	 * @param sciView the SciView instance. Null allowed.
	 */
	public void setSciView(final SciView sciView) {
		if (sciView == null) {
			nullifySciView();
		} else {
			this.sciView = sciView;
			this.sciView.addWindowListener(new WindowAdapter() {

				@Override
				public void windowClosing(final WindowEvent e) {
					nullifySciView();
				}
			});
			this.sciView.getFloor().setVisible(false);
			if (snt != null) syncPathManagerList();
		}
	}

	private void nullifySciView() {
		if (sciView == null) return;
		sciView.dispose(); // unnecessary?
		sciView.close();
		sciView = null;
		if (snt != null && snt.getUI() != null) snt.getUI().setSciViewSNT(null);
	}

	private String makeUniqueKey(final Map<String, ?> map, final String key) {
		for (int i = 2; i <= 100; i++) {
			final String candidate = key + " (" + i + ")";
			if (!map.containsKey(candidate)) return candidate;
		}
		return key + " (" + UUID.randomUUID() + ")";
	}

	private String getUniqueLabel(final Map<String, ?> map,
			final String fallbackPrefix, final String candidate)
	{
		final String label = (candidate == null || candidate.trim().isEmpty())
				? fallbackPrefix : candidate;
		return (map.containsKey(label)) ? makeUniqueKey(map, label) : label;
	}

	/**
	 * Adds a tree to the associated SciView instance. A new SciView instance is
	 * automatically instantiated if {@link #setSciView(SciView)} has not been
	 * called.
	 *
	 * @param tree the {@link Tree} to be added. The Tree's label will be used as
	 *             identifier. It is expected to be unique when rendering multiple
	 *             Trees, if not (or no label exists) a unique label will be
	 *             generated.
	 * @see Tree#getLabel()
	 * @see Tree#setColor(ColorRGB)
	 */
	public void addTree(final Tree tree) {
		initSciView();
		final String label = getUniqueLabel(plottedTrees, "Tree ", tree.getLabel());
		add(tree, label);
		//sciView.centerOnNode(plottedTrees.get(label));
	}

	public void addTree(final Tree tree, final float scaleFactor) {
		initSciView();
		final String label = getUniqueLabel(plottedTrees, "Tree ", tree.getLabel());
		add(tree, label, scaleFactor);
		//sciView.centerOnNode(plottedTrees.get(label));
	}

	/**
	 * Gets the specified Tree as a Scenery Node.
	 *
	 * @param tree the tree previously added to SciView using {@link #addTree(Tree)}
	 * @return the scenery Node
	 */
	public Node getTreeAsSceneryNode(final Tree tree) {
		final String treeLabel = getLabel(tree);
		final ShapeTree shapeTree = plottedTrees.get(treeLabel);
		return (shapeTree == null) ? null : shapeTree.get();
	}

	/**
	 * Removes the specified Tree.
	 *
	 * @param tree the tree previously added to SciView using {@link #addTree(Tree)}
	 * @return true, if tree was successfully removed.
	 * @see #addTree(Tree)
	 */
	public boolean removeTree(final Tree tree) {
		final String treeLabel = getLabel(tree);
		final ShapeTree shapeTree = plottedTrees.get(treeLabel);
		if (shapeTree == null) return false;
		removeTree(treeLabel);
		return  plottedTrees.containsKey(treeLabel);
	}

	private void add(final Tree tree, final String label) {
		float scaleFactor = 0.1f;
		add(tree, label, scaleFactor);
	}

	private void add(final Tree tree, final String label, float scaleFactor) {
		final ShapeTree shapeTree = new ShapeTree(tree, scaleFactor);
		shapeTree.setName(label);
		plottedTrees.put(label, shapeTree);
		sciView.addNode(shapeTree.get(), true);
//		for( Node node : shapeTree.get().getChildren() ) {
//			sciView.addChild(node);
//			//System.out.println("addTree: node " + node.getMetadata().get("pathID") + " node " + n);
//		}
//		//sciView.getCamera().setPosition(treeCenter.minus(new GLVector(0,0,-10f)));
//		sciView.centerOnNode( shapeTree );
	}

	/**
	 * (Re)loads the current list of Paths in the Path Manager list.
	 *
	 * @return true, if Path Manager list is not empty and synchronization was
	 *         successful
	 * @throws IllegalArgumentException if SNT is not running
	 */
	protected boolean syncPathManagerList() {
		if (snt == null)
			throw new IllegalArgumentException("Unknown SNT instance. SNT not running?");
		if (snt.getPathAndFillManager().size() == 0)
			return false;

		if (sciView == null || sciView.isClosed()) {
			// If we cannot sync, let's ensure the UI is not in some unexpected state
			nullifySciView();
			if (snt.getUI() != null) snt.getUI().setSciViewSNT(null);
			return false;
		}

		final Tree tree = new Tree(snt.getPathAndFillManager().getPathsFiltered());
		tree.setLabel(PATH_MANAGER_TREE_LABEL);
		if (plottedTrees.containsKey(PATH_MANAGER_TREE_LABEL)) {
			// If the "Path Manager" Node exists, remove it so that it can be replaced
			removeTree(PATH_MANAGER_TREE_LABEL);
		}
		add(tree, PATH_MANAGER_TREE_LABEL);
		sciView.centerOnNode(plottedTrees.get(PATH_MANAGER_TREE_LABEL));
		// sciView.getCamera().setPosition(treeCenter.minus(new GLVector(0,0,-10f)));
		// sciView.centerOnNode(sciView.getSceneNodes()[(int)(Math.random()*sciView.getSceneNodes().length)]);
		return true;
	}

	private void removeTree(final String label) {
		final Node treeToRemove = plottedTrees.get(label);
		if (treeToRemove != null && sciView != null) {
			for (final Node node : treeToRemove.getChildren()) {
				plottedTrees.get(label).removeChild(node);
				sciView.deleteNode(node, false);
			}
			sciView.deleteNode(treeToRemove);
			plottedTrees.remove(label);
		}
	}

	private String getLabel(final Tree tree) {
		for (final Map.Entry<String, ShapeTree> entry : plottedTrees.entrySet()) {
			if (entry.getValue().tree == tree) return entry.getKey();
		}
		return null;
	}

	@SuppressWarnings("unused")
	private void syncNode(final Tree tree, final Node node) {
		// Every Path in tree has a unique getID()
		// Node should know this ID for syncing
		final Integer pathID = (Integer) node.getMetadata().get("pathID");
		if( pathID != null ) {
			for (final Path p : tree.list()) {
				if (p.getID() == pathID) {
					// Sync the path to the node
					break;
				}
			}
		}
	}

	private class ShapeTree extends Node {

		private static final long serialVersionUID = 1L;
		private static final float DEF_NODE_RADIUS = 3f;

		private final Tree tree;
		private final float scaleFactor;
		private Node somaSubShape;
		private Vector3 translationReset;

		public ShapeTree(final Tree tree) {
			super();
			this.tree = tree;
			translationReset = new FloatVector3(0f,0f,0f);
			scaleFactor = 0.1f;
		}

		public ShapeTree(final Tree tree, final float scaleFactor) {
			super();
			this.tree = tree;
			this.scaleFactor = scaleFactor;
			translationReset = new FloatVector3(0f,0f,0f);
		}

		public Node get() {
			if (getChildren().size() == 0) assembleShape();
			return this;
		}

		private void translateTo(final Vector3 destination) {
			translationReset.setPosition(destination);
		}

		@SuppressWarnings("unused")
		private void resetTranslation() {
			translateTo(translationReset);
			translationReset = new FloatVector3(0f, 0f, 0f);
		}

		private void assembleShape() {

			final List<Node> lines = new ArrayList<>();
			final List<PointInImage> somaPoints = new ArrayList<>();
			final List<Color> somaColors = new ArrayList<>();

			for (final Path p : tree.list()) {

				// Stash soma coordinates
				if (Path.SWC_SOMA == p.getSWCType()) {
					for (int i = 0; i < p.size(); i++) {
						final PointInImage pim = p.getNodeWithoutChecks(i);
						pim.v = p.getNodeRadius(i);
						somaPoints.add(pim);
					}
					if (p.hasNodeColors()) {
						somaColors.addAll(Arrays.asList(p.getNodeColors()));
					}
					else {
						somaColors.add(p.getColor());
					}
					continue;
				}

				// Assemble arbor(s)
				final List<Vector3> points = new ArrayList<>();
				final List<ColorRGB> colors = new ArrayList<>();
				for (int i = 0; i < p.size(); ++i) {
					final PointInImage pim = p.getNodeWithoutChecks(i);
					final ClearGLVector3 coord = new ClearGLVector3((float)pim.x, (float)pim.y, (float)pim.z);
					final Material mat = new Material();
					final Color c = p.hasNodeColors() ? p.getNodeColor(i) : p.getColor();
					final ColorRGB color = c == null ? Colors.ANTIQUEWHITE : fromAWTColor(c);
					mat.setDiffuse(new GLVector(color.getRed(),color.getGreen(),color.getBlue()));
					//final float width = Math.max((float) p.getNodeRadius(i), DEF_NODE_RADIUS);
					//System.out.println( "(point " + i + " " + coord.source() + ")" );
					points.add( new FloatVector3(coord.source().x()*scaleFactor,coord.source().y()*scaleFactor,coord.source().z()*scaleFactor) );
					colors.add( color );
				}

				final Line3D line = new Line3D(points, colors, 0.25);
				line.getMetadata().put("pathID",p.getID());
				line.setName(p.getName());
				//sciView.addNode(line,false );
				lines.add(line);
			}

			// Group all lines
			if (!lines.isEmpty()) {
				for( final Node line : lines ) {
					addChild( line );
					//sciView.addNode(line, false);
				}
			}
			assembleSoma(somaPoints, somaColors);
			if (somaSubShape != null) addChild(somaSubShape);

			this.setPosition(this.getMaximumBoundingBox().getBoundingSphere().getOrigin());
			//sciView.setActiveNode(this);
			//sciView.surroundLighting();
		}

		private void assembleSoma(final List<PointInImage> somaPoints,
				final List<Color> somaColors)
		{
			//ColorRGB col = fromAWTColor(SNTColor.average(somaColors));
			final ColorRGB col = new ColorRGB(0,255,0);
			switch (somaPoints.size()) {
			case 0:
				//SNT.log(tree.getLabel() + ": No soma attribute");
				somaSubShape = null;
				return;
			case 1:
				// single point soma: http://neuromorpho.org/SomaFormat.html
				final PointInImage sCenter = somaPoints.get(0);
				somaSubShape = sciView.addSphere(convertPIIToVector3(sCenter), DEF_NODE_RADIUS, col);
				return;
			case 3:
				// 3 point soma representation: http://neuromorpho.org/SomaFormat.html
				final Vector3 p1 = convertPIIToVector3(somaPoints.get(0));
				final Vector3 p2 = convertPIIToVector3(somaPoints.get(1));
				final Vector3 p3 = convertPIIToVector3(somaPoints.get(2));
				final double lenthT1 = p2.minus(p1).getLength();
				final double lenthT2 = p1.minus(p3).getLength();
				final Node t1 = sciView.addCylinder(p2,DEF_NODE_RADIUS,(float)lenthT1,20);
				final Node t2 = sciView.addCylinder(p1,DEF_NODE_RADIUS,(float)lenthT2,20);
				addChild(t1);
				addChild(t2);
				return;
			default:
				// just create a centroid sphere
				final PointInImage cCenter = SNTPoint.average(somaPoints);
				somaSubShape = sciView.addSphere(convertPIIToVector3(cCenter), DEF_NODE_RADIUS, col);
				return;
			}
		}

		private Vector3 convertPIIToVector3(final PointInImage sCenter) {
			final Vector3 v = new DoubleVector3(sCenter.x,sCenter.y,sCenter.z);
			return v;
		}

		private ColorRGB fromAWTColor(final Color average) {
			return new ColorRGB(average.getRed(),average.getGreen(),average.getBlue());
		}

		/**
		 * Generates an [OrientedBoundingBox] for this [Node]. This will take
		 * geometry information into consideration if this Node implements [HasGeometry].
		 * In case a bounding box cannot be determined, the function will return null.
		 */
		public OrientedBoundingBox generateBoundingBox() {
			OrientedBoundingBox bb = new OrientedBoundingBox(this, 0.0f, 0.0f, 0.0f,
					0.0f, 0.0f, 0.0f);
			for( final Node n : getChildren() ) {
				final OrientedBoundingBox cBB = n.generateBoundingBox();
				if( cBB != null )
					bb = bb.expand(bb, cBB);
			}
			return bb;
		}
	}

	/* IDE debug method */
	public static void main(final String[] args) throws InterruptedException, IOException {
		SceneryBase.xinitThreads();
		//GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final SNTService sntService = ij.context().getService(SNTService.class);
		final SciViewSNT sciViewSNT = sntService.getOrCreateSciViewSNT();
		SciView sciView = sciViewSNT.getSciView();

		IOService ioService = ij.context().getService(IOService.class);

		String basePath = "/groups/cardona/home/harringtonk/SNT/";

		String ch1Path = basePath + "/SNT/C1-MLIGHTR_R1_3X2-cropped.tif";
		String ch2Path = basePath + "/SNT/C2-MLIGHTR_R1_3X2-cropped.tif";
		String ch3Path = basePath + "/SNT/C3-MLIGHTR_R1_3X2-cropped.tif";
		String swcPath = basePath + "/SNT/MLIGHTR_R1_3X2-cropped-000.swc";

		Dataset ch1 = (Dataset) ioService.open(ch1Path);
		Dataset ch2 = (Dataset) ioService.open(ch2Path);
		Dataset ch3 = (Dataset) ioService.open(ch3Path);

		float[] voxelResolution = new float[]{0.2285199f, 0.2285199f, 0.4188450f};

		Volume v1 = (Volume) sciView.addVolume(ch1, voxelResolution);

		v1.setPixelToWorldRatio(0.1f);
		//v2 = sciView.addVolume(ch2, voxel_resolution)
		//# v3 = sciView.addVolume(ch3, voxel_resolution)

		//v1.getRotation().rotateByAngleY(3.1415)

		v1.setName("Ch1");
//		# v2.setName('Ch2')
//		# v3.setName('Ch3')

		sciView.setColormap(v1, "Red.lut");
//		# sciView.setColormap(v2, "Green.lut")
//		# sciView.setColormap(v3, "Blue.lut")

		Tree tree = new Tree(swcPath);

		//#sntService.loadTracings(swc_path)
		sciViewSNT.addTree(tree, 0.1f);

		//treeName = "MLIGHTR_R1_3X2-cropped-000.swc";

	}
}
