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

package net.roboconf.dm.management.legacy;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;

import junit.framework.Assert;
import net.roboconf.core.Constants;
import net.roboconf.core.internal.tests.TestApplication;
import net.roboconf.core.internal.tests.TestUtils;
import net.roboconf.core.model.beans.ApplicationTemplate;
import net.roboconf.core.model.beans.Instance;
import net.roboconf.core.model.beans.Instance.InstanceStatus;
import net.roboconf.core.model.helpers.InstanceHelpers;
import net.roboconf.core.utils.Utils;
import net.roboconf.dm.internal.environment.messaging.DmMessageProcessor;
import net.roboconf.dm.internal.test.TestManagerWrapper;
import net.roboconf.dm.internal.test.TestTargetResolver;
import net.roboconf.dm.internal.utils.ConfigurationUtils;
import net.roboconf.dm.management.ManagedApplication;
import net.roboconf.dm.management.Manager;
import net.roboconf.dm.management.events.IDmListener;
import net.roboconf.dm.management.exceptions.AlreadyExistingException;
import net.roboconf.dm.management.exceptions.ImpossibleInsertionException;
import net.roboconf.dm.management.exceptions.UnauthorizedActionException;
import net.roboconf.messaging.api.MessagingConstants;
import net.roboconf.messaging.api.internal.client.test.TestClientDm;
import net.roboconf.messaging.api.messages.Message;
import net.roboconf.messaging.api.messages.from_agent_to_dm.MsgNotifHeartbeat;
import net.roboconf.messaging.api.messages.from_agent_to_dm.MsgNotifMachineDown;
import net.roboconf.messaging.api.messages.from_dm_to_agent.MsgCmdRemoveInstance;
import net.roboconf.messaging.api.messages.from_dm_to_agent.MsgCmdResynchronize;
import net.roboconf.messaging.api.messages.from_dm_to_agent.MsgCmdSetScopedInstance;
import net.roboconf.target.api.TargetHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/**
 * @author Vincent Zurczak - Linagora
 * @author Pierre Bourret - Université Joseph Fourier
 */
public class Manager_BasicsTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Manager manager;
	private TestManagerWrapper managerWrapper;
	private TestClientDm msgClient;
	private TestTargetResolver targetResolver;


	@Before
	public void resetManager() throws Exception {

		File directory = this.folder.newFolder();
		this.targetResolver = new TestTargetResolver();

		this.manager = new Manager();
		this.manager.setTargetResolver( this.targetResolver );
		this.manager.configurationMngr().setWorkingDirectory( directory );
		this.manager.setMessagingType(MessagingConstants.TEST_FACTORY_TYPE);
		this.manager.start();

		// Register mocked listeners - mainly for code coverage reasons
		this.manager.listenerAppears( Mockito.mock( IDmListener.class ));

		// Create the wrapper
		this.managerWrapper = new TestManagerWrapper( this.manager );
		this.managerWrapper.configureMessagingForTest();
		this.manager.reconfigure();

		this.msgClient = (TestClientDm) this.managerWrapper.getInternalMessagingClient();
		this.msgClient.sentMessages.clear();

		// Disable the messages timer for predictability
		TestUtils.getInternalField( this.manager, "timer", Timer.class ).cancel();
	}


	@After
	public void stopManager() throws Exception {
		this.manager.stop();

		// Some tests create a new manager, which save instances
		// at the current project's root when it is stopped.
		File dir = new File( "./applications" );
		Utils.deleteFilesRecursively( dir );
	}


	@Test
	public void testStop() throws Exception {

		Timer timer = TestUtils.getInternalField( this.manager, "timer", Timer.class );
		Assert.assertNotNull( timer );
		this.manager.stop();

		timer = TestUtils.getInternalField( this.manager, "timer", Timer.class );
		Assert.assertNull( timer );

		this.manager.stop();
		Assert.assertNull( timer );
	}


	@Test
	public void testStop_invalidConfiguration() throws Exception {

		this.manager = new Manager();
		this.managerWrapper = new TestManagerWrapper( this.manager );

		Timer timer = TestUtils.getInternalField( this.manager, "timer", Timer.class );
		Assert.assertNull( timer );

		this.manager.stop();
		timer = TestUtils.getInternalField( this.manager, "timer", Timer.class );
		Assert.assertNull( timer );
	}


	@Test
	public void testStop_messagingException() throws Exception {

		this.msgClient.failListeningToTheDm.set( true );
		this.msgClient.failClosingConnection.set( true );

		Timer timer = TestUtils.getInternalField( this.manager, "timer", Timer.class );
		Assert.assertNotNull( timer );

		this.manager.stop();
		timer = TestUtils.getInternalField( this.manager, "timer", Timer.class );
		Assert.assertNull( timer );
	}


	@Test( expected = ImpossibleInsertionException.class )
	public void testAddInstance_impossibleInsertion_rootInstance() throws Exception {

		TestApplication app = new TestApplication();
		app.setDirectory( this.folder.newFolder());
		ManagedApplication ma = new ManagedApplication( app );

		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );

		String existingInstanceName = app.getMySqlVm().getName();
		this.manager.instancesMngr().addInstance( ma, null, new Instance( existingInstanceName ));
	}


	@Test( expected = ImpossibleInsertionException.class )
	public void testAddInstance_impossibleInsertion_childInstance() throws Exception {

		TestApplication app = new TestApplication();
		app.setDirectory( this.folder.newFolder());
		ManagedApplication ma = new ManagedApplication( app );

		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );

		String existingInstanceName = app.getMySql().getName();
		this.manager.instancesMngr().addInstance( ma, app.getMySqlVm(), new Instance( existingInstanceName ));
	}


	@Test
	public void testAddInstance_successRoot() throws Exception {

		TestApplication app = new TestApplication();
		app.setDirectory( this.folder.newFolder());
		ManagedApplication ma = new ManagedApplication( app );

		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );

		Assert.assertEquals( 2, app.getRootInstances().size());
		Instance newInstance = new Instance( "mail-vm" ).component( app.getMySqlVm().getComponent());

		this.manager.instancesMngr().addInstance( ma, null, newInstance );
		Assert.assertEquals( 3, app.getRootInstances().size());
		Assert.assertTrue( app.getRootInstances().contains( newInstance ));
	}


	@Test( expected = IOException.class )
	public void testAddInstance_invalidConfiguration() throws Exception {

		TestApplication app = new TestApplication();
		app.setDirectory( this.folder.newFolder());
		ManagedApplication ma = new ManagedApplication( app );

		Assert.assertEquals( 2, app.getRootInstances().size());
		Instance newInstance = new Instance( "mail-vm" ).component( app.getMySqlVm().getComponent());

		this.manager = new Manager();
		this.managerWrapper = new TestManagerWrapper( this.manager );

		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );
		this.manager.instancesMngr().addInstance( ma, null, newInstance );
	}


	@Test
	public void testAddInstance_successChild() throws Exception {

		TestApplication app = new TestApplication();
		app.setDirectory( this.folder.newFolder());
		ManagedApplication ma = new ManagedApplication( app );

		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );

		// Insert a MySQL instance under the Tomcat VM
		Assert.assertEquals( 1, app.getTomcatVm().getChildren().size());
		Instance newInstance = new Instance( app.getMySql().getName()).component( app.getMySql().getComponent());

		this.manager.instancesMngr().addInstance( ma, app.getTomcatVm(), newInstance );
		Assert.assertEquals( 2, app.getTomcatVm().getChildren().size());
		Assert.assertTrue( app.getTomcatVm().getChildren().contains( newInstance ));
	}


	@Test( expected = UnauthorizedActionException.class )
	public void testRemoveInstance_unauthorized() throws Exception {

		TestApplication app = new TestApplication();
		ManagedApplication ma = new ManagedApplication( app );
		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );

		app.getMySql().setStatus( InstanceStatus.DEPLOYED_STARTED );
		this.manager.instancesMngr().removeInstance( ma, app.getMySqlVm());
	}


	@Test
	public void testRemoveInstance_success_1() throws Exception {

		TestApplication app = new TestApplication();
		ManagedApplication ma = new ManagedApplication( app );
		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );

		Assert.assertEquals( 2, app.getRootInstances().size());
		Assert.assertEquals( 0, ma.getScopedInstanceToAwaitingMessages().size());

		this.manager.instancesMngr().removeInstance( ma, app.getTomcatVm());

		Assert.assertEquals( 1, app.getRootInstances().size());
		Assert.assertEquals( app.getMySqlVm(), app.getRootInstances().iterator().next());
		Assert.assertEquals( 1, ma.getScopedInstanceToAwaitingMessages().size());

		List<Message> messages = ma.getScopedInstanceToAwaitingMessages().get( app.getTomcatVm());
		Assert.assertEquals( 1, messages.size());
		Assert.assertEquals( MsgCmdRemoveInstance.class, messages.get( 0 ).getClass());
		Assert.assertEquals(
				InstanceHelpers.computeInstancePath( app.getTomcatVm()),
				((MsgCmdRemoveInstance) messages.get( 0 )).getInstancePath());
	}


	@Test
	public void testRemoveInstance_success_2() throws Exception {

		TestApplication app = new TestApplication();
		ManagedApplication ma = new ManagedApplication( app );
		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );

		app.getTomcatVm().setStatus( InstanceStatus.DEPLOYED_STARTED );
		Assert.assertEquals( 2, app.getRootInstances().size());
		Assert.assertEquals( 0, ma.getScopedInstanceToAwaitingMessages().size());

		this.manager.instancesMngr().removeInstance( ma, app.getTomcat());

		Assert.assertEquals( 2, app.getRootInstances().size());
		Assert.assertEquals( app.getMySqlVm(), app.getRootInstances().iterator().next());
		Assert.assertEquals( 1, this.msgClient.sentMessages.size());
		Assert.assertEquals( MsgCmdRemoveInstance.class, this.msgClient.sentMessages.get( 0 ).getClass());

		MsgCmdRemoveInstance msg = (MsgCmdRemoveInstance) this.msgClient.sentMessages.get( 0 );
		Assert.assertEquals( InstanceHelpers.computeInstancePath( app.getTomcat()), msg.getInstancePath());
	}


	@Test( expected = IOException.class )
	public void testRemoveInstance_invalidConfiguration() throws Exception {

		TestApplication app = new TestApplication();
		ManagedApplication ma = new ManagedApplication( app );

		app.getTomcatVm().setStatus( InstanceStatus.DEPLOYED_STARTED );
		Assert.assertEquals( 2, app.getRootInstances().size());
		Assert.assertEquals( 0, ma.getScopedInstanceToAwaitingMessages().size());

		this.manager = new Manager();
		this.managerWrapper = new TestManagerWrapper( this.manager );

		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );
		this.manager.instancesMngr().removeInstance( ma, app.getTomcat());
	}


	@Test
	public void testCreateApplication_withTags() throws Exception {

		File directory = TestUtils.findApplicationDirectory( "lamp" );
		Assert.assertTrue( directory.exists());
		Assert.assertEquals( 0, this.manager.applicationTemplateMngr().getApplicationTemplates().size());

		ApplicationTemplate tpl = this.manager.applicationTemplateMngr().loadApplicationTemplate( directory );
		Assert.assertNotNull( tpl );
		Assert.assertEquals( 1, this.manager.applicationTemplateMngr().getApplicationTemplates().size());

		Assert.assertEquals( 0, this.managerWrapper.getNameToManagedApplication().size());
		ManagedApplication ma = this.manager.applicationMngr().createApplication( "toto", "desc", tpl.getName(), tpl.getQualifier());
		Assert.assertNotNull( ma );
		Assert.assertEquals( 1, this.managerWrapper.getNameToManagedApplication().size());

		Assert.assertEquals( ma.getDirectory().getName(), ma.getName());
		Assert.assertEquals( "toto", ma.getName());

		File expected = new File( this.manager.configurationMngr().getWorkingDirectory(), ConfigurationUtils.APPLICATIONS );
		Assert.assertEquals( expected, ma.getDirectory().getParentFile());
	}


	@Test( expected = AlreadyExistingException.class )
	public void testCreateApplication_conflict() throws Exception {

		File directory = TestUtils.findApplicationDirectory( "lamp" );
		Assert.assertTrue( directory.exists());
		Assert.assertEquals( 0, this.manager.applicationTemplateMngr().getApplicationTemplates().size());

		ApplicationTemplate tpl = this.manager.applicationTemplateMngr().loadApplicationTemplate( directory );
		Assert.assertNotNull( tpl );
		Assert.assertEquals( 1, this.manager.applicationTemplateMngr().getApplicationTemplates().size());

		Assert.assertEquals( 0, this.managerWrapper.getNameToManagedApplication().size());
		ManagedApplication ma = this.manager.applicationMngr().createApplication( "toto", "desc", tpl.getName(), tpl.getQualifier());
		Assert.assertNotNull( ma );
		Assert.assertEquals( 1, this.managerWrapper.getNameToManagedApplication().size());

		this.manager.applicationMngr().createApplication( "toto", "desc", tpl );
	}


	@Test
	public void testLoadNewApplication_success() throws Exception {

		File directory = TestUtils.findApplicationDirectory( "lamp" );
		Assert.assertTrue( directory.exists());
		Assert.assertEquals( 0, this.manager.applicationTemplateMngr().getApplicationTemplates().size());

		ApplicationTemplate tpl = this.manager.applicationTemplateMngr().loadApplicationTemplate( directory );
		Assert.assertNotNull( tpl );
		Assert.assertEquals( 1, this.manager.applicationTemplateMngr().getApplicationTemplates().size());

		Assert.assertEquals( 0, this.managerWrapper.getNameToManagedApplication().size());
		ManagedApplication ma = this.manager.applicationMngr().createApplication( "toto", "desc", tpl );
		Assert.assertNotNull( ma );
		Assert.assertEquals( 1, this.managerWrapper.getNameToManagedApplication().size());

		Assert.assertEquals( ma.getDirectory().getName(), ma.getName());
		Assert.assertEquals( "toto", ma.getApplication().getName());

		File expected = new File( this.manager.configurationMngr().getWorkingDirectory(), ConfigurationUtils.APPLICATIONS );
		Assert.assertEquals( expected, ma.getDirectory().getParentFile());
	}


	@Test
	public void testLoadApplicationTemplate_invalidConfiguration() throws Exception {
		this.manager = new Manager();
		this.managerWrapper = new TestManagerWrapper( this.manager );

		// No messaging is configured
		try {
			this.manager.messagingMngr().checkMessagingConfiguration();
			Assert.fail( "The configuration is supposed to be invalid." );

		} catch( IOException e ) {
			// nothing
		}

		File directory = TestUtils.findApplicationDirectory( "lamp" );
		Assert.assertTrue( directory.exists());
		this.manager.applicationTemplateMngr().loadApplicationTemplate( directory );
	}


	@Test
	public void testConfigurationChanged_withApps_noInstanceDeployed() throws Exception {

		// Copy an application in the configuration
		File source = TestUtils.findApplicationDirectory( "lamp" );
		ApplicationTemplate tpl = this.manager.applicationTemplateMngr().loadApplicationTemplate( source );
		this.manager.applicationMngr().createApplication( "lamp3", "test", tpl );

		// Reset the manager's configuration (simply reload it)
		this.manager.reconfigure();
		this.manager.messagingMngr().checkMessagingConfiguration();

		// Check there is an application
		Assert.assertEquals( 1, this.managerWrapper.getNameToManagedApplication().size());
		ManagedApplication ma = this.managerWrapper.getNameToManagedApplication().get( "lamp3" );
		Assert.assertNotNull( ma );
		Assert.assertEquals( 3, ma.getApplication().getRootInstances().size());
		Assert.assertEquals( 6, InstanceHelpers.getAllInstances( ma.getApplication()).size());

		for( Instance inst : InstanceHelpers.getAllInstances( ma.getApplication()))
			Assert.assertEquals( InstanceStatus.NOT_DEPLOYED, inst.getStatus());
	}


	@Test
	public void testConfigurationChanged_andShutdown_withApps_withInstances() throws Exception {

		// Copy an application in the configuration
		File source = TestUtils.findApplicationDirectory( "lamp" );
		ApplicationTemplate tpl = this.manager.applicationTemplateMngr().loadApplicationTemplate( source );
		ManagedApplication ma = this.manager.applicationMngr().createApplication( "lamp", "test", tpl );
		Instance apache = InstanceHelpers.findInstanceByPath( ma.getApplication(), "/Apache VM" );
		Assert.assertNotNull( apache );
		String path = InstanceHelpers.computeInstancePath( apache );

		// Make sure the VM is considered as deployed in the pseudo-IaaS
		TargetHandler th = this.targetResolver.findTargetHandler( new HashMap<String,String>( 0 ));
		Assert.assertNotNull( th );
		th.createMachine( null, null, path, ma.getName());

		// Update the instances
		apache.data.put( Instance.IP_ADDRESS, "192.168.1.23" );
		apache.data.put( Instance.MACHINE_ID, path );
		apache.data.put( "whatever", "something" );
		apache.setStatus( InstanceStatus.PROBLEM );

		// Save the manager's state
		this.manager.stop();

		// Reset the manager (reload the configuration)
		this.manager.reconfigure();

		// Check there is the right application
		Assert.assertEquals( 1, this.managerWrapper.getNameToManagedApplication().size());
		ma = this.managerWrapper.getNameToManagedApplication().get( "lamp" );
		Assert.assertNotNull( ma );
		Assert.assertEquals( 3, ma.getApplication().getRootInstances().size());
		Assert.assertEquals( 6, InstanceHelpers.getAllInstances( ma.getApplication()).size());

		for( Instance inst : InstanceHelpers.getAllInstances( ma.getApplication())) {
			if( ! inst.equals( apache ))
				Assert.assertEquals( InstanceStatus.NOT_DEPLOYED, inst.getStatus());
		}

		apache = InstanceHelpers.findInstanceByPath( ma.getApplication(), "/Apache VM" );
		Assert.assertEquals( "192.168.1.23", apache.data.get( Instance.IP_ADDRESS ));
		Assert.assertEquals( path, apache.data.get( Instance.MACHINE_ID ));
		Assert.assertEquals( "something", apache.data.get( "whatever" ));
		Assert.assertEquals( ma.getName(), apache.data.get( Instance.APPLICATION_NAME ));

		// It is considered started because upon a reconfiguration, the IaaS is contacted
		// to determine whether a VM runs or not.
		Assert.assertEquals( InstanceStatus.DEPLOYED_STARTED, apache.getStatus());
	}


	@Test
	public void testConfigurationChanged_andShutdown_withApps_withInstances_vmWasKilled() throws Exception {

		// Copy an application in the configuration
		File source = TestUtils.findApplicationDirectory( "lamp" );
		ApplicationTemplate tpl = this.manager.applicationTemplateMngr().loadApplicationTemplate( source );
		ManagedApplication ma = this.manager.applicationMngr().createApplication( "lamp2", "test", tpl );
		Instance apache = InstanceHelpers.findInstanceByPath( ma.getApplication(), "/Apache VM" );
		Assert.assertNotNull( apache );
		String path = InstanceHelpers.computeInstancePath( apache );

		// Make sure the VM is considered as deployed in the pseudo-IaaS
		TargetHandler th = this.targetResolver.findTargetHandler( new HashMap<String,String>( 0 ));
		Assert.assertNotNull( th );
		String machineId = th.createMachine( null, null, path, ma.getName());

		// Update the instances
		apache.data.put( Instance.IP_ADDRESS, "192.168.1.23" );
		apache.data.put( Instance.MACHINE_ID, path );
		apache.data.put( "whatever", "something" );
		apache.setStatus( InstanceStatus.PROBLEM );

		// Save the manager's state
		this.manager.stop();

		//
		// Here is the difference with #testConfigurationChanged_andShutdown_withApps_withInstances
		// We simulate the fact that the VM was killed while the DM was stopped.
		//
		th.terminateMachine( null, machineId );

		// Reset the manager (reload the configuration)
		this.manager.reconfigure();

		// Check there is the right application
		Assert.assertEquals( 1, this.managerWrapper.getNameToManagedApplication().size());
		ma = this.managerWrapper.getNameToManagedApplication().get( "lamp2" );
		Assert.assertNotNull( ma );
		Assert.assertEquals( 3, ma.getApplication().getRootInstances().size());
		Assert.assertEquals( 6, InstanceHelpers.getAllInstances( ma.getApplication()).size());

		for( Instance inst : InstanceHelpers.getAllInstances( ma.getApplication())) {
			if( ! inst.equals( apache ))
				Assert.assertEquals( InstanceStatus.NOT_DEPLOYED, inst.getStatus());
		}

		apache = InstanceHelpers.findInstanceByPath( ma.getApplication(), "/Apache VM" );
		Assert.assertNull( apache.data.get( Instance.IP_ADDRESS ));
		Assert.assertNull( apache.data.get( Instance.MACHINE_ID ));
		Assert.assertEquals( "something", apache.data.get( "whatever" ));
		Assert.assertEquals( ma.getName(), apache.data.get( Instance.APPLICATION_NAME ));

		// The VM was killed outside the DM. Upon restoration, the DM
		// contacts the IaaS and sets the NOT_DEPLOYED status.
		Assert.assertEquals( InstanceStatus.NOT_DEPLOYED, apache.getStatus());
	}


	@Test
	public void testResynchronizeAgents_withConnection() throws Exception {

		TestApplication app = new TestApplication();
		ManagedApplication ma = new ManagedApplication( app );

		this.manager.instancesMngr().resynchronizeAgents( ma );
		Assert.assertEquals( 0, this.msgClient.sentMessages.size());

		app.getTomcatVm().setStatus( InstanceStatus.DEPLOYED_STARTED );
		app.getMySqlVm().setStatus( InstanceStatus.DEPLOYING );
		this.manager.instancesMngr().resynchronizeAgents( ma );
		Assert.assertEquals( 1, this.msgClient.sentMessages.size());
		Assert.assertEquals( MsgCmdResynchronize.class, this.msgClient.sentMessages.get( 0 ).getClass());

		this.msgClient.sentMessages.clear();
		app.getMySqlVm().setStatus( InstanceStatus.DEPLOYED_STARTED );
		this.manager.instancesMngr().resynchronizeAgents( ma );
		Assert.assertEquals( 2, this.msgClient.sentMessages.size());
		Assert.assertEquals( MsgCmdResynchronize.class, this.msgClient.sentMessages.get( 0 ).getClass());
		Assert.assertEquals( MsgCmdResynchronize.class, this.msgClient.sentMessages.get( 1 ).getClass());
	}


	@Test( expected = IOException.class )
	public void testResynchronizeAgents_noConnection() throws Exception {

		TestApplication app = new TestApplication();
		ManagedApplication ma = new ManagedApplication( app );

		this.managerWrapper.getMessagingClient().closeConnection();
		app.getTomcatVm().setStatus( InstanceStatus.DEPLOYED_STARTED );
		app.getMySqlVm().setStatus( InstanceStatus.DEPLOYED_STARTED );

		this.manager.instancesMngr().resynchronizeAgents( ma );
	}


	@Test( expected = IOException.class )
	public void testResynchronizeAgents_invalidConfiguration() throws Exception {

		TestApplication app = new TestApplication();
		ManagedApplication ma = new ManagedApplication( app );
		app.getTomcatVm().setStatus( InstanceStatus.DEPLOYED_STARTED );
		app.getMySqlVm().setStatus( InstanceStatus.DEPLOYED_STARTED );

		this.manager = new Manager();
		this.managerWrapper = new TestManagerWrapper( this.manager );
		this.manager.instancesMngr().resynchronizeAgents( ma );
	}


	@Test
	public void testMsgNotifHeartbeat_requestModel() throws Exception {

		TestApplication app = new TestApplication();
		ManagedApplication ma = new ManagedApplication( app );
		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );

		this.msgClient.sentMessages.clear();
		Assert.assertEquals( 0, this.msgClient.sentMessages.size());

		MsgNotifHeartbeat msg = new MsgNotifHeartbeat( app.getName(), app.getMySqlVm(), "192.168.1.45" );
		msg.setModelRequired( true );

		this.managerWrapper.getMessagingClient().getMessageProcessor().storeMessage( msg );
		Thread.sleep( 100 );
		Assert.assertEquals( 1, this.msgClient.sentMessages.size());

		Message sentMessage = this.msgClient.sentMessages.get( 0 );
		Assert.assertEquals( MsgCmdSetScopedInstance.class, sentMessage.getClass());
		Assert.assertNotNull(((MsgCmdSetScopedInstance) sentMessage).getScopedInstance());
	}


	@Test
	public void testMsgNotifHeartbeat_requestModel_nonRoot() throws Exception {

		TestApplication app = new TestApplication();
		ManagedApplication ma = new ManagedApplication( app );
		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );

		this.msgClient.sentMessages.clear();
		Assert.assertEquals( 0, this.msgClient.sentMessages.size());

		// War is not a target / scoped instance: nothing will happen
		MsgNotifHeartbeat msg = new MsgNotifHeartbeat( app.getName(), app.getWar(), "192.168.1.45" );
		msg.setModelRequired( true );

		this.managerWrapper.getMessagingClient().getMessageProcessor().storeMessage( msg );
		Thread.sleep( 100 );
		Assert.assertEquals( 0, this.msgClient.sentMessages.size());

		// Let's try again, but we change the WAR installer
		app.getWar().getComponent().installerName( Constants.TARGET_INSTALLER );

		this.managerWrapper.getMessagingClient().getMessageProcessor().storeMessage( msg );
		Thread.sleep( 100 );
		Assert.assertEquals( 1, this.msgClient.sentMessages.size());

		Message sentMessage = this.msgClient.sentMessages.get( 0 );
		Assert.assertEquals( MsgCmdSetScopedInstance.class, sentMessage.getClass());
		Assert.assertNotNull(((MsgCmdSetScopedInstance) sentMessage).getScopedInstance());
	}


	@Test
	public void applicationsShouldBeDeletedEvenWhenNoMessagingServer() throws Exception {

		this.manager = new Manager();
		this.managerWrapper = new TestManagerWrapper( this.manager );

		TestApplication app = new TestApplication();
		app.setDirectory( this.folder.newFolder());
		ManagedApplication ma = new ManagedApplication( app );

		Assert.assertEquals( 0, this.managerWrapper.getNameToManagedApplication().size());
		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );
		Assert.assertEquals( 1, this.managerWrapper.getNameToManagedApplication().size());

		try {
			this.manager.messagingMngr().checkMessagingConfiguration();
			Assert.fail( "An exception should have been thrown, there is no messaging server in this test!" );

		} catch( Exception e ) {
			// ignore
		}

		this.manager.applicationMngr().deleteApplication( ma );
		Assert.assertEquals( 0, this.managerWrapper.getNameToManagedApplication().size());
	}


	@Test
	public void testSomeGetters() throws Exception {

		Assert.assertEquals( 0, this.manager.applicationTemplateMngr().getApplicationTemplates().size());
		Assert.assertNull( this.manager.applicationMngr().findApplicationByName( "invalid" ));

		TestApplication app = new TestApplication();
		ManagedApplication ma = new ManagedApplication( app );
		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );

		Assert.assertEquals( app, this.manager.applicationMngr().findApplicationByName( app.getName()));
	}


	@Test
	public void verifyMsgNotifMachineDown_allowsRedeployment() throws Exception {

		// Prepare the model
		TestApplication app = new TestApplication();
		app.setDirectory( this.folder.newFolder());
		ManagedApplication ma = new ManagedApplication( app );

		this.managerWrapper.getNameToManagedApplication().put( app.getName(), ma );
		String targetId = this.manager.targetsMngr().createTarget( "" );
		this.manager.targetsMngr().associateTargetWithScopedInstance( targetId, app, null );

		// Try a first deployment
		Assert.assertEquals( InstanceStatus.NOT_DEPLOYED, app.getMySqlVm().getStatus());
		this.manager.instancesMngr().deployAndStartAll( ma, app.getMySqlVm());
		Assert.assertEquals( InstanceStatus.DEPLOYING, app.getMySqlVm().getStatus());

		// Simulate the incoming of a heart beat message
		DmMessageProcessor processor = (DmMessageProcessor) this.managerWrapper.getMessagingClient().getMessageProcessor();
		processor.processMessage( new MsgNotifHeartbeat( app.getName(), app.getMySqlVm(), "127.0.0.1" ));
		Assert.assertEquals( InstanceStatus.DEPLOYED_STARTED, app.getMySqlVm().getStatus());

		// Simulate the incoming of a "machine down" notification
		processor.processMessage( new MsgNotifMachineDown( app.getName(), app.getMySqlVm()));
		Assert.assertEquals( InstanceStatus.NOT_DEPLOYED, app.getMySqlVm().getStatus());

		// Try to redeploy: it should work (no remains of the previous attempt)
		this.manager.instancesMngr().deployAndStartAll( ma, app.getMySqlVm());
		Assert.assertEquals( InstanceStatus.DEPLOYING, app.getMySqlVm().getStatus());
	}
}
