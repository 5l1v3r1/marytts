
/**   
*           The HMM-Based Speech Synthesis System (HTS)             
*                       HTS Working Group                           
*                                                                   
*                  Department of Computer Science                   
*                  Nagoya Institute of Technology                   
*                               and                                 
*   Interdisciplinary Graduate School of Science and Engineering    
*                  Tokyo Institute of Technology                    
*                                                                   
*                Portions Copyright (c) 2001-2006                       
*                       All Rights Reserved.
*                         
*              Portions Copyright 2000-2007 DFKI GmbH.
*                      All Rights Reserved.                  
*                                                                   
*  Permission is hereby granted, free of charge, to use and         
*  distribute this software and its documentation without           
*  restriction, including without limitation the rights to use,     
*  copy, modify, merge, publish, distribute, sublicense, and/or     
*  sell copies of this work, and to permit persons to whom this     
*  work is furnished to do so, subject to the following conditions: 
*                                                                   
*    1. The source code must retain the above copyright notice,     
*       this list of conditions and the following disclaimer.       
*                                                                   
*    2. Any modifications to the source code must be clearly        
*       marked as such.                                             
*                                                                   
*    3. Redistributions in binary form must reproduce the above     
*       copyright notice, this list of conditions and the           
*       following disclaimer in the documentation and/or other      
*       materials provided with the distribution.  Otherwise, one   
*       must contact the HTS working group.                         
*                                                                   
*  NAGOYA INSTITUTE OF TECHNOLOGY, TOKYO INSTITUTE OF TECHNOLOGY,   
*  HTS WORKING GROUP, AND THE CONTRIBUTORS TO THIS WORK DISCLAIM    
*  ALL WARRANTIES WITH REGARD TO THIS SOFTWARE, INCLUDING ALL       
*  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO EVENT   
*  SHALL NAGOYA INSTITUTE OF TECHNOLOGY, TOKYO INSTITUTE OF         
*  TECHNOLOGY, HTS WORKING GROUP, NOR THE CONTRIBUTORS BE LIABLE    
*  FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY        
*  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,  
*  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTUOUS   
*  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR          
*  PERFORMANCE OF THIS SOFTWARE.                                    
*                                                                   
*/

package marytts.modules;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.Vector;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;


import marytts.datatypes.MaryData;
import marytts.datatypes.MaryDataType;
import marytts.datatypes.MaryXML;
import marytts.exceptions.SynthesisException;
import marytts.features.FeatureDefinition;
import marytts.features.FeatureVector;
import marytts.htsengine.HMMData;
import marytts.htsengine.HMMVoice;
import marytts.htsengine.HTSModel;
import marytts.htsengine.HTSParameterGeneration;
import marytts.htsengine.CartTreeSet;
import marytts.htsengine.HTSUttModel;
import marytts.htsengine.HTSVocoder;
import marytts.htsengine.HTSEngineTest.PhonemeDuration;
import marytts.modules.synthesis.Voice;
import marytts.signalproc.analysis.PitchReaderWriter;
import marytts.unitselection.select.Target;
import marytts.util.data.audio.AppendableSequenceAudioInputStream;
import marytts.util.data.audio.AudioPlayer;
import marytts.util.dom.MaryDomUtils;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.NodeIterator;

import marytts.signalproc.analysis.*;


/**
 * HTSEngine: a compact HMM-based speech synthesis engine.
 * 
 * Java port and extension of HTS engine version 2.0
 * Extension: mixed excitation
 * @author Marc Schr&ouml;der, Marcela Charfuelan 
 */
public class HTSEngine extends InternalModule
{
    private Logger loggerHts = Logger.getLogger("HTSEngine");
    private String realisedDurations;  // HMM realised duration to be save in a file
    private boolean phoneAlignmentForDurations;
    private boolean stateAlignmentForDurations=false;   
    private Vector<PhonemeDuration> alignDur=null;  // list of external duration per phone for alignment
                                                    // this are durations loaded from a external file
    private double newStateDurationFactor = 0.5;   // this is a factor that extends or shrinks the duration of a state
                                                   // it can be used to try to syncronise the duration specified in a external file
                                                   // and the number of frames in a external lf0 file
    
    public String getRealisedDurations(){ return realisedDurations; }
    public boolean getPhonemeAlignmentForDurations(){ return phoneAlignmentForDurations; }
    public boolean getStateAlignmentForDurations(){ return stateAlignmentForDurations;}    
    public Vector<PhonemeDuration> getAlignDurations(){ return alignDur; }
    public double getNewStateDurationFactor(){ return newStateDurationFactor; }
  
    public void setRealisedDurations(String str){ realisedDurations=str; }
    public void setStateAlignmentForDurations(boolean bval){ stateAlignmentForDurations=bval; }
    public void setPhonemeAlignmentForDurations(boolean bval){ phoneAlignmentForDurations=bval; }    
    public void setAlignDurations(Vector<PhonemeDuration> val){ alignDur = val; }
    public void setNewStateDurationFactor(double dval){ newStateDurationFactor=dval; }
    
    
    
     
    public HTSEngine()
    {
        super("HTSEngine",
              MaryDataType.TARGETFEATURES,
              MaryDataType.AUDIO,
              null);
        phoneAlignmentForDurations=false;
        stateAlignmentForDurations=false;
        alignDur = null;       
    }

    /**
     * This module is actually tested as part of the HMMSynthesizer test,
     * for which reason this method does nothing.
     */
    public synchronized void powerOnSelfTest() throws Error
    {
    }
    
    
    /**
     * when calling this function HMMVoice must be initialised already.
     * that is TreeSet and ModelSet must be loaded already.
     * @param d
     * @return
     * @throws Exception
     */
    // THIS FUNCTION IS NOT USED ANY MORE
    public MaryData processKKKKK(MaryData d)
    throws Exception
    {
        /** The utterance model, um, is a Vector (or linked list) of Model objects. 
         * It will contain the list of models for current label file. */
        HTSUttModel um = new HTSUttModel();
        HTSParameterGeneration pdf2par = new HTSParameterGeneration();
        HTSVocoder par2speech = new HTSVocoder();
        AudioInputStream ais;
              
        Voice v = d.getDefaultVoice(); /* This is the way of getting a Voice through a MaryData type */
        assert v instanceof HMMVoice;
        HMMVoice hmmv = (HMMVoice)v;
        
        String context = d.getPlainText();
        //System.out.println("TARGETFEATURES:" + context);
              
        /* Process label file of Mary context features and creates UttModel um */
        processUtt(context, um, hmmv.getHMMData());

        /* Process UttModel */
        /* Generate sequence of speech parameter vectors, generate parameters out of sequence of pdf's */  
        boolean debug = false;  /* so it does not save the generated parameters. */
        pdf2par.htsMaximumLikelihoodParameterGeneration(um, hmmv.getHMMData(),"", debug);
    
        
        /* set parameters for generation: f0Std, f0Mean and length, default values 1.0, 0.0 and 0.0 */
        /* These values are fixed in HMMVoice */
        
        /* Process generated parameters */
        /* Synthesize speech waveform, generate speech out of sequence of parameters */
        ais = par2speech.htsMLSAVocoder(pdf2par, hmmv.getHMMData());
       
        MaryData output = new MaryData(outputType(), d.getLocale());
        if (d.getAudioFileFormat() != null) {
            output.setAudioFileFormat(d.getAudioFileFormat());
            if (d.getAudio() != null) {
               // This (empty) AppendableSequenceAudioInputStream object allows a 
               // thread reading the audio data on the other "end" to get to our data as we are producing it.
                assert d.getAudio() instanceof AppendableSequenceAudioInputStream;
                output.setAudio(d.getAudio());
            }
        }     
       output.appendAudio(ais);
       
       // no need to set actual durations in this function, because this function is called just for start up
              
       return output;
        
    }
    
    /**
     * This functions process directly the target features list and in the end set the actual durations and f0
     * in tokensAndBoundaries
     * @param d
     * @param targetFeaturesList
     * @param tokensAndBoundaries
     * @return
     * @throws Exception
     */
    public MaryData process(MaryData d, List<Target> targetFeaturesList, List<Element> segmentsAndBoundaries, List<Element> tokensAndBoundaries)
    throws Exception
    {
        /** The utterance model, um, is a Vector (or linked list) of Model objects. 
         * It will contain the list of models for current label file. */
        HTSUttModel um = new HTSUttModel();
        HTSParameterGeneration pdf2par = new HTSParameterGeneration();
        HTSVocoder par2speech = new HTSVocoder();
        AudioInputStream ais;
              
        Voice v = d.getDefaultVoice(); /* This is the way of getting a Voice through a MaryData type */
        assert v instanceof HMMVoice;
        HMMVoice hmmv = (HMMVoice)v;
        
        //String context = d.getPlainText();
        //System.out.println("TARGETFEATURES:" + context);
              
        /* Process label file of Mary context features and creates UttModel um */
        processTargetList(targetFeaturesList, segmentsAndBoundaries, um, hmmv.getHMMData());

        /* Process UttModel */
        /* Generate sequence of speech parameter vectors, generate parameters out of sequence of pdf's */  
        boolean debug = false;  /* so it does not save the generated parameters. */
        pdf2par.htsMaximumLikelihoodParameterGeneration(um, hmmv.getHMMData(),"", debug);
    
        
        /* set parameters for generation: f0Std, f0Mean and length, default values 1.0, 0.0 and 0.0 */
        /* These values are fixed in HMMVoice */
        
        /* Process generated parameters */
        /* Synthesize speech waveform, generate speech out of sequence of parameters */
        ais = par2speech.htsMLSAVocoder(pdf2par, hmmv.getHMMData());
       
        MaryData output = new MaryData(outputType(), d.getLocale());
        if (d.getAudioFileFormat() != null) {
            output.setAudioFileFormat(d.getAudioFileFormat());
            if (d.getAudio() != null) {
               // This (empty) AppendableSequenceAudioInputStream object allows a 
               // thread reading the audio data on the other "end" to get to our data as we are producing it.
                assert d.getAudio() instanceof AppendableSequenceAudioInputStream;
                output.setAudio(d.getAudio());
            }
        }     
       output.appendAudio(ais);
       
       /* include correct durations in MaryData output */
       //output.setPlainText(um.getRealisedAcoustParams());
              
       // maybe here we need to re-think how to set the actualDurations in segmentsAndBoundaries
       //setRealisedProsody(tokensAndBoundaries, um);
              
       return output;
        
    }
 
    public void setRealisedProsody(List<Element> tokensAndBoundaries, HTSUttModel um) 
    throws SynthesisException {
      int i,j, index;
      NodeList no1, no2;
      NamedNodeMap att;
      Scanner s = null;
      String line, str[];
      float totalDur = 0f; // total duration, in seconds 
      HTSModel m;
      
      int numModel=0;
      
      
      for (Element e : tokensAndBoundaries) {
       //System.out.println("TAG: " + e.getTagName());
       if( e.getTagName().equals(MaryXML.TOKEN) ) {
           NodeIterator nIt = MaryDomUtils.createNodeIterator(e, MaryXML.PHONE);
           Element phone;
           while ((phone = (Element) nIt.nextNode()) != null) {                        
               String p = phone.getAttribute("p");
               
               //index = ph.indexOf(p);
               //int currentDur = dur.elementAt(index);
               m = um.getUttModel(numModel++);
               int currentDur = m.getTotalDurMillisec();
               
               totalDur += currentDur * 0.001f;
               phone.setAttribute("d", String.valueOf(currentDur));
               phone.setAttribute("end", String.valueOf(totalDur));
               // remove this element of the vector otherwise next time it will return the same
               
           }
       } else if( e.getTagName().contentEquals(MaryXML.BOUNDARY) ) {
           int breakindex = 0;
           try {
               breakindex = Integer.parseInt(e.getAttribute("breakindex"));
           } catch (NumberFormatException nfe) {}
           if(e.hasAttribute("duration") || breakindex >= 3) {
             /*index = ph.indexOf("_");  
             int currentDur = dur.elementAt(index);
             totalDur += currentDur * 0.001f;
             e.setAttribute("duration", String.valueOf(currentDur));   
             // remove this element of the vector otherwise next time it will return the same
             ph.set(index, "");
             */
           }
       } // else ignore whatever other label...     
      }     
    }
    
    
    
    public void setActualDurations(List<Element> tokensAndBoundaries, String durations) 
    throws SynthesisException {
      int i,j, index;
      NodeList no1, no2;
      NamedNodeMap att;
      Scanner s = null;
      Vector<String> ph = new Vector<String>();
      Vector<Integer> dur = new Vector<Integer>(); // individual durations, in millis
      String line, str[];
      float totalDur = 0f; // total duration, in seconds 

      s = new Scanner(durations).useDelimiter("\n");
      while(s.hasNext()) {
        line = s.next();
        str = line.split(" ");
        //--- not needed ph.add(PhoneTranslator.replaceBackTrickyPhones(str[0]));
        ph.add(str[0]);
        dur.add(Integer.valueOf(str[1]));
      }
      /* the duration of the first phone includes the duration of the initial pause */
      if(dur.size() > 1 && ph.get(0).contentEquals("_")) {
        dur.set(1, (dur.get(1) + dur.get(0)) );
        ph.set(0, "");
        /* remove this element of the vector otherwise next time it will return the same */
        ph.set(0, "");
      }
    
      for (Element e : tokensAndBoundaries) {
       //System.out.println("TAG: " + e.getTagName());
       if( e.getTagName().equals(MaryXML.TOKEN) ) {
           NodeIterator nIt = MaryDomUtils.createNodeIterator(e, MaryXML.PHONE);
           Element phone;
           while ((phone = (Element) nIt.nextNode()) != null) {
               String p = phone.getAttribute("p");
               index = ph.indexOf(p);
               int currentDur = dur.elementAt(index);
               totalDur += currentDur * 0.001f;
               phone.setAttribute("d", String.valueOf(currentDur));
               phone.setAttribute("end", String.valueOf(totalDur));
               // remove this element of the vector otherwise next time it will return the same
               ph.set(index, "");
           }
       } else if( e.getTagName().contentEquals(MaryXML.BOUNDARY) ) {
           int breakindex = 0;
           try {
               breakindex = Integer.parseInt(e.getAttribute("breakindex"));
           } catch (NumberFormatException nfe) {}
           if(e.hasAttribute("duration") || breakindex >= 3) {
             index = ph.indexOf("_");  
             int currentDur = dur.elementAt(index);
             totalDur += currentDur * 0.001f;
             e.setAttribute("duration", String.valueOf(currentDur));   
             // remove this element of the vector otherwise next time it will return the same
             ph.set(index, "");
           }
       } // else ignore whatever other label...     
      }     
    }

   
    /* For stand alone testing. */
    public AudioInputStream processStr(String context, HMMData htsData)
    throws Exception
    {
        HTSUttModel um = new HTSUttModel();
        HTSParameterGeneration pdf2par = new HTSParameterGeneration();
        HTSVocoder par2speech = new HTSVocoder();
        AudioInputStream ais;
        
        /* htsData contains:
         * data in the configuration file, .pdf file names and other parameters. 
         * After InitHMMData it contains TreeSet ts and ModelSet ms 
         * ModelSet: Contains the .pdf's (means and variances) for dur, lf0, mcp, str and mag
         *           these are all the HMMs trained for a particular voice 
         * TreeSet: Contains the tree-xxx.inf, xxx: dur, lf0, mcp, str and mag 
         *          these are all the trees trained for a particular voice. */
        
        //loggerHts.info("TARGETFEATURES:" + context);
        
        /* Process label file of Mary context features and creates UttModel um */
        processUtt(context, um, htsData);

        /* Process UttModel */
        /* Generate sequence of speech parameter vectors, generate parameters out of sequence of pdf's */ 
        boolean debug = false;  /* so it does not save the generated parameters. */
        pdf2par.htsMaximumLikelihoodParameterGeneration(um, htsData, "", debug);
    
        /* Process generated parameters */
        /* Synthesize speech waveform, generate speech out of sequence of parameters */
        ais = par2speech.htsMLSAVocoder(pdf2par, htsData);
        
       return ais;
        
    }
  
 
    
    /** Reads the Label file, the file which contains the Mary context features,
     *  creates an scanner object and calls _ProcessUtt
     * @param LabFile
     */
    public void processUttFromFile(String LabFile, HTSUttModel um, HMMData htsData) throws Exception { 
        Scanner s = null;
        try {    
            /* parse text in label file */
            s = new Scanner(new BufferedReader(new FileReader(LabFile)));
            _processUtt(s,um,htsData,htsData.getCartTreeSet());
              
        } catch (FileNotFoundException e) {
            System.err.println("FileNotFoundException: " + e.getMessage());
            
        } finally {
            if (s != null)
                s.close();
        }           
    }
    
    /** Creates a scanner object with the Mary context features contained in Labtext
     * and calls _ProcessUtt
     * @param LabText
     */
    public void processUtt(String LabText, HTSUttModel um, HMMData htsData) throws Exception {
        Scanner s = null;
        try {
          s = new Scanner(LabText);
         _processUtt(s, um, htsData, htsData.getCartTreeSet());
        } finally {
            if (s != null)
              s.close();
        }   
    }
    

    
    /** Parse Mary context features. 
     * For each triphone model in the file, it creates a Model object in a linked list of 
     * Model objects -> UttModel um 
     * It also estimates state duration from state duration model (Gaussian).
     * For each model in the vector, the mean and variance of the DUR, LF0, MCP, STR and MAG 
     * are searched in the ModelSet and copied in each triphone model.   */
    private void _processUtt(Scanner s, HTSUttModel um, HMMData htsData, CartTreeSet cart)
      throws Exception {     
        int i, mstate,frame, k, statesDuration, newStateDuration;
        HTSModel m;                   /* current model, corresponds to a line in label file */
        String nextLine;
        double diffdurOld = 0.0;
        double diffdurNew = 0.0;
        double mean = 0.0;
        double var = 0.0;
        double durationsFraction;
        int alignDurSize=0;
        float fperiodmillisec = ((float)htsData.getFperiod() / (float)htsData.getRate()) * 1000;
        float fperiodsec = ((float)htsData.getFperiod() / (float)htsData.getRate());
        Integer dur;
        boolean firstPh = true; 
        boolean lastPh = false;
        realisedDurations = "#\n";
        Float durSec;
        Integer numLab=0;
        FeatureVector fv;
        FeatureDefinition feaDef = htsData.getFeatureDefinition();
        
        
       /* Skip mary context features definition */
        while (s.hasNext()) {
          nextLine = s.nextLine(); 
          if (nextLine.trim().equals("")) break;
        }
        /* skip until byte values */
        int numLines=0;
        while (s.hasNext()) {
          nextLine = s.nextLine();          
          if (nextLine.trim().equals("")) break;
          numLines++;
        }
         
        if(htsData.getUseDurationFromExternalFile() && alignDur != null){
          alignDurSize = alignDur.size();
          phoneAlignmentForDurations = true;
          loggerHts.info("Using external prosody for duration: using phone alignment for duration from external file.");
        } else if( htsData.getUseUnitDurationContinuousFeature() ){
          phoneAlignmentForDurations = true;
          loggerHts.info("Using external prosody for duration: using phone alignment for duration from ContinuousFeatureProcessors.");
        } else {
          phoneAlignmentForDurations = false;
          loggerHts.info("Estimating state durations from (Gaussian) state duration model.");
        }
            
        
        /* Parse byte values  */
        i=0;
        while (s.hasNext()) {
            nextLine = s.nextLine();
            //System.out.println("STR: " + nextLine);     
            
            fv = feaDef.toFeatureVector(0, nextLine);
            um.addUttModel(new HTSModel(cart.getNumStates()));            
            m = um.getUttModel(i);
            /* this function also sets the phone name, the phone between - and + */
            m.setName(fv.toString(), fv.getFeatureAsString(feaDef.getFeatureIndex("phone"), feaDef));
            
            /*
            System.out.println("context: " + fv.getFeatureAsString(feaDef.getFeatureIndex("prev_prev_phone"), feaDef) + 
                                     " " + fv.getFeatureAsString(feaDef.getFeatureIndex("prev_phone"), feaDef) +
                                     " " + fv.getFeatureAsString(feaDef.getFeatureIndex("phone"), feaDef) + 
                                     " " + fv.getFeatureAsString(feaDef.getFeatureIndex("next_phone"), feaDef) +
                                     " " + fv.getFeatureAsString(feaDef.getFeatureIndex("next_next_phone"), feaDef) +
                                     "  DUR= " + fv.getContinuousFeature(feaDef.getFeatureIndex("unit_duration")) +
                                     "  LF0= " + Math.exp(fv.getContinuousFeature(feaDef.getFeatureIndex("unit_logf0"))) );
            */
            
            if (htsData.getUseUnitDurationContinuousFeature()) {
                m.setUnit_duration(fv.getContinuousFeature(feaDef.getFeatureIndex("unit_duration"))); 
                float val = fv.getContinuousFeature(feaDef.getFeatureIndex("unit_logf0"));
                m.setUnit_logF0(fv.getContinuousFeature(feaDef.getFeatureIndex("unit_logf0")));
                m.setUnit_logF0delta(fv.getContinuousFeature(feaDef.getFeatureIndex("unit_logf0delta")));
            }
            
            if(!(s.hasNext()) )
              lastPh = true;

            // Determine state-level duration                      
            if( phoneAlignmentForDurations) {  // use phone alignment for duration
              // get the durations of the Gaussians any way, because we need to know how long each estate should be
              // knowing the duration of each state we can modified it so the 5 states reflect the external duration
              diffdurNew = cart.searchDurInCartTree(m, fv, htsData, firstPh, lastPh, diffdurOld);
              statesDuration=0;
              // get the sum of state durations
              for(k=0; k<htsData.getCartTreeSet().getNumStates(); k++)
                statesDuration += m.getDur(k);
              //System.out.println("sum states duration = " + statesDuration + "(" + fperiodsec*statesDuration + ")");
                
              // get the external duration
              if( htsData.getUseDurationFromExternalFile() && alignDur != null) { 
                // check if the external phone corresponds to the current  
                if( alignDur.get(i).getPhoneme().contentEquals(m.getPhoneName()) ){
                  if(i < alignDurSize ){
                    //System.out.print("  external duration=" + Math.round(alignDur.get(i).getDuration()/fperiodsec) + "(" + alignDur.get(i).getDuration() + ")");  
                    durationsFraction = alignDur.get(i).getDuration()/(fperiodsec*statesDuration);
                    //System.out.println("  dur_fraction = " + durationsFraction);  
                  }
                  else
                    throw new Exception("The number of durations provided for phone alignment (" + alignDurSize +
                        ") is less than the number of feature vectors, so far (" + um.getNumUttModel() + ").");
                } else {
                  throw new Exception("External phone: " + alignDur.get(i).getPhoneme() +
                         " does not correspond to current feature vector phone: " + m.getPhoneName() );
                }
              } else {  // if no alignDur use ContinuousFeatureProcessors unit_duration float
                 //System.out.print("  external duration=" + Math.round(fv.getContinuousFeature(feaDef.getFeatureIndex("unit_duration"))/fperiodsec) 
                 //        + "(" + fv.getContinuousFeature(feaDef.getFeatureIndex("unit_duration")) + ")"); 
                 durationsFraction = fv.getContinuousFeature(feaDef.getFeatureIndex("unit_duration"))/(fperiodsec*statesDuration);
                 //System.out.println("  dur_fraction = " + durationsFraction);  
              }
              
              m.setTotalDur(0);              
              for(k=0; k<htsData.getCartTreeSet().getNumStates(); k++){
                //System.out.print("   state: " + k + " durFromGaussians=" + m.getDur(k));
                newStateDuration = (int)(durationsFraction*m.getDur(k) + newStateDurationFactor);
                if( newStateDuration <= 0 )
                  newStateDuration = 1;
                m.setDur(k, newStateDuration);
                m.setTotalDur(m.getTotalDur() + m.getDur(k)); 
                //System.out.println(" durNew=" + m.getDur(k));       
              }  
              um.setTotalFrame(um.getTotalFrame() + m.getTotalDur());
              //System.out.println("   model TotalDur=" + m.getTotalDur() + "  TotalDur=" + um.getTotalFrame());
                
            } else if(stateAlignmentForDurations) {  // use state alignment for duration
              // Not implemented yet  
                
            } else { // Estimate state duration from state duration model (Gaussian)                 
                diffdurNew = cart.searchDurInCartTree(m, fv, htsData, firstPh, lastPh, diffdurOld);  
                um.setTotalFrame(um.getTotalFrame() + m.getTotalDur());             
            }
            
            // Set realised durations 
            m.setTotalDurMillisec((int)(fperiodmillisec * m.getTotalDur()));               
                      
            durSec = um.getTotalFrame() * fperiodsec;
            realisedDurations += durSec.toString() +  " " + numLab.toString() + " " + m.getPhoneName() + "\n";
            numLab++;
            dur = m.getTotalDurMillisec();
            um.concatRealisedAcoustParams(m.getPhoneName() + " " + dur.toString() + "\n");
            /*System.out.println(" phone=" + m.getPhoneName() + " dur=" + m.getTotalDur() + " ("  + (dur/1000.0) + ")  " 
                             + " durTotal=" + um.getTotalFrame() 
                             + " diffdurNew = " + diffdurNew + "  diffdurOld = " + diffdurOld);
            */
            diffdurOld = diffdurNew;  
            
            /* Find pdf for LF0, this function sets the pdf for each state. */ 
            cart.searchLf0InCartTree(m, fv, feaDef, htsData.getUV());
     
            /* Find pdf for MCP, this function sets the pdf for each state.  */
            cart.searchMcpInCartTree(m, fv, feaDef);

            /* Find pdf for strengths, this function sets the pdf for each state.  */
            if(htsData.getTreeStrFile() != null)
              cart.searchStrInCartTree(m, fv, feaDef);
            
            /* Find pdf for Fourier magnitudes, this function sets the pdf for each state.  */
            if(htsData.getTreeMagFile() != null)
              cart.searchMagInCartTree(m, fv, feaDef);
            
            /* increment number of models in utterance model */
            um.setNumModel(um.getNumModel()+1);
            /* update number of states */
            um.setNumState(um.getNumState() + cart.getNumStates());
            i++;
            
            if(firstPh)
              firstPh = false;
        }
        
        if(phoneAlignmentForDurations && alignDur != null)
          if( um.getNumUttModel() != alignDurSize )
              throw new Exception("The number of durations provided for phone alignment (" + alignDurSize +
                      ") is greater than the number of feature vectors (" + um.getNumUttModel() + ")."); 

        for(i=0; i<um.getNumUttModel(); i++){
            m = um.getUttModel(i);                  
            for(mstate=0; mstate<cart.getNumStates(); mstate++)
                for(frame=0; frame<m.getDur(mstate); frame++) 
                    if(m.getVoiced(mstate))
                        um.setLf0Frame(um.getLf0Frame() +1);
            //System.out.println("Vector m[" + i + "]=" + m.getPhoneName() ); 
        }

        loggerHts.info("Number of models in sentence numModel=" + um.getNumModel() + "  Total number of states numState=" + um.getNumState());
        loggerHts.info("Total number of frames=" + um.getTotalFrame() + "  Number of voiced frames=" + um.getLf0Frame());  
        
        //System.out.println("REALISED DURATIONS:" + realisedDurations);
        
    } /* method _ProcessUtt */

    
    private void processTargetList(List<Target> targetFeaturesList, List<Element> segmentsAndBoundaries, HTSUttModel um, HMMData htsData)
    throws Exception {          
      int i, mstate,frame, k, statesDuration, newStateDuration;
      HTSModel m;
      CartTreeSet cart = htsData.getCartTreeSet();
      
      double diffdurOld = 0.0;
      double diffdurNew = 0.0;
      double mean = 0.0;
      double var = 0.0;
      double durationsFraction;
      int alignDurSize=0;
      float fperiodmillisec = ((float)htsData.getFperiod() / (float)htsData.getRate()) * 1000;
      float fperiodsec = ((float)htsData.getFperiod() / (float)htsData.getRate());
      Integer dur;
      boolean firstPh = true; 
      boolean lastPh = false;
      float durVal = 0.0f; 
      realisedDurations = "#\n";
      Float durSec;
      Integer numLab=0;
      FeatureVector fv;
      FeatureDefinition feaDef = htsData.getFeatureDefinition();
       
      if(htsData.getUseDurationFromExternalFile() && alignDur != null){
        alignDurSize = alignDur.size();
        phoneAlignmentForDurations = true;
        loggerHts.info("Using external prosody for duration: using phone alignment for duration from external file.");
      } else if( htsData.getUseUnitDurationContinuousFeature() ){
        phoneAlignmentForDurations = true;
        loggerHts.info("Using external prosody for duration: using phone alignment for duration from ContinuousFeatureProcessors.");
      } else {
        phoneAlignmentForDurations = false;
        loggerHts.info("Estimating state durations from (Gaussian) state duration model.");
      }         
      
      /* Parse byte values  */
      i=0;
      for (Target target : targetFeaturesList) {
          
          Element e = segmentsAndBoundaries.get(i);
          
          fv = target.getFeatureVector();  //feaDef.toFeatureVector(0, nextLine);
          um.addUttModel(new HTSModel(cart.getNumStates()));            
          m = um.getUttModel(i);
          /* this function also sets the phone name, the phone between - and + */
          m.setName(fv.toString(), fv.getFeatureAsString(feaDef.getFeatureIndex("phone"), feaDef));
          
 
          // here i set dur and f0 from external acoustparams
          if (htsData.getUseUnitDurationContinuousFeature()) {
              String str = e.getAttribute("d");
              
              if(str.length() > 0){
                durVal = Float.parseFloat(str);  
                //System.out.println("dur=" + str + " milisec." + "  durFrames=" + (durVal/fperiodmillisec));                
              }
              else{
                  //System.out.println("dur=" + str + " milisec." + "  durFrames=0");
                  durVal=0;
              }
              m.setUnit_duration(durVal);
              //System.out.println("f0=" + e.getAttribute("f0")); 
              m.setUnit_logF0Array(e.getAttribute("f0"));
              
              // I am not using this continuous duration
              //m.setUnit_duration(fv.getContinuousFeature(feaDef.getFeatureIndex("unit_duration")));
              
              // CHECK: these times!!!!
              // CHECK: Why these values are not the same, the one from acoustparams and the one from continuous features???
              //m.setUnit_duration(fv.getContinuousFeature(feaDef.getFeatureIndex("unit_duration"))); 
              
              // I am not using any more these
              //float val = fv.getContinuousFeature(feaDef.getFeatureIndex("unit_logf0"));
              //m.setUnit_logF0(fv.getContinuousFeature(feaDef.getFeatureIndex("unit_logf0")));
              //m.setUnit_logF0delta(fv.getContinuousFeature(feaDef.getFeatureIndex("unit_logf0delta")));
          }
          

          // Determine state-level duration                      
          if( phoneAlignmentForDurations) {  // use phone alignment for duration
            // get the durations of the Gaussians any way, because we need to know how long each estate should be
            // knowing the duration of each state we can modified it so the 5 states reflect the external duration
            diffdurNew = cart.searchDurInCartTree(m, fv, htsData, firstPh, lastPh, diffdurOld);
            statesDuration=0;
            // get the sum of state durations
            for(k=0; k<htsData.getCartTreeSet().getNumStates(); k++)
              statesDuration += m.getDur(k);
            //System.out.println("sum hmm states duration = " + statesDuration + "(" + fperiodsec*statesDuration + ")");
              
            // get the external duration
            if( htsData.getUseDurationFromExternalFile() && alignDur != null) { 
              // check if the external phone corresponds to the current  
              if( alignDur.get(i).getPhoneme().contentEquals(m.getPhoneName()) ){
                if(i < alignDurSize ){
                  //System.out.print("  external duration=" + Math.round(alignDur.get(i).getDuration()/fperiodsec) + "(" + alignDur.get(i).getDuration() + ")");  
                  durationsFraction = alignDur.get(i).getDuration()/(fperiodsec*statesDuration);
                  //System.out.println("  dur_fraction = " + durationsFraction);  
                }
                else
                  throw new Exception("The number of durations provided for phone alignment (" + alignDurSize +
                      ") is less than the number of feature vectors, so far (" + um.getNumUttModel() + ").");
              } else {
                throw new Exception("External phone: " + alignDur.get(i).getPhoneme() +
                       " does not correspond to current feature vector phone: " + m.getPhoneName() );
              }
            } else {  // if no alignDur use ContinuousFeatureProcessors unit_duration float
               //System.out.print("  external duration=" + Math.round(fv.getContinuousFeature(feaDef.getFeatureIndex("unit_duration"))/fperiodsec) 
               //        + "(" + fv.getContinuousFeature(feaDef.getFeatureIndex("unit_duration")) + ")"); 
               // durationsFraction = fv.getContinuousFeature(feaDef.getFeatureIndex("unit_duration"))/(fperiodsec*statesDuration);
                durationsFraction = durVal/(fperiodmillisec*statesDuration);;
               //System.out.println("  dur_fraction = " + durationsFraction);  
            }
            
            m.setTotalDur(0);              
            for(k=0; k<htsData.getCartTreeSet().getNumStates(); k++){
              //System.out.print("   state: " + k + " durFromGaussians=" + m.getDur(k));
              newStateDuration = (int)(durationsFraction*m.getDur(k) + newStateDurationFactor);
              if( newStateDuration <= 0 )
                newStateDuration = 1;
              m.setDur(k, newStateDuration);
              m.setTotalDur(m.getTotalDur() + m.getDur(k)); 
              //System.out.println("   durNew=" + m.getDur(k));       
            }  
            um.setTotalFrame(um.getTotalFrame() + m.getTotalDur());
            //System.out.println("  model TotalDur=" + m.getTotalDur() + "  TotalDurMilisec=" + (fperiodmillisec * m.getTotalDur()));
              
          } else if(stateAlignmentForDurations) {  // use state alignment for duration
            // Not implemented yet  
              
          } else { // Estimate state duration from state duration model (Gaussian)                 
              diffdurNew = cart.searchDurInCartTree(m, fv, htsData, firstPh, lastPh, diffdurOld);  
              um.setTotalFrame(um.getTotalFrame() + m.getTotalDur());             
          }
          
          // Set realised durations 
          m.setTotalDurMillisec((int)(fperiodmillisec * m.getTotalDur()));               
                    
          durSec = um.getTotalFrame() * fperiodsec;
          realisedDurations += durSec.toString() +  " " + numLab.toString() + " " + m.getPhoneName() + "\n";
          numLab++;
          dur = m.getTotalDurMillisec();
          um.concatRealisedAcoustParams(m.getPhoneName() + " " + dur.toString() + "\n");
          /*System.out.println(" phone=" + m.getPhoneName() + " dur=" + m.getTotalDur() + " ("  + (dur/1000.0) + ")  " 
                           + " durTotal=" + um.getTotalFrame() 
                           + " diffdurNew = " + diffdurNew + "  diffdurOld = " + diffdurOld);
          */
          diffdurOld = diffdurNew;  
          
          /* Find pdf for LF0, this function sets the pdf for each state. 
           * here it is also set whether the model is voiced or not */ 
         // if ( ! htsData.getUseUnitDurationContinuousFeature() )
         // Here according to the HMM models it is decided whether the states of this model are voiced or unvoiced
            cart.searchLf0InCartTree(m, fv, feaDef, htsData.getUV());
         /* else {
            // determine whether this model is voiced or un-voiced according to the f0 values
            // this has to be done for the five states of the model
            boolean voicedValue;
            if( m.getUnit_f0Map().size() > 0 )
              voicedValue = true;
            else
              voicedValue = false;                 
            for(int s=0; s<cart.getNumStates(); s++)
              m.setVoiced(s, voicedValue);
          }*/
            
   
          /* Find pdf for MCP, this function sets the pdf for each state.  */
          cart.searchMcpInCartTree(m, fv, feaDef);

          /* Find pdf for strengths, this function sets the pdf for each state.  */
          if(htsData.getTreeStrFile() != null)
            cart.searchStrInCartTree(m, fv, feaDef);
          
          /* Find pdf for Fourier magnitudes, this function sets the pdf for each state.  */
          if(htsData.getTreeMagFile() != null)
            cart.searchMagInCartTree(m, fv, feaDef);
          
          /* increment number of models in utterance model */
          um.setNumModel(um.getNumModel()+1);
          /* update number of states */
          um.setNumState(um.getNumState() + cart.getNumStates());
          i++;
          
          if(firstPh)
            firstPh = false;
      }
      
      if(phoneAlignmentForDurations && alignDur != null)
        if( um.getNumUttModel() != alignDurSize )
            throw new Exception("The number of durations provided for phone alignment (" + alignDurSize +
                    ") is greater than the number of feature vectors (" + um.getNumUttModel() + ")."); 

      for(i=0; i<um.getNumUttModel(); i++){
          m = um.getUttModel(i);                  
          for(mstate=0; mstate<cart.getNumStates(); mstate++)
              for(frame=0; frame<m.getDur(mstate); frame++) 
                  if(m.getVoiced(mstate))
                      um.setLf0Frame(um.getLf0Frame() +1);
          //System.out.println("Vector m[" + i + "]=" + m.getPhoneName() ); 
      }

      loggerHts.info("Number of models in sentence numModel=" + um.getNumModel() + "  Total number of states numState=" + um.getNumState());
      loggerHts.info("Total number of frames=" + um.getTotalFrame() + "  Number of voiced frames=" + um.getLf0Frame());  
      
      //System.out.println("REALISED DURATIONS:" + realisedDurations);
      
  } /* method processTargetList */

    
    /** 
     * Stand alone testing using a TARGETFEATURES file as input. 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException, InterruptedException, Exception{
       
      int i, j; 
      /* configure log info */
      org.apache.log4j.BasicConfigurator.configure();

      /* To run the stand alone version of HTSEngine, it is necessary to pass a configuration
       * file. It can be one of the hmm configuration files in MARY_BASE/conf/*hmm*.config 
       * The input for creating a sound file is a TARGETFEATURES file in MARY format, there
       * is an example indicated in the configuration file as well.
       * For synthesising other text please generate first a TARGETFEATURES file with the MARY system
       * save it in a file and use it as feaFile. */
      HTSEngine hmm_tts = new HTSEngine();
      
      /* htsData contains:
       * Data in the configuration file, .pdf, tree-xxx.inf file names and other parameters. 
       * After initHMMData it containswhile(it.hasNext()){
            phon = it.next(); TreeSet ts and ModelSet ms 
       * ModelSet: Contains the .pdf's (means and variances) for dur, lf0, mcp, str and mag
       *           these are all the HMMs trained for a particular voice 
       * TreeSet: Contains the tree-xxx.inf, xxx: dur, lf0, mcp, str and mag 
       *          these are all the trees trained for a particular voice. */
      HMMData htsData = new HMMData();
            
      /* For initialise provide the name of the hmm voice and the name of its configuration file,*/
       
      String MaryBase    = "/project/mary/marcela/openmary/"; /* MARY_BASE directory.*/
      //String voiceName   = "roger-hsmm";                        /* voice name */
      //String voiceConfig = "en_GB-roger-hsmm.config";         /* voice configuration file name. */
      //String voiceName   = "dfki-poppy-hsmm";                        /* voice name */
      //String voiceConfig = "en_GB-dfki-poppy-hsmm.config";         /* voice configuration file name. */
      String voiceName   = "cmu-slt-hsmm";                        /* voice name */
      String voiceConfig = "en_US-cmu-slt-hsmm.config";         /* voice configuration file name. */
      //String voiceName   = "hsmm-ot";                        /* voice name */
      //String voiceConfig = "tr-hsmm-ot.config";         /* voice configuration file name. */
      String durFile     = MaryBase + "tmp/tmp.lab";          /* to save realised durations in .lab format */
      String parFile     = MaryBase + "tmp/tmp";              /* to save generated parameters tmp.mfc and tmp.f0 in Mary format */
      String outWavFile  = MaryBase + "tmp/tmp.wav";          /* to save generated audio file */
      
      // The settings for using GV and MixExc can be changed in this way:      
      htsData.initHMMData(voiceName, MaryBase, voiceConfig);
      htsData.setUseGV(true);
      htsData.setUseMixExc(true);
      htsData.setUseFourierMag(true);  // if the voice was trained with Fourier magnitudes
      
       
      /** The utterance model, um, is a Vector (or linked list) of Model objects. 
       * It will contain the list of models for current label file. */
      HTSUttModel um = new HTSUttModel();
      HTSParameterGeneration pdf2par = new HTSParameterGeneration();        
      HTSVocoder par2speech = new HTSVocoder();
      AudioInputStream ais;
               
      /** Example of context features file */
      String feaFile = htsData.getFeaFile();
      //String feaFile = "/project/mary/marcela/HMM-voices/poppy/phonefeatures/w0130.pfeats";
      // "Accept a father's blessing, and with it, this."
      //String feaFile = "/project/mary/marcela/HMM-voices/roger/phonefeatures/roger_5739.pfeats";
      // "It seems like a strange pointing of the hand of God."
      //String feaFile = "/project/mary/marcela/HMM-voices/roger/phonefeatures/roger_5740.pfeats";
      
      try {
          /* Process Mary context features file and creates UttModel um, a linked             
           * list of all the models in the utterance. For each model, it searches in each tree, dur,   
           * cmp, etc, the pdf index that corresponds to a triphone context feature and with           
           * that index retrieves from the ModelSet the mean and variance for each state of the HMM.   */
          hmm_tts.processUttFromFile(feaFile, um, htsData);
        
          /* save realised durations in a lab file */             
          FileWriter outputStream = new FileWriter(durFile);
          outputStream.write(hmm_tts.realisedDurations);
          outputStream.close();
          

          /* Generate sequence of speech parameter vectors, generate parameters out of sequence of pdf's */
          /* the generated parameters will be saved in tmp.mfc and tmp.f0, including Mary header. */
          boolean debug = true;  /* so it save the generated parameters in parFile */
          pdf2par.htsMaximumLikelihoodParameterGeneration(um, htsData, parFile, debug);
          
          /* Synthesize speech waveform, generate speech out of sequence of parameters */
          ais = par2speech.htsMLSAVocoder(pdf2par, htsData);
     
          System.out.println("Saving to file: " + outWavFile);
          System.out.println("Realised durations saved to file: " + durFile);
          File fileOut = new File(outWavFile);
          
          if (AudioSystem.isFileTypeSupported(AudioFileFormat.Type.WAVE,ais)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, fileOut);
          }

          System.out.println("Calling audioplayer:");
          AudioPlayer player = new AudioPlayer(fileOut);
          player.start();  
          player.join();
          System.out.println("Audioplayer finished...");
   
     
      } catch (Exception e) {
          System.err.println("Exception: " + e.getMessage());
      }
    }  /* main method */
    
    
  
}  /* class HTSEngine*/

