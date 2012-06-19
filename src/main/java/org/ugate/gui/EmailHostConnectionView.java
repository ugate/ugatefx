package org.ugate.gui;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ugate.IGateKeeperListener;
import org.ugate.UGateKeeper;
import org.ugate.UGateKeeperEvent;
import org.ugate.gui.components.UGateCtrlView;
import org.ugate.resources.RS;
import org.ugate.service.ActorType;
import org.ugate.service.ServiceManager;
import org.ugate.service.entity.jpa.Actor;

/**
 * Responsible for connecting to the mail service
 */
public class EmailHostConnectionView extends StatusView {

	private static final Logger log = LoggerFactory
			.getLogger(EmailHostConnectionView.class);
	public final UGateCtrlView<Actor> smtpHost;
	public final UGateCtrlView<Actor> smtpPort;
	public final UGateCtrlView<Actor> imapHost;
	public final UGateCtrlView<Actor> imapPort;
	public final UGateCtrlView<Actor> username;
	public final UGateCtrlView<Actor> password;
	public final UGateCtrlView<Actor> inboxFolder;
	public final UGateCtrlView<Actor> recipients;
	public final Button connect;

	public EmailHostConnectionView(final ControlBar controlBar) {
		super(controlBar, false, 20);
		final ImageView icon = RS.imgView(RS.IMG_EMAIL_ICON);
		smtpHost = new UGateCtrlView<Actor>(controlBar.getActorPA(),
				ActorType.MAIL_SMTP_HOST, UGateCtrlView.Type.TYPE_TEXT,
				RS.rbLabel("mail.smtp.host"), null);
		controlBar.addHelpTextTrigger(smtpHost,
				RS.rbLabel("mail.smtp.host.desc"));
		smtpPort = new UGateCtrlView<Actor>(controlBar.getActorPA(),
				ActorType.MAIL_SMTP_PORT, UGateCtrlView.Type.TYPE_TEXT,
				RS.rbLabel("mail.smtp.port"), null);
		controlBar.addHelpTextTrigger(smtpPort,
				RS.rbLabel("mail.smtp.port.desc"));
		imapHost = new UGateCtrlView<Actor>(controlBar.getActorPA(),
				ActorType.MAIL_IMAP_HOST, UGateCtrlView.Type.TYPE_TEXT,
				RS.rbLabel("mail.imap.host"), null);
		controlBar.addHelpTextTrigger(imapHost,
				RS.rbLabel("mail.imap.host.desc"));
		imapPort = new UGateCtrlView<Actor>(controlBar.getActorPA(),
				ActorType.MAIL_IMAP_PORT, UGateCtrlView.Type.TYPE_TEXT,
				RS.rbLabel("mail.imap.port"), null);
		controlBar.addHelpTextTrigger(imapPort,
				RS.rbLabel("mail.imap.port.desc"));
		username = new UGateCtrlView<Actor>(controlBar.getActorPA(),
				ActorType.MAIL_USERNAME, UGateCtrlView.Type.TYPE_TEXT,
				RS.rbLabel("mail.username"), null);
		controlBar.addHelpTextTrigger(username,
				RS.rbLabel("mail.username.desc"));
		password = new UGateCtrlView<Actor>(controlBar.getActorPA(),
				ActorType.MAIL_PASSWORD, UGateCtrlView.Type.TYPE_PASSWORD,
				RS.rbLabel("mail.password"), null);
		controlBar.addHelpTextTrigger(password,
				RS.rbLabel("mail.password.desc"));
		inboxFolder = new UGateCtrlView<Actor>(controlBar.getActorPA(),
				ActorType.MAIL_INBOX_NAME, UGateCtrlView.Type.TYPE_TEXT,
				RS.rbLabel("mail.folder"), null);
		controlBar.addHelpTextTrigger(inboxFolder,
				RS.rbLabel("mail.folder.desc"));
		recipients = new UGateCtrlView<Actor>(controlBar.getActorPA(),
				ActorType.MAIL_RECIPIENTS, UGateCtrlView.Type.TYPE_TEXT_AREA,
				RS.rbLabel("mail.alarm.notify.emails"), null, 5, "");
		controlBar.addHelpTextTrigger(recipients,
				RS.rbLabel("mail.alarm.notify.emails.desc"));

		// update the status when email connections are made/lost
		UGateKeeper.DEFAULT.addListener(new IGateKeeperListener() {
			@Override
			public void handle(final UGateKeeperEvent<?> event) {
				if (event.getType() == UGateKeeperEvent.Type.EMAIL_CONNECTING) {
					connect.setDisable(true);
					connect.setText(RS.rbLabel("mail.connecting"));
				} else if (event.getType() == UGateKeeperEvent.Type.EMAIL_CONNECTED) {
					connect.setDisable(false);
					// connect.setText(RS.rbLabel("mail.reconnect"));
					connect.setText(RS.rbLabel("mail.connected"));
					connect.setDisable(true);
					setStatusFill(true);
				} else if (event.getType() == UGateKeeperEvent.Type.EMAIL_CONNECT_FAILED) {
					connect.setDisable(false);
					connect.setText(RS.rbLabel("mail.connect"));
					setStatusFill(false);
				} else if (event.getType() == UGateKeeperEvent.Type.EMAIL_DISCONNECTING) {
					connect.setDisable(true);
					connect.setText(RS.rbLabel("mail.disconnecting"));
				} else if (event.getType() == UGateKeeperEvent.Type.EMAIL_DISCONNECTED
						|| event.getType() == UGateKeeperEvent.Type.EMAIL_CLOSED) {
					// run later in case the application is going to exit which
					// will cause an issue with FX thread
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							connect.setDisable(false);
							connect.setText(RS.rbLabel("mail.connect"));
							log.debug("Turning OFF email connection icon");
							setStatusFill(false);
						}
					});
				}
			}
		});

		connect = new Button();
		connect.addEventHandler(MouseEvent.MOUSE_CLICKED,
				new EventHandler<MouseEvent>() {
					@Override
					public void handle(MouseEvent event) {
						connect();
					}
				});
		connect.setText(RS.rbLabel("mail.connect"));

		final GridPane grid = new GridPane();
		grid.setHgap(10d);
		grid.setVgap(30d);

		final VBox toggleView = new VBox(10d);
		toggleView.getChildren().addAll(icon);

		final GridPane connectionGrid = new GridPane();
		connectionGrid.setPadding(new Insets(20d, 5, 5, 5));
		connectionGrid.setHgap(15d);
		connectionGrid.setVgap(15d);
		connectionGrid.add(smtpHost, 0, 0);
		connectionGrid.add(smtpPort, 1, 0);
		connectionGrid.add(imapHost, 0, 1);
		connectionGrid.add(imapPort, 1, 1);
		connectionGrid.add(username, 0, 2);
		connectionGrid.add(password, 1, 2);
		connectionGrid.add(inboxFolder, 0, 3, 2, 1);
		connectionGrid.add(recipients, 0, 4, 2, 1);

		grid.add(toggleView, 0, 0);
		grid.add(connectionGrid, 1, 0);
		grid.add(connect, 1, 1, 2, 1);
		getChildren().add(grid);
	}

	/**
	 * Establishes an email connection using internal parameters
	 */
	public void connect() {
		if (!cb.getActor().getHost().getMailSmtpHost().isEmpty()
				&& cb.getActor().getHost().getMailSmtpPort() > 0
				&& !cb.getActor().getHost().getMailImapHost().isEmpty()
				&& cb.getActor().getHost().getMailImapPort() > 0
				&& !cb.getActor().getHost().getMailUserName().isEmpty()
				&& !cb.getActor().getHost().getMailPassword().isEmpty()) {
			log.debug("Connecting to email...");
			ServiceManager.IMPL.getCredentialService().mergeHost(
					cb.getActor().getHost());
			cb.createEmailConnectionService().start();
		} else {
			log.debug("Unable to connect to email due to blank values");
		}
	}
}
