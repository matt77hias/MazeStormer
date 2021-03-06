package mazestormer.detect;

import java.util.Comparator;

import lejos.robotics.RangeReading;

import com.google.common.primitives.Floats;

/**
 * Compares range readings by their range.
 */
public class ReadingAngleComparator implements Comparator<RangeReading> {

	@Override
	public int compare(RangeReading left, RangeReading right) {
		return Floats.compare(left.getAngle(), right.getAngle());
	}

}
