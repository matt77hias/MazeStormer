package mazestormer.command;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import lejos.geom.Line;
import lejos.geom.Point;
import lejos.robotics.RangeReading;
import lejos.robotics.navigation.Pose;
import lejos.robotics.navigation.Waypoint;
import lejos.robotics.objectdetection.RangeFeature;
import mazestormer.barcode.Barcode;
import mazestormer.barcode.BarcodeScanner;
import mazestormer.barcode.BarcodeScannerListener;
import mazestormer.command.game.CollisionAvoider;
import mazestormer.line.LineAdjuster;
import mazestormer.line.LineFinder;
import mazestormer.maze.Edge.EdgeType;
import mazestormer.maze.IMaze;
import mazestormer.maze.Orientation;
import mazestormer.maze.PathFinder;
import mazestormer.maze.Tile;
import mazestormer.maze.TileShape;
import mazestormer.maze.TileType;
import mazestormer.player.Player;
import mazestormer.robot.ControllablePCRobot;
import mazestormer.robot.Navigator;
import mazestormer.robot.Navigator.NavigatorState;
import mazestormer.robot.NavigatorListener;
import mazestormer.state.DefaultStateListener;
import mazestormer.state.State;
import mazestormer.state.StateListener;
import mazestormer.state.StateMachine;
import mazestormer.util.Future;
import mazestormer.util.GuavaFutures;
import mazestormer.world.ModelType;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Floats;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Drives the robot in an unknown maze.
 */
public class Driver extends StateMachine<Driver, Driver.ExplorerState> implements StateListener<Driver.ExplorerState>,
		NavigatorListener {

	/*
	 * Settings
	 */
	private final Player player;
	private final PathFinder pathFinder;
	private Commander commander;

	/*
	 * Subroutines
	 */
	private final Navigator navigator;
	private final LineFinder lineFinder;
	private final LineAdjuster lineAdjuster;
	private final BarcodeScanner barcodeScanner;
	private final CollisionAvoider collisionAvoider;

	/*
	 * Navigation
	 */
	private Tile startTile;
	private Tile goalTile;

	/**
	 * Flag indicating if the driver should periodically adjust the robot's
	 * position by running the line finder.
	 */
	private boolean lineAdjustEnabled = false;
	/**
	 * The amount of tiles between two line finder adjustment runs.
	 */
	private int lineAdjustInterval = 10;

	/*
	 * State
	 */

	/**
	 * Tile counter for line finder adjustment.
	 */
	private AtomicInteger lineAdjustCounter = new AtomicInteger(0);
	/**
	 * Flag indicating if the line finder should be run.
	 */
	private AtomicBoolean shouldLineAdjust = new AtomicBoolean(false);
	/**
	 * Flag indicating whether the barcode at the current tile should be
	 * ignored.
	 */
	private AtomicBoolean skipCurrentBarcode = new AtomicBoolean(false);

	private boolean shouldBarcode = true;

	public Driver(Player player, Commander commander) {
		this.player = checkNotNull(player);
		this.pathFinder = new PathFinder(getMaze());
		this.commander = checkNotNull(commander);
		addStateListener(this);

		// Navigator
		this.navigator = new Navigator(getRobot().getPilot(), getRobot().getPoseProvider());
		navigator.addNavigatorListener(this);
		navigator.pauseAt(Navigator.NavigatorState.TRAVEL);

		// Line finder
		this.lineFinder = new LineFinder(player);
		this.lineAdjuster = new LineAdjuster(player);
		this.lineAdjuster.bind(lineFinder);
		this.lineFinder.addStateListener(new LineFinderListener());
		// Only for physical robots
		setLineAdjustEnabled(getRobot().getModelType() == ModelType.PHYSICAL);

		// Barcode scanner
		this.barcodeScanner = new BarcodeScanner(player);
		barcodeScanner.addBarcodeListener(new BarcodeListener());

		// Collision avoider
		this.collisionAvoider = new CollisionAvoider(this);
		collisionAvoider.addStateListener(new CollisionAvoiderListener());
	}

	protected void log(String message) {
		getPlayer().getLogger().log(Level.INFO, message);
	}

	/*
	 * Getters and setters
	 */

	public final Commander getCommander() {
		return commander;
	}

	protected ControlMode getMode() {
		return getCommander().getMode();
	}

	public final PathFinder getPathFinder() {
		return pathFinder;
	}

	public final Player getPlayer() {
		return player;
	}

	public final ControllablePCRobot getRobot() {
		return (ControllablePCRobot) getPlayer().getRobot();
	}

	public final IMaze getMaze() {
		return getPlayer().getMaze();
	}

	public boolean isLineAdjustEnabled() {
		return lineAdjustEnabled;
	}

	public void setLineAdjustEnabled(boolean isEnabled) {
		this.lineAdjustEnabled = isEnabled;
	}

	public int getLineAdjustInterval() {
		return lineAdjustInterval;
	}

	public void setLineAdjustInterval(int interval) {
		this.lineAdjustInterval = interval;
	}

	public void setScanSpeed(double scanSpeed) {
		barcodeScanner.setScanSpeed(scanSpeed);
	}

	public boolean isBarcodeActionEnabled() {
		return getMode().isBarcodeActionEnabled();
	}

	public void disableBarcodeScanner() {
		shouldBarcode = false;
	}

	/**
	 * Skip executing the barcode action when arriving at the current tile. This
	 * flag is reset afterwards.
	 * 
	 * <p>
	 * A barcode action can use this to redirect the driver with
	 * {@link #followPath(List)} without triggering the same barcode again.
	 * </p>
	 * 
	 * @param skip
	 *            True if the barcode at the current tile should be ignored.
	 */
	public void skipCurrentBarcode(boolean skip) {
		skipCurrentBarcode.set(skip);
	}

	private void reset() {
		// Reset state
		getMaze().clear();
	}

	private void stopSubroutines() {
		// Stop subroutines
		navigator.stop();
		lineFinder.stop();
		barcodeScanner.stop();
		collisionAvoider.stop();
	}

	/*
	 * States
	 */

	protected void init() {
		// Increment counter for start tile
		incrementLineAdjustCounter();

		// Start cycling
		transition(ExplorerState.NEXT_CYCLE);
	}

	protected void nextCycle() {
		// Get the current tile
		startTile = getCurrentTile();

		if (!startTile.isExplored()) {
			// Scan for walls
			transition(ExplorerState.SCAN);
		} else {
			// Go to next tile
			transition(ExplorerState.GO_NEXT_TILE);
		}
	}

	protected void scan() {
		// Scan and update current tile
		log("Scan for edges at (" + startTile.getX() + ", " + startTile.getY() + ")");
		bindTransition(scanAndUpdate(startTile), ExplorerState.AFTER_SCAN);
	}

	protected void afterScan() {
		// Set as explored
		getMaze().setExplored(startTile.getPosition());

		// Go to next tile
		transition(ExplorerState.GO_NEXT_TILE);
	}

	protected void goToNext() {
		skipToNextTile();
	}

	public void skipToNextTile() {
		// Get the next tile
		goalTile = getCommander().nextTile(startTile);

		// Objective completed
		if (goalTile == null) {
			noNextTile();
			return;
		}

		// Create and follow path to next tile
		log("Go to tile (" + goalTile.getX() + ", " + goalTile.getY() + ")");
		followPathToTile(goalTile);
	}

	public void followPathToTile(Tile tile) {
		List<Tile> tilePath = getMode().createPath(getCurrentTile(), tile);
		followPath(tilePath);
	}

	public void recalculateAndFollowPath() {
		startTile = getCurrentTile();
		skipToNextTile();
	}

	/**
	 * Follow the given path.
	 * 
	 * @param tilePath
	 *            The path to follow.
	 */
	public void followPath(List<Tile> tilePath) {
		// Stop collision avoider
		if (collisionAvoider.isDrivingToCorridor()) {
			collisionAvoider.stop();
		}

		// Set path
		navigator.stop();
		navigator.setPath(getPathFinder().toWaypointPath(tilePath));

		// Follow path until before traveling
		transition(ExplorerState.NEXT_WAYPOINT);
	}

	protected void nextWaypoint() {
		// Clean up
		barcodeScanner.stop();

		// Update flags
		incrementLineAdjustCounter();

		// Execute barcode action on current way point
		transition(ExplorerState.BARCODE_ACTION);
	}

	protected void barcodeAction() {
		Tile currentTile = getCurrentTile();
		/*
		 * Check if barcode action should be executed.
		 * 
		 * Implementation note: order is important! The flag should be cleared
		 * regardless of whether barcode actions are enabled.
		 */
		if (!skipCurrentBarcode.getAndSet(false) && isBarcodeActionEnabled() && currentTile.hasBarcode()) {
			log("Performing barcode action for (" + currentTile.getX() + ", " + currentTile.getY() + ")");
			// Pause navigator
			// Note: Cannot interrupt report state
			if (navigator.getState() != NavigatorState.REPORT) {
				navigator.pause();
			}
			// Resume when action is done
			Future<?> future = getCommander().getAction(currentTile.getBarcode()).performAction(player);
			bindTransition(future, ExplorerState.NAVIGATE);
		} else {
			// No action, just continue
			transition(ExplorerState.NAVIGATE);
		}
	}

	protected void navigate() {
		// Navigate until about to travel
		navigator.pauseAt(NavigatorState.TRAVEL);

		// Start navigation
		if (navigator.isRunning()) {
			navigator.resume();
		} else {
			navigator.start();
		}
	}

	protected void avoidCollision() {
		if (collisionAvoider.isBlocked()) {
			// Robot detected, restart collision avoider
			collisionAvoider.stop();
			collisionAvoider.start();
		} else {
			// No robot detected
			transition(ExplorerState.CLEAR_BARCODE);
		}
	}

	protected void clearBarcode() {
		if (getCurrentTile().hasBarcode()) {
			// Travel off barcode
			double clearDistance = getBarcodeClearingDistance();
			log("Travel off barcode: " + String.format("%.02f", clearDistance));
			bindTransition(getRobot().getPilot().travelComplete(clearDistance), ExplorerState.BEFORE_TRAVEL);
		} else {
			// No barcode
			transition(ExplorerState.BEFORE_TRAVEL);
		}
	}

	protected void beforeTravel() {
		if (isLineAdjustEnabled() && shouldLineAdjust()) {
			// Start line finder
			lineFinder.start();
		} else {
			// No need to line adjust
			transition(ExplorerState.AFTER_LINE_ADJUST);
		}
	}

	protected void afterLineAdjust() {
		// Stop line finder, if still running
		lineFinder.stop();

		// Start barcode scanner if necessary
		if (shouldBarcode(navigator.getCurrentTarget()) && shouldBarcode) {
			barcodeScanner.start();
		}

		// Travel
		transition(ExplorerState.TRAVEL);
	}

	private void travel() {
		// Navigate until way point reached
		navigator.pauseAt(NavigatorState.NEXT);
		navigator.resume();
	}

	protected void beforeBarcode() {
		// Pause navigator immediately
		navigator.pause();

		log("Barcode found, pausing navigation");
	}

	protected void afterBarcode(Barcode barcode) {
		Tile tile = getCurrentTile();
		log("Barcode read, placing on: (" + tile.getX() + ", " + tile.getY() + ")");
		// Set barcode on tile
		setBarcodeTile(tile, barcode);
		// Travel
		transition(ExplorerState.TRAVEL);
	}

	/**
	 * Check if the barcode scanner should be started for the given way point.
	 */
	private boolean shouldBarcode(Waypoint nextWaypoint) {
		if (nextWaypoint == null) {
			// No next way point, assume not explored
			return true;
		} else {
			// Only scan if next way point is not yet explored
			Tile nextTile = getPathFinder().getTileAt(nextWaypoint);
			return !nextTile.isExplored();
		}
	}

	/*
	 * Exploration finished
	 */
	private void noNextTile() {
		// Clean up
		stopSubroutines();
		// Done
		finish();
	}

	/**
	 * Set the barcode and edges of a tile.
	 * 
	 * @param tile
	 *            The tile.
	 * @param barcode
	 *            The barcode.
	 */
	private void setBarcodeTile(Tile tile, Barcode barcode) {
		Orientation heading = getRobotHeading();

		// Make straight tile
		getMaze().setTileShape(tile.getPosition(), new TileShape(TileType.STRAIGHT, heading));
		// Set barcode
		getMaze().setBarcode(tile.getPosition(), barcode);
		// Mark as explored
		getMaze().setExplored(tile.getPosition());
	}

	/**
	 * Scan in the direction of *unknown* edges, and updates them accordingly.
	 * 
	 * @param tile
	 *            The tile to update.
	 */
	private Future<?> scanAndUpdate(final Tile tile) {
		final List<Float> angles = getScanAngles(tile);
		// Scan in front first
		ListenableFuture<List<Orientation>> front = scanFront(tile, angles);
		// Set up a chain to scan behind afterwards
		ListenableFuture<List<Orientation>> behind = Futures.transform(front,
				new AsyncFunction<List<Orientation>, List<Orientation>>() {
					@Override
					public ListenableFuture<List<Orientation>> apply(List<Orientation> input) throws Exception {
						return scanBehind(tile, angles);
					}
				});
		// Combine results
		@SuppressWarnings("unchecked")
		ListenableFuture<List<List<Orientation>>> frontAndBehind = Futures.allAsList(front, behind);
		// Place edges afterwards
		ListenableFuture<Void> walls = Futures.transform(frontAndBehind, new Function<List<List<Orientation>>, Void>() {
			@Override
			public Void apply(List<List<Orientation>> input) {
				placeTileWalls(tile, Iterables.concat(input));
				return null;
			}
		});
		return GuavaFutures.fromGuava(walls);
	}

	private ListenableFuture<List<Orientation>> scanEdges(final Tile tile, final List<Float> angles) {
		// Read from scanner
		final Future<RangeFeature> future = getRobot().getRangeDetector().scanAsync(Floats.toArray(angles));
		// Get tile edges afterwards
		return Futures.transform(future, new Function<RangeFeature, List<Orientation>>() {
			@Override
			public List<Orientation> apply(RangeFeature feature) {
				return getTileEdges(tile, feature);
			}
		});
	}

	private ListenableFuture<List<Orientation>> scanFront(final Tile tile, List<Float> angles) {
		// Get angles in front of robot
		final List<Float> anglesFront = new ArrayList<Float>();
		for (Float angle : angles) {
			if (isInFront(angle)) {
				anglesFront.add(angle);
			}
		}
		// Exit if nothing to scan
		if (anglesFront.isEmpty()) {
			return Futures.immediateFuture(Collections.<Orientation> emptyList());
		}
		return scanEdges(tile, anglesFront);
	}

	private ListenableFuture<List<Orientation>> scanBehind(final Tile tile, List<Float> angles) {
		// Get angles behind robot
		final List<Float> anglesBehind = new ArrayList<Float>();
		for (Float angle : angles) {
			if (!isInFront(angle)) {
				// Rotate angle
				anglesBehind.add(normalize(angle - 180f));
			}
		}
		// Exit if nothing to scan
		if (anglesBehind.isEmpty()) {
			return Futures.immediateFuture(Collections.<Orientation> emptyList());
		}
		// Rotate robot
		Future<?> rotate = getRobot().getPilot().rotateComplete(180f);
		// Scan afterwards
		return Futures.transform(rotate, new AsyncFunction<Object, List<Orientation>>() {
			@Override
			public ListenableFuture<List<Orientation>> apply(Object input) throws Exception {
				return scanEdges(tile, anglesBehind);
			}
		});
	}

	/**
	 * Check if the given relative angle is in front of the robot.
	 * 
	 * @param angle
	 *            The angle to check.
	 */
	private boolean isInFront(float angle) {
		angle = normalize(angle);
		return angle <= 135 && angle >= -135;
	}

	/**
	 * Get the new edges of a tile using the given detected features.
	 * 
	 * @param tile
	 *            The tile to update.
	 * @param feature
	 *            The detected features.
	 */
	private List<Orientation> getTileEdges(Tile tile, RangeFeature feature) {
		List<Orientation> edges = new ArrayList<Orientation>();
		if (feature != null) {
			float relativeHeading = getMaze().toRelative(feature.getPose().getHeading());
			for (RangeReading reading : feature.getRangeReadings()) {
				Orientation orientation = angleToOrientation(reading.getAngle() + relativeHeading);
				edges.add(orientation);
			}
		}
		return edges;
	}

	/**
	 * Place the edges of a tile, filling the remaining edges with openings.
	 * 
	 * @param tile
	 *            The tile to update.
	 * @param edges
	 *            The wall edges.
	 */
	private void placeTileWalls(Tile tile, Iterable<Orientation> edges) {
		// Place walls
		for (Orientation edge : edges) {
			getMaze().setEdge(tile.getPosition(), edge, EdgeType.WALL);
		}
		// Replace unknown edges with openings
		for (Orientation orientation : tile.getUnknownSides()) {
			getMaze().setEdge(tile.getPosition(), orientation, EdgeType.OPEN);
		}
	}

	/**
	 * Get the angles towards <strong>unknown</strong> edges to scan.
	 */
	private List<Float> getScanAngles(Tile tile) {
		List<Float> list = new ArrayList<Float>();
		// TODO: pas heading vastzetten als we linefinder gedaan hebben.
		float heading = getPose().getHeading();

		for (Orientation direction : tile.getUnknownSides()) {
			// Get absolute angle relative to positive X (east) direction
			float angle = getMaze().toAbsolute(Orientation.EAST.angleTo(direction));
			list.add(normalize(angle - heading));
		}

		// Sort angles
		Collections.sort(list);
		return list;
	}

	/**
	 * Get the distance necessary to travel off the barcode on which the robot
	 * is currently located.
	 */
	private double getBarcodeClearingDistance() {
		Point currentPosition = getPose().getLocation();
		Waypoint currentWaypoint = getPathFinder().toWaypoint(getCurrentTile());
		Waypoint nextWaypoint = navigator.getCurrentTarget();

		// Get traveling line
		Line line = new Line(currentWaypoint.x, currentWaypoint.y, nextWaypoint.x, nextWaypoint.y);
		float angle = currentWaypoint.angleTo(nextWaypoint);

		// Get target position to clear barcode
		Point target = currentWaypoint.pointAt(getBarcodeClearing(), angle);

		// Project position on traveling line to ignore offsets from center line
		Point clearing = currentPosition.projectOn(line);

		if (nextWaypoint.distance(clearing) >= nextWaypoint.distance(target)) {
			// Robot is further away from way point than clearing target
			return target.distance(clearing);
		} else {
			// Robot is closer to way point than clearing target
			return 0d;
		}
	}

	/**
	 * Get the distance necessary to travel off a barcode.
	 */
	private float getBarcodeClearing() {
		return getMaze().getTileSize() / 3f;
	}

	/*
	 * Line adjust counter
	 */

	/**
	 * Check whether the robot should adjust its position with the line finder
	 * and resets the flag. Triggered before traveling to a tile.
	 */
	private boolean shouldLineAdjust() {
		return shouldLineAdjust.getAndSet(false);
	}

	/**
	 * Force a line adjust on the next tile.
	 */
	public void forceLineAdjust() {
		shouldLineAdjust.set(true);
		lineAdjustCounter.set(0);
	}

	/**
	 * Increment the way point counter which controls when the robot adjust its
	 * position with the line finder. Triggered when a way point is reached.
	 */
	private void incrementLineAdjustCounter() {
		// Increment counter for line finder
		if (lineAdjustCounter.incrementAndGet() >= getLineAdjustInterval()) {
			forceLineAdjust();
		}
	}

	/*
	 * Helpers
	 */

	/**
	 * Get the orientation the robot is looking towards.
	 */
	public Orientation getRobotHeading() {
		float relativeHeading = getMaze().toRelative(getPose().getHeading());
		return angleToOrientation(relativeHeading);
	}

	/**
	 * Get the orientation corresponding to the given angle.
	 * 
	 * @param angle
	 *            The angle.
	 */
	private static Orientation angleToOrientation(float angle) {
		angle = normalize(angle);

		if (angle > -45 && angle <= 45) {
			return Orientation.EAST;
		} else if (angle > 45 && angle <= 135) {
			return Orientation.NORTH;
		} else if (angle > 135 || angle <= -135) {
			return Orientation.WEST;
		} else {
			return Orientation.SOUTH;
		}
	}

	/**
	 * Normalize the given angle between -180� and +180�.
	 * 
	 * @param angle
	 *            The angle to normalize.
	 */
	private static float normalize(float angle) {
		while (angle > 180)
			angle -= 360f;
		while (angle < -180)
			angle += 360f;
		return angle;
	}

	/**
	 * Get the tile from which the robot started navigating.
	 */
	public Tile getStartTile() {
		return startTile;
	}

	/**
	 * Get the tile at which the robot is currently located.
	 */
	public Tile getCurrentTile() {
		return getPathFinder().getTileAt(getPose());
	}

	/**
	 * Get the tile the robot is currently looking towards.
	 */
	public Tile getFacingTile() {
		return getMaze().getOrCreateNeighbor(getCurrentTile(), getRobotHeading());
	}

	/**
	 * Get the next tile to which the robot is navigating.
	 */
	public Tile getGoalTile() {
		return goalTile;
	}

	/**
	 * Get the current pose of the robot.
	 */
	private Pose getPose() {
		return getRobot().getPoseProvider().getPose();
	}

	/*
	 * State
	 */

	@Override
	public void stateStarted() {
		log("Exploration started.");
		// Reset
		reset();
		stopSubroutines();
		// Initialize
		transition(ExplorerState.INIT);
	}

	@Override
	public void stateStopped() {
		log("Exploration stopped.");
		// Stop
		stopSubroutines();
	}

	@Override
	public void stateFinished() {
		log("Exploration completed.");
	}

	@Override
	public void statePaused(ExplorerState currentState, boolean onTransition) {
	}

	@Override
	public void stateResumed(ExplorerState currentState) {
	}

	@Override
	public void stateTransitioned(ExplorerState nextState) {
	}

	/*
	 * Navigator
	 */

	@Override
	public void navigatorStarted(Pose pose) {
	}

	@Override
	public void navigatorStopped(Pose pose) {
	}

	@Override
	public void navigatorPaused(NavigatorState currentState, Pose pose, boolean onTransition) {
		// Only respond to pauses on transitions
		if (!onTransition)
			return;

		if (getState() == ExplorerState.NAVIGATE) {
			// Paused after rotating and before traveling
			assert (currentState == NavigatorState.TRAVEL);
			transition(ExplorerState.AVOID_COLLISION);
		}
	}

	@Override
	public void navigatorResumed(NavigatorState currentState, Pose pose) {
	}

	@Override
	public void navigatorAtWaypoint(Waypoint waypoint, Pose pose) {
		// At way point
		if (!navigator.pathCompleted()) {
			transition(ExplorerState.NEXT_WAYPOINT);
		}
	}

	@Override
	public void navigatorCompleted(Waypoint waypoint, Pose pose) {
		if (collisionAvoider.isDrivingToCorridor()) {
			// Corridor reached
			collisionAvoider.atCorridor();
		} else {
			// Path completed
			transition(ExplorerState.NEXT_CYCLE);
		}
	}

	private class LineFinderListener extends DefaultStateListener<LineFinder.LineFinderState> {
		@Override
		public void stateFinished() {
			transition(ExplorerState.AFTER_LINE_ADJUST);
		}
	}

	private class BarcodeListener implements BarcodeScannerListener {
		@Override
		public void onStartBarcode() {
			transition(ExplorerState.BEFORE_BARCODE);
		}

		@Override
		public void onEndBarcode(final Barcode barcode) {
			afterBarcode(barcode);
		}
	}

	private class CollisionAvoiderListener extends DefaultStateListener<CollisionAvoider.AvoiderState> {
		@Override
		public void stateFinished() {
			transition(ExplorerState.NEXT_CYCLE);
		}
	}

	public enum ExplorerState implements State<Driver, ExplorerState> {
		INIT {
			@Override
			public void execute(Driver explorer) {
				explorer.init();
			}
		},
		NEXT_CYCLE {
			@Override
			public void execute(Driver explorer) {
				explorer.nextCycle();
			}
		},
		SCAN {
			@Override
			public void execute(Driver explorer) {
				explorer.scan();
			}
		},
		AFTER_SCAN {
			@Override
			public void execute(Driver explorer) {
				explorer.afterScan();
			}
		},
		GO_NEXT_TILE {
			@Override
			public void execute(Driver explorer) {
				explorer.goToNext();
			}
		},
		NEXT_WAYPOINT {
			@Override
			public void execute(Driver explorer) {
				explorer.nextWaypoint();
			}
		},
		BARCODE_ACTION {
			@Override
			public void execute(Driver explorer) {
				explorer.barcodeAction();
			}
		},
		NAVIGATE {
			@Override
			public void execute(Driver explorer) {
				explorer.navigate();
			}
		},
		AVOID_COLLISION {
			@Override
			public void execute(Driver explorer) {
				explorer.avoidCollision();
			}
		},
		CLEAR_BARCODE {
			@Override
			public void execute(Driver explorer) {
				explorer.clearBarcode();
			}
		},
		BEFORE_TRAVEL {
			@Override
			public void execute(Driver explorer) {
				explorer.beforeTravel();
			}
		},
		AFTER_LINE_ADJUST {
			@Override
			public void execute(Driver explorer) {
				explorer.afterLineAdjust();
			}
		},
		BEFORE_BARCODE {
			@Override
			public void execute(Driver explorer) {
				explorer.beforeBarcode();
			}
		},
		TRAVEL {
			@Override
			public void execute(Driver explorer) {
				explorer.travel();
			}
		}
	}

}