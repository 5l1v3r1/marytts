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

package de.dfki.lt.mary.htsengine;


/**
 * Data type and procedures used in parameter generation.
 * Contains means and variances of a particular model, 
 * mcep pdfs for a particular phoneme for example.
 * It also contains auxiliar matrices used in maximum likelihood 
 * parameter generation.
 * 
 * Java port and extension of HTS engine version 2.0
 * Extension: mixed excitation
 * @author Marcela Charfuelan
 */
public class PStream {
	
  public static final int WLEFT = 0;
  public static final int WRIGHT = 1;	
	  
  private int vSize;       /* vector size of observation vector (include static and dynamic features) */
  private int order;       /* vector size of static features */
  private int nT;           /* length, number of frames in utterance */
  private int width;       /* width of dynamic window */
  
  private double par[][];  /* output parameter vector, the size of this parameter is par[T][vSize] */ 
  
  /* ____________________Matrices for parameter generation____________________ */
  private double mseq[][];   /* sequence of mean vector */
  private double ivseq[][];  /* sequence of inversed variance vector */
  private double g[];        /* for forward substitution */
  private double wuw[][];    /* W' U^-1 W  */
  private double wum[];      /* W' U^-1 mu */
  
  /* ____________________Dynamic window ____________________ */
  private DWin dw;         
 // private int num;          /* number of static + deltas, number of window files */
  // private String fn;     /* delta window coefficient file */
 // private int dw_width[][]; /* width [0..num-1][0(left) 1(right)] */
 // private double coef[][];  /* coefficient [0..num-1][length[0]..length[1]] */
 // private int maxw[];       /* max width [0(left) 1(right)] */
 // private int max_L;        /* max {maxw[0], maxw[1]} */
 
  
  /* Constructor */
  public PStream(int vector_size, int utt_length) {
	/* in the c code for each PStream there is an InitDwin() and an InitPStream() */ 
	/* - InitDwin reads the window files passed as parameters for example: mcp.win1, mcp.win2, mcp.win3 */
	/*   for the moment the dynamic window is the same for all MCP, LF0, STR and MAG  */
	/*   The initialisation of the dynamic window is done with the constructor. */
	/* - InitPstream does the same as it is done here with the SMatrices constructor. */
	dw = new DWin();
    vSize = vector_size;
    order = vector_size / dw.getNum(); 
    nT = utt_length;
    width = 3;            /* hard-coded to 3, in the c code is:  pst->width = pst->dw.max_L*2+1;  */
                          /* pst->dw.max_L is hard-code to 1, for all windows                     */
    par = new double[nT][order];
   // sm = new SMatrices(T, vSize, width, order);
    
    /* ___________________________Matrices initialisation___________________ */
	mseq = new double[nT][vSize];
	ivseq = new double[nT][vSize];
	g = new double[nT];
	wuw = new double[nT][width];
	wum = new double[nT];
	
  }

  public void setOrder(int val){ order=val; }
  public int getOrder(){ return order; }
  
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
  
  private void printWUW(int t){
	for(int i=0; i<width; i++)
	  System.out.print("WUW[" + t + "][" + i + "]=" + wuw[t][i] + "  ");
	System.out.println(""); 
  }
  
  /* mlpg: generate sequence of speech parameter vector maximizing its output probability for 
   * given pdf sequence */
  public void mlpg() {
	 int m,t;
	 int M = order;
	 boolean debug=false;

	 for (m=0; m<M; m++) {
	   calcWUWandWUM( m , debug);
	   ldlFactorization(debug);   /* LDL factorization                               */
	   forwardSubstitution();     /* forward substitution in Cholesky decomposition  */
	   backwardSubstitution(m);   /* backward substitution in Cholesky decomposition */
	 } 
	 if(debug) {
	   for(m=0; m<M; m++){
	     for(t=0; t<4; t++)
		   System.out.print("par[" + t + "][" + m + "]=" + par[t][m] + " ");
	     System.out.println();
	   }
	   System.out.println();
	 }
	 
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
	      if(debug)
	        System.out.println("WIN: " + i);
	      iorder = i*order+m;
	      for( j = dw.getWidth(i, WLEFT); j <= dw.getWidth(i, WRIGHT); j++) {
	    	 if(debug) 
	    	   System.out.println("  j=" + j + " t+j=" + (t+j) + " iorder=" + iorder + " coef["+i+"]["+(-j)+"]="+dw.getCoef(i,-j));
			 if( ( t+j>=0 ) && ( t+j<nT ) && ( dw.getCoef(i,-j)!=0.0 )  ) {
				 WU = dw.getCoef(i,-j) * ivseq[t+j][iorder];
				 if(debug)
				   System.out.println("  WU = coef[" + i +"][" + j + "] * ivseq[" + (t+j) + "][" + iorder + "]"); 
				 wum[t] += WU * mseq[t+j][iorder];
				 if(debug)
				   System.out.println("  WUM[" + t + "] += WU * mseq[" + t+j + "][" + iorder + "]   WU*mseq=" + WU * mseq[t+j][iorder]); 
				 
				 for(k=0; ( k<width ) && ( t+k<nT ); k++)
				   if( ( k-j<=dw.getWidth(i, 1) ) && ( dw.getCoef(i,(k-j)) != 0.0 ) ) {
				     wuw[t][k] += WU * dw.getCoef(i,(k-j));
				     val = WU * dw.getCoef(i,(k-j));
				     if(debug)
				       System.out.println("  WUW[" + t + "][" + k + "] += WU * coef[" + i + "][" + (k-j) + "]   WU*coef=" + val);
				   }
			  }
		  }		  
	    }  /* for i */
	    if(debug) {
	      System.out.println("------------\nWUM[" + t + "]=" + wum[t]); 
	      printWUW(t);
	      System.out.println("------------\n");
	    }
	    
	}  /* for t */
	  
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
  
  
  

} /* class PStream */
