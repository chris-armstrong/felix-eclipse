 **This project is no longer maintained.**

Please consider alternatives such as:
  * [Pax Runner](https://ops4j1.jira.com/wiki/spaces/paxrunner/overview)
  * Eclipse - Eclipse itself should have builtin support for Apache Felix runtimes now

# About #

**felix-eclipse** is a plugin for the Eclipse Plugin Development Environment (PDE) that integrates Apache Felix as an OSGi framework in Eclipse. It makes it easy to debug Apache Felix as an Eclipse target platform.

# Download #

felix-eclipse is distributed as an zipped update site. The current version is [0.2.2](https://googledrive.com/host/0Bx-zGLMA4ZsOY0EzTXUyd3VfQkk/au.com.forge.felix.eclipse.update_site-0.2.2.zip).

[Downloads List](https://googledrive.com/host/0Bx-zGLMA4ZsOY0EzTXUyd3VfQkk/) (Google Drive)

# Features #

  * **Debug OSGi bundles with Apache Felix directly from Eclipse**: this plugin lets you build OSGi launch configurations that debug directly with Apache Felix, just like you can with Equinox. Just select _Apache Felix_ as your target platform, select the bundles you want to debug with and then launch.
  * **Fast launch time**: felix-eclipse builds your bundles in-memory and on-the-fly, which means that Felix framework is ready to go almost as quickly as you launch it. Other Eclipse launch methods for OSGi compile and assemble your bundles using Eclipse before they start, which is significantly slower.
  * **Update your bundles while debugging**: if you change something in your code or a resource in your bundle while you are debugging, you can update it directly in Apache Felix by just updating the bundle (like you can with Equinox). Other frameworks require you to stop the framework and restart it to update even one bundle.

The wiki contains information about [how it works](HowItWorks.md).

# Installation #

[Installation instructions are provided here](Installation.md).

We currently distribute it as an Eclipse update site in a zip file. I am searching for a good solution to host it as a proper update site.

**It is highly recommended that you read the information page about the plugin**, which can be found at the [Information Wiki page](Information.md).


# Support #

  * **Mailing List:** There is a Google Groups page at http://groups.google.com/group/felix-eclipse-discuss for discussing the project.
  * **Issues, Bugs and Feature Requests:** File bug reports or feature requests at the [issues page](https://github.com/chris-armstrong/felix-eclipse/issues).






