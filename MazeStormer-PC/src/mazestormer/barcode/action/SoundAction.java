package mazestormer.barcode.action;

import static com.google.common.base.Preconditions.checkNotNull;
import mazestormer.maze.IMaze;
import mazestormer.player.Player;
import mazestormer.robot.ControllableRobot;
import mazestormer.util.Future;
import mazestormer.util.ImmediateFuture;

public class SoundAction implements IAction {

	@Override
	public Future<?> performAction(Player player) {
		checkNotNull(player);
		ControllableRobot robot = (ControllableRobot) player.getRobot();
		checkNotNull(robot);
		IMaze maze = player.getMaze();
		checkNotNull(maze);
		
		robot.getSoundPlayer().playSound();

		return new ImmediateFuture<Void>(null);
	}

}