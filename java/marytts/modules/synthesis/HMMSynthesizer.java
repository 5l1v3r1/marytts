/* ----------------------------------------------------------------- */
/*           The HMM-Based Speech Synthesis Engine "hts_engine API"  */
/*           developed by HTS Working Group                          */
/*           http://hts-engine.sourceforge.net/                      */
/* ----------------------------------------------------------------- */
/*                                                                   */
/*  Copyright (c) 2001-2010  Nagoya Institute of Technology          */
/*                           Department of Computer Science          */
/*                                                                   */
/*                2001-2008  Tokyo Institute of Technology           */
/*                           Interdisciplinary Graduate School of    */
/*                           Science and Engineering                 */
/*                                                                   */
/* All rights reserved.                                              */
/*                                                                   */
/* Redistribution and use in source and binary forms, with or        */
/* without modification, are permitted provided that the following   */
/* conditions are met:                                               */
/*                                                                   */
/* - Redistributions of source code must retain the above copyright  */
/*   notice, this list of conditions and the following disclaimer.   */
/* - Redistributions in binary form must reproduce the above         */
/*   copyright notice, this list of conditions and the following     */
/*   disclaimer in the documentation and/or other materials provided */
/*   with the distribution.                                          */
/* - Neither the name of the HTS working group nor the names of its  */
/*   contributors may be used to endorse or promote products derived */
/*   from this software without specific prior written permission.   */
/*                                                                   */
/* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND            */
/* CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,       */
/* INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF          */
/* MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE          */
/* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS */
/* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,          */
/* EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED   */
/* TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,     */
/* DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON */
/* ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,   */
/* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY    */
/* OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE           */
/* POSSIBILITY OF SUCH DAMAGE.                                       */
/* ----------------------------------------------------------------- */
/**
 * Copyright 2011 DFKI GmbH.
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

package marytts.modules.synthesis;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import marytts.datatypes.MaryData;
import marytts.datatypes.MaryDataType;
import marytts.datatypes.MaryXML;
import marytts.exceptions.SynthesisException;
import marytts.features.FeatureRegistry;
import marytts.features.TargetFeatureComputer;
import marytts.htsengine.HMMVoice;
import marytts.modules.HTSEngine;
import marytts.modules.MaryModule;
import marytts.modules.ModuleRegistry;
import marytts.modules.TargetFeatureLister;
import marytts.modules.synthesis.Voice.Gender;
import marytts.server.MaryProperties;
import marytts.unitselection.select.Target;
import marytts.util.MaryUtils;
import marytts.util.dom.MaryDomUtils;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.NodeIterator;
import org.w3c.dom.traversal.TreeWalker;


/**
 * HTS-HMM synthesiser.
 *
 * Java port and extension of HTS engine version 2.0
 * Extension: mixed excitation
 * @author Marc Schr&ouml;der, Marcela Charfuelan 
 */
public class HMMSynthesizer implements WaveformSynthesizer {
    private TargetFeatureLister targetFeatureLister;
    private HTSEngine htsEngine;
    private Logger logger;
    //private TargetFeatureComputer comp;

    public HMMSynthesizer() {
    }

    public void startup() throws Exception {
        logger = MaryUtils.getLogger(this.toString());
        // Try to get instances of our tools from Mary; if we cannot get them,
        // instantiate new objects.

        try{
            targetFeatureLister = (TargetFeatureLister) ModuleRegistry.getModule(TargetFeatureLister.class);
        } catch (NullPointerException npe){
            targetFeatureLister = null;
        }
        if (targetFeatureLister == null) {
            logger.info("Starting my own TargetFeatureLister");
            targetFeatureLister = new TargetFeatureLister();
            targetFeatureLister.startup();
        } else if (targetFeatureLister.getState() == MaryModule.MODULE_OFFLINE) {
            targetFeatureLister.startup();
        }

        try{
            htsEngine = (HTSEngine) ModuleRegistry.getModule(HTSEngine.class);
        } catch (NullPointerException npe){
            htsEngine = null;
        }
        if (htsEngine == null) {
            logger.info("Starting my own HTSEngine");
            htsEngine = new HTSEngine();
            htsEngine.startup();
        } else if (htsEngine.getState() == MaryModule.MODULE_OFFLINE) {
            htsEngine.startup();
        }
        
        // Register HMM voices:
        String voiceNames = MaryProperties.getProperty("hmm.voices.list");
        if (voiceNames == null) {
            logger.debug("No HMM voices listed in config files.");
        } else {
            logger.debug("Register HMM voices:");
            for (StringTokenizer st = new StringTokenizer(voiceNames); st.hasMoreTokens(); ) {
                String voiceName = st.nextToken();
                logger.debug("Voice '" + voiceName + "'");
                Locale locale = MaryUtils.string2locale(MaryProperties.needProperty("voice."+voiceName+".locale"));
                int samplingRate = MaryProperties.getInteger("voice."+voiceName+".samplingRate", 16000);
                
                Gender gender = new Gender(MaryProperties.needProperty("voice."+voiceName+".gender"));
                AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        samplingRate, // samples per second
                        16, // bits per sample
                        1, // mono
                        2, // nr. of bytes per frame
                        samplingRate, // nr. of frames per second
                        false);
                
                
               /** When creating a HMMVoice object it should create and initialise a 
                * TreeSet ts, a ModelSet ms and load the context feature list used in this voice. */
                
                HMMVoice v = new HMMVoice (new String[] { voiceName },
                    locale, format, this, gender,
                    MaryProperties.getInteger("voice."+voiceName+".samplingRate"),
                    MaryProperties.getInteger("voice."+voiceName+".framePeriod"),
                    MaryProperties.getProperty("voice."+voiceName+".alpha"),
                    MaryProperties.getProperty("voice."+voiceName+".gamma"),
                    MaryProperties.getProperty("voice."+voiceName+".logGain"),
                    MaryProperties.getProperty("voice."+voiceName+".beta"),
                    MaryProperties.getFilename("voice."+voiceName+".Ftd"),     /* Tree DUR */
                    MaryProperties.getFilename("voice."+voiceName+".Ftf"),     /* Tree LF0 */
                    MaryProperties.getFilename("voice."+voiceName+".Ftm"),     /* Tree MCP */
                    MaryProperties.getFilename("voice."+voiceName+".Fts"),     /* Tree STR */
                    MaryProperties.getFilename("voice."+voiceName+".Fta"),     /* Tree MAG */
                    MaryProperties.getFilename("voice."+voiceName+".Fmd"),     /* Model DUR */
                    MaryProperties.getFilename("voice."+voiceName+".Fmf"),     /* Model LF0 */
                    MaryProperties.getFilename("voice."+voiceName+".Fmm"),     /* Model MCP */
                    MaryProperties.getFilename("voice."+voiceName+".Fms"),     /* Model STR */
                    MaryProperties.getFilename("voice."+voiceName+".Fma"),     /* Model MAG */        
                    MaryProperties.getBoolean("voice."+voiceName+".useAcousticModels"), /* use AcousticModeller, so prosody modification is enabled */                    
                    MaryProperties.getBoolean("voice."+voiceName+".useMixExc"),         /* Use Mixed excitation */
                    MaryProperties.getBoolean("voice."+voiceName+".useFourierMag"),     /* Use Fourier magnitudes for pulse generation*/
                    MaryProperties.getBoolean("voice."+voiceName+".useGV"),             /* Use Global Variance in parameter generation */
                    MaryProperties.getBoolean("voice."+voiceName+".useContextDependentGV"), /* Use context-dependent GV, (gv without sil)*/
                    MaryProperties.getProperty("voice."+voiceName+".gvMethod"),     /* GV method: gradient or derivative (default gradient)*/ 
                    MaryProperties.getInteger("voice."+voiceName+".maxMgcGvIter"),  /* Max number of iterations for MGC gv optimisation */
                    MaryProperties.getInteger("voice."+voiceName+".maxLf0GvIter"),  /* Max number of iterations for LF0 gv optimisation */
                    MaryProperties.getInteger("voice."+voiceName+".maxStrGvIter"),  /* Max number of iterations for STR gv optimisation */
                    MaryProperties.getProperty("voice."+voiceName+".gvWeightMgc"),  /* GV weight for mgc between 0.0-2.0 default 1.0*/
                    MaryProperties.getProperty("voice."+voiceName+".gvWeightLf0"),  /* GV weight for lf0 between 0.0-2.0 default 1.0*/
                    MaryProperties.getProperty("voice."+voiceName+".gvWeightStr"),  /* GV weight for str between 0.0-2.0 default 1.0*/
                    MaryProperties.getFilename("voice."+voiceName+".Fgvf"),     /* GV Model LF0 */
                    MaryProperties.getFilename("voice."+voiceName+".Fgvm"),     /* GV Model MCP */
                    MaryProperties.getFilename("voice."+voiceName+".Fgvs"),     /* GV Model STR */
                    MaryProperties.getFilename("voice."+voiceName+".Fgva"),     /* GV Model MAG */
                    MaryProperties.getFilename("voice."+voiceName+".FeaFile"),  /* targetfeatures file, for testing*/
                    MaryProperties.getFilename("voice."+voiceName+".trickyPhonesFile"),  /* tricky phones file, if any*/
                    MaryProperties.getFilename("voice."+voiceName+".Fif"),      /* Filter coefficients file for mixed excitation*/
                    MaryProperties.getInteger("voice."+voiceName+".in"));       /* Number of filters */                    
                Voice.registerVoice(v);
               
            }
        }
        logger.info("started.");
               
    }

    /**
      * Perform a power-on self test by processing some example input data.
      * @throws Error if the module does not work properly.
      */
     public synchronized void powerOnSelfTest() throws Error
     {

         logger.info("Starting power-on self test.");
         try {
             Collection<Voice> myVoices = Voice.getAvailableVoices(this);
             if (myVoices.size() == 0) {
                 return;
             }
             
             Voice v = (Voice) myVoices.iterator().next();
             MaryData in = new MaryData(MaryDataType.ACOUSTPARAMS, v.getLocale());
            
             String exampleText = MaryDataType.ACOUSTPARAMS.exampleText(v.getLocale());
             if (exampleText != null) {
                 in.readFrom(new StringReader(exampleText));
                 in.setDefaultVoice(v);
                 assert v instanceof HMMVoice : "Expected voice to be a HMMVoice, but it is a " + v.getClass().toString();
                 
                 //-- Here it is set the targetFeatureComputer for this voice                
                 String features = ((HMMVoice)v).getHMMData().getFeatureDefinition().getFeatureNames();
                 TargetFeatureComputer comp = FeatureRegistry.getTargetFeatureComputer(v, features);
                
                 in.setOutputParams(features);
                 Document doc = in.getDocument();
                 // First, get the list of segments and boundaries in the current document
                 TreeWalker tw = MaryDomUtils.createTreeWalker(doc, doc, MaryXML.PHONE, MaryXML.BOUNDARY);
                 List<Element> segmentsAndBoundaries = new ArrayList<Element>();
                 Element e;
                 while ((e = (Element) tw.nextNode()) != null) {
                     segmentsAndBoundaries.add(e);
                 }
                 
                 List<Target> targetFeaturesList = targetFeatureLister.getListTargetFeatures(comp, segmentsAndBoundaries);
                 
                 // The actual durations are already fixed in the htsEngine.process()
                 // here i pass segements and boundaries to update the realised acoustparams, dur and f0
                 MaryData audio = htsEngine.process(in, targetFeaturesList, segmentsAndBoundaries, null);     
                      
                 assert audio.getAudio() != null;           

             } else {
                 logger.debug("No example text -- no power-on self test!");
             }
         } catch (Throwable t) {
             throw new Error("Module " + toString() + ": Power-on self test failed.", t);
         }
         logger.info("Power-on self test complete.");
         

     }

    public String toString() {
        return "HMMSynthesizer";
    }

    /**
     * {@inheritDoc}
     */
    public AudioInputStream synthesize(List<Element> tokensAndBoundaries, Voice voice, String outputParams)
        throws SynthesisException {
        
        if (!voice.synthesizer().equals(this)) {
            throw new IllegalArgumentException(
                "Voice " + voice.getName() + " is not an HMM voice.");
        }
        logger.info("Synthesizing one sentence.");

        // from tokens and boundaries, extract segments and boundaries:
        List<Element> segmentsAndBoundaries = new ArrayList<Element>();
        Document doc = null;
        for (Element tOrB : tokensAndBoundaries) {
            if (tOrB.getTagName().equals(MaryXML.BOUNDARY)) {
                segmentsAndBoundaries.add(tOrB);
            } else { // a token -- add all segments below it
                if (doc == null) {
                    doc = tOrB.getOwnerDocument();
                }
                NodeIterator ni = MaryDomUtils.createNodeIterator(doc, tOrB, MaryXML.PHONE);
                Element s;
                while ((s = (Element) ni.nextNode()) != null) {
                    segmentsAndBoundaries.add(s);
                }
            }
        }
        try {
            assert voice instanceof HMMVoice : "Expected voice to be a HMMVoice, but it is a " + voice.getClass().toString();
        
            //-- This can be done just once when powerOnSelfTest() of this voice
            //-- mmmmmm it did not work, it takes the comp from the default voice
            //-- CHECK: do we need to do this for every call???
            String features = ((HMMVoice)voice).getHMMData().getFeatureDefinition().getFeatureNames();
            TargetFeatureComputer comp = FeatureRegistry.getTargetFeatureComputer(voice, features);
              
            // it is not faster to pass directly a list of targets?
            //--String targetFeatureString = targetFeatureLister.listTargetFeatures(comp, segmentsAndBoundaries);
            
            MaryData d = new MaryData(targetFeatureLister.outputType(), voice.getLocale());
            //--d.setPlainText(targetFeatureString);
            d.setDefaultVoice(voice);
            
            List<Target> targetFeaturesList = targetFeatureLister.getListTargetFeatures(comp, segmentsAndBoundaries);
            
            // the actual durations are already fixed in the htsEngine.process()
            // here i pass segements and boundaries to update the realised acoustparams, dur and f0
            MaryData audio = htsEngine.process(d, targetFeaturesList, segmentsAndBoundaries, tokensAndBoundaries);     
                 
            return audio.getAudio();           
                     
        } catch (Exception e) {
            throw new SynthesisException("HMM Synthesiser could not synthesise: ", e);
        }
    }
    
 
    

}
