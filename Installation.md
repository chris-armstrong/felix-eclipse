# Installation #

## Eclipse 3.6 ##
The plugin is currently distributed as a zipped update site.

  1. Goto [Downloads on Google Drive](https://googledrive.com/host/0Bx-zGLMA4ZsOY0EzTXUyd3VfQkk/) and obtain the latest update site zip and download it.
  1. In Eclipse, goto **Help->Install New Software** and select **Add**
  1. Click on **Archive**, and then navigate to the directory that contains the downloaded update site and select the zip file.
  1. (Optionally) give a name to the Update Site like **Apache Felix Eclipse PDE Integration (v0.1.4)** and then click OK.
  1. In Eclipse 3.6, you will need to select the newly created update site from the **Work With:** list. It should be under a special URL containing the path to the downloaded zip
  1. Select the feature _Apache Felix PDE Support for Eclipse_ and then click Next and follow the prompts for Installation. The feature is not cryptographically signed and nor is the plugins, so you will get a warning about installing unsigned plugins.