package mazestormer.simulator;

import mazestormer.infrared.IRRobot;
import mazestormer.simulator.WorldIRDetector.IRDetectionMode;
import mazestormer.world.World;

public class SemiPhysicalRobotIRSensor extends WorldIRSensor {

	public SemiPhysicalRobotIRSensor(World world) {
		super(world, IRRobot.ROBOT_IR_RANGE, IRRobot.class, IRDetectionMode.SEMI_PHYSICAL);
	}

}
