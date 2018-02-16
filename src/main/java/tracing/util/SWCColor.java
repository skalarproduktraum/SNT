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

package tracing.util;

import java.awt.Color;


/**
 * A simple class that associates an AWT Color to a SWC type integer tag.
 */
public class SWCColor {

	protected static final int SWC_TYPE_IGNORED = -1;
	private Color color;
	private int swcType;

	/**
	 * Instantiates a new SWC color.
	 *
	 * @param color the AWT color
	 * @param swcType the SWC type integer flag
	 * @see {@link tracing.Path#getSWCtypeName}
	 */
	public SWCColor(final Color color, final int swcType) {
		this.color = color;
		this.swcType = swcType;
	}

	/**
	 * Instantiates a new SWC color without SWC type association
	 *
	 * @param color the AWT color
	 */
	public SWCColor(final Color color) {
		this(color, SWC_TYPE_IGNORED);
	}

	/**
	 * Retrieves the Color
	 *
	 * @return the AWT color
	 */
	public Color color() {
		return color;
	}

	/**
	 * Retrieves the 
	 *
	 * @return the SWC type integer flag
	 */
	public int type() {
		return swcType;
	}

	/**
	 * Checks if an SWC type has been defined.
	 *
	 * @return true, if an SWC integer flag has been specified
	 */
	public boolean isTypeDefined() {
		return swcType != SWC_TYPE_IGNORED;
	}

	/**
	 * Re-assigns a AWT color.
	 *
	 * @param color the new color
	 */
	public void setAWTColor(final Color color) {
		this.color = color;
	}

	/**
	 * Re-assigns a SWC type integer flag
	 *
	 * @param swcType the new SWC type
	 */
	public void setSWCType(final int swcType) {
		this.swcType = swcType;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((color == null) ? 0 : color.hashCode());
		result = prime * result + swcType;
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof SWCColor)) {
			return false;
		}
		final SWCColor other = (SWCColor) obj;
		if (color == null) {
			if (other.color != null) {
				return false;
			}
		}
		else if (!color.equals(other.color)) {
			return false;
		}
		if (swcType != other.swcType) {
			return false;
		}
		return true;
	}

	/**
	 *  Returns the color encoded as hex string with the format #rrggbbaa.
	 *
	 * @param color the input AWT color
	 * @return the converted string
	 */
	public static String colorToString(final Color color) {
		if (color == null) throw new IllegalArgumentException(
			"Cannot convert null object");
		return String.format("#%02x%02x%02x%02x", color.getRed(), color.getGreen(),
			color.getBlue(), color.getAlpha());
	}

	/**
	 *  Returns an AWT Color from a (#)RRGGBB(AA) hex string.
	 *
	 * @param hex the input string
	 * @return the converted AWT color
	 */
	public static Color stringToColor(final String hex) {
		if (hex.length() < 6) throw new IllegalArgumentException(
			"Unsupported format. Only (#)RRGGBB(AA) allowed");
		final String input = hex.charAt(0) == '#' ? hex.substring(1) : hex;
		final int r = Integer.valueOf(input.substring(0, 2), 16);
		final int g = Integer.valueOf(input.substring(2, 4), 16);
		final int b = Integer.valueOf(input.substring(4, 6), 16);
		final int a = (hex.length() < 8) ? 255 : Integer.valueOf(hex.substring(6,
			8), 16);
		return new Color(r / 255f, g / 255f, b / 255f, a / 255f);
	}

	/**
	 * Adds an alpha component to a AWT colot.
	 *
	 * @param c the input color
	 * @param percent alpha value in percentage
	 * @return the color with an alpha component
	 */
	public static Color alphaColor(final Color c, final double percent) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)Math.round(percent/100 * 255));
	}

	/**
	 * Returns a suitable 'contrast' color.
	 *
	 * @param c the input color
	 * @return Either white or black, as per hue of input color.
	 */
	public static Color contrastColor(final Color c) {
		final int intensity = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
		return intensity < 128 ? Color.WHITE : Color.BLACK;
	}

}
