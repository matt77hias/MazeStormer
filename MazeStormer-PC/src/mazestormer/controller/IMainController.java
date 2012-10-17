package mazestormer.controller;

import mazestormer.util.EventSource;

public interface IMainController {

	public IConfigurationController configuration();
	
	public IParametersController parameters();

	public IManualControlController manualControl();

	public IPolygonControlController polygonControl();
	
	public IMapController map();

	public void register(EventSource eventSource);

}