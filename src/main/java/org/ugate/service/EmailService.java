package org.ugate.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.ugate.Command;
import org.ugate.UGateKeeper;
import org.ugate.UGateKeeperEvent;
import org.ugate.UGateUtil;
import org.ugate.mail.EmailAgent;
import org.ugate.mail.EmailEvent;
import org.ugate.mail.IEmailListener;
import org.ugate.resources.RS;
import org.ugate.resources.RS.KEYS;
import org.ugate.service.entity.RemoteNodeType;
import org.ugate.service.entity.jpa.Host;
import org.ugate.service.entity.jpa.MailRecipient;
import org.ugate.service.entity.jpa.RemoteNode;

/**
 * Email service
 */
public class EmailService {

	private final Logger log = UGateUtil.getLogger(EmailService.class);
	private EmailAgent emailAgent;
	private boolean isEmailConnected;

	/**
	 * Only {@linkplain ServiceProvider} constructor
	 */
	EmailService() {
	}
	
	/**
	 * Connects to an {@linkplain EmailAgent}
	 * 
	 * @param host the {@linkplain Host}
	 */
	public boolean connect(final Host host) {
		// test email connection
//		isEmailConnected = true;
//		if (true) {
//			return;
//		}
		// connect to email
		//emailDisconnect();
		if (host == null || host.getId() <= 0) {
			return false;
		}
		if (isEmailConnected) {
			disconnect();
		}
		String msg;
		UGateKeeperEvent<EmailService, Void> event;
		final String smtpHost = host.getMailSmtpHost();
		final int smtpPort = host.getMailImapPort();
		final String imapHost = host.getMailImapHost();
		final int imapPort = host.getMailImapPort();
		final String username = host.getMailUserName();
		final String password = host.getMailPassword();
		final String mainFolderName = host.getMailInboxName();
		try {
			msg = RS.rbLabel(KEYS.MAIL_CONNECTING);
			log.info(msg);
			event = new UGateKeeperEvent<>(this, UGateKeeperEvent.Type.EMAIL_CONNECTING, false);
			event.addMessage(msg);
			UGateKeeper.DEFAULT.notifyListeners(event);
			final List<IEmailListener> listeners = new ArrayList<IEmailListener>();
			listeners.add(new IEmailListener() {
				@Override
				public void handle(final EmailEvent event) {
					String msg;
					if (event.type == EmailEvent.Type.EXECUTE_COMMAND) {
						if (ServiceProvider.IMPL.getWirelessService().isConnected()) {
							RemoteNode rn;
							final List<String> commandMsgs = new ArrayList<String>();
							// send command to all the nodes defined in the email
							for (final String toAddress : event.toAddresses) {
								commandMsgs.clear();
								rn = ServiceProvider.IMPL.getWirelessService().findRemoteNodeByAddress(toAddress);
								for (final Command command : event.commands) {
									try {
										if (rn == null) {
											throw new IllegalArgumentException(
													"Invalid remote address: "
															+ toAddress);
										}
										ServiceProvider.IMPL.getWirelessService().sendData(rn, command);
										msg = RS.rbLabel(KEYS.SERVICE_EMAIL_CMD_EXEC, command, event.from, toAddress);
										log.info(msg);
									} catch (final Throwable t) {
										msg = RS.rbLabel(KEYS.SERVICE_EMAIL_CMD_EXEC_FAILED, command, event.from, toAddress);
										log.error(msg, t);
									}
									commandMsgs.add(msg);
								}
								if (rn != null && !commandMsgs.isEmpty()) {
									UGateKeeper.DEFAULT.notifyListeners(new UGateKeeperEvent<RemoteNode, List<Command>>(
											rn, UGateKeeperEvent.Type.EMAIL_EXECUTED_COMMANDS, 
											false, RemoteNodeType.WIRELESS_ADDRESS, null, null, event.commands, 
											commandMsgs.toArray(new String[]{})));
								} else {
									UGateKeeper.DEFAULT.notifyListeners(new UGateKeeperEvent<RemoteNode, List<Command>>(
											rn, UGateKeeperEvent.Type.EMAIL_EXECUTE_COMMANDS_FAILED, 
											false, RemoteNodeType.WIRELESS_ADDRESS, null, null, event.commands, 
											commandMsgs.toArray(new String[]{})));
								}
							}
						} else {
							msg = RS.rbLabel(KEYS.SERVICE_EMAIL_CMD_EXEC_FAILED, UGateUtil.toString(event.commands), UGateUtil.toString(event.from), 
									UGateUtil.toString(event.toAddresses), RS.rbLabel(KEYS.SERVICE_WIRELESS_CONNECTION_REQUIRED));
							log.warn(msg);
							UGateKeeper.DEFAULT.notifyListeners(new UGateKeeperEvent<EmailService, List<Command>>(
									EmailService.this, UGateKeeperEvent.Type.EMAIL_EXECUTE_COMMANDS_FAILED, false, 
									RemoteNodeType.WIRELESS_ADDRESS, null, null, event.commands, msg));
						}
					} else if (event.type == EmailEvent.Type.CONNECT) {
						isEmailConnected = true;
						msg = RS.rbLabel(KEYS.MAIL_CONNECTED);
						log.info(msg);
						UGateKeeper.DEFAULT.notifyListeners(new UGateKeeperEvent<EmailService, List<Command>>(
								EmailService.this, UGateKeeperEvent.Type.EMAIL_CONNECTED, false, 
								null, null, null, event.commands, msg));
					} else if (event.type == EmailEvent.Type.DISCONNECT) {
						isEmailConnected = false;
						msg = RS.rbLabel(KEYS.MAIL_DISCONNECTED);
						log.info(msg);
						UGateKeeper.DEFAULT.notifyListeners(new UGateKeeperEvent<EmailService, List<Command>>(
								EmailService.this, UGateKeeperEvent.Type.EMAIL_DISCONNECTED, false, 
								null, null, null, event.commands, msg));
					} else if (event.type == EmailEvent.Type.CLOSED) {
						isEmailConnected = false;
						msg = RS.rbLabel(KEYS.MAIL_CLOSED);
						log.info(msg);
						UGateKeeper.DEFAULT.notifyListeners(new UGateKeeperEvent<EmailService, List<Command>>(
								EmailService.this, UGateKeeperEvent.Type.EMAIL_CLOSED, false,  
								null, null, null, event.commands, msg));
					}
				}
			});
			List<String> authEmails = new ArrayList<>(host.getMailRecipients().size());
			for (final MailRecipient mr : host.getMailRecipients()) {
				authEmails.add(mr.getEmail());
			}
			this.emailAgent = EmailAgent.start(smtpHost, String.valueOf(smtpPort), imapHost, String.valueOf(imapPort), 
					username, password, mainFolderName, authEmails, listeners.toArray(new IEmailListener[0]));
			return true;
		} catch (final Throwable t) {
			msg = RS.rbLabel(KEYS.MAIL_CONNECT_FAILED, 
					smtpHost, smtpPort, imapHost, imapPort, username, mainFolderName);
			log.error(msg, t);
			event = new UGateKeeperEvent<>(
					this, UGateKeeperEvent.Type.EMAIL_CONNECT_FAILED, false);
			event.addMessage(msg);
			event.addMessage(t.getMessage());
			UGateKeeper.DEFAULT.notifyListeners(event);
			return false;
		}
	}

	/**
	 * Disconnects from email
	 */
	public void disconnect() {
		if (emailAgent != null) {
			final String msg = RS.rbLabel(KEYS.MAIL_DISCONNECTING);
			log.info(msg);
			UGateKeeper.DEFAULT.notifyListeners(new UGateKeeperEvent<EmailService, Void>(
					this, UGateKeeperEvent.Type.EMAIL_DISCONNECTING, false, msg));
			emailAgent.disconnect();
			emailAgent = null;
		}
	}
	
	/**
	 * Sends an email
	 * 
	 * @param subject the subject of the email
	 * @param message the email message
	 * @param from who the email is from
	 * @param to the recipients of the email
	 * @param fileNames file name paths to any attachments (optional)
	 */
	public void send(String subject, String message, String from, String[] to, String... fileNames) {
		if (emailAgent != null) {
			emailAgent.send(subject, message, from, to, fileNames);
		} else {
			log.warn("Unable to send email... no connection established");
		}
	}
	
	/**
	 * @return true if the {@linkplain EmailAgent} is connected
	 */
	public boolean isConnected() {
		return isEmailConnected;
	}
}
