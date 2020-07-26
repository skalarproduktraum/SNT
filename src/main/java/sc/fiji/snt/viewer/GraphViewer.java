package sc.fiji.snt.viewer;

import com.mxgraph.view.mxGraph;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultWeightedEdge;
import sc.fiji.GraphEditor.GraphEditor;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.view.mxInteractiveCanvas;
import com.mxgraph.view.mxCellState;
import net.imagej.ImageJ;
import net.imagej.lut.LUTService;

import org.scijava.Context;
import org.scijava.NullContextException;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;

import sc.fiji.GraphEditor.editor.EditorMenuBar;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.graph.*;
import sc.fiji.snt.gui.GuiUtils;
import sc.fiji.snt.io.MouseLightLoader;

import javax.swing.*;
import javax.swing.border.BevelBorder;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GraphViewer {
    @Parameter
    private Context context;
    private final SNTGraph graph;
    private SNTGraphAdapter adapter;
    private SNTGraphComponent component;

    public GraphViewer(final SNTGraph inputGraph) {
        this.graph = inputGraph;
    }

    public void setContext(final Context context) {
        if (context == null) throw new NullContextException("Context cannot be null!");
        context.inject(this);
    }

    private Context getContext() {
        if (context == null)
            setContext(new Context(CommandService.class, LUTService.class, PrefService.class));
        return context;
    }

    /**
     * Displays a graph in SNT's "Graph Viewer" featuring UI commands for
     * interactive visualization and export options.
     *
     * @return the assembled window
     */
    public Window show() {
        if (this.graph instanceof DirectedWeightedGraph) {
            adapter = new TreeGraphAdapter((DirectedWeightedGraph)this.graph);
            component = new TreeGraphComponent((TreeGraphAdapter) adapter, getContext());
        } else if (this.graph instanceof AnnotationGraph) {
            adapter = new AnnotationGraphAdapter((AnnotationGraph) this.graph);
            component = new AnnotationGraphComponent((AnnotationGraphAdapter) adapter, getContext());
        } else {
            throw new UnsupportedOperationException("Currently only DirectedWeightedGraph and AnnotationGraph are supported.");
        }

        //mxGraphComponent component = new mxGraphComponent(adapter);
        GuiUtils.setSystemLookAndFeel();
        GraphEditor editor = new GraphEditor("Graph Viewer", component);
        JFrame frame = editor.createFrame(new SNTEditorMenuBar(editor, getContext()));
        frame.pack();
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
        return frame;
    }

    public static void main(final String[] args) {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        SNTUtils.setDebugMode(true);
        List<String> cellIds = new ArrayList<>();
        cellIds.add("AA0004");
        cellIds.add("AA0100");
        cellIds.add("AA0788");
        //cellIds.add("AA1044");
        cellIds.add("AA0023");
        cellIds.add("AA0310");
        List<Tree> trees = new ArrayList<Tree>();
        for (String id : cellIds) {
            Tree tree = new MouseLightLoader(id).getTree("axon");
            trees.add(tree);
        }
        //List<Tree> trees = ij.context().getService(SNTService.class).demoTrees();
        final AnnotationGraph graph = new AnnotationGraph(trees, 10, 8);
        //graph.filterEdgesByWeight(20);
        // graph.removeOrphanedNodes();
        GraphViewer graphViewer = new GraphViewer(graph);
        graphViewer.setContext(ij.context());
        graphViewer.show();
    }
}