package mazestormer.controller;

import java.io.IOException;

import mazestormer.command.game.GameRunner;
import mazestormer.game.ConnectionMode;
import mazestormer.game.Game;
import mazestormer.game.GameListener;
import mazestormer.player.Player;
import mazestormer.simulator.VirtualRobot;
import mazestormer.world.World;
import mazestormer.world.WorldSimulator;
import peno.htttp.Callback;

import com.rabbitmq.client.Connection;

public class GameSetUpController extends SubController implements IGameSetUpController {

	private ConnectionMode connectionMode = ConnectionMode.LOCAL;
	private String gameID = "";

	private Game game;
	private WorldSimulator worldSimulator;
	private GameRunner runner;

	public GameSetUpController(MainController mainController) {
		super(mainController);
	}

	private World getWorld() {
		return getMainController().getWorld();
	}

	private void logToAll(String message) {
		logToWorld(message);
		for (Player player : getWorld().getPlayers()) {
			logTo(player, message);
		}
	}

	private void logToWorld(String message) {
		getWorld().getLogger().info(message);
	}

	private void logToLocal(String message) {
		logTo(getMainController().getPlayer(), message);
	}

	private void logTo(String playerID, String message) {
		logTo(getWorld().getPlayer(playerID), message);
	}

	private void logTo(Player player, String message) {
		if (player != null) {
			player.getLogger().info(message);
		}
	}

	@Override
	public ConnectionMode getConnectionMode() {
		return connectionMode;
	}

	@Override
	public void setConnectionMode(ConnectionMode connectionMode) {
		this.connectionMode = connectionMode;
	}

	@Override
	public String getPlayerID() {
		return getMainController().getPlayer().getPlayerID();
	}

	@Override
	public void setPlayerID(String newPlayerID) {
		Player player = getMainController().getPlayer();
		getWorld().renamePlayer(player.getPlayerID(), newPlayerID);
	}

	@Override
	public String getGameID() {
		return gameID;
	}

	@Override
	public void setGameID(String gameID) {
		this.gameID = gameID;
	}

	private void createGame() throws IOException {
		final Player localPlayer = getMainController().getPlayer();
		Connection connection = connectionMode.getConnection();
		game = new Game(connection, gameID, localPlayer);
		game.addGameListener(new Listener());

		worldSimulator = new WorldSimulator(connection, gameID, localPlayer, getWorld());

		runner = new GameRunner(localPlayer, game);
	}

	@Override
	public void joinGame() {
		if (!isReady()) {
			onNotReady();
			return;
		}

		try {
			// Create game
			createGame();
			// Join game
			game.join(new Callback<Void>() {
				@Override
				public void onSuccess(Void result) {
				}

				@Override
				public void onFailure(Throwable t) {
					logToAll("Error when joining: " + t.getMessage());
					t.printStackTrace();
				}
			});
		} catch (Exception e) {
			logToAll("Error when joining: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public void leaveGame() {
		try {
			if (game == null) {
				throw new Exception("Not connected.");
			}
			// Leave game
			game.leave(new Callback<Void>() {
				@Override
				public void onSuccess(Void result) {
				}

				@Override
				public void onFailure(Throwable t) {
					logToAll("Error when leaving: " + t.getMessage());
				}
			});
			// Terminate
			game.terminate();
			worldSimulator.terminate();
		} catch (Exception e) {
			logToAll("Error when leaving: " + e.getMessage());
		}
	}

	@Override
	public void setReady(final boolean isReady) {
		if (game == null) {
			logToAll("Error when readying: not connected.");
			return;
		}

		try {
			game.setReady(isReady, new Callback<Void>() {
				@Override
				public void onSuccess(Void result) {
				}

				@Override
				public void onFailure(Throwable t) {
					logToAll("Error when readying: " + t.getMessage());
				}
			});
		} catch (Exception e) {
			logToAll("Error when readying: " + e.getMessage());
		}
	}

	@Override
	public void stopGame() {
		if (game == null) {
			logToAll("Error when stopping: not connected.");
			return;
		}

		try {
			game.stop();
		} catch (IllegalStateException | IOException e) {
			logToAll("Error when stopping: " + e.getMessage());
		}
	}

	private boolean isReady() {
		// TODO cheating still possible
		if (getMainController().getPlayer().getRobot() == null) {
			return false;
		}

		/*
		 * TODO Do we really care about not having a maze configured? In the
		 * worst case scenario, the virtual robot will simply travel forever in
		 * an empty space.
		 */
		if (getMainController().getPlayer().getRobot() instanceof VirtualRobot
				&& getMainController().getWorld().getMaze().getNumberOfTiles() == 0) {
			return false;
		}

		return true;
	}

	private void onNotReady() {
		logToAll("Error when joining: not ready to join.");
		postState(GameSetUpEvent.EventType.NOT_READY);
	}

	private void postState(GameSetUpEvent.EventType eventType) {
		postEvent(new GameSetUpEvent(eventType));
	}

	private class Listener implements GameListener {

		@Override
		public void onGameJoined() {
			// Log
			logToLocal("Joined");
			postState(GameSetUpEvent.EventType.JOINED);
		}

		@Override
		public void onGameLeft() {
			// Log
			logToLocal("Left");
			postState(GameSetUpEvent.EventType.LEFT);
		}

		@Override
		public void onGameRolled(int playerNumber, int objectNumber) {
			logToLocal("Player number rolled: " + playerNumber);
			logToLocal("Object number rolled: " + objectNumber);
		}

		@Override
		public void onGameStarted() {
			logToAll("Game started");
		}

		@Override
		public void onGameStopped() {
			logToAll("Game stopped");
		}

		@Override
		public void onGameWon(int teamNumber) {
			logToAll("Game won by team #" + teamNumber);
		}

		@Override
		public void onPlayerReady(String playerID, boolean isReady) {
			logToWorld("Player " + playerID + (isReady ? " ready" : " not ready"));
		}

		@Override
		public void onObjectFound(String playerID) {
			logToWorld("Player " + playerID + " found their object");
		}

		@Override
		public void onPartnerConnected(Player partner) {
			logToLocal("Partner connected: " + partner.getPlayerID());
			getMainController().gameControl().addPlayer(partner);
		}

		@Override
		public void onPartnerDisconnected(Player partner) {
			logToLocal("Partner disconnected: " + partner.getPlayerID());
			getMainController().gameControl().removePlayer(partner);
		}

		@Override
		public void onMazesMerged() {
			logToLocal("Partner maze merged into own maze");
		}

	}

}
