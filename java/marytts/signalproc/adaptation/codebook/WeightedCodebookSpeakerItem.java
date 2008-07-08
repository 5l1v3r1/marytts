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

package marytts.signalproc.adaptation.codebook;

import java.io.IOException;

import marytts.signalproc.adaptation.Context;
import marytts.util.MaryRandomAccessFile;


/**
 * @author oytun.turk
 *
 */
public class WeightedCodebookSpeakerItem {
    public double [] lsfs;
    public double f0;
    public double duration;
    public double energy;
    public String phn;
    public Context context;
    
    public WeightedCodebookSpeakerItem()
    {
       this(0);
    }
    
    public WeightedCodebookSpeakerItem(int lpOrder)
    {
       allocate(lpOrder);
       phn = "";
    }
    
    public void allocate(int lpOrder)
    {
        if (lsfs==null || lpOrder!=lsfs.length)
        {
            if (lpOrder>0)
                lsfs = new double[lpOrder];
            else
                lsfs = null;
        }   
    }
    
    public void setLsfs(double [] lsfsIn)
    {
        if (lsfsIn!=null)
        {
            if (lsfs==null || lsfsIn.length!=lsfs.length)
                allocate(lsfsIn.length);
            
            System.arraycopy(lsfsIn, 0, lsfs, 0, lsfsIn.length);
        }
        else
            lsfs = null;
    }
    
    public void write(MaryRandomAccessFile ler)
    {
        if (lsfs!=null)
        {
            try {
                ler.writeDouble(lsfs);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            try {
                ler.writeDouble(f0);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            try {
                ler.writeDouble(duration);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            try {
                ler.writeDouble(energy);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            int tmpLen = 0;
            
            if (phn!="")
                tmpLen = phn.length();
            
            try {
                ler.writeInt(tmpLen);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            if (tmpLen>0)
            {
                try {
                    ler.writeChar(phn.toCharArray());
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            
            tmpLen = 0;
            
            if (context.allContext!="")
                tmpLen = context.allContext.length();
            
            try {
                ler.writeInt(tmpLen);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            if (tmpLen>0)
            {
                try {
                    ler.writeChar(context.allContext.toCharArray());
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
    
    public void read(MaryRandomAccessFile ler, int lpOrder)
    {
        allocate(lpOrder);
        
        if (lsfs!=null && lpOrder>0)
        {
            try {
                lsfs = ler.readDouble(lpOrder);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            try {
                f0 = ler.readDouble();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            try {
                duration = ler.readDouble();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            try {
                energy = ler.readDouble();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            int tmpLen = 0;
            try {
                tmpLen = ler.readInt();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            phn = "";
            if (tmpLen>0)
            {
                try {
                    phn = String.copyValueOf(ler.readChar(tmpLen));
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            
            tmpLen = 0;
            try {
                tmpLen = ler.readInt();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            context = null;
            if (tmpLen>0)
            {
                try {
                    context = new Context(String.copyValueOf(ler.readChar(tmpLen)));
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
}
