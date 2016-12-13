/*
* Copyright 2012-2016 Broad Institute, Inc.
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.broadinstitute.barclay.help;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.RootDoc;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

/**
 * Javadoc Doclet that combines javadoc, Barclay annotations, and FreeMarker
 * templates to produce PHP formatted docs for classes.
 * <p/>
 * This document has the following workflow:
 * <p/>
 * 1 -- walk the javadoc hierarchy, looking for class that have the
 * DocumentedFeature documentedFeatureObject or are in the type hierarchy in the
 * static list of things to document, and are to be documented
 * 2 -- construct for each a DocWorkUnit, resulting in the complete
 * set of things to document
 * 3 -- for each unit, actually generate a PHP page documenting it
 * as well as links to related features via their units.  Writing
 * of a specific class PHP is accomplished by a generate DocumentationHandler
 * 4 -- write out an index of all units, organized by group
 * 5 -- emit JSON version of Docs using Google GSON (currently incomplete but workable)
 * <p/>
 */
public abstract class HelpDoclet {
    final protected static Logger logger = LogManager.getLogger(HelpDoclet.class);

    // HelpDoclet command line options
    final private static String SETTINGS_DIR_OPTION = "-settings-dir";
    final private static String DESTINATION_DIR_OPTION = "-destination-dir";
    final private static String BUILD_TIMESTAMP_OPTION = "-build-timestamp";
    final private static String ABSOLUTE_VERSION_OPTION = "-absolute-version";
    final private static String INCLUDE_HIDDEN_OPTION = "-hidden-version";
    final private static String OUTPUT_FILE_EXTENSION_OPTION = "-output-file-extension";

    /**
     * Where we find the help FreeMarker templates
     */
    final private static File DEFAULT_SETTINGS_DIR = new File("settings/helpTemplates");

    /**
     * Where we write the PHP directory
     */
    final private static File DEFAULT_DESTINATION_DIR = new File("barclaydocs");

    final private static String DEFAULT_OUTPUT_FILE_EXTENSION = "html";

    // ----------------------------------------------------------------------
    //
    // Variables that are set on the command line by javadoc
    //
    // ----------------------------------------------------------------------
    protected static File settingsDir = DEFAULT_SETTINGS_DIR;
    protected static File destinationDir = DEFAULT_DESTINATION_DIR;
    protected static String buildTimestamp = "[no timestamp available]";
    protected static String absoluteVersion = "[no versionavailable]";
    protected static boolean showHiddenFeatures = false;
    protected static String outputFileExtension = DEFAULT_OUTPUT_FILE_EXTENSION;

    /**
     * The javadoc root doc
     */
    RootDoc rootDoc;

    /**
     * The set of all things we are going to document
     */
    private Set<DocWorkUnit> myWorkUnits;

    /**
     * Extracts the contents of certain types of javadoc and adds them to an XML file.
     *
     * @param rootDoc The documentation root.
     * @return Whether the JavaDoc run succeeded.
     * @throws java.io.IOException if output can't be written.
     */
    protected boolean startProcessDocs(final RootDoc rootDoc) throws IOException {
        for (String[] options : rootDoc.options()) {
            if (options[0].equals(SETTINGS_DIR_OPTION))
                settingsDir = new File(options[1]);
            if (options[0].equals(DESTINATION_DIR_OPTION))
                destinationDir = new File(options[1]);
            if (options[0].equals(BUILD_TIMESTAMP_OPTION))
                buildTimestamp = options[1];
            if (options[0].equals(ABSOLUTE_VERSION_OPTION))
                absoluteVersion = options[1];
            if (options[0].equals(INCLUDE_HIDDEN_OPTION))
                showHiddenFeatures = true;
            if (options[0].equals(OUTPUT_FILE_EXTENSION_OPTION)) {
                outputFileExtension = options[1];
            }
        }

        if (!settingsDir.exists())
            throw new RuntimeException(SETTINGS_DIR_OPTION + " :" + settingsDir.getPath() + " does not exist");
        else if (!settingsDir.isDirectory())
            throw new RuntimeException(SETTINGS_DIR_OPTION + " :" + settingsDir.getPath() + " is not a directory");

        // process the documentable objects
        processDocs(rootDoc);
        return true;
    }

    /**
     * Validate the given options against options supported by this doclet.
     *
     * @param option Option to validate.
     * @return Number of potential parameters; 0 if not supported.
     */
    public static int optionLength(final String option) {
        if (//TODO: every javadoc arg that is passed has to appear here or we fail
            option.equals("-d") ||
            option.equals("-doctitle") ||
            option.equals("-windowtitle") ||

            option.equals(SETTINGS_DIR_OPTION) ||
            option.equals(DESTINATION_DIR_OPTION) ||
            option.equals(BUILD_TIMESTAMP_OPTION) ||
            option.equals(ABSOLUTE_VERSION_OPTION) ||
            option.equals(OUTPUT_FILE_EXTENSION_OPTION)) {
            return 2;
        } else if (option.equals("-quiet")) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * @return Boolean indicating whether to include @Hidden annotations in our documented output
     */
    public boolean showHiddenFeatures() {
        return showHiddenFeatures;
    }

    protected String getOutputFileExtension() { return outputFileExtension;}

    protected String getIndexTemplateName() { return "generic.index.template.html"; }

    /**
     * Doclet implementations should return a DocumentedFeatureHandler-derived object
     * @return
     */
    protected abstract DocumentedFeatureHandler createDocumentedFeatureHandler();

    /**
     * Doclet implementations should return a GSONWorkUnit-derived object if the GSON objects for
     * Documentedfeature's need to contain custom values.
     * @return
     */
    protected GSONWorkUnit createGSONWorkUnit(
            final DocWorkUnit workUnit,
            final List<Map<String, String>> groups,
            final List<Map<String, String>> data)
    {
        return new GSONWorkUnit();
    }

    /**
     * @param rootDoc
     */
    private void processDocs(final RootDoc rootDoc) {
        // setup the global access to the root
        this.rootDoc = rootDoc;

        try {
            /* ------------------------------------------------------------------- */
            /* You should do this ONLY ONCE in the whole application life-cycle:   */

            final Configuration cfg = new Configuration();
            // Specify the data source where the template files come from.
            cfg.setDirectoryForTemplateLoading(settingsDir);
            // Specify how templates will see the data-model. This is an advanced topic...
            cfg.setObjectWrapper(new DefaultObjectWrapper());

            myWorkUnits = computeWorkUnits();

            final List<Map<String, String>> groups = new ArrayList<>();
            final Set<String> seenDocumentationFeatures = new HashSet<>();
            final List<Map<String, String>> data = new ArrayList<>();
            for (DocWorkUnit workUnit : myWorkUnits) {
                data.add(workUnit.indexDataMap());
                if (!seenDocumentationFeatures.contains(workUnit.documentedFeatureObject.groupName())) {
                    groups.add(getGroupMap(workUnit.documentedFeatureObject));
                    seenDocumentationFeatures.add(workUnit.documentedFeatureObject.groupName());
                }
            }

            for (final DocWorkUnit workUnit : myWorkUnits) {
                processDocWorkUnit(cfg, workUnit, groups, data);
            }
            processIndex(cfg, new ArrayList<>(myWorkUnits), groups);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the set of all DocWorkUnits for which we are generating docs.
     */
    private Set<DocWorkUnit> computeWorkUnits() {
        final TreeSet<DocWorkUnit> workUnits = new TreeSet<>();

        for (final ClassDoc doc : rootDoc.classes()) {
            final Class<?> clazz = getClassForClassDoc(doc);

            final DocumentedFeatureObject feature = getFeatureForClassDoc(doc);
            final DocumentedFeatureHandler handler = createHandler(doc, feature);
            if (handler != null && handler.includeInDocs(doc)) {
                final String filename = handler.getDestinationFilename(doc, clazz);
                final DocWorkUnit workUnit = new DocWorkUnit(
                        doc.name(),
                        filename,
                        feature.groupName(),
                        feature,
                        handler,
                        doc,
                        clazz,
                        buildTimestamp,
                        absoluteVersion);
                workUnits.add(workUnit);
            }
        }

        return workUnits;
    }

    /**
     * Create a handler capable of documenting the class doc according to feature.  Returns
     * null if no appropriate handler is found or doc shouldn't be documented at all.
     *
     * @param doc
     * @param feature
     * @return
     */
    private DocumentedFeatureHandler createHandler(final ClassDoc doc, final DocumentedFeatureObject feature) {
        if (feature != null) {
            if (feature.enable()) {
                final DocumentedFeatureHandler handler = createDocumentedFeatureHandler();
                handler.setDoclet(this);
                return handler;
            } else {
                logger.info("Skipping disabled Documentation for " + doc);
            }
        }

        return null;
    }

    /**
     * Returns the instantiated DocumentedFeatureObject that describes the Doc
     * structure we will apply to Doc.
     *
     * @param doc
     * @return null if this proves inappropriate or doc shouldn't be documented
     */
    private DocumentedFeatureObject getFeatureForClassDoc(final ClassDoc doc) {
        final Class<? extends Object> docClass = getClassForClassDoc(doc);
        if (docClass != null && docClass.isAnnotationPresent(DocumentedFeature.class)) {
            DocumentedFeature f = docClass.getAnnotation(DocumentedFeature.class);
            return new DocumentedFeatureObject(docClass, f.enable(), f.groupName(), f.summary(), f.extraDocs());
        } else {
            return null;
        }
    }

    /**
     * Return the Java class described by the ClassDoc doc
     *
     * @param doc
     * @return
     */
    private Class<? extends Object> getClassForClassDoc(final ClassDoc doc) {
        try {
            return DocletUtils.getClassForDoc(doc);
        } catch (ClassNotFoundException e) {
            // we got a classdoc for a class we can't find.  Maybe in a library or something
            return null;
        } catch (NoClassDefFoundError e) {
            return null;
        } catch (UnsatisfiedLinkError e) {
            return null; // naughty BWA bindings
        }
    }

    /**
     * Create the php index listing all of the Docs features
     *
     * @param cfg
     * @param indexData
     * @throws IOException
     */
    private void processIndex(
            final Configuration cfg,
            final List<DocWorkUnit> indexData,
            //final List<Map<String, String>> data,
            final List<Map<String, String>> groups
   ) throws IOException {
        // Get or create a template and merge in the data
        final Template temp = cfg.getTemplate(getIndexTemplateName());
        final File indexFile = new File(destinationDir + "/index." + outputFileExtension);
        try (final FileOutputStream fileOutStream = new FileOutputStream(indexFile);
             final OutputStreamWriter outWriter = new OutputStreamWriter(fileOutStream)) {
                temp.process(groupIndexData(indexData, groups), outWriter);
        } catch (TemplateException e) {
            throw new DocException("TemplateExceptioon during documentation creation", e);
        }
    }

    /**
     * Helpful function to create the php index.  Given all of the already run DocWorkUnits,
     * create the high-level grouping data listing individual features by group.
     *
     * @param indexData
     * @return
     */
    private Map<String, Object> groupIndexData(
            final List<DocWorkUnit> indexData,
            final List<Map<String, String>> groups
    ) {
        //
        // root -> data -> { summary -> y, filename -> z }, etc
        //      -> groups -> group1, group2, etc.
        Map<String, Object> root = new HashMap<>();
        List<Map<String, String>> data = new ArrayList<>();
        for(DocWorkUnit workUnit : indexData) {
            data.add(workUnit.indexDataMap());
        }

        Collections.sort(indexData);

        root.put("data", data);
        root.put("groups", groups);
        root.put("timestamp", buildTimestamp);
        root.put("version", absoluteVersion);

        return root;
    }


    /**
     * Helper routine that returns the map of name and summary given the documentedFeatureObject
     * AND adds a super-category so that we can custom-order the categories in the index
     *
     * @param annotation
     * @return
     */
    protected Map<String, String> getGroupMap(final DocumentedFeatureObject annotation) {
        Map<String, String> root = new HashMap<>();
        root.put("id", annotation.groupName().replaceAll("\\W", ""));
        root.put("name", annotation.groupName());
        root.put("summary", annotation.summary());
        return root;
    };

    /**
     * Helper function that finding the DocWorkUnit associated with class from among all of the work units
     *
     * @param c the class we are looking for
     * @return the DocWorkUnit whose .clazz.equals(c), or null if none could be found
     */
    public final DocWorkUnit findWorkUnitForClass(final Class<?> c) {
        for (final DocWorkUnit unit : this.myWorkUnits)
            if (unit.clazz.equals(c))
                return unit;
        return null;
    }

    /**
     * Return the ClassDoc associated with clazz
     *
     * @param clazz
     * @return
     */
    public ClassDoc getClassDocForClass(final Class<?> clazz) {
        return rootDoc.classNamed(clazz.getName());
    }

    /**
     * High-level function that processes a single DocWorkUnit unit using its handler
     *
     * @param cfg
     * @param workUnit
     * @param data
     * @throws IOException
     */
    private void processDocWorkUnit(
            final Configuration cfg,
            final DocWorkUnit workUnit,
            final List<Map<String, String>> groups,
            final List<Map<String, String>> data)
    {
        workUnit.handler.processOne(workUnit);
        workUnit.rootMap.put("groups", groups);
        workUnit.rootMap.put("data", data);

        try {
            // Merge data-model with template
            Template template = cfg.getTemplate(workUnit.handler.getTemplateName(workUnit.classDoc));
            File outputPath = new File(destinationDir + "/" + workUnit.filename);
            try (final Writer out = new OutputStreamWriter(new FileOutputStream(outputPath))) {
                template.process(workUnit.rootMap, out);
            }
        } catch (IOException e) {
            throw new DocException("IOException during documentation creation", e);
        } catch (TemplateException e) {
            throw new DocException("TemplateException during documentation creation", e);
        }

        // Create GSON-friendly container object
        GSONWorkUnit gsonworkunit = createGSONWorkUnit(workUnit, groups, data);

        gsonworkunit.populate(
                workUnit.rootMap.get("summary").toString(),
                workUnit.rootMap.get("gson-arguments"),
                workUnit.rootMap.get("description").toString(),
                workUnit.rootMap.get("name").toString(),
                workUnit.rootMap.get("group").toString()
        );

        // Convert object to JSON and write JSON entry to file
        File outputPathForJSON = new File(destinationDir + "/" + workUnit.filename + ".json");

        try (final BufferedWriter jsonWriter = new BufferedWriter(new FileWriter(outputPathForJSON))) {
            Gson gson = new GsonBuilder()
                .serializeSpecialFloatingPointValues()
                .setPrettyPrinting()
                .create();
            String json = gson.toJson(gsonworkunit);
            jsonWriter.write(json);
        } catch (IOException e) {
            throw new DocException("Failed to create JSON entry", e);
        }
    }

}
