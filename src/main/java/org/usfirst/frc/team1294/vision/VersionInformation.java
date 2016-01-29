package org.usfirst.frc.team1294.vision;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class VersionInformation {

	private Manifest manifest;
	
	public VersionInformation() {
		this.manifest = loadManifest();
	}
	
	public String getVersion() {
		return getAttribute("Version");
	}
	
	public String getAuthor() {
		return getAttribute("Author");
	}
	
	private Manifest loadManifest() {
		URLClassLoader cl = (URLClassLoader) VersionInformation.class
				.getClassLoader();
		try {
			URL url = cl.findResource("META-INF/MANIFEST.MF");
			return new Manifest(url.openStream());
		} catch (IOException ex) {
			ex.printStackTrace();
			System.out.println("loadManifest exception");
			return null;
		}
	}

	private String getAttribute(String attribute) {
		if (manifest == null) {
			return null;
		}
		Attributes attrs = manifest.getMainAttributes();
		String attr = attrs.getValue(attribute);
		return attr == null ? "<not found>" : attr;
	}
}