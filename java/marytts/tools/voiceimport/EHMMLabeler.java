/**
 * Copyright 2007 DFKI GmbH.
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
package marytts.tools.voiceimport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import marytts.modules.phonemiser.Allophone;
import marytts.modules.phonemiser.AllophoneSet;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


/**
 * Automatic Labelling using EHMM labeller
 * @author Sathish Chandra Pammi
 */

public class EHMMLabeler extends VoiceImportComponent {
        
        private DatabaseLayout db;        
        private File rootDir;
        private File ehmm;
        private String voicename;
        private String outputDir;
        protected String featsExt = ".pfeats";
        protected String labExt = ".lab";
        protected String xmlExt = ".xml";
        
        private int progress = -1;
        private String locale;
        private AllophoneSet allophoneSet;
        
        public final String EDIR = "EHMMLabeler.eDir";
        public final String EHMMDIR = "EHMMLabeler.ehmmDir";
        public final String INTONISEDDIR = "EHMMLabeler.intonisedDir";
        public final String OUTLABDIR = "EHMMLabeler.outputLabDir";
        public final String INITEHMMDIR = "EHMMLabeler.startEHMMModelDir";
        public final String RETRAIN = "EHMMLabeler.reTrainFlag";
        public final String PHONEMEXML = "EHMMLabeler.phonemeXMLFile";
        
        public final String getName(){
            return "EHMMLabeler";
        }
        
        public SortedMap getDefaultProps(DatabaseLayout db){
           this.db = db;
           String phonemeXml;
           locale = db.getProp(db.LOCALE);
           if (props == null){
               props = new TreeMap();
               String ehmmdir = System.getProperty("EHMMDIR");
               if ( ehmmdir == null ) {
                   ehmmdir = "/project/mary/Festival/festvox/src/ehmm/";
               }
               props.put(EHMMDIR,ehmmdir);
               
               props.put(EDIR,db.getProp(db.ROOTDIR)
                            +"ehmm"
                            +System.getProperty("file.separator"));
               props.put(INTONISEDDIR, db.getProp(db.ROOTDIR)
                       +"intonisedXML"
                       +System.getProperty("file.separator"));
               props.put(OUTLABDIR, db.getProp(db.ROOTDIR)
                       +"lab"
                       +System.getProperty("file.separator"));
               props.put(INITEHMMDIR,"/");
               props.put(RETRAIN,"false");
               
               if(locale.startsWith("de")){
                   phonemeXml = db.getProp(db.MARYBASE)
                           +File.separator+"lib"+File.separator+"modules"
                           +File.separator+"de"+File.separator+"cap"+File.separator+"phoneme-list-de.xml";
               }
               else{
                   phonemeXml = db.getProp(db.MARYBASE)
                           +File.separator+"lib"+File.separator+"modules"
                           +File.separator+"en"+File.separator+"cap"+File.separator+"phoneme-list-en.xml";
               }
               props.put(PHONEMEXML, phonemeXml);
               
           }
           return props;
       }
       
       protected void setupHelp(){
           props2Help = new TreeMap();
           props2Help.put(EHMMDIR,"directory containing the local installation of EHMM Labeller"); 
           props2Help.put(EDIR,"directory containing all files used for training and labeling. Will be created if it does not exist.");
           props2Help.put(INTONISEDDIR, "directory containing the IntonisedXML files.");
           props2Help.put(OUTLABDIR, "Directory to store generated lebels from EHMM.");
           props2Help.put(INITEHMMDIR,"If you provide a path to previous EHMM Directory, Models will intialize with those models. other wise EHMM Models will build with Flat-Start Initialization");
           props2Help.put(RETRAIN,"true - Do re-training by initializing with given models. false - Do just Decoding");
           props2Help.put(PHONEMEXML, "Phoneme XML file for given language.");
       }
        
        public void initialiseComp()
        {
           locale = db.getProp(db.LOCALE);
           
        }
        
        /**
         * Do the computations required by this component.
         * 
         * @return true on success, false on failure
         */
        public boolean compute() throws Exception{
            
            File ehmmFile = new File(getProp(EHMMDIR)+"/bin/ehmm");
            if (!ehmmFile.exists()) {
                throw new IOException("EHMM path setting is wrong. Because file "+ehmmFile.getAbsolutePath()+" does not exist");
            }
            allophoneSet = AllophoneSet.getAllophoneSet(getProp(PHONEMEXML));
            
            System.out.println("Preparing voice database for labelling using EHMM :");
            System.out.println("See $ROOTDIR/ehmm/log.txt for EHMM Labelling status... ");
            //get the voicename        
            voicename = db.getProp(db.VOICENAME);
            //make new directories ehmm and etc
            ehmm = new File(getProp(EDIR));
            // get the output directory of files used by EHMM 
            outputDir = ehmm.getAbsolutePath()+"/etc";
            
            // setup the EHMM directory 
           
            System.out.println("Setting up EHMM directory ...");
            setup();
            System.out.println(" ... done.");
            
            //Getting Phone Sequence for Force Alignment    
            System.out.println("Getting Phone Sequence from Phone Features...");
            getPhoneSequence();
            System.out.println(" ... done.");
           
            System.out.println("See $ROOTDIR/ehmm/log.txt for EHMM Labelling status... ");
            // dump the filenames 
            System.out.println("Dumping required files ....");
            dumpRequiredFiles();
            System.out.println(" ... done.");           
            
            System.out.println("See $ROOTDIR/ehmm/log.txt for EHMM Labelling status... ");
            // Computing Features (MFCCs) for EHMM 
            System.out.println("Computing MFCCs ...");
            computeFeatures();
            System.out.println(" ... done.");
            
            System.out.println("See $ROOTDIR/ehmm/log.txt for EHMM Labelling status... ");
            System.out.println("Scaling Feature Vectors ...");
            scaleFeatures();
            System.out.println(" ... done.");
            
            System.out.println("See $ROOTDIR/ehmm/log.txt for EHMM Labelling status... ");
            System.out.println("Intializing EHMM Model ...");
            intializeEHMMModels();
            System.out.println(" ... done.");
            
            baumWelchEHMM();            
            
            System.out.println("See $ROOTDIR/ehmm/log.txt for EHMM Labelling status... ");
            System.out.println("Aligning EHMM for labelling ...");
            alignEHMM();
            
//            System.out.println("And Copying label files into lab directory ...");
//            getProperLabelFormat();
//            System.out.println(" ... done.");
            
            System.out.println("Label file Generation Successfully completed using EHMM !"); 
            
            
            return true;
        }
        
        
       /**
        * Setup the EHMM directory
        * @throws IOException, InterruptedException
        */
        private void setup() throws IOException,InterruptedException{
            
            ehmm.mkdir();
            File lab = new File(ehmm.getAbsolutePath()+"/lab");
            //call setup of EHMM in this directory
            Runtime rtime = Runtime.getRuntime();
            //get a shell
            Process process = rtime.exec("/bin/bash");
            //get an output stream to write to the shell
            PrintWriter pw = new PrintWriter(
                    new OutputStreamWriter(process.getOutputStream()));
            //go to ehmm directory and setup Directory Structure 
            pw.print("( cd "+ehmm.getAbsolutePath()
                    +"; mkdir feat"
                    +"; mkdir etc"
                    +"; mkdir mod"
                    +"; mkdir lab"
                    +"; exit )\n");
            pw.flush();
            //shut down
            pw.close();
            process.waitFor();
            process.exitValue();
            PrintWriter settings = new PrintWriter(
                    new FileOutputStream (new File(outputDir+"/"+"ehmm"+".featSettings")));
            
            
            // Feature Settings required for EHMM Training
            settings.println("WaveDir: "+db.getProp(db.ROOTDIR)+"/"+db.getProp(db.WAVDIR)+" \n"
                    +"HeaderBytes: 44 \n"
                    +"SamplingFreq: 16000 \n"
                    +"FrameSize: 160 \n"
                    +"FrameShift: 80 \n"
                    +"Lporder: 12 \n"
                    +"CepsNum: 16 \n"
                    +"FeatDir: "+getProp(EDIR)+"/feat \n"
                    +"Ext: .wav \n");
            settings.flush();
            settings.close();
            }
       
        /**
         * Creating Required files for EHMM Training
         * @throws IOException, InterruptedException
         */
        private void dumpRequiredFiles()throws IOException,InterruptedException{
            
            Runtime rtime = Runtime.getRuntime();
            //get a shell
            Process process = rtime.exec("/bin/bash");
            //get an output stream to write to the shell
            PrintWriter pw = new PrintWriter(
                    new OutputStreamWriter(process.getOutputStream()));
            //go to ehmm directory and create required files for EHMM 
            System.out.println("( cd "+ehmm.getAbsolutePath()
                    +"; perl "+getProp(EHMMDIR)+"/bin/phfromutt.pl "
                    +outputDir+"/"+"ehmm"+".align "
                    +outputDir+"/"+"ehmm"+".phoneList 5"
                    +"; perl "+getProp(EHMMDIR)+"/bin/getwavlist.pl "
                    +outputDir+"/"+"ehmm"+".align "
                    +outputDir+"/"+"ehmm"+".waveList"
                    +"; exit )\n");
            
            pw.print("( cd "+ehmm.getAbsolutePath()
                    +"; perl "+getProp(EHMMDIR)+"/bin/phfromutt.pl "
                    +outputDir+"/"+"ehmm"+".align "
                    +outputDir+"/"+"ehmm"+".phoneList 5 > log.txt"
                    +"; perl "+getProp(EHMMDIR)+"/bin/getwavlist.pl "
                    +outputDir+"/"+"ehmm"+".align "
                    +outputDir+"/"+"ehmm"+".waveList >> log.txt"
                    +"; exit )\n");
            
            pw.flush();
            //shut down
            pw.close();
            process.waitFor();
            process.exitValue();
                  
        }
        
        /**
         * Computing Features Required files for EHMM Training
         * @throws IOException, InterruptedException
         */
        private void computeFeatures()throws IOException,InterruptedException{
  
            Runtime rtime = Runtime.getRuntime();
            //get a shell
            Process process = rtime.exec("/bin/bash");
            //get an output stream to write to the shell
            PrintWriter pw = new PrintWriter(
                    new OutputStreamWriter(process.getOutputStream()));
            System.out.println("( cd "+ehmm.getAbsolutePath()
                    +"; "+getProp(EHMMDIR)+"/bin/FeatureExtraction "
                    +outputDir+"/"+"ehmm"+".featSettings "
                    +outputDir+"/"+"ehmm"+".waveList >> log.txt"  
                    +"; perl "+getProp(EHMMDIR)+"/bin/comp_dcep.pl "
                    +outputDir+"/"+"ehmm"+".waveList "
                    +ehmm.getAbsoluteFile()+"/feat mfcc ft 0 0 >> log.txt "
                  +"; exit )\n");
            pw.print("( cd "+ehmm.getAbsolutePath()
                    +"; "+getProp(EHMMDIR)+"/bin/FeatureExtraction "
                    +outputDir+"/"+"ehmm"+".featSettings "
                    +outputDir+"/"+"ehmm"+".waveList >> log.txt"  
                    +"; perl "+getProp(EHMMDIR)+"/bin/comp_dcep.pl "
                    +outputDir+"/"+"ehmm"+".waveList "
                    +ehmm.getAbsoluteFile()+"/feat mfcc ft 0 0 >> log.txt"
                    +"; exit )\n");
            pw.flush();
            //shut down
            pw.close();
            process.waitFor();
            process.exitValue(); 
        
        }
        
        /**
         * Scaling Features for EHMM Training
         * @throws IOException, InterruptedException
         */
        private void scaleFeatures()throws IOException,InterruptedException{
            
            Runtime rtime = Runtime.getRuntime();
            //get a shell
            Process process = rtime.exec("/bin/bash");
            //get an output stream to write to the shell
            PrintWriter pw = new PrintWriter(
                    new OutputStreamWriter(process.getOutputStream()));
            System.out.println("( cd "+ehmm.getAbsolutePath()
                    +"; perl "+getProp(EHMMDIR)+"/bin/scale_feat.pl "
                    +outputDir+"/"+"ehmm"+".waveList "
                    +ehmm.getAbsoluteFile()+"/feat "+ehmm.getAbsolutePath()+"/mod ft 4 >> log.txt"
                    +"; exit )\n");
            pw.print("( cd "+ehmm.getAbsolutePath()
                    +"; perl "+getProp(EHMMDIR)+"/bin/scale_feat.pl "
                    +outputDir+"/"+"ehmm"+".waveList "
                    +ehmm.getAbsoluteFile()+"/feat "+ehmm.getAbsolutePath()+"/mod ft 4 >> log.txt"
                    +"; exit )\n");
            pw.flush();
            //shut down
            pw.close();
            process.waitFor();
            process.exitValue();
            
        }
        
        /**
         * Initializing EHMM Models
         * @throws IOException, InterruptedException
         */
     private void intializeEHMMModels()throws IOException,InterruptedException{

         Runtime rtime = Runtime.getRuntime();
         //get a shell
         Process process = rtime.exec("/bin/bash");
         //get an output stream to write to the shell
         PrintWriter pw = new PrintWriter(
                 new OutputStreamWriter(process.getOutputStream()));
         
         
         if(getProp(INITEHMMDIR).equals("/")){
         
             
             pw.print("( cd "+ehmm.getAbsolutePath()
                 +"; perl "+getProp(EHMMDIR)+"/bin/seqproc.pl "
                 +outputDir+"/"+"ehmm"+".align "
                 +outputDir+"/"+"ehmm"+".phoneList 2 2 13 >> log.txt"
                 +"; exit )\n");
         }
         else{
             
            
             
             File modelFile = new File(getProp(INITEHMMDIR)+"/mod/model101.txt");
             if (!modelFile.exists()) {
                 throw new IOException("Model file "+modelFile.getAbsolutePath()+" does not exist");
             }
             
             pw.print("( cd "+ehmm.getAbsolutePath()
                     +"; "+"cp "+getProp(INITEHMMDIR)+"/etc/ehmm.phoneList "
                     +outputDir
                     +"; "+"cp "+getProp(INITEHMMDIR)+"/mod/model101.txt "
                     +getProp(EDIR)+"/mod/ "
                     +"; perl "+getProp(EHMMDIR)+"/bin/seqproc.pl "
                     +outputDir+"/"+"ehmm"+".align "
                     +outputDir+"/"+"ehmm"+".phoneList 2 2 13 >> log.txt"
                     +"; exit )\n");
             
         }
         
         pw.flush();
         //shut down
         pw.close();
         process.waitFor();
         process.exitValue();
            
        }
       
     
     /**
      * Training EHMM Models
      * @throws IOException, InterruptedException
      */
     private void baumWelchEHMM() throws IOException,InterruptedException{
    
         Runtime rtime = Runtime.getRuntime();
         //get a shell
         Process process = rtime.exec("/bin/bash");
         //get an output stream to write to the shell
         PrintWriter pw = new PrintWriter(
                 new OutputStreamWriter(process.getOutputStream()));
         
         if(getProp(INITEHMMDIR).equals("/")){
             
         System.out.println("See $ROOTDIR/ehmm/log.txt for EHMM Labelling status... ");
         System.out.println("EHMM baum-welch re-estimation ...");
         System.out.println("It may take more time (may be 1 or 2 days) depending on voice database ...");
             
         System.out.println("( cd "+ehmm.getAbsolutePath()
                 +"; "+getProp(EHMMDIR)+"/bin/ehmm "
                 +outputDir+"/"+"ehmm"+".phoneList.int "
                 +outputDir+"/"+"ehmm"+".align.int 1 0 "
                 +ehmm.getAbsolutePath()+"/feat ft"
                 +ehmm.getAbsolutePath()+"/mod 0 0 0 >> log.txt"
                 +"; exit )\n");
         
         pw.print("( cd "+ehmm.getAbsolutePath()
                 +"; "+getProp(EHMMDIR)+"/bin/ehmm "
                 +outputDir+"/"+"ehmm"+".phoneList.int "
                 +outputDir+"/"+"ehmm"+".align.int 1 0 "
                 +ehmm.getAbsolutePath()+"/feat ft "
                 +ehmm.getAbsolutePath()+"/mod 0 0 0 >> log.txt"
                 +"; exit )\n");
         
         }
         else if(getProp(RETRAIN).equals("true")){
             
             System.out.println("See $ROOTDIR/ehmm/log.txt for EHMM Labelling status... ");
             System.out.println("EHMM baum-welch re-estimation ... Re-Training... ");
             System.out.println("It may take more time (may be 1 or 2 days) depending on voice database ...");
               
             System.out.println("( cd "+ehmm.getAbsolutePath()
                     +"; "+getProp(EHMMDIR)+"/bin/ehmm "
                     +outputDir+"/"+"ehmm"+".phoneList.int "
                     +outputDir+"/"+"ehmm"+".align.int 1 1 "
                     +ehmm.getAbsolutePath()+"/feat ft"
                     +ehmm.getAbsolutePath()+"/mod 0 0 0 >> log.txt"
                     +"; exit )\n");
             
             pw.print("( cd "+ehmm.getAbsolutePath()
                     +"; "+getProp(EHMMDIR)+"/bin/ehmm "
                     +outputDir+"/"+"ehmm"+".phoneList.int "
                     +outputDir+"/"+"ehmm"+".align.int 1 1 "
                     +ehmm.getAbsolutePath()+"/feat ft "
                     +ehmm.getAbsolutePath()+"/mod 0 0 0 >> log.txt"
                     +"; exit )\n");  
             
             
         }
         
         pw.flush();
         //shut down
         pw.close();
         process.waitFor();
         process.exitValue();    
         System.out.println(".... Done.");
         
            
     }
     
     /**
      * Aligning EHMM and Label file generation 
      * @throws IOException, InterruptedException
      */
     private void alignEHMM() throws IOException,InterruptedException{
  
         Runtime rtime = Runtime.getRuntime();
         //get a shell
         Process process = rtime.exec("/bin/bash");
         //get an output stream to write to the shell
         PrintWriter pw = new PrintWriter(
                 new OutputStreamWriter(process.getOutputStream()));
         
         System.out.println("( cd "+ehmm.getAbsolutePath()
                 +"; "+getProp(EHMMDIR)+"/bin/edec "
                 +outputDir+"/"+"ehmm"+".phoneList.int "
                 +outputDir+"/"+"ehmm"+".align.int 1 "
                 +ehmm.getAbsolutePath()+"/feat ft "
                 +outputDir+"/"+"ehmm"+".featSettings "
                 +ehmm.getAbsolutePath()+"/mod >> log.txt"
                 +"; perl "+getProp(EHMMDIR)+"/bin/sym2nm.pl "
                 +ehmm.getAbsolutePath()+"/lab "
                 +outputDir+"/"+"ehmm"+".phoneList.int >> log.txt"
                 +"; exit )\n");
         
         pw.print("( cd "+ehmm.getAbsolutePath()
                 +"; "+getProp(EHMMDIR)+"/bin/edec "
                 +outputDir+"/"+"ehmm"+".phoneList.int "
                 +outputDir+"/"+"ehmm"+".align.int 1 "
                 +ehmm.getAbsolutePath()+"/feat ft "
                 +outputDir+"/"+"ehmm"+".featSettings "
                 +ehmm.getAbsolutePath()+"/mod >> log.txt"
                 +"; perl "+getProp(EHMMDIR)+"/bin/sym2nm.pl "
                 +ehmm.getAbsolutePath()+"/lab "
                 +outputDir+"/"+"ehmm"+".phoneList.int >> log.txt"
                 +"; exit )\n");
         
         pw.flush();
         //shut down
         pw.close();
         process.waitFor();
         process.exitValue();     
         
     }
     
        /**
         * Create phone sequence file, which is 
         * used for Alignment
         * @throws Exception
         */
     
        private void getPhoneSequence() throws Exception {
                        
            
            // open transcription file used for labeling
            PrintWriter transLabelOut = new PrintWriter(
                    new FileOutputStream (new File(outputDir+"/"+"ehmm"+".align")));
            
            String phoneSeq; 
            
            for (int i=0; i<bnl.getLength(); i++) {
                           
                //phoneSeq = getSingleLine(bnl.getName(i));
                phoneSeq = getLineFromXML(bnl.getName(i));
                transLabelOut.println(phoneSeq.trim());

                //System.out.println( "    " + bnl.getName(i) );
                           
            }
            transLabelOut.flush();
            transLabelOut.close();
            
        }
        
        
        /**
         * Get phoneme sequence from a single feature file
         * @param basename
         * @return String
         * @throws Exception
         */
        private String getLineFromXML(String basename) throws Exception {
            
            String line;
            String phoneSeq;
            Matcher matcher;
            Pattern pattern;
            StringBuffer alignBuff = new StringBuffer();
            alignBuff.append(basename);
            DocumentBuilderFactory factory  = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder  = factory.newDocumentBuilder();
            Document doc = builder.parse( new File( getProp(INTONISEDDIR)+"/"+basename+xmlExt ) );
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList tokens = (NodeList) xpath.evaluate("//t | //boundary", doc, XPathConstants.NODESET);
            
            alignBuff.append(collectTranscription(tokens));
            phoneSeq = alignBuff.toString();
            pattern = Pattern.compile("pau ssil ");
            matcher = pattern.matcher(phoneSeq);
            phoneSeq = matcher.replaceAll("pau ");
            
            pattern = Pattern.compile(" ssil pau$");
            matcher = pattern.matcher(phoneSeq);
            phoneSeq = matcher.replaceAll(" pau");
            
            /* TODO: Extra code need to write
             * to maintain minimum number of short sil.
             * or consider word boundaries as ssil.
             */ 
            pattern = Pattern.compile(" vssil ");
            matcher = pattern.matcher(phoneSeq);
            phoneSeq = matcher.replaceAll(" ");
            
            return phoneSeq;
        }
 
        /**
         * 
         * This computes a string of phonetic symbols out of an intonised mary xml:
         * - standard phonemes are taken from "ph" attribute
         * @param tokens
         * @return
         */
        private String collectTranscription(NodeList tokens) {

            // TODO: make delims argument
            // String Tokenizer devides transcriptions into syllables
            // syllable delimiters and stress symbols are retained
            String delims = "',-";

            // String storing the original transcription begins with a pause
            String orig =  " pau " ;
            
            // get original phoneme String
            for (int tNr = 0; tNr < tokens.getLength() ; tNr++ ){
                
                Element token = (Element) tokens.item(tNr);
                
                // only look at it if there is a sampa to change
                if ( token.hasAttribute("ph") ){                   
                    
                    String sampa = token.getAttribute("ph");
        
                    List<String> sylsAndDelims = new ArrayList<String>();
                    StringTokenizer sTok = new StringTokenizer(sampa, delims, true);
                    
                    while(sTok.hasMoreElements()){
                        String currTok = sTok.nextToken();
                        
                        if (delims.indexOf(currTok) == -1) {
                            // current Token is no delimiter
                            for (Allophone ph : allophoneSet.splitIntoAllophones(currTok)){
                                orig += ph.name() + " ";
                            }// ... for each phoneme
                        }// ... if no delimiter
                    }// ... while there are more tokens    
                }
                    
                // TODO: simplify
                if ( token.getTagName().equals("t") ){
                                    
                    // if the following element is no boundary, insert a non-pause delimiter
                    if (tNr == tokens.getLength()-1 || 
                        !((Element) tokens.item(tNr+1)).getTagName().equals("boundary") ){
                            orig += "vssil "; // word boundary
                            
                        }
                                                           
                } else if ( token.getTagName().equals("boundary")){
                                    
                        orig += "ssil "; // phrase boundary

                } else {
                    // should be "t" or "boundary" elements
                    assert(false);
                }
                            
            }// ... for each t-Element
            orig += "pau";
            return orig;
        }
        
        
        /**
         * Post processing Step to convert Label files
         * to MARY supportable format
         * @throws Exception
         */        
        private void getProperLabelFormat() throws Exception {
            for (int i=0; i<bnl.getLength(); i++) {
            
                convertSingleLabelFile(bnl.getName(i));               
                System.out.println( "    " + bnl.getName(i) );
                
            }
        }
        
        /**
         * Post Processing single Label file
         * @param basename
         * @throws Exception
         */
        private void convertSingleLabelFile(String basename) throws Exception {
            
            String line;
            String previous, current;
            String regexp = "\\spau|\\sssil";

            //Compile regular expression
            Pattern pattern = Pattern.compile(regexp);

            File labDir = new File(getProp(OUTLABDIR));
            if(!labDir.exists()){
                labDir.mkdir();
            }
            
            PrintWriter labelOut = new PrintWriter(
                    new FileOutputStream (new File(labDir+"/"+basename+labExt)));
            
            
            BufferedReader labelIn = new BufferedReader(
                    new InputStreamReader(new FileInputStream(getProp(EDIR)+"/lab/"+basename+labExt)));
            
            previous = labelIn.readLine();
                                  
            while((line = labelIn.readLine()) != null){

                //Replace all occurrences of pattern in input
                Matcher matcher = pattern.matcher(line);
                current = matcher.replaceAll(" _");
                
                if(previous.endsWith("_") && current.endsWith("_")){
                    previous = current;
                    continue;
                }
                                             
                labelOut.println(previous);
                previous = current;
                
            }
            
            labelOut.println(previous);
            labelOut.flush();
            labelOut.close();
            labelIn.close();
                        
        }
        
        /**
         * Provide the progress of computation, in percent, or -1 if
         * that feature is not implemented.
         * @return -1 if not implemented, or an integer between 0 and 100.
         */
        public int getProgress()
        {
            return progress;
        }

}
