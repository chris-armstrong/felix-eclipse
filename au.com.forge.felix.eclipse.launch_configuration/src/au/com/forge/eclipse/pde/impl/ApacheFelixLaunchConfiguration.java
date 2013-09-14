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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.pde.core.plugin.IMatchRules;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.launching.AbstractPDELaunchConfiguration;
import org.eclipse.pde.launching.IPDELauncherConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

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
	private static final String LAUNCHER_PLUGIN_ID_LEGACY = "au.com.forge.felix.eclipse_pde_launcher";
	
	private static final String LAUNCHER_PLUGIN_ID_FELIX42 = "au.com.forge.felix.eclipse_pde_launcher-4.2";
	
	private static final String LAUNCHER_CONFIG_PROPERTY_KEY = "au.com.forge.felix.config.properties";

	/**
	 * The bundle name of the Apache Felix main bundle.
	 */
	private static final String APACHE_FELIX_MAIN_BUNDLE = "org.apache.felix.main";
	
	/**
	 * Internal representation of a plugin (aka bundle).
	 * @author chris
	 *
	 */
	private static class PluginSpec
	{
		public PluginSpec(String bundleName2, String version2,
				Integer startLevel2, boolean autostart2) {
			this.bundleName = bundleName2;
			this.version = version2;
			this.startLevel = startLevel2;
			this.autostart = autostart2;
		}
		public String bundleName;
		public String version;
		public Integer startLevel;
		public boolean autostart;
	}
	
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
		{
			IFileStore bundleCacheStore = EFS.getStore(bundleCacheDir.toURI());
			bundleCacheStore.delete(EFS.NONE, monitor);
		}
		if (configuration.getAttribute(IPDELauncherConstants.CONFIG_CLEAR_AREA, false)) {
			File confDir = new File(getConfigDir(configuration), "conf");
			IFileStore confDirStore = EFS.getStore(confDir.toURI());
			confDirStore.delete(EFS.NONE, monitor);
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
		
		// Find all the Apache Felix "main" bundles in the target platform
		IPluginModelBase[] apacheFelixBundles = PluginRegistry
				.findModels(APACHE_FELIX_MAIN_BUNDLE, null, null);
		
		if (apacheFelixBundles.length == 0)
			throw new CoreException(
					new Status(
							Status.CANCEL,
							PLUGIN_ID,
							"Unable to find any Apache Felix main bundle (org.apache.felix.main) in the workspace or the Target Platform."));
		// Find the selected apache felix bundle version
		Version targetVersion = findTargetApacheFelixVersion(configuration);
		IPluginModelBase apacheFelixBundle = null;
		if (targetVersion != null)
		{
			for (int i= 0; i < apacheFelixBundles.length; i++ )
			{
				if (apacheFelixBundles[i].getBundleDescription().getVersion().equals(targetVersion))
				{
					apacheFelixBundle = apacheFelixBundles[i];
					break;
				}
			}
			if (apacheFelixBundle == null)
			{
				throw new CoreException(new Status(Status.ERROR, PLUGIN_ID, "Could not find bundle location for selected Apache Felix Main version "+targetVersion+" among Apache Felix Main bundles found in target platform "+Arrays.toString(apacheFelixBundles)));
			}
		}
		else
		{
			apacheFelixBundle = apacheFelixBundles[0];
		}
		if (apacheFelixBundle.getInstallLocation() == null)
			throw new CoreException(
					new Status(
							Status.ERROR,
							PLUGIN_ID,
							"Found Felix plugin in workspace, but unable to find its corresponding JAR file."));
		classpath.add(apacheFelixBundle.getInstallLocation());

		Version felixBundleVersion = apacheFelixBundle.getBundleDescription().getVersion();
		int major = felixBundleVersion.getMajor();
		int minor = felixBundleVersion.getMinor();
		Collection felix42LauncherClasspath = calculateNeededClassPath(LAUNCHER_PLUGIN_ID_FELIX42);
		Collection legacyLauncherClasspath = calculateNeededClassPath(LAUNCHER_PLUGIN_ID_LEGACY);
		
		if ((major == 4 && minor >= 2) || (major > 4))
		{
			if (felix42LauncherClasspath != null)
				classpath.addAll(felix42LauncherClasspath);
			else
				throw new CoreException(new Status(Status.ERROR, PLUGIN_ID,
						"Unable to find required Eclipse-Felix launcher plugin for Apache Felix 4.2 or newer"
								+ " packaged as a JAR in the target "
								+ "platform, Eclipse installation or workspace."));
		} 
		else
		{
			if (legacyLauncherClasspath != null)
				classpath.addAll(legacyLauncherClasspath);
			else
				throw new CoreException(new Status(Status.ERROR, PLUGIN_ID,
					"Unable to find required Eclipse-Felix launcher plugin for older Apache Felix (older than 4.2)"
							+ " packaged as a JAR in the target "
							+ "platform, Eclipse installation or workspace."));

		}

		return (String[]) classpath.toArray(new String[classpath.size()]);
	}

	private Version findTargetApacheFelixVersion(
			ILaunchConfiguration configuration) throws CoreException {
		String[] targetPlugins = getTargetPluginList(configuration);
		for (int i = 0; i < targetPlugins.length; i++)
		{
			String targetPlugin = targetPlugins[i];
			PluginSpec pluginSpec = parseTargetPluginString(targetPlugin, false, null);
			if (pluginSpec.bundleName.equals(APACHE_FELIX_MAIN_BUNDLE))
				return pluginSpec.version != null ? new Version(pluginSpec.version) : null;
		}
		return null;
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
		return null;
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
	private String createConfigurationProperties(
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
		String[] targetPlugins = getTargetPluginList(configuration);
		if (targetPlugins != null) {
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
					new Status(Status.ERROR, PLUGIN_ID,
							"Unable to write config.properties file: "
									+ e.getMessage(), e));
		}
		return configPropertiesFile.toURI().toString();
	}

	/**
	 * Retrieve the target plugin list from the launch configuration
	 * @param configuration launch configuration
	 * @return target plugin list
	 * @throws CoreException
	 */
	private String[] getTargetPluginList(ILaunchConfiguration configuration) throws CoreException {
		String targetPlatformPluginList = configuration.getAttribute(
				IPDELauncherConstants.TARGET_BUNDLES, (String) null);
		String [] targetPlugins = null;
		 if (targetPlatformPluginList != null) {
			 targetPlugins= targetPlatformPluginList.split(",");
		 }
		 return targetPlugins;
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
			String targetPlugin = targetPlugins[i];
			PluginSpec plugin = parseTargetPluginString(targetPlugin, defaultAutoStart, defaultStartLevel);

			
			List bundles;
			if (plugin.autostart) {
				bundles = (List) startLevelBundles.get(plugin.startLevel);
				if (bundles == null) {
					bundles = new ArrayList();
					startLevelBundles.put(plugin.startLevel, bundles);
				}
			} else {
				bundles = (List) installLevelBundles.get(plugin.startLevel);
				if (bundles == null) {
					bundles = new ArrayList();
					installLevelBundles.put(plugin.startLevel, bundles);
				}
			}

			IPluginModelBase pluginModelBase = findBundleModel(plugin.bundleName, plugin.version);
			if (pluginModelBase == null)
			{
				throw new CoreException(new Status(Status.ERROR, PLUGIN_ID, "Unable to load plugin model for bundle '"+plugin.bundleName+"' version "+plugin.version));
			}
			bundles.add(protocol + ":" + pluginModelBase.getInstallLocation());
		}
	}

	private PluginSpec parseTargetPluginString(String targetPlugin, boolean defaultAutoStart, Integer defaultStartLevel) throws CoreException {
		String [] pluginSpec = targetPlugin.split("@", 2);
		
		if (pluginSpec.length != 2)
		{
			throw new CoreException(new Status(Status.ERROR, PLUGIN_ID, "Unexpected target plugin specification: "+targetPlugin));
		}
		final String bundleSpec = pluginSpec[0];
		final String startSpec = pluginSpec[1];
		final String[] nameParts = bundleSpec.split("\\*", 2);
		final String[] componentParts = startSpec.split(":", 2);
		
		Integer startLevel;
		boolean autostart;
		String version = null;
		String bundleName;
		if (componentParts.length == 2)
		{
			autostart = componentParts[1].equals("default") ? defaultAutoStart
					: Boolean.valueOf(componentParts[1]).booleanValue();

			startLevel = componentParts[0].equals("default") ? defaultStartLevel
					: Integer.valueOf(componentParts[0]);
			
			bundleName = nameParts[0];
			if (nameParts.length == 2)
			{
				version = nameParts[1];
			}
		}
		else // (componentParts.length < 3)
			throw new CoreException(new Status(Status.CANCEL, PLUGIN_ID,
					"Problem parsing target/workspace bundle strings."));
		
		return new PluginSpec(bundleName, version, startLevel, autostart);
	}

	private IPluginModelBase findBundleModel(final String bundleName, final String version) {
		if (version == null)
			return PluginRegistry
					.findModel(bundleName);
		else
		{
			return PluginRegistry.findModel(bundleName, version, IMatchRules.PERFECT, null);
//			IPluginModelBase[] activeModels = PluginRegistry.getActiveModels();
//			IPluginModelBase model;
//			for (int i= 0; i < activeModels.length; i++)
//			{
//				
//				model = activeModels[i];
//				BundleDescription desc = model.getBundleDescription();
//				if (desc != null &&
//						desc.getName().equals(bundleName) &&
//						desc.getVersion().toString().equals(version))
//					return model;
//			}
		}
//		return null;
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
