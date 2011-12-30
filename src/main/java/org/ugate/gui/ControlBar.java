package org.ugate.gui;

import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import org.apache.log4j.Logger;
import org.ugate.Command;
import org.ugate.IGateKeeperListener;
import org.ugate.Settings;
import org.ugate.UGateKeeper;
import org.ugate.UGateKeeperEvent;
import org.ugate.UGateUtil;
import org.ugate.gui.components.Digits;
import org.ugate.gui.components.UGateToggleSwitchPreferenceView;
import org.ugate.resources.RS;
import org.ugate.wireless.data.SensorReadings;

/**
 * Main menu control bar
 */
public class ControlBar extends ToolBar {
	
	private final ScrollPane helpTextPane;
	private final Label helpText;
	
	private static final Logger log = Logger.getLogger(ControlBar.class);
	public static final int HELP_TEXT_COLOR_CHANGE_CYCLE_COUNT = 8;
	public static final double CHILD_SPACING = 10d;
	public static final double CHILD_PADDING = 5d;
	public static final Insets PADDING_INSETS = new Insets(CHILD_PADDING, CHILD_PADDING, 0, CHILD_PADDING);

	private final Stage stage;
	private int remoteNodeIndex = 1;
	private boolean propagateSettingsToAllRemoteNodes = false;

	public ControlBar(final Stage stage) {
		this.stage = stage;
		// help view
		final DropShadow helpTextDropShadow = new DropShadow();
		final Timeline helpTextTimeline = GuiUtil.createDropShadowColorIndicatorTimline(
				helpTextDropShadow, Color.RED, Color.BLACK, HELP_TEXT_COLOR_CHANGE_CYCLE_COUNT);
		helpTextPane = new ScrollPane();
		helpTextPane.setStyle("-fx-background-color: #ffffff;");
		helpTextPane.setPrefHeight(40d);
		helpTextPane.setPrefWidth(200d);
		helpTextPane.setEffect(helpTextDropShadow);
		helpText = new Label(RS.rbLabel(UGateUtil.HELP_TEXT_DEFAULT_KEY));
		helpText.setWrapText(true);
		helpText.setPrefWidth(helpTextPane.getPrefWidth() - 35d);
		helpText.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, 
					String newValue) {
				helpTextTimeline.stop();
				if (newValue != null && newValue.length() > 0 && 
						!newValue.equals(RS.rbLabel(UGateUtil.HELP_TEXT_DEFAULT_KEY))) {
					helpTextTimeline.play();
				}
			}
		});
		helpTextPane.setContent(helpText);
		
		final DropShadow ds = new DropShadow();
		final ImageView camTakeQvga = RS.imgView(RS.IMG_CAM_QVGA);
		addHelpTextTrigger(camTakeQvga, RS.rbLabel("cam.take.qvga"));
		camTakeQvga.setCursor(Cursor.HAND);
		camTakeQvga.setEffect(ds);
		final ImageView camTakeVga = RS.imgView(RS.IMG_CAM_VGA);
		addHelpTextTrigger(camTakeVga, RS.rbLabel("cam.take.vga"));
		camTakeVga.setCursor(Cursor.HAND);
		camTakeVga.setEffect(ds);
		final ImageView settingsSet = RS.imgView(RS.IMG_SETTINGS_SET);
		settingsSet.setCursor(Cursor.HAND);
		settingsSet.addEventHandler(MouseEvent.MOUSE_PRESSED, new EventHandler<MouseEvent>(){
			@Override
			public void handle(final MouseEvent event) {
				if (GuiUtil.isPrimaryPress(event)) {
					createCommandService(Command.SENSOR_SET_SETTINGS, true);
				}
			}
	    });
		addHelpTextTrigger(settingsSet, RS.rbLabel("settings.send"));
		final DropShadow settingsDS = new DropShadow();
		settingsSet.setEffect(settingsDS);
		final ImageView readingsGet = RS.imgView(RS.IMG_READINGS_GET);
		addHelpTextTrigger(readingsGet, RS.rbLabel("sensors.readings.get"));
		readingsGet.setCursor(Cursor.HAND);
		readingsGet.addEventHandler(MouseEvent.MOUSE_PRESSED, new EventHandler<MouseEvent>(){
			@Override
			public void handle(final MouseEvent event) {
				if (GuiUtil.isPrimaryPress(event)) {
					createCommandService(Command.SENSOR_GET_READINGS, true);
				}
			}
	    });
		
		// add the readings view
		final ImageView sonarReadingLabel = RS.imgView(RS.IMG_SONAR);
		final Digits sonarReading = new Digits(String.format(SensorControl.FORMAT_SONAR, 0.0f),
				0.15f, SensorControl.COLOR_SONAR, null);
		final ImageView pirReadingLabel = RS.imgView(RS.IMG_PIR);
		final Digits pirReading = new Digits(String.format(SensorControl.FORMAT_PIR, 0.0f), 
				0.15f, SensorControl.COLOR_PIR, null);
		final ImageView mwReadingLabel = RS.imgView(RS.IMG_MICROWAVE);
		final Digits mwReading = new Digits(String.format(SensorControl.FORMAT_MW, 0), 0.15f, 
				SensorControl.COLOR_MW, null);
		final Group readingsGroup = GuiUtil.createBackgroundDisplay(PADDING_INSETS, CHILD_SPACING, 10,
				sonarReadingLabel, sonarReading, pirReadingLabel, pirReading, mwReadingLabel, mwReading);
		addHelpTextTrigger(readingsGroup, "Current sensors readings display");
		
		
		// add the multi-alarm trip state
		final UGateToggleSwitchPreferenceView multiAlarmToggleSwitch = new UGateToggleSwitchPreferenceView(
				Settings.MULTI_ALARM_TRIP_STATE,
				new UGateToggleSwitchPreferenceView.ToggleItem(RS.IMG_SONAR_ALARM_ON, RS.IMG_SONAR_ALARM_OFF, false),
				new UGateToggleSwitchPreferenceView.ToggleItem(RS.IMG_IR_ALARM_ON, RS.IMG_IR_ALARM_OFF, false),
				new UGateToggleSwitchPreferenceView.ToggleItem(RS.IMG_MICROWAVE_ALARM_ON, RS.IMG_MICROWAVE_ALARM_OFF, false));
		final Group multiAlarmGroup = GuiUtil.createBackgroundDisplay(PADDING_INSETS, CHILD_SPACING, 0,
				multiAlarmToggleSwitch);
		addHelpTextTrigger(multiAlarmGroup, RS.rbLabel("sensors.trip.multi"));
		
		// add the menu items
		getItems().addAll(camTakeQvga, camTakeVga, settingsSet, readingsGet, 
				new Separator(Orientation.VERTICAL), readingsGroup, 
				new Separator(Orientation.VERTICAL), multiAlarmGroup,
				new Separator(Orientation.VERTICAL), helpTextPane);
		
		final Timeline settingsSetTimeline = GuiUtil.createDropShadowColorIndicatorTimline(
				settingsDS, Color.RED, Color.BLACK, Timeline.INDEFINITE);
		// show a visual indication that the settings need updated
		UGateKeeper.DEFAULT.addListener(new IGateKeeperListener() {
			@Override
			public void handle(final UGateKeeperEvent<?> event) {
				setHelpText(event.getMessageString());
				if (event.getType() == UGateKeeperEvent.Type.SETTINGS_SAVE_LOCAL) {
					if (event.getKey() != null && event.getKey().canRemote) {
						settingsSetTimeline.play();
					}
				} else if (event.getType() == UGateKeeperEvent.Type.WIRELESS_DATA_ALL_TX_SUCCESS) {
					settingsSetTimeline.stop();
				} else if (event.getType() == UGateKeeperEvent.Type.WIRELESS_DATA_RX_SUCCESS) {
					if (event.getNewValue() instanceof SensorReadings) {
						final SensorReadings sr = (SensorReadings) event.getNewValue();
						sonarReading.setValue(String.format(SensorControl.FORMAT_SONAR, 
								Double.parseDouble(sr.getSonarFeet() + "." + sr.getSonarInches())));
						pirReading.setValue(String.format(SensorControl.FORMAT_PIR, 
								Double.parseDouble(sr.getIrFeet() + "." + sr.getIrInches())));
						mwReading.setValue(String.format(SensorControl.FORMAT_MW, 
								Math.round(sr.getSpeedMPH())));
					}
				}
			}
		});
		sonarReading.setValue(String.format(SensorControl.FORMAT_SONAR, 5.3f));
		pirReading.setValue(String.format(SensorControl.FORMAT_PIR, 3.7f));
		mwReading.setValue(String.format(SensorControl.FORMAT_MW, 24L));
	}
	
	/**
	 * Creates a service for the command that will show a progress indicator preventing
	 * further action until the command execution has completed.
	 * 
	 * @param command the command
	 * @param start true to start the service immediately after creating the service
	 * @return the service
	 */
	public Service<Boolean> createCommandService(final Command command, final boolean start) {
		if (!UGateKeeper.DEFAULT.wirelessIsConnected()) {
			setHelpText(RS.rbLabel("service.wireless.connection.required"));
			return null;
		}
		setHelpText(null);
		final Service<Boolean> service = GuiUtil.alertProgress(stage, new Task<Boolean>() {
			@Override
			protected Boolean call() throws Exception {
				try {
					if (command == Command.SENSOR_GET_READINGS) {
						if (!UGateKeeper.DEFAULT.wirelessSendData(getRemoteNodeIndex(), 
								Command.SENSOR_GET_READINGS)) {
							setHelpText(RS.rbLabel("sensors.readings.failed",
									(isPropagateSettingsToAllRemoteNodes() ? RS.rbLabel("all") : 
										getRemoteNodeAddress())));
							return false;
						}
					} else if (command == Command.SENSOR_SET_SETTINGS) {
						if (!UGateKeeper.DEFAULT.wirelessSendSettings(
								(isPropagateSettingsToAllRemoteNodes() ? null : getRemoteNodeIndex()))) {
							setHelpText(RS.rbLabel("settings.send.failed",
									(isPropagateSettingsToAllRemoteNodes() ? RS.rbLabel("all") : 
										getRemoteNodeAddress())));
							return false;
						}
					} else if (command == Command.GATE_TOGGLE_OPEN_CLOSE) {
						if (!UGateKeeper.DEFAULT.wirelessSendData(getRemoteNodeIndex(), 
								Command.GATE_TOGGLE_OPEN_CLOSE)) {
							setHelpText(RS.rbLabel("gate.toggle.failed",
									(isPropagateSettingsToAllRemoteNodes() ? RS.rbLabel("all") : 
										getRemoteNodeAddress())));
							return false;
						}
					} else {
						log.warn(String.format("%1$s is not a valid command for %2$s", 
								command, Controls.class.getName()));
						return false;
					}
				} catch (final Throwable t) {
					setHelpText(RS.rbLabel("service.command.failed"));
					log.error("Unable to execute " + command, t);
					return false;
				}
				return true;
			}
		});
		if (start) {
			service.start();
		}
		return service;
	}
	
	/**
	 * Creates a wireless connection service that will show a progress indicator preventing
	 * further action until the wireless connection has been established.
	 * 
	 * @param comPort the COM port to connect to
	 * @param baudRate the baud rate to connect at
	 * @return the service
	 */
	public Service<Boolean> createWirelessConnectionService(final String comPort, final int baudRate) {
		setHelpText(null);
		return GuiUtil.alertProgress(stage, new Task<Boolean>() {
			@Override
			protected Boolean call() throws Exception {
				try {
					// establish wireless connection (blocking)
					UGateKeeper.DEFAULT.wirelessConnect(comPort, baudRate);
				} catch (final Throwable t) {
					setHelpText(RS.rbLabel("service.wireless.failed"));
					log.error("Unable to establish a wireless connection", t);
				}
				return false;
			}
		});
	}
	
	/**
	 * Creates an email connection service that will show a progress indicator preventing
	 * further action until the email connection has been established.
	 * 
	 * @param comPort the COM port to connect to
	 * @param baudRate the baud rate to connect at
	 * @return the service
	 */
	public Service<Boolean> createEmailConnectionService(final String smtpHost, final String smtpPort, final String imapHost, 
			final String imapPort, final String username, final String password, 
			final String mainFolderName) {
		setHelpText(null);
		return GuiUtil.alertProgress(stage, new Task<Boolean>() {
			@Override
			protected Boolean call() throws Exception {
				try {
					// establish wireless connection (blocking)
					UGateKeeper.DEFAULT.emailConnect(smtpHost, smtpPort, imapHost, imapPort, username, password, mainFolderName);
				} catch (final Throwable t) {
					setHelpText(RS.rbLabel("service.email.failed"));
					log.error("Unable to establish a wireless connection", t);
				}
				return false;
			}
		});
	}
	
	/**
	 * Adds the help text when the node is right clicked
	 * 
	 * @param node the node to trigger the text
	 * @param text the text to show
	 */
	public void addHelpTextTrigger(final Node node, final String text) {
		node.setOnMousePressed(new EventHandler<MouseEvent>() {
			@Override
			public void handle(final MouseEvent event) {
				if (event.isSecondaryButtonDown()) {
					helpTextPane.setVvalue(helpTextPane.getVmin());
					helpText.setText(text);
					event.consume();
				}
			}
		});
	}
	
	/**
	 * @return the current help text
	 */
	public String getHelpText() {
		return helpText.getText();
	}
	
	/**
	 * Sets the help text
	 * 
	 * @param text the help text to set
	 */
	public void setHelpText(final String text) {
		helpText.setText(text == null || text.length() == 0 ? RS.rbLabel(UGateUtil.HELP_TEXT_DEFAULT_KEY) : text);
	}
	
	/**
	 * @return the remote node address for the current {@linkplain #getRemoteNodeIndex()}
	 */
	public String getRemoteNodeAddress() {
		return UGateKeeper.DEFAULT.wirelessGetAddress(getRemoteNodeIndex());
	}

	/**
	 * @return the remote index of the device node for which the controls represent
	 */
	public int getRemoteNodeIndex() {
		return this.remoteNodeIndex;
	}

	/**
	 * Sets the remote index of the device node for which the controls represent
	 * 
	 * @param remoteNodeIndex the remote node index
	 */
	public void setRemoteNodeIndex(final int remoteNodeIndex) {
		this.remoteNodeIndex = remoteNodeIndex;
	}

	/**
	 * @return true when settings updates should be propagated to all the remote nodes when
	 * 		the user chooses to save the settings
	 */
	public boolean isPropagateSettingsToAllRemoteNodes() {
		return propagateSettingsToAllRemoteNodes;
	}

	/**
	 * @param propagateSettingsToAllRemoteNodes true when settings updates should be propagated 
	 * 		to all the remote nodes when the user chooses to save the settings
	 */
	public void setPropagateSettingsToAllRemoteNodes(final boolean propagateSettingsToAllRemoteNodes) {
		this.propagateSettingsToAllRemoteNodes = propagateSettingsToAllRemoteNodes;
	}
}
