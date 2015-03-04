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

package net.roboconf.core.model.helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import junit.framework.Assert;
import net.roboconf.core.ErrorCode;
import net.roboconf.core.RoboconfError;
import net.roboconf.core.model.ModelError;
import net.roboconf.core.model.ParsingError;
import net.roboconf.core.model.RuntimeModelIo.ApplicationLoadResult;
import net.roboconf.core.model.SourceReference;
import net.roboconf.core.model.beans.Component;

import org.junit.Test;

/**
 * @author Vincent Zurczak - Linagora
 */
public class RoboconfErrorHelpersTest {

	@Test
	public void testContainsCriticalErrors() {

		List<RoboconfError> errors = new ArrayList<RoboconfError> ();
		Assert.assertFalse( RoboconfErrorHelpers.containsCriticalErrors( errors ));

		errors.add( new RoboconfError( ErrorCode.PM_MALFORMED_COMMENT ));
		Assert.assertFalse( RoboconfErrorHelpers.containsCriticalErrors( errors ));

		errors.add( new RoboconfError( ErrorCode.PM_DUPLICATE_PROPERTY ));
		Assert.assertTrue( RoboconfErrorHelpers.containsCriticalErrors( errors ));
	}


	@Test
	public void testFindWarnings() {

		Collection<RoboconfError> errors = new ArrayList<RoboconfError> ();
		Assert.assertEquals( 0, RoboconfErrorHelpers.findWarnings( errors ).size());

		errors.add( new RoboconfError( ErrorCode.PM_DUPLICATE_PROPERTY ));
		Assert.assertEquals( 0, RoboconfErrorHelpers.findWarnings( errors ).size());

		errors.add( new RoboconfError( ErrorCode.PROJ_NO_RESOURCE_DIRECTORY ));
		errors = RoboconfErrorHelpers.findWarnings( errors );
		Assert.assertEquals( 1, errors.size());
		Assert.assertEquals( ErrorCode.PROJ_NO_RESOURCE_DIRECTORY, errors.iterator().next().getErrorCode());
	}


	@Test
	public void testResolveErrorsWithLocation() {
		ApplicationLoadResult alr = new ApplicationLoadResult();
		Component c1 = new Component( "comp1" );
		Component c2 = new Component( "comp2" );

		// Empty case
		Assert.assertEquals( 0, RoboconfErrorHelpers.resolveErrorsWithLocation( alr ).size());

		// Try with an application that contains only a runtime error
		alr.getLoadErrors().add( new RoboconfError( ErrorCode.REC_BASH_NO_SCRIPTS_DIR ));
		alr.getLoadErrors().add( new ModelError( ErrorCode.RM_INVALID_VARIABLE_NAME, c1 ));
		alr.getLoadErrors().add( new ModelError( ErrorCode.RM_INVALID_COMPONENT_INSTALLER, c2 ));

		List<RoboconfError> errors = RoboconfErrorHelpers.resolveErrorsWithLocation( alr );
		Assert.assertEquals( alr.getLoadErrors().size(), errors.size());
		Assert.assertEquals( RoboconfError.class, errors.get( 0 ).getClass());
		Assert.assertEquals( ModelError.class, errors.get( 1 ).getClass());
		Assert.assertEquals( ModelError.class, errors.get( 2 ).getClass());

		// Add location in the context
		alr.getObjectToSource().put( c1, new SourceReference( c1, new File( "whatever" ), 5 ));
		errors = RoboconfErrorHelpers.resolveErrorsWithLocation( alr );

		Assert.assertEquals( alr.getLoadErrors().size(), errors.size());
		Assert.assertEquals( RoboconfError.class, errors.get( 0 ).getClass());
		Assert.assertEquals( ParsingError.class, errors.get( 1 ).getClass());
		Assert.assertEquals( ErrorCode.RM_INVALID_VARIABLE_NAME, errors.get( 1 ).getErrorCode());
		Assert.assertEquals( 5, ((ParsingError) errors.get( 1 )).getLine());
		Assert.assertEquals( ModelError.class, errors.get( 2 ).getClass());
	}
}
