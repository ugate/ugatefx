package org.ugate.wireless.data;

import java.util.Calendar;

import org.ugate.UGateUtil;
import org.ugate.service.entity.jpa.RemoteNode;

/**
 * Wireless response data
 */
public abstract class RxData {

	private final RemoteNode remoteNode;
	private final Calendar createdTime;
	private final int signalStrength;
	private Status status = Status.NORMAL;

	/**
	 * Constructor
	 * 
	 * @param remoteNode
	 *            the {@linkplain RemoteNode}
	 * @param status
	 *            the initial {@linkplain Status}
	 * @param signalStrength
	 *            the signal strength
	 */
	public RxData(final RemoteNode remoteNode, final Status status,
			final int signalStrength) {
		this.remoteNode = remoteNode;
		setStatus(status);
		this.signalStrength = signalStrength;
		this.createdTime = Calendar.getInstance();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return String
				.format("NODE ADDRESS %4$s, STATUS: %1$s, SIGNAL STRENGTH: %2$s, CREATED: %3$s",
						getStatus(), getSignalStrength(), UGateUtil
								.calFormat(getCreatedTime()),
						getRemoteNode() == null ? "N/A" : getRemoteNode()
								.getAddress());
	}

	/**
	 * @return the {@linkplain RemoteNode}
	 */
	public RemoteNode getRemoteNode() {
		return this.remoteNode;
	}

	/**
	 * @return the response code if an error exists
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * @param status
	 *            the status code
	 */
	public void setStatus(final Status status) {
		this.status = status;
	}

	/**
	 * @return the signal strength when data is received
	 */
	public int getSignalStrength() {
		return signalStrength;
	}

	/**
	 * @return date/time the data was created
	 */
	public Calendar getCreatedTime() {
		return createdTime;
	}

	/**
	 * @return date/time the data was created in a human readable format
	 */
	public String getCreatedTimeString() {
		return UGateUtil.calFormat(getCreatedTime());
	}

	/**
	 * Gets the date/time difference between the {@linkplain #getCreatedTime()}
	 * and the specified time
	 * 
	 * @param laterTime
	 *            the date/time to subtract
	 * @return date/time difference in a human readable format
	 */
	public String getCreatedTimeDiffernce(final Calendar laterTime) {
		return UGateUtil.calFormatDateDifference(getCreatedTime().getTime(),
				laterTime.getTime());
	}

	/**
	 * Wireless transmission status codes
	 */
	public enum Status {
		NORMAL, GENERAL_FAILURE, PARSING_ERROR;
	}
}
