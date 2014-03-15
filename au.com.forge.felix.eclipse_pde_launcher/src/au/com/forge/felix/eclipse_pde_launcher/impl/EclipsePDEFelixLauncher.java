/**
 *  Eclipse PDE Felix Launcher - launches Apache Felix with support for Eclipse PDE project 
 *  directories as bundles
 *  
 *  Copyright (C) 2010 Forge Research
 *  Copyright (C) 2010-2014 Christopher Armstrong <carmstrong@fastmail.com.au>
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

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.FrameworkFactory;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.main.AutoProcessor;
import org.apache.felix.main.Main;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;

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
     * Internal launcher property specifying if the user
     * selected to initialise AWT on MacOS X.
     * 
     * @author MOD_0.2.2 ogattaz 
     * */
    private static final String LAUNCHER_INIT_AWT_KEY = "au.com.forge.felix.config.init.awt";

	/**
	 * Launch the framework
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Main.loadSystemProperties();
		Properties configProperties = Main.loadConfigProperties();
		if (configProperties == null) {
			configProperties = new Properties();
		}
		Main.copySystemProperties(configProperties);

		mergeWithPDELauncherProperties(configProperties);

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
        
		/*
         * MOD_0.2.2 ogattaz
         *
         * Load the awt native library AWT (mapped on top of cocoa) if the
         * property "au.com.forge.felix.config.initawtinmain" contains "true" and
         * if the current OS is "OS X" and the current thread is the main thread
         * of the jvm
         */
        if (mustInitAwt(configProperties)) {
                initAwt(configProperties);
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
			Properties defaultProperties) {
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
					defaultProperties.put(
							key,
							mergeFelixBundleAutoOrInstallString(
									(String) defaultProperties.get(key),
									(String) toMergeProperties.get(key)));

				} else {
					defaultProperties.put(key, toMergeProperties.get(key));
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
    /**
     * Induce AWT initialisation.
     * @author MOD_0.2.2 ogattaz
     *
     * @param configProperties
     */
    private static void initAwt(Properties configProperties) {
            try {
                    int wRgbInt = Color.BLUE.getRGB();
                    System.out.println(String
                    		.format("EclipsePDEFelixLauncher initializes AWT in Thread [%s] OK : Color.BLUE.getRGB=[%s]",
                                               Thread.currentThread().getName(),
                                               Integer.toHexString(wRgbInt)));
            } catch (Exception e) {
                    // if the jvm is an headless one ...
                    System.err
                                    .println(String
                                                    .format("EclipsePDEFelixLauncher thrown an exception during AWT initialisation : %s",
                                                                    e.getMessage()));
                    
                    e.printStackTrace(System.err);
            }
    }

    /**
     * Determine if AWT must be initialised based on the {@link #LAUNCHER_INIT_AWT_KEY}
     * and the operating system.
     * 
     * MOD_0.2.2 ogattaz
     *
     * the name of the property :"au.com.forge.felix.config.init.awt"
     *
     *
     * @param configProperties
     *            the map of the properties given to the launcher
     * @return true is the initialisation of awt is explicitly asked of if the
     *         current OS is "OS X" and if the current Thread is the "main" one.
     */
    private static boolean mustInitAwt(Properties configProperties) {

            String launchAwtProperty = configProperties.getProperty(LAUNCHER_INIT_AWT_KEY);

            if (launchAwtProperty != null) {
                    // Check the property value, and return true only if
                    // the user has set the property to true AND it is the main thread
                    // AND MacOSX
                    boolean wExplicitInitAwt = Boolean.parseBoolean(launchAwtProperty);

                    return wExplicitInitAwt && isMacOsX() && isThreadmain();
            } else {
                    // The user has not set the property, so initialise AWT
                    // if we are running MacOS X and this is the main thread
                    return isMacOsX() && isThreadmain();
            }
    }

    /**
     * Determine if the current thread is also the main thread
     * 
     * MOD_0.2.2 ogattaz
     *
     * Thread.currentThread().getName() => name=[main]
     *
     * @return true if the name of the current Thread is "main"
     */
    private static boolean isThreadmain() {
            return "main".equals(Thread.currentThread().getName());
    }

    /**
     * MOD_0.2.2 ogattaz
     *
     * System property "os.name" contains : OsName=[Mac OS X]
     *
     * @see "Identifying OS X from Java" =>
     *      https://developer.apple.com/library/mac/technotes/tn2002/tn2110.html
     *
     * @return
     */
    private static boolean isMacOsX() {

            String wOsName = getOsName();
            return (wOsName != null && !wOsName.isEmpty()) ? wOsName.toUpperCase()
                            .contains("OS X") : false;
    }

    /**
     * MOD_0.2.2 ogattaz
     *
     * @return the value of the System property "os.name"
     */
    public static String getOsName() {
            return System.getProperty("os.name");
    }
}
