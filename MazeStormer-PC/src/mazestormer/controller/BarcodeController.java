package mazestormer.controller;

import mazestormer.barcode.ActionType;
import mazestormer.barcode.BarcodeRunner;
import mazestormer.barcode.BarcodeSpeed;
import mazestormer.barcode.IAction;
import mazestormer.barcode.NoAction;
import mazestormer.barcode.Threshold;
import mazestormer.maze.Maze;
import mazestormer.robot.ControllableRobot;
import mazestormer.state.AbstractStateListener;
import mazestormer.util.Future;
import mazestormer.util.FutureListener;

public class BarcodeController extends SubController implements
		IBarcodeController {

	private Future<?> action;
	private BarcodeRunner barcodeRunner;

	private double scanTravelSpeed = 2; // [cm/sec]

	public BarcodeController(MainController mainController) {
		super(mainController);
	}

	private ControllableRobot getRobot() {
		return getMainController().getControllableRobot();
	}

	private Maze getMaze() {
		return getMainController().getMaze();
	}

	private void log(String logText) {
		getMainController().getPlayer().getLogger().info(logText);
	}

	@Override
	public void startAction(ActionType actionType) {
		// Post state
		postActionState(BarcodeActionEvent.EventType.STARTED);
		// Start action
		this.action = getAction(actionType)
				.performAction(getRobot(), getMaze());
		this.action.addFutureListener(new ActionListener());
	}

	@Override
	public void stopAction() {
		if (this.action != null) {
			// Stop action
			this.action.cancel();
			this.action = null;
		}
	}

	private static IAction getAction(ActionType actionType) {
		return (actionType != null) ? actionType.build() : new NoAction();
	}

	@Override
	public double getScanSpeed() {
		return this.scanTravelSpeed;
	}

	@Override
	public void setScanSpeed(double speed) {
		this.scanTravelSpeed = speed;
	}

	@Override
	public int getWBThreshold() {
		return Threshold.WHITE_BLACK.getThresholdValue();
	}

	@Override
	public void setWBThreshold(int threshold) {
		Threshold.WHITE_BLACK.setThresholdValue(threshold);
	}

	@Override
	public int getBWThreshold() {
		return Threshold.BLACK_WHITE.getThresholdValue();
	}

	@Override
	public void setBWThreshold(int threshold) {
		Threshold.BLACK_WHITE.setThresholdValue(threshold);
	}

	@Override
	public void startScan() {
		// Prepare
		barcodeRunner = new BarcodeRunner(getRobot(), getMaze()) {
			@Override
			protected void log(String message) {
				BarcodeController.this.log(message);
			}
		};
		barcodeRunner.addStateListener(new BarcodeListener());
		barcodeRunner.setPerformAction(false);
		barcodeRunner.setScanSpeed(getScanSpeed());

		// Start
		getRobot().getPilot().forward();
		barcodeRunner.start();
	}

	@Override
	public void stopScan() {
		if (barcodeRunner != null) {
			barcodeRunner.stop();
			barcodeRunner = null;
		}
	}

	private void postScanState(BarcodeScanEvent.EventType eventType) {
		postEvent(new BarcodeScanEvent(eventType));
	}

	private void postActionState(BarcodeActionEvent.EventType eventType) {
		postEvent(new BarcodeActionEvent(eventType));
	}

	@Override
	public double getLowSpeed() {
		return BarcodeSpeed.LOW.getBarcodeSpeedValue();
	}

	@Override
	public void setLowSpeed(double speed) {
		BarcodeSpeed.LOW.setBarcodeSpeedValue(speed);
	}

	@Override
	public double getHighSpeed() {
		return BarcodeSpeed.HIGH.getBarcodeSpeedValue();
	}

	@Override
	public void setHighSpeed(double speed) {
		BarcodeSpeed.HIGH.setBarcodeSpeedValue(speed);
	}

	@Override
	public double getLowerSpeedBound() {
		return BarcodeSpeed.LOWERBOUND.getBarcodeSpeedValue();
	}

	@Override
	public double getUpperSpeedBound() {
		return BarcodeSpeed.UPPERBOUND.getBarcodeSpeedValue();
	}

	private class ActionListener implements FutureListener<Object> {

		@Override
		public void futureResolved(Future<? extends Object> future) {
			// Post state
			postActionState(BarcodeActionEvent.EventType.STOPPED);
		}

		@Override
		public void futureCancelled(Future<? extends Object> future) {
			// Stop robot
			getRobot().getPilot().stop();
			// Post state
			postActionState(BarcodeActionEvent.EventType.STOPPED);
		}

	}

	private class BarcodeListener extends
			AbstractStateListener<BarcodeRunner.BarcodeState> {

		@Override
		public void stateStarted() {
			// Post state
			postScanState(BarcodeScanEvent.EventType.STARTED);
		}

		@Override
		public void stateStopped() {
			// Post state
			postScanState(BarcodeScanEvent.EventType.STOPPED);
		}

		@Override
		public void stateFinished() {
			stateStopped();
		}

	}

}
