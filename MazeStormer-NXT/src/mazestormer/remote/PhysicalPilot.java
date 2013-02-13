package mazestormer.remote;

import lejos.nxt.Motor;
import lejos.robotics.navigation.DifferentialPilot;
import mazestormer.command.Command;
import mazestormer.command.PilotParameterCommand;
import mazestormer.command.RotateCommand;
import mazestormer.command.StopCommand;
import mazestormer.command.TravelCommand;
import mazestormer.report.MoveReporter;
import mazestormer.report.MovementReporter;
import mazestormer.robot.Pilot;
import mazestormer.robot.Robot;

public class PhysicalPilot extends DifferentialPilot implements Pilot,
		MessageListener<Command> {

	private final NXTCommunicator communicator;

	private MovementReporter movementReporter;

	public PhysicalPilot(NXTCommunicator communicator) {
		super(Robot.leftWheelDiameter, Robot.rightWheelDiameter,
				Robot.trackWidth, Motor.A, Motor.B, false);

		this.communicator = communicator;
		setup();
	}

	private void setup() {
		// Command listeners
		addMessageListener(this);

		// Move listener
		addMoveListener(new MoveReporter(communicator));

		// Start reporting movements
		movementReporter = new MovementReporter(communicator, this);
		movementReporter.start();
	}

	private void addMessageListener(MessageListener<Command> listener) {
		communicator.addListener(listener);
	}

	@Override
	public void terminate() {
		// Stop the pilot
		stop();

		// Stop reporting movements
		movementReporter.stop();
	}

	@Override
	public void messageReceived(Command command) {
		if (command instanceof TravelCommand) {
			onTravelCommand((TravelCommand) command);
		}
		if (command instanceof RotateCommand) {
			onRotateCommand((RotateCommand) command);
		}
		if (command instanceof StopCommand) {
			onStopCommand((StopCommand) command);
		}
		if (command instanceof PilotParameterCommand) {
			onParameterCommand((PilotParameterCommand) command);
		}
	}

	private void onTravelCommand(TravelCommand command) {
		travel(command.getDistance(), true);
	}

	private void onRotateCommand(RotateCommand command) {
		double angle = command.getAngle();
		if (Double.isInfinite(angle)) {
			if (angle > 0) {
				rotateLeft();
			} else {
				rotateRight();
			}
		} else {
			rotate(angle, true);
		}
	}

	private void onStopCommand(StopCommand command) {
		stop();
	}

	private void onParameterCommand(PilotParameterCommand command) {
		double value = command.getValue();
		switch (command.getType()) {
		case SET_TRAVEL_SPEED:
			setTravelSpeed(value);
			break;
		case SET_ROTATE_SPEED:
			setRotateSpeed(value);
			break;
		case SET_ACCELERATION:
			setAcceleration((int) value);
			break;
		default:
			break;
		}
	}

}
