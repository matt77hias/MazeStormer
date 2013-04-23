package mazestormer.line;

import static com.google.common.base.Preconditions.checkNotNull;
import mazestormer.condition.Condition;
import mazestormer.condition.ConditionType;
import mazestormer.condition.LightCompareCondition;
import mazestormer.robot.CalibratedLightSensor;
import mazestormer.robot.ControllableRobot;
import mazestormer.util.Future;
import mazestormer.util.state.State;
import mazestormer.util.state.StateListener;
import mazestormer.util.state.StateMachine;

public class LineFinder extends
		StateMachine<LineFinder, LineFinder.LineFinderState>
		implements StateListener<LineFinder.LineFinderState> {

	/*
	 * Constants
	 */

	private final static double slowRotateSpeed = 30;
	private final static double fastRotateSpeed = 50;
	private final static double slowTravelSpeed = 1.0;
	private final static double fastTravelSpeed = 5.0;

	private final static double maxAttackAngle = 20.0;
	private final static double safetyAngle = 10.0;
	private final static double fastRotateAngle = -(90 - maxAttackAngle - safetyAngle);

	private final static int threshold = 85;

	/*
	 * Settings
	 */

	private final ControllableRobot robot;
	private double originalTravelSpeed;
	private double originalRotateSpeed;
	
	/*
	 * State
	 */

	private volatile double lineWidth;

	public LineFinder(ControllableRobot robot) {
		this.robot = checkNotNull(robot);
		addStateListener(this);
	}

	protected void log(String message) {
		System.out.println(message);
	}

	private Future<Void> onLine() {
		Condition condition = new LightCompareCondition(
				ConditionType.LIGHT_GREATER_THAN, threshold);
		return robot.when(condition).stop().build();
	}

	private Future<Void> offLine() {
		Condition condition = new LightCompareCondition(
				ConditionType.LIGHT_SMALLER_THAN, threshold);
		return robot.when(condition).stop().build();
	}

	protected void findLineStart() {
		// Save original speeds
		originalTravelSpeed = robot.getPilot().getTravelSpeed();
		originalRotateSpeed = robot.getPilot().getRotateSpeed();

		// Travel forward until on line
		log("Start looking for line.");
		robot.getPilot().setTravelSpeed(fastTravelSpeed);
		robot.getPilot().forward();
		bindTransition(onLine(), LineFinderState.FIND_LINE_END);
	}

	protected void findLineEnd() {
		log("On line, start looking for end of line.");

		// Travel forward until off line
		robot.getPilot().setTravelSpeed(slowTravelSpeed);
		robot.getPilot().forward();

		bindTransition(offLine(), LineFinderState.ROTATE_CENTER);
	}

	protected void rotateCenter() {
		log("Off line, positioning robot on line edge.");

		lineWidth = robot.getPilot().getMovement().getDistanceTraveled();
		double centerOffset = ControllableRobot.sensorOffset
				- robot.getLightSensor().getSensorRadius();
		log("Line width: " + lineWidth);
		log("Offset from center: " + centerOffset);

		// Travel forward to center robot on end of line
		robot.getPilot().setTravelSpeed(fastTravelSpeed);
		bindTransition(robot.getPilot().travelComplete(centerOffset),
				LineFinderState.ROTATE_FIXED);
	}

	protected void rotateFixed() {
		// Rotate fixed angle
		robot.getPilot().setRotateSpeed(fastRotateSpeed);
		bindTransition(robot.getPilot().rotateComplete(fastRotateAngle),
				LineFinderState.ROTATE_UNTIL_LINE);
	}

	protected void rotateUntilLine() {
		// Rotate until on line again
		log("Start looking for line again.");
		robot.getPilot().setRotateSpeed(slowRotateSpeed);
		robot.getPilot().rotateRight();

		bindTransition(onLine(), LineFinderState.POSITION_PERPENDICULAR);
	}

	protected void positionPerpendicular() {
		log("On line, rotating robot perpendicular to line.");

		// Get sensor angle
		double sensorAngle = getSensorAngle();
		log("Angle adjusting for sensor radius: " + sensorAngle);

		// Position perpendicular to line
		double angle = 90d + sensorAngle;
		robot.getPilot().setRotateSpeed(fastRotateSpeed);
		bindTransition(robot.getPilot().rotateComplete(angle),
				LineFinderState.POSITION_CENTER);
	}

	protected void positionCenter() {
		// Position robot center on center of line
		log("Positioning on center of line.");
		double offset = -lineWidth / 2;
		bindTransition(robot.getPilot().travelComplete(offset),
				LineFinderState.FINISH);
	}

	/**
	 * Get the angle needed to account for the sensor radius.
	 * 
	 * <p>
	 * The sensor radius causes the robot to rotate further than the actual edge
	 * of the line. This is adjusted by rotating by an extra angle.
	 * </p>
	 * 
	 * <p>
	 * This angle corresponds to the central angle ({@code alpha}) of a secant
	 * line in a circle. The circle has a radius given by
	 * {@link ControllableRobot#sensorOffset} ({@code so}) and the secant line
	 * has a length given by {@link CalibratedLightSensor#getSensorRadius()} (
	 * {@code sr}). <br/>
	 * The length of a secant line in a circle given its central angle is:
	 * {@code sr = 2*so*sin(alpha/2)}
	 * </p>
	 * 
	 * <p>
	 * Therefore: {@code alpha = 2*asin(sr/(2*so))}
	 * </p>
	 */
	private double getSensorAngle() {
		return 2 * Math.asin(robot.getLightSensor().getSensorRadius()
				/ (2 * ControllableRobot.sensorOffset));
	}

	@Override
	public void stateStarted() {
		// Start
		transition(LineFinderState.FIND_LINE_START);
	}

	@Override
	public void stateStopped() {
		// Stop pilot
		robot.getPilot().stop();
		// Restore original speeds
		robot.getPilot().setTravelSpeed(originalTravelSpeed);
		robot.getPilot().setRotateSpeed(originalRotateSpeed);
	}

	@Override
	public void stateFinished() {
		// Stop
		stop();
	}

	@Override
	public void statePaused(LineFinderState currentState, boolean onTransition) {
	}

	@Override
	public void stateResumed(LineFinderState currentState) {
	}

	@Override
	public void stateTransitioned(LineFinderState nextState) {
	}

	public enum LineFinderState implements
			State<LineFinder, LineFinderState> {
		FIND_LINE_START {
			@Override
			public void execute(LineFinder finder) {
				finder.findLineStart();
			}
		},
		FIND_LINE_END {
			@Override
			public void execute(LineFinder finder) {
				finder.findLineEnd();
			}
		},
		ROTATE_CENTER {
			@Override
			public void execute(LineFinder finder) {
				finder.rotateCenter();
			}
		},
		ROTATE_FIXED {
			@Override
			public void execute(LineFinder finder) {
				finder.rotateFixed();
			}
		},
		ROTATE_UNTIL_LINE {
			@Override
			public void execute(LineFinder finder) {
				finder.rotateUntilLine();
			}
		},
		POSITION_PERPENDICULAR {
			@Override
			public void execute(LineFinder finder) {
				finder.positionPerpendicular();
			}
		},
		POSITION_CENTER {
			@Override
			public void execute(LineFinder finder) {
				finder.positionCenter();
			}
		},
		FINISH {
			@Override
			public void execute(LineFinder finder) {
				finder.finish();
			}
		};
	}

}