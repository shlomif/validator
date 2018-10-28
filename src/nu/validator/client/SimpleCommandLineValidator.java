/*
 * Copyright (c) 2013-2018 Mozilla Foundation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package nu.validator.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Pattern;

import nu.validator.htmlparser.sax.XmlSerializer;
import nu.validator.io.SystemIdIOException;
import nu.validator.messages.GnuMessageEmitter;
import nu.validator.messages.JsonMessageEmitter;
import nu.validator.messages.MessageEmitter;
import nu.validator.messages.MessageEmitterAdapter;
import nu.validator.messages.TextMessageEmitter;
import nu.validator.messages.XmlMessageEmitter;
import nu.validator.servlet.imagereview.ImageCollector;
import nu.validator.source.SourceCode;
import nu.validator.validation.SimpleDocumentValidator;
import nu.validator.validation.SimpleDocumentValidator.SchemaReadException;
import nu.validator.xml.SystemErrErrorHandler;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *
 * Simple command-line validator for HTML/XHTML files.
 */
public class SimpleCommandLineValidator {

    private static Package pkg = SimpleCommandLineValidator.class.getPackage();

    private static String version = pkg.getImplementationVersion();

    private static String userAgent;

    private static SimpleDocumentValidator validator;

    private static OutputStream out;

    private static Pattern filterPattern;

    private static MessageEmitterAdapter errorHandler;
    private static MessageEmitter errorHandlerEmitter;

    private static boolean verbose;

    private static boolean errorsOnly;

    private static boolean wError;

    private static boolean exitZeroAlways;

    private static boolean loadEntities;

    private static boolean noLangDetect;

    private static boolean noStream;

    private static boolean alsoCheckCSS;

    private static boolean skipNonCSS;

    private static boolean forceCSS;

    private static boolean alsoCheckSVG;

    private static boolean skipNonSVG;

    private static boolean forceSVG;

    private static boolean skipNonHTML;

    private static boolean forceHTML;

    private static boolean asciiQuotes;

    private static int lineOffset;

    private static enum OutputFormat {
        HTML, XHTML, TEXT, XML, JSON, RELAXED, SOAP, UNICORN, GNU
    }

    private static OutputFormat outputFormat;

    private static String schemaUrl;

    private static boolean hasSchemaOption;

    public static void main(String[] args) throws SAXException, Exception {
        out = System.err;
        userAgent = "Validator.nu/LV";
        System.setProperty("nu.validator.datatype.warn", "true");
        errorsOnly = false;
        wError = false;
        alsoCheckCSS = false;
        skipNonCSS = false;
        forceCSS = false;
        alsoCheckSVG = false;
        skipNonSVG = false;
        forceSVG = false;
        skipNonHTML = false;
        forceHTML = false;
        loadEntities = false;
        exitZeroAlways = false;
        noLangDetect = false;
        noStream = false;
        lineOffset = 0;
        asciiQuotes = false;
        verbose = false;

        filterPattern = null;
        String filterString = "";
        String outFormat = null;
        schemaUrl = null;
        hasSchemaOption = false;
        boolean hasFileArgs = false;
        boolean readFromStdIn = false;
        int fileArgsStart = 0;
        if (args.length == 0) {
            usage();
            System.exit(1);
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-")) {
                readFromStdIn = true;
                break;
            } else if (!args[i].startsWith("--")) {
                hasFileArgs = true;
                fileArgsStart = i;
                break;
            } else {
                if ("--verbose".equals(args[i])) {
                    verbose = true;
                } else if ("--errors-only".equals(args[i])) {
                    errorsOnly = true;
                    System.setProperty("nu.validator.datatype.warn", "false");
                } else if ("--Werror".equals(args[i])) {
                    wError = true;
                } else if ("--exit-zero-always".equals(args[i])) {
                    exitZeroAlways = true;
                } else if ("--asciiquotes".equals(args[i])) {
                    asciiQuotes = true;
                } else if ("--filterfile".equals(args[i])) {
                    File filterFile = new File(args[++i]);
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = //
                            new BufferedReader(new FileReader(filterFile))) {
                        String line;
                        String pipe = "";
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("#")) {
                                continue;
                            }
                            sb.append(pipe);
                            sb.append(line);
                            pipe = "|";
                        }
                        if (sb.length() != 0) {
                            if ("".equals(filterString)) {
                                filterString = sb.toString();
                            } else {
                                filterString += "|" + sb.toString();
                            }
                        }
                    } catch (FileNotFoundException e) {
                        System.err.println("error: File not found: "
                                + filterFile.getPath());
                        System.exit(1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if ("--filterpattern".equals(args[i])) {
                    if ("".equals(filterString)) {
                        filterString = args[++i];
                    } else {
                        filterString += "|" + args[++i];
                    }
                } else if ("--format".equals(args[i])) {
                    outFormat = args[++i];
                } else if ("--user-agent".equals(args[i])) {
                    userAgent = args[++i];
                } else if ("--version".equals(args[i])) {
                    if (version != null) {
                        System.out.println(version);
                    } else {
                        System.out.println("[unknown version]");
                    }
                    System.exit(0);
                } else if ("--help".equals(args[i])) {
                    help();
                    System.exit(0);
                } else if ("--also-check-css".equals(args[i])) {
                    alsoCheckCSS = true;
                } else if ("--skip-non-css".equals(args[i])) {
                    skipNonCSS = true;
                } else if ("--css".equals(args[i])) {
                    forceCSS = true;
                } else if ("--also-check-svg".equals(args[i])) {
                    alsoCheckSVG = true;
                } else if ("--skip-non-svg".equals(args[i])) {
                    skipNonSVG = true;
                } else if ("--svg".equals(args[i])) {
                    forceSVG = true;
                } else if ("--skip-non-html".equals(args[i])) {
                    skipNonHTML = true;
                } else if ("--html".equals(args[i])) {
                    forceHTML = true;
                } else if ("--entities".equals(args[i])) {
                    loadEntities = true;
                } else if ("--no-langdetect".equals(args[i])) {
                    noLangDetect = true;
                } else if ("--no-stream".equals(args[i])) {
                    noStream = true;
                } else if ("--schema".equals(args[i])) {
                    hasSchemaOption = true;
                    schemaUrl = args[++i];
                    if (!schemaUrl.startsWith("http:")) {
                        System.err.println("error: The \"--schema\" option"
                                + " requires a URL for a schema.");
                        System.exit(1);
                    }
                }
            }
        }
        if (!"".equals(filterString)) {
            filterPattern = Pattern.compile(filterString);
        }
        if (schemaUrl == null) {
            schemaUrl = "http://s.validator.nu/html5-rdfalite.rnc";
        }
        if (outFormat == null) {
            outputFormat = OutputFormat.GNU;
        } else {
            if ("text".equals(outFormat)) {
                outputFormat = OutputFormat.TEXT;
            } else if ("gnu".equals(outFormat)) {
                outputFormat = OutputFormat.GNU;
            } else if ("xml".equals(outFormat)) {
                outputFormat = OutputFormat.XML;
            } else if ("json".equals(outFormat)) {
                outputFormat = OutputFormat.JSON;
            } else {
                System.err.printf("Error: Unsupported output format \"%s\"."
                        + " Must be \"gnu\", \"xml\", \"json\","
                        + " or \"text\".\n", outFormat);
                System.exit(1);
            }
        }
        if (readFromStdIn) {
            InputSource is = new InputSource(System.in);
            if (noLangDetect) {
                validator = new SimpleDocumentValidator(true, false, false);
            } else {
                validator = new SimpleDocumentValidator();
            }
            setup(schemaUrl);
            validator.checkHtmlInputSource(is);
            end();
        } else if (hasFileArgs) {
            if (noLangDetect) {
                validator = new SimpleDocumentValidator(true, false, false);
            } else {
                validator = new SimpleDocumentValidator(true, false, true);
            }
            setup(schemaUrl);
            checkFiles(args, fileArgsStart);
            end();
        } else {
            System.err.printf("\nError: No documents specified.\n");
            usage();
            System.exit(1);
        }
    }

    private static void setup(String schemaUrl) throws SAXException, Exception {
        setErrorHandler();
        if (cssCheckingEnabled()) {
            errorHandler.setLineOffset(-1);
        }
        errorHandler.setHtml(true);
        errorHandler.start(null);
        validator.setAllowCss(cssCheckingEnabled());
        try {
            validator.setUpMainSchema(schemaUrl, new SystemErrErrorHandler());
        } catch (SchemaReadException e) {
            System.out.println(e.getMessage() + " Terminating.");
            System.exit(1);
        } catch (StackOverflowError e) {
            System.out.println("StackOverflowError"
                    + " while evaluating HTML schema.");
            System.out.println("The checker requires a java thread stack size"
                    + " of at least 512k.");
            System.out.println("Consider invoking java with the -Xss"
                    + " option. For example:");
            System.out.println("\n  java -Xss512k -jar ~/vnu.jar FILE.html");
            System.exit(1);
        }
        validator.setUpValidatorAndParsers(errorHandler, noStream, loadEntities);
    }

    private static void end() throws SAXException {
        errorHandler.end("Document checking completed. No errors found.",
                "Document checking completed.", "");
        if (errorHandler.getErrors() > 0 || errorHandler.getFatalErrors() > 0
                || (wError && errorHandler.getWarnings() > 0)) {
            System.exit(exitZeroAlways ? 0 : 1);
        }
    }

    private static void checkFiles(String[] args, int fileArgsStart)
            throws IOException, Exception, SAXException {
        for (int i = fileArgsStart; i < args.length; i++) {
            if (args[i].startsWith("http://") || args[i].startsWith("https://")) {
                emitFilename(args[i]);
                try {
                    validator.checkHttpURL(args[i], userAgent, errorHandler);
                } catch (IOException e) {
                    errorHandler.fatalError(new SAXParseException(e.getMessage(),
                            null, args[i], -1, -1,
                            new SystemIdIOException(args[i], e.getMessage())));
                }
            } else {
                File file = new File(args[i]);
                if (file.isDirectory()) {
                    recurseDirectory(file);
                } else if (forceCSS) {
                    checkCssFile(file);
                } else if (skipNonCSS) {
                    if (isCss(file)) {
                        checkCssFile(file);
                    }
                } else if (alsoCheckCSS && isCss(file)) {
                    checkCssFile(file);
                } else if (forceSVG) {
                    checkSvgFile(file);
                } else if (skipNonSVG) {
                    if (isSvg(file)) {
                        checkSvgFile(file);
                    }
                } else if (alsoCheckSVG && isSvg(file)) {
                    checkSvgFile(file);
                } else {
                    checkHtmlFile(file);
                }
            }
        }
    }

    private static void recurseDirectory(File directory)
            throws IOException, Exception {
        if (directory.canRead()) {
            File[] files = directory.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    recurseDirectory(file);
                } else if (forceCSS) {
                    checkCssFile(file);
                } else if (skipNonCSS) {
                    if (isCss(file)) {
                        checkCssFile(file);
                    }
                } else if (alsoCheckCSS && isCss(file)) {
                    checkCssFile(file);
                } else if (forceSVG) {
                    checkSvgFile(file);
                } else if (skipNonSVG) {
                    if (isSvg(file)) {
                        checkSvgFile(file);
                    }
                } else if (alsoCheckSVG && isSvg(file)) {
                    checkSvgFile(file);
                } else {
                    checkHtmlFile(file);
                }
            }
        }
    }

    private static void checkSvgFile(File file) throws IOException, Exception {
        try {
            String path = file.getPath();
            if (!file.exists()) {
                if (verbose) {
                    errorHandler.warning(new SAXParseException(
                            "File not found.", null,
                            file.toURI().toURL().toString(), -1, -1));
                }
                return;
            } else {
                emitFilename(path);
                if (!"http://s.validator.nu/svg-xhtml5-rdf-mathml.rnc".equals(
                        validator.getMainSchemaUrl()) && !hasSchemaOption) {
                    setup("http://s.validator.nu/svg-xhtml5-rdf-mathml.rnc");
                }
                validator.checkXmlFile(file);
            }
        } catch (SAXException e) {
            if (!errorsOnly) {
                System.err.printf("\"%s\":-1:-1: warning: %s\n",
                        file.toURI().toURL().toString(), e.getMessage());
            }
        }
    }

    private static void checkCssFile(File file) throws IOException, Exception {
        try {
            String path = file.getPath();
            if (!file.exists()) {
                if (verbose) {
                    errorHandler.warning(new SAXParseException(
                            "File not found.", null,
                            file.toURI().toURL().toString(), -1, -1));
                }
                return;
            } else {
                emitFilename(path);
                validator.checkCssFile(file, true);
            }
        } catch (SAXException e) {
            if (!errorsOnly) {
                System.err.printf("\"%s\":-1:-1: warning: %s\n",
                        file.toURI().toURL().toString(), e.getMessage());
            }
        }
    }

    private static void checkHtmlFile(File file) throws IOException, Exception {
        try {
            String path = file.getPath();
            if (!file.exists()) {
                if (verbose) {
                    errorHandler.warning(new SAXParseException(
                            "File not found.", null,
                            file.toURI().toURL().toString(), -1, -1));
                }
                return;
            } else if (isXhtml(file)) {
                emitFilename(path);
                if (forceHTML) {
                    validator.checkHtmlFile(file, true);
                } else {
                    if (!"http://s.validator.nu/xhtml5-rdfalite.rnc".equals(
                            validator.getMainSchemaUrl()) && !hasSchemaOption) {
                        setup("http://s.validator.nu/xhtml5-rdfalite.rnc");
                    }
                    validator.checkXmlFile(file);
                }
            } else if (isHtml(file)) {
                emitFilename(path);
                if (!"http://s.validator.nu/html5-rdfalite.rnc".equals(
                        validator.getMainSchemaUrl()) && !hasSchemaOption) {
                    setup("http://s.validator.nu/html5-rdfalite.rnc");
                }
                validator.checkHtmlFile(file, true);
            } else {
                if (verbose) {
                    errorHandler.warning(new SAXParseException(
                            "File was not checked. Files must have .html,"
                                    + " .xhtml, .htm, or .xht extensions.",
                            null, file.toURI().toURL().toString(), -1, -1));
                }
            }
        } catch (SAXException e) {
            if (!errorsOnly) {
                System.err.printf("\"%s\":-1:-1: warning: %s\n",
                        file.toURI().toURL().toString(), e.getMessage());
            }
        }
    }

    private static boolean cssCheckingEnabled() {
        return forceCSS || alsoCheckCSS;
    }

    private static boolean isCss(File file) {
        String name = file.getName();
        return (name.endsWith(".css"));
    }

    private static boolean isSvg(File file) {
        String name = file.getName();
        return (name.endsWith(".svg"));
    }

    private static boolean isXhtml(File file) {
        String name = file.getName();
        return (name.endsWith(".xhtml") || name.endsWith(".xht"));
    }

    private static boolean isHtml(File file) {
        String name = file.getName();
        return (name.endsWith(".html") || name.endsWith(".htm") || !skipNonHTML);
    }

    private static void emitFilename(String name) {
        if (verbose) {
            System.out.println(name);
        }
    }

    private static void setErrorHandler() {
        SourceCode sourceCode = validator.getSourceCode();
        ImageCollector imageCollector = new ImageCollector(sourceCode);
        boolean showSource = false;
        if (errorHandlerEmitter == null) {
            if (outputFormat == OutputFormat.TEXT) {
                errorHandlerEmitter = new TextMessageEmitter(out, asciiQuotes);
            } else if (outputFormat == OutputFormat.GNU) {
                errorHandlerEmitter = new GnuMessageEmitter(out, asciiQuotes);
            } else if (outputFormat == OutputFormat.XML) {
                errorHandlerEmitter = new XmlMessageEmitter(new XmlSerializer(out));
            } else if (outputFormat == OutputFormat.JSON) {
                String callback = null;
                errorHandlerEmitter = new JsonMessageEmitter(
                        new nu.validator.json.Serializer(out), callback);
            } else {
                throw new RuntimeException("Bug. Should be unreachable.");
            }
        }
        if (outputFormat == OutputFormat.TEXT) {
            errorHandler = new MessageEmitterAdapter(filterPattern, sourceCode,
                    showSource, imageCollector, lineOffset, true, errorHandlerEmitter
                    );
        } else if (outputFormat == OutputFormat.GNU) {
            errorHandler = new MessageEmitterAdapter(filterPattern, sourceCode,
                    showSource, imageCollector, lineOffset, true,
                    errorHandlerEmitter);
        } else if (outputFormat == OutputFormat.XML) {
            errorHandler = new MessageEmitterAdapter(filterPattern, sourceCode,
                    showSource, imageCollector, lineOffset, true,
                    errorHandlerEmitter);
        } else if (outputFormat == OutputFormat.JSON) {
            errorHandler = new MessageEmitterAdapter(filterPattern, sourceCode,
                    showSource, imageCollector, lineOffset, true,
                    errorHandlerEmitter);
        } else {
            throw new RuntimeException("Bug. Should be unreachable.");
        }
        errorHandler.setErrorsOnly(errorsOnly);
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("");
        System.out.println("    java -jar vnu.jar [--errors-only] [--Werror] [--exit-zero-always]");
        System.out.println("         [--asciiquotes] [--user-agent USER_AGENT] [--no-langdetect]");
        System.out.println("         [--no-stream] [--filterfile FILENAME] [--filterpattern PATTERN]");
        System.out.println("         [--css] [--skip-non-css] [--also-check-css]");
        System.out.println("         [--svg] [--skip-non-svg] [--also-check-svg]");
        System.out.println("         [--html] [--skip-non-html] [--format gnu|xml|json|text]");
        System.out.println("         [--help] [--verbose] [--version] FILES");
        System.out.println("");
        System.out.println("    java -cp vnu.jar nu.validator.servlet.Main 8888");
        System.out.println("");
        System.out.println("    java -cp vnu.jar nu.validator.client.HttpClient FILES");
        System.out.println("");
        System.out.println("For detailed usage information, use \"java -jar vnu.jar --help\" or see:");
        System.out.println("");
        System.out.println("  http://validator.github.io/");
        System.out.println("");
        System.out.println("To read from stdin, use \"-\" as the filename, like this: \"java -jar vnu.jar - \".");
    }

    private static void help() {
        try (InputStream help = SimpleCommandLineValidator.class.getClassLoader().getResourceAsStream(
                "nu/validator/localentities/files/cli-help")) {
            System.out.println("");
            for (int b = help.read(); b != -1; b = help.read()) {
                System.out.write(b);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
