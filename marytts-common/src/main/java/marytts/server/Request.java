/**
 * Copyright 2000-2006 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.Reader;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import marytts.datatypes.MaryData;
import marytts.datatypes.MaryDataType;
import marytts.datatypes.MaryXML;
import marytts.modules.MaryModule;
import marytts.modules.ModuleRegistry;
import marytts.modules.synthesis.Voice;
import marytts.util.MaryCache;
import marytts.util.MaryRuntimeUtils;
import marytts.util.MaryUtils;
import marytts.util.data.audio.AppendableSequenceAudioInputStream;
import marytts.util.dom.DomUtils;
import marytts.util.dom.MaryDomUtils;
import marytts.util.dom.NameNodeFilter;
import marytts.util.io.FileUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import marytts.io.XMLSerializer;
import marytts.data.Utterance;

/**
 * A request consists of input data, a desired output data type and the means to process the input data into the data of the
 * output type.<br>
 * <br>
 * A request is used as follows. First, its basic properties are set in the constructor, such as its input and output types.
 * Second, the input data is provided to the request either by directly setting it (<code>setInputData()</code>) or by reading it
 * from a Reader (<code>readInputData()</code>). Third, the request is processed (<code>process()</code>). Finally, the output
 * data is either accessed directly (<code>getOutputData()</code>) or written to an output stream (<code>writeOutputData</code>).
 */
public class Request {
	protected MaryDataType inputType;
	protected MaryDataType outputType;
    protected String configuration;
    protected String input_data;

	protected String outputTypeParams;
	protected AudioFileFormat audioFileFormat;
	protected AppendableSequenceAudioInputStream appendableAudioStream;
	protected Locale defaultLocale;
	protected Voice defaultVoice;
	protected String defaultStyle;
	protected String defaultEffects;

	protected int id;
	protected Logger logger;
	protected MaryData inputData;
	protected MaryData outputData;
	protected boolean streamAudio = false;
	protected boolean abortRequested = false;

	// Keep track of timing info for each module
	// (map MaryModule onto Long)
	protected Set<MaryModule> usedModules;
	protected Map<MaryModule, Long> timingInfo;

	public Request(MaryDataType inputType, MaryDataType outputType, String configuration, String input_data) {
		this.logger = MaryUtils.getLogger("R " + id);

		if (!inputType.isInputType())
			throw new IllegalArgumentException("not an input type: " + inputType.name());
		if (!outputType.isOutputType())
			throw new IllegalArgumentException("not an output type: " + outputType.name());
		this.inputType = inputType;
		this.outputType = outputType;

        this.configuration = configuration;
        this.input_data = input_data;

		usedModules = new LinkedHashSet<MaryModule>();
		timingInfo = new HashMap<MaryModule, Long>();
	}

    public void process() throws Exception {

        assert Mary.currentState() == Mary.STATE_RUNNING;


        // Parse configuration to get needed information
		List<MaryModule> neededModules = null;
        System.out.println("configuration = " + this.configuration);
        Locale cur_locale = Locale.US; // FIXME: hardcoded

        // Define the data
        MaryData input_mary_data = new MaryData(this.inputType, cur_locale);
        XMLSerializer xml_serializer = new XMLSerializer();
        input_mary_data.setData(xml_serializer.unpackDocument(this.input_data));

        if (neededModules == null) {
            neededModules = ModuleRegistry.modulesRequiredForProcessing(input_mary_data.getType(),
                                                                        outputType,
                                                                        cur_locale, null);

            // Now neededModules contains references to the needed modules,
            // in the order in which they are to process the data.
            if (neededModules == null) {
                // The modules we have cannot be combined such that
                // // TODO: he outputType can be generated from the inputData type.
                String message = "No known way of generating output (" + outputType.name() + ") from input("
					+ input_mary_data.getType().name() + "), no processing path through modules.";
                throw new UnsupportedOperationException(message);
            }
        }
        usedModules.addAll(neededModules);


        // Start to achieve the process
		long startTime = System.currentTimeMillis();

        //////////////////////////////////////////////////////////////////////////////////////////////////////
		logger.info("Handling request using the following modules:");
		for (MaryModule m : usedModules) {
			logger.info("- " + m.name() + " (" + m.getClass().getName() + ")");
		}
        outputData = input_mary_data;
		for (MaryModule m : usedModules)
        {
			if (abortRequested)
				break;

            if (m.getState() == MaryModule.MODULE_OFFLINE)
            {
				// This should happen only in command line mode:
				assert MaryProperties.needProperty("server").compareTo("commandline") == 0;
				logger.info("Starting module " + m.name());
				m.startup();
				assert m.getState() == MaryModule.MODULE_RUNNING;
			}
			long moduleStartTime = System.currentTimeMillis();

			logger.info("Next module: " + m.name());
			MaryData outData = null;
			try {
				outData = m.process(outputData);
			} catch (Exception e) {
				throw new Exception("Module " + m.name() + ": Problem processing the data.", e);
			}

			if (outData == null)
				throw new NullPointerException("Module " + m.name() + " returned null. This should not happen.");

			outputData = outData;

			long moduleStopTime = System.currentTimeMillis();
			long delta = moduleStopTime - moduleStartTime;
			Long soFar = timingInfo.get(m);
			if (soFar != null)
				timingInfo.put(m, new Long(soFar.longValue() + delta));
			else
				timingInfo.put(m, new Long(delta));

			if (MaryRuntimeUtils.veryLowMemoryCondition()) {
				logger.info("Very low memory condition detected (only " + MaryUtils.availableMemory()
						+ " bytes left). Triggering garbage collection.");
				Runtime.getRuntime().gc();
				logger.info("After garbage collection: " + MaryUtils.availableMemory() + " bytes available.");
			}
		}

		long stopTime = System.currentTimeMillis();
		logger.info("Request processed in " + (stopTime - startTime) + " ms.");
		for (MaryModule m : usedModules) {
			logger.info("   " + m.name() + " took " + timingInfo.get(m) + " ms");
		}
    }


	public Request(MaryDataType inputType, MaryDataType outputType, Locale defaultLocale, Voice defaultVoice,
			String defaultEffects, String defaultStyle, int id, AudioFileFormat audioFileFormat) {
		this(inputType, outputType, defaultLocale, defaultVoice, defaultEffects, defaultStyle, id, audioFileFormat, false, null);
	}

	public Request(MaryDataType inputType, MaryDataType outputType, Locale defaultLocale, Voice defaultVoice,
			String defaultEffects, String defaultStyle, int id, AudioFileFormat audioFileFormat, boolean streamAudio,
			String outputTypeParams) {
		if (!inputType.isInputType())
			throw new IllegalArgumentException("not an input type: " + inputType.name());
		if (!outputType.isOutputType())
			throw new IllegalArgumentException("not an output type: " + outputType.name());
		this.inputType = inputType;
		this.outputType = outputType;
		this.defaultLocale = defaultLocale;
		this.defaultVoice = defaultVoice;
		this.defaultEffects = defaultEffects;
		this.defaultStyle = defaultStyle;
		this.id = id;
		this.audioFileFormat = audioFileFormat;
		this.streamAudio = streamAudio;
		if (outputType == MaryDataType.get("AUDIO")) {
			if (audioFileFormat == null)
				throw new NullPointerException("audio file format is needed for output type AUDIO");
			this.appendableAudioStream = new AppendableSequenceAudioInputStream(audioFileFormat.getFormat(), null);
		} else {
			this.appendableAudioStream = null;
		}
		this.logger = MaryUtils.getLogger("R " + id);
		this.outputTypeParams = outputTypeParams;
		this.inputData = null;
		this.outputData = null;
		StringBuilder info = new StringBuilder("New request (input type \"" + inputType.name() + "\", output type \""
				+ outputType.name());
		if (this.defaultVoice != null)
			info.append("\", voice \"" + this.defaultVoice.getName());
		if (this.defaultEffects != null && this.defaultEffects != "")
			info.append("\", effect \"" + this.defaultEffects);
		if (this.defaultStyle != null && this.defaultStyle != "")
			info.append("\", style \"" + this.defaultStyle);
		if (audioFileFormat != null)
			info.append("\", audio \"" + audioFileFormat.getType().toString() + "\"");
		if (streamAudio)
			info.append(", streaming");
		info.append(")");
		logger.info(info.toString());

		// Keep track of timing info for each module
		// (map MaryModule onto Long)
		usedModules = new LinkedHashSet<MaryModule>();
		timingInfo = new HashMap<MaryModule, Long>();
	}

	public MaryDataType getInputType() {
		return inputType;
	}

	public MaryDataType getOutputType() {
		return outputType;
	}

	public int getId() {
		return id;
	}

	/**
	 * Inform this request that any further processing does not make sense.
	 */
	public void abort() {
		logger.info("Requesting abort.");
		abortRequested = true;
	}

	/**
	 * Set the input data directly, in case it is already in the form of a MaryData object.
	 *
	 * @param inputData
	 *            inputData
	 */
	public void setInputData(String input_data) {
		this.input_data = input_data;
	}

	/**
	 * Read the input data from a Reader.
	 *
	 * @param inputReader
	 *            inputReader
	 * @throws Exception
	 *             Exception
	 */
	public void readInputData(Reader inputReader) throws Exception {
		String inputText = FileUtils.getReaderAsString(inputReader);
		setInputData(inputText);
	}

	/**
	 * Direct access to the output data.
	 *
	 * @return outputdata
	 */
	public MaryData getOutputData() {
		return outputData;
	}

	/**
	 * Write the output data to the specified OutputStream.
	 *
	 * @param outputStream
	 *            outputStream
	 * @throws Exception
	 *             Exception
	 */
	public void writeOutputData(OutputStream outputStream) throws Exception {
		if (outputData == null) {
			throw new NullPointerException("No output data -- did process() succeed?");
		}
		if (outputStream == null)
			throw new NullPointerException("cannot write to null output stream");
		// Safety net: if the output is not written within a certain amount of
		// time, give up. This prevents our thread from being locked forever if an
		// output deadlock occurs (happened very rarely on Java 1.4.2beta).
		final OutputStream os = outputStream;
		Timer timer = new Timer();
		TimerTask timerTask = new TimerTask() {
			public void run() {
				logger.warn("Timeout occurred while writing output. Forcefully closing output stream.");
				try {
					os.close();
				} catch (IOException ioe) {
					logger.warn(ioe);
				}
			}
		};
		int timeout = MaryProperties.getInteger("modules.timeout", 10000);
		timer.schedule(timerTask, timeout);
		try {
            XMLSerializer xml_serializer = new XMLSerializer();
			os.write(xml_serializer.toString(this.outputData.getData()).getBytes());
		} catch (Exception e) {
			timer.cancel();
			throw e;
		}
		timer.cancel();
	}

}
