package de.dfki.lt.mary.util;

import java.io.File;
import java.util.Arrays;
import java.util.StringTokenizer;

import de.dfki.lt.signalproc.util.ESTLabel;
import de.dfki.lt.signalproc.util.ESTLabels;

public class StringUtil {
    
    //Removes blanks in the beginning and at the end of a string
    public static String deblank(String str) 
    {
          StringTokenizer s = new StringTokenizer(str," ",false);
          String strRet = "";
          
          while (s.hasMoreElements()) 
              strRet += s.nextElement();
          
          return strRet;
    }
    
    //Converts a String to a float
    public static float String2Float(String str)
    {
        return Float.valueOf(str).floatValue();
    }
    
    //Converts a String to a double
    public static double String2Double(String str)
    {
        return Double.valueOf(str).doubleValue();
    }
    
    //Converts a String to an int
    public static int String2Int(String str)
    {
        return Integer.valueOf(str).intValue();
    }
      
    //Find indices of multiple occurrences of a character in a String
    public static int[] find(String str, char ch, int stInd, int enInd)
    {
        int [] indices = null;
        int i;
        int count = 0;
        
        if (stInd<0)
            stInd = 0;
        if (stInd>str.length()-1)
            stInd=str.length()-1;
        if (enInd<stInd)
            enInd=stInd;
        if (enInd>str.length()-1)
            enInd=str.length()-1;
        
        for (i=stInd; i<=enInd; i++)
        {
            if (str.charAt(i)==ch)
                count++;
        }
        
        if (count>0)
            indices = new int[count];
        
        int total = 0;
        for (i=stInd; i<=enInd; i++)
        {
            if (str.charAt(i)==ch && total<count)
                indices[total++] = i; 
        }
        
        return indices;
    }
    
    public static int[] find(String str, char ch, int stInd)
    {
        return find(str, ch, stInd, str.length()-1);
    }
    
    public static int[] find(String str, char ch)
    {
        return find(str, ch, 0, str.length()-1);
    }
    
    //Check last folder separator character and append it if it does not exist
    public static String checkLastSlash(String strIn)
    {
        String strOut = strIn;
        
        char last = strIn.charAt(strIn.length()-1);
        
        if (last != File.separatorChar)
            strOut = strOut + File.separatorChar;
        
        return strOut;
    }
    
   //Check first file extension separator character and add it if it does not exist
    public static String checkFirstDot(String strIn)
    {
        String strOut = strIn;
        
        char extensionSeparator = '.';
        
        char first = strIn.charAt(0);
        
        if (first != extensionSeparator)
            strOut = extensionSeparator + strOut;
        
        return strOut;
    }
    
    //Default start index is 1
    public static String[] indexedNameGenerator(String preName, int numFiles)
    {
        return indexedNameGenerator(preName, numFiles, 1);
    }
    
    public static String[] indexedNameGenerator(String preName, int numFiles, int startIndex)
    {
        return indexedNameGenerator(preName, numFiles, startIndex, "");
    }
    
    public static String[] indexedNameGenerator(String preName, int numFiles, int startIndex, String postName)
    {
        return indexedNameGenerator(preName, numFiles, startIndex, postName, ".tmp");
    }
    
    public static String[] indexedNameGenerator(String preName, int numFiles, int startIndex, String postName, String extension)
    {
        int numDigits = 0;
        if (numFiles>0)
            numDigits = (int)Math.floor(Math.log10(startIndex+numFiles-1));
        
        return indexedNameGenerator(preName, numFiles, startIndex, postName, extension, numDigits);
    }
    
    //Generate a list of files in the format:
    // <preName>startIndex<postName>.extension
    // <preName>startIndex+1<postName>.extension
    // <preName>startIndex+2<postName>.extension
    // ...
    // The number of required characters for the largest index is computed automatically if numDigits<required number of characters for the largest index
    // The minimum value of startIndex is 0 (negative values are converted to zero)
    public static String[] indexedNameGenerator(String preName, int numFiles, int startIndex, String postName, String extension, int numDigits)
    {
        String[] fileList = null;
        
        if (numFiles>0)
        {
            if (startIndex<0)
                startIndex = 0;
            
            int tmpDigits = (int)Math.floor(Math.log10(startIndex+numFiles-1));
            if (tmpDigits>numDigits)
                numDigits=tmpDigits;
            
            fileList = new String[numFiles];
  
            String strNum;

            for (int i=startIndex; i<startIndex+numFiles; i++)
            {
                strNum = String.valueOf(i);
                
                //Add sufficient 0´s in the beginning
                while (strNum.length()<numDigits)
                    strNum = "0" + strNum;
                //
                
                fileList[i-startIndex] = preName + strNum + postName + extension;
            }
        }
        
        return fileList;
    }
    
    public static String modifyExtension(String strFilename, String desiredExtension)
    {
        String strNewname = strFilename;
        String desiredExtension2 = checkFirstDot(desiredExtension);
        
        int lastDotIndex = strNewname.lastIndexOf('.');
        strNewname = strNewname.substring(0, lastDotIndex) + desiredExtension2;
        
        return strNewname;
    }
    
    
    //This version assumes that there can only be insertions and deletions but no substitutions 
    // (i.e. text based alignment with possible differences in pauses only)
    public static int[][] alignLabels(ESTLabel[] seq1, ESTLabel[] seq2)
    {
        return alignLabels(seq1, seq2, 0.05, 0.05, 0.05);
    }
    
    public static int[][] alignLabels(ESTLabel[] labs1, ESTLabel[] labs2, double PDeletion, double PInsertion, double PSubstitution)
    {
        double PCorrect = 1.0-(PDeletion+PInsertion+PSubstitution);
        int n = labs1.length;
        int m = labs2.length;
        double D;
        int[][] labelMap = null;

        if (n==0 || m==0)
        {
            D=m;
            return labelMap;
        }

        int i, j;
        double[][] d = new double[n+1][m+1];
        for (i=0; i<d.length; i++)
        {
            for (j=0; j<d[i].length; j++)
                d[i][j] = 0.0;
        }

        int[][] p = new int[n+1][m+1];
        for (i=0; i<p.length; i++)
        {
            for (j=0; j<p[i].length; j++)
                p[i][j] = 0;
        }

        double z = 1;
        d[0][0] = z;
        for (i=1; i<=n; i++)
            d[i][0] = d[i-1][0]*PDeletion;

        for (j=1; j<=m; j++)
            d[0][j] = d[0][j-1]*PInsertion;

        String strEvents = "DISC";
        double c;
        double tmp;
        for (i=1; i<=n; i++)
        {
            for (j=1; j<=m; j++)
            {
                if (labs1[i-1].phn.compareTo(labs2[j-1].phn)==0)
                    c = PCorrect;
                else
                    c = PSubstitution;

                int ind = 1;
                d[i][j] = d[i-1][j]*PDeletion;
                tmp = d[i][j-1]*PInsertion;
                if (tmp>d[i][j])
                {
                    d[i][j] = tmp;
                    ind = 2;
                }

                tmp = d[i-1][j-1]*c;
                if (tmp>d[i][j])
                {
                    d[i][j] = tmp;
                    ind = 3;
                }

                if (ind==3 && labs1[i-1].phn.compareTo(labs2[j-1].phn)==0)
                    ind = 4;

                //Events 1:Deletion, 2:Insertion, 3:Substitution, 4:Correct
                p[i][j] = ind;
            }
        }

        //Backtracking
        D = d[n][m];
        int k = 1;
        int[] E = new int[m*n];
        E[k-1] = p[n][m];
        i=n+1;
        j=m+1;
        int t=m;
        while (true)
        {
            if (E[k-1]==3 || E[k-1]==4)
            {
                i=i-1;
                j=j-1;
            }
            else if (E[k-1]==2)
                j=j-1;
            else if (E[k-1]==1)
                i=i-1;

            if (p[i-1][j-1]==0)
            {
                while (j>1)
                {    
                    k=k+1;
                    j=j-1;
                    E[k-1]=2;
                }
                break;
            }
            else
            {
                k=k+1;
                E[k-1]=p[i-1][j-1];
            }
            t=t-1;
        }

        //Reverse the order
        int[] Events = new int[k];
        for (t=k; t>=1; t--)
            Events[t-1] = E[k-t];
        
        int[][] tmpLabelMap = new int[n*m][2];
        int ind = 0;
        int ind1 = 0;
        int ind2 = 0;
        for (t=1; t<=k; t++)
        {
            if (Events[t-1]==3 || Events[t-1]==4) //Substitution or correct
            {
                tmpLabelMap[ind][0] = ind1;
                tmpLabelMap[ind][1] = ind2;
                ind1++;
                ind2++;
                ind++;
            }
            else if (Events[t-1]==1) //An item in seq1 is deleted in seq2
            {
                ind1++;
            }
            else if (Events[t-1]==2) //An item is inserted in seq2
            {
                ind2++;
            }
        }
        
        if (ind>0)
        {
            labelMap = new int[ind][2];
            for (i=0; i<labelMap.length; i++)
            {
                labelMap[i][0] = tmpLabelMap[i][0];
                labelMap[i][1] = tmpLabelMap[i][1];
            }
        }

        return labelMap;
    }
    
    public static int findInMap(int[][] map, int ind1)
    {
        for (int i=0; i<map.length; i++)
        {
            if (map[i][0]==ind1)
                return map[i][1];
        }
        
        return -1;
    }
    
    public static boolean isNumeric(String str) 
    {
        for (int i=0; i<str.length(); i++)
        {
            char ch = str.charAt(i);
            if (!Character.isDigit(ch) && ch!='.') 
                return false;
        }
        
        return true;
    }
    
    //Retrieves filename from fullpathname
    // Also works for removing file extension from a filename with extension
    public static String getFileName(String fullpathFilename, boolean bRemoveExtension)
    {
        String filename = "";
        
        int ind1 = fullpathFilename.lastIndexOf('\\');
        int ind2 = fullpathFilename.lastIndexOf('/');
        
        ind1 = Math.max(ind1, ind2);
        
        if (ind1>=0 && ind1<fullpathFilename.length()-2)
            filename = fullpathFilename.substring(ind1+1);
        
        if (bRemoveExtension)
        {
            ind1 = filename.lastIndexOf('.');
            if (ind1>0 && ind1-1>=0)
                filename = filename.substring(0, ind1);
        }
        
        return filename;
    }
    
    public static String getFileName(String fullpathFilename)
    {
        return getFileName(fullpathFilename, true);
    }
    
    public static String getFolderName(String fullpathFilename)
    {
        String foldername = "";
        
        int ind1 = fullpathFilename.lastIndexOf('\\');
        int ind2 = fullpathFilename.lastIndexOf('/');
        
        ind1 = Math.max(ind1, ind2);
        
        if (ind1>=0 && ind1<fullpathFilename.length()-2)
            foldername = fullpathFilename.substring(0, ind1+1);
        
        return foldername;
    }
    
    public static void main(String[] args)
    {
        ESTLabels l = new ESTLabels("D:\\1\\neutral50\\test_tts\\neutral.lab");
        FestivalUtt f = new FestivalUtt("D:\\1\\neutral50\\test_tts\\neutral.tutt");
        
        double PDeletion = 0.05;
        double PInsertion = 0.05; 
        double PSubstitution = 0.05;
        int[][] map = alignLabels(l.items, f.labels[0].items, PDeletion, PInsertion, PSubstitution);
        
        for (int i=0; i<map.length; i++)
            System.out.println(String.valueOf(map[i][0]) + "->" + String.valueOf(map[i][1]));
    }
}
