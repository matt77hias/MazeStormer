package mazestormer.maze;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;

import mazestormer.util.LongPoint;

public class Tile {

	private final LongPoint position;
	private final EnumMap<Orientation, Edge> edges = new EnumMap<Orientation, Edge>(
			Orientation.class);

	public Tile(LongPoint position) {
		this.position = new LongPoint(position);
	}

	public long getX() {
		return (long) position.getX();
	}

	public long getY() {
		return (long) position.getY();
	}

	public LongPoint getPosition() {
		return new LongPoint(position);
	}

	public Collection<Edge> getEdges() {
		return Collections.unmodifiableCollection(edges.values());
	}

	public boolean hasEdge(Edge edge) {
		checkNotNull(edge);
		return edges.containsValue(edge);
	}

	public boolean hasEdgeAt(Orientation side) {
		checkNotNull(side);
		return edges.containsKey(side);
	}

	public Edge getEdgeAt(Orientation side) {
		checkNotNull(side);
		return edges.get(side);
	}

	public void addEdge(Edge edge) {
		checkNotNull(edge);
		edges.put(edge.getOrientationFrom(getPosition()), edge);
	}

	public void addEdges(Iterable<Edge> edges) {
		for (Edge edge : edges) {
			addEdge(edge);
		}
	}

	public EnumSet<Orientation> getClosedSides() {
		EnumSet<Orientation> result = EnumSet.noneOf(Orientation.class);
		for (Orientation orientation : Orientation.values()) {
			if (hasEdgeAt(orientation)) {
				result.add(orientation);
			}
		}
		return result;
	}

	public EnumSet<Orientation> getOpenSides() {
		return EnumSet.complementOf(getClosedSides());
	}
}
