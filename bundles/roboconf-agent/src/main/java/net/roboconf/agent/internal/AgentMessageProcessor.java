/**
 * Copyright 2014 Linagora, Université Joseph Fourier
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import net.roboconf.agent.internal.lifecycle.AbstractLifeCycleManager;
import net.roboconf.core.model.helpers.ComponentHelpers;
import net.roboconf.core.model.helpers.ImportHelpers;
import net.roboconf.core.model.helpers.InstanceHelpers;
import net.roboconf.core.model.helpers.VariableHelpers;
import net.roboconf.core.model.runtime.Application;
import net.roboconf.core.model.runtime.Component;
import net.roboconf.core.model.runtime.Import;
import net.roboconf.core.model.runtime.Instance;
import net.roboconf.core.model.runtime.Instance.InstanceStatus;
import net.roboconf.core.utils.Utils;
import net.roboconf.messaging.client.AbstractMessageProcessor;
import net.roboconf.messaging.client.IAgentClient;
import net.roboconf.messaging.client.IClient.ListenerCommand;
import net.roboconf.messaging.messages.Message;
import net.roboconf.messaging.messages.from_agent_to_agent.MsgCmdAddImport;
import net.roboconf.messaging.messages.from_agent_to_agent.MsgCmdRemoveImport;
import net.roboconf.messaging.messages.from_agent_to_agent.MsgCmdRequestImport;
import net.roboconf.messaging.messages.from_agent_to_dm.MsgNotifInstanceChanged;
import net.roboconf.messaging.messages.from_agent_to_dm.MsgNotifInstanceRemoved;
import net.roboconf.messaging.messages.from_agent_to_dm.MsgNotifMachineUp;
import net.roboconf.messaging.messages.from_dm_to_agent.MsgCmdAddInstance;
import net.roboconf.messaging.messages.from_dm_to_agent.MsgCmdChangeInstanceState;
import net.roboconf.messaging.messages.from_dm_to_agent.MsgCmdRemoveInstance;
import net.roboconf.messaging.messages.from_dm_to_agent.MsgCmdSendInstances;
import net.roboconf.messaging.messages.from_dm_to_agent.MsgCmdSetRootInstance;
import net.roboconf.plugin.api.PluginException;
import net.roboconf.plugin.api.PluginInterface;

/**
 * The class (thread) in charge of processing messages received by the agent.
 * @author Vincent Zurczak - Linagora
 */
public class AgentMessageProcessor extends AbstractMessageProcessor {

	private final Logger logger = Logger.getLogger( getClass().getName());
	private final AbstractAgent agent;

	Instance rootInstance;
	IAgentClient messagingClient, newMessagingClient;


	/**
	 * Constructor.
	 */
	public AgentMessageProcessor( AbstractAgent agent ) {
		super();
		this.agent = agent;
	}


	/**
	 * Sets the messaging client.
	 * @param messagingClient the messaging client
	 */
	public void setMessagingClient( IAgentClient messagingClient ) {

		// The messaging client can be changed at any moment (dynamic environment).
		// So, we can either put synchronized blocks everywhere...
		// ... or we can just wait for the current message to be processed
		// and change the client for the next one.
		this.newMessagingClient = messagingClient;

		// If there is no message to process, configure the new messaging client
		if( hasNoMessage())
			configure();
	}


	/**
	 * Configures a new messaging client.
	 */
	public synchronized void configure() {

		if( this.newMessagingClient != null ) {

			// If there was a client, release the connection
			if( this.messagingClient != null
					&& this.messagingClient.isConnected()) {

				try {
					this.messagingClient.closeConnection();

				} catch( IOException e ) {
					this.logger.severe( "An error occured while releasing a messaging client. " + e.getMessage());
					this.logger.finest( Utils.writeException( e ));
				}
			}

			try {
				// Store and configure the new client
				this.messagingClient = this.newMessagingClient;
				this.messagingClient.openConnection( this );
				this.messagingClient.listenToTheDm( ListenerCommand.START );

				// Send an "UP" message
				this.messagingClient.sendMessageToTheDm( new MsgNotifMachineUp(
						this.agent.getApplicationName(),
						this.agent.getRootInstanceName(),
						this.agent.getIpAddress()));

			} catch( IOException e ) {
				this.logger.severe( "An error occured while initializing a new messaging client. " + e.getMessage());
				this.logger.finest( Utils.writeException( e ));
			}

			// Reset the newMessagingClient variable
			this.newMessagingClient = null;
		}
	}


	/* (non-Javadoc)
	 * @see net.roboconf.messaging.client.AbstractMessageProcessor
	 * #processMessage(net.roboconf.messaging.messages.Message)
	 */
	@Override
	protected void processMessage( Message message ) {

		// Before processing a message, check if we need to update the messaging client
		configure();

		// Process the message
		try {
			if( message instanceof MsgCmdSetRootInstance )
				processMsgSetRootInstance((MsgCmdSetRootInstance) message );

			else if( message instanceof MsgCmdRemoveInstance )
				processMsgRemoveInstance((MsgCmdRemoveInstance) message );

			else if( message instanceof MsgCmdAddInstance )
				processMsgAddInstance((MsgCmdAddInstance) message );

			else if( message instanceof MsgCmdChangeInstanceState )
				processMsgChangeInstanceState((MsgCmdChangeInstanceState) message );

			else if( message instanceof MsgCmdAddImport )
				processMsgAddImport((MsgCmdAddImport) message );

			else if( message instanceof MsgCmdRemoveImport )
				processMsgRemoveImport((MsgCmdRemoveImport) message );

			else if( message instanceof MsgCmdRequestImport )
				processMsgRequestImport((MsgCmdRequestImport) message );

			else if( message instanceof MsgCmdSendInstances )
				processMsgSendInstances((MsgCmdSendInstances) message );

			else
				this.logger.warning( getName() + " got an undetermined message to process. " + message.getClass().getName());

		} catch( IOException e ) {
			this.logger.severe( "A problem occurred with the messaging. " + e.getMessage());
			this.logger.finest( Utils.writeException( e ));

		}  catch( PluginException e ) {
			this.logger.severe( "A problem occurred with a plug-in. " + e.getMessage());
			this.logger.finest( Utils.writeException( e ));
		}
	}


	/**
	 * Sends the local states to the DM.
	 * @param message the initial request
	 * @throws IOException if an error occurred with the messaging
	 */
	void processMsgSendInstances( MsgCmdSendInstances message ) throws IOException {

		String appName = this.agent.getApplicationName();
		if( this.rootInstance != null ) {
			for( Instance i : InstanceHelpers.buildHierarchicalList( this.rootInstance ))
				this.messagingClient.sendMessageToTheDm( new MsgNotifInstanceChanged( appName, i ));
		}
	}



	/**
	 * Sets or updates the local model.
	 * <p>
	 * This method is used to initialize the local model
	 * or to update it when new instances were created.
	 * </p>
	 * <p>
	 * Deletion is handled separately.
	 * </p>
	 *
	 * @param msg the message to process
	 * @throws IOException if an error occurred with the messaging
	 */
	void processMsgSetRootInstance( MsgCmdSetRootInstance msg ) throws IOException {

		Instance newRootInstance = msg.getRootInstance();
		List<Instance> instancesToProcess = new ArrayList<Instance> ();

		// Update the model and determine what must be updated
		if( newRootInstance.getParent() != null ) {
			this.logger.severe( "The received instance is not a root one. Request to update the local model is dropped." );

		} else if( this.rootInstance == null ) {
			this.logger.fine( "Setting the root instance." );
			this.rootInstance = newRootInstance;
			instancesToProcess.addAll( InstanceHelpers.buildHierarchicalList( this.rootInstance ));

			if( this.rootInstance.getStatus() != InstanceStatus.DEPLOYED_STARTED ) {
				this.rootInstance.setStatus( InstanceStatus.DEPLOYED_STARTED );
				this.messagingClient.sendMessageToTheDm( new MsgNotifInstanceChanged( this.agent.getApplicationName(), newRootInstance ));
			}
		}

		// Configure the messaging
		for( Instance instanceToProcess : instancesToProcess ) {
			VariableHelpers.updateNetworkVariables( instanceToProcess.getExports(), this.agent.getIpAddress());
			this.messagingClient.listenToExportsFromOtherAgents( ListenerCommand.START, instanceToProcess );
			this.messagingClient.requestExportsFromOtherAgents( instanceToProcess );
		}
	}


	/**
	 * Removes an instance to the local model.
	 * @param msg the message to process
	 * @throws IOException if an error occurred with the messaging
	 */
	void processMsgRemoveInstance( MsgCmdRemoveInstance msg ) throws IOException {

		// Remove the instance
		boolean removed = false;
		Instance instance = InstanceHelpers.findInstanceByPath( this.rootInstance, msg.getInstancePath());
		if( instance == null ) {
			this.logger.severe( "No instance matched " + msg.getInstancePath() + " on the agent. Request to remove it from the model is dropped." );

		} else if( instance.getStatus() != InstanceStatus.NOT_DEPLOYED ) {
			this.logger.severe( "Instance " + msg.getInstancePath() + " cannot be removed. Instance status: " + instance.getStatus() + "." );
			// We do not have to check children's status.
			// We cannot have a parent in NOT_DEPLOYED and a child in STARTED (as an example).

		} else if( instance.getParent() != null ) {
			removed = true;
			instance.getParent().getChildren().remove( instance );
			this.logger.fine( "Child instance " + msg.getInstancePath() + " was removed from the model." );

		} else {
			this.logger.fine( "The root instance " + msg.getInstancePath() + " cannot be removed. The agent must be reboot and/or reconfigured." );
		}

		// Configure the messaging
		if( removed ) {
			this.messagingClient.sendMessageToTheDm( new MsgNotifInstanceRemoved( this.agent.getApplicationName(), instance ));
			for( Instance instanceToProcess : InstanceHelpers.buildHierarchicalList( instance ))
				this.messagingClient.listenToExportsFromOtherAgents( ListenerCommand.STOP, instanceToProcess );
		}
	}


	/**
	 * Adds an instance to the local model.
	 * @param msg the message to process
	 * @throws IOException if an error occurred with the messaging
	 */
	void processMsgAddInstance( MsgCmdAddInstance msg ) throws IOException {

		Component instanceComponent;
		Instance parentInstance = InstanceHelpers.findInstanceByPath( this.rootInstance, msg.getParentInstancePath());
		if( parentInstance == null ) {
			this.logger.severe( "The parent instance for " + msg.getParentInstancePath() + " was not found. The request to add a new instance is dropped." );

		} else if(( instanceComponent = ComponentHelpers.findSubComponent( parentInstance.getComponent(), msg.getComponentName())) == null ) {
			this.logger.severe( "The component " + msg.getComponentName() + " was not found in the local graph." );

		} else {
			Instance newInstance = new Instance( msg.getInstanceName()).channel( msg.getChannel()).component( instanceComponent );
			if( msg.getData() != null )
				newInstance.getData().putAll( msg.getData());

			if( msg.getOverridenExports() != null )
				newInstance.getOverriddenExports().putAll( msg.getOverridenExports());

			Application tempApp = new Application( "temp app" );
			tempApp.getRootInstances().add( parentInstance );
			if( ! InstanceHelpers.tryToInsertChildInstance( tempApp, parentInstance, newInstance )) {
				this.logger.severe( "The new '" + msg.getInstanceName() + "' instance could not be inserted into the local model." );

			} else {
				VariableHelpers.updateNetworkVariables( newInstance.getExports(), this.agent.getIpAddress());
				this.messagingClient.listenToExportsFromOtherAgents( ListenerCommand.START, newInstance );
				this.messagingClient.requestExportsFromOtherAgents( newInstance );
			}
		}
	}


	/**
	 * Deploys an instance.
	 * @param msg the message to process
	 * @throws IOException if an error occurred with the messaging or while manipulating the file system
	 * @throws PluginException if something went wrong with the plug-in
	 */
	void processMsgChangeInstanceState( MsgCmdChangeInstanceState msg )
	throws IOException, PluginException {

		PluginInterface plugin;
		Instance instance = InstanceHelpers.findInstanceByPath( this.rootInstance, msg.getInstancePath());
		if( instance == null )
			this.logger.severe( "No instance matched " + msg.getInstancePath() + " on the agent. Request to deploy it is dropped." );

		else if( instance.getParent() == null )
			this.logger.severe( "No action on the root instance is permitted." );

		else if(( plugin = this.agent.findPlugin( instance )) == null )
			this.logger.severe( "No plug-in was found to deploy " + msg.getInstancePath() + "." );

		else
			AbstractLifeCycleManager
			.build( instance, this.agent.getApplicationName(), this.messagingClient )
			.changeInstanceState( instance, plugin, msg.getNewState(), msg.getFileNameToFileContent());
	}


	/**
	 * Publishes its exports when required.
	 * @param msg the message process
	 * @throws IOException if an error occurred with the messaging
	 */
	void processMsgRequestImport( MsgCmdRequestImport msg ) throws IOException {

		for( Instance instance : InstanceHelpers.buildHierarchicalList( this.rootInstance )) {
			if( instance.getStatus() == InstanceStatus.DEPLOYED_STARTED )
				this.messagingClient.publishExports( instance, msg.getComponentOrFacetName());
		}
	}


	/**
	 * Removes (if necessary) an import from the model instances.
	 * @param msg the message process
	 * @throws IOException if an error occurred with the messaging
	 * @throws PluginException if an error occurred with a plug-in
	 */
	void processMsgRemoveImport( MsgCmdRemoveImport msg ) throws IOException, PluginException {

		// Go through all the instances to see which ones are impacted
		String appName = this.agent.getApplicationName();
		for( Instance instance : InstanceHelpers.buildHierarchicalList( this.rootInstance )) {

			Set<String> importPrefixes = VariableHelpers.findPrefixesForImportedVariables( instance );
			if( ! importPrefixes.contains( msg.getComponentOrFacetName()))
				continue;

			// Is there an import to remove?
			Collection<Import> imports = instance.getImports().get( msg.getComponentOrFacetName());
			Import toRemove = ImportHelpers.findImportByExportingInstance( imports, msg.getRemovedInstancePath());
			if( toRemove == null )
				continue;

			// Remove the import and publish an update to the DM
			imports.remove( toRemove );
			this.logger.fine( "Removing import from " + InstanceHelpers.computeInstancePath( instance )
					+ ". Removed exporting instance: " + msg.getRemovedInstancePath());

			this.messagingClient.sendMessageToTheDm( new MsgNotifInstanceChanged( appName, instance ));

			// Update the life cycle if necessary
			PluginInterface plugin = this.agent.findPlugin( instance );
			if( plugin == null )
				throw new PluginException( "No plugin was found for " + InstanceHelpers.computeInstancePath( instance ));

			AbstractLifeCycleManager
			.build( instance, this.agent.getApplicationName(), this.messagingClient )
			.updateStateFromImports( instance, plugin, toRemove, InstanceStatus.DEPLOYED_STOPPED );
		}
	}


	/**
	 * Receives and adds (if necessary) a new import to the model instances.
	 * @param msg the message process
	 * @throws IOException if an error occurred with the messaging
	 * @throws PluginException if an error occurred with a plug-in
	 */
	void processMsgAddImport( MsgCmdAddImport msg ) throws IOException, PluginException {

		// Go through all the instances to see which ones need an update
		String appName = this.agent.getApplicationName();
		for( Instance instance : InstanceHelpers.buildHierarchicalList( this.rootInstance )) {

			// This instance does not depends on it
			Set<String> importPrefixes = VariableHelpers.findPrefixesForImportedVariables( instance );
			if( ! importPrefixes.contains( msg.getComponentOrFacetName()))
				continue;

			// If an instance depends on its component, make sure it does not add itself to the imports.
			// Example: MongoDB may depend on other MongoDB instances.
			if( Utils.areEqual(
					InstanceHelpers.computeInstancePath( instance ),
					msg.getAddedInstancePath()))
				continue;

			// Create the right import
			Import imp = ImportHelpers.buildTailoredImport(
					instance,
					msg.getAddedInstancePath(),
					msg.getComponentOrFacetName(),
					msg.getExportedVariables());

			// Add the import and publish an update to the DM
			this.logger.fine( "Adding import to " + InstanceHelpers.computeInstancePath( instance ) + ". New import: " + imp );
			ImportHelpers.addImport( instance, msg.getComponentOrFacetName(), imp );
			this.messagingClient.sendMessageToTheDm( new MsgNotifInstanceChanged( appName, instance ));

			// Update the life cycle if necessary
			PluginInterface plugin = this.agent.findPlugin( instance );
			if( plugin == null )
				throw new PluginException( "No plugin was found for " + InstanceHelpers.computeInstancePath( instance ));

			AbstractLifeCycleManager
			.build( instance, this.agent.getApplicationName(), this.messagingClient )
			.updateStateFromImports( instance, plugin, imp, InstanceStatus.DEPLOYED_STARTED );
		}
	}
}