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

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.Tag;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.broadinstitute.barclay.argparser.*;
import org.broadinstitute.barclay.utils.BarclayException;
import org.broadinstitute.barclay.utils.JVMUtils;

import org.broadinstitute.barclay.utils.Pair;

import java.lang.reflect.*;
import java.util.*;

//TODO: rename this class to something better after the PR is reviewed

/**
 * Base implementation of the DocumentedFeatureHandler
 */
public abstract class GenericDocumentationHandler extends DocumentedFeatureHandler {
    private static Logger logger = LogManager.getLogger(GenericDocumentationHandler.class);

    private static final String NAME_FOR_POSITIONAL_ARGS = "[NA - Positional]";
    /**
     * The Class we are documenting
     */
    private DocWorkUnit currentWorkUnit;

    @Override
    public boolean includeInDocs(final ClassDoc doc) {
        try {
            Class<?> type = DocletUtils.getClassForDoc(doc);
            boolean hidden = !getDoclet().showHiddenFeatures() && type.isAnnotationPresent(Hidden.class);
            return !hidden && JVMUtils.isConcrete(type);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String getTemplateName(ClassDoc doc) {
        return "generic.template.html";
    }

    /**
     * Create the Freemarker Template Map, with the following top-level structure:
     *
     * name -> String
     * group -> String
     * summary -> String
     * description -> String
     * version -> String
     * timestamp -> String
     * extraDocs -> List
     * arguments -> List
     * gson-arguments -> List
     * plugin1 ->
     *      name -> String
     *      filename -> String (link)
     * .
     * .
     * pluginN -> ...
     *
     * @param workUnit
     */
    @SuppressWarnings("unchecked")
    @Override
    public void processOne(final DocWorkUnit workUnit) {
        this.currentWorkUnit = workUnit;

        // Docgen needs to use the new CommandLineArgumentParser since it needs to interrogate plugins
        CommandLineArgumentParser clp;
        Object argumentContainer;
        List<? extends CommandLinePluginDescriptor<?>> pluginDescriptors = new ArrayList<>();
        try {
            argumentContainer = currentWorkUnit.clazz.newInstance();
            if (argumentContainer instanceof CommandLinePluginProvider ) {
                pluginDescriptors = ((CommandLinePluginProvider) argumentContainer).getPluginDescriptors();
                clp = new CommandLineArgumentParser(argumentContainer, pluginDescriptors);
            } else {
                clp = new CommandLineArgumentParser(argumentContainer);
            }
        } catch (IllegalAccessException | InstantiationException e) {
            throw new RuntimeException(e);
        }

        final Map<String, Object> root = new HashMap<>();

        addHighLevelBindings(clp, argumentContainer, pluginDescriptors, root);
        addCommandLineArgumentBindings(clp, argumentContainer, pluginDescriptors, root);
        addExtraDocsBindings(clp, argumentContainer, pluginDescriptors, root);
        addCustomBindings(currentWorkUnit.clazz, currentWorkUnit.classDoc, root);

        root.put("group", currentWorkUnit.group);

        currentWorkUnit.setHandlerContent((String) root.get("summary"), root);
    }

    /**
     * Add high-level summary information about toProcess to root, such as its
     * name, summary, description, version, etc.
     *
     * @param root
     */
    protected void addHighLevelBindings(
            final CommandLineArgumentParser clp,
            final Object argumentContainer,
            final List<? extends CommandLinePluginDescriptor<?>> pluginDescriptors,
            final Map<String, Object> root)
    {
        root.put("name", currentWorkUnit.classDoc.name());

        //TODO: we should be hijacking the CommandLineProgramProperties if they're present for the summary
        final StringBuilder summaryBuilder = new StringBuilder();
        for (final Tag tag : currentWorkUnit.classDoc.firstSentenceTags()) {
            summaryBuilder.append(tag.text());
        }
        root.put("summary", summaryBuilder.toString());

        final String tagFilterPrefix = "@" + getTagFilterPrefix() + ".";
        final StringBuilder descBuilder = new StringBuilder();
        Arrays.stream(currentWorkUnit.classDoc.inlineTags())
                .filter(t -> !t.name().startsWith(tagFilterPrefix))
                .forEach(t ->descBuilder.append(t.text()));
        root.put("description", descBuilder.toString());

        root.put("timestamp", currentWorkUnit.buildTimestamp);
        root.put("version", currentWorkUnit.absoluteVersion);


        // now add top level bindings the list of defaults for each plugin descriptor, in case they're needed in the doc
        for (final CommandLinePluginDescriptor<?> descriptor :  pluginDescriptors) {
            final String descriptorName = descriptor.getDisplayName();  // key/name at the root of the freemarker map
            final HashSet<HashMap<String, Object>> defaultsForPlugins = new HashSet<>();
            root.put(descriptorName, defaultsForPlugins);

            for (final Object plugin : descriptor.getDefaultInstances()) {
                final HashMap<String, Object> pluginDetails = new HashMap<>();
                pluginDetails.put("name", plugin.getClass().getSimpleName());
                pluginDetails.put("filename", DocUtils.phpFilenameForClass(plugin.getClass(), getDoclet().getOutputFileExtension()));
                defaultsForPlugins.add(pluginDetails);
            }
        }
    }

    /**
     * Umbrella function that groups the collection of values for specific annotations applied to an
     * instance of class c. Lists of collected values are added directly to the "toProcess" object.
     * Requires being able to instantiate the class.
     *
     * @param classToProcess the object to instantiate and query for the documentedFeatureObject
     * @param root the root of the document handler, to which we'll store collected annotations
     */
    protected abstract void addCustomBindings(Class<?> classToProcess, ClassDoc classDoc, Map<String, Object> root);

    /**
     * Subclasses override this to have javadoc tags with this prefix procesed specially
     * @return
     */
    protected String getTagFilterPrefix(){ return ""; }

    /**
     * Add bindings describing related capabilities to toProcess
     *
     * @param clp
     * @param argumentContainer
     * @param pluginDescriptors
     * @param root
     */
    protected void addExtraDocsBindings(
            final CommandLineArgumentParser clp,
            final Object argumentContainer,
            final List<? extends CommandLinePluginDescriptor<?>> pluginDescriptors,
            final Map<String, Object> root)
    {
        final List<Map<String, Object>> extraDocsData = new ArrayList<Map<String, Object>>();

        // add in all of the explicitly related extradocs items
        for (final Class<?> extraDocClass : currentWorkUnit.documentedFeatureObject.extraDocs()) {
            final DocWorkUnit otherUnit = getDoclet().findWorkUnitForClass(extraDocClass);
            if (otherUnit != null) {
                extraDocsData.add(
                        new HashMap<String, Object>() {
                            static final long serialVersionUID = 0L;
                            {
                                put("name", otherUnit.name);
                                put("filename", otherUnit.filename);
                            }
                        });
            } else {
                final String msg = String.format(
                        "An \"extradocs\" value (%s) was specified for (%s), but the target was not included in the " +
                        "source list for this javadoc run, or the target has no documentation.",
                        extraDocClass,
                        currentWorkUnit.name
                );
                throw new DocException(msg);
            }
        }
        root.put("extradocs", extraDocsData);
    }

    @SuppressWarnings("unchecked")
    /**
     * Add information about all of the arguments available to toProcess root
     *
     * @param root
     */
    protected void addCommandLineArgumentBindings(
            final CommandLineArgumentParser clp,
            final Object argumentContainer,
            final List<? extends CommandLinePluginDescriptor<?>> pluginDescriptors,
            final Map<String, Object> root)
    {
        final Map<String, List<Map<String, Object>>> args = createArgumentMap();
        root.put("arguments", args);

        processPositionalArguments(clp, args);

        // loop over all of the arguments according to the parsing engine
        for (final CommandLineArgumentParser.ArgumentDefinition argDef : clp.getArgumentDefinitions()) {
            processNamedArgument(args, argDef);
        }

        // sort the arguments
        for (final Map.Entry<String, List<Map<String, Object>>> entry : args.entrySet()) {
            entry.setValue(sortArguments(entry.getValue()));
        }

        // make a GSON-friendly map of arguments -- uses some hacky casting
        final List<GSONArgument> allGSONArgs = new ArrayList<>();
        for (final Map<String, Object> item : args.get("all")) {
            GSONArgument itemGSONArg = createGSONArgument(item);

            itemGSONArg.populate(item.get("summary").toString(),
                    item.get("name").toString(),
                    item.get("synonyms").toString(),
                    item.get("type").toString(),
                    item.get("required").toString(),
                    item.get("fulltext").toString(),
                    item.get("defaultValue").toString(),
                    item.get("minValue").toString(),
                    item.get("maxValue").toString(),
                    item.get("minRecValue").toString(),
                    item.get("maxRecValue").toString(),
                    item.get("kind").toString(),
                    (List<Map<String, Object>>)item.get("options")
            );
            allGSONArgs.add(itemGSONArg);
        }
        root.put("gson-arguments", allGSONArgs);
    }

    protected GSONArgument createGSONArgument(final Map<String, Object> item) { return new GSONArgument(); }

    private void processNamedArgument(Map<String, List<Map<String, Object>>> args, CommandLineArgumentParser.ArgumentDefinition argDef) {
        FieldDoc fieldDoc = getFieldDoc(currentWorkUnit.classDoc, argDef.field.getName());
        if (fieldDoc == null) {
            for (final ClassDoc classDoc : getRootDoc().classes()) {
                fieldDoc = getFieldDoc(classDoc, argDef.field.getName());
                if (fieldDoc != null) {
                    break;
                }
            }
        }
        if (fieldDoc == null) {
            throw new BarclayException(argDef.field.getClass().getName() +
                    " was referenced by, but not included in the list of javadoc targets");
        } else {
            final Map<String, Object> argBindings = docForArgument(fieldDoc, argDef);
            if (argDef.field.getAnnotation(Hidden.class) == null || getDoclet().showHiddenFeatures()) {
                final String kind = docKindOfArg(argDef);
                argBindings.put("kind", kind);

                // Retrieve default value
                final Object fieldValue = argDef.getFieldValue();
                argBindings.put("defaultValue",
                        fieldValue == null ?
                                argDef.defaultValue :
                                prettyPrintValueString(fieldValue));

                if (fieldValue instanceof Number) {
                    // Retrieve min and max / hard and soft value thresholds for numeric args
                    Argument argAnnotation = argDef.field.getAnnotation(Argument.class);
                    argBindings.put("minValue", argAnnotation.minValue());
                    argBindings.put("maxValue", argAnnotation.maxValue());
                    argBindings.put("minRecValue",
                            argAnnotation.minRecommendedValue() != Double.NEGATIVE_INFINITY ?
                                    argAnnotation.minRecommendedValue() :
                                    "NA");
                    argBindings.put("maxRecValue",
                            argAnnotation.maxRecommendedValue() != Double.POSITIVE_INFINITY ?
                                    argAnnotation.maxRecommendedValue() :
                                    "NA");
                } else {
                    argBindings.put("minValue", "NA");
                    argBindings.put("maxValue", "NA");
                    argBindings.put("minRecValue", "NA");
                    argBindings.put("maxRecValue", "NA");
                }
                // if its a plugin descriptor arg, get the allowed values
                processPluginDescriptorArgument(argDef, argBindings);

                // Finalize argument bindings
                args.get(kind).add(argBindings);
                args.get("all").add(argBindings);
            }
        }
    }

    private void processPositionalArguments(CommandLineArgumentParser clp, Map<String, List<Map<String, Object>>> args) {
        // first get the positional arguments
        final Field positionalField = clp.getPositionalArguments();
        if (positionalField != null) {
            final Map<String, Object> argBindings = new HashMap<>();
            PositionalArguments posArgs = positionalField.getAnnotation(PositionalArguments.class);
            argBindings.put("kind", "positional");
            argBindings.put("name", NAME_FOR_POSITIONAL_ARGS);
            argBindings.put("summary", posArgs.doc());
            argBindings.put("fulltext", posArgs.doc());
            argBindings.put("otherArgumentRequired", "NA");
            argBindings.put("synonyms", "NA");
            argBindings.put("exclusiveOf", "NA");
            argBindings.put("type", argumentTypeString(positionalField.getGenericType()));
            argBindings.put("options", Collections.EMPTY_LIST);
            argBindings.put("attributes", "NA");
            argBindings.put("required", "yes");
            argBindings.put("minRecValue", "NA");
            argBindings.put("maxRecValue", "NA");
            argBindings.put("minValue", "NA");
            argBindings.put("maxValue", "NA");
            argBindings.put("defaultValue", "NA");

            args.get("positional").add(argBindings);
            args.get("all").add(argBindings);
        }
    }

    protected void processPluginDescriptorArgument(
            final CommandLineArgumentParser.ArgumentDefinition argDef,
            final Map<String, Object> argBindings) {
        if (CommandLinePluginDescriptor.class.isAssignableFrom(argDef.parent.getClass()) &&
                CommandLineParser.getUnderlyingType(argDef.field).equals(String.class)) {
            final CommandLinePluginDescriptor<?> descriptor = (CommandLinePluginDescriptor<?>) argDef.parent;

            //TODO: delegate to usageForPluginDescriptorArgument
            // this argument is inside a plugin descriptor; the allowed values are the names of the classes that
            // are controlled by this descriptor; delegate to descriptor to get the list
            final List<String> allowedValues = new ArrayList<>(
                    descriptor.getAllowedValuesForDescriptorArgument(argDef.getLongName()));
            final List<Map<String, Object>> allowedOptions = new ArrayList<>();
            for (String s : allowedValues) {
                final String summary = getSummaryForPlugin(descriptor, s);
                allowedOptions.add(
                        new HashMap<String, Object>() {
                            static final long serialVersionUID = 0L;
                            {
                                put("name", getAnchorLinkStringFor(s, argDef.parent.getClass()));
                                put("summary", summary);
                            }
                        });
            }
            argBindings.put("options", allowedOptions);
        }
    }

    //TODO: need a way to get the classname of the plugin (this code is using the classname of the DESCRIPTOR
    private String getAnchorLinkStringFor(final String pluginDisplayName, final Class<?> pluginClazz) {
        return String.format("<a href=\"%s\">%s</a>",
                DocUtils.phpFilenameForClass(pluginClazz, getDoclet().getOutputFileExtension()),
                pluginDisplayName);
    }

    private String getSummaryForPlugin(final CommandLinePluginDescriptor<?> descriptor, final String pluginName) {
        final Class<?> pluginClass = descriptor.getClassForInstance(pluginName);
        if (pluginClass != null) {
            final ClassDoc doc = this.getDoclet().getClassDocForClass(pluginClass);
            if (doc != null) {
                return doc.getRawCommentText();
            }
        }
        return "No summary available";
    }

    /**
     * Return the argument kind (required, advanced, hidden, etc) of this argumentDefinition
     *
     * @param argumentDefinition
     * @return
     */
    private String docKindOfArg(CommandLineArgumentParser.ArgumentDefinition argumentDefinition) {

        // positional
        // required (common or otherwise)
        // common optional
        // advanced
        // hidden

        // Required first (after positional, which are separate), regardless of what else it might be
        if (argumentDefinition.isControlledByPlugin()) {
            return "dependent";
        }
        if (!argumentDefinition.optional) {
            return "required";
        }
        if (argumentDefinition.isCommon) {  // these will all be optional
            return "common";
        }
        if (argumentDefinition.field.isAnnotationPresent(Advanced.class)) {
            return "advanced";
        }
        if (argumentDefinition.field.isAnnotationPresent(Hidden.class)) {
            return "hidden";
        }
        if (argumentDefinition.field.isAnnotationPresent(Deprecated.class)) {
            return "deprecated";
        }
        return "optional";
    }

    /**
     * Create the argument map for holding class arguments
     *
     * @return
     */
    private Map<String, List<Map<String, Object>>> createArgumentMap() {
        final Map<String, List<Map<String, Object>>> args = new HashMap<String, List<Map<String, Object>>>();
        args.put("all", new ArrayList<>());
        args.put("common", new ArrayList<>());
        args.put("positional", new ArrayList<>());
        args.put("required", new ArrayList<>());
        args.put("optional", new ArrayList<>());
        args.put("advanced", new ArrayList<>());
        args.put("dependent", new ArrayList<>());
        args.put("hidden", new ArrayList<>());
        args.put("deprecated", new ArrayList<>());
        return args;
    }

    /**
     * Sorts the individual argument list in unsorted according to CompareArgumentsByName
     *
     * @param unsorted
     * @return
     */
    private List<Map<String, Object>> sortArguments(final List<Map<String, Object>> unsorted) {
        Collections.sort(unsorted, new CompareArgumentsByName());
        return unsorted;
    }

    /**
     * Sort arguments by case-insensitive comparison ignoring the -- and - prefixes
     */
    private class CompareArgumentsByName implements Comparator<Map<String, Object>> {
        public int compare(Map<String, Object> x, Map<String, Object> y) {
            return elt(x).compareTo(elt(y));
        }

        private String elt(Map<String, Object> m) {
            final String v = m.get("name").toString().toLowerCase();
            if (v.startsWith("--"))
                return v.substring(2);
            else if (v.startsWith("-"))
                return v.substring(1);
            else if (v.equals(NAME_FOR_POSITIONAL_ARGS.toLowerCase()))
                return "Positional";
            else
                throw new RuntimeException("Expect to see arguments beginning with at least one -, but found " + v);
        }
    }

    /**
     * Pretty prints value
     *
     * Assumes value != null
     *
     * @param value
     * @return
     */
    private Object prettyPrintValueString(final Object value) {
        if (value.getClass().isArray()) {
            final Class<?> type = value.getClass().getComponentType();
            if (boolean.class.isAssignableFrom(type))
                return Arrays.toString((boolean[]) value);
            if (byte.class.isAssignableFrom(type))
                return Arrays.toString((byte[]) value);
            if (char.class.isAssignableFrom(type))
                return Arrays.toString((char[]) value);
            if (double.class.isAssignableFrom(type))
                return Arrays.toString((double[]) value);
            if (float.class.isAssignableFrom(type))
                return Arrays.toString((float[]) value);
            if (int.class.isAssignableFrom(type))
                return Arrays.toString((int[]) value);
            if (long.class.isAssignableFrom(type))
                return Arrays.toString((long[]) value);
            if (short.class.isAssignableFrom(type))
                return Arrays.toString((short[]) value);
            if (Object.class.isAssignableFrom(type))
                return Arrays.toString((Object[]) value);
            else
                throw new RuntimeException("Unexpected array type in prettyPrintValue.  Value was " + value + " type is " + type);
        } else if (value instanceof String) {
            return value.equals("") ? "\"\"" : value;
        } else {
            return value.toString();
        }
    }

    /**
     * Gets the javadocs associated with field name in classDoc.  Throws a
     * runtime exception if this proves impossible.
     */
    private FieldDoc getFieldDoc(final ClassDoc classDoc, final String name) {
        return getFieldDoc(classDoc, name, false);
    }

    /**
     * Recursive helper routine to getFieldDoc()
     */
    private FieldDoc getFieldDoc(final ClassDoc classDoc, final String name, final boolean primary) {
        for (final FieldDoc fieldDoc : classDoc.fields(false)) {
            if (fieldDoc.name().equals(name))
                return fieldDoc;

            final Field field = DocletUtils.getFieldForFieldDoc(fieldDoc);
            if (field == null)
                throw new RuntimeException("Could not find the field corresponding to " + fieldDoc +
                        ", presumably because the field is inaccessible");
            if (field.isAnnotationPresent(ArgumentCollection.class)) {
                final ClassDoc typeDoc = getRootDoc().classNamed(fieldDoc.type().qualifiedTypeName());
                if (typeDoc == null)
                    throw new DocException("Tried to get javadocs for ArgumentCollection field " +
                            fieldDoc + " but couldn't find the class in the RootDoc");
                else {
                    FieldDoc result = getFieldDoc(typeDoc, name, false);
                    if (result != null)
                        return result;
                    // else keep searching
                }
            }
        }

        // if we didn't find it here, wander up to the superclass to find the field
        if (classDoc.superclass() != null) {
            return getFieldDoc(classDoc.superclass(), name, false);
        }

        if (primary)
            throw new RuntimeException("No field found for expected field " + name);
        else
            return null;
    }

    /**
     * Returns a Pair of (main, synonym) names for argument with fullName s1 and
     * shortName s2.
     *
     * Previously we had it so the main name was selected to be the longest of the two, provided
     * it didn't exceed MAX_DISPLAY_NAME, in which case the shorter was taken. But we now disable
     * the length-based name rearrangement in order to maintain consistency in the Docs table.
     *
     * This may cause messed up spacing in the CLI-help display but we don't care as much about that
     * since more users use the online Docs for looking up arguments.
     *
     * @param s1 the short argument name without -, or null if not provided
     * @param s2 the long argument name without --, or null if not provided
     * @return A pair of fully qualified names (with - or --) for the argument.  The first
     *         element is the primary display name while the second (potentially null) is a
     *         synonymous name.
     */
    private Pair<String, String> displayNames( String s1, String s2) {
        s1 = s1 == null ? null : "-" + s1;
        s2 = s2 == null ? null : "--" + s2;

        if (s1 == null) return new Pair<>(s2, null);
        if (s2 == null) return new Pair<>(s1, null);

        return new Pair<>(s2, s1);
    }

    /**
     * Returns a human readable string that describes the Type type of an argument.
     *
     * This will include parametrized types, so that Set{T} shows up as Set(T) and not
     * just Set in the docs.
     *
     * @param type
     * @return
     */
    protected String argumentTypeString(final Type type) {
        if (type instanceof ParameterizedType) {
            final ParameterizedType parameterizedType = (ParameterizedType) type;
            final List<String> subs = new ArrayList<>();
            for (final Type actualType : parameterizedType.getActualTypeArguments())
                subs.add(argumentTypeString(actualType));
            return argumentTypeString(((ParameterizedType) type).getRawType()) + "[" + String.join(",", subs) + "]";
        } else if (type instanceof GenericArrayType) {
            return argumentTypeString(((GenericArrayType) type).getGenericComponentType()) + "[]";
        } else if (type instanceof WildcardType) {
            throw new RuntimeException("We don't support wildcards in arguments: " + type);
        } else if (type instanceof Class<?>) {
            return ((Class) type).getSimpleName();
        } else {
            throw new DocException("Unknown type: " + type);
        }
    }

    /**
     * High-level entry point for creating a FreeMarker map describing the argument
     * source with definition def, with associated javadoc fieldDoc.
     *
     * @param fieldDoc
     * @param def
     * @return a non-null Map binding argument keys with their values
     */
    protected Map<String, Object> docForArgument(final FieldDoc fieldDoc, final CommandLineArgumentParser.ArgumentDefinition def) {
        final Map<String, Object> root = new HashMap<>();

        final Pair<String, String> names = displayNames(def.shortName, def.fullName);
        root.put("name", names.getFirst());
        root.put("synonyms", names.getSecond() != null ? names.getSecond() : "NA");
        root.put("required", def.optional ? "no": "yes") ;
        root.put("type", argumentTypeString(def.field.getGenericType()));

        // summary and fulltext
        root.put("summary", def.doc != null ? def.doc : "");
        root.put("fulltext", fieldDoc.commentText());

        // Does this argument interact with any others?
        if (def.isControlledByPlugin()) {
            root.put("otherArgumentRequired",
                    def.parent.getClass().getSimpleName().length() == 0 ?
                        def.parent.getClass().getName() :
                        def.parent.getClass().getSimpleName());
        } else {
            root.put("otherArgumentRequired", "NA");
        }

        root.put("exclusiveOf",
                def.mutuallyExclusive != null && !def.mutuallyExclusive.isEmpty() ?
                    String.join(", ", def.mutuallyExclusive) :
                    "NA");

        // enum options
        root.put("options",
                def.field.getType().isEnum() ?
                        docForEnumArgument(def.field.getType()) :
                        Collections.EMPTY_LIST);

        List<String> attributes = new ArrayList<>();
        if (!def.optional) {
            attributes.add("required");
        }
        if (def.field.isAnnotationPresent(Deprecated.class)) {
            attributes.add("deprecated");
        }
        root.put("attributes", attributes.size() > 0 ? String.join(", ", attributes) : "NA");

        return root;
    }

    /**
     * Helper routine that provides a FreeMarker map for an enumClass, grabbing the
     * values of the enum and their associated javadoc documentation.
     *
     * @param enumClass
     * @return
     */
    private List<Map<String, Object>> docForEnumArgument(final Class<?> enumClass) {
        final ClassDoc doc = this.getDoclet().getClassDocForClass(enumClass);
        if ( doc == null ) {
            throw new RuntimeException("Tried to get docs for enum " + enumClass + " but got null instead");
        }

        final Set<String> enumConstantFieldNames = enumConstantsNames(enumClass);
        final List<Map<String, Object>> bindings = new ArrayList<Map<String, Object>>();
        for (final FieldDoc fieldDoc : doc.fields(false)) {
            if (enumConstantFieldNames.contains(fieldDoc.name()) ) {
                bindings.add(
                        new HashMap<String, Object>() {
                            static final long serialVersionUID = 0L;
                            {
                                put("name", fieldDoc.name());
                                put("summary", fieldDoc.commentText());
                            }
                        }
                );
            }
        }

        return bindings;
    }

    /**
     * @return a non-null set of fields that are enum constants
     */
    private Set<String> enumConstantsNames(final Class<?> enumClass) {
        final Set<String> enumConstantFieldNames = new HashSet<String>();

        for ( final Field field : enumClass.getFields() ) {
            if ( field.isEnumConstant() )
                enumConstantFieldNames.add(field.getName());
        }

        return enumConstantFieldNames;
    }
}
