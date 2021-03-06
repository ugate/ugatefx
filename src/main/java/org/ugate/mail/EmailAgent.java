package org.ugate.mail;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.AuthenticationFailedException;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.mail.event.ConnectionEvent;
import javax.mail.event.ConnectionListener;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ugate.service.entity.ActorType;
import org.ugate.service.entity.Command;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;

/**
 * Email agent service provider that will listen for incoming emails for
 * {@linkplain Command}(s) to execute.
 */
public class EmailAgent implements Runnable {

	private static final Logger log = LoggerFactory.getLogger(EmailAgent.class);
	public static final Pattern SUBJECT_LINE_PATTERN = Pattern.compile("(?:\\[?(?:[Ff][Ww][Dd]?|[Rr][Ee])(?:\\s*[:;-]+\\s*\\]?))+");

//	private static final List<IEmailListener> LISTENERS = new CopyOnWriteArrayList<IEmailListener>();
	private final List<IEmailListener> listeners = new ArrayList<IEmailListener>();
//	private static final AtomicBoolean runIt = new AtomicBoolean(true);
	private boolean runIt = true;
	private final Options options;
	
//	private volatile IMAPFolder mainFolder;
//	private volatile IMAPStore store;
	private IMAPFolder mainFolder;
	private IMAPStore store;
	
	/**
	 * Creates/Starts an email agent service
	 * 
	 * @param options
	 *            the {@link Options} or {@link GmailOptions}
	 * @param listeners
	 *            any {@link IEmailListener}(s)
	 * @return the created/started email agent
	 */
	public static EmailAgent start(final Options options,
			final IEmailListener... listeners) {
		final EmailAgent emailAgent = new EmailAgent(options, listeners);
		final Thread emailAgentThread = new Thread(Thread.currentThread()
				.getThreadGroup(), emailAgent, getThreadName("main"));
		emailAgentThread.setDaemon(true);
		emailAgentThread.start();
		return emailAgent;
	}
	
	/**
	 * Creates an email agent service
	 * 
	 * @param options
	 *            the {@link Options}
	 * @param listeners
	 *            any {@link IEmailListener}(s)
	 */
	protected EmailAgent(final Options options,
			final IEmailListener... listeners) {
		if (options == null) {
			throw new IllegalArgumentException(String.format(
					"%1$s is required", Options.class.getName()));
		}
		this.options = options;

		this.listeners.addAll(Arrays.asList(listeners));

		this.runIt = true;
	}

	/**
	 * The email thread that is automatically started when a new
	 * {@linkplain EmailAgent} is created
	 */
	@Override
	public void run() {
		while(runIt) {
			try {
				disconnect(store, mainFolder);
				
				log.info("Connecting to store/floder...");
				final Session session = Session.getInstance(options.genProperties());
				session.setDebug(options.isDebug());
	
				final URLName imapUrlName = options.genImapUrlName();
				store = (IMAPStore) session.getStore(imapUrlName);
	
				final InternalConnectionListener connectionListener = new InternalConnectionListener();
				//store.addConnectionListener(connectionListener);
				store.connect(imapUrlName.getHost(), imapUrlName.getPort(), imapUrlName.getUsername(), imapUrlName.getPassword());
				mainFolder = (IMAPFolder) store.getFolder(options.getImapFolder());
				mainFolder.addConnectionListener(connectionListener);
				mainFolder.open(Folder.READ_ONLY);
				mainFolder.addMessageCountListener(new MessageCountAdapter() {
	
					@Override
					public void messagesAdded(MessageCountEvent event) {
						log.debug("Incoming messages...");
						try {
							if (listeners.isEmpty()) {
								log.warn("No listeners available for executing remote commands");
								return;
							}
							final Message[] msgs = event.getMessages();
							IMAPMessage msg;
							for (final Message rmsg : msgs) {
								if (!(rmsg instanceof IMAPMessage)) {
									log.info(String.format("Expected %1$s, but received %2$s subject: %3$s", 
											IMAPMessage.class.getSimpleName(), rmsg.getClass().getSimpleName(), rmsg.getSubject()));
									continue;
								}
								msg = (IMAPMessage) rmsg;
								final Address[] froms = msg.getFrom();
								if (hasCommandPermission(froms)) {
									final StringBuffer errorMessages = new StringBuffer();
									final List<Command> commands = getValidCommands(msg, errorMessages);
									final Set<String> destinations = getValidCommandDestinations(msg, errorMessages);
									if (errorMessages.length() > 0) {
										if (log.isInfoEnabled()) {
											log.info(String.format("Invalid command(s) received from: %1$s", Arrays.toString(froms)));
										}
										sendReply(msg, errorMessages.toString());
										return;
									}
									if (log.isInfoEnabled()) {
										log.info(String.format("Received raw commands: %1$s from: %2$s", 
												Arrays.toString(commands.toArray()), Arrays.toString(froms)));
									}
									final Thread newThread = new Thread(getThreadName(msg.getMessageID())) {
										@Override
										public void run() {
											final EmailEvent event = new EmailEvent(EmailEvent.Type.EXECUTE_COMMAND, commands, 
													froms, destinations);
											for (final IEmailListener listener : listeners) {
												listener.handle(event);
											}
										}
									};
									newThread.start();
								} else if (log.isInfoEnabled()) {
									log.info(String.format("Received an email message from %1$s, but they do not have permission to execute commands", 
											Arrays.toString(froms)));
								}
							}
						} catch (Exception e) {
							log.error("Error when processing incoming message", e);
						}
					}
				});
				log.info("Connected to " + imapUrlName.getHost() + " waiting for messages...");
				// when idle (blocking) gets interrupted (by host, sending an email, getting message content, etc.)
				// try to reinstate the idle process, if that fails (usually due to a closed folder) try to reconnect
				while (runIt) {
					mainFolder.idle();
					if (runIt) {
						log.debug("Stopped listening for incoming messages... attempting to reconnect...");
					} else {
						log.info("Stopped listening for incoming messages (from disconnect)");
					}
				}
			} catch (final FolderClosedException e) {
				if (runIt) {
					log.info("Folder closed... attempting to reconnect...");
				}
				dispatchEmailEvent(EmailEvent.Type.FOLDER_CLOSED, e.getClass().getSimpleName());
			} catch (final AuthenticationFailedException e) {
				runIt = false;
				log.warn(String.format("Unable to connect to email due to an %1$s", AuthenticationFailedException.class.getName()), e);
				dispatchEmailEvent(EmailEvent.Type.AUTH_FAILED, e.getClass().getSimpleName());
			} catch (final Exception e) {
				if (runIt) {
					log.warn("Unable to connect... attempting to reconnect...", e);
				}
				dispatchEmailEvent(EmailEvent.Type.GENERAL_EXCEPTION, e.getClass().getSimpleName());
			}
		}
	}
	
	/**
	 * Disconnects the email agent
	 */
	public void disconnect() {
		runIt = false;
		disconnect(store, mainFolder);
	}
	
	/**
	 * Disconnects from the email service
	 * 
	 * @param store
	 *            the {@linkplain IMAPStore}
	 * @param mainFolder
	 *            the {@linkplain IMAPFolder}
	 */
	private void disconnect(final IMAPStore store, final IMAPFolder mainFolder) {		
		try {
			if (mainFolder != null && mainFolder.isOpen()) {
				mainFolder.close(false);
				log.info("Disconnected from folder");
			}
			if (store != null && store.isConnected()) {
				store.close();
				log.info("Disconnected from store");
			}
		} catch (Exception e) {
			log.error("Unable to disconnect", e);
		}
	}

	/**
	 * Sends a message
	 * 
	 * @param subject
	 *            the subject of the message
	 * @param message
	 *            the message body
	 * @param from
	 *            the email address whom the email is from
	 * @param to
	 *            the email address(es) that the email will be sent to
	 * @param paths
	 *            the file {@link Path}(s) that will be attached to the email (if any)
	 */
	public void send(final String subject, final String message, final String from, final String[] to, final Path... paths) {
		try {
			log.info("Sending message...");
			final Session session = Session.getInstance(options.genProperties());
			final MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(from));
			final InternetAddress[] addresses = new InternetAddress[to.length];
			int toIndex = 0;
			for (String t : to) {
				addresses[toIndex++] = new InternetAddress(t);
			}
			msg.setRecipients(Message.RecipientType.TO, addresses);
			msg.setSubject(subject);
			msg.setSentDate(new Date());
			final Multipart mp = new MimeMultipart();
			final MimeBodyPart mbp1 = new MimeBodyPart();
			mbp1.setText(message);
			mp.addBodyPart(mbp1);
			if (paths != null && paths.length > 0) {
				for (final Path path : paths) {
					final MimeBodyPart mbp = new MimeBodyPart();
					final FileDataSource fds = new FileDataSource(path.toFile());
					mbp.setDataHandler(new DataHandler(fds));
					mbp.setFileName(fds.getName());
					mp.addBodyPart(mbp);
				}
			}
			// text attachment
			// MimeBodyPart mbp2 = new MimeBodyPart();
			// mbp2.setText("some text in an attachment form");
			// mp.addBodyPart(mbp2);
			msg.setContent(mp);
			
			// wirelessBtn to SMTP transport and send message
			send(session, msg);
		} catch (Exception e) {
			log.error("Unable to send mail message", e);
			return;
		}
	}
	
	/**
	 * Connects to the SMTP transport and send the message
	 * 
	 * @param session
	 *            the email session
	 * @param msg
	 *            the message to send
	 */
	protected void send(final Session session, final MimeMessage msg) {
		try {
			final URLName smtpUrlName = options.genSmtpUrlName();
			log.debug("Opening transport to: " + smtpUrlName);
			final Transport transport = session.getTransport(smtpUrlName);
			transport.connect(smtpUrlName.getHost(), smtpUrlName.getPort(),
					smtpUrlName.getUsername(), smtpUrlName.getPassword());
			transport.sendMessage(msg, msg.getAllRecipients());
			transport.close();
			log.info("Message sent");
		} catch (Exception e) {
			log.error("Unable to send message", e);
		} finally {
			// readyToListen();
		}
	}
	
	/**
	 * Sends a reply email
	 * 
	 * @param originalMessage
	 *            the original message
	 * @param replyMessage
	 *            the reply message
	 */
	protected void sendReply(final MimeMessage originalMessage,
			final String replyMessage) {
		try {
			final MimeMessage replyMsg = (MimeMessage) originalMessage
					.reply(false);
			replyMsg.setFrom(originalMessage.getFrom()[0]);
			send(Session.getInstance(options.genProperties()), replyMsg);
		} catch (Exception e) {
			log.error("Unable to send reply message", e);
		}
	}

	/**
	 * Extracts the to destinations from the email message subject (if any)
	 * 
	 * @param msg
	 *            the message to extract commands from
	 * @param invalidCommandErrors
	 *            <code>\n</code> delimited buffer to add error messages to (if
	 *            they occur)
	 * @return the set of to destinations
	 */
	protected Set<String> getValidCommandDestinations(final MimeMessage msg, final StringBuffer invalidCommandErrors) {
		final Set<String> commandDestitiantions = new HashSet<String>();
		try {
			if (msg.getSubject() != null && !msg.getSubject().isEmpty()) {
				log.debug(String.format("Checking message subject for destinations: %1$s", msg.getSubject()));
		        final Matcher m = SUBJECT_LINE_PATTERN.matcher(msg.getSubject());
		        final String subject = m.replaceAll("");
		        log.debug(String.format("Adjusted subject: %1$s", subject));
				commandDestitiantions.addAll(Arrays.asList(subject.trim().split(ActorType.MAIL_COMMAND_DELIMITER)));
			}
		} catch (final Exception e) {
			log.error("Unable to get command destinations from message", e);
			invalidCommandErrors.append("An unexpected error occurred during command destination extraction: " + e.getMessage());
		}
		return commandDestitiantions;
	}
	
	/**
	 * Extracts any valid commands from an email message body (delimited by
	 * {@linkplain HostSettings#MAIL_COMMAND_DELIMITER})
	 * 
	 * @param msg
	 *            the message to extract commands from
	 * @param invalidCommandErrors
	 *            <code>\n</code> delimited buffer to add error messages to (if
	 *            they occur)
	 * @return the list of valid commands
	 */
	protected List<Command> getValidCommands(final MimeMessage msg, final StringBuffer invalidCommandErrors) {
		final List<Command> validCommands = new ArrayList<Command>();
		try {
			final List<String> rawCommands = new ArrayList<String>();
			log.debug("Checking message body for commands");
			String msgRawContent = null;
			if (msg.getContentType().toLowerCase().indexOf("text") > -1) {
				msgRawContent = (String) msg.getContent();
			} else if (msg.getContentType().toLowerCase().indexOf("multipart") > -1) {
				final Multipart multipart = (Multipart) msg.getContent();
				BodyPart bodyPart = multipart.getBodyPart(0);
				msgRawContent = bodyPart.getContent().toString(); 
			} else {
				log.warn("Commands being ignored for content type: " + msg.getContentType());
			}
			if (msgRawContent != null) {
				rawCommands.addAll(Arrays.asList(msgRawContent.trim().split(ActorType.MAIL_COMMAND_DELIMITER)));
			}
			int intCmd = -1;
			Command cmd = null;
			for (String rawCmd : rawCommands) {
				try {
					intCmd = Integer.parseInt(rawCmd);
				} catch (NumberFormatException e) {
					log.warn("Non-numeric command received: " + rawCmd, e);
					continue;
				}
				if ((cmd = Command.lookup(intCmd)) != null) {
					validCommands.add(cmd);
				} else if (invalidCommandErrors != null) {
					invalidCommandErrors.append("Invalid Command \"" + intCmd + "\"\n");
				}
			}
		} catch (final Exception e) {
			log.error("Unable to get valid commands from message", e);
			invalidCommandErrors.append("An unexpected error occurred during command extraction: " + e.getMessage());
		}
		return validCommands;
	}

	/**
	 * Determines if the addresses have permission to execute commands
	 * 
	 * @param addresses
	 *            the addresses to check for
	 * @return true when the addresses have permission to execute commands
	 */
	protected boolean hasCommandPermission(final Address... addresses) {
		boolean hasPermission = false;
		InternetAddress inernetAddress;
		for (Address from : addresses) {
			inernetAddress = (InternetAddress) from;
			if (options.isCommandAuthorized(inernetAddress.getAddress())) {
				hasPermission = true;
				break;
			}
		}
		return hasPermission;
	}

	/**
	 * Adds an email listener
	 * 
	 * @param listener
	 *            the listener
	 */
	public void addListener(final IEmailListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes an email listener
	 * 
	 * @param listener
	 *            the listener
	 */
	public void removeListener(final IEmailListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Gets a thread name for internally spawned threads
	 * 
	 * @param postfix
	 *            the name appended to the end of the thread name
	 * @return the thread name
	 */
	private static String getThreadName(final String postfix) {
		return EmailAgent.class.getSimpleName() + '-' + postfix;
	}

	/**
	 * Dispatches an email event
	 * 
	 * @param type
	 *            the {@linkplain EmailEvent.Type}
	 */
	private void dispatchEmailEvent(final EmailEvent.Type type, final String threadName) {
		new Thread(getThreadName(threadName)) {
			@Override
			public void run() {
				try {
					final EmailEvent event = new EmailEvent(type, null, null, null);
					for (final IEmailListener listener : listeners) {
						listener.handle(event);
					}
				} catch (final Throwable t) {
					log.error(String.format("Unable to dispatch %1$s", EmailEvent.class.getName(), type), t);
				}
			}
		}.start();
	}

	/**
	 * @return the {@link Options} used by the {@link EmailAgent}
	 */
	public Options getOptions() {
		return options;
	}

	/**
	 * {@link EmailAgent} Options interface. See <a href=
	 * "http://javamail.kenai.com/nonav/javadocs/com/sun/mail/imap/package-summary.html"
	 * >the IMAP package summary</a> for more details concerning some of the
	 * parameters used. Also, see <a
	 * href="http://www.oracle.com/technetwork/java/sslnotes-150073.txt">the SSL
	 * notes</a>
	 */
	public static abstract class Options {

		/**
		 * @return the outgoing SMTP user name
		 */
		public abstract String getSmtpUsername();

		/**
		 * @return the outgoing SMTP password
		 */
		public abstract String getSmtpPassword();

		/**
		 * @return the outgoing SMTP host
		 */
		public abstract String getSmtpHost();

		/**
		 * @return the outgoing SMTP port (defaults to <b>465</b> for
		 *         {@link #useSmtpSsl()} and {@link #useTls()} <b>true</b>,
		 *         <b>587</b> for just {@link #useSmtpSsl()} <b>true</b>, and
		 *         <b>25</b> for everything else)
		 */
		public int getSmtpPort() {
			return useSmtpSsl() && useTls() ? 465 : useTls() ? 587 : 25;
		}

		/**
		 * @return the incoming IMAP user name
		 */
		public abstract String getImapUsername();

		/**
		 * @return the incoming IMAP password
		 */
		public abstract String getImapPassword();

		/**
		 * @return the incoming IMAP host
		 */
		public abstract String getImapHost();

		/**
		 * @return the incoming IMAP port (defaults to <b>993</b> for
		 *         {@link #useImapSsl()} and {@link #useTls()} <b>true</b>,
		 *         <b>585</b> for just {@link #useImapSsl()} <b>true</b>, and
		 *         <b>143</b> for everything else)
		 */
		public int getImapPort() {
			return useImapSsl() && useTls() ? 993 : useImapSsl() ? 585
					: 143;
		}

		/**
		 * @return true when the email is authorized to execute command via the
		 *         {@link EmailAgent}
		 */
		public abstract boolean isCommandAuthorized(final String email);

		/**
		 * @return true to use SSL for SMTP connections
		 */
		public abstract boolean useSmtpSsl();

		/**
		 * @return true to use SSL for IMAP connections
		 */
		public abstract boolean useImapSsl();

		/**
		 * @return true to use TLS for connections
		 */
		public abstract boolean useTls();

		/**
		 * @return the IMAP folder to listen on for incoming messages (defaults
		 *         to "Inbox")
		 */
		public String getImapFolder() {
			return "Inbox";
		}

		/**
		 * @return the incoming IMAP trust (When set to "*", all hosts are
		 *         trusted. If set to a whitespace separated list of hosts,
		 *         those hosts are trusted. Otherwise, trust depends on the
		 *         certificate the server presents. Only applicable when
		 *         {@link #useImapSsl()} is true)
		 */
		public String getImapTrust() {
			return null;
		}

		/**
		 * The IMAP and SMTP protocols supports the use of the STARTTLS command
		 * (see RFC 2487 and RFC 3501) to switch the connection to be secured by
		 * TLS. Use of the STARTTLS command is preferred in cases where the
		 * server supports both SSL and non-SSL connections.
		 * 
		 * @return true to use the STARTTLS command for the connection
		 */
		public abstract boolean useStartTls();

		/**
		 * @return true to turn on debugging to the console (defaults to false)
		 */
		public boolean isDebug() {
			return false;
		}

		/**
		 * @return the {@link URLName} for outgoing SMTP messages
		 */
		private final URLName genSmtpUrlName() {
			return new URLName(genSmtpProtocol(), getSmtpHost(), getSmtpPort(),
					null, getSmtpUsername(), getSmtpPassword());
		}

		/**
		 * @return the {@link URLName} for incoming IMAP messages
		 */
		private final URLName genImapUrlName() {
			return new URLName(genImapProtocol(), getImapHost(), getImapPort(),
					null, getImapUsername(), getImapPassword());
		}

		/**
		 * @return the outgoing SMTP protocol
		 */
		private final String genSmtpProtocol() {
			return useSmtpSsl() ? "smtps" : "smtp";
		}

		/**
		 * @return the incoming IMAP protocol
		 */
		private final String genImapProtocol() {
			return useImapSsl() ? "imaps" : "imap";
		}

		/**
		 * @return the {@link Properties} required for the {@link EmailAgent}
		 */
		private final Properties genProperties() {
			final String smtpStr = useSmtpSsl() ? "smtps" : "smtp";
			final String imapStr = useImapSsl() ? "imaps" : "imap";
			final Properties props = new Properties();
			props.put("mail." + smtpStr + ".auth", "true");
			props.put("mail." + smtpStr + ".user", getSmtpUsername());
			props.put("mail." + smtpStr + ".host", getSmtpHost());
			props.put("mail." + smtpStr + ".port",
					String.valueOf(getSmtpPort()));
			if (useImapSsl() && getImapTrust() != null) {
				props.put("mail." + imapStr + ".ssl.trust", getImapTrust());
			}
			if (useStartTls()) {
				props.put("mail." + smtpStr + ".starttls.enable", "true");
				props.setProperty("mail." + imapStr + ".starttls.enable",
						"true");
			}
			return props;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return String.format("%1$s smtpUsername=%2$s, imapUsername=%3$s, "
					+ "smtpHost=%4$s, smtpPort=%5$s, imapHost=%6$s, "
					+ "imapPort=%7$s, useSmtpSsl=%8$s, useImapSsl=%9$s, "
					+ "useImapTls=%10$s, useStartTls=%11$s", getClass()
					.getSimpleName(), getSmtpUsername(), getImapUsername(),
					getSmtpHost(), getSmtpPort(), getImapHost(), getImapPort(),
					useSmtpSsl(), useImapSsl(), useTls(), useStartTls());
		}
	}

	/**
	 * GMail convenience {@link Options}
	 */
	public abstract class GmailOptions extends Options {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public final String getSmtpHost() {
			return "smtp.gmail.com";
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public final String getImapHost() {
			return "imap.gmail.com";
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public final boolean useSmtpSsl() {
			return true;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public final boolean useImapSsl() {
			return true;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public final boolean useStartTls() {
			return true;
		}
	}

	/**
	 * Internal connection listener used to spawn new threads for external email
	 * listeners
	 */
	class InternalConnectionListener implements ConnectionListener {
		
		@Override
		public void opened(final ConnectionEvent event) {
			log.info("Mail Store/Folder opened: " + event.getType());
			dispatchEmailEvent(EmailEvent.Type.CONNECT, "opened");
		}
		
		@Override
		public void disconnected(final ConnectionEvent event) {
			log.info("Mail Store/Folder disconnected unexpectedly: " + event.getType());
			dispatchEmailEvent(EmailEvent.Type.DISCONNECT, "disconnected");
		}
		
		@Override
		public void closed(final ConnectionEvent event) {
			log.info("Mail Store/Folder disconnected: " + event.getType());
			dispatchEmailEvent(EmailEvent.Type.CLOSED, "closed");
		}
	};
}
