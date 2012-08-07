package org.ugate.service.entity.jpa;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Size;

import org.ugate.service.entity.Email;
import org.ugate.service.entity.Model;


/**
 * The persistent class for the HOST database table.
 * 
 */
@Entity
@Table(name="HOST")
public class Host implements Model {
	private static final long serialVersionUID = 1L;

	@Id
	@SequenceGenerator(name="HOST_ID_GENERATOR", sequenceName="HOST_ID", allocationSize=1)
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="HOST_ID_GENERATOR")
	@Column(unique=true, nullable=false)
	private int id;

	@Column(name="COM_ADDRESS", length=100)
	private String comAddress;

	@Column(name="COM_BAUD")
	@Digits(integer=6, fraction=0, message="valid.com.digits")
	private int comBaud;

	@Column(name="COM_PORT", length=50)
	private String comPort;

	@Column(name="MAIL_IMAP_HOST", length=100)
	private String mailImapHost;

	@Column(name="MAIL_IMAP_PORT")
	private int mailImapPort;

	@Column(name="MAIL_INBOX_NAME", length=100)
	private String mailInboxName;

	@Column(name="MAIL_PASSWORD", length=100)
	@Size(min=8, max=30, message="valid.password.length")
	private String mailPassword;

	@Column(name="MAIL_SMTP_HOST", length=100)
	private String mailSmtpHost;

	@Column(name="MAIL_SMTP_PORT")
	private int mailSmtpPort;

	@Email
	@Column(name="MAIL_USER_NAME", length=100)
	private String mailUserName;

	@Column(name="USE_METRIC", nullable=false)
	private boolean useMetric;
	
	@Column(name="WEB_HOST")
	private String webHost;
	
	@Column(name="WEB_PORT")
	private int webPort;
	
	@Lob
	@Basic(fetch=FetchType.EAGER)
	@Column(name="WEB_KEY_STORE")
	private byte[] webKeyStore;

	//bi-directional many-to-one association to Actor
	@OneToMany(mappedBy="host")
	private Set<Actor> actors;
	
	//bi-directional many-to-many association to Role
    @ManyToMany(fetch=FetchType.EAGER, cascade=CascadeType.ALL)
	@JoinTable(
		name="HOST_MAIL_RECIPIENT"
		, joinColumns={
			@JoinColumn(name="HOST_ID", nullable=false)
			}
		, inverseJoinColumns={
			@JoinColumn(name="MAIL_RECIPIENT_ID", nullable=false)
			}
		, uniqueConstraints={
			@UniqueConstraint(columnNames = {"HOST_ID", "MAIL_RECIPIENT_ID"})
			}
		)
    @OrderBy("email")
	private LinkedHashSet<MailRecipient> mailRecipients;
    
	//bi-directional many-to-one association to RemoteNode
	@OneToMany(mappedBy="host")
	private Set<RemoteNode> remoteNodes;

    public Host() {
    }

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getComAddress() {
		return this.comAddress;
	}

	public void setComAddress(String comAddress) {
		this.comAddress = comAddress;
	}

	public int getComBaud() {
		return this.comBaud;
	}

	public void setComBaud(int comBaud) {
		this.comBaud = comBaud;
	}

	public String getComPort() {
		return this.comPort;
	}

	public void setComPort(String comPort) {
		this.comPort = comPort;
	}

	public String getMailImapHost() {
		return this.mailImapHost;
	}

	public void setMailImapHost(String mailImapHost) {
		this.mailImapHost = mailImapHost;
	}

	public int getMailImapPort() {
		return this.mailImapPort;
	}

	public void setMailImapPort(int mailImapPort) {
		this.mailImapPort = mailImapPort;
	}

	public String getMailInboxName() {
		return this.mailInboxName;
	}

	public void setMailInboxName(String mailInboxName) {
		this.mailInboxName = mailInboxName;
	}

	public String getMailPassword() {
		return this.mailPassword;
	}

	public void setMailPassword(String mailPassword) {
		this.mailPassword = mailPassword;
	}

	public String getMailSmtpHost() {
		return this.mailSmtpHost;
	}

	public void setMailSmtpHost(String mailSmtpHost) {
		this.mailSmtpHost = mailSmtpHost;
	}

	public int getMailSmtpPort() {
		return this.mailSmtpPort;
	}

	public void setMailSmtpPort(int mailSmtpPort) {
		this.mailSmtpPort = mailSmtpPort;
	}

	public String getMailUserName() {
		return this.mailUserName;
	}

	public void setMailUserName(String mailUserName) {
		this.mailUserName = mailUserName;
	}

	public boolean getUseMetric() {
		return this.useMetric;
	}

	public void setUseMetric(boolean useMetric) {
		this.useMetric = useMetric;
	}

	public Set<Actor> getActors() {
		return this.actors;
	}

	public void setActors(Set<Actor> actors) {
		this.actors = actors;
	}

	public LinkedHashSet<MailRecipient> getMailRecipients() {
		return mailRecipients;
	}

	public void setMailRecipients(LinkedHashSet<MailRecipient> mailRecipients) {
		this.mailRecipients = mailRecipients;
	}

	public String getWebHost() {
		return webHost;
	}

	public void setWebHost(String webHost) {
		this.webHost = webHost;
	}

	public int getWebPort() {
		return webPort;
	}

	public void setWebPort(int webPort) {
		this.webPort = webPort;
	}

	public byte[] getWebKeyStore() {
		return webKeyStore;
	}

	public void setWebKeyStore(byte[] webKeyStore) {
		this.webKeyStore = webKeyStore;
	}

	public Set<RemoteNode> getRemoteNodes() {
		return remoteNodes;
	}

	public void setRemoteNodes(Set<RemoteNode> remoteNodes) {
		this.remoteNodes = remoteNodes;
	}
	
}