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
package de.dfki.lt.mary.modules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.jsresources.AppendableSequenceAudioInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.NodeIterator;

import de.dfki.lt.mary.MaryData;
import de.dfki.lt.mary.MaryDataType;
import de.dfki.lt.mary.MaryProperties;
import de.dfki.lt.mary.MaryXML;
import de.dfki.lt.mary.modules.synthesis.SynthesisException;
import de.dfki.lt.mary.modules.synthesis.Voice;
import de.dfki.lt.mary.modules.synthesis.WaveformSynthesizer;
import de.dfki.lt.mary.util.dom.MaryDomUtils;
import de.dfki.lt.mary.util.dom.NameNodeFilter;

/**
 * The synthesis module.
 *
 * @author Marc Schr&ouml;der
 */

public class Synthesis extends InternalModule
{
    private List waveformSynthesizers;
    
    public Synthesis()
    {
        super("Synthesis",
              MaryDataType.get("ACOUSTPARAMS"),
              MaryDataType.get("AUDIO")
              );
    }

    public void startup() throws Exception
    {
        startupSynthesizers();
        super.startup();
    }

    private void startupSynthesizers()
        throws ClassNotFoundException, InstantiationException, Exception
    {
       waveformSynthesizers = new ArrayList();
        for (Iterator it = MaryProperties.synthesizerClasses().iterator();
             it.hasNext(); ) {
            String synthClassName = (String)it.next();
            WaveformSynthesizer ws = (WaveformSynthesizer)
                Class.forName(synthClassName).newInstance();
            ws.startup();
            waveformSynthesizers.add(ws);
        }
    }
    
    /**
     * Perform a power-on self test by processing some example input data.
     * @throws Error if the module does not work properly.
     */
    public synchronized void powerOnSelfTest() throws Error
     {
            for (Iterator it = waveformSynthesizers.iterator(); it.hasNext(); ) {
                WaveformSynthesizer ws = (WaveformSynthesizer) it.next();
                ws.powerOnSelfTest();
            }
     }

    public MaryData process(MaryData d)
    throws Exception
    {
        // We produce audio data, so we expect some helpers in our input:
        assert d.getAudioFileFormat() != null;
        Document doc = d.getDocument();
        // As the input may contain multipe voice sections,
        // the challenge in this method is to join the audio data
        // resulting from individual synthesis calls with the respective
        // voice into one audio stream of the specified type.
        // Overall strategy:
        // * In input, identify the sections to be spoken by different voices
        // * For each of these sections,
        //   - synthesise the section in the voice's native audio format
        //   - convert to the common audio format if necessary / possible
        // * Join the audio input streams by appending each part to the output MaryData audio.
        // * Return a MaryData structure containing a single audio input stream
        //   from which the audio data in the desired format can be read.

        AudioFormat targetFormat = d.getAudioFileFormat().getFormat();
        Voice defaultVoice = d.getDefaultVoice();
        if (defaultVoice == null) {
            defaultVoice = Voice.getDefaultVoice(Locale.GERMAN);
            logger.info("No default voice associated with data. Assuming global default " +
                         defaultVoice.getName());
        }

        MaryData result = new MaryData(outputType());
        result.setAudioFileFormat(d.getAudioFileFormat());
        if (d.getAudio() != null) {
            // This (empty) AppendableSequenceAudioInputStream object allows a 
            // thread reading the audio data on the other "end" to get to our data as we are producing it.
            assert d.getAudio() instanceof AppendableSequenceAudioInputStream;
            result.setAudio(d.getAudio());
        }

        NodeIterator it = ((DocumentTraversal)doc).createNodeIterator
            (doc, NodeFilter.SHOW_ELEMENT,
             new NameNodeFilter(new String[]{MaryXML.TOKEN, MaryXML.BOUNDARY}),
             false);
        List elements = new ArrayList();
        Element element = null;
        Voice currentVoice = defaultVoice;
        Element currentVoiceElement = null;
        while ((element = (Element) it.nextNode()) != null) {
            Element v = (Element) MaryDomUtils.getAncestor(element, MaryXML.VOICE);
            if (v == null) {
                if (currentVoiceElement != null) {
                    // We have just left a voice section
                    if (!elements.isEmpty()) {
                        AudioInputStream ais = synthesizeOneSection
                            (elements, currentVoice, targetFormat);
                        if (ais != null) {
                            result.appendAudio(ais);
                        }
                        elements.clear();
                    }
                    currentVoice = defaultVoice;
                    currentVoiceElement = null;
                }
            } else if (v != currentVoiceElement) {
                // We have just entered a new voice section
                if (!elements.isEmpty()) {
                    AudioInputStream ais = synthesizeOneSection
                        (elements, currentVoice, targetFormat);
                    if (ais != null) {
                        result.appendAudio(ais);
                    }
                    elements.clear();
                }
                Voice newVoice = Voice.getVoice(v);
                if (newVoice != null) {
                    currentVoice = newVoice;
                }
                currentVoiceElement = v;
            }
            elements.add(element);
        }
        if (!elements.isEmpty()) {
            AudioInputStream ais =
                synthesizeOneSection(elements, currentVoice, targetFormat);
            if (ais != null) {
                result.appendAudio(ais);
            }
        }

        return result;
    }

    /**
     * Synthesize one section, consisting of tokens and boundaries, with a
     * given voice, to the given target audio format.
     */
    private AudioInputStream synthesizeOneSection
        (List tokensAndBoundaries, Voice voice, AudioFormat targetFormat)
    throws SynthesisException, UnsupportedAudioFileException
    {
        AudioInputStream ais = null;
        ais = voice.synthesize(tokensAndBoundaries);
        if (ais == null) return null;
        // Conversion to targetFormat required?
        if (!ais.getFormat().matches(targetFormat)) {
            // Attempt conversion; if not supported, log a warning
            // and provide the non-converted stream.
            logger.info("Audio format conversion required for voice " +
                         voice.getName());
            try {
                AudioInputStream intermedStream = AudioSystem.
                    getAudioInputStream(targetFormat, ais);
                ais = intermedStream;
            } catch (IllegalArgumentException iae) { // conversion not supported
                boolean solved = false;
                // try again with intermediate sample rate conversion
                if (!targetFormat.getEncoding().equals(ais.getFormat())
                        && targetFormat.getSampleRate() != ais.getFormat().getSampleRate()) {
                    AudioFormat sampleRateConvFormat = new AudioFormat(ais.getFormat().getEncoding(), targetFormat.getSampleRate(), ais.getFormat().getSampleSizeInBits(), ais.getFormat().getChannels(), ais.getFormat().getFrameSize(), ais.getFormat().getFrameRate(), ais.getFormat().isBigEndian());
                    try {
                        AudioInputStream intermedStream = AudioSystem.getAudioInputStream(sampleRateConvFormat, ais);
                        ais = AudioSystem.getAudioInputStream(targetFormat, intermedStream);
                        // No exception thrown, i.e. success
                        solved = true;
                    } catch (IllegalArgumentException iae1) {}
                }
                if (!solved)
                    throw new UnsupportedAudioFileException
                        ("Conversion from audio format " + ais.getFormat() +
                         " to requested audio format " + targetFormat +
                         " not supported.\n" + iae.getMessage());
            }
        }
        return ais;
    }

}
