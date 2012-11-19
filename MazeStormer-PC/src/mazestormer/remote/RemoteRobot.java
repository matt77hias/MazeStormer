package mazestormer.remote;

import lejos.robotics.RangeScanner;
import lejos.robotics.localization.OdometryPoseProvider;
import lejos.robotics.localization.PoseProvider;
import mazestormer.command.CommandType;
import mazestormer.condition.Condition;
import mazestormer.condition.ConditionType;
import mazestormer.condition.LightCompareCondition;
import mazestormer.detect.RangeFeatureDetector;
import mazestormer.detect.RangeScannerFeatureDetector;
import mazestormer.robot.CalibratedLightSensor;
import mazestormer.robot.Pilot;
import mazestormer.robot.Robot;
import mazestormer.robot.SoundPlayer;
import mazestormer.simulator.DelegatedCalibratedLightSensor;

public class RemoteRobot extends RemoteComponent implements Robot {

	private RemotePilot pilot;
	private PoseProvider poseProvider;

	private RemoteLightSensor light;
	private CalibratedLightSensor calibratedLight;

	private RangeScanner scanner;
	private RangeScannerFeatureDetector detector;

	private SoundPlayer soundPlayer;

	public RemoteRobot(RemoteCommunicator communicator) {
		super(communicator);
	}

	@Override
	public Pilot getPilot() {
		if (pilot == null) {
			pilot = new RemotePilot(getCommunicator());
		}
		return pilot;
	}

	@Override
	public CalibratedLightSensor getLightSensor() {
		if (calibratedLight == null) {
			light = new RemoteLightSensor(getCommunicator());
			calibratedLight = new DelegatedCalibratedLightSensor(light);
		}
		return calibratedLight;
	}

	@Override
	public RangeScanner getRangeScanner() {
		if (scanner == null) {
			scanner = new RemoteRangeScanner();
		}
		return scanner;
	}

	@Override
	public RangeFeatureDetector getRangeDetector() {
		if (detector == null) {
			detector = new RangeScannerFeatureDetector(getRangeScanner());
			detector.setPoseProvider(getPoseProvider());
		}
		return detector;
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
		if (soundPlayer == null) {
			soundPlayer = new RemoteSoundPlayer(getCommunicator());
		}
		return soundPlayer;
	}

	@Override
	public CommandBuilder when(ConditionSource source,
			CompareOperator operator, double value) {
		Condition condition = null;
		switch (source) {
		case LIGHT:
			ConditionType type = null;
			switch (operator) {
			case GREATER_THAN:
				type = ConditionType.LIGHT_GREATER_THAN;
				break;
			case SMALLER_THAN:
				type = ConditionType.LIGHT_SMALLER_THAN;
				break;
			default:
				break;
			}
			condition = new LightCompareCondition(type, (int) value);
			break;
		}

		RemoteCommandBuilder builder = new RemoteCommandBuilder(
				getCommunicator(), CommandType.WHEN, condition);
		addMessageListener(builder);
		return builder;
	}

	@Override
	public void terminate() {
		// Terminate components
		getPilot().terminate();
		light.terminate();
		// Stop all communications
		getCommunicator().stop();
		// Remove registered message listeners
		super.terminate();
	}

}
