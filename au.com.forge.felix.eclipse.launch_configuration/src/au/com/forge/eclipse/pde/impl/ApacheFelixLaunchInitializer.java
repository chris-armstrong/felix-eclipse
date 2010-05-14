/**
 *  Eclipse PDE Launch Configuration - An Eclipse plugin that can launch
 *  	Apache Felix in conjunction with the Eclipse Felix PDE Launcher
 *  
 *  Copyright (C) 2010 Forge Research
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package au.com.forge.eclipse.pde.impl;

import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.pde.ui.launcher.OSGiLaunchConfigurationInitializer;

/**
 * A launch configuration initialiser that removes all the project arguments
 * stuffed in by {@link OSGiLaunchConfigurationInitializer} which are
 * relevant to Eclipse Equinox but useless for Apache Felix.
 *  
 * @author Christopher Armstrong
 *
 */
public class ApacheFelixLaunchInitializer extends
		OSGiLaunchConfigurationInitializer {
	public void initialize(ILaunchConfigurationWorkingCopy configuration) {
		super.initialize(configuration);
		// We don't want any launch arguments, as the defaults fail on Felix for OSGi
		configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, "");
	}
}
