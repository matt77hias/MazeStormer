package mazestormer.player;

import java.util.logging.Level;
import java.util.logging.Logger;

import mazestormer.infrared.IRRobot;
import mazestormer.maze.IMaze;

public class RelativePlayer extends AbstractPlayer {

	public RelativePlayer(String playerID, IRRobot robot, IMaze maze) {
		// Set player identifier
		setPlayerID(playerID);
		// Set robot
		setRobot(robot);
		// Set maze
		setMaze(maze);

		// Create logger
		Logger logger = Logger.getLogger(getPlayerID());
		logger.setLevel(Level.ALL);
		setLogger(logger);
	}

	@Override
	public void setRobot(IRRobot robot) {
		super.setRobot(robot);
	}

	@Override
	public void setMaze(IMaze maze) {
		super.setMaze(maze);
	}

}
