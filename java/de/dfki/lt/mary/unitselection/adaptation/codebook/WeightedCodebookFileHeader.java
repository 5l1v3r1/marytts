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

package de.dfki.lt.mary.unitselection.adaptation.codebook;

import java.io.IOException;

import de.dfki.lt.signalproc.analysis.EnergyAnalyserRms;
import de.dfki.lt.signalproc.analysis.EnergyFileHeader;
import de.dfki.lt.signalproc.analysis.LsfFileHeader;
import de.dfki.lt.signalproc.analysis.PitchFileHeader;
import de.dfki.lt.signalproc.util.MaryRandomAccessFile;

/**
 * @author oytun.turk
 *
 */
public class WeightedCodebookFileHeader {
    public int totalLsfEntries;
    public int totalF0StatisticsEntries;
    
    //Codebook type
    public int codebookType;
    public static int FRAMES = 1; //Frame-by-frame mapping of features
    public static int FRAME_GROUPS = 2; //Mapping of frame average features (no label information but fixed amount of neighbouring frames is used)
    public static int LABELS = 3; //Mapping of label average features
    public static int LABEL_GROUPS = 4; //Mapping of average features collected across label groups (i.e. vowels, consonants, etc)
    public static int SPEECH = 5; //Mapping of average features collected across all speech parts (i.e. like spectral equalization)
    //
    
    public String sourceTag; //Source name tag (i.e. style or speaker identity)
    public String targetTag; //Target name tag (i.e. style or speaker identity)
    
    public LsfFileHeader lsfParams;
    public PitchFileHeader ptcParams;
    public EnergyFileHeader energyParams;
    
    public int numNeighboursInFrameGroups; //Functional only when codebookType == FRAME_GROUPS
    public int numNeighboursInLabelGroups; //Functional only when codebookType == LABEL_GROUPS
    
    public WeightedCodebookFileHeader()
    {
        this(0, 0);
    } 
    
    public WeightedCodebookFileHeader(int totalEntriesIn, int totalF0StatisticsEntriesIn)
    {
        totalLsfEntries = totalEntriesIn;
        totalF0StatisticsEntries = totalF0StatisticsEntriesIn;
        
        codebookType = FRAMES;
        
        sourceTag = "source"; //Source name tag (i.e. style or speaker identity)
        targetTag = "target"; //Target name tag (i.e. style or speaker identity)
        
        lsfParams = new LsfFileHeader();
        ptcParams = new PitchFileHeader();
        energyParams = new EnergyFileHeader();
    } 
    
    public WeightedCodebookFileHeader(WeightedCodebookFileHeader h)
    {
        totalLsfEntries = h.totalLsfEntries;
        totalF0StatisticsEntries = h.totalF0StatisticsEntries;
        
        codebookType = h.codebookType;
        
        sourceTag = h.sourceTag;
        targetTag = h.targetTag;
        
        lsfParams = new LsfFileHeader(h.lsfParams);
        ptcParams = new PitchFileHeader(h.ptcParams);
        energyParams = new EnergyFileHeader(h.energyParams);
        
        numNeighboursInFrameGroups = h.numNeighboursInFrameGroups;
        numNeighboursInLabelGroups = h.numNeighboursInLabelGroups;
    } 
    
    public void resetTotalEntries()
    {
        totalLsfEntries = 0;
        totalF0StatisticsEntries = 0;
    }

    public void read(MaryRandomAccessFile ler) throws IOException
    {   
        totalLsfEntries = ler.readInt();
        totalF0StatisticsEntries = ler.readInt();
        
        lsfParams = new LsfFileHeader();
        lsfParams.readLsfHeader(ler);
        
        ptcParams = new PitchFileHeader();
        ptcParams.readPitchHeader(ler);
        
        energyParams = new EnergyFileHeader();
        energyParams.read(ler, true);
        
        codebookType = ler.readInt();
        numNeighboursInFrameGroups = ler.readInt();
        numNeighboursInLabelGroups = ler.readInt();
        
        int tagLen = ler.readInt();
        sourceTag = String.copyValueOf(ler.readChar(tagLen));
        tagLen = ler.readInt();
        targetTag = String.copyValueOf(ler.readChar(tagLen));
    }
    
    public void write(MaryRandomAccessFile ler) throws IOException
    {
        ler.writeInt(totalLsfEntries);
        ler.writeInt(totalF0StatisticsEntries);
        
        lsfParams.writeLsfHeader(ler);
        ptcParams.writePitchHeader(ler);
        energyParams.write(ler);
        
        ler.writeInt(codebookType);
        ler.writeInt(numNeighboursInFrameGroups);
        ler.writeInt(numNeighboursInLabelGroups);
        
        int tagLen = sourceTag.length();
        ler.writeInt(tagLen);
        ler.writeChar(sourceTag.toCharArray());

        tagLen = targetTag.length();
        ler.writeInt(tagLen);
        ler.writeChar(targetTag.toCharArray());
    }
}
