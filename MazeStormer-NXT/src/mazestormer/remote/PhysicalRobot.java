package mazestormer.remote;

import lejos.nxt.Motor;
import lejos.nxt.SensorPort;
import lejos.nxt.UltrasonicSensor;
import lejos.robotics.RangeFinder;
import lejos.robotics.RangeScanner;
import lejos.robotics.RegulatedMotor;
import lejos.robotics.RotatingRangeScanner;
import lejos.robotics.localization.OdometryPoseProvider;
import lejos.robotics.localization.PoseProvider;
import mazestormer.command.ShutdownCommandListener;
import mazestormer.detect.RangeFeatureDetector;
import mazestormer.robot.CalibratedLightSensor;
import mazestormer.robot.Pilot;
import mazestormer.robot.Robot;
import mazestormer.robot.SoundPlayer;

public class PhysicalRobot extends NXTComponent implements Robot {

	private PhysicalPilot pilot;
	private PhysicalLightSensor light;
	private RangeScanner scanner;
	private PoseProvider poseProvider;

	public PhysicalRobot(NXTCommunicator communicator) {
		super(communicator);
		setup();
	}

	@Override
	public Pilot getPilot() {
		return pilot;
	}

	@Override
	public CalibratedLightSensor getLightSensor() {
		return light;
	}

	@Override
	public RangeScanner getRangeScanner() {
		return scanner;
	}

	@Override
	public RangeFeatureDetector getRangeDetector() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PoseProvider getPoseProvider() {
		if (poseProvider == null) {
			poseProvider = new OdometryPoseProvider(getPilot());
		}
		return poseProvider;
	}

	@Override
	public SoundPlayer getSoundPlayer() {
		// TODO Auto-generated method stub
		return null;
	}

	public void setup() {
		// Pilot
		pilot = new PhysicalPilot(getCommunicator());

		// Light sensor
		light = new PhysicalLightSensor(getCommunicator());

		// Scanner
		RangeFinder sensor = new UltrasonicSensor(SensorPort.S2);
		RegulatedMotor headMotor = Motor.C;
		scanner = new RotatingRangeScanner(headMotor, sensor);

		// Command listeners
		addMessageListener(new ShutdownCommandListener(this));

		// Reporters
	}

	@Override
	public void terminate() {
		// Stop all communications
		getCommunicator().stop();
		// Remove registered message listeners
		super.terminate();
		// Release resources
		getPilot().terminate();
	}

	/**
	 * Not implemented on NXT.
	 */
	@Override
	public CommandBuilder when(ConditionSource source,
			CompareOperator operator, double value) {
		return null;
	}

}
