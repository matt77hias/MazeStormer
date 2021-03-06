package mazestormer.barcode;

import static com.google.common.base.Preconditions.checkNotNull;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;

import lejos.robotics.navigation.Move;
import mazestormer.condition.Condition;
import mazestormer.condition.ConditionType;
import mazestormer.condition.LightCompareCondition;
import mazestormer.maze.IMaze;
import mazestormer.player.Player;
import mazestormer.robot.ControllableRobot;
import mazestormer.state.State;
import mazestormer.state.StateListener;
import mazestormer.state.StateMachine;
import mazestormer.util.Future;

import com.google.common.base.Strings;
import com.google.common.math.DoubleMath;

public class BarcodeScanner extends StateMachine<BarcodeScanner, BarcodeScanner.BarcodeState> implements
		StateListener<BarcodeScanner.BarcodeState>, BarcodeScannerListener {

	/*
	 * Constants
	 */

	// private static final double START_BAR_LENGTH = 1.8; // [cm]
	// private static final double BAR_LENGTH = 1.85; // [cm]
	private static final float BACKUP_DISTANCE = 5f; // cm
	private static final float NOISE_LENGTH = 0.65f;

	/*
	 * Settings
	 */

	private final Player player;
	private double scanSpeed = 2; // cm/sec
	private double originalTravelSpeed;

	/*
	 * State
	 */

	private volatile float strokeStart;
	private volatile float strokeEnd;
	private final List<Float> distances = new ArrayList<Float>();

	private final List<BarcodeScannerListener> listeners = new ArrayList<BarcodeScannerListener>();

	public BarcodeScanner(Player player) {
		this.player = checkNotNull(player);

		addBarcodeListener(this);
		addStateListener(this);
	}

	public ControllableRobot getRobot() {
		return (ControllableRobot) player.getRobot();
	}

	public IMaze getMaze() {
		return player.getMaze();
	}

	public double getScanSpeed() {
		return scanSpeed;
	}

	public void setScanSpeed(double scanSpeed) {
		this.scanSpeed = scanSpeed;
	}

	protected Move getMovement() {
		return getRobot().getPilot().getMovement();
	}

	protected double getBarLength() {
		return getMaze().getBarLength();
	}

	protected float getStartOffset() {
		return getRobot().getLightSensor().getSensorRadius();
	}

	protected void log(String message) {
		player.getLogger().log(Level.FINE, message);
	}

	public void addBarcodeListener(BarcodeScannerListener listener) {
		listeners.add(listener);
	}

	public void removeBarcodeListener(BarcodeScannerListener listener) {
		listeners.remove(listener);
	}

	@Override
	public void onStartBarcode() {
	}

	@Override
	public void onEndBarcode(Barcode barcode) {
		// Log
		String binary = Integer.toBinaryString(barcode.getValue());
		String padded = Strings.padStart(binary, Barcode.getNbValueBars(), '0');
		log("Scanned barcode: " + padded);
	}

	private Future<Void> onFirstBlack() {
		Condition condition = new LightCompareCondition(ConditionType.LIGHT_SMALLER_THAN,
				Threshold.BLACK_WHITE.getThresholdValue());
		return getRobot().when(condition).stop().build();
	}

	private Future<Void> onSecondBlack() {
		Condition condition = new LightCompareCondition(ConditionType.LIGHT_SMALLER_THAN,
				Threshold.BLACK_WHITE.getThresholdValue());
		return getRobot().when(condition).build();
	}

	private Future<Void> onWhiteToBlack() {
		Condition condition = new LightCompareCondition(ConditionType.LIGHT_SMALLER_THAN,
				Threshold.WHITE_BLACK.getThresholdValue());
		return getRobot().when(condition).build();
	}

	private Future<Void> onBlackToWhite() {
		Condition condition = new LightCompareCondition(ConditionType.LIGHT_GREATER_THAN,
				Threshold.BLACK_WHITE.getThresholdValue());
		return getRobot().when(condition).build();
	}

	protected void findStart() {
		// Save original speed
		originalTravelSpeed = getRobot().getPilot().getTravelSpeed();
		// Reset state
		strokeStart = 0f;
		strokeEnd = 0f;
		distances.clear();

		// Find first black line
		log("Start looking for black line.");
		bindTransition(onFirstBlack(), BarcodeState.GO_TO_START);
	}

	protected void goToStart() {
		// Notify listeners
		for (BarcodeScannerListener listener : listeners) {
			listener.onStartBarcode();
		}

		log("Go to the begin of the barcode zone.");
		getRobot().getPilot().setTravelSpeed(getScanSpeed());
		bindTransition(getRobot().getPilot().travelComplete(-BACKUP_DISTANCE), BarcodeState.FIND_START_AGAIN);
	}

	protected void findStartAgain() {
		log("Find start of barcode again.");
		// Go forward
		getRobot().getPilot().forward();
		// Find first black stroke again
		bindTransition(onSecondBlack(), BarcodeState.STROKE_START);
	}

	protected void strokeStart() {
		log("Found start of barcode.");
		// At begin of barcode
		strokeStart = getMovement().getDistanceTraveled();
		// Find white stroke
		transition(BarcodeState.FIND_STROKE_WHITE);
	}

	protected void findWhiteStroke() {
		bindTransition(onBlackToWhite(), BarcodeState.STROKE_WHITE);
	}

	protected void findBlackStroke() {
		bindTransition(onWhiteToBlack(), BarcodeState.STROKE_BLACK);
	}

	protected void stroke(boolean foundBlack) {
		boolean nextStrokeBlack = addStroke(foundBlack);

		if (isCompleted()) {
			// Completed
			transition(BarcodeState.FINISH);
		} else {
			// Iterate
			if (nextStrokeBlack) {
				transition(BarcodeState.FIND_STROKE_BLACK);
			} else {
				transition(BarcodeState.FIND_STROKE_WHITE);
			}
		}
	}

	protected void blackToBrown() {
		// Came from a black stroke
		boolean foundBlack = false;
		// Add last stroke
		addStroke(foundBlack);

		if (isCompleted()) {
			// Completed
			transition(BarcodeState.FINISH);
		} else {
			// Failed
			transition(BarcodeState.FAILED);
		}
	}

	protected void failed() {
		log("Barcode scanner failed");
		// TODO Restart barcode scanner?
		stop();
	}

	private boolean addStroke(boolean foundBlack) {
		// Get stroke width
		strokeEnd = getMovement().getDistanceTraveled();
		log(strokeStart + " - " + strokeEnd);
		float strokeWidth = Math.abs(strokeEnd - strokeStart);

		if (strokeWidth >= NOISE_LENGTH) {
			// Store width
			distances.add(strokeWidth);
			// Set start of next stroke
			strokeStart = strokeEnd;
			log("Found " + (foundBlack ? "white" : "black") + " stroke of " + strokeWidth + " cm");
			// Find next stroke
			return !foundBlack;
		} else {
			log("Noise: " + strokeWidth);
			// Noise detected, retry same stroke
			return foundBlack;
		}
	}

	private boolean isCompleted() {
		return getTotalSum(distances) >= (Barcode.getNbBars() - 1) * getBarLength();
	}

	private int readBarcode(List<Float> distances) {
		final double barLength = getBarLength();
		int result = 0;
		int index = Barcode.getNbValueBars() - 1;

		// Iterate over distances until barcode complete
		ListIterator<Float> it = distances.listIterator();
		while (it.hasNext() && index >= 0) {
			int i = it.nextIndex();
			float distance = it.next();
			double at;
			if (i == 0) {
				// First bar
				// Cut off first black stroke
				at = Math.max((distance - barLength) / barLength, 0);
			} else {
				at = Math.max(distance / barLength, 1);
			}
			// TODO Check rounding
			int limit = DoubleMath.roundToInt(at, RoundingMode.HALF_DOWN);
			// Odd indices are white, even indices are black
			int barBit = i & 1; // == i % 2
			// Set bit from index to index-a
			for (int j = 0; j < limit && index >= 0; j++) {
				result |= barBit << index;
				index--;
			}
		}
		return result;
	}

	private static float getTotalSum(Iterable<Float> values) {
		float sum = 0;
		for (float value : values) {
			sum += value;
		}
		return sum;
	}

	@Override
	public void stateStarted() {
		// Start
		transition(BarcodeState.FIND_START);
	}

	@Override
	public void stateStopped() {
		// Restore original speed
		getRobot().getPilot().setTravelSpeed(originalTravelSpeed);
	}

	@Override
	public void stateFinished() {
		// Reset
		stateStopped();
		// Read barcode
		Barcode barcode = new Barcode(readBarcode(distances));
		// Notify listeners
		for (BarcodeScannerListener listener : listeners) {
			listener.onEndBarcode(barcode);
		}
	}

	@Override
	public void statePaused(BarcodeState currentState, boolean onTransition) {
	}

	@Override
	public void stateResumed(BarcodeState currentState) {
	}

	@Override
	public void stateTransitioned(BarcodeState nextState) {
	}

	public enum BarcodeState implements State<BarcodeScanner, BarcodeState> {
		FIND_START {
			@Override
			public void execute(BarcodeScanner scanner) {
				scanner.findStart();
			}
		},
		GO_TO_START {
			@Override
			public void execute(BarcodeScanner scanner) {
				scanner.goToStart();
			}
		},
		FIND_START_AGAIN {
			@Override
			public void execute(BarcodeScanner scanner) {
				scanner.findStartAgain();
			}
		},
		STROKE_START {
			@Override
			public void execute(BarcodeScanner scanner) {
				scanner.strokeStart();
			}
		},
		FIND_STROKE_BLACK {
			@Override
			public void execute(BarcodeScanner scanner) {
				scanner.findBlackStroke();
			}
		},
		FIND_STROKE_WHITE {
			@Override
			public void execute(BarcodeScanner scanner) {
				scanner.findWhiteStroke();
			}
		},
		STROKE_BLACK {
			@Override
			public void execute(BarcodeScanner scanner) {
				scanner.stroke(true);
			}
		},
		STROKE_WHITE {
			@Override
			public void execute(BarcodeScanner scanner) {
				scanner.stroke(false);
			}
		},
		BLACK_TO_BROWN {
			@Override
			public void execute(BarcodeScanner scanner) {
				scanner.blackToBrown();
			}
		},
		FINISH {
			@Override
			public void execute(BarcodeScanner scanner) {
				scanner.finish();
			}
		},
		FAILED {
			@Override
			public void execute(BarcodeScanner scanner) {
				scanner.failed();
			}
		}
	}

}
