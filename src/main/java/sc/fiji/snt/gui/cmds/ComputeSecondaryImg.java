/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2021 Fiji developers.
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

package sc.fiji.snt.gui.cmds;

import ij.ImagePlus;
import ij.measure.Calibration;
import net.imagej.ImageJ;
import net.imagej.legacy.LegacyService;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.display.DisplayService;
import org.scijava.io.IOService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;

import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.filter.*;
import sc.fiji.snt.gui.GuiUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Implements the "Generate Secondary Image" command.
 *
 * @author Tiago Ferreira
 * @author Cameron Arshadi
 */
@Plugin(type = Command.class, visible = false, initializer = "init", label = "Compute \"Secondary Image\"")
public class ComputeSecondaryImg extends CommonDynamicCmd {

	private static final String NONE = "None. Duplicate primary image";
	private static final String FRANGI = "Frangi";
	private static final String TUBENESS = "Tubeness";

	@Parameter
	private DisplayService displayService;

	@Parameter
	private LegacyService legacyService;

	@Parameter
	private PlatformService platformService;

	@Parameter
	private OpService ops;

	@Parameter
	private IOService io;

	@Parameter(label = "Filter", choices = { FRANGI, TUBENESS, NONE })
	private String filter;

	@Parameter(label = "Compute statistics")
	private boolean computeStats;

	@Parameter(label = "Display", required = false)
	private boolean show;

	@Parameter(label = "Save", required = false)
	private boolean save;

	@Parameter(label = "<HTML>&nbsp;", persist = false, visibility = ItemVisibility.MESSAGE)
	private String msg = "<HTML>It is assumed that the current sigma values for the primary image in<br>"
			+ "the Auto-tracing widget reflect the size of structures to be filtered.<br>"
			+ "If that is not the case, you should dismiss this prompt and adjust it.";

	@Parameter(label = "Online Help", callback = "help")
	private Button button;

	private RandomAccessibleInterval<FloatType> filteredImg;

	protected void init() {
		super.init(true);
		if (!snt.accessToValidImageData()) {
			error("Valid image data is required for computation.");
			return;
		}
	}

	@SuppressWarnings("unused")
	private void help() {
		final String url = "https://imagej.net/SNT:_Manual#Tracing_on_Secondary_Image";
		try {
			platformService.open(new URL(url));
		} catch (final IOException e) {
			error("Web page could not be open. " + "Please visit " + url + " using your web browser.");
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		// TODO fix type mismatch here, probably will need to convert...
		final ImagePlus inputImp = sntService.getPlugin().getLoadedDataAsImp();
		if (NONE.equals(filter)) {
			//snt.loadSecondaryImage(sntService.getPlugin().getLoadedData());
			apply();
			return;
		}
		final RandomAccessibleInterval<? extends RealType<?>> data = sntService.getPlugin().getLoadedData();
		final RandomAccessibleInterval<? extends RealType<?>> in = Views.dropSingletonDimensions(data);
		final double[] sigmas = sntService.getPlugin().getHessianSigma("primary", true);
		final Calibration cal = inputImp.getCalibration();
		final double[] spacing = new double[3];
		spacing[0] = cal.pixelWidth;
		spacing[1] = cal.pixelHeight;
		spacing[2] = cal.pixelDepth;
		Consumer<RandomAccessibleInterval<FloatType>> op;
		switch (filter) {
			case FRANGI:
				op = new Frangi(in, sigmas, spacing, sntService.getPlugin().getStackMax());
				break;
			case TUBENESS:
				op = new Tubeness(in, sigmas, spacing);
				break;
			default:
				throw new IllegalArgumentException("Unrecognized filter " + filter);
		}
		// TODO: let user set cell dims?
		filteredImg = Lazy.process(
				in,
				new int[]{30, 30, 30},
				new FloatType(),
				op);
		apply();
	}

	private void apply() {
		snt.loadSecondaryImage(filteredImg, computeStats);
		if (show) {
			displayService.createDisplay(getImageName(), filteredImg); // virtual stack!?
		}
		if (save) {
			try {
				io.save(filteredImg, getSaveFile().getAbsolutePath());
			} catch (final IOException e) {
				error("An error occurred when trying to save image. See console for details");
				e.printStackTrace();
			}
		}
		resetUI();
	}

	private String getImageName() {
		final String basename = SNTUtils.stripExtension(sntService.getPlugin().getImagePlus().getTitle());
		final String sfx = (NONE.equals(filter)) ? "DUP" : filter;
		return basename + " Sec Img [" + sfx + "].tif";
	}

	private File getSaveFile() {
		File file = new File(sntService.getPlugin().getPrefs().getRecentDir(), getImageName());
		file = SNTUtils.getUniquelySuffixedTifFile(file);
		return legacyService.getIJ1Helper().saveDialog("Save \"Filtered Image\"", file, ".tif");
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(ComputeSecondaryImg.class, true);
	}
}
