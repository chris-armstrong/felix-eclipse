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
package au.com.forge.felix.eclipse_pde_launcher.impl;

import java.util.ArrayList;
import java.util.Properties;

import org.osgi.framework.launch.FrameworkFactory;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.main.AutoProcessor;
import org.apache.felix.main.Main;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

import au.com.forge.eclipse.osgi.autoupdater.impl.EclipseProjectURLAutoUpdater;
import au.com.forge.eclipse.osgi.urlhandler.impl.EPURLHandlerActivator;

/**
 * The Eclipse PDE Felix framework launcher. It automatically
 * installs the Eclipse project directory stream handler and
 * the Eclipse project URL auto-updater.
 * 
 * It is needed to be able to start the Apache Felix framework
 * from an Eclipse launch configuration that specifies startup
 * bundles.
 * 
 * @author Christopher Armstrong
 *
 */
public class EclipsePDEFelixLauncher {
	private static Framework framework = null;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Main.loadSystemProperties();
		Properties configProperties = Main.loadConfigProperties();
		if (configProperties == null) {
			configProperties = new Properties();
		}
		Main.copySystemProperties(configProperties);

		String enableHook = configProperties
				.getProperty(Main.SHUTDOWN_HOOK_PROP);
		if (enableHook == null || !enableHook.equalsIgnoreCase("false")) {
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					try {
						framework.stop();
						framework.waitForStop(0);
					} catch (Exception e) {
						System.err.println("Error stopping framework: " + e);
					}
				}
			});
		}
		try {
			FrameworkFactory frameworkFactory = getFrameworkFactory();
			ArrayList<BundleActivator> activators = new ArrayList<BundleActivator>();

			// Must put the URL handler first because it is used during
			// the auto-update process.
			activators.add(new EPURLHandlerActivator());
			activators.add(new EclipseProjectURLAutoUpdater());

			StringMap stringMap = new StringMap(configProperties, false);
			stringMap.put("felix.systembundle.activators", activators);
			
			framework = frameworkFactory.newFramework(stringMap);
			framework.init();
			AutoProcessor.process(stringMap, framework.getBundleContext());
			framework.start();
			framework.waitForStop(0);
			System.exit(0);
		} catch (BundleException e) {
			System.err.println("Could not start the framework framework: " + e);
			e.printStackTrace();
			System.exit(1);
		} catch (InterruptedException e) {
			System.err.println("Interrupted waiting for framework to finish: "+e);
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static FrameworkFactory getFrameworkFactory() {
		return new org.apache.felix.framework.FrameworkFactory();
	}

}
