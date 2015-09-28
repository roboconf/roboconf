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

package net.roboconf.messaging.http;

import java.util.concurrent.LinkedBlockingQueue;

import junit.framework.Assert;
import net.roboconf.messaging.api.messages.Message;
import net.roboconf.messaging.http.internal.HttpAgentClient;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Pierre-Yves Gibello - Linagora
 */
public class AgentClientTest {

	@Before
	public void runWebServer() {
		HttpTestUtils.runWebServer();
	}

	@Test
	public void testConnectAndDisconnect() throws Exception {

		HttpAgentClient agentClient = new HttpAgentClient(null, "localhost", "8080" );
		agentClient.setApplicationName( "app" );
		agentClient.setScopedInstancePath( "/root" );

		Assert.assertFalse( agentClient.isConnected());

		LinkedBlockingQueue<Message> messagesQueue = new LinkedBlockingQueue<>();
		agentClient.setMessageQueue( messagesQueue );
		agentClient.openConnection();
		Assert.assertTrue( agentClient.isConnected());

		agentClient.closeConnection();
		Assert.assertFalse(agentClient.isConnected());

		// closeConnection is idem-potent
		agentClient.closeConnection();
		Assert.assertFalse(agentClient.isConnected());
	}
}


