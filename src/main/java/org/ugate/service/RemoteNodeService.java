package org.ugate.service;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.ugate.service.dao.RemoteNodeDao;
import org.ugate.service.entity.jpa.Host;
import org.ugate.service.entity.jpa.RemoteNode;
import org.ugate.service.entity.jpa.RemoteNodeReading;

/**
 * {@linkplain RemoteNode} service
 */
@Service
@Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
public class RemoteNodeService {

	@Resource
	private RemoteNodeDao remoteNodeDao;

	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public void save(final RemoteNode remoteNode) {
		remoteNodeDao.persistEntity(remoteNode);
	}
	
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public void merge(final RemoteNode remoteNode) {
		remoteNodeDao.mergeEntity(remoteNode);
	}

	/**
	 * Gets a {@link RemoteNode} by {@link RemoteNode#getId()}
	 * 
	 * @param id
	 *            the {@link RemoteNode#getId()}
	 * @return the {@link RemoteNode}
	 */
	public RemoteNode findById(final int id) {
		return remoteNodeDao.findEntityById(RemoteNode.class, id);
	}

	/**
	 * Gets a {@link RemoteNode} by {@link RemoteNode#getAddress()}
	 * 
	 * @param address
	 *            the {@link RemoteNode#getAddress()}
	 * @return the {@link RemoteNode}
	 */
	public RemoteNode findByAddress(final String address) {
		if (address != null && !address.isEmpty()) {
			return remoteNodeDao.findByAddress(address);
		}
		return null;
	}

	/**
	 * Gets the {@linkplain RemoteNode}(s) for a given {@linkplain Host#getId()}
	 * 
	 * @param hostId
	 *            the {@linkplain Host#getId()} to get the
	 *            {@linkplain RemoteNode}(s) for
	 * @return the {@linkplain RemoteNode}(s)
	 */
	public List<RemoteNode> findForHost(final int hostId) {
		if (hostId > 0) {
			return remoteNodeDao.findByHostId(hostId);
		}
		return Collections.emptyList();
	}

	/**
	 * Gets the {@linkplain RemoteNodeReading}(s) for a given
	 * {@linkplain RemoteNode#getId()}
	 * 
	 * @param remoteNode
	 *            the {@linkplain RemoteNode} to get the
	 *            {@linkplain RemoteNodeReading}(s) for
	 * @param startPosition
	 *            the pagination starting position
	 * @param maxResults
	 *            the maximum number of {@linkplain RemoteNodeReading}(s) to
	 *            return
	 * @return the {@linkplain RemoteNodeReading}(s)
	 */
	public List<RemoteNodeReading> findReadingsById(final RemoteNode remoteNode, 
			final int startPosition, final int maxResults) {
		return remoteNodeDao.findReadingsById(remoteNode, startPosition, maxResults);
	}

	/**
	 * Gets the latest {@link RemoteNodeReading} using the last entry by
	 * {@link RemoteNodeReading#getReadDate()}
	 * 
	 * @param remoteNode
	 *            the {@linkplain RemoteNode} to get the
	 *            {@linkplain RemoteNodeReading} for
	 * @return the {@linkplain RemoteNodeReading}
	 */
	public RemoteNodeReading findReadingLatest(final RemoteNode remoteNode) {
		final List<RemoteNodeReading> rnrs = findReadingsById(remoteNode, 0, 1);
		if (rnrs != null && !rnrs.isEmpty()) {
			return rnrs.get(0);
		}
		return null;
	}

	/**
	 * Gets the {@linkplain RemoteNodeReading}(s) for a given
	 * {@linkplain RemoteNode#getId()} and {@linkplain Date}
	 * 
	 * @param remoteNode
	 *            the {@linkplain RemoteNode} to get the
	 *            {@linkplain RemoteNodeReading}(s) for
	 * @param date
	 *            the {@linkplain Date} to get the
	 *            {@linkplain RemoteNodeReading}(s) for
	 * @param asc
	 *            true for results in ascending order, false for descending
	 * @return the {@linkplain RemoteNodeReading}(s)
	 */
	public List<RemoteNodeReading> findReadingsByDate(
			final RemoteNode remoteNode, final Calendar cal, 
			final boolean asc) {
		final Calendar sc = Calendar.getInstance();
		sc.setTime(cal.getTime());
		sc.set(Calendar.HOUR_OF_DAY, 0);
		sc.set(Calendar.MINUTE, 0);
		sc.set(Calendar.SECOND, 0);
		sc.set(Calendar.MILLISECOND, 0);
		final Calendar ec = Calendar.getInstance();
		ec.setTime(sc.getTime());
		ec.add(Calendar.DAY_OF_MONTH, 1);
		//UGateUtil.PLAIN_LOGGER.info("Start : " + UGateUtil.calFormat(sc) + " End: " + UGateUtil.calFormat(ec));
		return remoteNodeDao.findReadingsByIdAndDate(remoteNode, sc, ec, asc);
	}

	/**
	 * Saves a new {@linkplain RemoteNodeReading}
	 * 
	 * @param remoteNodeReading
	 *            the {@linkplain RemoteNodeReading} to save
	 */
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public void saveReading(final RemoteNodeReading remoteNodeReading) {
		remoteNodeDao.persistEntity(remoteNodeReading);
	}

	/**
	 * Removes any {@linkplain RemoteNodeReading}(s) for a given
	 * {@linkplain Date} range and {@linkplain RemoteNode}
	 * 
	 * @param remoteNode
	 *            the {@linkplain RemoteNode} to remove
	 *            {@linkplain RemoteNodeReading}(s) for
	 * @param startInclusive
	 *            the {@linkplain Date} range to <i>start</i>
	 *            {@linkplain RemoteNodeReading} removal at (inclusive)
	 * @param startInclusive
	 *            the {@linkplain Date} range to <i>end</i>
	 *            {@linkplain RemoteNodeReading} removal at (exclusive)
	 */
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public void removeReadingsByDateRange(final RemoteNode remoteNode,
			final Calendar startInclusive, final Calendar endExclusive) {
		remoteNodeDao.deleteReadingsByIdAndDate(remoteNode, startInclusive,
				endExclusive);
	}
}
