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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileStore;
import org.eclipse.core.filesystem.provider.FileSystem;
import org.eclipse.core.internal.resources.Project;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.ui.launcher.AbstractPDELaunchConfiguration;
import org.eclipse.pde.ui.launcher.IPDELauncherConstants;
import org.osgi.framework.Bundle;

/**
 * Apache Felix launch configuration for Eclipse. This plugin depends on the
 * custom launcher specified by {@link #LAUNCHER_PLUGIN_ID}.
 * 
 * @author Christopher Armstrong
 * 
 */
public class ApacheFelixLaunchConfiguration extends
		AbstractPDELaunchConfiguration {

	/**
	 * The main class in the Eclipse PDE Felix Launcher bundle.
	 */
	private static final String LAUNCHER_PLUGIN_MAIN_CLASS = "au.com.forge.felix.eclipse_pde_launcher.impl.EclipsePDEFelixLauncher";
	// private static final String URL_HANDLER_PLUGIN_ID =
	// "au.com.forge.osgi.eclipse_url_handler";

	/**
	 * The identifier of this plugin.
	 */
	private static final String PLUGIN_ID = "au.com.forge.felix.eclipse.launch_configuration";
	/**
	 * The identifier of the Eclipse PDE Felix Launcher
	 */
	private static final String LAUNCHER_PLUGIN_ID = "au.com.forge.felix.eclipse_pde_launcher";
	
	private static final String LAUNCHER_CONFIG_PROPERTY_KEY = "au.com.forge.felix.config.properties";

	/**
	 * Create the list of program arguments that are to be passed to the Felix
	 * Application launcher. This implementation passes the arguments generated
	 * by the parent class, and adds:
	 * <dl>
	 * <dt><bundle-cache>
	 * <dd>The location of the bundle cache. This is calculated to be a
	 * subdirectory called <code>bundle-cache</code> of the configuration
	 * directory (<code>{@link #getConfigDir(ILaunchConfiguration)}</code>). It
	 * is created if it does not exist.
	 * </dl>
	 * <p>
	 * This is different from the VM arguments, which are the parameters passed
	 * to the virtual machine, not the application being launched.
	 */
	public String[] getProgramArguments(ILaunchConfiguration configuration)
			throws CoreException {
		List arguments = new ArrayList();

		File bundleCacheDir = new File(getConfigDir(configuration),
				"bundle-cache");
		if (!bundleCacheDir.exists())
			bundleCacheDir.mkdir();
		arguments.add("bundle-cache");

		arguments.addAll(Arrays.asList(configuration.getAttribute(
				IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, "")
				.split(" ")));
		return (String[]) arguments.toArray(new String[arguments.size()]);
	}
	protected void clear(ILaunchConfiguration configuration,
			IProgressMonitor monitor) throws CoreException {
		File bundleCacheDir = new File(getConfigDir(configuration),
		"bundle-cache");
		if (configuration.getAttribute(IPDELauncherConstants.CONFIG_CLEAR_AREA, false)) {
			if (bundleCacheDir.exists())
			{
				IFileStore bundleCacheStore = EFS.getStore(bundleCacheDir.toURI());
				bundleCacheStore.delete(EFS.NONE, monitor);
			}
		}
	}

	/**
	 * Calculate the classpath of the OSGi instance we create (the Felix
	 * launcher). This method adds the Apache Felix main bundle
	 * (org.apache.felix.main) and the custom Eclipse-Felix launcher bundle that
	 * is packaged with this plugin.
	 */
	public String[] getClasspath(ILaunchConfiguration configuration)
			throws CoreException {
		ArrayList classpath = new ArrayList();
		IPluginModelBase apacheFelixBundle = PluginRegistry
				.findModel("org.apache.felix.main");
		if (apacheFelixBundle == null)
			throw new CoreException(
					new Status(
							Status.CANCEL,
							PLUGIN_ID,
							"Unable to find an Apache Felix main bundle (org.apache.felix.main) in the workspace or the Target Platform."));
		if (apacheFelixBundle.getInstallLocation() == null)
			throw new CoreException(
					new Status(
							Status.CANCEL,
							PLUGIN_ID,
							"Found Felix plugin in workspace, but unable to find its corresponding JAR file."));
		classpath.add(apacheFelixBundle.getInstallLocation());

		classpath.addAll(calculateNeededClassPath(LAUNCHER_PLUGIN_ID));
		return (String[]) classpath.toArray(new String[classpath.size()]);
	}

	/**
	 * Calculate the classpath of the specified plugin that lives in the Eclipse
	 * installation.
	 * 
	 * @param pluginId
	 *            The OSGi plugin ID to calculate the classpath for.
	 * @return A collection of classpath elements
	 * @throws CoreException
	 *             Thrown when the specified plugin cannot be found in the
	 *             workspace or Eclipse installation.
	 */
	private Collection calculateNeededClassPath(String pluginId)
			throws CoreException {
		ArrayList classpath = new ArrayList();
		IPluginModelBase model = PluginRegistry.findModel(pluginId);
		if (model != null && model.getUnderlyingResource() == null) {
			classpath.add(model.getInstallLocation());
			return classpath;
		}
		Bundle bundle = Platform.getBundle(pluginId);
		if (bundle != null) {
			try {
				File bundleFile = FileLocator.getBundleFile(bundle);
				classpath.add(bundleFile.getAbsolutePath());
				return classpath;
			} catch (IOException e) {
			}
		}

		throw new CoreException(new Status(Status.CANCEL, PLUGIN_ID,
				"Unable to find required library " + pluginId
						+ " packaged as a JAR in the target "
						+ "platform, Eclipse installation or workspace."));
	}

	/**
	 * Determine the arguments to pass to the sub-process VM. This method
	 * extends the super method and adds the location of the felix configuration
	 * properties file, which in turn is generated by this
	 */
	public String[] getVMArguments(ILaunchConfiguration configuration)
			throws CoreException {
		ArrayList vmArguments = new ArrayList();
		vmArguments.addAll(Arrays.asList(super.getVMArguments(configuration)));
		vmArguments.add(String.format("-D%s=", new Object[] {LAUNCHER_CONFIG_PROPERTY_KEY})
				+ createConfigurationProperties(configuration));
		return (String[]) vmArguments.toArray(new String[vmArguments.size()]);
	}

	/**
	 * Get the Main class of the launcher. This value is specified by
	 * {@link #LAUNCHER_PLUGIN_MAIN_CLASS}.
	 */
	public String getMainClass() {
		return LAUNCHER_PLUGIN_MAIN_CLASS;
	}

	/**
	 * Generate the configuration properties file that is passed to the Felix
	 * Launcher. This takes the configuration specified in the Launch
	 * Configuration dialog and translates it into Felix properties.
	 * 
	 * @param configuration
	 *            The launch configuration, which contains the user-specified
	 *            properties
	 * @return The URI of the properties file, which is passed to the Felix
	 *         launcher
	 * @throws CoreException
	 *             Thrown when there is a problem writing the configuration
	 *             properties to the filesystem.
	 */
	public String createConfigurationProperties(
			ILaunchConfiguration configuration) throws CoreException {
		// Felix conf/ directory, inside the Eclipse "configuration area"
		
		File confDir = new File(getConfigDir(configuration), "conf");
		if (!confDir.exists())
			confDir.mkdir();
		File configPropertiesFile = new File(confDir, "config.properties");
		Properties configProperties = new Properties();

		configProperties.setProperty("felix.cache.rootdir", getConfigDir(
				configuration).getAbsolutePath());
		configProperties.setProperty("org.osgi.framework.storage",
				"bundle-cache");
		configProperties
				.setProperty("org.osgi.framework.storage.clean", "none");

		Integer defaultStartLevel = new Integer(configuration.getAttribute(
				IPDELauncherConstants.DEFAULT_START_LEVEL, 4));
		configProperties.setProperty("org.osgi.framework.startlevel.beginning",
				defaultStartLevel.toString());
		boolean defaultAutoStart = configuration.getAttribute(
				IPDELauncherConstants.DEFAULT_AUTO_START, true);

		Map startLevelBundles = new HashMap(); // Installed+Started bundles
		Map installLevelBundles = new HashMap(); // Installed only bundles

		// Parse the list of selected Target Platform bundles
		String targetPlatformPluginList = configuration.getAttribute(
				IPDELauncherConstants.TARGET_BUNDLES, (String) null);
		if (targetPlatformPluginList != null) {
			String[] targetPlugins = targetPlatformPluginList.split(",");

			parseBundleList(defaultStartLevel, defaultAutoStart, targetPlugins,
					startLevelBundles, installLevelBundles, "file");
		}

		// Parse the list of selected Workspace bundles
		String workspacePluginList = configuration.getAttribute(
				IPDELauncherConstants.WORKSPACE_BUNDLES, (String) null);
		if (workspacePluginList != null) {
			String[] workspacePlugins = workspacePluginList.split(",");
			parseBundleList(defaultStartLevel, defaultAutoStart,
					workspacePlugins, startLevelBundles, installLevelBundles,
					"eclipse-project");
		}

		// // Find the Eclipse URL handler bundle (disabled, URL handler done by
		// launcher)
		// String eclipseProjectHandlerURL = findURLHandlerBundle();
		//
		// List zeroStartLevel = (List) startLevelBundles.get(new Integer(1));
		// if (zeroStartLevel == null) {
		// zeroStartLevel = new ArrayList();
		// startLevelBundles.put(new Integer(1), zeroStartLevel);
		// }
		// zeroStartLevel.add(eclipseProjectHandlerURL);

		writeBundles(startLevelBundles, "felix.auto.start.", configProperties);
		writeBundles(installLevelBundles, "felix.auto.install.",
				configProperties);

		try {
			configProperties.store(new FileOutputStream(configPropertiesFile),
					"");
		} catch (IOException e) {
			throw new CoreException(
					new Status(Status.CANCEL, PLUGIN_ID,
							"Unable to write config.properties file: "
									+ e.getMessage(), e));
		}
		return configPropertiesFile.toURI().toString();
	}


	/**
	 * Parse the list of bundles.
	 * 
	 * @param defaultStartLevel
	 *            The default start level.
	 * @param defaultAutoStart
	 *            A boolean indicating if bundles should be auto-started.
	 * @param targetPlugins
	 *            The list of plugins in the target, specified as plugin
	 *            identifiers
	 * @param startLevelBundles
	 *            The map of start level => bundles (bundles which start
	 *            automatically)
	 * @param installLevelBundles
	 *            The map of install level => bundles (bundles which are to be
	 *            installed only)
	 * @param protocol
	 *            The protocol used to launch the plugin.
	 * @throws CoreException
	 *             Thrown when an error occurs parsing the target platform
	 *             plugin names.
	 */
	private void parseBundleList(Integer defaultStartLevel,
			boolean defaultAutoStart, String[] targetPlugins,
			Map startLevelBundles, Map installLevelBundles, String protocol)
			throws CoreException {
		for (int i = 0; i < targetPlugins.length; i++) {
			String[] componentParts = targetPlugins[i].split("@|:|\\*", 4);
			final String bundleName = componentParts[0];
			Integer startLevel;
			boolean autostart;
			String version;
			if (componentParts.length == 4)
			{
				version = componentParts[1];
				
				autostart = componentParts[3].equals("default") ? defaultAutoStart
						: Boolean.valueOf(componentParts[2]).booleanValue();

				startLevel = componentParts[2].equals("default") ? defaultStartLevel
						: Integer.valueOf(componentParts[1]);
			}
			else if (componentParts.length == 3)
			{
				autostart = componentParts[2].equals("default") ? defaultAutoStart
						: Boolean.valueOf(componentParts[2]).booleanValue();

				startLevel = componentParts[1].equals("default") ? defaultStartLevel
						: Integer.valueOf(componentParts[1]);
				version = null;
			}
			else // (componentParts.length < 3)
				throw new CoreException(new Status(Status.CANCEL, PLUGIN_ID,
						"Problem parsing target/workspace bundle strings."));

			
			List bundles;
			if (autostart) {
				bundles = (List) startLevelBundles.get(startLevel);
				if (bundles == null) {
					bundles = new ArrayList();
					startLevelBundles.put(startLevel, bundles);
				}
			} else {
				bundles = (List) installLevelBundles.get(startLevel);
				if (bundles == null) {
					bundles = new ArrayList();
					installLevelBundles.put(startLevel, bundles);
				}
			}

			IPluginModelBase pluginModelBase = findBundleModel(bundleName, version);
			bundles.add(protocol + ":" + pluginModelBase.getInstallLocation());
		}
	}

	private IPluginModelBase findBundleModel(final String bundleName, final String version) {
		if (version == null)
			return PluginRegistry
					.findModel(bundleName);
		else
		{
			IPluginModelBase[] activeModels = PluginRegistry.getActiveModels();
			IPluginModelBase model;
			for (int i= 0; i < activeModels.length; i++)
			{
				
				model = activeModels[i];
				BundleDescription desc = model.getBundleDescription();
				if (desc != null &&
						desc.getName().equals(bundleName) &&
						desc.getVersion().toString().equals(version))
					return model;
			}
		}
		return null;
	}

	/**
	 * Write the list of bundles and their start levels to the configuration
	 * file.
	 * 
	 * @param startLevelBundles
	 *            A map of start level => array of strings containing bundle
	 *            names
	 * @param propertyPrefix
	 *            The prefix of the property used to auto-start / auto-install
	 *            that start level
	 * @param configProperties
	 *            The set of configuration properties which will be written to
	 *            the configuration properties file for Felix
	 */
	private void writeBundles(Map startLevelBundles, String propertyPrefix,
			Properties configProperties) {

		Iterator startLevelIt = startLevelBundles.entrySet().iterator();
		while (startLevelIt.hasNext()) {
			StringBuffer bundleList = new StringBuffer();
			Entry entry = (Entry) startLevelIt.next();
			Integer startLevel = (Integer) entry.getKey();
			List bundles = (List) entry.getValue();
			Iterator bundleIt = bundles.iterator();
			while (bundleIt.hasNext()) {
				String bundleUrl = (String) bundleIt.next();
				bundleList.append(bundleUrl);
				bundleList.append(" ");
			}
			configProperties.setProperty(
					propertyPrefix + startLevel.toString(), bundleList
							.toString());
		}
	}
	/**
	 * Write the list of bundles and their start levels to the configuration
	 * file.
	 * 
	 * @param startLevelBundles
	 *            A map of start level => array of strings containing bundle
	 *            names
	 * @param propertyPrefix
	 *            The prefix of the property used to auto-start / auto-install
	 *            that start level
	 * @param configProperties
	 *            The set of configuration properties which will be written to
	 *            the configuration properties file for Felix
	 */
	private void writeBundles(Map startLevelBundles, String propertyPrefix,
			List props) {
		Iterator startLevelIt = startLevelBundles.entrySet().iterator();
		while (startLevelIt.hasNext()) {
			StringBuffer bundleList = new StringBuffer();
			Entry entry = (Entry) startLevelIt.next();
			Integer startLevel = (Integer) entry.getKey();
			List bundles = (List) entry.getValue();
			Iterator bundleIt = bundles.iterator();
			while (bundleIt.hasNext()) {
				String bundleUrl = (String) bundleIt.next();
				bundleList.append(bundleUrl);
				bundleList.append(" ");
			}
			props.add(String.format("-D%s%d=%s", new Object[]{propertyPrefix , startLevel, bundleList
							.toString()}));
		}
	}
}
