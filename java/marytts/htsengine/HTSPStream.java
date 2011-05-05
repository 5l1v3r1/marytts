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

import marytts.util.MaryUtils;
import java.util.Arrays;

import org.apache.log4j.Logger;


/**
 * Data type and procedures used in parameter generation.
 * Contains means and variances of a particular model, 
 * mcep pdfs for a particular phone for example.
 * It also contains auxiliar matrices used in maximum likelihood 
 * parameter generation.
 * 
 * Java port and extension of HTS engine version 2.0 and GV from HTS version 2.1alpha.
 * Extension: mixed excitation
 * @author Marcela Charfuelan
 */
public class HTSPStream {
	
  public static final int WLEFT = 0;
  public static final int WRIGHT = 1;	
  
  private int feaType;     /* type of features it contains */  
  private int vSize;       /* vector size of observation vector (include static and dynamic features) */
  private int order;       /* vector size of static features */
  private int nT;          /* length, number of frames in utterance */
  private int width;       /* width of dynamic window */
  
  private double par[][];  /* output parameter vector, the size of this parameter is par[nT][vSize] */
  
  
  /* ____________________Matrices for parameter generation____________________ */
  private double mseq[][];   /* sequence of mean vector */
  private double ivseq[][];  /* sequence of inversed variance vector */
  private double g[];        /* for forward substitution */
  private double wuw[][];    /* W' U^-1 W  */
  private double wum[];      /* W' U^-1 mu */
  
  /* ____________________Dynamic window ____________________ */
  private HTSDWin dw;       /* Windows used to calculate dynamic features, delta and delta-delta */

  
  /* ____________________ GV related variables ____________________*/
  /* GV: Global mean and covariance (diagonal covariance only) */
  private double mean, var;  /* mean and variance for current utt eqs: (16), (17)*/
  private int maxGVIter     = 200;      /* max iterations in the speech parameter generation considering GV */
  private double GVepsilon  = 1.0E-4;  //1.0E-4;  /* convergence factor for GV iteration */
  private double minEucNorm = 1.0E-2;  //1.0E-2;  /* minimum Euclid norm of a gradient vector */ 
  private double stepInit   = 0.1;     /* initial step size */  
  private double stepDec    = 0.5;     /* step size deceralation factor */
  private double stepInc    = 1.2;     /* step size acceleration factor */
  private double w1         = 1.0;     /* weight for HMM output prob. */
  private double w2         = 1.0;     /* weight for GV output prob. */
  private double lzero      = (-1.0e+10);  /* ~log(0) */
  double norm=0.0, GVobj=0.0, HMMobj=0.0;
  private boolean gvSwitch[];          /* GV flag sequence, to consider or not the frame in gv */
  private int gvLength;                /* this will be the number of frames for which gv can be calculated */
 
  private Logger logger = MaryUtils.getLogger("PStream");
  
  /* Constructor */
  public HTSPStream(int vector_size, int utt_length, int fea_type, int maxIterationsGV) throws Exception {
	/* In the c code for each PStream there is an InitDwin() and an InitPStream() */ 
	/* - InitDwin reads the window files passed as parameters for example: mcp.win1, mcp.win2, mcp.win3 */
	/*   for the moment the dynamic window is the same for all MCP, LF0, STR and MAG  */
	/*   The initialisation of the dynamic window is done with the constructor. */
	dw = new HTSDWin();
    feaType = fea_type; 
    vSize = vector_size;
    order = vector_size / dw.getNum(); 
    nT = utt_length;
    maxGVIter = maxIterationsGV;
    width = 3;            /* hard-coded to 3, in the c code is:  pst->width = pst->dw.max_L*2+1;  */
                          /* pst->dw.max_L is hard-code to 1, for all windows                     */
    par = new double[nT][order];
    
    /* ___________________________Matrices initialisation___________________ */
	mseq = new double[nT][vSize];
	ivseq = new double[nT][vSize];
	g = new double[nT];
	wuw = new double[nT][width];
	wum = new double[nT];   
	
	/* GV Switch sequence initialisation */
	gvSwitch = new boolean[nT];
	for(int i=0; i<nT; i++)
	  gvSwitch[i] = true;  
	gvLength = nT;  /* at initialisation, all the frames can be used for gv */
    
  }

  public void setVsize(int val){ vSize=val; }
  public int getVsize(){ return vSize; }
  
  public void setOrder(int val){ order=val; }
  public int getOrder(){ return order; }
  
  public void setPar(int i, int j, double val){ par[i][j] = val; }
  public double getPar(int i, int j){ return par[i][j]; }
  public int getT(){ return nT; }
  
  public void setMseq(int i, int j, double val){ mseq[i][j]=val; }
  public double getMseq(int i, int j){ return mseq[i][j]; }
  
  public void setIvseq(int i, int j, double val){ ivseq[i][j]=val; }
  public double getIvseq(int i, int j){ return ivseq[i][j]; }
  
  public void setG(int i, double val){ g[i]=val; }
  public double getG(int i){ return g[i]; }
  
  public void setWUW(int i, int j, double val){ wuw[i][j]=val; }
  public double getWUW(int i, int j){ return wuw[i][j]; }
  
  public void setWUM(int i, double val){ wum[i]=val; }
  public double getWUM(int i){ return wum[i]; }
  
  public int getDWwidth(int i, int j){ return dw.getWidth(i,j); }
  
  public void setGvSwitch(int i, boolean bv){
    if(bv == false)
      gvLength--;
    gvSwitch[i] = bv;
  }
  
  private void printWUW(int t){
	for(int i=0; i<width; i++)
	  System.out.print("WUW[" + t + "][" + i + "]=" + wuw[t][i] + "  ");
	System.out.println(""); 
  }
  
  
  /* mlpg: generate sequence of speech parameter vector maximizing its output probability for 
   * given pdf sequence */
  public void mlpg(HMMData htsData, boolean useGV) {
	 int m;
	 int M = order;
	 boolean debug=false;
 
     htsData.getGVModelSet().setTotalNumIter(0);
     htsData.getGVModelSet().setFirstIter(0);
     
     //if(useGV)
     //  logger.info("Generation using Global Variance maxGVIterations = " + maxGVIter);  
     /*
     for(int t=0; t<nT; t++){
       System.out.format("t=%d  ",t);
       for(int j=0; j<vSize; j++)
         System.out.format("(%d)%f ",  j,ivseq[t][j]);
       System.out.format("\n");
     }
     System.out.format("\n");
     */
     
     //System.out.println("\ngvLength = "+ gvLength + "  maxGVIterations = " + maxGVIter);
	 for (m=0; m<M; m++) {
	   //System.out.println("m=" + m);  
	     
	   calcWUWandWUM( m , debug);
	   ldlFactorization(debug);   /* LDL factorization                               */
	   forwardSubstitution();     /* forward substitution in Cholesky decomposition  */
	   backwardSubstitution(m);   /* backward substitution in Cholesky decomposition */
	          
       /* Global variance optimisation for MCP and LF0 */
       if( useGV && gvLength>0) {
           
         
         
         if(feaType == HMMData.MCP){
           gvParmGen(m, htsData.getGVModelSet(), debug);           
           //logger.info("GV optimization for MCP feature: ("+ m + ")  number of iterations=" + htsData.getGVModelSet().getTotalNumIter());           
           if(debug) 
               logger.info("Total number of iterations = " + htsData.getGVModelSet().getTotalNumIter() + 
                         "  average = " + htsData.getGVModelSet().getTotalNumIter()/M + 
                         "  first iteration = " + htsData.getGVModelSet().getFirstIter() );
          
         }
         if(feaType == HMMData.LF0){
             gvParmGen(m, htsData.getGVModelSet(), debug);
             //logger.info("GV optimization for LF0 feature: ("+ m + ")  number of iterations=" + htsData.getGVModelSet().getTotalNumIter());             
             if(debug) 
                 logger.info("Total number of iterations = " + htsData.getGVModelSet().getTotalNumIter() + 
                           "  average = " + htsData.getGVModelSet().getTotalNumIter()/M + 
                           "  first iteration = " + htsData.getGVModelSet().getFirstIter() );
                           
             
         }
         if(feaType == HMMData.STR){              
             gvParmGen(m, htsData.getGVModelSet(), debug);
             //logger.info("GV optimization for STR feature: ("+ m + ")  number of iterations=" + htsData.getGVModelSet().getTotalNumIter());
             if(debug) 
                 logger.info("Total number of iterations = " + htsData.getGVModelSet().getTotalNumIter() + 
                           "  average = " + htsData.getGVModelSet().getTotalNumIter()/M + 
                           "  first iteration = " + htsData.getGVModelSet().getFirstIter() );
         }
         //if(feaType == HMMData.MAG)  
         //    logger.info("GV optimization for MAG feature: ("+ m + ")");         
         //gvParmGen(m, htsData.getGVModelSet(), debug);  
       
         
       }
	 } 
/*	 
	 if(debug==false) {
	   for(int t=0; t<200; t=t+20){
	     System.out.format("par Frame %d:  ",t);
	     for(int j=0; j<M; j++)
	        System.out.format("%f ",  par[t][j]);
	     System.out.format("\n");
	   }
	 }
 */    
	
	 
  }  /* method mlpg */
  
  
  /*----------------- HTS parameter generation fuctions  -----------------------------*/
  
  /* Calc_WUW_and_WUM: calculate W'U^{-1}W and W'U^{-1}M      
  * W is size W[T][width] , width is width of dynamic window 
  * for the Cholesky decomposition:  A'Ax = A'b              
  * W'U^{-1}W C = W'U^{-1}M                                  
  *        A  C = B   where A = LL'                          
  *  Ly = B , solve for y using forward elimination          
  * L'C = y , solve for C using backward substitution        
  * So having A and B we can find the parameters C.          
  * U^{-1} = inverse covariance : inseq[][]                  */
  /*------ HTS parameter generation fuctions                  */
  /* Calc_WUW_and_WUM: calculate W'U^{-1}W and W'U^{-1}M      */
  /* W is size W[T][width] , width is width of dynamic window */
  /* for the Cholesky decomposition:  A'Ax = A'b              */
  /* W'U^{-1}W C = W'U^{-1}M                                  */
  /*        A  C = B   where A = LL'                          */
  /*  Ly = B , solve for y using forward elimination          */
  /* L'C = y , solve for C using backward substitution        */
  /* So having A and B we can find the parameters C.          */
  /* U^{-1} = inverse covariance : inseq[][]                  */
  private void calcWUWandWUM(int m, boolean debug) {
	int t, i, j, k,iorder;
	double WU;
	double val;
	
	for(t=0; t<nT; t++) {
	  /* initialise */
	  wum[t] = 0.0;
	  for(i=0; i<width; i++)
		wuw[t][i] = 0.0;
	  
	  /* calc WUW & WUM, U is already inverse  */
	    for(i=0; i<dw.getNum(); i++) {
	      iorder = i*order+m;
	      for( j = dw.getWidth(i, WLEFT); j <= dw.getWidth(i, WRIGHT); j++) {

	          if( ( t+j>=0 ) && ( t+j<nT ) && ( dw.getCoef(i,-j)!=0.0 )  ) {
	             
	             //System.out.format("coef[%d,%d]=%f  ivseq[%d][%d]=%f  ", i, -j, dw.getCoef(i,-j),t+j,iorder, ivseq[t+j][iorder]); 
	              
				 WU = dw.getCoef(i,-j) * ivseq[t+j][iorder];
				 
				 wum[t] += WU * mseq[t+j][iorder];
				 
				 //System.out.format("wu=%f wum[%d]=%f iorder=%d\n", WU, t, wum[t], iorder);
				 
				 for(k=0; ( k<width ) && ( t+k<nT ); k++)
				   if( ( k-j<=dw.getWidth(i, 1) ) && ( dw.getCoef(i,(k-j)) != 0.0 ) ) {
				     wuw[t][k] += WU * dw.getCoef(i,(k-j));
				     val = WU * dw.getCoef(i,(k-j));
				   }
			  }
		  }		  
	    }  /* for i */	    
	}  /* for t */
	if(debug){ 
	for(t=0; t<nT; t++) {
	  System.out.format("t=%d wum=%f  wuw:", t, wum[t]); 
      for(k=0; k<wuw[t].length; k++)
        System.out.format("%f ", wuw[t][k]);
      System.out.format("\n");
	}
	System.out.format("\n");
	}
  }
  
  
  /* ldlFactorization: Factorize W'*U^{-1}*W to L*D*L' (L: lower triangular, D: diagonal) */
  /* ldlFactorization: Factorize W'*U^{-1}*W to L*D*L' (L: lower triangular, D: diagonal) */
  private void ldlFactorization(boolean debug) {
	int t,i,j;
	for(t=0; t<nT; t++) {
		
	  if(debug){
	    System.out.println("WUW calculation:");
	    printWUW(t);
	  }
	  
	  /* I need i=1 for the delay in t, but the indexes i in WUW[t][i] go from 0 to 2 
	   * so wherever i is used as index i=i-1  (this is just to keep somehow the original 
	   * c implementation). */
	  for(i=1; (i<width) && (t-i>=0); i++)  
		wuw[t][0] -= wuw[t-i][i+1-1] * wuw[t-i][i+1-1] * wuw[t-i][0];
	  
	  for(i=2; i<=width; i++) {
	    for(j=1; (i+j<=width) && (t-j>=0); j++)
		  wuw[t][i-1] -= wuw[t-j][j+1-1] * wuw[t-j][i+j-1] * wuw[t-j][0];
	    wuw[t][i-1] /= wuw[t][0];
	 
	  }
	  if(debug) {
	    System.out.println("LDL factorization:");
	    printWUW(t);	
	    System.out.println();
	  }
	}
	
  }
  
  /* forward_Substitution */ 
  private void forwardSubstitution() {
	 int t, i;
	 
	 for(t=0; t<nT; t++) {
	   g[t] = wum[t];
	   for(i=1; (i<width) && (t-i>=0); i++)
		 g[t] -= wuw[t-i][i+1-1] * g[t-i];  /* i as index should be i-1 */
	   //System.out.println("  g[" + t + "]=" + g[t]);
	 }
	 /*
	 for(t=0; t<nT; t++)
	   System.out.format("%f ", g[t]);
	 System.out.println(); */
  }
  
  /* backward_Substitution */
  private void backwardSubstitution(int m) {
	 int t, i;
	 
	 for(t=(nT-1); t>=0; t--) {
	   par[t][m] = g[t] / wuw[t][0];
	   for(i=1; (i<width) && (t+i<nT); i++) {
		   par[t][m] -= wuw[t][i+1-1] * par[t+i][m]; /* i as index should be i-1 */
	   }
	   //System.out.println("  par[" + t + "]["+ m + "]=" + par[t][m]); 
	 }
	  
  }

  
  /*----------------- GV functions  -----------------------------*/
  private void gvParmGen(int m, GVModelSet gv, boolean debug){    
    int t,iter;
    double step = stepInit;
    double prev = -lzero;
    double obj=0.0;
    double diag[] = new double[nT];
    double par_ori[] = new double[nT];
    mean=0.0;
    var=0.0;
    int numDown = 0;
    double gvmean[];
    double gvcovInv[];
    
    /* make a copy in case there is problems during optimisation */
    for(t=0; t<nT; t++){
      g[t] = 0.0;
      par_ori[t] = par[t][m];  
    }
    
    if( feaType == HMMData.MCP){
      gvmean = gv.getGVmeanMcp();
      gvcovInv = gv.getGVcovInvMcp();
    } else if( feaType == HMMData.LF0){
      gvmean = gv.getGVmeanLf0();
      gvcovInv = gv.getGVcovInvLf0();
    } else if( feaType == HMMData.STR){
      gvmean = gv.getGVmeanStr();
      gvcovInv = gv.getGVcovInvStr();
    } else {//if( feaType == HMMData.MAG) 
      gvmean = gv.getGVmeanMag();
      gvcovInv = gv.getGVcovInvMag();
    } 
    
    //for (t=0; t<gvmean.length; t++)
    //  System.out.format("gvmean=%f  gvvar=%f\n", gvmean[t], gvcovInv[t]);  
       
    /* first convert c (c=par) according to GV pdf and use it as the initial value */
    convGV(m, gvmean);
    
    /* recalculate R=WUW and r=WUM */
    calcWUWandWUM(m, false);
    
    /* iteratively optimize c */
    for (iter=1; iter<=maxGVIter; iter++) {
      /* calculate GV objective and its derivative with respect to c */
      obj = calcGradient(m, gvmean, gvcovInv);   
      
      /* objective function improved -> increase step size */
      if (obj > prev)
        step *= stepDec;
               
      /* objective function degraded -> go back c and decrese step size */
      if (obj < prev) 
         step *= stepInc;
        
      /* steepest ascent and quasy Newton  c(i+1) = c(i) + alpha * grad(c(i)) */
      for(t=0; t<nT; t++)
        par[t][m] += step * g[t];
      
      //System.out.format("iter=%d  prev=%f  obj=%f \n", iter, prev, obj);
      prev = obj;
    }

 }

  
  private void gvParmGenOLD(int m, GVModelSet gv, boolean debug){    
      int t,iter;
      double step=stepInit;
      double obj=0.0, prev=0.0;
      double diag[] = new double[nT];
      double par_ori[] = new double[nT];
      mean=0.0;
      var=0.0;
      int numDown = 0;
      double gvmean[];
      double gvcovInv[];
      
      /* make a copy in case there is problems during optimisation */
      for(t=0; t<nT; t++){
        g[t] = 0.0;
        par_ori[t] = par[t][m];  
      }
      
      if( feaType == HMMData.MCP){
        gvmean = gv.getGVmeanMcp();
        gvcovInv = gv.getGVcovInvMcp();
      } else if( feaType == HMMData.LF0){
        gvmean = gv.getGVmeanLf0();
        gvcovInv = gv.getGVcovInvLf0();
      } else if( feaType == HMMData.STR){
        gvmean = gv.getGVmeanStr();
        gvcovInv = gv.getGVcovInvStr();
      } else {//if( feaType == HMMData.MAG) 
        gvmean = gv.getGVmeanMag();
        gvcovInv = gv.getGVcovInvMag();
      } 
         
      /* first convert c (c=par) according to GV pdf and use it as the initial value */
      convGV(m, gvmean);
      
      /* recalculate R=WUW and r=WUM */
      calcWUWandWUM(m, false);
      
      /* iteratively optimize c */
      for (iter=1; iter<=maxGVIter; iter++) {
        /* calculate GV objective and its derivative with respect to c */
        obj = calcGradient(m, gvmean, gvcovInv);   
        /* accelerate/decelerate step size */
        if(iter > 1) { 
          /* objective function improved -> increase step size */
          if (obj > prev){
            step *= stepInc;
            //logger.info("+++ obj > prev iter=" + iter +"  obj=" + obj + "  > prev=" + prev);
            numDown = 0;
          }      
          /* objective function degraded -> go back c and decrese step size */
          if (obj < prev) {
             for (t=0; t<nT; t++)  /* go back c=par to that at the previous iteration */
                par[t][m] -= step * diag[t];
             step *= stepDec;
             for (t=0; t<nT; t++)  /* gradient c */
                par[t][m] += step * diag[t];
             iter--;
             numDown++;
             //logger.info("--- obj < prev iter=" + iter +"  obj=" + obj + "  < prev=" + prev +"  numDown=" + numDown);
             if(numDown < 100)
              continue;
             else {
               logger.info("  ***Convergence problems....optimization stopped. Number of iterations: " + iter );
               break;
             }
          }         
        } else {
         if(debug)   
           logger.info("  First iteration:  GVobj=" + obj + " (HMMobj=" + HMMobj + "  GVobj=" + GVobj + ")");
        }     
        /* convergence check (Euclid norm, objective function) */
        if(norm < minEucNorm || (iter > 1 && Math.abs(obj-prev) < GVepsilon )){
          if(debug)  
            logger.info("  Number of iterations: [   " + iter + "   ] GVobj=" + obj + " (HMMobj=" + HMMobj + "  GVobj=" + GVobj + ")");
          gv.incTotalNumIter(iter);
          if(m==0)
            gv.setFirstIter(iter);
          if(debug){
            if(iter > 1 )  
              logger.info("  Converged (norm=" + norm + ", change=" + Math.abs(obj-prev) + ")");
            else            
              logger.info("  Converged (norm=" + norm + ")");
          }
          break;
        }    
        /* steepest ascent and quasy Newton  c(i+1) = c(i) + alpha * grad(c(i)) */
        for(t=0; t<nT; t++){
          par[t][m] += step * g[t];
          diag[t] = g[t];
        }
        prev = obj;       
      }   
      if( iter>maxGVIter ){
        //logger.info("");  
        //logger.info("  Number of iterations: " + maxGVIter + " GVobj=" + obj + " (HMMobj=" + HMMobj + "  GVobj=" + GVobj + ")");
        logger.info("   optimization stopped by reaching max number of iterations (no global variance applied)");
        //logger.info("");

        /* If there it does not converge, the feature parameter is not optimized */
        for(t=0; t<nT; t++){
          par[t][m] = par_ori[t];  
        }      
      }
      gv.setTotalNumIter(iter);     
    }
 
  
  private double calcGradientOLD(int m, double gvmean[], double gvcovInv[]){
   int t, i,k; 
   double vd;
   double h, aux;
   double w = 1.0 / (dw.getNum() * nT);
   
   /* recalculate GV of the current c = par */
   calcGV(m);   
   
   /* GV objective function and its derivative with respect to c */
   /* -1/2 * v(c)' U^-1 v(c) + v(c)' U^-1 mu + K  --> second part of eq (20) in Toda and Tokuda IEICE-2007 paper.*/
   GVobj =  -0.5 * w2 * (var - gvmean[m]) * gvcovInv[m] * (var - gvmean[m]);
   vd = gvcovInv[m] * (var - gvmean[m]);
     
   /* calculate g = R*c = WUW*c*/
   for(t=0; t<nT; t++) {
     g[t] = wuw[t][0] * par[t][m];
     for(i=2; i<=width; i++){   /* width goes from 0 to 2  width=3 */
       if( t+i-1 < nT)
         g[t] += wuw[t][i-1] * par[t+i-1][m];      /* i as index should be i-1 */
       if( t-i+1 >= 0 )
         g[t] += wuw[t-i+1][i-1] * par[t-i+1][m];  /* i as index should be i-1 */
     }   
   }
      
   for(t=0, HMMobj=0.0, norm=0.0; t<nT; t++) {
       
     HMMobj += -0.5 * w1 * w * par[t][m] * (g[t] - 2.0 * wum[t]); 
       
     /* case STEEPEST: do not use hessian */
     //h = 1.0;
     /* case NEWTON */
     /* only diagonal elements of Hessian matrix are used */
     h = ( ( nT-1) * vd + 2.0 * gvcovInv[m] * (par[t][m] - mean) * (par[t][m] - mean) );
     h = -w1 * w * wuw[t][1-1] - w2 * 2.0 / (nT*nT) * h;
     
     h = -1.0/h;
       
     /* gradient vector */
     if(gvSwitch[t]) {
       aux = (par[t][m] - mean ) * vd;        
       g[t] = h * ( w1 * w *(-g[t] + wum[t]) + w2 * -2.0/nT * aux );
     } else 
       g[t] = h * ( w1 * w *(-g[t] + wum[t]) );  
     
     /*  Euclidian norm of gradient vector */  
     norm += g[t]*g[t];
       
   }
     
   norm = Math.sqrt(norm);
   //logger.info("HMMobj=" + HMMobj + "  GVobj=" + GVobj + "  norm=" + norm);
   
   return(HMMobj+GVobj);  
   
  }

  private double calcGradient(int m, double gvmean[], double gvcovInv[]){
      int t, i,k; 
      double vd;
      double h, aux;
      double w = 1.0 / (dw.getNum() * nT);
      
      /* recalculate GV of the current c = par */
      calcGV(m);   
      
      /* GV objective function and its derivative with respect to c */
      /* -1/2 * v(c)' U^-1 v(c) + v(c)' U^-1 mu + K  --> second part of eq (20) in Toda and Tokuda IEICE-2007 paper.*/
      GVobj =  -0.5 * w2 * var * gvcovInv[m] * (var - 2.0 * gvmean[m]);
      vd = -2.0 * gvcovInv[m] * (var - gvmean[m])/nT;
      //System.out.format("GVobj=%f  vd=%f \n", GVobj, vd);  
      
      /* calculate g = R*c = WUW*c*/
      for(t=0; t<nT; t++) {
        g[t] = wuw[t][0] * par[t][m];
        for(i=2; i<=width; i++){   /* width goes from 0 to 2  width=3 */
          if( t+i-1 < nT)
            g[t] += wuw[t][i-1] * par[t+i-1][m];      /* i as index should be i-1 */
          if( t-i+1 >= 0 )
            g[t] += wuw[t-i+1][i-1] * par[t-i+1][m];  /* i as index should be i-1 */
        }   
      }
         
      for(t=0, HMMobj=0.0; t<nT; t++) {
          
        HMMobj += w1 * w * par[t][m] * (wum[t] - 0.5 * g[t]); 
 
        h = -w1 * w * wuw[t][1-1] - w2 * 2.0 / (nT*nT) * ( (nT-1) * gvcovInv[m] * (var - gvmean[m]) + 2.0 * gvcovInv[m] * (par[t][m] - mean) * (par[t][m] - mean) ); 
  
        //System.out.format("HMMobj=%f  h=%f \n", HMMobj, h);
        /* gradient vector */
        if(gvSwitch[t]) {
          g[t] = 1.0 / h * ( w1 * w *(-g[t] + wum[t]) + w2 * vd * (par[t][m] - mean) );  

        } else 
          g[t] = 1.0 / h * ( w1 * w *(-g[t] + wum[t]) );  
                  
      }
              
      return(-(HMMobj+GVobj));  
      
     }
  
  
  private void convGV(int m, double gvmean[]){
    int t, k;
    double ratio, mixmean; 
    /* calculate GV of c */
    calcGV(m);
       
    ratio = Math.sqrt(gvmean[m] / var);
    //System.out.format("    mean=%f vari=%f ratio=%f \n", mean, var, ratio);
   
    /* c'[t][d] = ratio * (c[t][d]-mean[d]) + mean[d]  eq. (34) in Toda and Tokuda IEICE-2007 paper. */  
    for(t=0; t<nT; t++){
     if( gvSwitch[t] )
       par[t][m] = ratio * ( par[t][m]-mean ) + mean;
    }
      
  }
  
  private void calcGV(int m){
    int t, i;
    mean=0.0;
    var=0.0;
 
    /* mean */
    for(t=0; t<nT; t++)
      if(gvSwitch[t]){
        mean += par[t][m];
        //System.out.format("%f ", par[t][m]);
      }
    //System.out.format("\n");
    mean = mean / gvLength;
      
    /* variance */  
    for(t=0; t<nT; t++)
      if(gvSwitch[t])
        var += (par[t][m] - mean) * (par[t][m] - mean);
    var = var / gvLength;
      
  }
  
 
  

} /* class PStream */
