/**
 * Copyright 2014-2016 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.agent.internal;

import org.junit.Assert;
import net.roboconf.agent.internal.misc.PluginMock;
import net.roboconf.core.internal.tests.TestUtils;
import net.roboconf.core.model.beans.Instance;
import net.roboconf.messaging.api.MessagingConstants;
import net.roboconf.messaging.api.extensions.IMessagingClient;
import net.roboconf.messaging.api.factory.MessagingClientFactoryRegistry;
import net.roboconf.messaging.api.internal.client.test.TestClient;
import net.roboconf.messaging.api.internal.client.test.TestClientFactory;
import net.roboconf.messaging.api.messages.from_agent_to_dm.MsgNotifAutonomic;
import net.roboconf.messaging.api.messages.from_agent_to_dm.MsgNotifHeartbeat;
import net.roboconf.messaging.api.messages.from_agent_to_dm.MsgNotifLogs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author Vincent Zurczak - Linagora
 */
public class Agent_BasicsTest {

	private Agent agent;
	private MessagingClientFactoryRegistry registry;


	@Before
	public void initializeAgent() throws Exception {

		this.registry = new MessagingClientFactoryRegistry();
		this.registry.addMessagingClientFactory(new TestClientFactory());
		this.agent = new Agent();

		// We first need to start the agent, so it creates the reconfigurable messaging client.
		this.agent.setMessagingType(MessagingConstants.FACTORY_TEST);
		this.agent.start();

		// We then set the factory registry of the created client, and reconfigure the agent, so the messaging client backend is created.
		this.agent.getMessagingClient().setRegistry(this.registry);
		this.agent.reconfigure();

		Thread.sleep( 200 );
		getInternalClient().messagesForTheDm.clear();
	}


	@After
	public void stopAgent() {
		this.agent.stop();
	}


	@Test
	public void tryToStopNotStartedAgent() {
		stopAgent();
		this.agent = new Agent();
		stopAgent();
	}


	@Test
	public void testForceHeartbeatSending() throws Exception {

		TestClient client = getInternalClient();
		Assert.assertEquals( 0, client.messagesForTheDm.size());

		this.agent.forceHeartbeatSending();
		Assert.assertEquals( 1, client.messagesForTheDm.size());
		Assert.assertEquals( MsgNotifHeartbeat.class, client.messagesForTheDm.get( 0 ).getClass());
	}


	@Test
	public void testBasicStartAndStop() throws Exception {

		TestClient client = getInternalClient();

		// Stop when not running => no problem
		Assert.assertTrue( client.isConnected());
		this.agent.stop();
		Assert.assertFalse( client.isConnected());

		// Start
		this.agent.start();
		this.agent.getMessagingClient().setRegistry(this.registry);
		this.agent.reconfigure();
		client = getInternalClient();
		Assert.assertTrue( client.isConnected());

		// Start when already started => nothing
		IMessagingClient oldClient = client;
		this.agent.start();
		this.agent.getMessagingClient().setRegistry(this.registry);
		this.agent.reconfigure();
		client = getInternalClient();
		Assert.assertTrue( client.isConnected());
		Assert.assertNotSame( oldClient, client );

		// Stop and start again
		this.agent.stop();
		Assert.assertFalse( client.isConnected());

		this.agent.start();
		this.agent.getMessagingClient().setRegistry(this.registry);
		this.agent.reconfigure();
		client = getInternalClient();
		Assert.assertTrue( client.isConnected());

		this.agent.stop();
		Assert.assertFalse( client.isConnected());

		// Stop called twice => nothing
		this.agent.stop();
		Assert.assertFalse( client.isConnected());
	}


	@Test
	public void testStop_withException() throws Exception {

		TestClient client = getInternalClient();
		client.failMessageSending.set( true );
		Assert.assertTrue( client.isConnected());

		this.agent.stop();
		Assert.assertFalse( client.isConnected());
	}


	@Test
	public void testStop_notConnected() throws Exception {

		TestClient client = getInternalClient();
		client.closeConnection();
		Assert.assertFalse( client.isConnected());

		this.agent.stop();
		Assert.assertFalse( client.isConnected());
	}


	@Test
	public void testGetAgentId() {

		Agent agent = new Agent();
		Assert.assertFalse( agent.getAgentId().contains( "null" ));

		agent.applicationName = "my app";
		Assert.assertFalse( agent.getAgentId().contains( "null" ));
		Assert.assertTrue( agent.getAgentId().contains( "my app" ));

		agent.scopedInstancePath = "/root";
		Assert.assertFalse( agent.getAgentId().contains( "null" ));
		Assert.assertTrue( agent.getAgentId().contains( "my app" ));
		Assert.assertTrue( agent.getAgentId().contains( "/root" ));

		agent.applicationName = null;
		Assert.assertFalse( agent.getAgentId().contains( "null" ));
		Assert.assertFalse( agent.getAgentId().contains( "my app" ));
		Assert.assertTrue( agent.getAgentId().contains( "/root" ));
	}


	@Test
	public void testNeedModel_startedButNothingReceived() {
		Assert.assertTrue( this.agent.needsModel());
	}


	@Test
	public void testNeedModel_notStarted() {
		Agent agent = new Agent();
		Assert.assertTrue( agent.needsModel());
	}


	@Test
	public void testNeedModel_modelReceived() {
		((AgentMessageProcessor) this.agent.getMessagingClient().getMessageProcessor()).scopedInstance = new Instance( "root" );
		Assert.assertFalse( this.agent.needsModel());
	}


	@Test
	public void testMiscPlugins() {

		// No error expected.
		Agent agent = new Agent();
		agent.reconfigure();
	}


	@Test
	public void testReconfigureWhenAgentIsNotStarted() {

		Agent agent = new Agent();
		agent.pluginAppears( null );
		agent.pluginAppears( new PluginMock());
		agent.pluginAppears( new PluginMock());
		agent.listPlugins();
	}

	@Test
	public void testAgentStatus() throws Exception {

		Assert.assertNotNull( this.agent.agentStatus() );

		this.agent.stop();

		this.agent.getMessagingClient().getMessageProcessor().getMessageQueue().add(Mockito.mock(MsgNotifHeartbeat.class));
		this.agent.getMessagingClient().getMessageProcessor().getMessageQueue().add(Mockito.mock(MsgNotifAutonomic.class));
		this.agent.getMessagingClient().getMessageProcessor().getMessageQueue().add(Mockito.mock(MsgNotifLogs.class));

		Assert.assertFalse( this.agent.agentStatus().startsWith( "There is no message" ));

	}

	private TestClient getInternalClient() throws IllegalAccessException {
		return TestUtils.getInternalField( this.agent.getMessagingClient(), "messagingClient", TestClient.class );
	}
}
