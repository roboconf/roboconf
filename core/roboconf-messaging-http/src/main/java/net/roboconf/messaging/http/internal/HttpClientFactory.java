/**
 * Copyright 2014-2015 Linagora, Université Joseph Fourier, Floralis
 *
 * The present code is developed in the scope of the joint LINAGORA -
 * Université Joseph Fourier - Floralis research program and is designated
 * as a "Result" pursuant to the terms and conditions of the LINAGORA
 * - Université Joseph Fourier - Floralis research program. Each copyright
 * holder of Results enumerated here above fully & independently holds complete
 * ownership of the complete Intellectual Property rights applicable to the whole
 * of said Results, and may freely exploit it in any manner which does not infringe
 * the moral rights of the other copyright holders.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.roboconf.messaging.http.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Logger;

import net.roboconf.core.utils.Utils;
import net.roboconf.messaging.api.extensions.IMessagingClient;
import net.roboconf.messaging.api.extensions.MessagingContext.RecipientKind;
import net.roboconf.messaging.api.factory.IMessagingClientFactory;
import net.roboconf.messaging.api.reconfigurables.ReconfigurableClient;
import net.roboconf.messaging.http.HttpConstants;
import net.roboconf.messaging.http.internal.clients.HttpAgentClient;
import net.roboconf.messaging.http.internal.clients.HttpDmClient;
import net.roboconf.messaging.http.internal.clients.HttpDmClient.HttpRoutingContext;
import net.roboconf.messaging.http.internal.sockets.DmWebSocketServlet;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;

/**
 * Messaging client factory for HTTP.
 * @author Pierre-Yves Gibello - Linagora
 */
public class HttpClientFactory implements IMessagingClientFactory {

	// The created CLIENTS.
	// References to the CLIENTS are *weak*, so we never prevent their garbage collection.
	public final Set<HttpAgentClient> agentClients = Collections.newSetFromMap( new WeakHashMap<HttpAgentClient,Boolean> ());

	// We must always have one DM client. Indeed, messaging between agents relies on this client.
	// Besides, it does not need any reconfiguration. And it greatly simplifies unit tests BTW.
	// This client is only used when a servlet is registered in the HTTP service.
	public static final HttpDmClient DM_CLIENT = new HttpDmClient();

	// The logger
	private final Logger logger = Logger.getLogger( getClass().getName());

	// Injected by iPojo
	BundleContext bundleContext;
	HttpService httpService;

	String httpServerIp;
	int httpPort;



	// Setters

	public synchronized void setHttpServerIp( final String serverIp ) {
		this.httpServerIp = serverIp;
		this.logger.finer( "Server IP set to " + this.httpServerIp );
	}


	public synchronized void setHttpPort( final int port ) {
		this.httpPort = port;
		this.logger.finer( "Server port set to " + this.httpPort );
	}


	// iPojo methods


	/**
	 * The method to use when all the dependencies are resolved.
	 * <p>
	 * It means iPojo guarantees that both the manager and the HTTP
	 * service are not null.
	 * </p>
	 *
	 * @throws Exception
	 */
	public void start() throws Exception {

		// Is the DM part of the distribution?
		boolean found = false;
		for( Bundle b : this.bundleContext.getBundles()) {
			if( "net.roboconf.dm".equals( b.getSymbolicName())) {
				found = true;
				break;
			}
		}

		// If we are on an agent, we have nothing to do.
		// Otherwise, we must register a servlet.
		if( found ) {
			this.logger.fine( "iPojo registers a servlet for HTTP messaging." );

			Hashtable<String,String> initParams = new Hashtable<String,String> ();
			initParams.put( "servlet-name", "roboconf-http-messaging" );

			DmWebSocketServlet messagingServlet = new DmWebSocketServlet();
			this.httpService.registerServlet( HttpConstants.DM_SOCKET_PATH, messagingServlet, initParams, null );
		}
	}



	public void stop() {
		this.logger.fine( "iPojo unregisters a servlet for HTTP messaging." );
		resetClients( true );
	}


	@Override
	public IMessagingClient createClient( ReconfigurableClient<?> parent ) {

		IMessagingClient client;
		if( parent.getOwnerKind() == RecipientKind.DM ) {
			client = DM_CLIENT;

		} else {
			synchronized( this ) {
				client = new HttpAgentClient( parent, this.httpServerIp, this.httpPort );
			}

			this.agentClients.add((HttpAgentClient) client);
		}

		return client;
	}


	@Override
	public String getType() {
		return HttpConstants.FACTORY_HTTP;
	}


	@Override
	public boolean setConfiguration( final Map<String, String> configuration ) {

		boolean valid = HttpConstants.FACTORY_HTTP.equals( configuration.get( MESSAGING_TYPE_PROPERTY ));
		if( valid ) {
			boolean hasChanged = false;
			String ip = configuration.get( HttpConstants.HTTP_SERVER_IP );
			if( ip == null )
				ip = HttpConstants.DEFAULT_IP;

			String portAS = configuration.get( HttpConstants.HTTP_SERVER_PORT );
			int port = portAS == null ? HttpConstants.DEFAULT_PORT : Integer.valueOf( portAS );

			// Avoid unnecessary (and potentially problematic) reconfiguration if nothing has changed.
			// First we detect for changes, and set the parameters accordingly.
			synchronized( this ) {

				if( ! Objects.equals( this.httpServerIp, ip )) {
					this.httpServerIp = ip;
					hasChanged = true;
				}

				if( this.httpPort != port ) {
					this.httpPort = port;
					hasChanged = true;
				}
			}

			// Then, if changes has occurred, we reconfigure the factory. This will invalidate every created client.
			// Otherwise, if nothing has changed, we do nothing. Thus we avoid invalidating clients uselessly, and
			// prevent any message loss.
			if( hasChanged )
				reconfigure();
		}

		return valid;
	}


	public void reconfigure() {
		resetClients( false );
	}


	public static void reset() {
		DM_CLIENT.getRoutingContext().subscriptions.clear();
		((HttpRoutingContext) DM_CLIENT.getRoutingContext()).ctxToSession.clear();
	}


	/**
	 * Closes messaging clients or requests a replacement to the reconfigurable client.
	 * @param shutdown true to close, false to request...
	 */
	private void resetClients( boolean shutdown ) {

		// Only agent clients need to be reconfigured.
		// Make fresh snapshots of the CLIENTS, as we don't want to reconfigure them while holding the lock.
		final List<HttpAgentClient> clients;
		synchronized( this ) {

			// Get the snapshot.
			clients = new ArrayList<>( this.agentClients );

			// Remove the clients, new ones will be created if necessary.
			this.agentClients.clear();
		}

		// Now reconfigure all the CLIENTS.
		for( HttpAgentClient client : clients ) {
			try {
				final ReconfigurableClient<?> reconfigurable = client.getReconfigurableClient();
				if (shutdown)
					reconfigurable.closeConnection();
				else
					reconfigurable.switchMessagingType( HttpConstants.FACTORY_HTTP );

			} catch( Throwable t ) {
				// Warn but continue to reconfigure the next CLIENTS!
				this.logger.warning( "A client has thrown an exception on reconfiguration: " + client );
				Utils.logException( this.logger, new RuntimeException( t ));
			}
		}
	}
}