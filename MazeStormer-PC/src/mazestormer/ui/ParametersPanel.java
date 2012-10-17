package mazestormer.ui;

import java.awt.BorderLayout;
import java.beans.Beans;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import mazestormer.controller.IParametersController;
import mazestormer.controller.RobotParameterChangeEvent;
import net.miginfocom.swing.MigLayout;

import com.google.common.eventbus.Subscribe;
import javax.swing.UIManager;

public class ParametersPanel extends ViewPanel {

	private static final long serialVersionUID = 1L;

	private final IParametersController controller;

	private JPanel container;

	private SpinnerNumberModel travelSpeedModel;
	private SpinnerNumberModel rotateSpeedModel;

	/**
	 * Create the panel.
	 */
	public ParametersPanel(IParametersController controller) {
		this.controller = controller;

		setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Parameters", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		setLayout(new BorderLayout(0, 0));

		container = new JPanel();
		container.setLayout(new MigLayout("", "[grow 75][grow 25][right]",
				"[][]"));
		add(container, BorderLayout.NORTH);

		createTravelSpeed();
		createRotateSpeed();

		if (!Beans.isDesignTime())
			registerController();
	}

	private void registerController() {
		registerEventBus(controller.getEventBus());

		travelSpeedModel.setMaximum(controller.getMaxTravelSpeed());
		travelSpeedModel.setValue(controller.getTravelSpeed());

		rotateSpeedModel.setMaximum(controller.getMaxRotateSpeed());
		rotateSpeedModel.setValue(controller.getRotateSpeed());
	}

	private void createTravelSpeed() {
		JLabel lblTravelSpeed = new JLabel("Travel speed");
		container.add(lblTravelSpeed, "cell 0 0,grow");

		travelSpeedModel = new SpinnerNumberModel(new Double(0), new Double(0),
				null, new Double(1));

		travelSpeedModel.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				controller.setTravelSpeed((double) travelSpeedModel.getValue());
			}
		});

		JSpinner spinTravelSpeed = new JSpinner();
		spinTravelSpeed.setModel(travelSpeedModel);
		container.add(spinTravelSpeed, "cell 1 0,grow");

		JLabel lblUnit = new JLabel("cm/sec");
		container.add(lblUnit, "cell 2 0,grow");
	}

	private void createRotateSpeed() {
		JLabel lblRotateSpeed = new JLabel("Rotate speed");
		container.add(lblRotateSpeed, "cell 0 1,grow");

		rotateSpeedModel = new SpinnerNumberModel(new Double(0), new Double(0),
				null, new Double(1));

		rotateSpeedModel.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				controller.setRotateSpeed((double) rotateSpeedModel.getValue());
			}
		});

		JSpinner spinRotateSpeed = new JSpinner();
		spinRotateSpeed.setModel(rotateSpeedModel);
		container.add(spinRotateSpeed, "cell 1 1,grow");

		JLabel lblUnit = new JLabel("degrees/sec");
		container.add(lblUnit, "cell 2 1,grow");
	}

	@Subscribe
	public void onParameterChanged(RobotParameterChangeEvent e) {
		switch (e.getParameter()) {
		case "travelSpeed":
			travelSpeedModel.setValue(e.getValue());
			break;
		case "rotateSpeed":
			rotateSpeedModel.setValue(e.getValue());
			break;
		}
	}
}