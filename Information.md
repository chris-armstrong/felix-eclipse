# Introduction #

This project provides Eclipse PDE support for the Apache Felix framework. At the moment, we have a plugin for the Eclipse PDE environment that provides a custom launch configuration for Apache Felix under the _OSGi Frameworks_ option of Eclipse _Run/Debug Configurations_.



# Usage #

The plugin does not contain a copy of Apache Felix. You need to ensure that it is in your target platform, as per the following instructions.

After installing the plugin, you need to ensure that your Eclipse PDE Target Platform contains at least the following plugins:
  1. Apache Felix Main bundle (org.apache.felix.main)
  1. Apache Felix Shell bundle (org.apache.felix.shell)
  1. Apache Felix Shell TUI bundle (org.apache.felix.shell.tui)

You need all three plugins if you wish to debug Felix as an OSGi framework with console support. The launcher can operate with only the first one, but you will not have a console in the Eclipse **Console** window (it will be a headless OSGi framework). These plugins can be downloaded separately from the [Apache Felix Downloads Page](http://felix.apache.org/site/downloads.cgi).

You can install these plugins into your target platform by copying them into your target platform directory, or reconfiguring your target platform to include a directory with the plugins. **When you copy the plugins into your target platform, don't forget to Reload it.**

You can modify your Eclipse Target Platform by opening the **Preferences window** (Window->Preferences on Windows/Linux/Other compatible Unixes; Eclipse->Preferences on Mac) and then selecting and expanding **Plug-in Development** and then **Target Platform**.

## Launcher properties ##

The internal launcher supports extra properties which control it's behaviour. These are listed below:

  * `au.com.forge.felix.config.init.awt` (boolean): This property causes AWT to be initialised before Felix is launched on MacOS X so that AWT will be started on the main thread. It defaults to true if unspecified. However, it only has affect when the felix-eclipse launcher is run on MacOS X.

**To use any of the above, add a ` -Dpropertyname=propertyvalue ` to the launch configuration on the _Arguments_ tab in the _VM Arguments_ section.**

## build.properties file ##
If you have a project that needs to run with more resources than just META-INF/MANIFEST.MF, you may need to configure your build.properties file. This is often the case when using maven2 for building and when you don't use the File->Export feature at all.

Eclipse usually exports plugins using the File->Export... option for plugin projects. This uses a custom built-in ant task which reads the build.properties file in the project to guide the build. If this file is missing, Eclipse just uses some sensible defaults, which seem to be:

  * Everything under the output directories gets copied into the root of the .jar. For example, if your source output directory is bin/, everything under this directory will be placed in the root of the .jar
  * META-INF/MANIFEST.MF is always included.

If you usually don't use the built-in Eclipse export feature for your bundles, you may not realise that it is unusable (although, if you debug with Pax Runner, bad build.properties file will prevent you from exporting and starting up an OSGi framework as it uses the export feature in Eclipse internally). When you have other resources which need to be copied to the root of the bundle, you will need to make sure they have been selected from the build.properties page under "Binary Build" section. If you don't have a build.properties file, please create one if you need to support extra resources.

# How it works #

The PDE support consists of two parts - an Eclipse launch configuration plugin and a custom Apache Felix launcher. The launch configuration plugin builds a config.properties file for launching Apache Felix.

Each bundle that you selected in your launch configuration will be included in the config.properties file. Those bundles which are Eclipse PDE projects will be included with a pseudo URL protocol called "eclipse-project".

The custom launcher, which is installed in your Eclipse Platform (or if it isn't, a copy must be in the target platform) installs a fake URLStreamHandler for the eclipse-project protocol which is able to load Eclipse project directories as bundles.

The URL handler will read the Eclipse configuration for the PDE project and use it to generate a bundle in memory. The project configuration that we use consists of the contents of the .classpath file (which is used to determine the binary .class files from the output directories), and the build.properties file, which is usually used by the Eclipse bundle export feature's ant task. The stream for this bundle is then passed back to Felix and used to "install" the bundle into the framework.

Each time Felix is restarted, it will automatically issue a Bundle.update() call against each of the eclipse-project: bundles. It does not automatically update any of the normal .jar files installed into the framework from your Target Platform.

# Future Improvements #

The in-memory bundle generation is not ideal but needed to get Felix to read an Eclipse project directory as a bundle. It could be possible to add Eclipse project directory support directly into Apache Felix, but it would probably require alot of refactoring if it was to be implemented in the same way that Equinox supports it.

# Limitations and Known Issues #

  * Apache Felix 2.0.0 and above are supported, as well as Felix 4.2 and above(there was an API change in v4.2 for launchers that necessitates two internal versions). Versions below 2.0.0 are not supported and there is no plans to do so.
  * In-memory bundle generation. This should not be a problem unless you have huge bundles .
  * Everything in your project directory may make it into the generated bundle due to the way Eclipse works and the need to emulate Eclipse Equinox PDE launch configuration debug behaviour. Combined with the above, large files lying around your project directory may be problematic (the worst case is slow startup).
  * I don't know if Apache Karaf works with it. Patches obviously welcome.