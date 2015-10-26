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

package net.roboconf.dm.management.api;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import net.roboconf.core.model.beans.AbstractApplication;
import net.roboconf.core.model.beans.Application;
import net.roboconf.core.model.beans.Instance;
import net.roboconf.core.model.targets.TargetUsageItem;
import net.roboconf.core.model.targets.TargetWrapperDescriptor;
import net.roboconf.dm.management.exceptions.UnauthorizedActionException;

/**
 * @author Vincent Zurczak - Linagora
 */
public interface ITargetsMngr {


	// CRUD operations on targets


	/**
	 * Creates a new target.
	 * @param targetContent the target content
	 * @return the ID of the newly created target
	 * @throws IOException if something went wrong
	 */
	String createTarget( String targetContent ) throws IOException;

	/**
	 * Creates a new target.
	 * @param targetPropertiesFile a target.properties file
	 * @return the ID of the newly created target
	 * @throws IOException if something went wrong
	 */
	String createTarget( File targetPropertiesFile ) throws IOException;

	/**
	 * Updates an existing target.
	 * @param targetId the target's ID
	 * @param newTargetContent the new content of the target.properties file
	 * @throws IOException if the update failed
	 * @throws UnauthorizedActionException if the target does not exist
	 */
	void updateTarget( String targetId, String newTargetContent ) throws IOException, UnauthorizedActionException;

	/**
	 * Deletes a target.
	 * <p>
	 * Deletion is only permitted if the target is not used.
	 * </p>
	 *
	 * @param targetId the target's ID
	 * @throws IOException if something went wrong
	 * @throws UnauthorizedActionException if the target is being used
	 * @see #lockAndGetTarget(AbstractApplication, String)
	 * @see #unlockTarget(AbstractApplication, String)
	 */
	void deleteTarget( String targetId ) throws IOException, UnauthorizedActionException;


	// Associating targets and application instances


	/**
	 * Associates a target and a scoped instance within an application or application template.
	 * <p>
	 * If the instance path is null, then the target is considered to be the default one for this application.
	 * </p>
	 * <p>
	 * If the instance was already associated with another target, this association is removed.
	 * </p>
	 *
	 * @param targetId a target ID
	 * @param app an application or an application template
	 * @param instancePath an instance path (null to set the default for the application)
	 * @throws IOException if something went wrong
	 * @throws UnauthorizedActionException if the instance is already deployed with another target
	 */
	void associateTargetWithScopedInstance( String targetId, AbstractApplication app, String instancePath )
	throws IOException, UnauthorizedActionException;

	/**
	 * Dissociates a target and a scoped instance within an application or application template.
	 * <p>
	 * If the instance path is null, no action is undertaken. To override the default target for a given
	 * application, just invoke {@link #associateTargetWithScopedInstance(String, AbstractApplication, String)}.
	 * </p>
	 *
	 * @param app an application or application template
	 * @param instancePath an instance path
	 * @throws IOException if something went wrong
	 * @throws UnauthorizedActionException if the instance is already deployed with another target
	 */
	void dissociateTargetFromScopedInstance( AbstractApplication app, String instancePath )
	throws IOException, UnauthorizedActionException;

	/**
	 * Copies the target mapping of the application template to the application.
	 * <p>
	 * If there was already a target mapping for the application, it is overwritten.
	 * </p>
	 * <p>
	 * If there is no mapping for the application's template, then nothing is copied.
	 * </p>
	 *
	 * @param app an application
	 * @throws IOException if something went wrong
	 */
	void copyOriginalMapping( Application app ) throws IOException;

	/**
	 * Removes any reference to an application after it was deleted.
	 * @param app an application or application template
	 * @throws IOException if something went wrong
	 */
	void applicationWasDeleted( AbstractApplication app ) throws IOException;


	// Finding targets


	/**
	 * Finds the RAW target properties of a scoped instance within an application or an application template.
	 * <p>
	 * Notice that this variable does not expand anything (e.g. IP addresses).
	 * </p>
	 *
	 * @param app an application or application template
	 * @param instancePath an instance path
	 * @return a non-null map of properties (empty if the file was not found)
	 * @see #lockAndGetTarget(AbstractApplication, String)
	 */
	Map<String,String> findRawTargetProperties( AbstractApplication app, String instancePath );

	/**
	 * Finds the RAW target properties of a scoped instance within an application or an application template.
	 * <p>
	 * Notice that this variable does not expand anything (e.g. IP addresses).
	 * </p>
	 *
	 * @param targetId the target ID
	 * @return a string with the target properties as a string (null if the file was not found)
	 */
	String findRawTargetProperties( String targetId );

	/**
	 * Finds the target ID for a scoped instance within an application or an application template.
	 * @param app an application or application template
	 * @param instancePath an instance path
	 * @return a string, or null if no associated target was found
	 */
	String findTargetId( AbstractApplication app, String instancePath );

	/**
	 * @return a non-null list of targets
	 */
	List<TargetWrapperDescriptor> listAllTargets();

	/**
	 * @param targetId a non-null target ID
	 * @return a wrapper describing the given target, or null if it was not found
	 */
	TargetWrapperDescriptor findTargetById( String targetId );


	// Defining and in relation with hints (contextual help to reduce the number of choices when associating
	// a target and an application instance). Indeed, some targets may be very specific
	// and we thus do not need to list them for some applications as it would not make sense.


	/**
	 * Lists all the available targets for a given application or application template.
	 * <p>
	 * The result is built by listing all the targets and by filtering them with hints.
	 * Indeed, some targets may be "tagged" to be used (or let's say visible) only for some
	 * applications or templates.
	 * </p>
	 * <p>
	 * If <code>app</code> is a template, then we only search for the targets associated with
	 * it. If <code>app</code> is an application, we either search for it or for its template.
	 * </p>
	 *
	 * @param app an application or an application template
	 * @return a non-null list of targetsMngr
	 * @see #addHint(int, AbstractApplication)
	 * @see #removeHint(int, AbstractApplication)
	 */
	List<TargetWrapperDescriptor> listPossibleTargets( AbstractApplication app );

	/**
	 * Adds a hint to a target ID.
	 * <p>
	 * A hint is not a rule. It is only a filter that can be used when
	 * retrieving targets for a given application or template. It could be seen
	 * as a scope. Indeed, some targetsMngr may be specific to an application or an
	 * application template. And in this case, we do not want them to appear for
	 * other applications.
	 * </p>
	 * <p>
	 * If the target ID is invalid, this method does nothing.
	 * </p>
	 *
	 * @param targetId a target ID
	 * @param app an application or an application template
	 * @throws IOException if the hint could not be saved
	 * @see #findTargets(AbstractApplication)
	 */
	void addHint( String targetId, AbstractApplication app ) throws IOException;

	/**
	 * Removes a hint from a target ID.
	 * <p>
	 * A hint is not a rule. It is only a filter that can be used when
	 * retrieving targets for a given application or template. It could be seen
	 * as a scope. Indeed, some targetsMngr may be specific to an application or an
	 * application template. And in this case, we do not want them to appear for
	 * other applications.
	 * </p>
	 * <p>
	 * If the target ID is invalid, this method does nothing.
	 * Same thing if there was no hint between this target and this application
	 * or template.
	 * </p>
	 *
	 * @param targetId a target ID
	 * @param app an application or an application template
	 * @throws IOException if the hint removal could not be saved
	 * @see #findTargets(AbstractApplication)
	 */
	void removeHint( String targetId, AbstractApplication app ) throws IOException;


	// Atomic operations that prevent conflicting actions.
	// As an example, we do not want to be able to delete a target when it is already used.


	/**
	 * Almost equivalent to {@link #findRawTargetProperties(AbstractApplication, String)}.
	 * <p>
	 * However, there are two differences. First, it locks the target.
	 * And second, it expands properties with information extracted from the instance.
	 * </p>
	 * <p>
	 * Once locked, the target cannot be deleted until it has been released.
	 * However, it can be updated (at the user's risks and perils).
	 * </p>
	 *
	 * @param app an application
	 * @param scopedInstance a scoped instance
	 * @return a non-null map of properties (empty if the file was not found)
	 * @throws IOException if the target could not be locked or properties not be read
	 * <p>
	 * In case of exception, you will have to explicitly invoke {@link #unlockTarget(AbstractApplication, Instance)}.
	 * </p>
	 */
	Map<String,String> lockAndGetTarget( Application app, Instance scopedInstance ) throws IOException;


	/**
	 * Unlocks a target.
	 * <p>
	 * If the target had not been locked, this method does nothing.
	 * </p>
	 *
	 * @param app an application
	 * @param scopedInstance a scoped instance
	 * @throws IOException if the target could not be unlocked
	 */
	void unlockTarget( Application app, Instance scopedInstance ) throws IOException;


	// Diagnostics


	/**
	 * Finds usage statistics for a given target.
	 * @param targetId a non-null target ID
	 * @return a non-null list of usage item
	 */
	List<TargetUsageItem> findUsageStatistics( String targetId );
}
