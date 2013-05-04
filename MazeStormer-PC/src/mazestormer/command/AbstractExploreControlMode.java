package mazestormer.command;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import mazestormer.maze.IMaze;
import mazestormer.maze.Orientation;
import mazestormer.maze.PathFinder;
import mazestormer.maze.Tile;
import mazestormer.player.Player;

public abstract class AbstractExploreControlMode extends ControlMode {

	/*
	 * Data
	 */

	private final LinkedList<Tile> queue = new LinkedList<Tile>();

	/*
	 * Constructor
	 */

	public AbstractExploreControlMode(Player player, Commander commander) {
		super(player, commander);
	}

	/*
	 * ControlMode management
	 */

	@Override
	public void takeControl() {
		log("Exploring the maze");
	}

	@Override
	public void releaseControl() {
	}

	/*
	 * Driver support
	 */

	@Override
	public Tile nextTile(Tile currentTile) {
		// Create new paths to all neighbors
		selectTiles(currentTile);

		// Queue depleted
		if (queue.isEmpty()) {
			return null;
		}

		// Sort queue
		Collections.sort(queue, new ClosestTileComparator(currentTile, getMaze()));

		// Find the next unexplored tile
		Tile nextTile = queue.peekFirst();
		while (nextTile.isExplored()) {
			// Remove explored tile
			queue.pollFirst();
			// Try the next tile
			nextTile = queue.peekFirst();
			if (nextTile == null) {
				// Queue depleted
				return null;
			}
		}

		// Go to next tile
		return nextTile;
	}

	@Override
	public boolean isBarcodeActionEnabled() {
		return true;
	}

	/*
	 * Utilities
	 */

	public boolean hasUnexploredTiles() {
		while (!queue.isEmpty()) {
			if (!queue.peekFirst().isExplored())
				return true;
			else
				queue.pollFirst();
		}
		return false;
	}

	/**
	 * Add tiles to the queue if the edge in its direction is open and it is not
	 * explored yet.
	 */
	private void selectTiles(Tile tile) {
		for (Orientation direction : tile.getOpenSides()) {
			Tile neighborTile = getMaze().getOrCreateNeighbor(tile, direction);
			// Reject the new paths with loops
			if (!neighborTile.isExplored() && !queue.contains(neighborTile)) {
				// Add the new paths to front of queue
				queue.addFirst(neighborTile);
			}
		}
	}

	protected void skipCurrentBarcode(boolean skip) {
		getCommander().getDriver().skipCurrentBarcode(skip);
	}

	protected void skipToNextTile() {
		getCommander().getDriver().skipToNextTile();
	}

	/**
	 * Compares tiles based on their shortest path distance to a given reference
	 * tile.
	 */
	public static class ClosestTileComparator implements Comparator<Tile> {

		private final Tile referenceTile;
		private IMaze maze;

		public ClosestTileComparator(Tile referenceTile, IMaze iMaze) {
			this.referenceTile = referenceTile;
			this.maze = iMaze;
		}

		@Override
		public int compare(Tile left, Tile right) {
			int leftDistance = shortestPathLength(referenceTile, left);
			int rightDistance = shortestPathLength(referenceTile, right);
			return Integer.compare(leftDistance, rightDistance);
		}

		public int shortestPathLength(Tile startTile, Tile endTile) {
			List<Tile> path = new PathFinder(maze).findTilePathWithoutSeesaws(startTile, endTile);
			return path.size();
		}

	}

}