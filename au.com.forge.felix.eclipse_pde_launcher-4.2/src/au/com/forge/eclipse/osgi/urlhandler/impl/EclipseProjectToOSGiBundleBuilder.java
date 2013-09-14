package au.com.forge.eclipse.osgi.urlhandler.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A disposable class used to convert an Eclipse project directory into
 * a JAR bundle. It uses the .classpath and build.properties file to
 * assemble the bundle. It assumes the Java source files in the project
 * directory have already been compiled by Eclipse.
 * 
 * @author chris
 *
 */
public class EclipseProjectToOSGiBundleBuilder {
	private static final int MAX_RECURSE_DEPTH = 256;
	private final SAXParserFactory saxParserFactory;

	private final ZipOutputStream zipOutputStream;
	private final File projectDirectory;
	private final Set<String> addedEntries = new HashSet<String>();

	/**
	 * Create a new OSGi bundle builder
	 * @param projectDirectory the Eclipse project directory
	 * @param outputBundleStream the output stream to write the bundle to
	 * @param saxParserFactory A SAX parser factory (needed to generate a SAX parser)
	 */
	public EclipseProjectToOSGiBundleBuilder(File projectDirectory, OutputStream outputBundleStream, SAXParserFactory saxParserFactory) {
		this.projectDirectory = projectDirectory;
		this.zipOutputStream = new ZipOutputStream(outputBundleStream);
		this.saxParserFactory = saxParserFactory;
	}

	/**
	 * Build the OSGi bundle from an Eclipse project directory.
	 * This method closes the output stream because it can
	 * only be called once.
	 * @throws IOException thrown if there is a problem assembling the bundle
	 * @throws FileNotFoundException thrown if a path could not be loaded
	 */
	public void build() throws FileNotFoundException, IOException {
		zipOutputStream.setLevel(ZipOutputStream.STORED);
		
		handleBuildProperties();
		handleClasspath();
		
		zipOutputStream.close();
	}
	/**
	 * Adds the entries from the .classpath file to the
	 * in-memory pseudo-bundle.
	 * 
	 * @param projectDirectory the project directory
	 * @param zipOutputStream the in-memory zip file for the bundle
	 * @throws IOException
	 */
	private void handleClasspath() throws IOException {
		File classpathFile = new File(projectDirectory, ".classpath");
		if (!classpathFile.exists() || !classpathFile.isFile())
			throw new IOException(
					"The .classpath file does not exist in the project directory or is not a file.");
		SAXParser parser;
		
		// The .classpath file is a very simple XML file, so we
		// use a SAX parser to pull out the bits we need. Each
		// relevant classpath string is stored in classpathSet (we
		// need a set as duplicates are not uncommon).
		final Set<String> classpathSet = new TreeSet<String>();
		try {
			parser = saxParserFactory.newSAXParser();
			parser.parse(classpathFile, new DefaultHandler() {
				public void startElement(String uri, String localName,
						String name, Attributes attributes)
						throws SAXException {
					if (name.equals("classpathentry")) {
						String kind = attributes.getValue("", "kind");
						if (kind == null || kind.equals(""))
							throw new SAXException(
									"Missing kind attribute on classpathentry.");
						if (kind.equals("src")) {
							String output = attributes.getValue("output");
							if (output != null && !output.equals(""))
								classpathSet.add(output);
						} else if (kind.equals("output")) {
							String path = attributes.getValue("path");
							if (path != null && !path.equals(""))
								classpathSet.add(path);
						}
					}
				}
			});
		} catch (ParserConfigurationException e) {
			throw new IOException(
					"Unable to configure XML parser to parse '.classpath' file ("
							+ e.getMessage() + ")");
		} catch (SAXException e) {
			throw new IOException("Unable to parse the '.classpath' file: "
					+ e.getMessage());
		}
		Iterator<String> it = classpathSet.iterator();
		while (it.hasNext()) {
			String path = (String) it.next();
			File inputPath = new File(projectDirectory, path);
			// We have to ignore invalid classpath locations, because if
			// nothing is compiled,
			// the directory might not be created. (At least, thats the case
			// with maven, and
			// Eclipse seems not to care).
			if (inputPath.exists() && inputPath.isDirectory())
				writeResourcesFromDirectory(zipOutputStream, addedEntries, ".", inputPath, 0);
		}
	}

	/**
	 * Parse and build the part of in-memory bundle based on the
	 * Eclipse build.properties file.
	 * 
	 * @param projectDirectory The project directory
	 * @param zipOutputStream The in-memory zip file
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private void handleBuildProperties() throws IOException,
			FileNotFoundException {
		Properties buildProperties = loadBuildProperties();

		String binIncludes = buildProperties.getProperty("bin.includes");
		if (binIncludes != null) {
			String[] binFiles = binIncludes.split(",");

			for (int i = 0; i < binFiles.length; ++i) {
				String binFile = binFiles[i].trim();

				// Could be nasty and throw an exception, but we know it
				// just means the user has left a blank line
				// in the file after a "\"
				if (binFile.equals(""))
					continue;

				// We deliberately ignore this one because it is
				// handled by the classpath. I'm not sure, but I think
				// output. entries are intended for the exporter
				// compiler stage, not the packaging stage (which is
				// what we're imitating). We still handle them for
				// .jar files because the default Eclipse builder
				// doesn't always build them (so we simulate their
				// "building").
				// (If we were building a source bundle, we would
				// include it, as the source.. entry has the source
				// folders, although the .classpath file does too :-?)
				if (binFile.equals("."))
					continue;

				String binOutput = buildProperties.getProperty("output."
						+ binFiles[i]);
				if (binOutput != null) {

					// This specifies a library, with an output directory
					File outputDirectory = new File(projectDirectory,
							binOutput);
					writeResourcesFromDirectory(zipOutputStream, addedEntries, binFiles[i],
							outputDirectory, 0);
				} else {
					// This specifies a direct resource include in the
					// binary build
					File resource = new File(projectDirectory, binFiles[i]);
					if (!resource.exists())
						throw new IOException(
								"Invalid binary build include in build.properties: "
										+ binFiles[i]);
					if (resource.isFile()) {
						String zipPathToResource =  new File(
								binFiles[i]).getPath();
						
						if (File.separator.equals("\\")) {
							zipPathToResource = zipPathToResource.replaceAll("\\", "/");
						}
						writeResourceFromFile(zipOutputStream, addedEntries, zipPathToResource, resource);
					}
					else
						writeResourcesFromDirectory(zipOutputStream, addedEntries, new File(
								binFiles[i]).getPath(), resource, 0);
				}
			}
		}
	}

	/**
	 * Load the build.properties file. This method synthesizes
	 * a default build.properties if it is missing.
	 * 
	 * @return the loaded build.properties file, or a synthesized
	 * set of properties if not found
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private Properties loadBuildProperties()
			throws IOException, FileNotFoundException {
		Properties buildProperties = new Properties();
		File buildPropertiesFile = new File(projectDirectory,
				"build.properties");
		if (!buildPropertiesFile.exists() || !buildPropertiesFile.isFile()) {
			// The build.properties file is missing. We probably ended up
			// here because there is a MANIFEST.MF file. Lets just cheat and
			// include META-INF/ directory as if the build.properties
			// file was just generated by the
			// "Configure -> Convert to Plugin Project" menu
			buildProperties.put("bin.includes", "META-INF,.");
		} else
			buildProperties.load(new FileInputStream(buildPropertiesFile));
		return buildProperties;
	}

	/**
	 * Write the resources from the specified input directory to the output
	 * stream.
	 * @param jarFile the output JAR file (specified as a parameter as this method
	 * is re-used for internal JARs)
	 * @param addedEntries the entries that have been added to <code>jarFile</code> already. this set will be updated for new entries in this directory
	 * @param outputResource the name of the output path in the zip file
	 * @param inputDirectory the directory containing files for <code>outputResource</code>
	 * @param recurseDepth recursion depth tracker -- incremented on recursive calls to this method
	 * @throws IOException
	 */
	private static void writeResourcesFromDirectory(ZipOutputStream jarFile,
			Set<String> addedEntries,
			String outputResource, File inputDirectory, int recurseDepth)
			throws IOException {
		String prefix;
		
		if (recurseDepth > MAX_RECURSE_DEPTH)
			throw new IOException(
					"Unwanted recursion building resource directory: "
							+ inputDirectory + " for outputResource="
							+ outputResource);
		if (outputResource.equals(".")) {
			prefix = ""; // project root directory -> no prefix
		} else if (outputResource.endsWith(".jar")) {
			// Generate internal jar
			ByteArrayOutputStream internalJarByteBuffer = new ByteArrayOutputStream();
			JarOutputStream internalJar = new JarOutputStream(
					internalJarByteBuffer);
			internalJar.setLevel(ZipOutputStream.STORED);
			Set<String> internalAddedEntries = new HashSet<String>();
			writeResourcesFromDirectory(internalJar, internalAddedEntries, ".", inputDirectory, 0);
			internalJarByteBuffer.close();

			// Write its output entry
			ZipEntry outputEntry = new ZipEntry(outputResource);
			jarFile.putNextEntry(outputEntry);
			jarFile.write(internalJarByteBuffer.toByteArray());
			jarFile.closeEntry();
			return;
		} else
			prefix = outputResource;
		if (inputDirectory.exists() == false)
			return; // Don't think we should throw an exception here, as its
		// possible that nothing is generated for the build.
		File[] files = inputDirectory.listFiles();
		for (int i = 0; i < files.length; i++) {
			File inputFile = files[i];
			String fileOutputPath;
			if (prefix.equals(""))
				fileOutputPath = inputFile.getName();
			else
				// Use '/' as ZIP files are always constructed with forward slash
				fileOutputPath = prefix + "/"
						+ inputFile.getName();

			if (inputFile.isFile()) {
				writeResourceFromFile(jarFile, addedEntries, fileOutputPath, inputFile);
			} else if (inputFile.isDirectory()) {
				writeResourcesFromDirectory(jarFile, addedEntries, fileOutputPath,
						inputFile, recurseDepth + 1);
			}
		}
	}

	/**
	 * Write the specified <code>inputFile</code> to the path <code>outputResource</code>
	 * in bundle <code>jarFile</code>
	 * @param jarFile the output JAR file
	 * @param addedEntries entries already added to this JAR file. this method will update
	 * the set with <code>outputResource</code>
	 * @param outputResource the path in the bundle to write <code>inputFile</code> to
	 * @param inputFile the file to write to the bundle
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private static void writeResourceFromFile(ZipOutputStream jarFile, Set<String> addedEntries,
			String outputResource, File inputFile) throws IOException,
			FileNotFoundException {
		if (addedEntries.contains(outputResource))
			return; // Ignore it, it has already been found and added
		
		ZipEntry entry = new ZipEntry(outputResource);
		addedEntries.add(outputResource);
		jarFile.putNextEntry(entry);
		InputStream inputFileStream = new FileInputStream(inputFile);
		byte[] readBuffer = new byte[100 * 1024];

		int read = 0;
		while (read != -1) {
			read = inputFileStream.read(readBuffer);
			if (read > 0)
				jarFile.write(readBuffer, 0, read);
		}
		inputFileStream.close();
		jarFile.closeEntry();
	}

}
