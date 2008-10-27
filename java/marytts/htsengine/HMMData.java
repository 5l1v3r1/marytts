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

package marytts.htsengine;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Properties;
import java.util.Scanner;
import java.util.Vector;

import marytts.features.FeatureDefinition;

import org.apache.log4j.Logger;

/**
 * Configuration files and global variables for HTS engine.
 * 
 * Java port and extension of HTS engine version 2.0
 * Extension: mixed excitation
 * @author Marcela Charfuelan
 */
public class HMMData {
    	
    /** Number of model and identificator for the models*/
	public static final int HTS_NUMMTYPE = 5;
	public static final int DUR = 0;
	public static final int LF0 = 1;
	public static final int MCP = 2;
	public static final int STR = 3;
	public static final int MAG = 4;

    private Logger logger = Logger.getLogger("HMMData");
    
	/** Global variables for some functions, initialised with default values, so these values 
	 * can be loaded from a configuration file. */
	private int rate       = 16000; /* sampling rate                              */
	private int fperiod    = 80;    /* frame period (point)                       */
	private double rho     = 0.0;   /* variable for speaking rate control         */
    
    /* MGC: stage=gamma=0.0 alpha=0.42 linear gain  
     * LSP: gamma>0.0 
     *     LSP: gamma=1.0 alpha=0.0  
     * Mel-LSP: gamma=1.0 alpha=0.42 
     * MGC-LSP: gamma=3.0 alpha=0.42 */
    private int stage          = 0;      /* defines gamma=-1/stage : if stage=0 then Gamma=0 */
    private double alpha       = 0.42;   /* variable for frequency warping parameter   */
    private double beta        = 0.0;    /* variable for postfiltering                 */
    private boolean useLogGain = false;  /* log gain flag (for LSP) */
    
	private double uv      = 0.5;   /* variable for U/V threshold                 */
	private boolean algnst        = false; /* use state level alignment for duration     */
	private boolean algnph        = false; /* use phoneme level alignment for duration   */
    private boolean useMixExc     = true;  /* use Mixed Excitation */
    private boolean useFourierMag = false;   /* use Fourier magnitudes for pulse generation */
    private boolean useGV         = false; /* use global variance in parameter generation */
    private boolean useGmmGV      = false; /* use global variance as a Gaussian Mixture Model */
    
    /** variables for controling generation of speech in the vocoder                
     * these variables have default values but can be fixed and read from the      
     * audio effects component.                                              [Default][min--max]   */
    private double f0Std   = 1.0;   /* variable for f0 control, multiply f0      [1.0][0.0--5.0]   */
    private double f0Mean  = 0.0;   /* variable for f0 control, add f0           [0.0][0.0--100.0] */
    private double length  = 0.0;   /* total number of frame for generated speech                  */
	                                /* length of generated speech (in seconds)   [N/A][0.0--30.0]  */
    private double durationScale = 1.0; /* less than 1.0 is faster and more than 1.0 is slower, min=0.1 max=3.0 */
	
	/** Tree files and TreeSet object */
	private String treeDurFile;         /* durations tree file */
	private String treeLf0File;         /* lf0 tree file */
	private String treeMcpFile;         /* MCP tree file */
	private String treeStrFile;         /* Strengths tree file */
	private String treeMagFile;         /* Fourier magnitudes tree file */   
    private FeatureDefinition feaDef;   /* The feature definition is used for loading the tree using questions in MARY format */
    
     /** TreeSet contains the tree-xxx.inf, xxx: dur, lf0, mcp, str and mag 
     * these are all the trees trained for a particular voice. */
    private HTSTreeSet ts = new HTSTreeSet(HTS_NUMMTYPE);
	
    /** HMM pdf model files and ModelSet object */
	private String pdfDurFile;  /* durations Pdf file */
	private String pdfLf0File;  /* lf0 Pdf file */
	private String pdfMcpFile;  /* MCP Pdf file */
	private String pdfStrFile;  /* Strengths Pdf file */
	private String pdfMagFile;  /* Fourier magnitudes Pdf file */
    
     /** ModelSet contains the .pdf's (means and variances) for dur, lf0, mcp, str and mag
     * these are all the HMMs trained for a particular voice */   
    private HTSModelSet ms = new HTSModelSet();
	
    /** GV pdf files*/
    /** Global variance file, it contains one global mean vector and one global diagonal covariance vector */
    private String pdfLf0GVFile; /* lf0 GV pdf file */  
    private String pdfMcpGVFile; /* Mcp GV pdf file */ 
    private String pdfStrGVFile; /* Str GV pdf file */ 
    private String pdfMagGVFile; /* Mag GV pdf file */ 
    
    /** GVModelSet contains the global covariance and mean for lf0, mcp, str and mag */
    private GVModelSet gv = new GVModelSet();

	/** Variables for mixed excitation */
	private String mixFiltersFile; /* this file contains the filter taps for mixed excitation */
	private int numFilters;
	private int orderFilters;
    private double mixFilters[][];      /* filters for mixed excitation */
	
	/* Feature list file and Vector which will contain the loaded features from this file */
	private String featureListFile;
    private Vector<String> featureList = new Vector<String>();
    
    
    /* Example CONTEXTFEATURE file in MARY format */
    private String feaFile;
	
	public int getRate() { return rate; }
	public int getFperiod() { return fperiod; } 
	public double getRho() { return rho; } 
	public double getAlpha() { return alpha; }
	public double getBeta() { return beta; }
    public int getStage() { return stage; }
    public boolean getUseLogGain(){ return useLogGain; }
	public double getUV() { return  uv; }
	public boolean getAlgnst() { return algnst; }
	public boolean getAlgnph() { return algnph; }

    public double getF0Std() { return f0Std; }
    public double getF0Mean() { return f0Mean; }
    public double getLength() { return length; }
    public double getDurationScale() { return durationScale; }
    
	public String getTreeDurFile() { return treeDurFile; } 
	public String getTreeLf0File() { return treeLf0File; } 
	public String getTreeMcpFile() { return treeMcpFile; }  
	public String getTreeStrFile() { return treeStrFile; } 
	public String getTreeMagFile() { return treeMagFile; }  
    public FeatureDefinition getFeatureDefinition() { return feaDef; }
	
	public String getPdfDurFile() { return pdfDurFile; }   
	public String getPdfLf0File() { return pdfLf0File; }   
	public String getPdfMcpFile() { return pdfMcpFile; } 
	public String getPdfStrFile() { return pdfStrFile; } 
	public String getPdfMagFile() { return pdfMagFile; } 
    
    public boolean getUseMixExc(){ return useMixExc; }
    public boolean getUseFourierMag(){ return useFourierMag; }
    public boolean getUseGV(){ return useGV; }
    public boolean getUseGmmGV(){ return useGmmGV; }
    public String getPdfLf0GVFile() { return pdfLf0GVFile; }   
    public String getPdfMcpGVFile() { return pdfMcpGVFile; } 
    public String getPdfStrGVFile() { return pdfStrGVFile; } 
    public String getPdfMagGVFile() { return pdfMagGVFile; }
    	
	public String getFeatureListFile() { return featureListFile; }
    /* This function returns the feature list already loaded in a Vector */
    public Vector<String> getFeatureList() { return featureList; }  
    public String getFeaFile() { return feaFile; }
	
	public String getMixFiltersFile() { return mixFiltersFile; } 
	public int getNumFilters(){ return numFilters; }
	public int getOrderFilters(){ return orderFilters; }
    public double [][] getMixFilters(){ return mixFilters; }
    
    public void setAlpha(double dval){ alpha = dval; }
    public void setBeta(double dval){ beta = dval; }
    public void setStage(int ival){ stage = ival; }
    public void setUseLogGain(boolean bval){ useLogGain = bval; }
    
    /* These variables have default values but can be modified with setting in 
     * audio effects component. */
    public void setF0Std(double dval) {
        /* default=1.0, min=0.0, max=3.0 */
        if( dval >= 0.0 && dval <= 3.0 )
          f0Std = dval;
        else
          f0Std = 1.0;
    }
    public void setF0Mean(double dval) {
        /* default=0.0, min=-300.0, max=300.0 */
        if( dval >= -300.0 && dval <= 300.0 )
          f0Mean = dval; 
        else
          f0Mean = 0.0;
    }
    public void setLength(double dval) { length = dval; }
    public void setDurationScale(double dval) { 
        /* default=1.0, min=0.1, max=3.0 */
        if( dval >= 0.1 && dval <= 3.0 )
          durationScale = dval; 
        else
          durationScale = 1.0; 
        
    }
  
    public HTSTreeSet getTreeSet() { return ts; }       
    public HTSModelSet getModelSet() { return ms; }
    public GVModelSet getGVModelSet() { return gv; }
 
    public void setTreeDurFile(String str) { treeDurFile = str; } 
    public void setTreeLf0File(String str) { treeLf0File = str; } 
    public void setTreeMcpFile(String str) { treeMcpFile = str; }  
    public void setTreeStrFile(String str) { treeStrFile = str; } 
    public void setTreeMagFile(String str) { treeMagFile = str; }  
    public void setFeatureDefinition(String contextFile) /* this file should include next, next_next, prev, prev_prev phoneme features */
    throws Exception                                     
    { 
        Scanner context = new Scanner(new BufferedReader(new FileReader(contextFile)));
        String strContext="";
        while (context.hasNext()) {
          strContext += context.nextLine(); 
          strContext += "\n";
        }
        context.close();
        //System.out.println(strContext);
        feaDef = new FeatureDefinition(new BufferedReader(new StringReader(strContext)), false);
          
    }
    
    public void setPdfDurFile(String str) { pdfDurFile = str; }   
    public void setPdfLf0File(String str) { pdfLf0File = str; }   
    public void setPdfMcpFile(String str) { pdfMcpFile = str; } 
    public void setPdfStrFile(String str) { pdfStrFile = str; } 
    public void setPdfMagFile(String str) { pdfMagFile = str; } 
    
    public void setUseMixExc(boolean bval){ useMixExc = bval; }
    public void setUseFourierMag(boolean bval){ useFourierMag = bval; }
    public void setUseGV(boolean bval){ useGV = bval; }
    public void setUseGmmGV(boolean bval){ useGmmGV = bval; }
    
    public void setPdfLf0GVFile(String str) { pdfLf0GVFile = str; }   
    public void setPdfMcpGVFile(String str) { pdfMcpGVFile = str; } 
    public void setPdfStrGVFile(String str) { pdfStrGVFile = str; } 
    public void setPdfMagGVFile(String str) { pdfMagGVFile = str; } 
    
    public void setFeaListFile(String str) { featureListFile = str; }
    public void setFeaFile(String str) { feaFile = str; }
    
    public void setMixFiltersFile(String str) { mixFiltersFile = str; } 
    public void setNumFilters(int val){ numFilters = val; }
    public void setOrderFilters(int val){ orderFilters = val; }
    
    public void loadTreeSet() throws Exception { ts.loadTreeSet(this, feaDef); }   
    public void loadModelSet() throws Exception { ms.loadModelSet(this); }  
    public void loadGVModelSet() throws Exception { gv.loadGVModelSet(this); } 
	
	/** Reads from configuration file all the data files in this class 
     * this method is used when running HTSengine stand alone. */
	public void initHMMData(String voice, String MaryBase, String ConfigFile) {		
      Properties props = new Properties();
      
      try {
          FileInputStream fis = new FileInputStream( MaryBase+"conf/"+ConfigFile );
          props.load( fis );
          fis.close();
          
          if( props.getProperty( "voice." + voice + ".alpha" ) != null)
            alpha = Double.parseDouble(props.getProperty( "voice." + voice + ".alpha" ));
          if( props.getProperty( "voice." + voice + ".gamma" ) != null)
            stage = Integer.parseInt(props.getProperty( "voice." + voice + ".gamma" ));
          if( props.getProperty( "voice." + voice + ".logGain" ) != null)
            useLogGain = Boolean.valueOf(props.getProperty( "voice." + voice + ".logGain" )).booleanValue();  
          if( props.getProperty( "voice." + voice + ".beta" ) != null)
            beta = Double.parseDouble(props.getProperty( "voice." + voice + ".beta" ));
                  
          treeDurFile = MaryBase + props.getProperty( "voice." + voice + ".Ftd" ).substring(10);
          treeLf0File = MaryBase + props.getProperty( "voice." + voice + ".Ftf" ).substring(10);          
          treeMcpFile = MaryBase + props.getProperty( "voice." + voice + ".Ftm" ).substring(10);
          if( props.getProperty( "voice." + voice + ".Fts" ) != null)
            treeStrFile = MaryBase + props.getProperty( "voice." + voice + ".Fts" ).substring(10);
          if( props.getProperty( "voice." + voice + ".Fta" ) != null)
            treeMagFile = MaryBase + props.getProperty( "voice." + voice + ".Fta" ).substring(10);
          
          pdfDurFile = MaryBase + props.getProperty( "voice." + voice + ".Fmd" ).substring(10);
          pdfLf0File = MaryBase + props.getProperty( "voice." + voice + ".Fmf" ).substring(10);           
          pdfMcpFile = MaryBase + props.getProperty( "voice." + voice + ".Fmm" ).substring(10);
          if( props.getProperty( "voice." + voice + ".Fms" ) != null)
            pdfStrFile = MaryBase + props.getProperty( "voice." + voice + ".Fms" ).substring(10);
          if( props.getProperty( "voice." + voice + ".Fma" ) != null)
            pdfMagFile = MaryBase + props.getProperty( "voice." + voice + ".Fma" ).substring(10);
          
          if( props.getProperty( "voice." + voice + ".useMixExc" ) != null)
            useMixExc = Boolean.valueOf(props.getProperty( "voice." + voice + ".useMixExc" )).booleanValue();
          if( props.getProperty( "voice." + voice + ".useFourierMag" ) != null)
            useFourierMag = Boolean.valueOf(props.getProperty( "voice." + voice + ".useFourierMag" )).booleanValue();
          useGV     = Boolean.valueOf(props.getProperty( "voice." + voice + ".useGV" )).booleanValue();
          useGmmGV  = Boolean.valueOf(props.getProperty( "voice." + voice + ".useGmmGV" )).booleanValue();
          
          if(useGV){
            pdfLf0GVFile = MaryBase + props.getProperty( "voice." + voice + ".Fgvf" ).substring(10);        
            pdfMcpGVFile = MaryBase + props.getProperty( "voice." + voice + ".Fgvm" ).substring(10);
            if( props.getProperty( "voice." + voice + ".Fgvs" ) != null)
              pdfStrGVFile = MaryBase + props.getProperty( "voice." + voice + ".Fgvs" ).substring(10);
            if( props.getProperty( "voice." + voice + ".Fgva" ) != null)
              pdfMagGVFile = MaryBase + props.getProperty( "voice." + voice + ".Fgva" ).substring(10);
          }else if(useGmmGV){
              pdfLf0GVFile = MaryBase + props.getProperty( "voice." + voice + ".Fgmmgvf" ).substring(10);        
              pdfMcpGVFile = MaryBase + props.getProperty( "voice." + voice + ".Fgmmgvm" ).substring(10);
          }
          
          /* Feature list file */
          featureListFile = MaryBase + props.getProperty( "voice." + voice + ".FeaList" ).substring(10);
          
          /* Example context feature file in HTSCONTEXT_EN format */
          feaFile = MaryBase + props.getProperty( "voice." + voice + ".FeaFile" ).substring(10);
          
          /* Configuration for mixed excitation */
          if( treeStrFile != null ) {
            mixFiltersFile = MaryBase + props.getProperty( "voice." + voice + ".Fif" ).substring(10); 
            numFilters     = Integer.parseInt(props.getProperty( "voice." + voice + ".in" ));
            orderFilters   = Integer.parseInt(props.getProperty( "voice." + voice + ".io" ));
            logger.info("Loading Mixed Excitation Filters File:");
            readMixedExcitationFiltersFile();
          }
          
          props.clear();
          
      } 
      catch (IOException e) {
          logger.debug("Caught IOException: " +  e.getMessage());
	  }	catch (Exception e) {
          logger.debug(e.getMessage()); 
      }
      
      try {
        /* Load TreeSet ts and ModelSet ms for current voice*/
        logger.info("Loading Tree Set:");
        setFeatureDefinition(feaFile); /* first set the feature definition with one example of context feature file */
        ts.loadTreeSet(this, feaDef);   
       
        logger.info("Loading Model Set:");
        ms.loadModelSet(this);
        
        logger.info("Loading GV Model Set:");
        gv.loadGVModelSet(this);
      
        /* Load (un-commented) context feature list from featureListFile */
        logger.info("Loading Feature List:");
        readFeatureList(featureList, featureListFile);
      }
      catch (Exception e) {
          logger.debug(e.getMessage()); 
      }
      
      if( featureList.size() == 0)
          logger.debug("initHMMData: Warning feature list file empty or feature list not loaded. ");
   		
	}
	
    
    /** This function reads the feature list file, for example feature_list_en_05.pl
     * and fills in a vector the elements in that list that are un-commented 
     */
    public void readFeatureList(Vector<String> feaList, String feaListFile) throws FileNotFoundException {
      String line;
      int i;
      
      Scanner s = null;
      try {
        s = new Scanner(new BufferedReader(new FileReader(feaListFile))).useDelimiter("\n");
         
        /*  see NOTE in HTSCONTEXTTRANSLATOR processTargetFeatures() 
        feaList.addElement("phoneme");
        feaList.addElement("prev_phoneme");
        feaList.addElement("prev_prev_phoneme");
        feaList.addElement("next_phoneme");
        feaList.addElement("next_next_phoneme");
        */
        
        while (s.hasNext()) {
          line = s.next();
          //System.out.println("fea: "+ line);
          if(!line.contains("#") && line.length()>0){    /* if it is not commented */
              // MS, 20 Oct 2008: as the feature names don't contain the prefix "mary_" anymore,
              // we cannot identify feature names as before.
              // However, it seems that, when there is a comma in the line, 
              // the first element should be the right one in all examples I found.
              if (line.contains(",")) { // it contains a comma
                  String[] elem = line.split(",");
                  String feaString = elem[0];
                  assert feaString.contains("\"") : "Unlikely to contain a feature name: '"+feaString+"'!";
                  feaList.addElement(feaString.substring(feaString.indexOf("\"")+1, feaString.lastIndexOf("\"")));
                  //for(i=0; i<elem.length; i++)
                  //  if(elem[i].contains("mary_")){  /* if starts with mary_ */                 
                  //    feaList.addElement(elem[i].substring(elem[i].indexOf("\"")+1, elem[i].lastIndexOf("\"")));
                  //    //System.out.println("  -->  "+ featureList.lastElement()); 
                  //  }                  
              }
          }
        }
                
        if (s != null) { 
          s.close();
        }
        
      } catch (FileNotFoundException e) {
          logger.debug("readFeatureList:  " + e.getMessage());
          throw new FileNotFoundException("readFeatureList " + e.getMessage());
      }
      
      logger.info("readFeatureList: loaded " + feaList.size() + " context features from " + featureListFile);
      
    } /* method ReadFeatureList */

    
    /** Initialisation for mixed excitation : it loads the filter taps are read from 
     * MixFilterFile specified in the configuration file. */
    public void readMixedExcitationFiltersFile() throws Exception {
      String line;
      mixFilters = new double[numFilters][orderFilters];  
      /* get the filter coefficients */
      Scanner s = null;
        int i,j;
        try {
          s = new Scanner(new BufferedReader(new FileReader(mixFiltersFile)));
          s.useLocale(Locale.US);
          
          logger.debug("reading mixed excitation filters file: " + mixFiltersFile);
          while ( s.hasNext("#") ) {  /* skip comment lines */
            line = s.nextLine(); 
            //System.out.println("comment: " + line ); 
          }
          for(i=0; i<numFilters; i++){
            for(j=0; j<orderFilters; j++) {                  
              if (s.hasNextDouble()) {
                mixFilters[i][j] = s.nextDouble();
                //System.out.println("h["+i+"]["+j+"]="+h[i][j]);
              }
              else{
                logger.debug("initMixedExcitation: not enough fiter taps in file = " + mixFiltersFile);
                throw new Exception("initMixedExcitation: not enough fiter taps in file = " + mixFiltersFile);
              }
            }
          }   
        } catch (FileNotFoundException e) {
            logger.debug("initMixedExcitation: " + e.getMessage());
            throw new FileNotFoundException("initMixedExcitation: " + e.getMessage());
        } finally {
            s.close();
        }   
        
    } /* method readMixedExcitationFiltersFile() */


	
}
