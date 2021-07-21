package de.unioninvestment.portal.liferay.addon.portlet;

import static org.junit.Assert.assertTrue;

public class SvnExporterTest {
	public static void main(String[] args) {

		byte[] fileAsByteArrayFromURL = SvnExporter
				.getFileAsByteArrayFromURL("http://svn.d3.uid.de/repos/sofia/trunk/"
						+ "liferay-addon/liferay-import-core/"
						+ "src/main/resources/content/LiferayContentDescriptor.xsd");
		assertTrue(fileAsByteArrayFromURL != null
				&& fileAsByteArrayFromURL.length > 0);

		String fileAsTextFromURL = SvnExporter
				.getTextFileFromURL("http://svn.d3.uid.de/repos/sofia/trunk/"
						+ "liferay-addon/liferay-import-core/"
						+ "src/main/resources/content/LiferayContentDescriptor.xsd", "utf-8");
		assertTrue(fileAsTextFromURL != null
				&& fileAsTextFromURL.length() > 0);
	}
}
