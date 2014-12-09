/**
 * Copyright 2014 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.target.vmware.internal;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.logging.Logger;

import net.roboconf.core.agents.DataHelpers;
import net.roboconf.core.utils.Utils;
import net.roboconf.target.api.TargetException;
import net.roboconf.target.api.TargetHandler;

import com.vmware.vim25.DynamicProperty;
import com.vmware.vim25.GuestProgramSpec;
import com.vmware.vim25.NamePasswordAuthentication;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.mo.ComputeResource;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.GuestOperationsManager;
import com.vmware.vim25.mo.GuestProcessManager;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * @author Pierre-Yves Gibello - Linagora
 */
public class VmwareIaasHandler implements TargetHandler {

	public static final String TARGET_ID = "iaas-vmware";
	private final Logger logger = Logger.getLogger( getClass().getName());


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.target.api.TargetHandler#getTargetId()
	 */
	@Override
	public String getTargetId() {
		return TARGET_ID;
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.target.api.TargetHandler
	 * #createOrConfigureMachine(java.util.Map, java.lang.String, java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public String createOrConfigureMachine(
			Map<String,String> targetProperties,
			String messagingIp,
			String messagingUsername,
			String messagingPassword,
			String rootInstanceName,
			String applicationName )
	throws TargetException {

		try {
			final String machineImageId = targetProperties.get("vmware.template");
			final ServiceInstance vmwareServiceInstance = new ServiceInstance(
					new URL(targetProperties.get("vmware.url")),
					targetProperties.get("vmware.user"),
					targetProperties.get("vmware.password"),
					Boolean.parseBoolean(targetProperties.get("vmware.ignorecert")));

			final ComputeResource vmwareComputeResource = (ComputeResource)(
					new InventoryNavigator( vmwareServiceInstance.getRootFolder())
					.searchManagedEntity("ComputeResource", targetProperties.get("vmware.cluster")));

			// Generate the user data first, so that nothing has been done on the IaaS if it fails
			String userData = DataHelpers.writeUserDataAsString( messagingIp, messagingUsername, messagingPassword, applicationName, rootInstanceName );
			VirtualMachine vm = getVirtualMachine( vmwareServiceInstance, machineImageId );
			String vmwareDataCenter = targetProperties.get("vmware.datacenter");
			Folder vmFolder =
					((Datacenter)(new InventoryNavigator( vmwareServiceInstance.getRootFolder())
					.searchManagedEntity("Datacenter", vmwareDataCenter)))
					.getVmFolder();

			this.logger.fine("machineImageId=" + machineImageId);
			if (vm == null || vmFolder == null)
				throw new TargetException("VirtualMachine (= " + vm + " ) or Datacenter path (= " + vmFolder + " ) is NOT correct. Pls double check.");

			VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
			cloneSpec.setLocation(new VirtualMachineRelocateSpec());
			cloneSpec.setPowerOn(false);
			cloneSpec.setTemplate(true);

			VirtualMachineConfigSpec vmSpec = new VirtualMachineConfigSpec();
			vmSpec.setAnnotation( userData );
			cloneSpec.setConfig(vmSpec);

			Task task = vm.cloneVM_Task( vmFolder, rootInstanceName, cloneSpec );
			this.logger.fine("Cloning the template: "+ machineImageId +" ...");
			String status = task.waitForTask();
			if (!status.equals(Task.SUCCESS))
				throw new TargetException("Failure: Virtual Machine cannot be cloned");

			VirtualMachine vm2 = getVirtualMachine( vmwareServiceInstance, rootInstanceName );
			this.logger.fine("Transforming the clone template to Virtual machine ...");
			vm2.markAsVirtualMachine( vmwareComputeResource.getResourcePool(), null);

			DynamicProperty dprop = new DynamicProperty();
			dprop.setName("guestinfo.userdata");
			dprop.setVal(userData);
			vm2.getGuest().setDynamicProperty(new DynamicProperty[]{dprop});

			task = vm2.powerOnVM_Task(null);
			this.logger.fine("Starting the virtual machine: "+ rootInstanceName +" ...");
			status = task.waitForTask();
			if (!status.equals(Task.SUCCESS))
				throw new TargetException("Failure -: Virtual Machine cannot be started");

			// VMWare tools not yet started (!)
			// FIXME (VZ): how great!
			Thread.sleep( 20000 );

			GuestOperationsManager gom = vmwareServiceInstance.getGuestOperationsManager();
		    NamePasswordAuthentication npa = new NamePasswordAuthentication();
		    npa.username = targetProperties.get("vmware.vmuser");
		    npa.password = targetProperties.get("vmware.vmpassword");
		    GuestProgramSpec spec = new GuestProgramSpec();

		    spec.programPath = "/bin/echo";
		    spec.arguments = "$\'" + userData + "\' > /tmp/roboconf.properties";
		    this.logger.fine(spec.programPath + " " + spec.arguments);

		    GuestProcessManager gpm = gom.getProcessManager(vm2);
		    long pid = gpm.startProgramInGuest(npa, spec);
		    this.logger.fine("pid: " + pid);

			return vm2.getName();

		} catch(RemoteException e) {
			throw new TargetException(e);

		} catch (InterruptedException e) {
			throw new TargetException(e);

		} catch( IOException e ) {
			throw new TargetException(e);
		}
	}


	/*
	 * (non-Javadoc)
	 * @see net.roboconf.target.api.TargetHandler
	 * #terminateMachine(java.util.Map, java.lang.String)
	 */
	@Override
	public void terminateMachine( Map<String, String> targetProperties, String instanceId ) throws TargetException {

		try {
			final ServiceInstance vmwareServiceInstance = new ServiceInstance(
					new URL(targetProperties.get("vmware.url")),
					targetProperties.get("vmware.user"),
					targetProperties.get("vmware.password"),
					Boolean.parseBoolean(targetProperties.get("vmware.ignorecert")));

			VirtualMachine vm = getVirtualMachine( vmwareServiceInstance, instanceId );
			if (vm == null)
				throw new TargetException("error vm: "+instanceId+" not found");

			Task task = vm.powerOffVM_Task();
			try {
				if(!(task.waitForTask()).equals(Task.SUCCESS))
					throw new TargetException("error when trying to stop vm: "+instanceId);

			} catch (InterruptedException ignore) { /*ignore*/ }

			task = vm.destroy_Task();
			try {
				if(!(task.waitForTask()).equals(Task.SUCCESS))
					throw new TargetException("error when trying to remove vm: "+instanceId);

			} catch (InterruptedException ignore) { /*ignore*/ }

		} catch( RemoteException e ) {
			throw new TargetException(e);

		} catch( MalformedURLException e ) {
			throw new TargetException(e);
		}
	}


	private VirtualMachine getVirtualMachine( ServiceInstance vmwareServiceInstance , String virtualmachineName )
	throws RemoteException {

		VirtualMachine result = null;
		if( ! Utils.isEmptyOrWhitespaces( virtualmachineName )) {
			Folder rootFolder = vmwareServiceInstance.getRootFolder();
			result = (VirtualMachine) new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine", virtualmachineName);
		}

		return result;
	}
}