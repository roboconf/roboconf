/**
 * Copyright 2015 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.dm.internal.api.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import net.roboconf.core.Constants;
import net.roboconf.core.model.beans.AbstractApplication;
import net.roboconf.core.model.beans.Application;
import net.roboconf.core.model.beans.ApplicationTemplate;
import net.roboconf.core.model.beans.Instance;
import net.roboconf.core.model.beans.Instance.InstanceStatus;
import net.roboconf.core.model.helpers.InstanceHelpers;
import net.roboconf.core.model.targets.TargetUsageItem;
import net.roboconf.core.model.targets.TargetWrapperDescriptor;
import net.roboconf.core.utils.Utils;
import net.roboconf.dm.internal.api.impl.beans.TargetMappingKey;
import net.roboconf.dm.internal.utils.ConfigurationUtils;
import net.roboconf.dm.internal.utils.TargetHelpers;
import net.roboconf.dm.management.api.IConfigurationMngr;
import net.roboconf.dm.management.api.ITargetsMngr;
import net.roboconf.dm.management.exceptions.UnauthorizedActionException;

/**
 * Here is the way this class stores information.
 * <p>
 * Each target has an ID, which is an integer. And each target has its
 * own directory, located under the DM's configuration directory.
 * </p>
 * <p>
 * Each target may contain...
 * </p>
 * <ul>
 * 		<li>target.properties (mandatory)</li>
 * 		<li>{@value #TARGETS_ASSOC_FILE} (mapping between this target and application instances)</li>
 * 		<li>{@value #TARGETS_HINTS_FILE} (user preferences to show or hide targets for a given application)</li>
 * 		<li>{@value #TARGETS_USAGE_FILE} (mapping indicating which instances have deployed things with this target)</li>
 * </ul>
 *
 * @author Vincent Zurczak - Linagora
 */
public class TargetsMngrImpl implements ITargetsMngr {

	private static final String TARGETS_ASSOC_FILE = "associations.properties";
	private static final String TARGETS_HINTS_FILE = "hints.properties";
	private static final String TARGETS_USAGE_FILE = "usage.properties";

	private static final Object LOCK = new Object();

	private final Logger logger = Logger.getLogger( getClass().getName());
	private final AtomicInteger id = new AtomicInteger();

	private final IConfigurationMngr configurationMngr;
	private final Map<TargetMappingKey,String> instanceToCachedId;


	/**
	 * Constructor.
	 * @param configurationMngr
	 */
	public TargetsMngrImpl( IConfigurationMngr configurationMngr ) {
		this.configurationMngr = configurationMngr;
		this.instanceToCachedId = new ConcurrentHashMap<> ();

		// Restore the cache
		restoreAssociationsCache();
	}


	// CRUD operations on targets


	@Override
	public String createTarget( String targetContent ) throws IOException {

		File targetFile = new File( findTargetDirectory( null ), Constants.TARGET_PROPERTIES_FILE_NAME );
		Utils.createDirectory( targetFile.getParentFile());
		Utils.writeStringInto( targetContent, targetFile );

		return targetFile.getParentFile().getName();
	}


	@Override
	public String createTarget( File targetPropertiesFile ) throws IOException {

		File targetFile = new File( findTargetDirectory( null ), Constants.TARGET_PROPERTIES_FILE_NAME );
		Utils.createDirectory( targetFile.getParentFile());
		Utils.copyStream( targetPropertiesFile, targetFile );

		return targetFile.getParentFile().getName();
	}


	@Override
	public void updateTarget( String targetId, String newTargetContent ) throws IOException, UnauthorizedActionException {

		File targetFile = new File( findTargetDirectory( targetId ), Constants.TARGET_PROPERTIES_FILE_NAME );
		if( ! targetFile.getParentFile().exists())
			throw new UnauthorizedActionException( "Target " + targetId + " does not exist." );

		Utils.writeStringInto( newTargetContent, targetFile );
	}


	@Override
	public void deleteTarget( String targetId ) throws IOException, UnauthorizedActionException {

		// No machine using this target can be running.
		boolean used = false;
		synchronized( LOCK ) {
			used = isTargetUsed( targetId );
		}

		if( used )
			throw new UnauthorizedActionException( "Deletion is not permitted." );

		// Delete the files related to this target
		File targetDirectory = findTargetDirectory( targetId );
		Utils.deleteFilesRecursively( targetDirectory );
	}


	// Associating targets and application instances


	@Override
	public void associateTargetWithScopedInstance( String targetId, AbstractApplication app, String instancePath )
	throws IOException, UnauthorizedActionException {

		Instance instance = InstanceHelpers.findInstanceByPath( app, instancePath );
		if( instance != null && instance.getStatus() != InstanceStatus.NOT_DEPLOYED )
			throw new UnauthorizedActionException( "Operation not allowed: " + app + " :: " + instancePath + " should be not deployed." );

		saveAssociation( app, targetId, instancePath, true );
	}


	@Override
	public void dissociateTargetFromScopedInstance( AbstractApplication app, String instancePath )
	throws IOException, UnauthorizedActionException {

		Instance instance = InstanceHelpers.findInstanceByPath( app, instancePath );
		if( instance != null && instance.getStatus() != InstanceStatus.NOT_DEPLOYED )
			throw new UnauthorizedActionException( "Operation not allowed: " + app + " :: " + instancePath + " should be not deployed." );

		saveAssociation( app, null, instancePath, false );
	}


	@Override
	public void copyOriginalMapping( Application app ) throws IOException {

		List<TargetMappingKey> keys = new ArrayList<> ();

		// Null <=> The default for the application
		keys.add( new TargetMappingKey( app.getTemplate(), (String) null ));

		// We can search defaults only for the existing instances
		for( Instance scopedInstance : InstanceHelpers.findAllScopedInstances( app ))
			keys.add( new TargetMappingKey( app.getTemplate(), scopedInstance ));

		// Copy the associations when they exist for the template
		for( TargetMappingKey key : keys ) {
			String targetId = this.instanceToCachedId.get( key );
			try {
				if( targetId != null )
					associateTargetWithScopedInstance( targetId, app, key.getInstancePath());

			} catch( UnauthorizedActionException e ) {

				// This method could be used to reset the mappings / associations.
				// However, this is not possible if the current instance is already deployed.
				// So, we just skip it.
				this.logger.severe( e.getMessage());
				Utils.logException( this.logger, e );
			}
		}
	}


	@Override
	public void applicationWasDeleted( AbstractApplication app ) throws IOException {

		String name = app.getName();
		String qualifier = app instanceof ApplicationTemplate ? ((ApplicationTemplate) app).getQualifier() : null;

		List<TargetMappingKey> toClean = new ArrayList<> ();
		Set<String> targetIds = new HashSet<> ();

		// Find the mapping keys and the targets to update
		for( Map.Entry<TargetMappingKey,String> entry : this.instanceToCachedId.entrySet()) {

			if( Objects.equals( name, entry.getKey().getName())
					&& Objects.equals( qualifier, entry.getKey().getQualifier())) {

				targetIds.add( entry.getValue());
				toClean.add( entry.getKey());
			}
		}

		// Update the target files
		for( String targetId : targetIds ) {
			File targetDirectory = findTargetDirectory( targetId );

			// Update the association file
			File[] files = new File[] {
					new File( targetDirectory, TARGETS_ASSOC_FILE ),
					new File( targetDirectory, TARGETS_HINTS_FILE )
			};

			for( File f : files ) {
				for( TargetMappingKey key : toClean ) {
					Properties props = Utils.readPropertiesFileQuietly( f, this.logger );
					props.remove( key.toString());
					writeProperties( props, f );
				}
			}
		}

		// Update the cache
		for( TargetMappingKey key : toClean )
			this.instanceToCachedId.remove( key );
	}


	// Finding targets


	@Override
	public Map<String,String> findRawTargetProperties( AbstractApplication app, String instancePath ) {

		Map<String,String> result = new HashMap<> ();
		String targetId = findTargetId( app, instancePath );
		if( targetId != null ) {
			File f = new File( findTargetDirectory( targetId ), Constants.TARGET_PROPERTIES_FILE_NAME );
			for( Map.Entry<Object,Object> entry : Utils.readPropertiesFileQuietly( f, this.logger ).entrySet()) {
				result.put((String) entry.getKey(), (String) entry.getValue());
			}
		}

		return result;
	}


	@Override
	public String findRawTargetProperties( String targetId ) {

		File f = new File( findTargetDirectory( targetId ), Constants.TARGET_PROPERTIES_FILE_NAME );
		String content = null;
		try {
			if( f.exists())
				content = Utils.readFileContent( f );

		} catch( IOException e ) {
			this.logger.severe( "Raw properties could not be read for target " + targetId );
			Utils.logException( this.logger, e );
		}

		return content;
	}


	@Override
	public String findTargetId( AbstractApplication app, String instancePath ) {

		TargetMappingKey key = new TargetMappingKey( app, instancePath );
		String targetId = this.instanceToCachedId.get( key );
		if( targetId == null )
			key = new TargetMappingKey( app, (String) null );

		targetId = this.instanceToCachedId.get( key );
		return targetId;
	}


	@Override
	public List<TargetWrapperDescriptor> listAllTargets() {

		File dir = new File( this.configurationMngr.getWorkingDirectory(), ConfigurationUtils.TARGETS );
		List<File> targetDirectories = Utils.listDirectories( dir );

		return buildList( targetDirectories, null );
	}


	@Override
	public TargetWrapperDescriptor findTargetById( String targetId ) {

		File dir = new File( this.configurationMngr.getWorkingDirectory(), ConfigurationUtils.TARGETS );
		File targetDirectory = new File( dir, targetId );

		return build( targetDirectory );
	}


	// In relation with hints


	@Override
	public List<TargetWrapperDescriptor> listPossibleTargets( AbstractApplication app ) {

		// Find the matching targets based on registered hints
		String key = new TargetMappingKey( app ).toString();
		String tplKey = null;
		if( app instanceof Application )
			tplKey = new TargetMappingKey(((Application) app).getTemplate()).toString();

		List<File> targetDirectories = new ArrayList<> ();
		File dir = new File( this.configurationMngr.getWorkingDirectory(), ConfigurationUtils.TARGETS );
		for( File f : Utils.listDirectories( dir )) {

			// If there is no hint for this target, then it is global.
			// We can list it.
			File hintsFile = new File( f, TARGETS_HINTS_FILE );
			if( ! hintsFile.exists()) {
				targetDirectories.add( f );
				continue;
			}

			// Otherwise, the key must exist in the file
			Properties props = Utils.readPropertiesFileQuietly( hintsFile, this.logger );
			if( props.containsKey( key ))
				targetDirectories.add( f );
			else if( tplKey != null && props.containsKey( tplKey ))
				targetDirectories.add( f );
		}

		// Build the result
		return buildList( targetDirectories, app );
	}


	@Override
	public void addHint( String targetId, AbstractApplication app ) throws IOException {
		saveHint( targetId, app, true );
	}


	@Override
	public void removeHint( String targetId, AbstractApplication app ) throws IOException {
		saveHint( targetId, app, false );
	}


	// Atomic operations...


	@Override
	public Map<String,String> lockAndGetTarget( Application app, Instance scopedInstance )
	throws IOException {

		String instancePath = InstanceHelpers.computeInstancePath( scopedInstance );
		String targetId = findTargetId( app, instancePath );
		if( targetId == null )
			throw new IOException( "No target was found for " + app + " :: " + instancePath );

		TargetMappingKey mappingKey = new TargetMappingKey( app, instancePath );
		synchronized( LOCK ) {
			saveUsage( mappingKey, targetId, true );
		}

		this.logger.fine( "Target " + targetId + "'s lock was acquired for " + instancePath );
		Map<String,String> result = findRawTargetProperties( app, instancePath );
		return TargetHelpers.expandProperties( scopedInstance, result );
	}


	@Override
	public void unlockTarget( Application app, Instance scopedInstance )
	throws IOException {

		String instancePath = InstanceHelpers.computeInstancePath( scopedInstance );
		String targetId = findTargetId( app, instancePath );
		TargetMappingKey mappingKey = new TargetMappingKey( app, instancePath );

		synchronized( LOCK ) {
			saveUsage( mappingKey, targetId, false );
		}

		this.logger.fine( "Target " + targetId + "'s lock was released for " + instancePath );
	}


	// Diagnostics


	@Override
	public List<TargetUsageItem> findUsageStatistics( String targetId ) {

		// Get usage first
		List<String> appNames;
		synchronized( LOCK ) {
			appNames = applicationsThatUse( targetId );
		}

		// Now, let's build the result
		Set<TargetUsageItem> result = new HashSet<> ();
		for( Map.Entry<TargetMappingKey,String> entry : this.instanceToCachedId.entrySet()) {
			if( ! entry.getValue().equals( targetId ))
				continue;

			String appName = entry.getKey().getName();
			TargetUsageItem item = new TargetUsageItem();

			item.setName( appName );
			item.setQualifier( entry.getKey().getQualifier());
			item.setReferencing( true );
			item.setUsing( appNames.contains( appName ));

			result.add( item );
		}

		return new ArrayList<>( result );
	}


	// Package methods


	List<TargetWrapperDescriptor> buildList( List<File> targetDirectories, AbstractApplication app ) {

		List<TargetWrapperDescriptor> result = new ArrayList<> ();
		for( File targetDirectory : targetDirectories ) {

			File associationFile = new File( targetDirectory, TARGETS_ASSOC_FILE );
			Properties props = Utils.readPropertiesFileQuietly( associationFile, this.logger );
			boolean isDefault = false;
			if( app != null )
				isDefault = props.containsKey( new TargetMappingKey( app ).toString());

			TargetWrapperDescriptor tb = build( targetDirectory );
			if( tb != null ) {
				tb.setDefault( isDefault );
				result.add( tb );
			}
		}

		return result;
	}


	TargetWrapperDescriptor build( File targetDirectory ) {

		TargetWrapperDescriptor tb = null;
		File targetPropertiesFile = new File( targetDirectory, Constants.TARGET_PROPERTIES_FILE_NAME );
		try {
			Properties props = Utils.readPropertiesFile( targetPropertiesFile );
			tb = new TargetWrapperDescriptor();

			tb.setId( targetDirectory.getName());
			tb.setName( props.getProperty( Constants.TARGET_PROPERTY_NAME ));
			tb.setDescription( props.getProperty( Constants.TARGET_PROPERTY_DESCRIPTION ));

			String handler = TargetHelpers.findTargetHandlerName( props );
			tb.setHandler( handler );

		} catch( IOException e ) {
			this.logger.severe( "Properties of the target #" + targetDirectory.getName() + " could not be read." );
			Utils.logException( this.logger, e );
		}

		return tb;
	}


	// Private methods


	private void saveAssociation( AbstractApplication app, String targetId, String instancePath, boolean add )
	throws IOException {

		// Association means an exact mapping between an application instance
		// and a target ID.
		TargetMappingKey key = new TargetMappingKey( app, instancePath );

		// Remove the old association, always.
		if( instancePath != null ) {
			String oldTargetId = this.instanceToCachedId.get( key );
			if( oldTargetId != null ) {
				File associationFile = new File( findTargetDirectory( oldTargetId ), TARGETS_ASSOC_FILE );
				Properties props = Utils.readPropertiesFileQuietly( associationFile, this.logger );
				props.remove( key );
				writeProperties( props, associationFile );
			}
		}

		// Register a potential new association and update the cache.
		if( add ) {
			File associationFile = new File( findTargetDirectory( targetId ), TARGETS_ASSOC_FILE );
			Properties props = Utils.readPropertiesFileQuietly( associationFile, this.logger );
			props.setProperty( key.toString(), "" );
			writeProperties( props, associationFile );

			this.instanceToCachedId.put( key, targetId );

		} else if( instancePath != null ) {
			this.instanceToCachedId.remove( key );
		}
	}


	private void restoreAssociationsCache() {

		File dir = new File( this.configurationMngr.getWorkingDirectory(), ConfigurationUtils.TARGETS );
		for( File f : Utils.listDirectories( dir )) {

			// Update the global ID
			Integer targetId = Integer.valueOf( f.getName());
			if( targetId > this.id.get())
				this.id.set( targetId );

			// Cache associations for quicker access
			File associationFile = new File( f, TARGETS_ASSOC_FILE );
			Properties props = Utils.readPropertiesFileQuietly( associationFile, this.logger );
			for( Map.Entry<Object,Object> entry : props.entrySet()) {

				TargetMappingKey key = TargetMappingKey.parse( entry.getKey().toString());
				this.instanceToCachedId.put( key, f.getName());
			}
		}
	}


	private void saveUsage( TargetMappingKey mappingKey, String targetId, boolean add )
	throws IOException {

		// Usage means the target has been used to create a real machine.
		File usageFile = new File( findTargetDirectory( targetId ), TARGETS_USAGE_FILE );
		Properties props = Utils.readPropertiesFileQuietly( usageFile, this.logger );

		String key = mappingKey.toString();
		if( add )
			props.setProperty( key, targetId );
		else
			props.remove( key );

		writeProperties( props, usageFile );
	}


	private boolean isTargetUsed( String targetId ) {

		File usageFile = new File( findTargetDirectory( targetId ), TARGETS_USAGE_FILE );
		Properties props = Utils.readPropertiesFileQuietly( usageFile, this.logger );

		boolean found = false;
		for( Iterator<Object> it = props.values().iterator(); it.hasNext() && ! found; ) {
			found = it.next().equals( targetId );
		}

		return found;
	}


	private List<String> applicationsThatUse( String targetId ) {

		File usageFile = new File( findTargetDirectory( targetId ), TARGETS_USAGE_FILE );
		Properties props = Utils.readPropertiesFileQuietly( usageFile, this.logger );

		List<String> result = new ArrayList<> ();
		for( Object o : props.keySet()) {
			TargetMappingKey key = TargetMappingKey.parse((String) o);
			result.add( key.getName());
		}

		return result;
	}


	private void saveHint( String targetId, AbstractApplication app, boolean add ) throws IOException {

		// A hint is just a preference (some kind of scope for a target).
		// If a hint is not respected, no exception will be thrown.
		File hintsFile = new File( findTargetDirectory( targetId ), TARGETS_HINTS_FILE );
		Properties props = Utils.readPropertiesFileQuietly( hintsFile, this.logger );

		String key = new TargetMappingKey( app ).toString();
		if( add ) {
			props.setProperty( key, "" );
		} else {
			props.remove( key );
		}

		writeProperties( props, hintsFile );
	}


	private void writeProperties( Properties props, File file ) throws IOException {

		if( props.isEmpty())
			Utils.deleteFilesRecursivelyAndQuietly( file );
		else
			Utils.writePropertiesFile( props, file );
	}


	private File findTargetDirectory( String targetId ) {

		if( targetId == null )
			targetId = String.valueOf( this.id.incrementAndGet());

		File dir = new File( this.configurationMngr.getWorkingDirectory(), ConfigurationUtils.TARGETS );
		return new File( dir, targetId );
	}
}
