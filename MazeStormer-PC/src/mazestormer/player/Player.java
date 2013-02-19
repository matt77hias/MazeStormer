package mazestormer.player;

import static com.google.common.base.Preconditions.checkNotNull;
import mazestormer.maze.Maze;
import mazestormer.robot.Robot;

public class Player {
	
	private String playerName;
	private String playerID;
	
	private final Robot robot;
	private Maze maze;
	
	public Player(Robot robot) {
		this.robot = robot;
	}
	
	public String getPlayerName() {
		return this.playerName;
	}
	
	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}
	
	public String getPlayerID() {
		return this.playerID;
	}
	
	public void setPlayerID(String playerID) {
		this.playerID = playerID;
	}
	
	public Robot getRobot() {
		return this.robot;
	}
	
	public Maze getMaze() {
		if (this.maze == null) {
			this.maze = new Maze();
		}
		return this.maze;
	}
	
	public void setMaze(Maze maze) {
		checkNotNull(maze);
		this.maze = maze;
	}
}
