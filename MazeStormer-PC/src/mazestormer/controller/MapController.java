package mazestormer.controller;

import java.awt.EventQueue;
import java.awt.Rectangle;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import lejos.robotics.navigation.Pose;
import mazestormer.connect.ConnectEvent;
import mazestormer.maze.Maze;
import mazestormer.maze.parser.FileUtils;
import mazestormer.maze.parser.Parser;
import mazestormer.robot.MoveEvent;
import mazestormer.ui.map.MapDocument;
import mazestormer.ui.map.MapLayer;
import mazestormer.ui.map.MazeLayer;
import mazestormer.ui.map.RangesLayer;
import mazestormer.ui.map.RobotLayer;
import mazestormer.ui.map.event.MapChangeEvent;
import mazestormer.ui.map.event.MapDOMChangeRequest;
import mazestormer.ui.map.event.MapLayerAddEvent;
import mazestormer.ui.map.event.MapRobotPoseChangeEvent;
import mazestormer.util.MapUtils;

import org.w3c.dom.svg.SVGDocument;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.Subscribe;

public class MapController extends SubController implements IMapController {

	private MapDocument map;
	private RobotLayer robotLayer;
	private MazeLayer mazeLayer;
	private MazeLayer loadedMazeLayer;
	private RangesLayer rangesLayer;

	private Timer updater;
	private long updateInterval;

	private static final long defaultUpdateInterval = 1000 / 25;

	public MapController(MainController mainController) {
		super(mainController);

		setUpdateInterval(defaultUpdateInterval);

		createMap();
		createLayers();
	}

	private void createMap() {
		map = new MapDocument();

		// TODO Make maze define the view rectangle
		map.setViewRect(new Rectangle(-500, -500, 1000, 1000));

		SVGDocument document = map.getDocument();
		postEvent(new MapChangeEvent(document));
	}

	private void createLayers() {
		robotLayer = new RobotLayer("Robot");
		addLayer(robotLayer);

		Maze loadedMaze = getMainController().getLoadedMaze();
		loadedMazeLayer = new MazeLayer("Loaded maze", loadedMaze);
		loadedMazeLayer.setZIndex(1);
		loadedMazeLayer.setOpacity(0.5f);
		addLayer(loadedMazeLayer);

		Maze maze = getMainController().getMaze();
		// TODO Remove hard-coded loading of example maze
		try {
			maze.setOrigin(new Pose(-20, -20, 0));
			String path = MapController.class.getResource("/res/ExampleMaze.txt").getPath();
			CharSequence contents = FileUtils.load(path);
			new Parser(maze).parse(contents);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// End remove
		mazeLayer = new MazeLayer("Discovered maze", maze);
		mazeLayer.setZIndex(2);
		addLayer(mazeLayer);

		rangesLayer = new RangesLayer("Detected ranges");
		addLayer(rangesLayer);
	}

	private void addLayer(MapLayer layer) {
		layer.registerEventBus(getEventBus());
		map.addLayer(layer);
		postEvent(new MapLayerAddEvent(layer));
	}

	@Override
	public SVGDocument getDocument() {
		return map.getDocument();
	}

	@Override
	public Set<MapLayer> getLayers() {
		return map.getLayers();
	}

	@Override
	public void setLayerVisible(MapLayer layer, boolean isVisible) {
		layer.setVisible(isVisible);
	}

	@Override
	public Pose getRobotPose() {
		return MapUtils.toMapCoordinates(getMainController().getPose());
	}

	private void updateRobotPose() {
		Pose pose = getRobotPose();

		if (robotLayer != null) {
			robotLayer.setPosition(pose.getLocation());
			robotLayer.setRotationAngle(pose.getHeading());
		}

		postEvent(new MapRobotPoseChangeEvent(pose));
	}

	private void invokeUpdateRobotPose() {
		// Invoke Swing methods in AWT thread
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				updateRobotPose();
			}
		});
	}

	private long getUpdateInterval() {
		return updateInterval;
	}

	public void setUpdateInterval(long interval) {
		updateInterval = Math.abs(interval);
	}

	public void setUpdateFPS(long fps) {
		setUpdateInterval((long) (1000f / (float) fps));
	}

	private void startUpdateTimer() {
		stopUpdateTimer();

		updater = new Timer();
		updater.scheduleAtFixedRate(new UpdateTimerTask(), 0, getUpdateInterval());
	}

	private void stopUpdateTimer() {
		if (updater != null) {
			updater.cancel();
			updater = null;
		}
	}

	@Subscribe
	public void updateRobotPoseOnConnect(ConnectEvent e) {
		if (e.isConnected()) {
			// Set initial pose
			invokeUpdateRobotPose();
		} else {
			// Stop updating pose
			stopUpdateTimer();
		}
	}

	@Subscribe
	public void updateRobotPoseOnMove(MoveEvent e) {
		if (e.getEventType() == MoveEvent.EventType.STARTED) {
			// Start updating while moving
			startUpdateTimer();
		} else {
			// Stop updating when move ended
			stopUpdateTimer();
		}
	}

	/**
	 * When no view is attached yet, DOM change requests may not be consumed by
	 * any listener and potentially get lost.
	 * 
	 * This event listener catches these dead requests and runs them directly.
	 */
	@Subscribe
	public void recoverDeadDOMChangeRequest(DeadEvent event) {
		if (event.getEvent() instanceof MapDOMChangeRequest) {
			((MapDOMChangeRequest) event.getEvent()).getRequest().run();
		}
	}

	private class UpdateTimerTask extends TimerTask {

		@Override
		public void run() {
			invokeUpdateRobotPose();
		}

	}

}
