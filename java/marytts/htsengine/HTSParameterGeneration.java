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

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import marytts.signalproc.analysis.F0ReaderWriter;
import marytts.signalproc.analysis.Mfccs;

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
  
  private Logger logger = Logger.getLogger("ParameterGeneration");
  
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
  public boolean [] getVoicedArray(){ return voiced; }
  public void setVoicedArray(boolean []var){ voiced = var; }
	
  /* Inverse of a given double */
  /* We actually need the inverse of the matrix of covariance, but since this matrix */ 
  /* is a diagonal matrix, then we just need to calculate the inverse of each of the  */
  /* numbers in the diagonal. */
  private double finv(double x) {
	  
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
  public void htsMaximumLikelihoodParameterGeneration(HTSUttModel um, HMMData htsData, String parFileName, boolean debug) throws Exception{
	  
	int frame, uttFrame, lf0Frame;
	int state, lw, rw, k, n, i;
	boolean nobound;
    HTSModel m;
    CartTreeSet ms = htsData.getCartTreeSet();
 //---   HTSModelSet ms = htsData.getModelSet();
    
	/* Initialisation of PStream objects */
  	/* Initialise Parameter generation using UttModel um and Modelset ms */
  	/* initialise PStream objects for all the parameters that are going to be generated: */
  	/* mceppst, strpst, magpst, lf0pst */
	/* Here i should pass the window files to initialise the dynamic windows dw */
	/* for the moment the dw are all the same and hard-coded */
	mcepPst = new HTSPStream(ms.getMcepVsize(), um.getTotalFrame(), HMMData.MCP);
    /* for lf0 count just the number of lf0frames that are voiced or non-zero */
    lf0Pst  = new HTSPStream(ms.getLf0Stream(), um.getLf0Frame(), HMMData.LF0);
    /* The following are optional in case of generating mixed excitation */
    if( htsData.getPdfStrFile() != null)
	  strPst  = new HTSPStream(ms.getStrVsize(), um.getTotalFrame(), HMMData.STR);
    if (htsData.getPdfMagFile() != null )
	  magPst  = new HTSPStream(ms.getMagVsize(), um.getTotalFrame(), HMMData.MAG);
	   
	
	uttFrame = lf0Frame = 0;
	voiced = new boolean[um.getTotalFrame()];
	
	for(i=0; i<um.getNumUttModel(); i++){
        m = um.getUttModel(i);          		
        for(state=0; state<ms.getNumStates(); state++)
      	 for(frame=0; frame<m.getDur(state); frame++) {
      		voiced[uttFrame] = m.getVoiced(state);
      		uttFrame++;
      		if(m.getVoiced(state))
      		  lf0Frame++;
      	 }
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
      	  for(k=0; k<ms.getMcepVsize(); k++){
      		mcepPst.setMseq(uttFrame, k, m.getMcepMean(state, k));
      		mcepPst.setIvseq(uttFrame, k, finv(m.getMcepVariance(state, k)));
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
      	  
      	  if( voiced[uttFrame] )
             lf0Frame++;      	  
      	  uttFrame++;
      	  
      	} /* for each frame in this state */
      } /* for each state in this model */
	}  /* for each model in this utterance */ 
	
	//System.out.println("After copying pdfs to PStreams uttFrame=" + uttFrame + " lf0frame=" + lf0Frame);
	//System.out.println("mseq[" + uttFrame + "][" + k + "]=" + mceppst.get_mseq(uttFrame, k) + "   " + m.get_mcepmean(state, k));
		
	/* parameter generation for mcep */    
	logger.info("Parameter generation for MCEP: ");
    mcepPst.mlpg(htsData, htsData.getUseGV());

    /* parameter generation for lf0 */
    logger.info("Parameter generation for LF0: ");
    if (lf0Frame>0)
      lf0Pst.mlpg(htsData, htsData.getUseGV());
    
	/* parameter generation for str */
    boolean useGV=false;
    if( strPst != null ) {
      logger.info("Parameter generation for STR: ");
      if(htsData.getUseGV() && (htsData.getPdfStrGVFile() != null) )
        useGV = true;
      strPst.mlpg(htsData, useGV);
    }

	/* parameter generation for mag */
    useGV = false;
    if( magPst != null ) {
      logger.info("Parameter generation for MAG: ");
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
             }
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
         F0ReaderWriter.write_pitch_file(fileName, f0s, (float)(ws), (float)(ss), fs);
          
          
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
  
} /* class ParameterGeneration */
