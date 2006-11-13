/**
 * Copyright 2006 DFKI GmbH.
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
package de.dfki.lt.mary.unitselection.voiceimport;

import java.io.*;
import java.util.*;
import java.text.DecimalFormat;

import de.dfki.lt.mary.unitselection.voiceimport.SphinxTrainer.StreamGobbler;


/**
 * Preparate the directory of the voice for sphinx labelling
 * @author Anna Hunecke
 */
public class SphinxLabeler implements VoiceImportComponent {
    
    private DatabaseLayout dbLayout;
    
    /**
     * Create new LabelingPreparator
     * 
     * @param dbLayout the database layout
     * @param baseNames the list of file base names
     */
    public SphinxLabeler(DatabaseLayout dbLayout){
        this.dbLayout = dbLayout;
    }
    
    /**
     * Do the computations required by this component.
     * 
     * @return true on success, false on failure
     */
    public boolean compute() throws Exception{
        
        System.out.println("Preparing voice database for labelling");
        /* get the directories of sphinxtrain and edinburgh speech tools */
        String sphinx2dir = System.getProperty("SPHINX2DIR");
        if ( sphinx2dir == null ) {
            System.out.println( "Warning: The environment variable SPHINX2DIR was not found on your system." );
            System.out.println( "         Defaulting SPHINX2DIR to [ /project/mary/anna/sphinx/sphinx2/ ]." );
            sphinx2dir = "/project/mary/anna/sphinx/sphinx2/";
        }
        
        //get voicename and root dir name
        //get the root dir and the voicename
        File rootDirFile = new File(dbLayout.rootDirName());
        String rootDirName = rootDirFile.getCanonicalPath();
        String voicename = rootDirName.substring(rootDirName.lastIndexOf("/")+1);
        
        
        /* Sphinx2 variables */
        System.out.println("Calling Sphinx2 ...");
        //model directory
        String hmm = "st/model_parameters/"+voicename+".s2models/";
        
        // the 'task' 
        String task= "st/wav";
        
        //dictionary and silence-symbol files
        String dictfile = "st/etc/"+voicename+".dic";
        String ndictfile = "st/etc/"+voicename+".sil";

        //list of filenames
        String ctlfile = "st/etc/"+voicename+".fileids";

        //the transcription file
        String tactlfn = "st/etc/"+voicename+".align";
        
        /* Run Sphinx2 */
        Runtime rtime = Runtime.getRuntime();        
        //get a shell
        Process process = rtime.exec("/bin/bash");
        //get an output stream to write to the shell
        PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(process.getOutputStream()));
        //go to voice directory
        pw.print("cd "+rootDirName+"\n");
        pw.flush();
        //call Sphinx2 and exit
        pw.print("( "+sphinx2dir+"build/bin/sphinx2-batch -adcin TRUE -adcext wav "
                +"-ctlfn "+ctlfile+" -tactlfn "+tactlfn+" -ctloffset 0"
                +"-ctlcount 100000000 -datadir wav -agcmax FALSE "
                +"-langwt 6.5 -fwdflatlw 8.5 -rescorelw 9.5 -ugwt 0.5"
                +"-fillpen 1e-10 -silpen 1e-10 -inspen 0.65 -topn 5"
                +"-topsenfrm 3 -topsenthresh -70000 -beam 2e-90 "
                +"-npbeam 2e-90 -lpbeam 2e-90 -lponlybeam 0.0005 "
                +"-nwbeam 0.0005 -fwdflat FALSE -fwdflatbeam 1e-08 "
                +"-fwdflatnwbeam 0.0003 -bestpath TRUE -kbdumpdir "+task+" "
                +"-dictfn "+dictfile+" -fdictfn "+ndictfile+" "
                +"-phnfn "+hmm+"/phone -mapfn "+hmm+"/map -hmmdir "+hmm+" "
                +"-hmmdirlist "+hmm+" -8bsen TRUE -sendumpfn "+hmm+"/sendump"
                +" -cbdir "+hmm+" -phonelabdir st/lab"    
                +"; exit)\n");
        pw.flush();
        pw.close();
        
//      collect the output
        //any error message?
        StreamGobbler errorGobbler = new 
            StreamGobbler(process.getErrorStream(), "err");            
        
        //any output?
        StreamGobbler outputGobbler = new 
            StreamGobbler(process.getInputStream(), "out");
            
        //kick them off
        errorGobbler.start();
        outputGobbler.start();
        
        //shut down
        process.waitFor();
        process.exitValue();    
        System.out.println("... done.");
        
        /* Write the labels into lab directory */
        System.out.println("Exporting Labels ...");
        //lab destination directory
        String labDestDir = dbLayout.labDirName();
        String labExtension = dbLayout.labExt();
        //used to prune the times to 5 positions behind .
        DecimalFormat df = new DecimalFormat( "0.00000" );
        String line;
        //go through original lab files
        File[] labFiles = new File(rootDirName+"/st/lab").listFiles();
        for (int i=0;i<labFiles.length;i++){
            
            //open original lab file
            BufferedReader labIn = new BufferedReader(
                    new FileReader(labFiles[i]));
            
            //open destination lab file
            PrintWriter labOut = new PrintWriter(
                    new FileWriter(new File(labDestDir
                            +labFiles[i].getName())));
            
            //go through original lab file 
            while ((line = labIn.readLine()) != null){
                if (line.startsWith("#")){
                    //copy the line to destination lab file
                    labOut.println(line);
                } else {
                    //tokenize the line
                    StringTokenizer tok = new StringTokenizer(line); 
                    
                    //first token is time
                    double time = Float.parseFloat(tok.nextToken());
                    //add 0.012
                    //TODO: find out why we are adding 0.012
                    time += 0.012;
                    //prune time to 5 positions behind the dot
                    String timeString = df.format(time);
                    
                    //next token is some number
                    String mysteriousNumber = tok.nextToken();
                   
                    //next token is the phone
                    String phone = tok.nextToken();
                    
                    if (phone.equals("SIL")){
                        //replace silence symbol
                        phone = "pau";
                    } else {
                        //cut off the stuff behind the phone
                        phone = phone.substring(0,phone.indexOf("("));
                        //convert phone back to SAMPA
                        phone = convertPhone(phone);
                    }
                    labOut.println(timeString+" "+mysteriousNumber+" "+phone);
                }
            }
        
            //close files     
            labIn.close();
            labOut.flush();
            labOut.close();
        }
        System.out.println("... done.");
        System.out.println("All done!");
        
        return true;
    }
    
    /**
     * Convert the given phone from Sphinx-readable format
     * back to SAMPA
     * 
     * @param phone the phone
     * @return the converted phone
     */
     private String convertPhone(String phone){
         boolean uppercase = false;
         char[] phoneChars = phone.toCharArray();
         StringBuffer convertedPhone = new StringBuffer();
         for (int i=0;i<phoneChars.length;i++){
             char phoneChar = phoneChars[i];
             if (Character.isLetter(phoneChar)){
                 if (uppercase){
                     //character originally was uppercase
                     //append the phone as it is
                     convertedPhone.append(phoneChar);
                     uppercase = false;
                 } else {
                     //character originally was lowercase
                     //convert back to lowercase
                     convertedPhone.append(Character.toLowerCase(phoneChar));
                 }
             } else {
                 if (phoneChar == '*'){
                     //next letter was uppercase, set uppercase to true
                     uppercase = true;
                 } else {
                     //just append other non-letter signs
                     convertedPhone.append(phoneChar);
                 }
             }
         }
         return convertedPhone.toString();
     }
     
   
    
    
    /**
     * Provide the progress of computation, in percent, or -1 if
     * that feature is not implemented.
     * @return -1 if not implemented, or an integer between 0 and 100.
     */
    public int getProgress()
    {
        return -1;
    }
    
    class StreamGobbler extends Thread
    {
        InputStream is;
        String type;
        
        StreamGobbler(InputStream is, String type)
        {
            this.is = is;
            this.type = type;
        }
        
        public void run()
        {
            try
            {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line=null;
                while ( (line = br.readLine()) != null)
                    System.out.println(type + ">" + line);    
                } catch (IOException ioe)
                  {
                    ioe.printStackTrace();  
                  }
        }
    }
    

}