package mazestormer.command;

import mazestormer.barcode.Barcode;
import mazestormer.barcode.action.IAction;
import mazestormer.maze.Tile;
import mazestormer.player.Player;

/**
 * Uses different controlmodes to achieve an objective.
 */
public abstract class Commander {

	/*
	 * Data
	 */
	private final Player player;
	private final Driver driver;

	/*
	 * Control Modes
	 */
	private ControlMode currentMode;

	/*
	 * Constructor
	 */

	public Commander(Player player) {
		this.player = player;
		this.driver = new Driver(player, this);
	}

	/*
	 * Getters
	 */

	public final Player getPlayer() {
		return player;
	}

	public Driver getDriver() {
		return driver;
	}

	/*
	 * Objective management
	 */

	/**
	 * Starts persuing the objective of this commander.
	 */
	protected void start() {
		getDriver().start();
	}

	/**
	 * Stops persuing the objective of this commander.
	 */
	protected void stop() {
		getDriver().stop();
		releaseControl();
	}

	/*
	 * Control mode management
	 */

	public final ControlMode getMode() {
		return currentMode;
	}

	public abstract ControlMode nextMode(ControlMode currentMode);

	protected final void setMode(ControlMode mode) {
		releaseControl();
		this.currentMode = mode;
		takeControl(mode);
	}

	private final void takeControl(ControlMode mode) {
		currentMode.takeControl();
	}

	private final void releaseControl() {
		if (getMode() != null) {
			getMode().releaseControl();
		}
		this.currentMode = null;
	}

	/*
	 * Driver support
	 */

	public Tile nextTile(Tile currentTile) {
		Tile nextTile = getMode().nextTile(currentTile);
		while (nextTile == null) {
			ControlMode nextMode = nextMode(getMode());
			if (nextMode == null) {
				// Done
				return null;
			} else {
				// Try next mode
				setMode(nextMode);
				nextTile = getMode().nextTile(currentTile);
			}
		}
		return nextTile;
	}

	public IAction getAction(Barcode barcode) {
		return getMode().getAction(barcode);
	}

}
