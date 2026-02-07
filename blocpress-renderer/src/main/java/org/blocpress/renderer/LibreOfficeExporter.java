package org.blocpress.renderer;

import com.sun.star.beans.PropertyValue;
import com.sun.star.comp.helper.Bootstrap;
import com.sun.star.document.UpdateDocMode;
import com.sun.star.frame.*;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.text.XTextFieldsSupplier;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;
import com.sun.star.util.URL;
import com.sun.star.util.XCloseable;
import com.sun.star.util.XRefreshable;
import com.sun.star.util.XUpdatable;

import java.nio.file.Files;
import java.nio.file.Path;

public class LibreOfficeExporter {
    /**
     * Refreshes and transforms document to specified output format
     */
    public static byte[] refreshAndTransform(byte[] odt, OutputFormat outputFormat) throws Exception {
        Path inFile = Files.createTempFile("blocpress_", ".odt");
        Path outFile = Files.createTempFile("blocpress_", "."+outputFormat.getSuffix());
        inFile.toFile().deleteOnExit();
        outFile.toFile().deleteOnExit();

        Files.write(inFile, odt);
        refreshAndExport(inFile.toString(), outFile.toString(), outputFormat);
        var result = Files.readAllBytes(outFile);
        return result;
    }

    /**
     * Refreshes then exports document to specified format
     *
     * @param input  Input file path
     * @param output Output file path
     * @param format Output format
     */
    private static void refreshAndExport(String input, String output, OutputFormat format) throws Exception {

        // 1) LibreOffice headless starten
        XComponentContext ctx = Bootstrap.bootstrap();
        XMultiComponentFactory smgr = ctx.getServiceManager();

        // 2) Desktop-Service erzeugen
        Object desktop = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx);
        XComponentLoader loader = UnoRuntime.queryInterface(XComponentLoader.class, desktop);

        // 3) Dokument hidden laden
        PropertyValue[] openProps = createOpenProps();

        XComponent xComponent = loader.loadComponentFromURL(
                pathToUrl(input),
                "_blank",
                0,
                openProps
        );
        updateConditionsAndFields(xComponent);
        dispatchUno(xComponent, ".uno:UpdateAll");      // "alles" aktualisieren
        dispatchUno(xComponent, ".uno:UpdateFields");   // Felder explizit
        dispatchUno(xComponent, ".uno:UpdateIndexes");  // Inhaltsverzeichnis/Indizes (falls vorhanden)

        // exportieren
        String filterName = switch (format) {
            case PDF -> "writer_pdf_Export";
            case ODT -> "writer8";
            case RTF -> "Rich Text Format";
            default -> throw new IllegalArgumentException("Unknow export format: " + format);
        };
        PropertyValue[] exportProps = new PropertyValue[]{
                createProperty("FilterName", filterName)
        };

        XStorable storable = UnoRuntime.queryInterface(XStorable.class, xComponent);

        storable.storeToURL(pathToUrl(output), exportProps);

        // 6) Schließen
        XCloseable closeable = UnoRuntime.queryInterface(XCloseable.class, xComponent);
        if (closeable != null) {
            closeable.close(true);
        }
    }
    private static void updateConditionsAndFields(XComponent xComponent) throws Exception {
        XTextFieldsSupplier textFieldSupplier = UnoRuntime.queryInterface(XTextFieldsSupplier.class, xComponent);
        if (textFieldSupplier == null) {
            return;
        }

        // Hole alle Felder im Dokument
        var textFields = textFieldSupplier.getTextFields();

        // Aktualisiere die Feldwerte
        XUpdatable updatableFields = UnoRuntime.queryInterface(XUpdatable.class, textFields);
        if (updatableFields != null) {
            updatableFields.update();  // Aktualisiert Bedingungen wie IF-Felder
        }

        // Refresh-Aufruf für Ansichts-/Darstellungsupdates
        XRefreshable refreshableFields = UnoRuntime.queryInterface(XRefreshable.class, textFields);
        if (refreshableFields != null) {
            refreshableFields.refresh();
        }
    }

    private static void dispatchUno(XComponent xComponent, String command) throws Exception {
        XModel model = UnoRuntime.queryInterface(XModel.class, xComponent);
        if (model == null) {
            return;
        }
        XController controller = model.getCurrentController();
        if (controller == null) {
            return;
        }
        XFrame frame = controller.getFrame();
        if (frame == null) {
            return;
        }
        XDispatchProvider dispatchProvider = UnoRuntime.queryInterface(XDispatchProvider.class, frame);
        if (dispatchProvider == null) {
            return;
        }

        // DispatchHelper über Service Manager erzeugen (kein normaler Konstruktor!)
        XComponentContext ctx = Bootstrap.bootstrap();
        XMultiComponentFactory smgr = ctx.getServiceManager();
        Object dispatcherObj = smgr.createInstanceWithContext("com.sun.star.frame.DispatchHelper", ctx);
        XDispatchHelper helper = UnoRuntime.queryInterface(XDispatchHelper.class, dispatcherObj);

        if (helper != null) {
            helper.executeDispatch(dispatchProvider, command, "", 0, new PropertyValue[0]);
        }
    }

    private static PropertyValue[] createOpenProps() {
        return new PropertyValue[] {
                createProperty("Hidden", true),
                createProperty("UpdateDocMode", UpdateDocMode.FULL_UPDATE)
        };
    }

    private static PropertyValue createProperty(String name, Object value) {
        PropertyValue p = new PropertyValue();
        p.Name = name;
        p.Value = value;
        return p;
    }

    private static String pathToUrl(String path) {
        return "file:///" + new java.io.File(path).getAbsolutePath();
    }
}
