package de.unioninvestment.portal.liferay.addon.portlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.commons.io.IOUtils;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.Base64;

public class SvnExporter {

	private static final Log logger = LogFactoryUtil.getLog(SvnExporter.class.getName());

	public static byte[] getFileAsByteArrayFromURL(String vcsUri) {
		try {
			// Authenticator.setDefault(new VcsAuthenticator());
			URL url = new URL(new URI(vcsUri).toASCIIString());
			URLConnection connection = url.openConnection();
			applyOsirisBasicAuthCredentials(connection);

			InputStream ins = connection.getInputStream();
			return IOUtils.toByteArray(ins);

		} catch (MalformedURLException e) {
			logger.error(e.toString());
		} catch (URISyntaxException e) {
			logger.error(e.toString());
		} catch (IOException e) {
			logger.error(e.toString());
		}
		return null;
	}

	public static String getTextFileFromURL(String vcsUri, String encoding) {
		try {

			// Authenticator.setDefault(new VcsAuthenticator());
			URL url = new URL(vcsUri);
			URLConnection connection = url.openConnection();
			applyOsirisBasicAuthCredentials(connection);

			String contentEncoding = encoding != null ? encoding
					: getContentEncoding(connection);
			InputStream ins = connection.getInputStream();

			return IOUtils.toString(ins, contentEncoding);

		} catch (MalformedURLException e) {
			logger.error(e.toString());
		} catch (IOException e) {
			logger.error(e.toString());
		}
		return null;
	}

	private static String getContentEncoding(URLConnection connection)
			throws UnsupportedEncodingException {
		String contentType = connection.getContentType();
		String[] values = contentType.split(";"); // The values.length must be
													// equal to 2...
		String charset = "";

		for (String value : values) {
			value = value.trim();

			if (value.toLowerCase().startsWith("charset=")) {
				charset = value.substring("charset=".length());
			}
		}

		if ("".equals(charset)) {
			throw new UnsupportedEncodingException(
					"Unknown encoding (contentType='" + contentType + "'");
		}
		return null;
	}

	private static void applyOsirisBasicAuthCredentials(URLConnection connection)
			throws UnsupportedEncodingException {
		connection.setRequestProperty(
				"Authorization",
				"Basic "
						+ Base64.encode(("<<user>>:" + "<<password>>").getBytes("iso-8859-1")));
	}
}
