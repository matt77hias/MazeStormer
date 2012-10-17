package mazestormer.controller;

import mazestormer.controller.PolygonEvent.EventType;
import mazestormer.robot.Robot;

public class PolygonControlController extends SubController implements
		IPolygonControlController {

	public PolygonControlController(MainController mainController) {
		super(mainController);
	}

	private PolygonRunner runner;

	private Robot getRobot() {
		return getMainController().getRobot();
	}

	@Override
	public void startPolygon(int nbSides, double sideLength, Direction direction) {
		runner = new PolygonRunner(nbSides, sideLength, direction);
		runner.start();
	}

	@Override
	public void stopPolygon() {
		if (runner != null) {
			runner.stop();
			runner = null;
		}
	}

	private void postState(EventType eventType) {
		postEvent(new PolygonEvent(eventType));
	}

	private class PolygonRunner implements Runnable {

		private final int nbSides;
		private final double sideLength;
		private final Direction direction;

		private final Robot robot;
		private boolean isRunning = false;

		public PolygonRunner(int nbSides, double sideLength, Direction direction) {
			this.robot = getRobot();
			this.nbSides = nbSides;
			this.sideLength = sideLength;
			this.direction = direction;
		}

		public void start() {
			isRunning = true;
			new Thread(this).start();
			postState(EventType.STARTED);
		}

		public void stop() {
			if (isRunning()) {
				isRunning = false;
				robot.stop();
				postState(EventType.STOPPED);
			}
		}

		public synchronized boolean isRunning() {
			return isRunning;
		}

		@Override
		public void run() {
			int parity = (direction == Direction.ClockWise) ? -1 : 1;
			double angle = (double) parity * 360d / (double) nbSides;

			for (int i = 0; i < nbSides; ++i) {
				if (!isRunning())
					return;
				robot.travel(sideLength);
				if (!isRunning())
					return;
				robot.rotate(angle);
			}

			stop();
		}

	}

}