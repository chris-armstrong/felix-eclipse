/**
 *  Eclipse PDE Felix Launcher - launches Apache Felix with support for Eclipse PDE project 
 *  directories as bundles
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
package au.com.forge.eclipse.osgi.autoupdater.impl;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Automatically updates all the eclipse-project bundles because Felix won't on
 * its own.
 * 
 * @author Christopher Armstrong
 * 
 */
public class EclipseProjectURLAutoUpdater implements BundleActivator {

	public void start(BundleContext context) throws Exception {
		Bundle[] bundles = context.getBundles();
		for (int i = 0; i < bundles.length; i++) {
			Bundle b = bundles[i];
			if (b.getLocation().startsWith("eclipse-project:")) {
				b.update();
			}
		}
	}

	public void stop(BundleContext arg0) throws Exception {

	}
}
