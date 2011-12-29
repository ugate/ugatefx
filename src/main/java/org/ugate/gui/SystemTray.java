package org.ugate.gui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.FadeTransitionBuilder;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import org.apache.log4j.Logger;
import org.ugate.IGateKeeperListener;
import org.ugate.UGateKeeper;
import org.ugate.UGateKeeperEvent;
import org.ugate.resources.RS;

/**
 * System tray that uses AWT until JavaFX 3.0 is released 
 * {@linkplain http://javafx-jira.kenai.com/browse/RT-17503}.
 */
public class SystemTray {

	private static final Logger log = Logger.getLogger(SystemTray.class);
	public static final double NOTIFY_WINDOW_WIDTH = 350d;
	public static final double NOTIFY_WINDOW_HEIGHT = 100d;
	public static final double NOTIFY_WINDOW_CLOSE_DELAY = 4d;
	private static volatile Stage stage;
	private static volatile Stage dummyPopup;
	private static volatile Stage notifyPopup;
	private static volatile FadeTransition notifyFadeTrans;
	//private static final Semaphore waitForFX = new Semaphore(-1, true);
	
	/**
	 * Private utility constructor
	 */
	private SystemTray() {
	}
	
	/**
	 * Creates a system tray icon that when clicked will restore the specified stage.
	 * When the stage is {@linkplain Stage#setIconified(boolean)} is set to <code>true</code>
	 * the stage is hidden/closed. When it is set to <code>false</code> the stage will be restored.
	 * 
	 * @param stage the stage that will be controlled by the system tray
	 * @return true when the creation is successful
	 */
	public static boolean createSystemTray(final Stage stage) {
		if (!isSystemTraySupported()) {
			return false;
		}
		if (stage == null) {
			throw new NullPointerException("Stage cannot be null and must not be showing");
		}
		if (SystemTray.stage != null) {
			throw new IllegalStateException(SystemTray.class.getName() + " can only be created once");
		}
		if (!Platform.isFxApplicationThread()) {
			throw new IllegalStateException(SystemTray.class.getName() + 
					" can only be create within the JavaFX application thread");
		}
		final java.awt.SystemTray st = isSystemTraySupported() ? java.awt.SystemTray.getSystemTray() : null;
		if (st != null && st.getTrayIcons().length == 0) {
			// listen for minimize changes and handle minimize/restore functions from the system tray
			SystemTray.stage = stage;
			// translate primary stage min/max to system tray
			SystemTray.stage.iconifiedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> paramObservableValue, Boolean oldValue, Boolean newValue) {
					if (newValue) {
						minimizeToSystemTray();
					} else {
						restoreFromSystemTray();
					}
				}
			});
			// when the primary stage is minimized to the system tray show a notification for the event message
			UGateKeeper.DEFAULT.addListener(new IGateKeeperListener() {
				@Override
				public void handle(final UGateKeeperEvent<?> event) {
					if (!SystemTray.stage.isShowing()) {
						final String msg = event.getMessageString();
						if (msg != null && !msg.isEmpty()) {
							if (Platform.isFxApplicationThread()) {
								showNotification(msg);
							} else {
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										showNotification(msg);
									}
								});
							}
						}
					}
				}
			});
			final String imageName = st.getTrayIconSize().width > 16 ? 
					st.getTrayIconSize().width > 64 ? RS.IMG_LOGO_128 : RS.IMG_LOGO_64 : RS.IMG_LOGO_16;
			try {
				final java.awt.Image image = javax.imageio.ImageIO.read(RS.stream(imageName));
				final java.awt.TrayIcon trayIcon = new java.awt.TrayIcon(image);
				//UGateKeeper.DEFAULT.
				trayIcon.setToolTip(RS.rbLabel("win.systray.tooltip"));
				st.add(trayIcon);
				trayIcon.addMouseListener(new java.awt.event.MouseListener() {
					@Override
					public void mouseReleased(java.awt.event.MouseEvent e) {
					}
					@Override
					public void mousePressed(java.awt.event.MouseEvent e) {
					}
					@Override
					public void mouseExited(java.awt.event.MouseEvent e) {
					}
					@Override
					public void mouseEntered(java.awt.event.MouseEvent e) {
					}
					@Override
					public void mouseClicked(java.awt.event.MouseEvent e) {
						Platform.runLater(new Runnable() {
							@Override
							public void run() {
								restoreFromSystemTray();
							}
						});
					}
				});
				Runtime.getRuntime().addShutdownHook(new Thread() {
					@Override
					public void run() {
						exit();
					}
				});
				return true;
			} catch (final java.io.IOException e) {
				log.error("Unable to add system tray icons", e);
			} catch (java.awt.AWTException e) {
				log.error("Unable to add system tray icons", e);
			}
		}
		return false;
	}
	
	/**
	 * Shows a notification window displaying the specified message. The window will be closed after a short duration, 
	 * when focus is lost on the window, or when the window is clicked.
	 * 
	 * @param message the message to display
	 */
	public static void showNotification(final String message) {
		showNotification(message, null);
	}
	
	/**
	 * Shows a notification window displaying the specified message. The window will be closed after a short duration, 
	 * when focus is lost on the window, or when the window is clicked (unless consumed by the passed mouse event handler).
	 * 
	 * @param message the message to display
	 * @param mouseEventHandler a mouse event handler for the message
	 */
	public static void showNotification(final String message, final EventHandler<MouseEvent> mouseEventHandler) {
		if (notifyPopup == null) {
	        notifyPopup = new Stage(StageStyle.TRANSPARENT);
	        notifyPopup.initOwner(dummyPopup);
	        notifyPopup.initModality(Modality.WINDOW_MODAL);
//	        notifyPopup.focusedProperty().addListener(new ChangeListener<Boolean>() {
//				@Override
//				public void changed(final ObservableValue<? extends Boolean> observable, 
//						final Boolean oldValue, final Boolean newValue) {
//					if (!newValue) {
//						closeNotification();
//					}
//				}
//			});
	        final Button btn = new Button(message);
	        btn.setPrefSize(NOTIFY_WINDOW_WIDTH, NOTIFY_WINDOW_HEIGHT);
	        btn.setWrapText(true);
	        if (mouseEventHandler != null) {
	        	btn.addEventHandler(MouseEvent.ANY, mouseEventHandler);
	        }
        	btn.addEventHandler(MouseEvent.ANY, new EventHandler<MouseEvent>() {
				@Override
				public void handle(final MouseEvent event) {
					if (event.getEventType() == MouseEvent.MOUSE_CLICKED) {
						closeNotification();
					} else if (event.getEventType() == MouseEvent.MOUSE_ENTERED) {
						if (notifyFadeTrans != null) {
							notifyFadeTrans.pause();
						}
					} else if (event.getEventType() == MouseEvent.MOUSE_EXITED) {
						if (notifyFadeTrans != null && notifyFadeTrans.getStatus() == Animation.Status.PAUSED) {
							notifyFadeTrans.play();
						}
					}
				}
			});
        	final VBox root = new VBox(0);
        	VBox.setMargin(btn, Insets.EMPTY);
        	root.getChildren().add(btn);
	        notifyPopup.setScene(new Scene(root, NOTIFY_WINDOW_WIDTH, NOTIFY_WINDOW_HEIGHT, Color.TRANSPARENT));
	        notifyPopup.getScene().getStylesheets().add(RS.path(RS.CSS_MAIN));
	        positionNotification();
	        notifyPopup.show();
	        // TODO : set notification to full screen so that toFront() will work
	        notifyPopup.setFullScreen(true);
	        notifyPopup.toFront();
	        notifyPopup.setFullScreen(false);
	        
	        log.debug("Showing notification window with message: " + message);
		} else {
			positionNotification();
			//((Button) notifyPopup.getScene().getRoot()).setText(message);
			if (!notifyPopup.isShowing()) {
				notifyPopup.show();
				notifyPopup.toFront();
			}
		}
		if (notifyFadeTrans != null) {
			notifyFadeTrans.stop();
		} else {
			notifyFadeTrans = FadeTransitionBuilder.create().cycleCount(1).delay(Duration.seconds(
					NOTIFY_WINDOW_CLOSE_DELAY)).node(notifyPopup.getScene().getRoot()).fromValue(
							1d).toValue(0).build();
	        notifyFadeTrans.setOnFinished(new EventHandler<ActionEvent>() {
				@Override
				public void handle(final ActionEvent event) {
					closeNotification();
				}
			});
		}
		notifyFadeTrans.playFromStart();
	}
	
	/**
	 * Positions the notification window
	 */
	private static void positionNotification() {
        final Screen screen = Screen.getPrimary();
        final Rectangle2D bounds = screen.getVisualBounds();
        notifyPopup.setX(bounds.getMaxX() - NOTIFY_WINDOW_WIDTH - 10d);
        notifyPopup.setY(bounds.getMaxY() - NOTIFY_WINDOW_HEIGHT - 10d);
	}
	
	/**
	 * Stops the notification animation and closes the notification window
	 */
	private static void closeNotification() {
		if (!Platform.isFxApplicationThread()) {
			return;
		}
		if (notifyFadeTrans != null) {
			log.debug("Stoping notification window animation");
			notifyFadeTrans.stop();
			notifyFadeTrans = null;
		}
		if (notifyPopup != null) {
			log.debug("Closing notification window");
			notifyPopup.close();
			notifyPopup = null;
		}
	}
	
	/**
	 * Minimizes/Hides the primary stage and shows
	 */
	public static void minimizeToSystemTray() {
		if (!isSystemTraySupported() || !stage.isShowing()) {
			return;
		}
		log.debug("Minimizing application to system tray");
		if (dummyPopup == null) {
			// javafx.stage.Popup does not work
			dummyPopup = new Stage();
	        final Screen screen = Screen.getPrimary();
	        final Rectangle2D bounds = screen.getVisualBounds();
			dummyPopup.initModality(Modality.NONE);
			dummyPopup.initStyle(StageStyle.UTILITY);
			dummyPopup.setOpacity(0d);
			final Group root = new Group();
			root.getChildren().add(new Text("Close"));
			dummyPopup.setScene(new Scene(root, NOTIFY_WINDOW_WIDTH, NOTIFY_WINDOW_HEIGHT, Color.TRANSPARENT));
	        dummyPopup.setX(bounds.getMaxX());
	        dummyPopup.setY(bounds.getMaxY());
			dummyPopup.show();
			
			if (isSystemTraySupported()) {
				showNotification(RS.rbLabel("win.systray.minimize.info"));
			}
		}
		stage.close();
	}
	
	/**
	 * Restores the primary stage and removes the system tray windows (if any)
	 */
	public static void restoreFromSystemTray() {
		if (!isSystemTraySupported() || stage.isShowing()) {
			return;
		}
		stage.show();
		closeNotification();
		closeHidden();
	}
	
	/**
	 * Closes the hidden window that prevents the application from closing while the primary stage is closed
	 */
	private static void closeHidden() {
		if (dummyPopup != null) {
			dummyPopup.close();
			dummyPopup = null;
		}
	}
	
	/**
	 * Exits the system tray and performs any cleanup operations
	 */
	public static void exit() {
		final java.awt.SystemTray st = isSystemTraySupported() ? java.awt.SystemTray.getSystemTray() : null;
		if (st != null && st.getTrayIcons().length > 0) {
			log.debug("Removing system tray icon(s)...");
			for (java.awt.TrayIcon trayIcon : st.getTrayIcons()) {
				try {
					st.remove(trayIcon);
				} catch (final Throwable t) {
					log.warn("Unable to remove system tray icon", t);
				}
			}
		}
		closeNotification();
		closeHidden();
		// TODO : shutdown AWT
	}
	
	/**
	 * @return true when the underlying OS supports the system tray
	 */
	public static boolean isSystemTraySupported() {
		return java.awt.SystemTray.isSupported();
	}
}