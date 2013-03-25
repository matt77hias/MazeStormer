package mazestormer.game;

import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import lejos.robotics.navigation.Move;
import lejos.robotics.navigation.MoveListener;
import lejos.robotics.navigation.MoveProvider;
import lejos.robotics.navigation.Pose;
import mazestormer.barcode.Barcode;
import mazestormer.barcode.TeamTreasureTrekBarcodeMapping;
import mazestormer.explore.ExplorerRunner;
import mazestormer.maze.Edge.EdgeType;
import mazestormer.maze.Maze;
import mazestormer.maze.Orientation;
import mazestormer.maze.Tile;
import mazestormer.player.Player;
import mazestormer.robot.ControllableRobot;
import mazestormer.util.LongPoint;

public class GameRunner implements GameListener {

	/**
	 * The frequency of position updates.
	 */
	private static final long updateFrequency = 2000; // in ms

	private final Player player;
	private final Game game;
	private final ExplorerRunner explorerRunner;

	private final PositionReporter positionReporter;
	private final PositionPublisher positionPublisher;
	private final ScheduledExecutorService positionExecutor = Executors
			.newSingleThreadScheduledExecutor(factory);

	private static final ThreadFactory factory = new ThreadFactoryBuilder()
			.setNameFormat("GameRunner-%d").build();

	private int objectNumber;

	public GameRunner(Player player, Game game) {
		this.player = player;
		this.explorerRunner = new ExplorerRunner(player) {
			@Override
			protected void log(String message) {
				GameRunner.this.log(message);
			}
		};
		explorerRunner.setBarcodeMapping(new TeamTreasureTrekBarcodeMapping(
				this));

		this.game = game;
		game.addGameListener(this);

		this.positionReporter = new PositionReporter();
		this.positionPublisher = new PositionPublisher();
	}

	protected void log(String message) {
		System.out.println(message);
	}

	private ControllableRobot getRobot() {
		return (ControllableRobot) player.getRobot();
	}

	public int getObjectNumber() {
		return objectNumber;
	}

	public void setWallsOnNextTile() {
		log("Object on next tile, set walls");

		// Set all unknown edges to walls
		Tile nextTile = explorerRunner.getNextTile();
		EnumSet<Orientation> unknownSides = nextTile.getUnknownSides();
		for (Orientation side : unknownSides) {
			explorerRunner.getMaze().setEdge(nextTile.getPosition(), side,
					EdgeType.WALL);
		}
	}

	public void setSeesawWalls() {
		log("Seesaw on next tiles, set seesaw & barcode");
		
		// Set all unknown edges to walls or open
		Tile currentTile = explorerRunner.getCurrentTile();
		Barcode seesawBarcode = currentTile.getBarcode();
		Tile nextTile = explorerRunner.getNextTile();
		
		Orientation orientation = currentTile.orientationTo(nextTile);
		
		Maze maze = player.getMaze();
		
		LongPoint nextTilePosition = nextTile.getPosition();
		
		maze.setEdge(nextTilePosition, orientation.rotateClockwise(), EdgeType.WALL);
		maze.setEdge(nextTilePosition, orientation.rotateCounterClockwise(), EdgeType.WALL);
		maze.setEdge(nextTilePosition, orientation, EdgeType.OPEN);
		maze.getTileAt(nextTilePosition).setIgnoreFlag(true);
		
		nextTilePosition = orientation.shift(nextTilePosition);
		
		maze.setEdge(nextTilePosition, orientation.rotateClockwise(), EdgeType.WALL);
		maze.setEdge(nextTilePosition, orientation.rotateCounterClockwise(), EdgeType.WALL);
		maze.setEdge(nextTilePosition, orientation, EdgeType.OPEN);
		maze.getTileAt(nextTilePosition).setIgnoreFlag(true);
		
		nextTilePosition = orientation.shift(nextTilePosition);
		
		maze.setEdge(nextTilePosition, orientation.rotateClockwise(), EdgeType.WALL);
		maze.setEdge(nextTilePosition, orientation.rotateCounterClockwise(), EdgeType.WALL);
		maze.setEdge(nextTilePosition, orientation, EdgeType.OPEN);
		Barcode otherBarcode = TeamTreasureTrekBarcodeMapping.getOtherSeesawBarcode(seesawBarcode);
		maze.setBarcode(nextTilePosition, otherBarcode);
	}

	public void objectFound() {
		log("Report own object found");
		// Report object found
		game.objectFound();
		// Done
		stopGame();
	}
	
	public void onSeesaw(int barcode) {
		log("The seesaw is currently opened, onwards!");
		game.lockSeesaw(barcode);
	}
	
	public void offSeesaw() {
		game.unlockSeesaw();
	}

	public void afterObjectBarcode() {
		log("Object found, go to next tile");
		// Skip next tile
		explorerRunner.skipNextTile();
		// Create new path
		explorerRunner.createPath();
		// Object found action resolves after this
	}

	public boolean isRunning() {
		return explorerRunner.isRunning();
	}

	private void stopGame() {
		// Stop
		explorerRunner.stop();
		// Stop pilot
		getRobot().getPilot().stop();
		// Stop reporting
		stopPositionReport();
	}

	private void startPositionReport() {
		getRobot().getPilot().addMoveListener(positionReporter);
	}

	private void stopPositionReport() {
		getRobot().getPilot().removeMoveListener(positionReporter);
	}

	@Override
	public void onGameJoined() {
	}

	@Override
	public void onGameLeft() {
		// Stop
		onGameStopped();
	}

	@Override
	public void onGameRolled(int playerNumber, int objectNumber) {
		// Store object number
		this.objectNumber = objectNumber;
	}

	@Override
	public void onGameStarted() {
		// Reset player pose
		// TODO Do not reset when resuming from paused game?
		getRobot().getPoseProvider().setPose(new Pose());
		// Start
		explorerRunner.start();
		// Start reporting
		startPositionReport();
	}

	@Override
	public void onGamePaused() {
		// Pause
		explorerRunner.pause();
		// Stop pilot
		getRobot().getPilot().stop();
		// Stop reporting
		stopPositionReport();
	}

	@Override
	public void onGameStopped() {
		stopGame();
	}

	@Override
	public void onGameWon(int teamNumber) {
		// Not really needed, since it will be stopped later on
		// onGameStopped();
	}

	@Override
	public void onPlayerReady(String playerID, boolean isReady) {
	}

	@Override
	public void onObjectFound(String playerID) {
	}

	private class PositionReporter implements MoveListener {

		private ScheduledFuture<?> task;

		@Override
		public void moveStarted(Move event, MoveProvider mp) {
			if (task == null) {
				// Start publishing
				task = positionExecutor.scheduleWithFixedDelay(
						positionPublisher, 0, updateFrequency,
						TimeUnit.MILLISECONDS);
			}
		}

		@Override
		public void moveStopped(Move event, MoveProvider mp) {
			if (task != null) {
				// Stop publishing
				task.cancel(false);
				task = null;
			}
		}

	}

	private class PositionPublisher implements Runnable {

		@Override
		public void run() {
			game.updatePosition(getRobot().getPoseProvider().getPose());
		}

	}

}
