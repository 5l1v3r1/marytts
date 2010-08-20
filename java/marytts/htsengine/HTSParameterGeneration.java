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

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import marytts.signalproc.analysis.PitchReaderWriter;
import marytts.signalproc.analysis.Mfccs;
import marytts.util.MaryUtils;
import marytts.util.io.LEDataInputStream;

import org.apache.log4j.Logger;


/**
 * Parameter generation out of trained HMMs.
 * 
 * Java port and extension of HTS engine version 2.0
 * Extension: mixed excitation
 * @author Marcela Charfuelan
 */
public class HTSParameterGeneration {
	
  public static final double INFTY   = ((double) 1.0e+38);
  public static final double INFTY2  = ((double) 1.0e+19);
  public static final double INVINF  = ((double) 1.0e-38);
  public static final double INVINF2 = ((double) 1.0e-19);
  public static final double LTPI    = 1.83787706640935;    /* log(2*PI) */
	

  private HTSPStream mcepPst = null;
  private HTSPStream strPst  = null;
  private HTSPStream magPst  = null;
  private HTSPStream lf0Pst  = null;
  private boolean voiced[];
  
  private Logger logger = MaryUtils.getLogger("ParameterGeneration");
  
  public double getMcep(int i, int j){ return mcepPst.getPar(i, j); }
  public int getMcepOrder(){ return mcepPst.getOrder(); }
  public int getMcepT(){ return mcepPst.getT(); }
  public HTSPStream getMcepPst(){ return mcepPst;}
  public void setMcepPst(HTSPStream var){ mcepPst = var; };
  
  public double getStr(int i, int j){ return strPst.getPar(i, j); }
  public int getStrOrder(){ return strPst.getOrder(); }
  public HTSPStream getStrPst(){ return strPst; }
  
  public double getMag(int i, int j){ return magPst.getPar(i, j); }
  public int getMagOrder(){ return magPst.getOrder(); }
  public HTSPStream getMagPst(){ return magPst; }
  
  public double getLf0(int i, int j){ return lf0Pst.getPar(i, j); }
  public int getLf0Order(){ return lf0Pst.getOrder(); }
  public HTSPStream getlf0Pst(){ return lf0Pst;}
  public void setlf0Pst(HTSPStream var){ lf0Pst = var; };
  
  public boolean getVoiced(int i){ return voiced[i]; }
  public void setVoiced(int i, boolean bval){ voiced[i]=bval; }
  public boolean [] getVoicedArray(){ return voiced; }
  public void setVoicedArray(boolean []var){ voiced = var; }
	
  /* Inverse of a given double */
  /* We actually need the inverse of the matrix of covariance, but since this matrix */ 
  /* is a diagonal matrix, then we just need to calculate the inverse of each of the  */
  /* numbers in the diagonal. */
  static public double finv(double x) {
	  
	if( x >= INFTY2 ) return 0.0;
	if( x <= -INFTY2 ) return 0.0;
	if( x <= INVINF2 && x >= 0 ) return INFTY;
	if( x >= -INVINF2 && x < 0 ) return -INFTY;
	
	return ( 1.0 / x );
	  
  }
  /** HTS maximum likelihood parameter generation
   * @param um  : utterance model sequence after processing Mary context features
   * @param ms  : HMM pdfs model set.
   */
  public void htsMaximumLikelihoodParameterGeneration(HTSUttModel um, HMMData htsData) throws Exception{
      htsMaximumLikelihoodParameterGeneration(um, htsData, "", false);
  }
  
  /** HTS maximum likelihood parameter generation
  * @param um  : utterance model sequence after processing Mary context features
  * @param ms  : HMM pdfs model set.
  * @param parFileName : file name to save parameters
  * @param debug : true for more debug information
  */
  public void htsMaximumLikelihoodParameterGeneration(HTSUttModel um, HMMData htsData, String parFileName, boolean debug) throws Exception{
	  
	int frame, uttFrame, lf0Frame;
	int state, lw, rw, k, n, i, numVoicedInModel;
	boolean nobound;
    HTSModel m;
    CartTreeSet ms = htsData.getCartTreeSet();
    
	/* Initialisation of PStream objects */
  	/* Initialise Parameter generation using UttModel um and Modelset ms */
  	/* initialise PStream objects for all the parameters that are going to be generated: */
  	/* mceppst, strpst, magpst, lf0pst */
	/* Here i should pass the window files to initialise the dynamic windows dw */
	/* for the moment the dw are all the same and hard-coded */
    if( htsData.getPdfMcpFile() != null)
	  mcepPst = new HTSPStream(ms.getMcepVsize(), um.getTotalFrame(), HMMData.MCP, htsData.getMaxGVIter());
    /* for lf0 count just the number of lf0frames that are voiced or non-zero */
    if( htsData.getPdfLf0File() != null)
      lf0Pst  = new HTSPStream(ms.getLf0Stream(), um.getLf0Frame(), HMMData.LF0, htsData.getMaxGVIter());

    /* The following are optional in case of generating mixed excitation */
    if( htsData.getPdfStrFile() != null)
	  strPst  = new HTSPStream(ms.getStrVsize(), um.getTotalFrame(), HMMData.STR, htsData.getMaxGVIter());
    if (htsData.getPdfMagFile() != null )
	  magPst  = new HTSPStream(ms.getMagVsize(), um.getTotalFrame(), HMMData.MAG, htsData.getMaxGVIter());
	   
    
	uttFrame = lf0Frame = 0;
	voiced = new boolean[um.getTotalFrame()];
	
	for(i=0; i<um.getNumUttModel(); i++){
        m = um.getUttModel(i);
        numVoicedInModel = 0;
        for(state=0; state<ms.getNumStates(); state++)
      	 for(frame=0; frame<m.getDur(state); frame++) {
      		voiced[uttFrame] = m.getVoiced(state);
      		uttFrame++;
      		if(m.getVoiced(state)){
      		  lf0Frame++;
              numVoicedInModel++;
            }
      	 }
        m.setNumVoiced(numVoicedInModel);
    }
	/* mcepframe and lf0frame are used in the original code to initialise the T field */
	/* in each pst, but here the pst are already initialised .... */
	logger.debug("utteranceFrame=" + uttFrame + " lf0frame=" + lf0Frame);
	
	
	uttFrame = 0;
	lf0Frame = 0;
	/* copy pdfs */
	for(i=0; i<um.getNumUttModel(); i++){
      m = um.getUttModel(i);          		
      for(state=0; state<ms.getNumStates(); state++) {
    	         
      	for(frame=0; frame<m.getDur(state); frame++) {
            
          //System.out.println("uttFrame=" + uttFrame + "  phone frame=" + frame + "  phone state=" + state);
             
      	  /* copy pdfs for mcep */
          if( mcepPst !=null ) {
      	    for(k=0; k<ms.getMcepVsize(); k++){
      		  mcepPst.setMseq(uttFrame, k, m.getMcepMean(state, k));
      		  mcepPst.setIvseq(uttFrame, k, finv(m.getMcepVariance(state, k)));
      	    }
          }
      	  
      	  /* copy pdf for str */
          if( strPst !=null ) {
      	    for(k=0; k<ms.getStrVsize(); k++){
      		  strPst.setMseq(uttFrame, k, m.getStrMean(state, k));
      		  strPst.setIvseq(uttFrame, k, finv(m.getStrVariance(state, k)));
      	    }
          }
      	  
      	  /* copy pdf for mag */
          if( magPst != null ) {
      	    for(k=0; k<ms.getMagVsize(); k++){
      		  magPst.setMseq(uttFrame, k, m.getMagMean(state, k));
      		  magPst.setIvseq(uttFrame, k, finv(m.getMagVariance(state, k)));
    	    }
          }
      	  
      	  /* copy pdfs for lf0 */
          if( lf0Pst != null && !htsData.getUseUnitLogF0ContinuousFeature() ) {
      	  for(k=0; k<ms.getLf0Stream(); k++){
      		lw = lf0Pst.getDWwidth(k, HTSPStream.WLEFT);
      		rw = lf0Pst.getDWwidth(k, HTSPStream.WRIGHT);
      		nobound = true;
      		/* check if current frame is voiced/unvoiced boundary or not */
      		for(n=lw; n<=rw; n++)
      		  if( (uttFrame+n) <= 0 || um.getTotalFrame() <= (uttFrame+n))
      			 nobound = false;
      		  else
      			 nobound = ( nobound && voiced[uttFrame+n] );
      		/* copy pdfs */
      		if( voiced[uttFrame] ) {
      		  lf0Pst.setMseq(lf0Frame, k, m.getLf0Mean(state, k));
      		  if( nobound || k==0 )
      			lf0Pst.setIvseq(lf0Frame, k, finv(m.getLf0Variance(state, k)));
      		  else  /* the variances for dynamic feature are set to inf on v/uv boundary */
      			lf0Pst.setIvseq(lf0Frame, k, 0.0);
      		}
    	  }
          }
      	  if( voiced[uttFrame] )
             lf0Frame++;      	  
      	  uttFrame++;
      	  
      	} /* for each frame in this state */
      } /* for each state in this model */
	}  /* for each model in this utterance */ 
	
	//System.out.println("After copying pdfs to PStreams uttFrame=" + uttFrame + " lf0frame=" + lf0Frame);
	//System.out.println("mseq[" + uttFrame + "][" + k + "]=" + mceppst.get_mseq(uttFrame, k) + "   " + m.get_mcepmean(state, k));
		
	/* parameter generation for mcep */  
    if( mcepPst != null ) {
	  logger.info("Parameter generation for MCEP: ");
      mcepPst.mlpg(htsData, htsData.getUseGV());
    }
   
    if(htsData.getUseUnitLogF0ContinuousFeature())
        loadMaryXmlF0(um, htsData);
    else if ( lf0Pst != null ){
        logger.info("Parameter generation for LF0: "); 
        lf0Pst.mlpg(htsData, htsData.getUseGV());
        // here we need set realisedF0
        //htsData.getCartTreeSet().getNumStates()
        setRealisedF0(lf0Pst, um, ms.getNumStates());
    }  
   
	/* parameter generation for str */
    boolean useGV=false;
    if( strPst != null ) {
      logger.info("Parameter generation for STR ");
      if(htsData.getUseGV() && (htsData.getPdfStrGVFile() != null) )
        useGV = true;
      strPst.mlpg(htsData, useGV);
    }

	/* parameter generation for mag */
    useGV = false;
    if( magPst != null ) {
      logger.info("Parameter generation for MAG ");
      if(htsData.getUseGV() && (htsData.getPdfMagGVFile() != null) )
        useGV = true;  
	  magPst.mlpg(htsData, useGV);
    }
	   
    if(debug) {
        // saveParam(parFileName+"mcep.bin", mcepPst, HMMData.MCP);  // no header
        // saveParam(parFileName+"lf0.bin", lf0Pst, HMMData.LF0);    // no header
        saveParamMaryFormat(parFileName, mcepPst, HMMData.MCP);
        saveParamMaryFormat(parFileName, lf0Pst, HMMData.LF0);
     }
    

	  
  }  /* method htsMaximumLikelihoodParameterGeneration */
  
  
  
  /* Save generated parameters in a binary file */
  public void saveParamMaryFormat(String fileName, HTSPStream par, int type){
    int t, m, i;
    double ws = 0.025; /* window size in seconds */
    double ss = 0.005; /* skip size in seconds */
    int fs = 16000;    /* sampling rate */
    
    try{  
        
      if(type == HMMData.LF0 ) {          
          fileName += ".ptc";
          /*
          DataOutputStream data_out = new DataOutputStream (new FileOutputStream (fileName));
          data_out.writeFloat((float)(ws*fs));
          data_out.writeFloat((float)(ss*fs));
          data_out.writeFloat((float)fs);          
          data_out.writeFloat(voiced.length);
          
          i=0;
          for(t=0; t<voiced.length; t++){    // here par.getT are just the voiced!!! so the actual length of frames can be taken from the voiced array 
             if( voiced[t] ){
               data_out.writeFloat((float)Math.exp(par.getPar(i,0)));
               i++;
             }System.out.println("GEN f0s[" + t + "]=" + Math.exp(lf0Pst.getPar(i,0)));  
             else
               data_out.writeFloat((float)0.0);
          }
          data_out.close();
          */
          
          i=0;
          double f0s[] = new double[voiced.length];
          //System.out.println("voiced.length=" + voiced.length);
          for(t=0; t<voiced.length; t++){    // here par.getT are just the voiced!!! so the actual length of frames can be taken from the voiced array 
             if( voiced[t] ){
               f0s[t] = Math.exp(par.getPar(i,0));               
               i++;
             }
             else
               f0s[t] = 0.0;
             //System.out.println("GEN f0s[" + t + "]=" + f0s[t]);
             
          }
          /* i am using this function but it changes the values of sw, and ss  *samplingrate+0.5??? for the HTS values ss=0.005 and sw=0.025 is not a problem though */
         PitchReaderWriter.write_pitch_file(fileName, f0s, (float)(ws), (float)(ss), fs);
          
          
      } else if(type == HMMData.MCP ){
          
        int numfrm =  par.getT();
        int dimension = par.getOrder();
        Mfccs mgc = new Mfccs(numfrm, dimension);  
               
        fileName += ".mfc";
                 
        for(t=0; t<par.getT(); t++)
         for (m=0; m<par.getOrder(); m++)
           mgc.mfccs[t][m] = par.getPar(t,m);
        
        mgc.params.samplingRate = fs;         /* samplingRateInHz */
        mgc.params.skipsize     = (float)ss;  /* skipSizeInSeconds */
        mgc.params.winsize      = (float)ws;  /* windowSizeInSeconds */
        
        
        mgc.writeMfccFile(fileName);
        
        /* The whole set for header is in the following order:   
        ler.writeInt(numfrm);
        ler.writeInt(dimension);
        ler.writeFloat(winsize);
        ler.writeFloat(skipsize);
        ler.writeInt(samplingRate);
        */
        
      }
      
      
      logger.info("saveParam in file: " + fileName);
    
      
    } catch (IOException e) {
        logger.info("IO exception = " + e );
    }    
  }

  
  
  /* Save generated parameters in a binary file */
  public void saveParam(String fileName, HTSPStream par, int type){
    int t, m, i;
    try{  
      
      
      if(type == HMMData.LF0 ) {
          fileName += ".f0";
          DataOutputStream data_out = new DataOutputStream (new FileOutputStream (fileName));
          i=0;
          for(t=0; t<voiced.length; t++){    /* here par.getT are just the voiced!!!*/
             if( voiced[t] ){
               data_out.writeFloat((float)Math.exp(par.getPar(i,0)));
               i++;
             }
             else
               data_out.writeFloat((float)0.0);
          }
          data_out.close();
          
      } else if(type == HMMData.MCP ){
        fileName += ".mgc";
        DataOutputStream data_out = new DataOutputStream (new FileOutputStream (fileName));  
        for(t=0; t<par.getT(); t++)
         for (m=0; m<par.getOrder(); m++)
           data_out.writeFloat((float)par.getPar(t,m));
        data_out.close();
      }
      
      
      logger.info("saveParam in file: " + fileName);
      
    } catch (IOException e) {
        logger.info("IO exception = " + e );
    }    
  }
  
  public void loadMaryXmlF0(HTSUttModel um, HMMData htsData) throws Exception{
      logger.info("Using f0 from maryXML acoustparams");      
      int i, t, state, frame, numVoiced;      
      HTSPStream newLf0Pst  = new HTSPStream(3, um.getTotalFrame(), HMMData.LF0, htsData.getMaxGVIter()); // actually the size of lf0Pst is 
                                                                                  // just the number of voiced frames               
      HTSModel m;      
      Pattern p = Pattern.compile("(\\d+,\\d+)");     
      double dval;
      numVoiced=0;
      t=0;     
      for(i=0; i<um.getNumUttModel(); i++){
        m = um.getUttModel(i);
        
        Matcher xml=null;
        if(m.getMaryXmlF0() != null)
          xml = p.matcher(m.getMaryXmlF0());
                  
        //System.out.format("model=%s  totalDur=%d numVoicedFrames=%d F0=%s\n", m.getPhoneName(), m.getTotalDur(), m.getNumVoiced(), m.getMaryXmlF0());
        
        //getF0Values( m.getMaryXmlF0(), m.getNumVoiced());
      
        for(state=0; state<htsData.getCartTreeSet().getNumStates(); state++) {
          for(frame=0; frame<m.getDur(state); frame++) { 
            if( voiced[t++] ){  // numVoiced and t are not the same because voiced values can be true or false, numVoiced counts just the voiced
               if( xml !=null && xml.find() ){
                  //System.out.println("  " + xml.group());
                  String[] f0Values = (xml.group().trim()).split(",");
                  dval = Double.parseDouble(f0Values[1]);
                  if(dval>0.0)
                    newLf0Pst.setPar(numVoiced, 0, Math.log(dval));
                  else
                    newLf0Pst.setPar(numVoiced, 0, 0.0);                   
                } else //if no  more values in xml, repeat the last one
                  newLf0Pst.setPar(numVoiced, 0, newLf0Pst.getPar(numVoiced-1, 0));
                
                //System.out.format("  state=%d frame=%d  f0=%f\n", state, frame, Math.exp(newLf0Pst.getPar(numVoiced, 0)));
                numVoiced++;              
              }//else  // this frame is not voiced, we just need to fill in the voiced segments
              
            } // for frame
        } // for state
      }  // for model in utterance model list
            
      setlf0Pst(newLf0Pst);        
  }
  
 
  public void loadMaryXmlF0New(HTSUttModel um, HMMData htsData) throws Exception{
      logger.info("Using f0 from maryXML acoustparams");      
      int i, t, k, tate, frame, numVoiced;      
      HTSPStream newLf0Pst  = new HTSPStream(3, um.getTotalFrame(), HMMData.LF0, htsData.getMaxGVIter()); // actually the size of lf0Pst is 
                                                                                  // just the number of voiced frames               
      HTSModel m;
      Vector<Double> f0Vector = new Vector<Double>();
      t=0;  
      Vector<Integer> index = new Vector<Integer>();
      Vector<Double> value = new Vector<Double>();
      int f=0; // number of voiced f0
      Pattern p = Pattern.compile("(\\d+,\\d+)"); 
      
      int key, n, interval;
      double valF0, lastValF0=0;              
      int numTotalVoiced = 0;
      for(i=0; i<um.getNumUttModel(); i++){
        m = um.getUttModel(i);
        System.out.format("\nmodel=%s  totalDur=%d numVoicedFrames=%d F0=%s\n", m.getPhoneName(), m.getTotalDur(), m.getNumVoiced(), m.getMaryXmlF0());
        k = numTotalVoiced;
        
        //getF0Values( m.getMaryXmlF0(), m.getNumVoiced(), f0FullVector, f0Vector);
        String maryXmlF0 = m.getMaryXmlF0();
        int numVoicedFrames = m.getNumVoiced();
                      
        if(maryXmlF0 != null) {
          Matcher xml = p.matcher(maryXmlF0);        
          SortedMap<Integer,Double> f0Map = new TreeMap<Integer, Double>();
          int numF0s=0;
          while ( xml.find() ) {
            String[] f0Values = (xml.group().trim()).split(",");
            f0Map.put(new Integer(f0Values[0]), new Double(f0Values[1]));
            numF0s++;
          }
          
          Set<Map.Entry<Integer,Double>> s = f0Map.entrySet();
          Iterator<Map.Entry<Integer,Double>> if0 = s.iterator();
          
          if(numF0s == numVoicedFrames){
            System.out.println("numF0s=" + numF0s);
            while(if0.hasNext()) {              
              Map.Entry<Integer,Double> mf0 = if0.next();
              key   = (Integer)mf0.getKey();
              valF0 = (Double)mf0.getValue();
              f0Vector.add(valF0);
              System.out.format("  n=%d  value:%.1f\n",f0Vector.size(),valF0);
            }
          } else {
                    
          while(if0.hasNext()) {              
            Map.Entry<Integer,Double> mf0 = if0.next();
            key   = (Integer)mf0.getKey();
            valF0 = (Double)mf0.getValue();
              
            n = (int)((numVoicedFrames*key)/100.0);           
            if(k>0)
              k--;
            System.out.println("k=" + k + "  n=" + n + "  (n+k)=" + (n+k) + "  Key :" + key + "  value :" + valF0 + "  f=" + f);            
            index.add((n+k));
            value.add(valF0);                       
            if(f>=1){
              interval = index.elementAt(f) - index.elementAt(f-1); 
              if( interval > 1 ) {  
                double slope = ((value.elementAt(f)-value.elementAt(f-1)) / interval);  
                for(n=index.elementAt(f-1); n<index.elementAt(f); n++) {
                  double newVal = (slope * (n-index.elementAt(f-1))) +  value.elementAt(f-1);
                  f0Vector.add(newVal);
                  System.out.format("  n=%d  value:%.1f\n",n,newVal);
                }
              } else if (interval == 1){ // if at least there is one value in between
                 f0Vector.add(value.elementAt(f-1));
                 System.out.format("  *n=%d  value:%.1f\n",index.elementAt(f-1),value.elementAt(f-1));
              }
            }
            lastValF0 =  value.elementAt(f);          
            f++;            
          }
        } 
        }
        numTotalVoiced += numVoicedFrames;
        System.out.println("numTotalVoiced=" + numTotalVoiced);
        
      }  // for model in utterance model list
      // Add the last value
      if(lastValF0 > 0.0)
        f0Vector.add(lastValF0);  
      
      for(i=0; i<f0Vector.size(); i++){
        System.out.format("n=%d  %.2f \n", i, f0Vector.elementAt(i));
        newLf0Pst.setPar(i, 0, Math.log(f0Vector.elementAt(i)));
      }
      System.out.println();
      setlf0Pst(newLf0Pst);        
  }
 

  
  
  public void setRealisedF0(HTSPStream lf0Pst, HTSUttModel um, int numStates) {
      int i, t, k, numVoicedInModel;      
      HTSModel m;
      int state, frame;            
      String formattedF0; 
      float f0;
      t=0;      
      for(i=0; i<um.getNumUttModel(); i++){
        m = um.getUttModel(i);
        numVoicedInModel = m.getNumVoiced();
        formattedF0 = "";
        k=1;
        for(state=0; state<numStates; state++) {
          for(frame=0; frame<m.getDur(state); frame++){
            if( voiced[t++] ){
                f0 = (float)Math.exp(lf0Pst.getPar(i,0));
                formattedF0 += "(" + Integer.toString((int)((k*100.0)/numVoicedInModel)) + "," + Integer.toString((int)f0) + ")";
                k++;  
            }
          } // for unvoiced frame                             
        } // for state
       if(!formattedF0.contentEquals("")){ 
           m.setMaryXmlF0(formattedF0);
         //m.setUnit_f0ArrayStr(formattedF0);  
         //System.out.println("ph=" + m.getPhoneName() + "  " + formattedF0);
       }
      }  // for model in utterance model list
  }
  

  
  
  /***
   * Load logf0, in HTS format, create a voiced array and set this values in pdf2par
   * This contour should be aligned with the durations, so the total duration in frames should be the same as in the lf0 file 
   * @param lf0File: in HTS formant 
   * @param totalDurationFrames: the total duration in frames can be calculated as:
   *                             totalDurationFrames = totalDurationInSeconds / (framePeriodInSamples / SamplingFrequencyInHz)
   * @param pdf2par: HTSParameterGeneration object
   * @throws Exception If the number of frames in the lf0 file is not the same as represented in the total duration (in frames).
   */
  public void loadLogF0FromExternalFile(String lf0File, int totalDurationFrames) throws Exception{
      
      LEDataInputStream lf0Data;
      
      int lf0Vsize = 3;
      int totalFrame = 0;
      int lf0VoicedFrame = 0;
      float fval;  
      lf0Data = new LEDataInputStream (new BufferedInputStream(new FileInputStream(lf0File)));
      
      /* First i need to know the size of the vectors */
      try { 
        while (true) {
          fval = lf0Data.readFloat();
          totalFrame++;  
          if(fval>0)
           lf0VoicedFrame++;
        } 
      } catch (EOFException e) { }
      lf0Data.close();
      
      // Here we need to check that the total duration in frames is the same as the number of frames
      // (NOTE: it can be a problem afterwards when the durations per phone are aligned to the lenght of each state
      // in htsEngine._processUtt() )         
      if( totalDurationFrames > totalFrame){
        throw new Exception("The total duration in frames (" +  totalDurationFrames + ") is greater than the number of frames in the lf0File (" + 
             totalFrame + "): " + lf0File + "\nIt can be fixed to some extend using a smaller value for the variable: newStateDurationFactor");
      } else if( totalDurationFrames < totalFrame ){
        if (Math.abs(totalDurationFrames-totalFrame) < 5)
          System.out.println("Warning: The total duration in frames (" +  totalDurationFrames + ") is smaller than the number of frames in the lf0 file (" 
            + totalFrame + "): " + lf0File + "\n         It can be fixed to some extend using a greater value for the variable: newStateDurationFactor");
        else
          throw new Exception("The total duration in frames (" +  totalDurationFrames + ") is smaller than the number of frames in the lf0File (" + 
              totalFrame + "): " + lf0File + "\nIt can be fixed to some extend using a greater value for the variable: newStateDurationFactor");
        
      } else
        System.out.println("totalDurationFrames = " + totalDurationFrames + "  totalF0Frames = " + totalFrame);  
        
     
      voiced = new boolean[totalFrame];
      lf0Pst = new HTSPStream(lf0Vsize, totalFrame, HMMData.LF0, 0);
      
      /* load lf0 data */
      /* for lf0 i just need to load the voiced values */
      lf0VoicedFrame = 0;
      lf0Data = new LEDataInputStream (new BufferedInputStream(new FileInputStream(lf0File)));
      for(int i=0; i<totalFrame; i++){
        fval = lf0Data.readFloat();  
        if(fval < 0){
          voiced[i] = false;
          //System.out.println("frame: " + i + " = 0.0");
        }
        else{
          voiced[i] = true;
          lf0Pst.setPar(lf0VoicedFrame, 0, fval);
          lf0VoicedFrame++;
          //System.out.format("frame: %d = %.2f\n", i, fval);
        }
      }
      lf0Data.close();
      
  }
 
  
  
} /* class ParameterGeneration */
