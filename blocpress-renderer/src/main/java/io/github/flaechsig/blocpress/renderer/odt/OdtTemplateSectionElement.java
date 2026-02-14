package io.github.flaechsig.blocpress.renderer.odt;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import io.github.flaechsig.blocpress.renderer.TemplateDocument;
import io.github.flaechsig.blocpress.renderer.TemplateSectionElement;
import org.odftoolkit.odfdom.pkg.OdfElement;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a template section element in an ODT document.
 * <p>
 * Construction rules:<br/>
 * if blocpress.mode ==
 * <ul>
 *  <li>"file" the "File Name" determines the source of a template</li>
 *  <li>"server" the "Section Name" determines the source of a template. Additionally blocpress.url must be set</li>
 * </ul>
 * <p>
 * if you use a template server (for mode=server) the resource name ist constructed by the following rule/example
 * <p>
 * Section Name: TextBuildingBlock(customer=customer,address=customer.address)
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-e-2">E-2: Baustein</a></li>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-tf-5">TF-5: Dokument generieren</a> (Textblock-Expansion)</li>
 * </ul>
 * URL: <blocpress.url>/TextBuildingBlock
 * <p>
 * The parameter in parentheses are the initialisation for the template. So template name is needed in file and
 * server mode. Section name and file name should correspond.
 *
 * </p>
 */
public class OdtTemplateSectionElement extends OdtTemplateElement implements TemplateSectionElement {

    /**
     * Creates a new {@code OdtTemplateSectionElement}.
     *
     * @param element the ODF element representing the template section
     */
    public OdtTemplateSectionElement(OdfElement element) {
        super(element);
    }

    /**
     * Retrieves the URL for the current template section element based on the configured mode.
     * <p>
     * The retrieval logic depends on the property "blocpress.mode", which determines
     * the operational mode and subsequent URL source:
     * - If "blocpress.mode" is set to "file", the URL is retrieved from the **File Name** of the **Link**-Section.
     * - If "blocpress.mode" is set to "server", the URL is constructed using the "blocpress.url"
     * system property and the sanitized **Section Name**.
     *
     * @return the URL of the template section as a {@link URL} object
     * @throws IllegalStateException if "blocpress.url" is not configured in "server" mode,
     *                               or if an unknown "blocpress.mode" is specified
     */
    @Override
    @SneakyThrows
    public URL getUrl(URL baseURL) {
        var mode = System.getProperty("blocpress.mode", "file");
        var url = System.getProperty("blocpress.url");

        var name = ((OdfElement) element.getParentNode()).getAttribute("text:name");
        var href = element.getAttribute("xlink:href");

        return switch (mode) {
            case "file" -> resolveTemplateFileUrl(href, baseURL);
            case "server" -> URI.create(url + "/" + name).toURL();
            default -> throw new IllegalStateException("Configuration exception. Unknown mode " + mode);
        };
    }

    /**
     * Resolves the URL of a template section element based on the given href and baseURL.
     * Handles both absolute and relative URLs, and searches for templates in parent directories if necessary.
     *
     * @param href    The href attribute of the template section element.
     * @param baseURL The base URL of the document containing the template section.
     * @return The resolved URL of the template section, or null if not found.
     * @throws Exception If an error occurs during URL resolution.
     */
    private static URL resolveTemplateFileUrl(String href, URL baseURL) throws Exception {
        if (StringUtils.isBlank(href)) {
            return null;
        }

        // Absolute URL/URI? (file:/ http:/ https:/ etc.)
        if (href.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return URI.create(href).toURL();
        }

        // baseURL muss eine lokale Datei sein, sonst können wir nicht "im Dateisystem nach oben suchen"
        if (baseURL == null || !"file".equalsIgnoreCase(baseURL.getProtocol())) {
            // Fallback: relative Pfade gegen cwd
            return Path.of(href).toAbsolutePath().normalize().toUri().toURL();
        }

        Path baseFile = Path.of(baseURL.toURI()).toAbsolutePath().normalize();
        Path baseDir = Files.isDirectory(baseFile) ? baseFile : baseFile.getParent();
        if (baseDir == null) {
            // sehr unwahrscheinlich, aber sauberer Fallback
            return Path.of(href).toAbsolutePath().normalize().toUri().toURL();
        }

        // 1) Standard: relativ zum Template-Verzeichnis auflösen
        Path direct = baseDir.resolve(href).normalize();
        if (Files.exists(direct)) {
            return direct.toUri().toURL();
        }

        // 2) Kommentar-Logik:
        // find the template with the same Name in the directory of baseURL or in on of the parent directories
        Path fileName = Path.of(href).getFileName(); // nur der Name, ohne evtl. Unterordner
        if (fileName == null) {
            return null;
        }

        Path dir = baseDir;
        while (dir != null) {
            Path candidate = dir.resolve(fileName).normalize();
            if (Files.exists(candidate)) {
                return candidate.toUri().toURL();
            }
            dir = dir.getParent();
        }

        // nicht gefunden -> null (oder Exception, je nach gewünschtem Verhalten)
        return null;
    }
}
