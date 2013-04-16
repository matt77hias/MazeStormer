package mazestormer.explore;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import mazestormer.barcode.BarcodeMapping;
import mazestormer.maze.Orientation;
import mazestormer.maze.Tile;
import mazestormer.player.Player;

public class ExploreControlMode extends ControlMode {

	private final LinkedList<Tile> queue = new LinkedList<Tile>();

	public ExploreControlMode(Player player, BarcodeMapping mapping) {
		super(player, mapping);
	}

	@Override
	public void takeControl(Driver driver) {
		log("Exploring the maze");
	}

	@Override
	public void releaseControl(Driver driver) {
	}

	@Override
	public Tile nextTile(Tile currentTile) {
		// Create new paths to all neighbors
		selectTiles(currentTile);

		// Queue depleted
		if (queue.isEmpty()) {
			return null;
		}

		// Sort queue
		Collections.sort(queue, new ClosestTileComparator(currentTile));

		// Go to next tile
		return queue.pollFirst();
	}

	@Override
	public boolean isBarcodeActionEnabled() {
		return true;
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

	/**
	 * Compares tiles based on their Manhattan distance to a given reference
	 * tile.
	 */
	public class ClosestTileComparator implements Comparator<Tile> {

		private final Tile referenceTile;

		public ClosestTileComparator(Tile referenceTile) {
			this.referenceTile = referenceTile;
		}

		@Override
		public int compare(Tile left, Tile right) {
			int leftDistance = shortestPathLength(referenceTile, left);
			int rightDistance = shortestPathLength(referenceTile, right);
			return Integer.compare(leftDistance, rightDistance);
		}

		// TODO This won't work if there exist longer paths around a seesaw!!!
		public int shortestPathLength(Tile startTile, Tile endTile) {
			List<Tile> path = getPathFinder().findTilePath(startTile, endTile);
			for (Tile tile : path) {
				if (tile.getIgnoreFlag()) {
					return Integer.MAX_VALUE;
				}
			}
			return path.size();
		}

	}

}