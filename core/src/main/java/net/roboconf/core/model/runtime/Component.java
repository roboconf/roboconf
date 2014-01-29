/**
 * Copyright 2013-2014 Linagora, Université Joseph Fourier
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

package net.roboconf.core.model.runtime;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * A component represents a Software item (hardware, software, whatever).
 * @author Vincent Zurczak - Linagora
 */
public interface Component {

	String getName();
	String getAlias();

	File getResourceFile();
	void setResourceFile( File resourceFile );

	String getIconLocation();
	void setIconLocation( String iconLocation );

	String getInstallerName();
	void setInstallerName( String installerName );

	Collection<String> getFacetNames();
	Collection<String> getImportedVariableNames();
	Map<String,String> getExportedVariables();
	Collection<Component> getChildren();
	Collection<Component> getAncestors();
}
