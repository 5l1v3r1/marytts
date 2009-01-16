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
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.SortedMap;
import java.util.TreeMap;

import marytts.cart.CART;
import marytts.cart.Node;
import marytts.cart.LeafNode.LeafType;
import marytts.cart.io.MaryCARTWriter;
import marytts.cart.io.WagonCARTReader;
import marytts.features.FeatureDefinition;
import marytts.unitselection.data.FeatureFileReader;
import marytts.unitselection.data.TimelineReader;
import marytts.unitselection.data.Unit;
import marytts.unitselection.data.UnitFileReader;


/**
 * A class which converts a text file in festvox format
 * into a one-file-per-utterance format in a given directory.
 * @author schroed
 *
 */
public class DurationCARTTrainer extends VoiceImportComponent
{
    protected File unitlabelDir;
    protected File unitfeatureDir;
    protected File durationDir;
    protected File durationFeatsFile;
    protected File durationDescFile;
    protected DatabaseLayout db = null;
    protected int percent = 0;
    protected boolean useStepwiseTraining = false;
    
    private final String name = "DurationCARTTrainer";
    public final String DURTREE = name+".durTree";
    public final String LABELDIR = name+".labelDir";
    public final String FEATUREDIR = name+".featureDir";   
    public final String STEPWISETRAINING = name+".stepwiseTraining";
    public final String FEATUREFILE = name+".featureFile";
    public final String UNITFILE = name+".unitFile";
    public final String WAVETIMELINE = name+".waveTimeline";
    public final String ESTDIR = name+".estDir";

    public String getName(){
        return name;
    }
    
 
     public void initialiseComp()
    {       
        this.unitlabelDir = new File(getProp(LABELDIR));
        this.unitfeatureDir = new File(getProp(FEATUREDIR));
        String rootDir = db.getProp(db.ROOTDIR);
        String durDir = db.getProp(db.TEMPDIR);
        this.durationDir = new File(durDir);
        if (!durationDir.exists()){
            System.out.print("temp dir "+durDir
                    +" does not exist; ");
            if (!durationDir.mkdir()){
                throw new Error("Could not create DURDIR");
            }
            System.out.print("Created successfully.\n");
        }         
        this.durationFeatsFile = new File(durDir+"dur.feats");
        this.durationDescFile = new File(durDir+"dur.desc");
        this.useStepwiseTraining = Boolean.valueOf(getProp(STEPWISETRAINING)).booleanValue();

    }
    
     public SortedMap getDefaultProps(DatabaseLayout db){
        this.db = db;
        if (props == null){
            props = new TreeMap();
            String fileSeparator = System.getProperty("file.separator");
            props.put(FEATUREDIR, db.getProp(db.ROOTDIR)                       
                    +"phonefeatures"
                    +fileSeparator);
            props.put(LABELDIR, db.getProp(db.ROOTDIR)
                    +"phonelab"
                    +fileSeparator);            
            props.put(STEPWISETRAINING,"false");
            props.put(FEATUREFILE, db.getProp(db.FILEDIR)
                    +"phoneFeatures"+db.getProp(db.MARYEXT));
            props.put(UNITFILE, db.getProp(db.FILEDIR)
                    +"phoneUnits"+db.getProp(db.MARYEXT));
            props.put(WAVETIMELINE, db.getProp(db.FILEDIR)
                    +"timeline_waveforms"+db.getProp(db.MARYEXT));  
            props.put(DURTREE,db.getProp(db.FILEDIR)
                    +"dur.tree");                    
           String estdir = System.getProperty("ESTDIR");
           if ( estdir == null ) {
               estdir = "/project/mary/Festival/speech_tools/";
           }
           props.put(ESTDIR,estdir);
        }
       return props;
    }
     
     protected void setupHelp(){
         props2Help = new TreeMap();
         props2Help.put(FEATUREDIR, "directory containing the phonefeatures");
         props2Help.put(LABELDIR, "directory containing the phone labels");            
         props2Help.put(STEPWISETRAINING,"\"false\" or \"true\" ???????????????????????????????????????????????????????????");
         props2Help.put(FEATUREFILE, "file containing all phone units and their target cost features");
         props2Help.put(UNITFILE, "file containing all phone units");
         props2Help.put(WAVETIMELINE, "file containing all wave files"); 
         props2Help.put(ESTDIR,"directory containing the local installation of the Edinburgh Speech Tools");
         props2Help.put(DURTREE,"file containing the duration CART. Will be created by this module");
     }


    public boolean compute() throws IOException
    {
        FeatureFileReader featureFile = 
            FeatureFileReader.getFeatureFileReader(getProp(FEATUREFILE));
        UnitFileReader unitFile = new UnitFileReader(getProp(UNITFILE));
        TimelineReader waveTimeline = new TimelineReader(getProp(WAVETIMELINE));

        PrintWriter toFeaturesFile = new PrintWriter(new FileOutputStream(durationFeatsFile));
        System.out.println("Duration CART trainer: exporting duration features");

        FeatureDefinition featureDefinition = featureFile.getFeatureDefinition();
        int nUnits = 0;
        for (int i=0, len=unitFile.getNumberOfUnits(); i<len; i++) {
            // We estimate that feature extraction takes 1/10 of the total time
            // (that's probably wrong, but never mind)
            percent = 10*i/len;
            Unit u = unitFile.getUnit(i);
            float dur = u.getDuration() / (float) unitFile.getSampleRate();
            if (dur >= 0.01) { // enforce a minimum duration for training data
                toFeaturesFile.println(dur + " " + featureDefinition.toFeatureString(featureFile.getFeatureVector(i)));
                nUnits++;
            }
        }
        if (useStepwiseTraining) percent = 1;
        else percent = 10;
        toFeaturesFile.close();
        System.out.println("Duration features extracted for "+nUnits+" units");
        
        PrintWriter toDesc = new PrintWriter(new FileOutputStream(durationDescFile));
        generateFeatureDescriptionForWagon(featureDefinition, toDesc);
        toDesc.close();
        
        boolean ok = false;
        // Now, call wagon
        WagonCaller wagonCaller = new WagonCaller(getProp(ESTDIR),null);
        File wagonTreeFile = new File(getProp(DURTREE));
        if (useStepwiseTraining) {
            // Split the data set in training and test part:
            Process traintest = Runtime.getRuntime().exec("/project/mary/Festival/festvox/src/general/traintest "+durationFeatsFile.getAbsolutePath());
             try {
                traintest.waitFor();
             } catch (InterruptedException ie) {}
             ok = wagonCaller.callWagon("-data "+durationFeatsFile.getAbsolutePath()+".train"
                     +" -test "+durationFeatsFile.getAbsolutePath()+".test -stepwise"
                     +" -desc "+durationDescFile.getAbsolutePath()
                     +" -stop 10 "
                     +" -output "+wagonTreeFile.getAbsolutePath());
        } else {
            ok = wagonCaller.callWagon("-data "+durationFeatsFile.getAbsolutePath()
                    +" -desc "+durationDescFile.getAbsolutePath()
                    +" -stop 10 "
                    +" -output "+wagonTreeFile.getAbsolutePath());            
        }
        if(ok){
            String destinationFile = getProp(DURTREE);
            WagonCARTReader wagonDURReader = new WagonCARTReader(LeafType.FloatLeafNode);
            Node rootNode = wagonDURReader.load(new BufferedReader(new FileReader(destinationFile)), featureDefinition);
            CART durCart = new CART(rootNode, featureDefinition);
            MaryCARTWriter wwdur = new MaryCARTWriter();
            wwdur.dumpMaryCART(durCart, destinationFile);
        }
        percent = 100;
        return ok;

    }
    
    
    private void generateFeatureDescriptionForWagon(FeatureDefinition fd, PrintWriter out)
    {
        out.println("(");
        out.println("(segment_duration float)");
        int nDiscreteFeatures = fd.getNumberOfByteFeatures()+fd.getNumberOfShortFeatures();
        for (int i=0, n=fd.getNumberOfFeatures(); i<n; i++) {            
            out.print("( ");
            out.print(fd.getFeatureName(i));
            if (i<nDiscreteFeatures) { // list values
                if (fd.getNumberOfValues(i) == 20 && fd.getFeatureValueAsString(i, 19).equals("19")) {
                    // one of our pseudo-floats
                    out.println(" float )");
                } else { // list the values
                    for (int v=0, vmax=fd.getNumberOfValues(i); v<vmax; v++) {
                        out.print("  ");
                        String val = fd.getFeatureValueAsString(i, v);
                        if (val.indexOf('"') != -1) {
                            StringBuffer buf = new StringBuffer();
                            for (int c=0; c<val.length(); c++) {
                                char ch = val.charAt(c);
                                if (ch == '"') buf.append("\\\"");
                                else buf.append(ch);
                            }
                            val = buf.toString();
                        }
                        out.print("\""+val+"\"");
                    }
                    out.println(" )");
                }
            } else { // float feature
                    out.println(" float )");
            }

        }
        out.println(")");
    }

    
    /**
     * Provide the progress of computation, in percent, or -1 if
     * that feature is not implemented.
     * @return -1 if not implemented, or an integer between 0 and 100.
     */
    public int getProgress()
    {
        return percent;
    }


    public static void main(String[] args) throws Exception
    {
        DurationCARTTrainer dct = new DurationCARTTrainer();
         DatabaseLayout db = new DatabaseLayout(dct);
         dct.compute();
    }


}
