package mazestormer.maze;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import lejos.geom.Line;
import lejos.geom.Point;

import com.google.common.math.IntMath;

public enum Orientation {
	/**
	 * North, the positive Y orientation.
	 */
	NORTH("N", new Point(0, 1), new Line(0, 1, 1, 1)),

	/**
	 * East, the positive X orientation.
	 */
	EAST("E", new Point(1, 0), new Line(1, 0, 1, 1)),

	/**
	 * South, the negative Y orientation.
	 */
	SOUTH("S", new Point(0, -1), new Line(0, 0, 1, 0)),

	/**
	 * West, the negative X orientation.
	 */
	WEST("W", new Point(-1, 0), new Line(0, 0, 0, 1));

	private final String shortName;
	private final Point delta;
	private final Line line;

	private Orientation(String shortName, Point delta, Line line) {
		this.shortName = shortName;
		this.delta = delta;
		this.line = line;
	}

	/**
	 * Get the short name of this orientation.
	 */
	public String getShortName() {
		return shortName;
	}

	/**
	 * Get the delta vector of this orientation.
	 * 
	 * <p>
	 * The positive X-axis runs from left to right, the positive Y-axis runs
	 * from bottom to top.
	 * </p>
	 */
	public Point getDelta() {
		return delta;
	}

	/**
	 * Get the angle of this orientation, in degrees.
	 * 
	 * <p>
	 * The angle is measured counterclockwise from the positive X-axis.
	 * </p>
	 */
	public float getAngle() {
		return (float) Math.toDegrees(getDelta().angle());
	}

	/**
	 * Get the angle from this orientation to the given other orientation, in
	 * degrees.
	 * 
	 * @param other
	 *            The other orientation.
	 */
	public float angleTo(Orientation other) {
		return other.getAngle() - getAngle();
	}

	/**
	 * Get the unit line of the side in this orientation.
	 * 
	 * <p>
	 * The relative origin {@code (0, 0)} is the bottom left corner. The
	 * orientations are identical to those used by {@link getDelta}. This
	 * implies that all coordinates of the returned line are nonnegative.
	 * </p>
	 */
	public Line getLine() {
		return line;
	}

	/**
	 * Rotate this orientation clockwise for the given amount of times.
	 * 
	 * <p>
	 * Example: {@code Orientation.NORTH.rotateClockwise(3) == Orientation.WEST}
	 * </p>
	 * 
	 * @param amount
	 *            The amount of times to rotate.
	 * 
	 * @return The rotated orientation.
	 */
	public Orientation rotateClockwise(int amount) {
		Orientation[] values = Orientation.values();
		// Add ordinal to amount and normalize
		// Note: the modulo operation (mod) is needed
		// as opposed to the remainder operator (%)
		int nextOrdinal = IntMath.mod(this.ordinal() + amount, values.length);
		return Orientation.values()[nextOrdinal];
	}

	/**
	 * Rotate this orientation clockwise.
	 * 
	 * <p>
	 * Example: {@code Orientation.NORTH.rotateClockwise() == Orientation.EAST}
	 * </p>
	 * 
	 * @return The rotated orientation.
	 */
	public Orientation rotateClockwise() {
		return rotateClockwise(1);
	}

	/**
	 * Rotate this orientation counter-clockwise for the given amount of times.
	 * 
	 * <p>
	 * Example:
	 * {@code Orientation.EAST.rotateCounterClockwise(3) == Orientation.SOUTH}
	 * </p>
	 * 
	 * @param amount
	 *            The amount of times to rotate.
	 * 
	 * @return The rotated orientation.
	 */
	public Orientation rotateCounterClockwise(int amount) {
		return rotateClockwise(-amount);
	}

	/**
	 * Rotate this orientation counter-clockwise.
	 * 
	 * <p>
	 * Example:
	 * {@code Orientation.NORTH.rotateCounterClockwise() == Orientation.WEST}
	 * </p>
	 * 
	 * @return The rotated orientation.
	 */
	public Orientation rotateCounterClockwise() {
		return rotateCounterClockwise(1);
	}

	/**
	 * Get the number of clockwise orientations needed to rotate this
	 * orientation to the given orientation.
	 * 
	 * <p>
	 * {@code nbRotations == this.getNbClockwiseRotationsTo(orientation)} if and
	 * only if {@code this.rotateClockwise(nbOrientations) == orientation}.
	 * </p>
	 * 
	 * @param orientation
	 *            The orientation to rotate towards.
	 * @return The number of clockwise rotations.
	 */
	public int getNbClockwiseRotationsTo(Orientation orientation) {
		final int nbOrientations = Orientation.values().length;
		return IntMath.mod(orientation.ordinal() - this.ordinal(), nbOrientations);
	}

	/**
	 * Get the number of counter-clockwise orientations needed to rotate this
	 * orientation to the given orientation.
	 * 
	 * <p>
	 * {@code nbRotations == this.getNbCounterClockwiseRotationsTo(orientation)}
	 * if and only if
	 * {@code this.rotateCounterClockwise(nbOrientations) == orientation}.
	 * </p>
	 * 
	 * @param orientation
	 *            The orientation to rotate towards.
	 * @return The number of counter-clockwise rotations.
	 */
	public int getNbCounterClockwiseRotationsTo(Orientation orientation) {
		final int nbOrientations = Orientation.values().length;
		return IntMath.mod(this.ordinal() - orientation.ordinal(), nbOrientations);
	}

	/**
	 * Shift the given point in the direction of this orientation by the given
	 * shift amount.
	 * 
	 * @param point
	 *            The point to shift.
	 * @param amount
	 *            The shift amount.
	 * 
	 * @return The shifted point.
	 */
	public <P extends Point2D> P shift(P point, float amount) {
		Point shift = getDelta().multiply(amount);
		double x = point.getX() + shift.getX();
		double y = point.getY() + shift.getY();
		@SuppressWarnings("unchecked")
		P result = (P) point.clone();
		result.setLocation(x, y);
		return result;
	}

	/**
	 * Shift the given point in the direction of this orientation by one unit.
	 * 
	 * @param point
	 *            The point to shift.
	 * 
	 * @return The shifted point. | result.equals(shift(point, 1))
	 */
	public <P extends Point2D> P shift(P point) {
		return shift(point, 1);
	}

	public static List<String> getShortNames() {
		List<String> shortNames = new ArrayList<String>(values().length);
		for (Orientation orientation : values()) {
			shortNames.add(orientation.getShortName());
		}
		return shortNames;
	}

	/**
	 * Get an orientation by its short name.
	 * 
	 * @param shortName
	 *            The short name of the orientation.
	 * 
	 * @return The orientation, or null if not found.
	 */
	public static Orientation byShortName(String shortName) {
		if (shortName != null) {
			for (Orientation orientation : values()) {
				if (orientation.getShortName().equalsIgnoreCase(shortName)) {
					return orientation;
				}
			}
		}
		return null;
	}

}
