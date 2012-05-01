package org.ugate.service.web;

import java.io.IOException;
import java.sql.Connection;
import java.util.List;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.openjpa.persistence.OpenJPAEntityManager;
import org.apache.openjpa.persistence.OpenJPAPersistence;
import org.eclipse.jetty.plus.jndi.Transaction;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ugate.UGateUtil;
import org.ugate.service.entity.jpa.Message;

public class DefaultHandler extends AbstractHandler {

	private static final Logger log = LoggerFactory.getLogger(DefaultHandler.class);

	public void handle(final String target, final Request baseRequest, final HttpServletRequest request, 
			final HttpServletResponse response) throws IOException, ServletException {
		response.setContentType("text/html;charset=utf-8");
		response.setStatus(HttpServletResponse.SC_OK);
		baseRequest.setHandled(true);
		response.getWriter().println("<h1>Hello UGate World</h1>");
		try {
	        // Create a new EntityManagerFactory using the System properties.
	        // The "hellojpa" name will be used to configure based on the
	        // corresponding name in the META-INF/persistence.xml file
//	        EntityManagerFactory factory = Persistence.
//	            createEntityManagerFactory(RS.rbLabel("persistent.unit"), System.getProperties());

	        // Create a new EntityManager from the EntityManagerFactory. The
	        // EntityManager is the main object in the persistence API, and is
	        // used to create, delete, and query objects, as well as access
	        // the current transaction
	        EntityManager em = WebServer.getEmFactory().createEntityManager();
	        OpenJPAEntityManager oem = OpenJPAPersistence.cast(em);
	        Connection conn = (Connection) oem.getConnection();
	        boolean autoCommit = conn.getAutoCommit();
	        
	        try {
	        	// http://stackoverflow.com/questions/2131798/jetty-mysql-connection-pool-configuration-error-javax-naming-namenotfoundexcept
	        	final Context ic = (Context) new InitialContext().lookup("java:comp");
	    		listContext(ic, "");
	        } catch (Throwable t) {
	        	log.info("JNDI lookup error: ", t);
	        }

	        // Begin a new local transaction so that we can persist a new entity
//	        em.getTransaction().begin();

	        // Create and persist a new Message entity
	        //Query q1 = em.createNativeQuery("SET LOCK_TIMEOUT 1");
	        //Object o = q1.executeUpdate();
	        final Message msg = new Message("Hello Persistence! " + System.currentTimeMillis());
	        em.persist(msg);

	        // Commit the transaction, which will cause the entity to
	        // be stored in the database
//	        em.getTransaction().commit();

	        // It is always good practice to close the EntityManager so that
	        // resources are conserved.
	        em.close();

	        // Create a fresh, new EntityManager
	        EntityManager em2 = WebServer.getEmFactory().createEntityManager();

	        // Perform a simple query for all the Message entities
	        Query q = em2.createQuery("select m from Message m");

	        // Go through each of the entities and print out each of their
	        // messages, as well as the date on which it was created 
	        for (Message m : (List<Message>) q.getResultList()) {
	            UGateUtil.PLAIN_LOGGER.info(m.getMessage() + " (created on: " + m.getCreated() + ')');
	            response.getWriter().println("<h3>" + m.getMessage() + " (created on: " + m.getCreated() + ')' + "</h3>");
	        }

	        // Again, it is always good to clean up after ourselves
	        em2.close();
//	        factory.close();
		} catch (Throwable t) {
			log.error("JPA error: ", t);
		}
	}

	private static final void listContext(Context ctx, String indent) {
	    try {
	        NamingEnumeration<?> list = ctx.listBindings("");
	        while (list.hasMore()) {
	            Binding item = (Binding) list.next();
	            String className = item.getClassName();
	            String name = item.getName();
	            System.out.println(indent + className + " " + name);
	            Object o = item.getObject();
	            if (o instanceof javax.naming.Context) {
	                listContext((Context) o, indent + " ");
	            }
	        }
	    } catch (NamingException ex) {
	        System.out.println(ex);
	    }
	}

}
