package com.openhtmltopdf.pdfboxout;

import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.extend.*;
import com.openhtmltopdf.extend.impl.FSNoOpCacheStore;
import com.openhtmltopdf.outputdevice.helper.AddedFont;
import com.openhtmltopdf.outputdevice.helper.BaseDocument;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.outputdevice.helper.PageDimensions;
import com.openhtmltopdf.outputdevice.helper.UnicodeImplementation;
import com.openhtmltopdf.util.XRLog;

import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.OutputStream;
import java.util.logging.Level;

public class PdfRendererBuilder extends BaseRendererBuilder<PdfRendererBuilder, PdfRendererBuilderState> {

	public PdfRendererBuilder() {
		super(new PdfRendererBuilderState());
		
		for (CacheStore cacheStore : CacheStore.values()) {
		    // Use the flyweight pattern to initialize all caches with a no-op implementation to
		    // avoid excessive null handling.
		    state._caches.put(cacheStore, FSNoOpCacheStore.INSTANCE);
		}
	}

	/**
	 * Run the XHTML/XML to PDF conversion and output to an output stream set by
	 * toStream.
	 *
	 * @throws Exception
	 */
	public void run() throws Exception {
		PdfBoxRenderer renderer = null;
		try {
			renderer = this.buildPdfRenderer();
			renderer.layout();
			renderer.createPDF();
		} finally {
			if (renderer != null)
				renderer.close();
		}
	}

	/**
	 * Build a PdfBoxRenderer for further customization. Remember to call
	 * {@link PdfBoxRenderer#cleanup()} after use.
	 *
	 * @return
	 */
	public PdfBoxRenderer buildPdfRenderer() {
		UnicodeImplementation unicode = new UnicodeImplementation(state._reorderer, state._splitter, state._lineBreaker,
				state._unicodeToLowerTransformer, state._unicodeToUpperTransformer, state._unicodeToTitleTransformer, state._textDirection,
				state._charBreaker);

		PageDimensions pageSize = new PageDimensions(state._pageWidth, state._pageHeight, state._isPageSizeInches);

		BaseDocument doc = new BaseDocument(state._baseUri, state._html, state._document, state._file, state._uri);

		PdfBoxRenderer renderer = new PdfBoxRenderer(doc, unicode, pageSize, state);

		/*
		 * Register all Fonts
		 */
		PdfBoxFontResolver resolver = renderer.getFontResolver();
		for (AddedFont font : state._fonts) {
			IdentValue fontStyle = null;

			if (font.style == FontStyle.NORMAL) {
				fontStyle = IdentValue.NORMAL;
			} else if (font.style == FontStyle.ITALIC) {
				fontStyle = IdentValue.ITALIC;
			} else if (font.style == FontStyle.OBLIQUE) {
				fontStyle = IdentValue.OBLIQUE;
			}

			if (font.supplier != null) {
				resolver.addFont(font.supplier, font.family, font.weight, fontStyle, font.subset);
			} else {
				try {
					resolver.addFont(font.fontFile, font.family, font.weight, fontStyle, font.subset);
				} catch (Exception e) {
					XRLog.init(Level.WARNING, "Font " + font.fontFile + " could not be loaded", e);
				}
			}
		}

		return renderer;
	}

	/**
	 * An output stream to output the resulting PDF. The caller is required to close
	 * the output stream after calling run.
	 *
	 * @param out
	 * @return
	 */
	public PdfRendererBuilder toStream(OutputStream out) {
		state._os = out;
		return this;
	}

	/**
	 * Set the PDF version, typically we use 1.7. If you set a lower version, it is
	 * your responsibility to make sure no more recent PDF features are used.
	 *
	 * @param version
	 * @return
	 */
	public PdfRendererBuilder usePdfVersion(float version) {
		state._pdfVersion = version;
		return this;
	}

	/**
	 * Set the PDF/A conformance, typically we use PDF/A-1
	 * 
	 * Note: PDF/A documents require fonts to be embedded. So if this is not set to NONE,
	 * the built-in fonts will not be available and currently any text without a
	 * specified and embedded font will cause the renderer to crash with an exception.
	 *
	 * @param pdfAConformance
	 * @return
	 */
	public PdfRendererBuilder usePdfAConformance(PdfAConformance pdfAConformance) {
		this.state._pdfAConformance = pdfAConformance;
		return this;
	}
	
	/**
	 * Whether to conform to PDF/UA or Accessible PDF. False by default.
	 * @param pdfUaAccessibility
	 * @return this for method chaining
	 */
	public PdfRendererBuilder usePdfUaAccessbility(boolean pdfUaAccessibility) {
	    this.state._pdfUaConform = pdfUaAccessibility;
	    return this;
	}

	/**
	 * Sets the color profile, needed for PDF/A conformance.
	 *
	 * You can use the sRGB.icc from https://svn.apache.org/viewvc/pdfbox/trunk/examples/src/main/resources/org/apache/pdfbox/resources/pdfa/
	 *
	 * @param colorProfile
	 * @return
	 */
	public PdfRendererBuilder useColorProfile(byte[] colorProfile) {
		this.state._colorProfile = colorProfile;
		return this;
	}
	
	/**
	 * By default, this project creates an entirely in-memory <code>PDDocument</code>.
	 * The user can use this method to create a document either entirely on-disk
	 * or a mix of in-memory and on-disk using the <code>PDDocument</code> constructor
	 * that takes a <code>MemoryUsageSetting</code>.
	 * @param doc a (usually empty) PDDocument
	 * @return this for method chaining
	 */
	public PdfRendererBuilder usePDDocument(PDDocument doc) {
	    state.pddocument = doc;
	    return this;
	}



	/**
	 * Set a producer on the output document
	 *
	 * @param producer
	 *            the name of the producer to set defaults to openhtmltopdf.com
	 * @return this for method chaining
	 */
	public PdfRendererBuilder withProducer(String producer) {
		state._producer = producer;
		return this;
	}
	
	/**
	 * List of caches available.
	 */
	public enum CacheStore {
	    
	    /**
	     * Caches font metrics, based on a combined key of family name, weight and style.
	     * Using this cache avoids loading fallback fonts if the metrics are already in the cache
	     * and the previous fonts contain the needed characters.
	     */
	    PDF_FONT_METRICS;
	}
	
	/**
	 * Use a specific cache. Cache values should be thread safe, so provided your cache store itself
	 * is thread safe can be used accross threads.
	 * @return this for method chaining.
	 * @see CacheStore
	 */
	public PdfRendererBuilder useCacheStore(CacheStore which, FSCacheEx<String, FSCacheValue> cache) {
	    state._caches.put(which, cache);
	    return this;
	}

	/**
	 * Set a PageSupplier that is called whenever a new page is needed.
	 * 
	 * @param pageSupplier 
	 *            {@link PageSupplier} to use
	 * @return this for method chaining.
	 */
	public PdfRendererBuilder usePageSupplier(PageSupplier pageSupplier) {
		state._pageSupplier = pageSupplier;
		return this;
	}

	/**
	 * Various level of PDF/A conformance:
	 *
	 * PDF/A-1, PDF/A-2 and PDF/A-3
	 */
	public enum PdfAConformance {
		NONE(-1, ""),
		PDFA_1_A(1, "A"), PDFA_1_B(1, "B"),
		PDFA_2_A(2, "A"), PDFA_2_B(2, "B"), PDFA_2_U(2, "U"),
		PDFA_3_A(3, "A"), PDFA_3_B(3, "B"), PDFA_3_U(3, "U");

		PdfAConformance(int part, String value) {
			this.part = part;
			this.value = value;
		}

		private final int part;
		private final String value;
		
		public String getConformanceValue() {
		    return this.value;
		}

		public int getPart() {
			return this.part;
		}
	}
}

