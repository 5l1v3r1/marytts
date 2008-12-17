/**
 * Copyright 2000-2006 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */

package marytts.server;

// General Java Classes
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;

import marytts.Version;
import marytts.datatypes.MaryDataType;
import marytts.modules.MaryModule;
import marytts.modules.ModuleRegistry;
import marytts.modules.Synthesis;
import marytts.modules.synthesis.Voice;
import marytts.server.http.MaryHttpServer;
import marytts.server.http.RequestHttp;
import marytts.util.MaryUtils;
import marytts.util.data.audio.MaryAudioUtils;
import marytts.util.io.FileUtils;
import marytts.util.string.StringUtils;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;


/**
 * The main program for the mary TtS system.
 *          It can run as a socket server or as a stand-alone program.
 * @author Marc Schr&ouml;der
 */

public class Mary {
    public static final int STATE_OFF = 0;
    public static final int STATE_STARTING = 1;
    public static final int STATE_RUNNING = 2;
    public static final int STATE_SHUTTING_DOWN = 3;

    private static Logger logger;

    private static int currentState = STATE_OFF;
    private static boolean jarsAdded = false;

    /**
     * Inform about system state.
     * @return an integer representing the current system state.
     * @see #STATE_OFF
     * @see #STATE_STARTING
     * @see #STATE_RUNNING
     * @see #STATE_SHUTTING_DOWN
     */
    public static int currentState() {
        return currentState;
    }

    /**
     * Add jars to classpath. Normally this is called from startup().
     * @throws Exception
     */
    protected static void addJarsToClasspath() throws Exception
    {
        if (jarsAdded) return; // have done this already
        File jarDir = new File(MaryProperties.maryBase()+"/java");
        File[] jarFiles = jarDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });
        assert jarFiles != null;
        URLClassLoader sysloader = (URLClassLoader)ClassLoader.getSystemClassLoader();
        Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
        method.setAccessible(true);
        for (int i=0; i<jarFiles.length; i++) {
            URL jarURL = new URL("file:"+jarFiles[i].getPath());
            method.invoke(sysloader, new Object[] {jarURL});
        }
        jarsAdded = true;
    }


    private static void startModules()
        throws ClassNotFoundException, InstantiationException, Exception {
        // TODO: add parameterisation here, to be able to provide configuration parameters to modules at startup time 
        for (String moduleClassName : MaryProperties.moduleInitInfo()) {
            MaryModule m = ModuleRegistry.instantiateModule(moduleClassName);
            // Partially fill module repository here; 
            // TODO: voice-specific entries will be added when each voice is loaded.
            ModuleRegistry.registerModule(m, m.getLocale(), null);
        }
        ModuleRegistry.setRegistrationComplete();
        // Separate loop for startup allows modules to cross-reference to each
        // other via Mary.getModule(Class) even if some have not yet been
        // started.
        for (MaryModule m : ModuleRegistry.getAllModules()) {
            // Only start the modules here if in server mode: 
            if (((MaryProperties.getProperty("server").compareTo("commandline")!=0) || m instanceof Synthesis) 
                    && m.getState() == MaryModule.MODULE_OFFLINE) {
                try {
                    m.startup();
                } catch (Throwable t) {
                    throw new Exception("Problem starting module "+ m.name(), t);
                }
                
            }
            if (MaryProperties.getAutoBoolean("modules.poweronselftest", false)) {
                m.powerOnSelfTest();
            }
        }
    }

    /**
     * Start the MARY system and all modules. This method must be called
     * once before any calls to {@link #process()} are possible.
     * @throws IllegalStateException if the system is not offline.
     * @throws Exception
     */
    public static void startup() throws Exception
    {
        if (currentState != STATE_OFF) throw new IllegalStateException("Cannot start system: it is not offline");
        currentState = STATE_STARTING;

        addJarsToClasspath();
        MaryProperties.readProperties();

        // Configure Logging:
        logger = Logger.getLogger("main");
        Logger.getRootLogger().setLevel(Level.toLevel(MaryProperties.needProperty("log.level")));
        PatternLayout layout = new PatternLayout("%d [%t] %-5p %-10c %m\n");
        if (MaryProperties.needAutoBoolean("log.tofile")) {
            String filename = MaryProperties.getFilename("log.filename", "mary.log");
            File logFile = new File(filename);
            if (logFile.exists()) logFile.delete();
            BasicConfigurator.configure(new FileAppender(layout, filename));
        } else {
            BasicConfigurator.configure(new WriterAppender(layout, System.err));
        }
        logger.info("Mary starting up...");
        logger.info("Specification version " + Version.specificationVersion());
        logger.info("Implementation version " + Version.implementationVersion());
        logger.info("Running on a Java " + System.getProperty("java.version")
                + " implementation by " + System.getProperty("java.vendor")
                + ", on a " + System.getProperty("os.name") + " platform ("
                + System.getProperty("os.arch") + ", " + System.getProperty("os.version")
                + ")");
        logger.debug("Full dump of system properties:");
        for (Object key : new TreeSet<Object>(System.getProperties().keySet())) {
            logger.debug(key + " = " + System.getProperties().get(key));
        }
        logger.debug("XML libraries used:");
        try {
            Class xercesVersion = Class.forName("org.apache.xerces.impl.Version");
            logger.debug(xercesVersion.getMethod("getVersion").invoke(null));
        } catch (Exception e) {
            logger.debug("XML parser is not Xerces: " + DocumentBuilderFactory.newInstance().getClass());
        }
        try {
            Class xalanVersion = Class.forName("org.apache.xalan.Version");
            logger.debug(xalanVersion.getMethod("getVersion").invoke(null));
        } catch (Exception e) {
            logger.debug("XML transformer is not Xalan: " + TransformerFactory.newInstance().getClass());
        }

        // Essential environment checks:
        EnvironmentChecks.check();

        // Instantiate module classes and startup modules:
        startModules();

        logger.info("Startup complete.");
        currentState = STATE_RUNNING;
    }

    /**
     * Orderly shut down the MARY system.
     * @throws IllegalStateException if the MARY system is not running.
     */
    public static void shutdown()
    {
        if (currentState != STATE_RUNNING) throw new IllegalStateException("MARY system is not running");
        currentState = STATE_SHUTTING_DOWN;
        logger.info("Shutting down modules...");
        // Shut down modules:
        for (MaryModule m : ModuleRegistry.getAllModules()) {
            if (m.getState() == MaryModule.MODULE_RUNNING)
                m.shutdown();
        }
        logger.info("Shutdown complete.");
        currentState = STATE_OFF;
    }
    
    /**
     * Process input into output using the MARY system. For inputType TEXT
     * and output type AUDIO, this does text-to-speech conversion; for other
     * settings, intermediate processing results can be generated or provided
     * as input.
     * @param input
     * @param inputTypeName
     * @param outputTypeName
     * @param localeString
     * @param audioType
     * @param voiceName
     * @param style
     * @param effects
     * @param output the output stream into which the processing result will be
     * written.
     * @throws IllegalStateException if the MARY system is not running.
     * @throws Exception
     */
    public static void process(String input, String inputTypeName, String outputTypeName,
            String localeString, String audioTypeName, String voiceName, 
            String style, String effects, OutputStream output)
    throws Exception
    {
        if (currentState != STATE_RUNNING) throw new IllegalStateException("MARY system is not running");
        
        MaryDataType inputType = MaryDataType.get(inputTypeName);
        MaryDataType outputType = MaryDataType.get(outputTypeName);
        Locale locale = MaryUtils.string2locale(localeString);
        Voice voice = null;
        if (voiceName != null)
            voice = Voice.getVoice(voiceName);
        AudioFileFormat audioFileFormat = null;
        AudioFileFormat.Type audioType = null;
        if (audioTypeName != null) {
            audioType = MaryAudioUtils.getAudioFileFormatType(audioTypeName);
            AudioFormat audioFormat = null;
            if (audioTypeName.equals("MP3")) {
                audioFormat = MaryAudioUtils.getMP3AudioFormat();
            } else if (audioTypeName.equals("Vorbis")) {
                audioFormat = MaryAudioUtils.getOggAudioFormat();
            } else if (voice != null) {
                audioFormat = voice.dbAudioFormat();
            } else {
                audioFormat = Voice.AF22050;
            }
            audioFileFormat = new AudioFileFormat(audioType, audioFormat, AudioSystem.NOT_SPECIFIED);
        }
        
        Request request = new Request(inputType, outputType, locale, voice, effects, style, 1, audioFileFormat);
        request.readInputData(new StringReader(input));
        request.process();
        request.writeOutputData(System.out);

        
        
    }
    

    /**
     * The starting point of the standalone Mary program.
     * If server mode is requested by property settings, starts
     * the <code>MaryServer</code>; otherwise, a <code>Request</code>
     * is created reading from the file given as first argument and writing
     * to System.out.
     *
     * <p>Usage:<p>
     * As a socket server:
     * <pre>
     * java -Dmary.base=$MARY_BASE -Dserver=true marytts.server.Mary
     * </pre><p>
     * As a stand-alone program:
     * <pre>
     * java -Dmary.base=$MARY_BASE marytts.server.Mary myfile.txt
     * </pre>
     * @see MaryProperties
     * @see MaryServer
     * @see RequestHandler
     * @see Request
     */
    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();

        addJarsToClasspath();
        MaryProperties.readProperties();

        String server = MaryProperties.needProperty("server");
        System.err.print("MARY server " + Version.specificationVersion() + " starting as a ");
        if (server.equals("socket")) System.err.print("socket server...");
        else if (server.equals("http")) System.err.print("HTTP server...");
        else System.err.print("command-line application...");
        startup();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                shutdown();
            }
        });
        System.err.println(" started in " + (System.currentTimeMillis()-startTime)/1000. + " s");
        
        if (server.equals("socket")) //socket server mode
            new MaryServer().run();
        else if (server.equals("http")) //http server mode
            new MaryHttpServer().run();
        else { // command-line mode
            InputStream inputStream;
            if (args.length == 0 || args[0].equals("-"))
                inputStream = System.in;
            else
                inputStream = new FileInputStream(args[0]);
            String input = FileUtils.getStreamAsString(inputStream, "UTF-8");
            process(input,
                    MaryProperties.getProperty("input.type", "TEXT"),
                    MaryProperties.getProperty("output.type", "AUDIO"),
                    MaryProperties.getProperty("locale", "en"),
                    MaryProperties.getProperty("audio.type", "WAVE"),
                    MaryProperties.getProperty("voice", null),
                    MaryProperties.getProperty("style", null),
                    MaryProperties.getProperty("effect", null),
                    System.out);
        }
        shutdown();
    }
}
