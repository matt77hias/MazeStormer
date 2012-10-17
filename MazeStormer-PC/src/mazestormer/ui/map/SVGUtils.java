package mazestormer.ui.map;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.svg.SVGDocument;

public class SVGUtils {

	/**
	 * Creates a new SVG document.
	 */
	public static final SVGDocument createSVGDocument() {
		String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
		DOMImplementation impl = SVGDOMImplementation.getDOMImplementation();
		return (SVGDocument) impl.createDocument(svgNS, "svg", null);
	}

	/**
	 * Loads an SVG file into a SVG document.
	 * 
	 * @param uri
	 *            The URI of the SVG file.
	 */
	public static final SVGDocument loadSVGDocument(String uri) {
		UserAgent ua = new UserAgentAdapter();
		DocumentLoader loader = new DocumentLoader(ua);
		try {
			return (SVGDocument) loader.loadDocument(uri);
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Loads an SVG file into a SVG document.
	 * 
	 * @param uri
	 *            The URI of the SVG file.
	 */
	public static final SVGDocument loadSVGDocument(URI uri) {
		return loadSVGDocument(uri.toString());
	}

	/**
	 * Loads an SVG file into a SVG document.
	 * 
	 * @param url
	 *            The URL of the SVG file.
	 */
	public static final SVGDocument loadSVGDocument(URL url) {
		return loadSVGDocument(url.toExternalForm());
	}

}