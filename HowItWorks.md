# How it works #

PDE project support in Felix is provided through a custom launcher that adds support for treating Eclipse project directories as bundles. This is all performed without modifying your project configuration or exporting each bundle. Bundles are built in-memory from the already compiled code and other resources in your project, which is significantly faster than the full build and export mechanism used by some other launchers.

The integration between Eclipse PDE and Apache Felix through two components:
  * **PDE Launch Configuration Support for Apache Felix** - It provides PDE launch configuration support in a similar fashion to how Eclipse supports debugging Equinox framework instances: you just select the bundles you want to run in your launch configuration, and the plugin takes care of the framework initialisation and bootstrapping based on what you selected using the custom launcher.
  * **Apache Felix launcher** - A custom Felix launcher is used to automatically install the selected bundles directly into the Felix framework instance that is created for debugging.  It adds support for Eclipse projects directly into the Felix framework without needing to export each project as a JAR bundle.

This plugin works with the Apache Felix framework. You just need to install this plugin and provide a copy of Apache Felix framework (2.0 series or higher) in your Eclipse Target Platform.