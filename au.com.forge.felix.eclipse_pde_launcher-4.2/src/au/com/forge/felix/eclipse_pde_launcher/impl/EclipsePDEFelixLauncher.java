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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.main.AutoProcessor;
import org.apache.felix.main.Main;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;

import org.apache.felix.framework.FrameworkFactory;

import au.com.forge.eclipse.osgi.autoupdater.impl.EclipseProjectURLAutoUpdater;
import au.com.forge.eclipse.osgi.urlhandler.impl.EPURLHandlerActivator;

/**
 * The Eclipse PDE Felix framework launcher. It automatically installs the
 * Eclipse project directory stream handler and the Eclipse project URL
 * auto-updater.
 * 
 * It is needed to be able to start the Apache Felix framework from an Eclipse
 * launch configuration that specifies startup bundles.
 * 
 * @author Christopher Armstrong
 * 
 */
public class EclipsePDEFelixLauncher {
	private static Felix framework = null;
	private static final String LAUNCHER_CONFIG_PROPERTY_KEY = "au.com.forge.felix.config.properties";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Main.loadSystemProperties();
		Map<String, String> configProperties = Main.loadConfigProperties();
		if (configProperties == null) {
			configProperties = new HashMap<String, String>();
		}
		Main.copySystemProperties(configProperties);

		mergeWithPDELauncherProperties(configProperties);

		String enableHook = configProperties
				.get(Main.SHUTDOWN_HOOK_PROP);
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

			StringMap stringMap = new StringMap(configProperties);
			stringMap.put("felix.systembundle.activators", activators);

			framework = (Felix)frameworkFactory.newFramework(stringMap);
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
			System.err.println("Interrupted waiting for framework to finish: "
					+ e);
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static FrameworkFactory getFrameworkFactory() {
		return new org.apache.felix.framework.FrameworkFactory();
	}

	private static void mergeWithPDELauncherProperties(
			Map<String, String> configProperties) {
		String configUri = System.getProperty(LAUNCHER_CONFIG_PROPERTY_KEY);

		if (configUri == null) {
			// nothing todo
			return;
		}
		try {
			InputStream openStream = URI.create(configUri).toURL().openStream();
			Properties toMergeProperties = new Properties();
			toMergeProperties.load(openStream);
			Enumeration<Object> keys = toMergeProperties.keys();
			while (keys.hasMoreElements()) {
				String key = (String) keys.nextElement();

				if (key.startsWith("felix.auto.")) {
					configProperties.put(
							key,
							mergeFelixBundleAutoOrInstallString(
									(String) configProperties.get(key),
									(String) toMergeProperties.get(key)));

				} else {
					configProperties.put(key, toMergeProperties.getProperty(key));
				}
			}

		} catch (IOException e) {
			System.err
					.println("error while loading PDE felix configuration file: "
							+ e);
			e.printStackTrace();
			// ?!
			// System.exit(1);
		}
	}

	private static String mergeFelixBundleAutoOrInstallString(
			String toBeExtended, String toExtend) {

		StringBuilder builder = new StringBuilder();
		if (toBeExtended != null) {
			builder.append(toBeExtended);
			builder.append(" ");
		}
		builder.append(toExtend);
		return builder.toString();
	}
}
